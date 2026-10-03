package com.chen.schedule.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeConfigTest {

    @Test
    fun `default ThemeConfig has expected initial values`() {
        val config = ThemeConfig()

        assertEquals(ThemePrefs.THEME_MODE_LIGHT, config.themeMode)
        assertEquals(BackgroundPreset.DEFAULT, config.backgroundPreset)

        // Custom background defaults
        assertNull(config.customBackgroundPath)
        assertEquals(0.25f, config.backgroundDim, 0.001f)
        assertEquals(0.85f, config.cardAlpha, 0.001f)

        // Widget background defaults
        assertEquals(WidgetBackgroundMode.DEFAULT, config.widgetBackgroundMode)
        assertNull(config.widgetCustomBgPath)
        assertEquals(1.0f, config.widgetOpacity, 0.001f)
        assertEquals(WidgetTextContrast.AUTO, config.widgetTextContrast)
    }

    @Test
    fun `ThemeConfig copy works for new background options`() {
        val initial = ThemeConfig()
        val modified = initial.copy(
            customBackgroundPath = "/data/user/0/com.chen.schedule/files/backgrounds/app_bg.jpg",
            backgroundDim = 0.30f,
            cardAlpha = 0.70f,
            widgetBackgroundMode = WidgetBackgroundMode.TRANSLUCENT_DARK,
            widgetCustomBgPath = "/data/user/0/com.chen.schedule/files/backgrounds/widget_bg.jpg",
            widgetOpacity = 0.80f,
            widgetTextContrast = WidgetTextContrast.LIGHT
        )

        assertEquals("/data/user/0/com.chen.schedule/files/backgrounds/app_bg.jpg", modified.customBackgroundPath)
        assertEquals(0.30f, modified.backgroundDim, 0.001f)
        assertEquals(0.70f, modified.cardAlpha, 0.001f)
        assertEquals(WidgetBackgroundMode.TRANSLUCENT_DARK, modified.widgetBackgroundMode)
        assertEquals("/data/user/0/com.chen.schedule/files/backgrounds/widget_bg.jpg", modified.widgetCustomBgPath)
        assertEquals(0.80f, modified.widgetOpacity, 0.001f)
        assertEquals(WidgetTextContrast.LIGHT, modified.widgetTextContrast)
    }

    @Test
    fun `WidgetBackgroundMode enum has all documented variants`() {
        val names = WidgetBackgroundMode.entries.map { it.name }
        assertTrue(names.contains("DEFAULT"))
        assertTrue(names.contains("TRANSPARENT"))
        assertTrue(names.contains("TRANSLUCENT_LIGHT"))
        assertTrue(names.contains("TRANSLUCENT_DARK"))
        assertTrue(names.contains("SOLID_WHITE"))
        assertTrue(names.contains("SOLID_BLACK"))
        assertTrue(names.contains("APP_IMAGE"))
        assertTrue(names.contains("CUSTOM_IMAGE"))

        assertEquals(WidgetBackgroundMode.DEFAULT, WidgetBackgroundMode.fromId(0))
        assertEquals(WidgetBackgroundMode.TRANSPARENT, WidgetBackgroundMode.fromId(1))
        assertEquals(WidgetBackgroundMode.CUSTOM_IMAGE, WidgetBackgroundMode.fromId(7))
        assertEquals(WidgetBackgroundMode.DEFAULT, WidgetBackgroundMode.fromId(999))
    }

    @Test
    fun `WidgetTextContrast enum supports lookup by id`() {
        assertEquals(WidgetTextContrast.AUTO, WidgetTextContrast.fromId(0))
        assertEquals(WidgetTextContrast.LIGHT, WidgetTextContrast.fromId(1))
        assertEquals(WidgetTextContrast.DARK, WidgetTextContrast.fromId(2))
        assertEquals(WidgetTextContrast.AUTO, WidgetTextContrast.fromId(999))
    }
}
