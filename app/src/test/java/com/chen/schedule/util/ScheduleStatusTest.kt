package com.chen.schedule.util

import com.chen.schedule.domain.model.Course
import com.chen.schedule.domain.model.Semester
import com.chen.schedule.domain.model.TimeSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleStatusTest {

    private fun semester(
        name: String = "2026–2027 第一学期",
        startDate: Long = 1_700_000_000_000L,
        totalWeeks: Int = 18,
        schemeId: Long = 0
    ) = Semester(id = 1, name = name, startDate = startDate, totalWeeks = totalWeeks, isCurrent = true, schemeId = schemeId)

    private fun slots(vararg ranges: Pair<String, String>) =
        ranges.mapIndexed { i, (s, e) ->
            TimeSlot(slotNumber = i + 1, startTime = s, endTime = e, name = "第${i + 1}节")
        }

    private fun course(startSlot: Int, endSlot: Int) = Course(
        name = "数学", dayOfWeek = 1, startSlot = startSlot, endSlot = endSlot,
        startWeek = 1, endWeek = 16, semesterId = 1
    )

    // ===== 学期 =====

    @Test fun semesterWithCompleteDataIsDone() {
        val check = ScheduleStatus.checkSemester(semester())
        assertTrue(check.done)
        assertTrue(check.issues.isEmpty())
    }

    @Test fun semesterMissingNameIsPending() {
        val check = ScheduleStatus.checkSemester(semester(name = "  "))
        assertFalse(check.done)
        assertTrue(check.issues.contains(ScheduleStatus.Issue.NAME_EMPTY))
    }

    @Test fun semesterInvalidWeeksIsPending() {
        assertFalse(ScheduleStatus.checkSemester(semester(totalWeeks = 0)).done)
        assertFalse(ScheduleStatus.checkSemester(semester(totalWeeks = 54)).done)
        assertTrue(ScheduleStatus.checkSemester(semester(totalWeeks = 1)).done)
        assertTrue(ScheduleStatus.checkSemester(semester(totalWeeks = 53)).done)
    }

    @Test fun semesterInvalidDateIsPending() {
        assertFalse(ScheduleStatus.checkSemester(semester(startDate = 0L)).done)
    }

    @Test fun noSemesterIsPending() {
        val check = ScheduleStatus.checkSemester(null)
        assertFalse(check.done)
        assertTrue(check.issues.contains(ScheduleStatus.Issue.NO_SEMESTER))
    }

    // ===== 作息 =====

    @Test fun validSlotsAndCoverageIsDone() {
        val check = ScheduleStatus.checkScheme(
            semester(),
            slots("08:00" to "08:45", "08:55" to "09:40"),
            listOf(course(1, 2))
        )
        assertTrue(check.done)
        assertTrue(check.missingSlots.isEmpty())
    }

    /** 未到开学日期 / 已放假 / 没有课程都不应影响状态。 */
    @Test fun noCoursesDoesNotMakeSchemePending() {
        val check = ScheduleStatus.checkScheme(
            semester(schemeId = 0),
            slots("08:00" to "08:45"),
            emptyList()
        )
        assertTrue(check.done)
    }

    @Test fun emptySlotsIsPending() {
        val check = ScheduleStatus.checkScheme(semester(), emptyList(), emptyList())
        assertFalse(check.done)
        assertTrue(check.issues.contains(ScheduleStatus.Issue.NO_SLOTS))
    }

    @Test fun duplicateSlotNumbersIsPending() {
        val duplicate = listOf(
            TimeSlot(slotNumber = 1, startTime = "08:00", endTime = "08:45"),
            TimeSlot(slotNumber = 1, startTime = "09:00", endTime = "09:45")
        )
        val check = ScheduleStatus.checkScheme(semester(), duplicate, emptyList())
        assertFalse(check.done)
        assertTrue(check.issues.contains(ScheduleStatus.Issue.SLOT_NUMBER_INVALID))
    }

    @Test fun endNotAfterStartIsPending() {
        val bad = listOf(TimeSlot(slotNumber = 1, startTime = "09:00", endTime = "08:45"))
        assertFalse(ScheduleStatus.checkScheme(semester(), bad, emptyList()).done)
    }

    @Test fun overlappingSlotsIsPending() {
        val overlapping = slots("08:00" to "09:00", "08:30" to "09:30")
        val check = ScheduleStatus.checkScheme(semester(), overlapping, emptyList())
        assertFalse(check.done)
        assertTrue(check.issues.contains(ScheduleStatus.Issue.SLOT_ORDER_INVALID))
    }

    @Test fun missingCourseSlotsAreReported() {
        val check = ScheduleStatus.checkScheme(
            semester(),
            slots("08:00" to "08:45"),
            listOf(course(1, 3))
        )
        assertFalse(check.done)
        assertEquals(listOf(2, 3), check.missingSlots)
        assertTrue(check.issues.contains(ScheduleStatus.Issue.SLOT_COVERAGE_MISSING))
    }

    @Test fun isSlotsValidMatchesCheckRules() {
        assertTrue(ScheduleStatus.isSlotsValid(slots("08:00" to "08:45", "08:55" to "09:40")))
        assertFalse(ScheduleStatus.isSlotsValid(emptyList()))
        assertFalse(ScheduleStatus.isSlotsValid(slots("08:00" to "09:00", "08:30" to "09:30")))
    }

    @Test fun builtInTemplatesAreSelfConsistent() {
        TimeSchemeTemplates.builtIns.forEach { builtIn ->
            val generated = TimeSchemeTemplates.slotsOf(builtIn.startTimes)
            assertEquals(builtIn.startTimes.size, generated.size)
            assertTrue(builtIn.name, ScheduleStatus.isSlotsValid(generated))
            // 每节 45 分钟
            generated.forEach {
                val start = ScheduleStatus.parseTime(it.startTime)!!
                val end = ScheduleStatus.parseTime(it.endTime)!!
                assertEquals(45L, java.time.Duration.between(start, end).toMinutes())
            }
        }
    }
}
