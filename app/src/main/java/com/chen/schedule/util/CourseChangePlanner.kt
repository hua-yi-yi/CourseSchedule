package com.chen.schedule.util

import com.chen.schedule.domain.model.Course

enum class ChangeScope(val label: String) { ONCE("仅本周这次"), FUTURE("从本周开始"), ALL("整条上课安排") }

/** Split recurrence boundaries rather than modifying other occurrences. */
object CourseChangePlanner {
    fun plan(original: Course, edited: Course?, scope: ChangeScope, week: Int): List<Course> {
        if (scope == ChangeScope.ALL) {
            require(edited == null || edited.hasEffectiveWeeks()) { "所选周次内没有符合单双周设置的课程" }
            return listOfNotNull(edited?.copy(id = original.id, importSource = ""))
        }
        require(original.appliesToWeek(week)) { "这条安排在所选周没有课程" }
        val retained = mutableListOf<Course>()
        if (week > original.startWeek) retained += original.copy(id = 0, endWeek = week - 1, importSource = "")
        if (scope == ChangeScope.ONCE && week < original.endWeek)
            retained += original.copy(id = 0, startWeek = week + 1, importSource = "")
        edited?.let {
            val changed = it.copy(id = 0, startWeek = week,
                endWeek = if (scope == ChangeScope.ONCE) week else original.endWeek,
                weekType = if (scope == ChangeScope.ONCE) com.chen.schedule.domain.model.WeekType.ALL else it.weekType,
                importSource = "")
            require(changed.hasEffectiveWeeks()) { "所选周次内没有符合单双周设置的课程" }
            retained += changed
        }
        return retained.filter(Course::hasEffectiveWeeks)
    }
}
