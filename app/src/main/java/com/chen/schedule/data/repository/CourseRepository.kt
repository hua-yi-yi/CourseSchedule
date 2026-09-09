package com.chen.schedule.data.repository

import com.chen.schedule.data.local.dao.CourseDao
import com.chen.schedule.data.local.entity.CourseEntity
import com.chen.schedule.domain.model.Course
import com.chen.schedule.domain.model.WeekType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CourseRepository @Inject constructor(
    private val courseDao: CourseDao
) {
    fun getCoursesBySemester(semesterId: Long): Flow<List<Course>> =
        courseDao.getCoursesBySemester(semesterId).map { list -> list.map { it.toDomain() } }

    /** 全部课程(跨学期),用于完整备份。 */
    fun getAllCourses(): Flow<List<Course>> =
        courseDao.getAllCourses().map { list -> list.map { it.toDomain() } }

    fun getCoursesByDay(semesterId: Long, day: Int): Flow<List<Course>> =
        courseDao.getCoursesByDay(semesterId, day).map { list -> list.map { it.toDomain() } }

    suspend fun getCourseById(id: Long): Course? =
        courseDao.getCourseById(id)?.toDomain()

    suspend fun insert(course: Course): Long =
        courseDao.insert(course.toEntity())

    suspend fun update(course: Course) =
        courseDao.update(course.toEntity())

    suspend fun delete(course: Course) =
        courseDao.delete(course.toEntity())

    suspend fun deleteAllBySemester(semesterId: Long) =
        courseDao.deleteAllBySemester(semesterId)

    /** 清空全部课程(完整恢复备份前使用)。 */
    suspend fun deleteAll() = courseDao.deleteAllCourses()

    suspend fun insertAll(courses: List<Course>) =
        courseDao.insertAll(courses.map { it.toEntity() })

    suspend fun updateColorByNameAndSemester(name: String, semesterId: Long, color: Long) =
        courseDao.updateColorByNameAndSemester(name, semesterId, color)

    /** 引用指定节次的课程(删除/减少节次前的引用检查)。 */
    suspend fun getCoursesUsingSlot(semesterId: Long, slotNumber: Int): List<Course> =
        courseDao.getCoursesUsingSlot(semesterId, slotNumber).map { it.toDomain() }

    suspend fun countCoursesUsingSlot(slotNumber: Int): Int =
        courseDao.countCoursesUsingSlot(slotNumber)

    private fun CourseEntity.toDomain() = Course(
        id = id, name = name, teacher = teacher, classroom = classroom,
        dayOfWeek = dayOfWeek, startSlot = startSlot, endSlot = endSlot,
        startWeek = startWeek, endWeek = endWeek,
        weekType = when (weekType) { "odd" -> WeekType.ODD; "even" -> WeekType.EVEN; else -> WeekType.ALL },
        color = color, semesterId = semesterId, note = note
    )

    private fun Course.toEntity() = CourseEntity(
        id = id, name = name, teacher = teacher, classroom = classroom,
        dayOfWeek = dayOfWeek, startSlot = startSlot, endSlot = endSlot,
        startWeek = startWeek, endWeek = endWeek,
        weekType = when (weekType) { WeekType.ODD -> "odd"; WeekType.EVEN -> "even"; else -> "all" },
        color = color, semesterId = semesterId, note = note
    )
}
