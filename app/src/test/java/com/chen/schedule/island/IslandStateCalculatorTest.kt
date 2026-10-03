package com.chen.schedule.island

import com.chen.schedule.domain.model.Course
import com.chen.schedule.domain.model.TimeSlot
import com.chen.schedule.domain.model.WeekType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class IslandStateCalculatorTest {

    private val zone = ZoneId.of("UTC")
    private val today: LocalDate = LocalDate.of(2026, 9, 14) // 周一

    private fun at(hour: Int, minute: Int): Long =
        today.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

    private val slots = listOf(
        TimeSlot(slotNumber = 1, startTime = "08:00", endTime = "08:45"),
        TimeSlot(slotNumber = 2, startTime = "08:55", endTime = "09:40"),
        TimeSlot(slotNumber = 3, startTime = "10:00", endTime = "10:45"),
        TimeSlot(slotNumber = 4, startTime = "10:55", endTime = "11:40"),
        TimeSlot(slotNumber = 5, startTime = "14:00", endTime = "14:45"),
        TimeSlot(slotNumber = 6, startTime = "14:55", endTime = "15:40")
    )

    private val testCourses = listOf(
        Course(
            id = 1,
            name = "高等数学",
            teacher = "张教授",
            classroom = "公教楼B204",
            dayOfWeek = 1,
            startSlot = 1,
            endSlot = 2,
            startWeek = 1,
            endWeek = 16,
            weekType = WeekType.ALL,
            color = 0xFF3B82F6
        ),
        Course(
            id = 2,
            name = "大学英语",
            teacher = "李老师",
            classroom = "外语楼101",
            dayOfWeek = 1,
            startSlot = 3,
            endSlot = 4,
            startWeek = 1,
            endWeek = 16,
            weekType = WeekType.ODD,
            color = 0xFF10B981
        ),
        Course(
            id = 3,
            name = "体育课",
            teacher = "王教练",
            classroom = "体育馆",
            dayOfWeek = 1,
            startSlot = 5,
            endSlot = 5,
            startWeek = 1,
            endWeek = 16,
            weekType = WeekType.ALL,
            color = 0xFFF59E0B
        )
    )

    @Test
    fun `空课程或空节次返回 None`() {
        val r1 = IslandStateCalculator.calculate(at(8, 0), emptyList(), testCourses, 1, 1, 30, zone)
        assertEquals(IslandState.None, r1)

        val r2 = IslandStateCalculator.calculate(at(8, 0), slots, emptyList(), 1, 1, 30, zone)
        assertEquals(IslandState.None, r2)
    }

    @Test
    fun `当天无课程返回今日无课 Idle 状态`() {
        // 周日(7)没有排课
        val r = IslandStateCalculator.calculate(at(9, 0), slots, testCourses, 1, 7, 30, zone)
        assertTrue(r is IslandState.Idle)
        val idle = r as IslandState.Idle
        assertEquals(0, idle.todayTotalCourses)
        assertEquals("今日无课", idle.compactText)
    }

    @Test
    fun `上课前超过预警时间处于 Idle 状态`() {
        // 07:00，距离 08:00 第一节课还有 60 分钟 (leadMinutes = 30)
        val r = IslandStateCalculator.calculate(at(7, 0), slots, testCourses, 1, 1, 30, zone)
        assertTrue("7点应为未进入预警期的 Idle", r is IslandState.Idle)
        val idle = r as IslandState.Idle
        assertEquals("高等数学", idle.nextCourse?.name)
        assertEquals("08:00", idle.nextCourseStartTime)
        assertTrue(idle.subText.contains("60"))
    }

    @Test
    fun `上课前进入预警时间精准识别 Upcoming`() {
        // 07:45，距离 08:00 还有 15 分钟 (leadMinutes = 30)
        val r = IslandStateCalculator.calculate(at(7, 45), slots, testCourses, 1, 1, 30, zone)
        assertTrue("7:45 应识别为 Upcoming", r is IslandState.Upcoming)
        val upcoming = r as IslandState.Upcoming
        assertEquals("高等数学", upcoming.courseName)
        assertEquals("公教楼B204", upcoming.classroom)
        assertEquals(15, upcoming.minutesUntilStart)
        assertEquals("第1-2 节", upcoming.slotRange)
        assertEquals("15分后 · 高等数学", upcoming.compactText)
        assertEquals("@公教楼B204 · 08:00上课", upcoming.subText)
    }

    @Test
    fun `上课开始整点与中途精准识别 Ongoing 并计算进度与剩余分钟`() {
        // 08:00 整上课
        val rStart = IslandStateCalculator.calculate(at(8, 0), slots, testCourses, 1, 1, 30, zone)
        assertTrue(rStart is IslandState.Ongoing)
        val onStart = rStart as IslandState.Ongoing
        assertEquals("高等数学", onStart.courseName)
        assertEquals(100, onStart.totalMinutes) // 08:00 - 09:40 共 100 分钟
        assertEquals(100, onStart.remainingMinutes)
        assertEquals(0f, onStart.progress, 0.01f)

        // 08:50 (处于第 1-2 节课之间)
        val rMid = IslandStateCalculator.calculate(at(8, 50), slots, testCourses, 1, 1, 30, zone)
        assertTrue(rMid is IslandState.Ongoing)
        val onMid = rMid as IslandState.Ongoing
        assertEquals("高等数学", onMid.courseName)
        assertEquals(50, onMid.remainingMinutes)
        assertEquals(0.5f, onMid.progress, 0.01f)
        assertEquals("高等数学 · 剩50分", onMid.compactText)
        assertEquals("@公教楼B204 · 09:40下课", onMid.subText)
    }

    @Test
    fun `课间休息精准衔接下一门课的 Upcoming 预警`() {
        // 09:40 高数下课，10:00 英语上课。
        // 09:45 课间休息，距离 10:00 英语还有 15 分钟 (leadMinutes = 30)
        val r = IslandStateCalculator.calculate(at(9, 45), slots, testCourses, 1, 1, 30, zone)
        assertTrue(r is IslandState.Upcoming)
        val upcoming = r as IslandState.Upcoming
        assertEquals("大学英语", upcoming.courseName)
        assertEquals(15, upcoming.minutesUntilStart)
        assertEquals("15分后 · 大学英语", upcoming.compactText)
    }

    @Test
    fun `单双周智能过滤`() {
        // 大学英语为单周(ODD)。
        // 第 2 周(双周)的 09:45，不应识别大学英语
        val rEven = IslandStateCalculator.calculate(at(9, 45), slots, testCourses, 2, 1, 30, zone)
        // 第2周当天后续只有下午 14:00 的体育课，距离 14:00 超过 30 分钟 -> Idle
        assertTrue(rEven is IslandState.Idle)
        val idle = rEven as IslandState.Idle
        assertEquals("体育课", idle.nextCourse?.name)
    }

    @Test
    fun `全天所有课程结束后状态为 Idle 并显示完成数`() {
        // 16:00 所有课均已下课 (最后节体育课 14:45 结束)
        val r = IslandStateCalculator.calculate(at(16, 0), slots, testCourses, 1, 1, 30, zone)
        assertTrue(r is IslandState.Idle)
        val idle = r as IslandState.Idle
        assertNull(idle.nextCourse)
        assertEquals("今日课程已结束", idle.compactText)
        assertEquals(3, idle.todayTotalCourses)
        assertEquals(3, idle.finishedCourses)
    }
}
