package com.chen.schedule.ui.schedule

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chen.schedule.data.repository.CourseRepository
import com.chen.schedule.data.repository.SemesterRepository
import com.chen.schedule.data.repository.TimeSchemeRepository
import com.chen.schedule.data.repository.TimeSlotRepository
import com.chen.schedule.domain.model.Course
import com.chen.schedule.domain.model.Semester
import com.chen.schedule.domain.model.TimeScheme
import com.chen.schedule.domain.model.TimeSlot
import com.chen.schedule.util.ScheduleStatus
import com.chen.schedule.util.TimeSlotParser
import com.chen.schedule.util.WeekCalculator
import com.chen.schedule.widget.WidgetUpdater
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/** 「学期与作息」总览:两张菜单卡片的摘要与完成状态。 */
data class SemesterScheduleHubState(
    val semester: Semester? = null,
    val schemes: List<TimeScheme> = emptyList(),
    val currentScheme: TimeScheme? = null,
    val currentSlots: List<TimeSlot> = emptyList(),
    val currentCourses: List<Course> = emptyList(),
    val semesterCheck: ScheduleStatus.SemesterCheck = ScheduleStatus.checkSemester(null),
    val schemeCheck: ScheduleStatus.SchemeCheck =
        ScheduleStatus.checkScheme(null, emptyList(), emptyList()),
    val loading: Boolean = true
) {
    val semesterDone: Boolean get() = semesterCheck.done
    val schemeDone: Boolean get() = schemeCheck.done
}

/** 学期编辑页状态。 */
data class SemesterFormState(
    val editingId: Long? = null,
    val name: String = "",
    val dateMillis: Long? = null,
    val totalWeeks: String = "16",
    val weekHint: String = "",
    val saving: Boolean = false,
    val error: String? = null,
    val saved: Boolean = false,
    val dirty: Boolean = false,
    val started: Boolean = false
) {
    val totalWeeksValue: Int? get() = totalWeeks.toIntOrNull()
    val isEditing: Boolean get() = editingId != null
}

/** 方案编辑页状态。 */
data class SchemeFormState(
    val editingId: Long? = null,
    /** 非空表示「正在编辑内置模板」——保存时会先复制为自定义方案。 */
    val builtInSourceName: String? = null,
    val name: String = "",
    val slots: List<TimeSlot> = emptyList(),
    val importText: String = "",
    val importPreview: List<TimeSlot> = emptyList(),
    val importError: String? = null,
    val setCurrent: Boolean = true,
    val saving: Boolean = false,
    val error: String? = null,
    val saved: Boolean = false,
    val dirty: Boolean = false,
    val started: Boolean = false
) {
    val isNew: Boolean get() = editingId == null
    val isEditingBuiltInCopy: Boolean get() = builtInSourceName != null
}

