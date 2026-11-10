package com.chen.schedule.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PresetColorSchemeTest {
    @Test
    fun `default preset keeps the selected theme palette`() {
        assertSame(LightColorScheme, resolveScheduleColorScheme(BackgroundPreset.DEFAULT, darkTheme = false))
        assertSame(DarkColorScheme, resolveScheduleColorScheme(BackgroundPreset.DEFAULT, darkTheme = true))
    }

    @Test
    fun `black and charcoal presets use light text even in light mode`() {
        for (preset in listOf(BackgroundPreset.DEEP_BLACK, BackgroundPreset.CHARCOAL)) {
            val scheme = resolveScheduleColorScheme(preset, darkTheme = false)
            assertEquals(DarkColorScheme.onBackground, scheme.onBackground)
            assertEquals(DarkColorScheme.onSurface, scheme.onSurface)
        }
    }

    @Test
    fun `tinted presets carry their hue into cards and borders`() {
        val mint = resolveScheduleColorScheme(BackgroundPreset.MINT_GREEN, darkTheme = false)
        val warm = resolveScheduleColorScheme(BackgroundPreset.WARM_BEIGE, darkTheme = false)

        for (color in listOf(mint.surfaceContainerLow, mint.surfaceContainerHighest, mint.outlineVariant)) {
            assertTrue(color.green > color.red && color.green > color.blue)
        }
        for (color in listOf(warm.surfaceContainerLow, warm.surfaceContainerHighest, warm.outlineVariant)) {
            assertTrue(color.red > color.green && color.green > color.blue)
        }
    }

    @Test
    fun `all nondefault presets keep text readable in both modes`() {
        for (preset in BackgroundPreset.entries.filter { it != BackgroundPreset.DEFAULT }) {
            for (darkTheme in listOf(false, true)) {
                val scheme = resolveScheduleColorScheme(preset, darkTheme)
                val context = "$preset (darkTheme=$darkTheme)"
                val background = checkNotNull(if (darkTheme) preset.darkColor else preset.lightColor)
                assertEquals(Color(background), scheme.background)
                assertTextContrast(context, scheme.onBackground, scheme.background)

                val surfaces = listOf(
                    scheme.background, scheme.surface, scheme.surfaceVariant,
                    scheme.surfaceDim, scheme.surfaceBright, scheme.surfaceContainerLowest,
                    scheme.surfaceContainerLow, scheme.surfaceContainer, scheme.surfaceContainerHigh,
                    scheme.surfaceContainerHighest
                )
                val textColors = listOf(
                    scheme.onSurface, scheme.onSurfaceVariant, scheme.primary,
                    scheme.secondary, scheme.tertiary, scheme.error
                )
                for (surface in surfaces) {
                    for (textColor in textColors) {
                        assertTextContrast(context, textColor, surface)
                    }
                }
                assertTextContrast(context, scheme.onPrimary, scheme.primary)
                assertTextContrast(context, scheme.onSecondary, scheme.secondary)
                assertTextContrast(context, scheme.onTertiary, scheme.tertiary)
                assertTextContrast(context, scheme.onError, scheme.error)
            }
        }
    }

    private fun assertTextContrast(context: String, text: Color, background: Color) {
        val textLuminance = text.luminance()
        val backgroundLuminance = background.luminance()
        val contrast = (maxOf(textLuminance, backgroundLuminance) + 0.05f) /
            (minOf(textLuminance, backgroundLuminance) + 0.05f)
        assertTrue("$context, text $text on $background has contrast $contrast", contrast >= 4.5f)
    }
}
