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
    private val slots: TimeSlotRepository,
    private val backup: com.chen.schedule.ui.settings.ScheduleBackupService
) {
    data class Result(val added: Int, val skipped: Int, val replaced: Int = 0, val removed: Int = 0) {
        val message: String get() = "导入完成：新增 $added 条，替换 $replaced 条，删除 $removed 条，跳过 $skipped 条重复安排"
    }

    suspend fun review(incoming: List<Course>, source: String): com.chen.schedule.util.ImportReviewContext {
        val semester = semesters.getCurrentSemester() ?: error("请先创建学期")
        return com.chen.schedule.util.ImportReviewContext(semester, slots.getTimeSlotsBySchemeDirect(semester.schemeId), courses.getCoursesBySemester(semester.id).first(), source)
    }

    suspend fun applyReviewed(selection: com.chen.schedule.util.ImportSelection, review: com.chen.schedule.util.ImportReviewContext): Result = database.withTransaction {
        val semester = semesters.getCurrentSemester() ?: error("请先创建学期")
        require(semester == review.semester) { "学期配置已变化，请重新读取课表" }
        val currentSlots = slots.getTimeSlotsBySchemeDirect(semester.schemeId)
        val existing = courses.getCoursesBySemester(semester.id).first()
        require(existing.toSet() == review.existing.toSet() && currentSlots.toSet() == review.slots.toSet()) { "课表或作息已变化，请重新读取" }
        com.chen.schedule.util.ImportReview.validateSelection(selection, existing, review.source)
        CourseImportRules.validateForSemester(selection.courses, semester, currentSlots)
        val removedIds = selection.removals + selection.replacements.values
        val remaining = existing.filter { it.id !in removedIds }
        val normal = selection.courses.filterIndexed { index, _ -> index !in selection.replacements }
        val proposedReplacements = selection.replacements.map { (index, oldId) ->
            val old = existing.single { it.id == oldId }
            selection.courses[index].copy(id = 0, color = old.color, note = old.note, semesterId = semester.id, importSource = review.source)
        }
        val replacementPlan = CourseImportRules.plan(proposedReplacements, remaining)
        val replacementCourses = replacementPlan.fresh
        val plan = CourseImportRules.plan(normal, remaining + replacementCourses)
        val colors = existing.associate { it.name.trim() to it.color }
        val fresh = CoursePalette.assignColors(plan.fresh).map { it.copy(id = 0, semesterId = semester.id, color = colors[it.name] ?: it.color, importSource = review.source) }
        if (removedIds.isNotEmpty() || fresh.isNotEmpty()) {
            backup.createRecoveryPoint("导入课表")
            existing.filter { it.id in removedIds }.forEach { courses.delete(it) }
            courses.insertAll(replacementCourses + fresh)
        }
        Result(fresh.size, plan.skipped + replacementPlan.skipped, selection.replacements.size, selection.removals.size)
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
