package com.chen.schedule.util

import com.chen.schedule.domain.model.Course
import com.chen.schedule.domain.model.TimeSlot
import java.time.LocalTime

/** Uses complete course intervals, including breaks between consecutive slots. */
object TodaySummary {
    fun describe(courses: List<Course>, slots: List<TimeSlot>, now: LocalTime): String {
        if (courses.isEmpty()) return "今天没有课程，享受自己的时间"
        val prefix = "共 ${courses.size} 门课"
        val intervals = courses.sortedBy { it.startSlot }.map { course ->
            val start = slots.find { it.slotNumber == course.startSlot }?.startTime
            val end = slots.find { it.slotNumber == course.endSlot }?.endTime
            Triple(course, parse(start), parse(end))
        }
        val active = intervals.firstOrNull { (_, start, end) ->
            start != null && end != null && !now.isBefore(start) && now.isBefore(end)
        }
        if (active != null) return "$prefix · 进行中 ${active.first.name}"
        val next = intervals.filter { it.second?.isAfter(now) == true }.minByOrNull { it.second!! }
        if (next != null) return "$prefix · ${next.second} ${next.first.name}"
        if (intervals.any { it.second == null || it.third == null }) return "$prefix · 设置作息后可查看上课提醒"
        return "$prefix · 今日课程已结束"
    }

    private fun parse(value: String?): LocalTime? =
        runCatching { LocalTime.parse(value) }.getOrNull()
}
