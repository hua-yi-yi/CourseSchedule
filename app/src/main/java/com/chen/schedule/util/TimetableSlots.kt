package com.chen.schedule.util

import com.chen.schedule.domain.model.Course
import com.chen.schedule.domain.model.TimeSlot

/** Preserve every configured row, including empty rows and courses missing a time definition. */
object TimetableSlots {
    // A minute-based daily time scheme cannot have more than 24 * 60 non-overlapping slots.
    private const val MAX_EXPANDED_SLOTS = 24 * 60

    /** Keep malformed legacy data visible at its boundaries without allocating an unbounded range. */
    internal fun courseNumbers(course: Course): List<Int> {
        val start = course.startSlot.coerceAtLeast(1)
        if (course.endSlot < start) return emptyList()
        val count = course.endSlot.toLong() - start + 1
        return if (count <= MAX_EXPANDED_SLOTS) (start..course.endSlot).toList()
        else listOf(start, course.endSlot)
    }

    fun rows(slots: List<TimeSlot>, courses: List<Course>): List<TimeSlot> {
        val configured = slots.associateBy { it.slotNumber }
        val numbers = configured.keys + courses.flatMap(::courseNumbers)
        val visible = if (numbers.isEmpty()) (1..12).toList() else numbers.toList()
        return visible.distinct().sorted().map { number ->
            configured[number] ?: TimeSlot(slotNumber = number, startTime = "", endTime = "", name = "$number")
        }
    }
}
