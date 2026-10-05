package com.chen.schedule.island

import com.chen.schedule.domain.model.Course
import com.chen.schedule.domain.model.Semester
import com.chen.schedule.domain.model.TimeSlot
import com.chen.schedule.domain.model.WeekType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class IslandEventPlannerTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val monday = LocalDate.of(2026, 9, 14)
    private fun at(hour: Int, minute: Int, date: LocalDate = monday): Long =
        date.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()
    private val semester = Semester(startDate = at(0, 0), totalWeeks = 2)
    private val slots = listOf(
        TimeSlot(slotNumber = 1, startTime = "08:00", endTime = "08:45"),
        TimeSlot(slotNumber = 2, startTime = "08:55", endTime = "09:40"),
        TimeSlot(slotNumber = 3, startTime = "09:40", endTime = "10:25")
    )
    private val first = Course(id = 1, name = "数学", dayOfWeek = 1, startSlot = 1, endSlot = 2)
    private fun snapshot(now: Long, courses: List<Course> = listOf(first)) =
        IslandScheduleSnapshot(semester, courses, slots, now, zone)

    @Test
    fun `one course schedules only lead start and end boundaries`() {
        assertEquals(listOf(at(7, 30), at(8, 0), at(9, 40)),
            IslandEventPlanner.boundaries(snapshot(at(7, 0)), 30))
    }

    @Test
    fun `a delivered boundary is excluded and current state advances at exact times`() {
        val lead = snapshot(at(7, 30))
        assertTrue(lead.state(30) is IslandState.Upcoming)
        assertEquals(listOf(at(8, 0), at(9, 40)), IslandEventPlanner.boundaries(lead, 30))
        val start = snapshot(at(8, 0))
        assertTrue(start.state(30) is IslandState.Ongoing)
        assertEquals(listOf(at(9, 40)), IslandEventPlanner.boundaries(start, 30))
        val end = snapshot(at(9, 40))
        assertTrue(end.state(30) is IslandState.Idle)
        assertTrue(IslandEventPlanner.boundaries(end, 30).isEmpty())
    }

    @Test
    fun `adjacent courses deduplicate shared end and start boundaries`() {
        val next = first.copy(id = 2, name = "英语", startSlot = 3, endSlot = 3)
        assertEquals(listOf(at(7, 30), at(8, 0), at(9, 10), at(9, 40), at(10, 25)),
            IslandEventPlanner.boundaries(snapshot(at(7, 0), listOf(next, first, first)), 30))
        val atHandoff = snapshot(at(9, 40), listOf(first, next))
        assertEquals(2L, (atHandoff.state(30) as IslandState.Ongoing).course.id)
    }

    @Test
    fun `late legacy start and end events recompute the next course without rearming expired boundaries`() {
        val next = first.copy(id = 2, name = "英语", startSlot = 3, endSlot = 3)
        val oldEvents = com.chen.schedule.reminders.ClassReminderPlanner.planOngoingEvents(
            at(7, 0), slots, listOf(first), 1, 1, zone)
        assertEquals(2, oldEvents.size)
        // Both old broadcasts arrive ten minutes after the first class ended.
        val deliveredAt = at(9, 50)
        oldEvents.forEach { oldEvent ->
            assertTrue(oldEvent.triggerAtMillis < deliveredAt)
            assertEquals(first.name, oldEvent.info.courseName)
            val latest = snapshot(deliveredAt, listOf(first, next))
            val state = latest.state(30) as IslandState.Ongoing
            assertEquals(next.id, state.course.id)
            assertEquals(at(10, 25), IslandPresentationPolicy.expiresAt(state))
            assertTrue(IslandPresentationPolicy.shouldShowSystem(IslandConfig(enabled = true), state))
            assertEquals(listOf(at(10, 25)), IslandEventPlanner.boundaries(latest, 30))
        }
    }

    @Test
    fun `planning uses actual semester week and weekday`() {
        val odd = first.copy(weekType = WeekType.ODD)
        val even = first.copy(id = 2, startSlot = 3, endSlot = 3, weekType = WeekType.EVEN)
        val tuesday = first.copy(id = 3, dayOfWeek = 2)
        val secondWeek = snapshot(at(7, 0), listOf(odd, even, tuesday)).copy(
            semester = semester.copy(startDate = at(0, 0, monday.minusWeeks(1)))
        )
        assertEquals(2, secondWeek.activeWeek)
        assertEquals(listOf(at(9, 10), at(9, 40), at(10, 25)),
            IslandEventPlanner.boundaries(secondWeek, 30))
    }

    @Test
    fun `no semester or dates outside semester produce no alarms`() {
        val original = snapshot(at(7, 0))
        listOf(
            original.copy(semester = null),
            original.copy(nowMillis = at(7, 0, monday.minusDays(1))),
            original.copy(nowMillis = at(7, 0, monday.plusWeeks(2)))
        ).forEach {
            assertEquals(IslandState.None, it.state(30))
            assertTrue(IslandEventPlanner.boundaries(it, 30).isEmpty())
        }
    }

    @Test
    fun `invalid or missing course times cannot create wakeups`() {
        val invalid = snapshot(at(7, 0)).copy(slots = listOf(
            TimeSlot(slotNumber = 1, startTime = "invalid", endTime = "08:45"),
            TimeSlot(slotNumber = 2, startTime = "08:55", endTime = "09:40")
        ))
        assertTrue(IslandEventPlanner.boundaries(invalid, 30).isEmpty())
        assertTrue(IslandEventPlanner.boundaries(invalid.copy(slots = emptyList()), 30).isEmpty())
        val backwards = snapshot(at(7, 0)).copy(slots = listOf(
            TimeSlot(slotNumber = 1, startTime = "09:00", endTime = "08:00")
        ), courses = listOf(first.copy(endSlot = 1)))
        assertTrue(IslandEventPlanner.boundaries(backwards, 30).isEmpty())
    }

    @Test
    fun `cross midnight lead is clamped to current day without rearming past alarms`() {
        val early = snapshot(at(0, 0)).copy(slots = listOf(
            TimeSlot(slotNumber = 1, startTime = "00:10", endTime = "00:55")
        ), courses = listOf(first.copy(endSlot = 1)))
        assertTrue(early.state(30) is IslandState.Upcoming)
        assertEquals(listOf(at(0, 10), at(0, 55)), IslandEventPlanner.boundaries(early, 30))
    }

    @Test
    fun `device timezone selects local weekday rather than UTC date`() {
        // 2026-09-13 16:15 UTC is already Monday in Shanghai.
        val early = snapshot(at(0, 15)).copy(slots = listOf(
            TimeSlot(slotNumber = 1, startTime = "00:20", endTime = "01:00")
        ), courses = listOf(first.copy(endSlot = 1)))
        assertEquals(1, early.dayOfWeek)
        assertEquals(1, early.activeWeek)
        assertEquals(listOf(at(0, 20), at(1, 0)), IslandEventPlanner.boundaries(early, 30))
    }

    @Test
    fun `deleting or moving courses invalidates old planned boundaries`() {
        assertTrue(IslandEventPlanner.boundaries(snapshot(at(7, 0), emptyList()), 30).isEmpty())
        val moved = first.copy(startSlot = 3, endSlot = 3)
        assertEquals(listOf(at(9, 10), at(9, 40), at(10, 25)),
            IslandEventPlanner.boundaries(snapshot(at(7, 0), listOf(moved)), 30))
    }
}
