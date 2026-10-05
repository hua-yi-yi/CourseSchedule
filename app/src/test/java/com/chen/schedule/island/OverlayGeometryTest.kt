package com.chen.schedule.island

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayGeometryTest {
    private val portrait = OverlaySafeBounds(0, 80, 1080, 2200)

    @Test
    fun `saved position above the camera area moves below system safe inset`() {
        assertEquals(OverlayPosition(0, 80),
            OverlayGeometry.clampCentered(0, 32, 310, 200, 1080, portrait))
    }

    @Test
    fun `a long drag cannot put either side of the card off screen`() {
        assertEquals(OverlayPosition(385, 500),
            OverlayGeometry.clampCentered(10_000, 500, 310, 200, 1080, portrait))
        assertEquals(OverlayPosition(-385, 500),
            OverlayGeometry.clampCentered(-10_000, 500, 310, 200, 1080, portrait))
    }

    @Test
    fun `expanding a card at the bottom moves the whole expanded card above navigation`() {
        assertEquals(OverlayPosition(385, 1910),
            OverlayGeometry.clampCentered(500, 2180, 310, 290, 1080, portrait))
    }

    @Test
    fun `landscape camera inset is respected on the short screen edge`() {
        val landscape = OverlaySafeBounds(120, 0, 2180, 1020)
        assertEquals(OverlayPosition(-845, 800),
            OverlayGeometry.clampCentered(-2000, 1700, 310, 220, 2240, landscape))
    }

    @Test
    fun `rotation preserves a valid position using the new screen dimensions`() {
        val landscape = OverlaySafeBounds(120, 0, 2180, 1020)
        val result = OverlayGeometry.clampCentered(385, 1910, 310, 290, 2240, landscape)
        assertEquals(OverlayPosition(385, 730), result)
    }

    @Test
    fun `card constrained to a narrow display uses the full safe width`() {
        val narrow = OverlaySafeBounds(12, 30, 228, 290)
        assertEquals(OverlayPosition(0, 30),
            OverlayGeometry.clampCentered(200, 500, 310, 400, 240, narrow))
    }

    @Test
    fun `all bounded drag positions remain inside safe rectangle`() {
        for (x in -1200..1200 step 91) {
            for (y in -100..2600 step 113) {
                val result = OverlayGeometry.clampCentered(x, y, 310, 290, 1080, portrait)
                val left = (1080 - 310) / 2 + result.x
                assertTrue(left >= portrait.left)
                assertTrue(left + 310 <= portrait.right)
                assertTrue(result.y >= portrait.top)
                assertTrue(result.y + 290 <= portrait.bottom)
            }
        }
    }
}
