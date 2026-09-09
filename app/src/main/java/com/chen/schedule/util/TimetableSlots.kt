package com.chen.schedule.util

import com.chen.schedule.domain.model.Course
import com.chen.schedule.domain.model.TimeSlot

/** Preserve every configured row, including empty rows and courses missing a time definition. */
object TimetableSlots {
    fun rows(slots: List<TimeSlot>, courses: List<Course>): List<TimeSlot> {
        val configured = slots.associateBy { it.slotNumber }
        val numbers = configured.keys + courses.flatMap { (it.startSlot..it.endSlot).toList() }
        val visible = if (numbers.isEmpty()) (1..12).toList() else numbers.toList()
        return visible.distinct().sorted().map { number ->
            configured[number] ?: TimeSlot(slotNumber = number, startTime = "", endTime = "", name = "$number")
        }
    }
}
