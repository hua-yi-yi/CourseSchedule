package com.chen.schedule.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TimeSchemeTemplatesShiftTest {

    @Test
    fun `平移零分钟时返回基准序列`() {
        assertEquals(
            TimeSchemeTemplates.SUMMER_START_TIMES,
            TimeSchemeTemplates.shiftedStartTimes(TimeSchemeTemplates.SUMMER_START_TIMES, "08:00")
        )
    }

    @Test
    fun `整体平移十分钟`() {
        val shifted = TimeSchemeTemplates.shiftedStartTimes(TimeSchemeTemplates.SUMMER_START_TIMES, "08:10")
        assertEquals("08:10", shifted.first())
        assertEquals("09:05", shifted[1])
        assertEquals("10:10", shifted[2])
    }

    @Test
    fun `支持负向平移`() {
        val shifted = TimeSchemeTemplates.shiftedStartTimes(TimeSchemeTemplates.WINTER_START_TIMES, "07:50")
        assertEquals("07:50", shifted.first())
        assertEquals("08:45", shifted[1])
    }

    @Test
    fun `平移超过一天的节次按23点59分截断`() {
        val shifted = TimeSchemeTemplates.shiftedStartTimes(TimeSchemeTemplates.SUMMER_START_TIMES, "23:00")
        assertEquals("23:00", shifted.first())
        assertEquals("23:55", shifted[1])
        // 之后的节次全部溢出,统一截断到 23:59
        shifted.drop(2).forEach { assertEquals("23:59", it) }
    }

    @Test
    fun `平移后配合 slotsOf 保持每节45分钟`() {
        val shifted = TimeSchemeTemplates.shiftedStartTimes(TimeSchemeTemplates.SUMMER_START_TIMES, "08:30")
        val slots = TimeSchemeTemplates.slotsOf(shifted)
        assertEquals(shifted.size, slots.size)
        slots.forEachIndexed { index, slot ->
            assertEquals(index + 1, slot.slotNumber)
            val start = TimeSchemeTemplates.toMinutes(slot.startTime)!!
            val end = TimeSchemeTemplates.toMinutes(slot.endTime)!!
            if (slot.endTime == "23:59") {
                // 截断节次允许不足 45 分钟
                assert(end >= start)
            } else {
                assertEquals(45, end - start)
            }
        }
    }

    @Test
    fun `非法时间返回null`() {
        assertNull(TimeSchemeTemplates.toMinutes("8点"))
        assertNull(TimeSchemeTemplates.toMinutes("25:00"))
        assertNull(TimeSchemeTemplates.toMinutes("08:60"))
        assertNull(TimeSchemeTemplates.toMinutes(""))
    }
}
