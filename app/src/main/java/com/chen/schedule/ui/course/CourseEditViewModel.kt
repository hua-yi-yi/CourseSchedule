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
    val saved: Boolean = false
)

@HiltViewModel
class CourseEditViewModel @Inject constructor(
    private val courseRepository: CourseRepository
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
    fun updateStartSlot(slot: Int) = _state.update { it.copy(startSlot = slot) }
    fun updateEndSlot(slot: Int) = _state.update { it.copy(endSlot = slot) }
    fun updateStartWeek(week: Int) = _state.update { it.copy(startWeek = week) }
    fun updateEndWeek(week: Int) = _state.update { it.copy(endWeek = week) }
    fun updateWeekType(weekType: WeekType) = _state.update { it.copy(weekType = weekType) }
    fun updateColor(color: Long) = _state.update { it.copy(color = color) }
    fun updateNote(note: String) = _state.update { it.copy(note = note) }

    fun save(semesterId: Long, courseId: Long? = null) {
        viewModelScope.launch {
            val s = _state.value
            val course = Course(
                id = courseId ?: 0,
                name = s.name,
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
            _state.update { it.copy(saved = true) }
        }
    }
}
