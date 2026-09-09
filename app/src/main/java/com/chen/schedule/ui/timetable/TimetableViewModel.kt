package com.chen.schedule.ui.timetable

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chen.schedule.data.repository.CourseRepository
import com.chen.schedule.data.repository.SemesterRepository
import com.chen.schedule.data.repository.TimeSlotRepository
import com.chen.schedule.domain.model.Course
import com.chen.schedule.domain.model.Semester
import com.chen.schedule.domain.model.TimeScheme
import com.chen.schedule.domain.model.TimeSlot
import com.chen.schedule.util.ScheduleStatus
import com.chen.schedule.util.WeekCalculator
import com.chen.schedule.widget.WidgetUpdater
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 空白单元格被点击后要预填的位置。 */
data class BlankClickTarget(
    val dayOfWeek: Int,
    val startSlot: Int,
    /** 点击时正在查看的周次。 */
    val week: Int,
    /** 当前学期总周数,用于周次范围预填。 */
    val totalWeeks: Int = 16
)

data class TimetableState(
    val courses: List<Course> = emptyList(),
    val currentSemester: Semester? = null,
    val timeSlots: List<TimeSlot> = emptyList(),
    val currentWeek: Int = 1,
    val selectedDay: Int = java.time.LocalDate.now().dayOfWeek.value, // Monday = DayOfWeek.MON.index
    val isDayView: Boolean = false,
    val showWeekend: Boolean = java.time.LocalDate.now().dayOfWeek.value > 5,
    /** 引导面板:null = 不显示。 */
    val guide: ScheduleGuide? = null
) {
    /** 学期与作息是否都已有效配置。 */
    val isConfigured: Boolean get() = currentSemester?.isDefined == true && timeSlots.isNotEmpty()
}

/** 学期是否具备「已完成」的必要数据。 */
private val Semester?.isDefined: Boolean
    get() = this != null &&
        name.isNotBlank() &&
        startDate > 0L &&
        totalWeeks in ScheduleStatus.MIN_WEEKS..ScheduleStatus.MAX_WEEKS

/** 「先完成课表设置」引导面板状态。 */
data class ScheduleGuide(
    val semesterDone: Boolean,
    val schemeDone: Boolean,
    val missingSlots: List<Int>,
    /** 保留的点击位置,配置完成后据此继续添加课程。 */
    val target: BlankClickTarget?,
    /**
     * 临时隐藏(进入设置页期间)。隐藏时仍保留 [target],
     * 返回课表或数据变化后重新显示,以便用户「继续添加课程」。
     */
    val hidden: Boolean = false
) {
    val allDone: Boolean get() = semesterDone && schemeDone
}

