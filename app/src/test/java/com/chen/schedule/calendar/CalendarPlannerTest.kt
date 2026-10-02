package com.chen.schedule.calendar

import com.chen.schedule.domain.model.Course
import com.chen.schedule.domain.model.Semester
import com.chen.schedule.domain.model.TimeSlot
import com.chen.schedule.domain.model.WeekType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class CalendarPlannerTest {

    private val testZone = ZoneId.of("Asia/Shanghai")
    // 2026-09-07 是周一
    private val semesterStart = LocalDate.of(2026, 9, 7).atStartOfDay(testZone).toInstant().toEpochMilli()

    private val semester = Semester(
        id = 1L,
        name = "2026秋季学期",
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
    fun planEmptyCourses_returnsEmptyList() {
        val events = CalendarPlanner.planEvents(semester, emptyList(), slots, testZone)
        assertTrue(events.isEmpty())
    }

    @Test
    fun planCourse_expandsOddEvenWeeksCorrectly() {
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

        val events = CalendarPlanner.planEvents(semester, listOf(oddCourse, evenCourse), slots, testZone)
        assertEquals(4, events.size)

        val oddEvents = events.filter { it.courseId == 101L }
        assertEquals(listOf(1, 3), oddEvents.map { it.week })

        val evenEvents = events.filter { it.courseId == 102L }
        assertEquals(listOf(2, 4), evenEvents.map { it.week })
    }

    @Test
    fun planCourse_calculatesAccurateTimestamps() {
        val course = Course(
            id = 201L,
            name = "高等数学",
            classroom = "教一 101",
            teacher = "张老师",
            dayOfWeek = 3, // 周三
            startSlot = 1,
            endSlot = 2,
            startWeek = 2,
            endWeek = 2,
            weekType = WeekType.ALL
        )

        val events = CalendarPlanner.planEvents(semester, listOf(course), slots, testZone)
        assertEquals(1, events.size)

        val event = events.first()
        assertEquals("高等数学", event.title)
        assertEquals("教一 101", event.location)
        assertEquals(2, event.week)
        assertEquals(3, event.dayOfWeek)

        // 第 2 周周三: 2026-09-07 (周一) + 1周 + 2天 = 2026-09-16
        val expectedDate = LocalDate.of(2026, 9, 16)
        val expectedStart = expectedDate.atTime(LocalTime.of(8, 0)).atZone(testZone).toInstant().toEpochMilli()
        val expectedEnd = expectedDate.atTime(LocalTime.of(9, 40)).atZone(testZone).toInstant().toEpochMilli()

        assertEquals(expectedStart, event.startMillis)
        assertEquals(expectedEnd, event.endMillis)
        assertTrue(event.description.contains("任课教师: 张老师"))
        assertTrue(event.description.contains("第 1-2 节"))
        assertTrue(event.description.contains("第 2 周"))
    }

    @Test
    fun planCourse_sortsChronologically() {
        val mondayLater = Course(
            id = 1L,
            name = "下午课",
            dayOfWeek = 1,
            startSlot = 3,
            endSlot = 4,
            startWeek = 1,
            endWeek = 1,
            weekType = WeekType.ALL
        )
        val mondayEarlier = Course(
            id = 2L,
            name = "上午课",
            dayOfWeek = 1,
            startSlot = 1,
            endSlot = 2,
            startWeek = 1,
            endWeek = 1,
            weekType = WeekType.ALL
        )
        val nextWeekCourse = Course(
            id = 3L,
            name = "下周课",
            dayOfWeek = 1,
            startSlot = 1,
            endSlot = 2,
            startWeek = 2,
            endWeek = 2,
            weekType = WeekType.ALL
        )

        val events = CalendarPlanner.planEvents(semester, listOf(mondayLater, nextWeekCourse, mondayEarlier), slots, testZone)
        assertEquals(listOf(2L, 1L, 3L), events.map { it.courseId })
    }

    @Test
    fun planCourse_blankNameDefaultsToFallback() {
        val course = Course(
            id = 501L,
            name = "   ",
            dayOfWeek = 1,
            startSlot = 1,
            endSlot = 1,
            startWeek = 1,
            endWeek = 1,
            weekType = WeekType.ALL
        )
        val events = CalendarPlanner.planEvents(semester, listOf(course), slots, testZone)
        assertEquals(1, events.size)
        assertEquals("未命名课程", events.first().title)
    }

    @Test
    fun planCourse_invalidWeekRangeProducesNoEvents() {
        val course = Course(
            id = 502L,
            name = "无效周次",
            dayOfWeek = 1,
            startSlot = 1,
            endSlot = 2,
            startWeek = 10,
            endWeek = 5,
            weekType = WeekType.ALL
        )
        val events = CalendarPlanner.planEvents(semester, listOf(course), slots, testZone)
        assertTrue(events.isEmpty())
    }

    @Test
    fun planCourse_endBeforeStartCorrectsSafely() {
        val abnormalSlots = listOf(
            TimeSlot(slotNumber = 1, startTime = "10:00", endTime = "09:00") // 异常节次
        )
        val course = Course(
            id = 503L,
            name = "异常节次课程",
            dayOfWeek = 1,
            startSlot = 1,
            endSlot = 1,
            startWeek = 1,
            endWeek = 1,
            weekType = WeekType.ALL
        )
        val events = CalendarPlanner.planEvents(semester, listOf(course), abnormalSlots, testZone)
        assertEquals(1, events.size)
        val event = events.first()
        assertTrue("结束时间必须晚于开始时间", event.endMillis > event.startMillis)
        assertEquals(event.startMillis + 45 * 60 * 1000L, event.endMillis)
    }

    @Test
    fun planCourse_missingSlotsInSchemeDefaultsSafely() {
        val course = Course(
            id = 504L,
            name = "越界节次课程",
            dayOfWeek = 1,
            startSlot = 99,
            endSlot = 99,
            startWeek = 1,
            endWeek = 1,
            weekType = WeekType.ALL
        )
        val events = CalendarPlanner.planEvents(semester, listOf(course), slots, testZone)
        assertEquals(1, events.size)
        assertTrue(events.first().endMillis > events.first().startMillis)
    }
}
