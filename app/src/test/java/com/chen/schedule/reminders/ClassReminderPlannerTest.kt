package com.chen.schedule.reminders

import com.chen.schedule.domain.model.Course
import com.chen.schedule.domain.model.TimeSlot
import com.chen.schedule.domain.model.WeekType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ClassReminderPlannerTest {

    private val zone = ZoneId.of("UTC")

    /** 2026-09-14 是周一。 */
    private val today: LocalDate = LocalDate.of(2026, 9, 14)

    private fun at(hour: Int, minute: Int): Long =
        today.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

    private val slots = listOf(
        TimeSlot(slotNumber = 1, startTime = "08:00", endTime = "08:45"),
        TimeSlot(slotNumber = 2, startTime = "08:55", endTime = "09:40"),
        TimeSlot(slotNumber = 3, startTime = "10:00", endTime = "10:45"),
        TimeSlot(slotNumber = 4, startTime = "10:55", endTime = "11:40")
    )

    @Test
    fun `提前10分钟生成今天的提醒`() {
        val courses = listOf(
            Course(name = "高等数学", dayOfWeek = 1, startSlot = 1, endSlot = 2, startWeek = 1, endWeek = 16)
        )
        val plans = ClassReminderPlanner.planForToday(
            at(7, 0), slots, courses, currentWeek = 2, todayDayOfWeek = 1, leadMinutes = 10, zone = zone
        )
        assertEquals(1, plans.size)
        assertEquals(at(7, 50), plans[0].triggerAtMillis)
        assertEquals("高等数学", plans[0].courseName)
        assertEquals("第 1-2 节", plans[0].slotRange)
        assertEquals("08:00", plans[0].startTime)
    }

    @Test
    fun `已过时刻的提醒被过滤`() {
        val courses = listOf(
            Course(name = "第一节课", dayOfWeek = 1, startSlot = 1, endSlot = 1),
            Course(name = "第三节课", dayOfWeek = 1, startSlot = 3, endSlot = 3)
        )
        val plans = ClassReminderPlanner.planForToday(
            at(8, 5), slots, courses, currentWeek = 2, todayDayOfWeek = 1, leadMinutes = 10, zone = zone
        )
        assertEquals(listOf("第三节课"), plans.map { it.courseName })
        assertEquals(at(9, 50), plans[0].triggerAtMillis)
    }

    @Test
    fun `单双周按当前周过滤`() {
        val courses = listOf(
            Course(name = "单周课", dayOfWeek = 1, startSlot = 1, endSlot = 1, weekType = WeekType.ODD),
            Course(name = "双周课", dayOfWeek = 1, startSlot = 2, endSlot = 2, weekType = WeekType.EVEN)
        )
        val plans = ClassReminderPlanner.planForToday(
            at(7, 0), slots, courses, currentWeek = 2, todayDayOfWeek = 1, leadMinutes = 10, zone = zone
        )
        assertEquals(listOf("双周课"), plans.map { it.courseName })
    }

    @Test
    fun `其他星期的课程不提醒`() {
        val courses = listOf(Course(name = "周二课", dayOfWeek = 2, startSlot = 1, endSlot = 1))
        val plans = ClassReminderPlanner.planForToday(
            at(7, 0), slots, courses, currentWeek = 2, todayDayOfWeek = 1, leadMinutes = 10, zone = zone
        )
        assertTrue(plans.isEmpty())
    }

    @Test
    fun `缺失节次的课程跳过`() {
        val courses = listOf(Course(name = "夜课", dayOfWeek = 1, startSlot = 13, endSlot = 13))
        val plans = ClassReminderPlanner.planForToday(
            at(7, 0), slots, courses, currentWeek = 2, todayDayOfWeek = 1, leadMinutes = 10, zone = zone
        )
        assertTrue(plans.isEmpty())
    }

    @Test
    fun `提醒按时间升序排列`() {
        val courses = listOf(
            Course(name = "第三节", dayOfWeek = 1, startSlot = 3, endSlot = 3),
            Course(name = "第一节", dayOfWeek = 1, startSlot = 1, endSlot = 1)
        )
        val plans = ClassReminderPlanner.planForToday(
            at(7, 0), slots, courses, currentWeek = 2, todayDayOfWeek = 1, leadMinutes = 10, zone = zone
        )
        assertEquals(listOf("第一节", "第三节"), plans.map { it.courseName })
        assertTrue(plans.zipWithNext().all { (a, b) -> a.triggerAtMillis <= b.triggerAtMillis })
    }

    @Test
    fun `提前量跨过零点的课程跳过`() {
        val midnightSlots = listOf(TimeSlot(slotNumber = 1, startTime = "00:10", endTime = "00:55"))
        val courses = listOf(Course(name = "早课", dayOfWeek = 1, startSlot = 1, endSlot = 1))
        val plans = ClassReminderPlanner.planForToday(
            at(0, 0), midnightSlots, courses, currentWeek = 2, todayDayOfWeek = 1, leadMinutes = 30, zone = zone
        )
        assertTrue(plans.isEmpty())
    }

    @Test
    fun `单节课程显示为第N节`() {
        val courses = listOf(Course(name = "体育", dayOfWeek = 1, startSlot = 3, endSlot = 3))
        val plans = ClassReminderPlanner.planForToday(
            at(7, 0), slots, courses, currentWeek = 2, todayDayOfWeek = 1, leadMinutes = 10, zone = zone
        )
        assertEquals("第 3 节", plans[0].slotRange)
    }

    @Test
    fun `提醒时刻恰好等于现在时被过滤`() {
        val courses = listOf(Course(name = "恰点课", dayOfWeek = 1, startSlot = 1, endSlot = 1))
        val plans = ClassReminderPlanner.planForToday(
            at(7, 50), slots, courses, currentWeek = 2, todayDayOfWeek = 1, leadMinutes = 10, zone = zone
        )
        assertTrue(plans.isEmpty())
    }
}
