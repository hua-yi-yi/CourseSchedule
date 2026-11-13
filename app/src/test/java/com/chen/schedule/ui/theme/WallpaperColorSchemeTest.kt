package com.chen.schedule.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.chen.schedule.domain.model.CoursePalette
import com.chen.schedule.ui.timetable.courseCardColors
import com.chen.schedule.ui.timetable.timetableControls
import com.chen.schedule.ui.timetable.timetableGrid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WallpaperColorSchemeTest {
    @Test
    fun `wallpaper hue changes surfaces and preserves the selected lightness`() {
        val tint = Color(0xFFB25439)
        for (base in listOf(LightColorScheme, DarkColorScheme)) {
            val scheme = wallpaperColorScheme(base, tint)
            assertTrue(scheme.surface.red > scheme.surface.blue)
            assertTrue(kotlin.math.abs(scheme.surface.luminance() - base.surface.luminance()) < 0.1f)
            assertEquals(0.45f, scheme.timetableGrid(0.45f).alpha, 0.001f)
            assertEquals(1f, scheme.timetableControls.alpha, 0.001f)
        }
    }

    @Test
    fun `wallpaper controls remain readable across light dark and saturated images`() {
        val tints = listOf(Color.White, Color.Black, Color.Red, Color.Green, Color.Blue, Color.Magenta, Color.Cyan, Color.Yellow)
        for (base in listOf(LightColorScheme, DarkColorScheme)) {
            for (tint in tints) {
                val scheme = wallpaperColorScheme(base, tint)
                assertContrast(scheme.onBackground, scheme.background)
                assertContrast(scheme.onSurface, scheme.timetableControls)
                assertContrast(scheme.onSurfaceVariant, scheme.timetableControls)
                assertContrast(scheme.primary, scheme.timetableControls)
                assertContrast(scheme.onPrimary, scheme.primary)
            }
        }
    }

    @Test
    fun `course location and badge text keep contrast over stable surfaces`() {
        val themes = BackgroundPreset.entries.flatMap { preset ->
            listOf(resolveScheduleColorScheme(preset, false), resolveScheduleColorScheme(preset, true))
        } + listOf(
            wallpaperColorScheme(LightColorScheme, Color(0xFFECA71B)),
            wallpaperColorScheme(DarkColorScheme, Color(0xFF0AB575))
        )
        val colors = CoursePalette.colors.map { Color(it) } +
            listOf(Color.White, Color.Black, Color.Transparent, Color(0xFF777777))
        for (theme in themes) {
            for (color in colors) {
                val course = theme.courseCardColors(color)
                assertEquals(1f, course.container.alpha, 0.001f)
                assertContrast(theme.onSurface, course.container)
                assertContrast(course.supportingText, course.container)
                assertContrast(course.accentText, course.container)
                assertContrast(course.badgeText, course.accent)
            }
        }
    }

    private fun assertContrast(text: Color, background: Color) {
        val lightness = text.luminance()
        val baseLightness = background.luminance()
        val contrast = (maxOf(lightness, baseLightness) + 0.05f) /
            (minOf(lightness, baseLightness) + 0.05f)
        assertTrue("Text $text on $background has contrast $contrast", contrast >= 4.5f)
    }
}
