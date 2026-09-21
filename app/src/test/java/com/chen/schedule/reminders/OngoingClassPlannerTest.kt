package com.chen.schedule.reminders

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

class OngoingClassPlannerTest {

    private val zone = ZoneId.of("UTC")
    private val today: LocalDate = LocalDate.of(2026, 9, 14) // 周一

    private fun at(hour: Int, minute: Int): Long =
        today.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

    private val slots = listOf(
        TimeSlot(slotNumber = 1, startTime = "08:00", endTime = "08:45"),
        TimeSlot(slotNumber = 2, startTime = "08:55", endTime = "09:40"),
        TimeSlot(slotNumber = 3, startTime = "10:00", endTime = "10:45"),
        TimeSlot(slotNumber = 4, startTime = "10:55", endTime = "11:40")
    )

    private val testCourses = listOf(
        Course(
            id = 1,
            name = "高等数学",
            teacher = "张教授",
            classroom = "教学楼101",
            dayOfWeek = 1,
            startSlot = 1,
            endSlot = 2,
            startWeek = 1,
            endWeek = 16,
            weekType = WeekType.ALL
        ),
        Course(
            id = 2,
            name = "大学英语",
            teacher = "李老师",
            classroom = "外语楼202",
            dayOfWeek = 1,
            startSlot = 3,
            endSlot = 4,
            startWeek = 1,
            endWeek = 16,
            weekType = WeekType.ODD
        )
    )

    @Test
    fun `上课前时刻判定为无进行中课程`() {
        val result = ClassReminderPlanner.findCurrentOngoingCourse(
            nowMillis = at(7, 59),
            slots = slots,
            courses = testCourses,
            currentWeek = 1,
            todayDayOfWeek = 1,
            zone = zone
        )
        assertNull("07:59 还未上课，应为 null", result)
    }

    @Test
    fun `上课开始时刻与课程中途精准识别进行中`() {
        // 08:00 整上课
        val atStart = ClassReminderPlanner.findCurrentOngoingCourse(
            nowMillis = at(8, 0),
            slots = slots,
            courses = testCourses,
            currentWeek = 1,
            todayDayOfWeek = 1,
            zone = zone
        )
        assertNotNull(atStart)
        assertEquals("高等数学", atStart?.courseName)
        assertEquals("第 1-2 节", atStart?.slotRange)
        assertEquals("08:00", atStart?.startTime)
        assertEquals("09:40", atStart?.endTime)

        // 09:00 (处于第2节课中途)
        val inMiddle = ClassReminderPlanner.findCurrentOngoingCourse(
            nowMillis = at(9, 0),
            slots = slots,
            courses = testCourses,
            currentWeek = 1,
            todayDayOfWeek = 1,
            zone = zone
        )
        assertNotNull(inMiddle)
        assertEquals("高等数学", inMiddle?.courseName)
    }

    @Test
    fun `下课时刻及课间休息判定为无进行中课程`() {
        // 09:40 准时下课
        val atEnd = ClassReminderPlanner.findCurrentOngoingCourse(
            nowMillis = at(9, 40),
            slots = slots,
            courses = testCourses,
            currentWeek = 1,
            todayDayOfWeek = 1,
            zone = zone
        )
        assertNull("09:40 已经下课，应为 null", atEnd)

        // 09:50 课间休息
        val atBreak = ClassReminderPlanner.findCurrentOngoingCourse(
            nowMillis = at(9, 50),
            slots = slots,
            courses = testCourses,
            currentWeek = 1,
            todayDayOfWeek = 1,
            zone = zone
        )
        assertNull("09:50 课间休息，应为 null", atBreak)
    }

    @Test
    fun `单双周与星期过滤验证`() {
        // 当前为第2周(双周)，而大学英语是单周课(ODD)
        val evenWeek = ClassReminderPlanner.findCurrentOngoingCourse(
            nowMillis = at(10, 15),
            slots = slots,
            courses = testCourses,
            currentWeek = 2,
            todayDayOfWeek = 1,
            zone = zone
        )
        assertNull("双周时不应识别单周课程", evenWeek)

        // 当前为第1周(单周)，正常识别大学英语
        val oddWeek = ClassReminderPlanner.findCurrentOngoingCourse(
            nowMillis = at(10, 15),
            slots = slots,
            courses = testCourses,
            currentWeek = 1,
            todayDayOfWeek = 1,
            zone = zone
        )
        assertNotNull(oddWeek)
        assertEquals("大学英语", oddWeek?.courseName)
        assertEquals("外语楼202", oddWeek?.classroom)
    }

    @Test
    fun `规划当天后续进行中事件点`() {
        // 08:30 时，后续事件应包含：
        // 1. 09:40 高等数学下课 (isStart = false)
        // 2. 10:00 大学英语上课 (isStart = true)
        // 3. 11:40 大学英语下课 (isStart = false)
        val events = ClassReminderPlanner.planOngoingEvents(
            nowMillis = at(8, 30),
            slots = slots,
            courses = testCourses,
            currentWeek = 1,
            todayDayOfWeek = 1,
            zone = zone
        )
        assertEquals(3, events.size)

        assertEquals(at(9, 40), events[0].triggerAtMillis)
        assertEquals(false, events[0].isStart)
        assertEquals("高等数学", events[0].info.courseName)

        assertEquals(at(10, 0), events[1].triggerAtMillis)
        assertEquals(true, events[1].isStart)
        assertEquals("大学英语", events[1].info.courseName)

        assertEquals(at(11, 40), events[2].triggerAtMillis)
        assertEquals(false, events[2].isStart)
        assertEquals("大学英语", events[2].info.courseName)
    }
}
