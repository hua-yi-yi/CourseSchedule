package com.chen.schedule.island

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IslandPreferenceMigrationTest {
    @Test
    fun `an existing overlay selection takes precedence over reminder preferences`() {
        listOf(false, true).forEach { reminders ->
            listOf(false, true).forEach { ongoing ->
                val config = IslandPreferenceMigration.migrate(true, reminders, ongoing)
                assertEquals(IslandDisplayMode.OVERLAY, config.mode)
                assertTrue(config.enabled)
            }
        }
    }

    @Test
    fun `only an enabled legacy ongoing board becomes enabled system display`() {
        listOf(false, true).forEach { reminders ->
            listOf(false, true).forEach { ongoing ->
                val config = IslandPreferenceMigration.migrate(false, reminders, ongoing)
                assertEquals(IslandDisplayMode.SYSTEM, config.mode)
                assertEquals(reminders && ongoing, config.enabled)
            }
        }
    }

    @Test
    fun `fresh users begin with disabled system display and no preview`() {
        val config = IslandPreferenceMigration.migrate(false, false, true)
        assertEquals(IslandDisplayMode.SYSTEM, config.mode)
        assertFalse(config.enabled)
        assertFalse(config.mockMode)
        assertTrue(config.inAppEnabled)
    }

    @Test
    fun `legacy preview enabled flag cannot become a permanent overlay`() {
        val previewOnly = IslandPreferenceMigration.migrate(true, false, true, legacyMockMode = true)
        assertEquals(IslandDisplayMode.SYSTEM, previewOnly.mode)
        assertFalse(previewOnly.enabled)
        assertFalse(previewOnly.mockMode)
        val previewWithBoard = IslandPreferenceMigration.migrate(true, true, true, legacyMockMode = true)
        assertEquals(IslandDisplayMode.SYSTEM, previewWithBoard.mode)
        assertTrue(previewWithBoard.enabled)
        assertFalse(previewWithBoard.mockMode)
    }
}
