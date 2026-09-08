package com.chen.schedule.util
import org.junit.Assert.*
import org.junit.Test

class TimeSlotParserTest {
    @Test fun parsesAndSortsValidSlots() {
        val slots = TimeSlotParser.parse("2 08:55-09:40 第二节\n1 08:00-08:45")
        assertEquals(listOf(1, 2), slots.map { it.slotNumber })
        assertEquals("第二节", slots[1].name)
    }
    @Test fun rejectsInvalidInputWithoutPartialImport() {
        listOf("1 99:99-10:00", "1 09:00-08:00", "0 08:00-08:45",
            "1 08:00-08:45\n1 09:00-09:45", "1 08:00-09:00\n2 08:30-09:30",
            "1 08:00-08:45\nbad row", "").forEach { input ->
            assertTrue(input, runCatching { TimeSlotParser.parse(input) }.isFailure)
        }
    }
    @Test fun backupRoundTripPreservesSemesterAndSlots() {
        val backup = ScheduleBackup(semester = com.chen.schedule.domain.model.Semester(
            name = "秋季", startDate = 123456789L, totalWeeks = 18), courses = emptyList(),
            timeSlots = TimeSlotParser.parse("1 08:00-08:45").map { it.copy(season = 2) })
        val json = kotlinx.serialization.json.Json
        assertEquals(backup, json.decodeFromString(ScheduleBackup.serializer(),
            json.encodeToString(ScheduleBackup.serializer(), backup)))
    }
}
