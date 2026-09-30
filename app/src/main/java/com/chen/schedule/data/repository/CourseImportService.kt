package com.chen.schedule.data.repository

import androidx.room.withTransaction
import com.chen.schedule.data.local.AppDatabase
import com.chen.schedule.domain.model.Course
import com.chen.schedule.domain.model.CoursePalette
import com.chen.schedule.util.CourseImportRules
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class CourseImportService @Inject constructor(
    private val database: AppDatabase,
    private val semesters: SemesterRepository,
    private val courses: CourseRepository,
    private val slots: TimeSlotRepository
) {
    data class Result(val added: Int, val skipped: Int) {
        val message: String get() = "导入完成：新增 $added 条，跳过 $skipped 条重复安排"
    }

    suspend fun importCourses(incoming: List<Course>, expectedSemesterId: Long): Result = database.withTransaction {
        require(incoming.isNotEmpty()) { "没有可导入的课程" }
        val semester = semesters.getCurrentSemester() ?: error("请先创建学期")
        require(semester.id == expectedSemesterId) { "当前学期已切换，请重新读取课表" }
        CourseImportRules.validateForSemester(incoming, semester, slots.getTimeSlotsBySchemeDirect(semester.schemeId))
        val existing = courses.getCoursesBySemester(semester.id).first()
        val plan = CourseImportRules.plan(incoming, existing)
        val colors = existing.associate { it.name.trim() to it.color }
        val fresh = CoursePalette.assignColors(plan.fresh).map {
            it.copy(semesterId = semester.id, color = colors[it.name] ?: it.color)
        }
        if (fresh.isNotEmpty()) courses.insertAll(fresh)
        Result(fresh.size, plan.skipped)
    }
}
