package com.chen.schedule.widget

import androidx.compose.ui.graphics.Color
import com.chen.schedule.ui.theme.ThemeConfig
import com.chen.schedule.ui.theme.WidgetBackgroundMode
import com.chen.schedule.ui.theme.WidgetTextContrast
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetThemeHelperTest {

    @Test
    fun `default mode preserves system colors`() {
        val config = ThemeConfig(
            widgetBackgroundMode = WidgetBackgroundMode.DEFAULT,
            widgetTextContrast = WidgetTextContrast.AUTO
        )

        val lightStyle = WidgetThemeHelper.resolveWithConfig(config = config, isDarkTheme = false)
        assertNull("Container color should be null for DEFAULT mode", lightStyle.containerColor)
        assertNull("Title color should be null to use GlanceTheme", lightStyle.titleColor)
        assertNull("Subtitle color should be null to use GlanceTheme", lightStyle.subtitleColor)
        assertFalse(lightStyle.isLightText)

        val darkStyle = WidgetThemeHelper.resolveWithConfig(config = config, isDarkTheme = true)
        assertNull(darkStyle.containerColor)
        assertTrue(darkStyle.isLightText)
    }

    @Test
    fun `transparent mode sets transparent container and adapts text contrast`() {
        val configAuto = ThemeConfig(
            widgetBackgroundMode = WidgetBackgroundMode.TRANSPARENT,
            widgetTextContrast = WidgetTextContrast.AUTO
        )

        val autoLight = WidgetThemeHelper.resolveWithConfig(config = configAuto, isDarkTheme = false)
        assertEquals(Color.Transparent, autoLight.containerColor)
        assertFalse(autoLight.isLightText)
        assertNotNull(autoLight.titleColor)

        val autoDark = WidgetThemeHelper.resolveWithConfig(config = configAuto, isDarkTheme = true)
        assertEquals(Color.Transparent, autoDark.containerColor)
        assertTrue(autoDark.isLightText)
        assertNotNull(autoDark.titleColor)

        // Force light text on transparent
        val configForceLight = ThemeConfig(
            widgetBackgroundMode = WidgetBackgroundMode.TRANSPARENT,
            widgetTextContrast = WidgetTextContrast.LIGHT
        )
        val forcedLight = WidgetThemeHelper.resolveWithConfig(config = configForceLight, isDarkTheme = false)
        assertTrue(forcedLight.isLightText)

        // Force dark text on transparent
        val configForceDark = ThemeConfig(
            widgetBackgroundMode = WidgetBackgroundMode.TRANSPARENT,
            widgetTextContrast = WidgetTextContrast.DARK
        )
        val forcedDark = WidgetThemeHelper.resolveWithConfig(config = configForceDark, isDarkTheme = true)
        assertFalse(forcedDark.isLightText)
    }

    @Test
    fun `translucent modes configure appropriate default text contrast`() {
        val configLight = ThemeConfig(
            widgetBackgroundMode = WidgetBackgroundMode.TRANSLUCENT_LIGHT,
            widgetOpacity = 0.85f,
            widgetTextContrast = WidgetTextContrast.AUTO
        )
        val styleLight = WidgetThemeHelper.resolveWithConfig(config = configLight, isDarkTheme = true)
        assertFalse("TRANSLUCENT_LIGHT should use dark text in AUTO mode", styleLight.isLightText)
        assertNotNull(styleLight.containerColor)

        val configDark = ThemeConfig(
            widgetBackgroundMode = WidgetBackgroundMode.TRANSLUCENT_DARK,
            widgetOpacity = 0.85f,
            widgetTextContrast = WidgetTextContrast.AUTO
        )
        val styleDark = WidgetThemeHelper.resolveWithConfig(config = configDark, isDarkTheme = false)
        assertTrue("TRANSLUCENT_DARK should use light text in AUTO mode", styleDark.isLightText)
        assertNotNull(styleDark.containerColor)
    }

    @Test
    fun `solid white and solid black configure correct contrast`() {
        val configWhite = ThemeConfig(
            widgetBackgroundMode = WidgetBackgroundMode.SOLID_WHITE,
            widgetOpacity = 1.0f,
            widgetTextContrast = WidgetTextContrast.AUTO
        )
        val styleWhite = WidgetThemeHelper.resolveWithConfig(config = configWhite, isDarkTheme = true)
        assertFalse(styleWhite.isLightText)

        val configBlack = ThemeConfig(
            widgetBackgroundMode = WidgetBackgroundMode.SOLID_BLACK,
            widgetOpacity = 1.0f,
            widgetTextContrast = WidgetTextContrast.AUTO
        )
        val styleBlack = WidgetThemeHelper.resolveWithConfig(config = configBlack, isDarkTheme = false)
        assertTrue(styleBlack.isLightText)
    }

    @Test
    fun `image modes set container color transparent and handle null paths safely`() {
        val configAppImage = ThemeConfig(
            widgetBackgroundMode = WidgetBackgroundMode.APP_IMAGE,
            customBackgroundPath = null
        )
        val styleApp = WidgetThemeHelper.resolveWithConfig(config = configAppImage, isDarkTheme = false)
        assertEquals(Color.Transparent, styleApp.containerColor)
        assertNull(styleApp.backgroundBitmap)

        val configCustomImage = ThemeConfig(
            widgetBackgroundMode = WidgetBackgroundMode.CUSTOM_IMAGE,
            widgetCustomBgPath = null,
            customBackgroundPath = null
        )
        val styleCustom = WidgetThemeHelper.resolveWithConfig(config = configCustomImage, isDarkTheme = true)
        assertEquals(Color.Transparent, styleCustom.containerColor)
        assertNull(styleCustom.backgroundBitmap)
    }

    @Test
    fun `opacity out of bounds is clamped gracefully`() {
        val configNegative = ThemeConfig(
            widgetBackgroundMode = WidgetBackgroundMode.SOLID_WHITE,
            widgetOpacity = -0.5f
        )
        val styleNegative = WidgetThemeHelper.resolveWithConfig(config = configNegative, isDarkTheme = false)
        assertNotNull(styleNegative.containerColor)

        val configOverflow = ThemeConfig(
            widgetBackgroundMode = WidgetBackgroundMode.SOLID_WHITE,
            widgetOpacity = 2.5f
        )
        val styleOverflow = WidgetThemeHelper.resolveWithConfig(config = configOverflow, isDarkTheme = false)
        assertNotNull(styleOverflow.containerColor)
    }
}
