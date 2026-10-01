package com.chen.schedule.util

import com.chen.schedule.domain.model.*

data class ImportReviewContext(val semester: Semester, val slots: List<TimeSlot>, val existing: List<Course>, val source: String)
data class ImportSelection(val courses: List<Course>, val replacements: Map<Int, Long> = emptyMap(), val removals: Set<Long> = emptySet())

object ImportReview {
    fun suggestions(incoming: List<Course>, existing: List<Course>, source: String): Map<Int, Long> {
        val exact = incoming.map(CourseImportRules::identity).toSet()
        val available = existing.filter { it.importSource == source && source.isNotBlank() && CourseImportRules.identity(it) !in exact }
        val proposed = incoming.mapIndexedNotNull { index, course ->
            if (existing.any { CourseImportRules.identity(it) == CourseImportRules.identity(course) }) return@mapIndexedNotNull null
            val sameName = available.filter { it.name.trim() == course.name.trim() }
            val sameTime = sameName.filter { it.dayOfWeek == course.dayOfWeek && it.startSlot == course.startSlot && it.endSlot == course.endSlot }
            val candidate = sameTime.singleOrNull() ?: sameName.singleOrNull()
            candidate?.let { index to it.id }
        }
        return proposed.filter { pair -> proposed.count { it.second == pair.second } == 1 }.toMap()
    }

    fun validateSelection(selection: ImportSelection, existing: List<Course>, source: String) {
        require(selection.courses.isNotEmpty()) { "请至少保留一条安排" }
        require(selection.replacements.keys.all { it in selection.courses.indices }) { "替换索引无效" }
        require(selection.replacements.values.toSet().size == selection.replacements.size) { "一条旧安排只能替换一次" }
        require(selection.replacements.values.none { it in selection.removals }) { "不能同时替换和删除同一安排" }
        val ids = selection.replacements.values.toSet() + selection.removals
        require(ids.all { id -> existing.any { it.id == id && source.isNotBlank() && it.importSource == source } }) {
            "只能更新本入口之前导入的安排；手动修改及其他来源的课程受保护"
        }
    }
}
