package com.chen.schedule.ui.timetable

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chen.schedule.data.repository.CourseRepository
import com.chen.schedule.data.repository.SemesterRepository
import com.chen.schedule.data.repository.TimeSlotRepository
import com.chen.schedule.domain.model.Course
import com.chen.schedule.domain.model.Semester
import com.chen.schedule.domain.model.TimeSlot
import com.chen.schedule.util.WeekCalculator
import com.chen.schedule.widget.WidgetUpdater
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TimetableState(
    val courses: List<Course> = emptyList(),
    val currentSemester: Semester? = null,
    val timeSlots: List<TimeSlot> = emptyList(),
    val currentWeek: Int = 1,
    val selectedDay: Int = 1, // Monday = DayOfWeek.MON.index
    val isDayView: Boolean = false,
    val showWeekend: Boolean = false
)

@HiltViewModel
class TimetableViewModel @Inject constructor(
    private val courseRepository: CourseRepository,
    private val semesterRepository: SemesterRepository,
    private val timeSlotRepository: TimeSlotRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _state = MutableStateFlow(TimetableState())
    val state: StateFlow<TimetableState> = _state.asStateFlow()

    init {
        observeSemesterAndCourses()
        loadTimeSlots()
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
                            currentWeek = current?.let { c -> WeekCalculator.currentWeek(c.startDate, c.totalWeeks) } ?: 1
                        )
                    }
                    if (current != null) courseRepository.getCoursesBySemester(current.id)
                    else flowOf(emptyList())
                }
                .collect { courses -> _state.update { it.copy(courses = courses) } }
        }
    }

    private fun loadTimeSlots() {
        viewModelScope.launch {
            timeSlotRepository.getAllTimeSlots().collect { slots ->
                _state.update { it.copy(timeSlots = slots) }
            }
        }
    }

    fun setWeek(week: Int) {
        _state.update { it.copy(currentWeek = week) }
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
        _state.update { it.copy(showWeekend = !it.showWeekend) }
    }

    /** 跳转到「今天」:回到当前周、选中今天、切换到日视图 */
    fun openToday() {
        val s = _state.value
        val actualWeek = s.currentSemester?.let { WeekCalculator.currentWeek(it.startDate, it.totalWeeks) } ?: 1
        _state.update {
            it.copy(
                currentWeek = actualWeek,
                selectedDay = java.time.LocalDate.now().dayOfWeek.value,
                isDayView = true
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

    fun deleteCourse(course: Course) {
        viewModelScope.launch {
            courseRepository.delete(course)
            WidgetUpdater.refreshAll(context)
        }
    }
}