@HiltViewModel
class TimetableViewModel @Inject constructor(
    private val courseRepository: CourseRepository,
    private val semesterRepository: SemesterRepository,
    private val timeSlotRepository: TimeSlotRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _state = MutableStateFlow(TimetableState())
    val state: StateFlow<TimetableState> = _state.asStateFlow()

    private var slotObservation: Job? = null

    init {
        observeSemesterAndCourses()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeSemesterAndCourses() {
        viewModelScope.launch {
            semesterRepository.getAllSemesters()
                .map { semesters -> semesters.find { it.isCurrent } }
                .flatMapLatest { current ->
                    _state.update {
                        it.copy(
                            currentSemester = current,
                            currentWeek = current?.let { c ->
                                WeekCalculator.currentWeek(c.startDate, c.totalWeeks)
                            } ?: 1
                        )
                    }
                    refreshGuide()
                    observeSlotsFor(current?.schemeId ?: TimeScheme.LEGACY_ID)
                    if (current != null) courseRepository.getCoursesBySemester(current.id)
                    else flowOf(emptyList())
                }
                .collect { courses ->
                    _state.update { it.copy(courses = courses) }
                    refreshGuide()
                }
        }
    }

    /** 课表只展示「当前学期所关联方案」的节次,切换学期/方案即时生效。 */
    private fun observeSlotsFor(schemeId: Long) {
        slotObservation?.cancel()
        slotObservation = viewModelScope.launch {
            timeSlotRepository.getTimeSlotsByScheme(schemeId).collect { slots ->
                _state.update { it.copy(timeSlots = slots) }
                refreshGuide()
            }
        }
    }

    fun setWeek(week: Int) {
        _state.update { it.copy(currentWeek = week.coerceIn(1, (it.currentSemester?.totalWeeks ?: 16).coerceAtLeast(1))) }
    }

    fun prevWeek() {
        val maxWeek = _state.value.currentSemester?.totalWeeks ?: 16
        _state.update { it.copy(currentWeek = (it.currentWeek - 1).coerceIn(1, maxWeek)) }
    }

    fun nextWeek() {
        val maxWeek = _state.value.currentSemester?.totalWeeks ?: 16
        _state.update { it.copy(currentWeek = (it.currentWeek + 1).coerceIn(1, maxWeek)) }
    }

    fun selectDay(day: Int) {
        _state.update { it.copy(selectedDay = day) }
    }

    fun toggleView() {
        _state.update { it.copy(isDayView = !it.isDayView) }
    }

    fun toggleWeekend() {
        _state.update {
            it.copy(
                showWeekend = !it.showWeekend,
                selectedDay = if (it.showWeekend && it.selectedDay > 5) 1 else it.selectedDay
            )
        }
    }

    /** 跳转到「今天」:回到当前周、选中今天、切换到日视图 */
    fun openToday() {
        val s = _state.value
        val actualWeek = s.currentSemester?.let { WeekCalculator.currentWeek(it.startDate, it.totalWeeks) } ?: 1
        val today = java.time.LocalDate.now().dayOfWeek.value
        _state.update {
            it.copy(
                currentWeek = actualWeek,
                selectedDay = today,
                isDayView = true,
                showWeekend = s.showWeekend || today > 5
            )
        }
    }

    fun getFilteredCourses(): List<Course> {
        val s = _state.value
        return s.courses.filter { course ->
            val weekMatch = course.appliesToWeek(s.currentWeek)
            val dayMatch = if (s.isDayView) course.dayOfWeek == s.selectedDay else true
            weekMatch && dayMatch
        }
    }

    /**
     * 点击空白单元格。
     * - 学期与作息都已配置:返回点击位置,调用方直接打开「新增课程」;
     * - 任一未配置:打开引导面板并保留位置,返回 null(调用方不导航)。
     */
    fun onBlankCellClick(dayOfWeek: Int, startSlot: Int): BlankClickTarget? {
        val state = _state.value
        val target = BlankClickTarget(
            dayOfWeek = dayOfWeek,
            startSlot = startSlot,
            week = state.currentWeek,
            totalWeeks = state.currentSemester?.totalWeeks ?: 16
        )
        val semesterDone = state.currentSemester.isDefined
        val schemeCheck = ScheduleStatus.checkScheme(
            state.currentSemester,
            state.timeSlots,
            state.courses
        )
        return if (semesterDone && schemeCheck.done) {
            _state.update { it.copy(guide = null) }
            target
        } else {
            _state.update {
                it.copy(
                    guide = ScheduleGuide(
                        semesterDone = semesterDone,
                        schemeDone = schemeCheck.done,
                        missingSlots = schemeCheck.missingSlots,
                        target = target
                    )
                )
            }
            null
        }
    }

    /** 引导面板关闭(用户选择「稍后再说」):连同保留的点击位置一起清除。 */
    fun dismissGuide() {
        _state.update { it.copy(guide = null) }
    }

    /**
     * 临时隐藏引导面板(用户去设置学期/作息时)。
     * **保留 [ScheduleGuide.target]**,以便返回后仍能按原点击位置「继续添加课程」。
     */
    fun hideGuidePreservingTarget() {
        _state.update { state ->
            state.copy(guide = state.guide?.copy(hidden = true))
        }
    }

    /**
     * 「继续添加课程」:配置完成后由用户主动点击。
     * 返回保留的点击位置;若无则返回 null。不会自动创建课程。
     */
    fun consumeGuideTarget(): BlankClickTarget? {
        val state = _state.value
        val target = state.guide?.target?.let { old ->
            val weeks = state.currentSemester?.totalWeeks ?: old.totalWeeks
            val slots = state.timeSlots.map { it.slotNumber }.sorted()
            old.copy(
                totalWeeks = weeks,
                week = old.week.coerceIn(1, weeks.coerceAtLeast(1)),
                startSlot = old.startSlot.takeIf { it in slots } ?: slots.firstOrNull() ?: 1
            )
        }
        _state.update { it.copy(guide = null) }
        return target
    }

    /**
     * 重新核对状态并重新显示引导面板(数据变化 / 从设置页返回时调用)。
     * 保留 target,更新「已完成/待设置」与缺失节次。
     */
    fun refreshGuide() {
        val state = _state.value
        val guide = state.guide ?: return
        val semesterDone = state.currentSemester.isDefined
        val schemeCheck = ScheduleStatus.checkScheme(
            state.currentSemester,
            state.timeSlots,
            state.courses
        )
        _state.update {
            it.copy(
                guide = guide.copy(
                    semesterDone = semesterDone,
                    schemeDone = schemeCheck.done,
                    missingSlots = schemeCheck.missingSlots,
                    hidden = false
                )
            )
        }
    }

    fun deleteCourse(course: Course) {
        viewModelScope.launch {
            courseRepository.delete(course)
            WidgetUpdater.refreshAll(context)
        }
    }
}
