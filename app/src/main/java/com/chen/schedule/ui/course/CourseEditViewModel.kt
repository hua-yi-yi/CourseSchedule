package com.chen.schedule.ui.course

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chen.schedule.data.repository.CourseRepository
import com.chen.schedule.domain.model.Course
import com.chen.schedule.domain.model.WeekType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.chen.schedule.ui.theme.courseColors
import javax.inject.Inject

data class CourseEditState(
    val name: String = "",
    val teacher: String = "",
    val classroom: String = "",
    val dayOfWeek: Int = 1,
    val startSlot: Int = 1,
    val endSlot: Int = 2,
    val startWeek: Int = 1,
    val endWeek: Int = 16,
    val weekType: WeekType = WeekType.ALL,
    val color: Long = 0xFF4CAF50,
    val note: String = "",
    val isEditing: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
    val saved: Boolean = false
)

@HiltViewModel
class CourseEditViewModel @Inject constructor(
    private val courseRepository: CourseRepository,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context
) : ViewModel() {

    private val _state = MutableStateFlow(CourseEditState())
    val state: StateFlow<CourseEditState> = _state.asStateFlow()

    init {
        _state.update { it.copy(color = courseColors.random()) }
    }

    fun loadCourse(courseId: Long) {
        viewModelScope.launch {
            courseRepository.getCourseById(courseId)?.let { course ->
                _state.update {
                    it.copy(
                        name = course.name,
                        teacher = course.teacher,
                        classroom = course.classroom,
                        dayOfWeek = course.dayOfWeek,
                        startSlot = course.startSlot,
                        endSlot = course.endSlot,
                        startWeek = course.startWeek,
                        endWeek = course.endWeek,
                        weekType = course.weekType,
                        color = course.color,
                        note = course.note,
                        isEditing = true
                    )
                }
            }
        }
    }

    fun updateName(name: String) = _state.update { it.copy(name = name) }
    fun updateTeacher(teacher: String) = _state.update { it.copy(teacher = teacher) }
    fun updateClassroom(classroom: String) = _state.update { it.copy(classroom = classroom) }
    fun updateDayOfWeek(day: Int) = _state.update { it.copy(dayOfWeek = day) }
    fun updateStartSlot(slot: Int) = _state.update { it.copy(startSlot = slot, endSlot = maxOf(slot, it.endSlot)) }
    fun updateEndSlot(slot: Int) = _state.update { it.copy(endSlot = slot, startSlot = minOf(slot, it.startSlot)) }
    fun updateStartWeek(week: Int) = _state.update { it.copy(startWeek = week, endWeek = maxOf(week, it.endWeek)) }
    fun updateEndWeek(week: Int) = _state.update { it.copy(endWeek = week, startWeek = minOf(week, it.startWeek)) }
    fun updateWeekType(weekType: WeekType) = _state.update { it.copy(weekType = weekType) }
    fun updateColor(color: Long) = _state.update { it.copy(color = color) }
    fun updateNote(note: String) = _state.update { it.copy(note = note) }

    fun save(semesterId: Long, courseId: Long? = null) {
        val s = _state.value
        if (s.isSaving || s.saved) return
        if (s.name.isBlank() || s.startSlot < 1 || s.endSlot < s.startSlot ||
            s.startWeek < 1 || s.endWeek < s.startWeek || s.dayOfWeek !in 1..7) {
            _state.update { it.copy(error = "请填写课程名称，并检查节次和周次范围") }
            return
        }
        _state.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            try {
                val course = Course(
                    id = courseId ?: 0,
                    name = s.name.trim(),
                    teacher = s.teacher,
                    classroom = s.classroom,
                    dayOfWeek = s.dayOfWeek,
                    startSlot = s.startSlot,
                    endSlot = s.endSlot,
                    startWeek = s.startWeek,
                    endWeek = s.endWeek,
                    weekType = s.weekType,
                    color = s.color,
                    semesterId = semesterId,
                    note = s.note
                )
                if (courseId != null) {
                    courseRepository.update(course)
                    courseRepository.updateColorByNameAndSemester(course.name, semesterId, course.color)
                } else {
                    courseRepository.insert(course)
                }
                _state.update { it.copy(saved = true, isSaving = false) }
                com.chen.schedule.widget.WidgetUpdater.refreshAll(context)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(isSaving = false, error = "保存失败，请重试") }
            }
        }
    }
}
