package com.chen.schedule.island

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IslandConfigTest {

    @Test
    fun `default IslandConfig values match expectations`() {
        val config = IslandConfig()
        assertFalse(config.enabled)
        assertTrue(config.inAppEnabled)
        assertTrue(config.onlyWhenClass)
        assertEquals(30, config.leadMinutes)
        assertEquals(32, config.positionY)
        assertFalse(config.mockMode)
        assertEquals(0, config.mockState)
    }

    @Test
    fun `IslandConfig copy works properly`() {
        val config = IslandConfig().copy(
            enabled = true,
            inAppEnabled = false,
            onlyWhenClass = false,
            leadMinutes = 45,
            positionY = 48,
            mockMode = true,
            mockState = 1
        )
        assertTrue(config.enabled)
        assertFalse(config.inAppEnabled)
        assertFalse(config.onlyWhenClass)
        assertEquals(45, config.leadMinutes)
        assertEquals(48, config.positionY)
        assertTrue(config.mockMode)
        assertEquals(1, config.mockState)
    }
}
