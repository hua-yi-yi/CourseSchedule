package com.chen.schedule.util

import com.chen.schedule.domain.model.Course

enum class ChangeScope(val label: String) { ONCE("仅本周这次"), FUTURE("从本周开始"), ALL("整条上课安排") }

/** Split recurrence boundaries rather than modifying other occurrences. */
object CourseChangePlanner {
    fun plan(original: Course, edited: Course?, scope: ChangeScope, week: Int): List<Course> {
        if (scope == ChangeScope.ALL) return listOfNotNull(edited?.copy(id = 0, importSource = ""))
        require(original.appliesToWeek(week)) { "这条安排在所选周没有课程" }
        val retained = mutableListOf<Course>()
        if (week > original.startWeek) retained += original.copy(id = 0, endWeek = week - 1, importSource = "")
        if (scope == ChangeScope.ONCE && week < original.endWeek)
            retained += original.copy(id = 0, startWeek = week + 1, importSource = "")
        edited?.let {
            retained += it.copy(id = 0, startWeek = week,
                endWeek = if (scope == ChangeScope.ONCE) week else original.endWeek,
                weekType = if (scope == ChangeScope.ONCE) com.chen.schedule.domain.model.WeekType.ALL else it.weekType,
                importSource = "")
        }
        return retained.filter { c -> (c.startWeek..c.endWeek).any(c::appliesToWeek) }
    }
}
