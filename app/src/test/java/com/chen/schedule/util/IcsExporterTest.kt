package com.chen.schedule.util

import com.chen.schedule.domain.model.Course
import com.chen.schedule.domain.model.Semester
import com.chen.schedule.domain.model.TimeSlot
import com.chen.schedule.domain.model.WeekType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class IcsExporterTest {

    private val testZone = ZoneId.of("Asia/Shanghai")
    // 2026-09-07 是周一
    private val semesterStart = LocalDate.of(2026, 9, 7).atStartOfDay(testZone).toInstant().toEpochMilli()

    private val semester = Semester(
        id = 1L,
        name = "2026年秋季学期",
        startDate = semesterStart,
        totalWeeks = 16,
        isCurrent = true,
        schemeId = 1L
    )

    private val slots = listOf(
        TimeSlot(slotNumber = 1, startTime = "08:00", endTime = "08:45"),
        TimeSlot(slotNumber = 2, startTime = "08:55", endTime = "09:40"),
        TimeSlot(slotNumber = 3, startTime = "10:00", endTime = "10:45"),
        TimeSlot(slotNumber = 4, startTime = "10:55", endTime = "11:40")
    )

    @Test
    fun exportEmptyCourses_returnsValidEnvelope() {
        val ics = IcsExporter.export(semester, emptyList(), slots, testZone)
        assertTrue(ics.startsWith("BEGIN:VCALENDAR\r\n"))
        assertTrue(ics.endsWith("END:VCALENDAR\r\n"))
        assertTrue(ics.contains("X-WR-CALNAME:2026年秋季学期 课程表\r\n"))
        assertFalse(ics.contains("BEGIN:VEVENT"))
    }

    @Test
    fun exportCourse_expandsOddEvenWeeksCorrectly() {
        val oddCourse = Course(
            id = 101L,
            name = "单周实验",
            dayOfWeek = 1, // 周一
            startSlot = 1,
            endSlot = 2,
            startWeek = 1,
            endWeek = 4,
            weekType = WeekType.ODD
        )
        val evenCourse = Course(
            id = 102L,
            name = "双周研讨",
            dayOfWeek = 2, // 周二
            startSlot = 3,
            endSlot = 4,
            startWeek = 1,
            endWeek = 4,
            weekType = WeekType.EVEN
        )

        val ics = IcsExporter.export(semester, listOf(oddCourse, evenCourse), slots, testZone)

        // 单周实验应生成第 1 周 (2026-09-07)、第 3 周 (2026-09-21) 两个事件
        assertTrue(ics.contains("SUMMARY:单周实验"))
        assertTrue(ics.contains("DTSTART;TZID=Asia/Shanghai:20260907T080000"))
        assertTrue(ics.contains("DTSTART;TZID=Asia/Shanghai:20260921T080000"))
        assertFalse(ics.contains("DTSTART;TZID=Asia/Shanghai:20260914T080000")) // 第 2 周不应有单周课

        // 双周研讨应生成第 2 周 (2026-09-15)、第 4 周 (2026-09-29) 两个事件
        assertTrue(ics.contains("SUMMARY:双周研讨"))
        assertTrue(ics.contains("DTSTART;TZID=Asia/Shanghai:20260915T100000"))
        assertTrue(ics.contains("DTSTART;TZID=Asia/Shanghai:20260929T100000"))
        assertFalse(ics.contains("DTSTART;TZID=Asia/Shanghai:20260908T100000")) // 第 1 周不应有双周课
    }

    @Test
    fun exportCourse_mapsSlotTimesCorrectly() {
        val course = Course(
            id = 201L,
            name = "高等数学",
            teacher = "张教授",
            classroom = "公教楼 A101",
            dayOfWeek = 3, // 周三: 2026-09-09
            startSlot = 1,
            endSlot = 2,
            startWeek = 1,
            endWeek = 1,
            weekType = WeekType.ALL
        )

        val ics = IcsExporter.export(semester, listOf(course), slots, testZone, alarmMinutes = 20)

        assertTrue(ics.contains("DTSTART;TZID=Asia/Shanghai:20260909T080000"))
        assertTrue(ics.contains("DTEND;TZID=Asia/Shanghai:20260909T094000"))
        assertTrue(ics.contains("LOCATION:公教楼 A101"))
        assertTrue(ics.contains("任课教师: 张教授"))
        assertTrue(ics.contains("TRIGGER:-PT20M"))
    }

    @Test
    fun exportCourse_withoutAlarm_doesNotContainValarm() {
        val course = Course(
            id = 301L,
            name = "无闹钟课程",
            dayOfWeek = 5,
            startSlot = 1,
            endSlot = 1,
            startWeek = 1,
            endWeek = 1
        )
        val ics = IcsExporter.export(semester, listOf(course), slots, testZone, alarmMinutes = 0)
        assertFalse(ics.contains("BEGIN:VALARM"))
    }

    @Test
    fun escapeText_escapesSpecialCharactersProperly() {
        val raw = "高等数学; (上), 附录\\说明\n第1行\r\n第2行"
        val escaped = IcsExporter.escapeText(raw)
        assertEquals("高等数学\\; (上)\\, 附录\\\\说明\\n第1行\\n第2行", escaped)
    }
}
