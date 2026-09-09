package com.chen.schedule.util

import com.chen.schedule.domain.model.Course
import com.chen.schedule.domain.model.TimeSlot
import org.junit.Assert.assertEquals
import org.junit.Test

class TimetableSlotsTest {
    private fun slots(vararg numbers: Int) = numbers.map {
        TimeSlot(slotNumber = it, startTime = "08:00", endTime = "08:45", name = "$it")
    }
    @Test fun keepsAfternoonRowsAfterAddingMorningCourse() {
        val course = Course(name = "Math", dayOfWeek = 1, startSlot = 1, endSlot = 2,
            startWeek = 1, endWeek = 18)
        assertEquals((1..14).toList(), TimetableSlots.rows(slots(*(1..14).toList().toIntArray()), listOf(course)).map { it.slotNumber })
    }
    @Test fun emptyDayKeepsConfiguredRows() {
        assertEquals(listOf(1, 3, 14), TimetableSlots.rows(slots(1, 3, 14), emptyList()).map { it.slotNumber })
    }
    @Test fun missingCourseRowsRemainVisible() {
        val course = Course(name = "Math", dayOfWeek = 1, startSlot = 2, endSlot = 3,
            startWeek = 1, endWeek = 18)
        assertEquals(listOf(1, 2, 3, 4), TimetableSlots.rows(slots(1, 4), listOf(course)).map { it.slotNumber })
    }
}