@HiltViewModel
class ScheduleConfigViewModel @Inject constructor(
    private val semesterRepository: SemesterRepository,
    private val timeSlotRepository: TimeSlotRepository,
    private val timeSchemeRepository: TimeSchemeRepository,
    private val courseRepository: CourseRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _hub = MutableStateFlow(SemesterScheduleHubState())
    val hub: StateFlow<SemesterScheduleHubState> = _hub.asStateFlow()

    private val _semesterForm = MutableStateFlow(SemesterFormState())
    val semesterForm: StateFlow<SemesterFormState> = _semesterForm.asStateFlow()

    private val _schemeForm = MutableStateFlow(SchemeFormState())
    val schemeForm: StateFlow<SchemeFormState> = _schemeForm.asStateFlow()

    private val _message = MutableStateFlow("")
    val message: StateFlow<String> = _message.asStateFlow()

    private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    init {
        observeSemesters()
        observeCourses()
        observeSlots()
        // 幂等补齐内置模板行,失败不阻塞界面。
        viewModelScope.launch { runCatching { timeSchemeRepository.ensureBuiltInSchemes() } }
    }

    // ==================== 数据观察 ====================

    private fun observeSemesters() {
        viewModelScope.launch {
            semesterRepository.getAllSemesters().collect { semesters ->
                val current = semesters.firstOrNull { it.isCurrent }
                _hub.update { it.copy(semester = current, loading = false) }
                reloadSchemes()
            }
        }
    }

    private fun reloadSchemes() {
        viewModelScope.launch {
            runCatching { timeSchemeRepository.getAllSchemesDirect() }
                .onSuccess { schemes ->
                    _hub.update { it.copy(schemes = schemes) }
                    refreshCurrentScheme()
                }
        }
    }

    private fun refreshCurrentScheme() {
        viewModelScope.launch {
            val schemeId = _hub.value.semester?.schemeId ?: TimeScheme.LEGACY_ID
            val scheme = runCatching { timeSchemeRepository.getSchemeById(schemeId) }.getOrNull()
            _hub.update {
                it.copy(
                    currentScheme = scheme
                        ?: it.schemes.firstOrNull { s -> s.id == TimeScheme.LEGACY_ID }
                )
            }
            reloadSlots(schemeId)
        }
    }

    private var observingSemesterId: Long? = null

    private var courseObservation: kotlinx.coroutines.Job? = null

    private fun observeCourses() {
        viewModelScope.launch {
            _hub.collect { hub ->
                val id = hub.semester?.id
                if (id == observingSemesterId) return@collect
                observingSemesterId = id
                courseObservation?.cancel()
                if (id == null) {
                    _hub.update { it.copy(currentCourses = emptyList()) }
                    recomputeStatus()
                } else {
                    courseObservation = launch {
                        courseRepository.getCoursesBySemester(id).collect { courses ->
                            _hub.update { it.copy(currentCourses = courses) }
                            recomputeStatus()
                        }
                    }
                }
            }
        }
    }

    private var observingSchemeId: Long? = null
    private var slotObservation: kotlinx.coroutines.Job? = null

    private fun observeSlots() {
        viewModelScope.launch {
            _hub.collect { hub ->
                val id = hub.semester?.schemeId ?: TimeScheme.LEGACY_ID
                if (id == observingSchemeId) return@collect
                observingSchemeId = id
                slotObservation?.cancel()
                slotObservation = launch {
                    timeSlotRepository.getTimeSlotsByScheme(id).collect { slots ->
                        _hub.update { it.copy(currentSlots = slots) }
                        recomputeStatus()
                    }
                }
            }
        }
    }

    private fun reloadSlots(schemeId: Long) {
        observingSchemeId = schemeId
        slotObservation?.cancel()
        slotObservation = viewModelScope.launch {
            timeSlotRepository.getTimeSlotsByScheme(schemeId).collect { slots ->
                _hub.update { it.copy(currentSlots = slots) }
                recomputeStatus()
            }
        }
    }

    fun recomputeStatus() {
        _hub.update {
            it.copy(
                semesterCheck = ScheduleStatus.checkSemester(it.semester),
                schemeCheck = ScheduleStatus.checkScheme(it.semester, it.currentSlots, it.currentCourses)
            )
        }
    }

    fun clearMessage() { _message.value = "" }

    /** 选择页需要完整学期列表(含非当前学期)。 */
    suspend fun loadAllSemesters(): List<Semester> =
        runCatching { semesterRepository.getAllSemesters().first() }.getOrDefault(emptyList())

    // ==================== 学期 ====================

    fun startSemesterForm(editingId: Long?) {
        viewModelScope.launch {
            if (editingId == null) {
                _semesterForm.value = SemesterFormState(started = true)
                return@launch
            }
            val sem = semesterRepository.getSemesterById(editingId)
            _semesterForm.value = SemesterFormState(
                editingId = sem?.id,
                name = sem?.name.orEmpty(),
                dateMillis = sem?.startDate,
                totalWeeks = (sem?.totalWeeks ?: 16).toString(),
                started = true
            )
        }
    }

    fun updateSemesterName(value: String) = _semesterForm.update {
        it.copy(name = value, dirty = true, error = null)
    }

    fun updateSemesterDate(millis: Long?) = _semesterForm.update {
        it.copy(dateMillis = millis, dirty = true, error = null)
    }

    fun updateSemesterWeeks(value: String) = _semesterForm.update {
        it.copy(
            totalWeeks = value.filter { c -> c.isDigit() }.take(2),
            dirty = true,
            error = null
        )
    }

    fun updateWeekHint(value: String) = _semesterForm.update {
        it.copy(weekHint = value.filter { c -> c.isDigit() }.take(2))
    }

    /** 「根据当前周推算开学日期」——折叠辅助区域,结果写入同一个日期字段。 */
    fun computeStartDateFromWeek() {
        val form = _semesterForm.value
        val week = form.weekHint.toIntOrNull()
        val total = form.totalWeeksValue
        if (week == null || week < 1) {
            _semesterForm.update { it.copy(error = "请输入有效的当前周") }
            return
        }
        if (total != null && week > total) {
            _semesterForm.update { it.copy(error = "当前周不能超过总周数") }
            return
        }
        val start = LocalDate.now().minusWeeks((week - 1).toLong()).with(java.time.DayOfWeek.MONDAY)
        val millis = start.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        _semesterForm.update { it.copy(dateMillis = millis, error = null, dirty = true) }
    }

    fun saveSemesterForm() {
        val form = _semesterForm.value
        if (form.saving) return
        val name = form.name.trim()
        val weeks = form.totalWeeksValue
        val dateMillis = form.dateMillis

        val error = when {
            name.isBlank() -> "请填写学期名称"
            weeks == null || weeks !in ScheduleStatus.MIN_WEEKS..ScheduleStatus.MAX_WEEKS ->
                "总周数应为 ${ScheduleStatus.MIN_WEEKS}–${ScheduleStatus.MAX_WEEKS} 的整数"
            dateMillis == null -> "请选择开学日期"
            else -> null
        }
        if (error != null) {
            _semesterForm.update { it.copy(error = error) }
            return
        }

        _semesterForm.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            try {
                val existing = form.editingId?.let { semesterRepository.getSemesterById(it) }
                val semester = Semester(
                    id = form.editingId ?: 0,
                    name = name,
                    startDate = dateMillis!!,
                    totalWeeks = weeks!!,
                    isCurrent = existing?.isCurrent ?: true,
                    schemeId = existing?.schemeId ?: TimeScheme.LEGACY_ID
                )
                if (form.editingId == null) {
                    val newId = semesterRepository.insert(semester)
                    semesterRepository.setCurrentSemester(newId)
                } else {
                    semesterRepository.update(semester)
                }
                WidgetUpdater.refreshAll(context)
                _semesterForm.update { it.copy(saving = false, saved = true, dirty = false) }
            } catch (e: Exception) {
                _semesterForm.update {
                    it.copy(saving = false, error = "保存失败:${e.message ?: "未知错误"}")
                }
            }
        }
    }

    fun switchSemester(semesterId: Long) {
        viewModelScope.launch {
            try {
                semesterRepository.switchCurrentSemester(semesterId)
                refreshCurrentScheme()
                recomputeStatus()
                WidgetUpdater.refreshAll(context)
                _message.value = "已切换学期"
            } catch (e: Exception) {
                _message.value = "切换失败:${e.message ?: "未知错误"}"
            }
        }
    }

    // ==================== 作息方案 ====================

    fun startNewSchemeForm() {
        _schemeForm.value = SchemeFormState(started = true)
    }

    fun startSchemeFromTemplate(builtInName: String) {
        val slots = timeSchemeRepository.previewSlots(builtInName)
        _schemeForm.value = SchemeFormState(name = builtInName, slots = slots, dirty = true, started = true)
    }

    fun startSchemeFromExisting(source: TimeScheme) {
        viewModelScope.launch {
            val slots = timeSchemeRepository.getSlotsOfSchemeDirect(source.id)
            _schemeForm.value = SchemeFormState(
                name = "${source.name} 副本",
                slots = slots.map { it.copy(id = 0, schemeId = 0) },
                dirty = true,
                started = true
            )
        }
    }

    /**
     * 打开方案编辑页。
     * - 自定义方案:直接编辑;
     * - 内置模板:标记 builtInSourceName,保存时自动「复制为自定义方案」,模板本身不被修改。
     */
    fun startSchemeEditForm(schemeId: Long) {
        viewModelScope.launch {
            val scheme = timeSchemeRepository.getSchemeById(schemeId)
            val slots = timeSchemeRepository.getSlotsOfSchemeDirect(schemeId)
            _schemeForm.value = SchemeFormState(
                editingId = scheme?.id,
                builtInSourceName = if (scheme?.isBuiltIn == true) scheme.name else null,
                name = if (scheme?.isBuiltIn == true) "${scheme.name} 副本" else scheme?.name.orEmpty(),
                slots = slots,
                dirty = false,
                started = true
            )
        }
    }

    fun updateSchemeName(value: String) = _schemeForm.update {
        it.copy(name = value, dirty = true, error = null)
    }

    fun updateSchemeSetCurrent(value: Boolean) = _schemeForm.update {
        it.copy(setCurrent = value, dirty = true)
    }

    fun updateImportText(value: String) = _schemeForm.update {
        it.copy(importText = value, importError = null, dirty = true)
    }

    /** 文本导入:先解析并预览,确认后才写入。 */
    fun previewImportText() {
        val text = _schemeForm.value.importText
        try {
            val parsed = TimeSlotParser.parse(text)
            _schemeForm.update { it.copy(importPreview = parsed, importError = null) }
        } catch (e: Exception) {
            _schemeForm.update {
                it.copy(importPreview = emptyList(), importError = e.message ?: "解析失败")
            }
        }
    }

    fun confirmImportPreview() {
        val preview = _schemeForm.value.importPreview
        if (preview.isEmpty()) {
            _schemeForm.update { it.copy(importError = "请先解析并确认预览结果") }
            return
        }
        _schemeForm.update {
            it.copy(slots = preview, importPreview = emptyList(), importText = "", dirty = true)
        }
    }

    fun addBlankSlot() = _schemeForm.update { form ->
        val next = (form.slots.maxOfOrNull { it.slotNumber } ?: 0) + 1
        val previousEnd = form.slots.maxByOrNull { it.slotNumber }?.endTime ?: "08:00"
        form.copy(
            slots = form.slots + TimeSlot(
                slotNumber = next,
                startTime = previousEnd,
                endTime = addMinutes(previousEnd, 45),
                name = "第${next}节"
            ),
            dirty = true,
            error = null
        )
    }

    fun updateSlot(updated: TimeSlot) = _schemeForm.update { form ->
        form.copy(
            slots = form.slots.map {
                if (it.slotNumber == updated.slotNumber && it.id == updated.id) updated else it
            },
            dirty = true,
            error = null
        )
    }

    fun removeSlot(target: TimeSlot) = _schemeForm.update { form ->
        form.copy(
            slots = form.slots.filterNot { it.slotNumber == target.slotNumber && it.id == target.id },
            dirty = true,
            error = null
        )
    }

    /** 删除节次前的引用检查:引用该节次的课程数(跨学期)。 */
    suspend fun countCoursesUsingSlot(slotNumber: Int): Int =
        runCatching { courseRepository.countCoursesUsingSlot(slotNumber) }.getOrDefault(0)

    fun saveSchemeForm() {
        val form = _schemeForm.value
        if (form.saving) return
        val name = form.name.trim()
        if (name.isBlank()) {
            _schemeForm.update { it.copy(error = "请填写方案名称") }
            return
        }
        if (form.slots.isEmpty()) {
            _schemeForm.update { it.copy(error = "方案至少需要一个节次") }
            return
        }
        if (!ScheduleStatus.isSlotsValid(form.slots)) {
            _schemeForm.update {
                it.copy(error = "节次时间无效:结束需晚于开始,编号不可重复,且按编号排序后不得重叠")
            }
            return
        }

        _schemeForm.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            try {
                val current = semesterRepository.getCurrentSemester()
                val schemeId: Long = when {
                    // 编辑内置模板 → 复制为自定义方案,并保存用户实际编辑后的节次
                    form.isEditingBuiltInCopy ->
                        timeSchemeRepository.copyBuiltInToCustom(
                            form.builtInSourceName!!, name, form.setCurrent && current != null, form.slots
                        )

                    // 新建,或编辑「原有作息」→ 另存为新方案,不动原有数据
                    form.editingId == null || form.editingId == TimeScheme.LEGACY_ID ->
                        timeSchemeRepository.createCustomScheme(
                            name, form.slots, form.setCurrent && current != null
                        )

                    else -> {
                        timeSchemeRepository.renameScheme(form.editingId, name)
                        timeSchemeRepository.saveSchemeSlots(form.editingId, form.slots)
                        if (form.setCurrent && current != null) {
                            timeSchemeRepository.selectSchemeForSemester(current.id, form.editingId)
                        }
                        form.editingId
                    }
                }
                recomputeStatus()
                WidgetUpdater.refreshAll(context)
                _schemeForm.update { it.copy(saving = false, saved = true, dirty = false, editingId = schemeId) }
            } catch (e: Exception) {
                _schemeForm.update {
                    it.copy(saving = false, error = "保存失败:${e.message ?: "未知错误"}")
                }
            }
        }
    }

    /** 选择页「使用此方案」:只切换当前学期绑定,不删除任何方案。 */
    fun useSchemeForCurrentSemester(schemeId: Long) {
        viewModelScope.launch {
            try {
                val current = semesterRepository.getCurrentSemester()
                if (current == null) {
                    _message.value = "请先设置当前学期"
                    return@launch
                }
                timeSchemeRepository.selectSchemeForSemester(current.id, schemeId)
                refreshCurrentScheme()
                recomputeStatus()
                WidgetUpdater.refreshAll(context)
                _message.value = "已启用该作息方案"
            } catch (e: Exception) {
                _message.value = "切换失败:${e.message ?: "未知错误"}"
            }
        }
    }

    /**
     * 删除方案。若仍有学期引用,必须指定 [replacement] —— 引用学期改绑到该方案,
     * 被删方案的节次一并删除(不会并入「原有作息」)。
     */
    fun deleteScheme(scheme: TimeScheme, replacement: TimeScheme? = null) {
        viewModelScope.launch {
            try {
                timeSchemeRepository.deleteScheme(scheme.id, replacement?.id)
                refreshCurrentScheme()
                recomputeStatus()
                WidgetUpdater.refreshAll(context)
                _message.value = if (replacement != null) {
                    "已删除方案「${scheme.name}」,关联学期已改用「${replacement.name}」"
                } else {
                    "已删除方案「${scheme.name}」"
                }
            } catch (e: Exception) {
                _message.value = "删除失败:${e.message ?: "未知错误"}"
            }
        }
    }

    suspend fun semestersUsingScheme(schemeId: Long): List<Semester> =
        runCatching { timeSchemeRepository.semestersUsingScheme(schemeId) }.getOrDefault(emptyList())

    /** 方案预览/选择页需要某方案的节次。 */
    suspend fun loadSchemeSlots(schemeId: Long): List<TimeSlot> =
        runCatching { timeSchemeRepository.getSlotsOfSchemeDirect(schemeId) }.getOrDefault(emptyList())

    // ==================== 工具 ====================

    fun formatDate(millis: Long?): String = millis?.let {
        Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate().format(dateFormat)
    } ?: "未选择"

    fun currentWeekOf(semester: Semester): Int =
        WeekCalculator.currentWeek(semester.startDate, semester.totalWeeks)

    private fun addMinutes(time: String, minutes: Int): String {
        val parts = time.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: 8
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
        val total = (hour * 60 + minute + minutes).coerceIn(0, 24 * 60 - 1)
        return String.format("%02d:%02d", total / 60, total % 60)
    }
}
