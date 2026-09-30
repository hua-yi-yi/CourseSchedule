package com.chen.schedule.util

import com.chen.schedule.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class CourseImportRulesTest {
    private val course = Course(name = "高数")
    private val semester = Semester(name = "秋季", startDate = 1, totalWeeks = 16)
    private val slots = listOf(TimeSlot(slotNumber = 1), TimeSlot(slotNumber = 2, startTime = "09:00", endTime = "09:45"))
    private fun fails(c: Course, available: List<TimeSlot> = slots) =
        assertTrue(runCatching { CourseImportRules.validateForSemester(listOf(c), semester, available) }.isFailure)

    @Test fun duplicatesWithinInputAndExistingDataAreSkipped() {
        val p = CourseImportRules.plan(listOf(course, course.copy(id = 22, note = "新备注"), course.copy(classroom = "另一教室")), listOf(course.copy(id = 3, color = 99)))
        assertEquals(1, p.fresh.size)
        assertEquals(2, p.skipped)
        assertEquals(0L, p.fresh.single().id)
    }
    @Test fun whitespaceDoesNotCreateNewCourseIdentity() {
        assertEquals(1, CourseImportRules.plan(listOf(course.copy(name = " 高数 ")), listOf(course)).skipped)
    }
    @Test fun teacherRoomWeeksAndWeekTypeDistinguishArrangements() {
        val changes = listOf(course.copy(teacher = "张老师"), course.copy(classroom = "101"), course.copy(startWeek = 2), course.copy(weekType = WeekType.ODD))
        assertEquals(4, CourseImportRules.plan(changes, listOf(course)).fresh.size)
    }
    @Test fun validCourseAndCompleteSlotsAreAccepted() {
        CourseImportRules.validateForSemester(listOf(course), semester, slots)
    }
    @Test fun missingIntermediateSlotIsRejected() { fails(course.copy(endSlot = 3), slots + TimeSlot(slotNumber = 4, startTime = "10:00", endTime = "10:45")) }
    @Test fun emptyOrInvalidTimetableIsRejected() { fails(course, emptyList()); fails(course, listOf(TimeSlot(endTime = "07:00"))) }
    @Test fun semesterWeekLimitAndHugeSlotRangeAreRejected() { fails(course.copy(endWeek = 17)); fails(course.copy(endSlot = Int.MAX_VALUE)) }
}
