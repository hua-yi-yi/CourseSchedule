package com.chen.schedule.island

import com.chen.schedule.domain.model.Course
import com.chen.schedule.domain.model.Semester
import com.chen.schedule.domain.model.TimeSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class IslandPresentationPolicyTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val date = LocalDate.of(2026, 9, 14)
    private fun at(hour: Int, minute: Int): Long =
        date.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()
    private val semester = Semester(startDate = at(0, 0), totalWeeks = 1)
    private val course = Course(id = 1, name = "数学", dayOfWeek = 1, startSlot = 1, endSlot = 1)
    private val slots = listOf(TimeSlot(slotNumber = 1, startTime = "08:00", endTime = "08:45"))
    private val enabledSystem = IslandConfig(enabled = true, mode = IslandDisplayMode.SYSTEM)
    private fun snapshot(now: Long) = IslandScheduleSnapshot(semester, listOf(course), slots, now, zone)

    @Test
    fun `system display requires a finite current or imminent course`() {
        assertFalse(IslandPresentationPolicy.shouldShowSystem(enabledSystem, snapshot(at(7, 0)).state(30)))
        assertTrue(IslandPresentationPolicy.shouldShowSystem(enabledSystem, snapshot(at(7, 30)).state(30)))
        assertTrue(IslandPresentationPolicy.shouldShowSystem(enabledSystem, snapshot(at(8, 0)).state(30)))
        assertFalse(IslandPresentationPolicy.shouldShowSystem(enabledSystem, snapshot(at(8, 45)).state(30)))
    }

    @Test
    fun `overlay and disabled settings never publish a system course notification`() {
        val ongoing = snapshot(at(8, 15)).state(30)
        assertFalse(IslandPresentationPolicy.shouldShowSystem(enabledSystem.copy(enabled = false), ongoing))
        assertFalse(IslandPresentationPolicy.shouldShowSystem(enabledSystem.copy(mode = IslandDisplayMode.OVERLAY), ongoing))
    }

    @Test
    fun `temporary preview cannot enable a disabled system notification`() {
        val upcoming = snapshot(at(7, 45)).state(30)
        val preview = enabledSystem.copy(enabled = false, mockMode = true, mockState = 0)
        assertFalse(IslandPresentationPolicy.shouldShowSystem(preview, upcoming))
    }

    @Test
    fun `idle system notifications stay hidden even when overlay idle display is allowed`() {
        val idleConfig = enabledSystem.copy(onlyWhenClass = false)
        val idle = snapshot(at(9, 0)).state(30)
        assertFalse(IslandPresentationPolicy.shouldShowSystem(idleConfig, idle))
        assertFalse(IslandPresentationPolicy.shouldShowSystem(idleConfig, IslandState.None))
        assertNull(IslandPresentationPolicy.expiresAt(idle))
        assertNull(IslandPresentationPolicy.expiresAt(IslandState.None))
    }

    @Test
    fun `upcoming ends exactly at class start and ongoing exactly at class end`() {
        val upcoming = snapshot(at(7, 45)).state(30)
        val ongoing = snapshot(at(8, 0)).state(30)
        assertEquals(at(8, 0), IslandPresentationPolicy.expiresAt(upcoming))
        assertEquals(at(8, 45), IslandPresentationPolicy.expiresAt(ongoing))
    }

    @Test
    fun `actual semester bounds suppress stale classes before and after term`() {
        val before = snapshot(at(8, 15)).copy(semester = semester.copy(startDate = at(0, 0) + 7 * 86_400_000L))
        val after = snapshot(at(8, 15)).copy(nowMillis = at(8, 15) + 7 * 86_400_000L)
        listOf(before, after, snapshot(at(8, 15)).copy(semester = null)).forEach { current ->
            assertEquals(IslandState.None, current.state(30))
            assertFalse(IslandPresentationPolicy.shouldShowSystem(enabledSystem, current.state(30)))
        }
    }
}
