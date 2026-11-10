package com.chen.schedule.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

internal val LightColorScheme = lightColorScheme(
    primary = md_theme_light_primary,
    onPrimary = md_theme_light_onPrimary,
    primaryContainer = md_theme_light_primaryContainer,
    onPrimaryContainer = md_theme_light_onPrimaryContainer,
    secondary = md_theme_light_secondary,
    onSecondary = md_theme_light_onSecondary,
    secondaryContainer = md_theme_light_secondaryContainer,
    onSecondaryContainer = md_theme_light_onSecondaryContainer,
    tertiary = md_theme_light_tertiary,
    onTertiary = md_theme_light_onTertiary,
    tertiaryContainer = md_theme_light_tertiaryContainer,
    onTertiaryContainer = md_theme_light_onTertiaryContainer,
    error = md_theme_light_error,
    onError = md_theme_light_onError,
    errorContainer = md_theme_light_errorContainer,
    onErrorContainer = md_theme_light_onErrorContainer,
    background = md_theme_light_background,
    onBackground = md_theme_light_onBackground,
    surface = md_theme_light_surface,
    onSurface = md_theme_light_onSurface,
    surfaceVariant = md_theme_light_surfaceVariant,
    onSurfaceVariant = md_theme_light_onSurfaceVariant,
    outline = md_theme_light_outline,
    outlineVariant = md_theme_light_outlineVariant,
    surfaceContainerLowest = md_theme_light_surfaceContainerLowest,
    surfaceContainerLow = md_theme_light_surfaceContainerLow,
    surfaceContainer = md_theme_light_surfaceContainer,
    surfaceContainerHigh = md_theme_light_surfaceContainerHigh,
    surfaceContainerHighest = md_theme_light_surfaceContainerHighest,
    inverseSurface = md_theme_light_inverseSurface,
    inverseOnSurface = md_theme_light_inverseOnSurface,
    inversePrimary = md_theme_light_inversePrimary,
    scrim = md_theme_light_scrim
)

internal val DarkColorScheme = darkColorScheme(
    primary = md_theme_dark_primary,
    onPrimary = md_theme_dark_onPrimary,
    primaryContainer = md_theme_dark_primaryContainer,
    onPrimaryContainer = md_theme_dark_onPrimaryContainer,
    secondary = md_theme_dark_secondary,
    onSecondary = md_theme_dark_onSecondary,
    secondaryContainer = md_theme_dark_secondaryContainer,
    onSecondaryContainer = md_theme_dark_onSecondaryContainer,
    tertiary = md_theme_dark_tertiary,
    onTertiary = md_theme_dark_onTertiary,
    tertiaryContainer = md_theme_dark_tertiaryContainer,
    onTertiaryContainer = md_theme_dark_onTertiaryContainer,
    error = md_theme_dark_error,
    onError = md_theme_dark_onError,
    errorContainer = md_theme_dark_errorContainer,
    onErrorContainer = md_theme_dark_onErrorContainer,
    background = md_theme_dark_background,
    onBackground = md_theme_dark_onBackground,
    surface = md_theme_dark_surface,
    onSurface = md_theme_dark_onSurface,
    surfaceVariant = md_theme_dark_surfaceVariant,
    onSurfaceVariant = md_theme_dark_onSurfaceVariant,
    outline = md_theme_dark_outline,
    outlineVariant = md_theme_dark_outlineVariant,
    surfaceContainerLowest = md_theme_dark_surfaceContainerLowest,
    surfaceContainerLow = md_theme_dark_surfaceContainerLow,
    surfaceContainer = md_theme_dark_surfaceContainer,
    surfaceContainerHigh = md_theme_dark_surfaceContainerHigh,
    surfaceContainerHighest = md_theme_dark_surfaceContainerHighest,
    inverseSurface = md_theme_dark_inverseSurface,
    inverseOnSurface = md_theme_dark_inverseOnSurface,
    inversePrimary = md_theme_dark_inversePrimary,
    scrim = md_theme_dark_scrim
)

// At this luminance, black and white have the same contrast against the background.
private const val DARK_BACKGROUND_THRESHOLD = 0.179f

internal fun resolveScheduleColorScheme(
    preset: BackgroundPreset,
    darkTheme: Boolean,
    baseScheme: (Boolean) -> ColorScheme = { if (it) DarkColorScheme else LightColorScheme }
): ColorScheme {
    val presetBackground = if (darkTheme) preset.darkColor else preset.lightColor
    val background = presetBackground?.let { Color(it) } ?: return baseScheme(darkTheme)
    val useDarkPalette = background.luminance() < DARK_BACKGROUND_THRESHOLD
    val scheme = baseScheme(useDarkPalette)
    val presetSurface = if (darkTheme) preset.darkSurface else preset.lightSurface
    val surface = presetSurface?.let { Color(it) } ?: background
    val shade = if (useDarkPalette) Color.White else Color.Black
    val oppositeShade = if (useDarkPalette) Color.Black else Color.White
    val surfaceVariant = surface.tintedWith(shade, 0.10f)
    val byLuminance = compareBy<Color> { it.luminance() }
    val limitingSurface = if (useDarkPalette) {
        maxOf(background, surfaceVariant, byLuminance)
    } else {
        minOf(background, surfaceVariant, byLuminance)
    }
    val primary = scheme.primary.withTextContrast(limitingSurface, scheme.onSurface)

    return scheme.copy(
        background = background,
        surface = surface,
        surfaceVariant = surfaceVariant,
        surfaceDim = if (useDarkPalette) surface else surfaceVariant,
        surfaceBright = if (useDarkPalette) surfaceVariant else surface,
        surfaceContainerLowest = surface.tintedWith(oppositeShade, 0.10f),
        surfaceContainerLow = surface.tintedWith(shade, 0.02f),
        surfaceContainer = surface.tintedWith(shade, 0.04f),
        surfaceContainerHigh = surface.tintedWith(shade, 0.06f),
        surfaceContainerHighest = surface.tintedWith(shade, 0.08f),
        outline = surface.tintedWith(scheme.onSurface, 0.62f),
        outlineVariant = surface.tintedWith(scheme.onSurface, 0.18f),
        primary = primary,
        surfaceTint = primary,
        secondary = scheme.secondary.withTextContrast(limitingSurface, scheme.onSurface),
        tertiary = scheme.tertiary.withTextContrast(limitingSurface, scheme.onSurface),
        error = scheme.error.withTextContrast(limitingSurface, scheme.onSurface)
    )
}

private fun Color.tintedWith(tint: Color, amount: Float): Color =
    tint.copy(alpha = amount).compositeOver(this)

private fun Color.withTextContrast(background: Color, onSurface: Color): Color {
    val backgroundLuminance = background.luminance()
    fun contrast(color: Color): Float {
        val luminance = color.luminance()
        return (maxOf(luminance, backgroundLuminance) + 0.05f) /
            (minOf(luminance, backgroundLuminance) + 0.05f)
    }

    if (contrast(this) >= 4.5f) return this

    // Find the smallest tint toward the palette's normal text color that reaches 4.5:1.
    var lower = 0f
    var upper = 1f
    repeat(8) {
        val amount = (lower + upper) / 2f
        if (contrast(tintedWith(onSurface, amount)) >= 4.5f) {
            upper = amount
        } else {
            lower = amount
        }
    }
    return tintedWith(onSurface, upper)
}

@Composable
fun ScheduleTheme(
    themeConfig: ThemeConfig = ThemeConfig(),
    darkTheme: Boolean = when (themeConfig.themeMode) {
        ThemePrefs.THEME_MODE_SYSTEM -> isSystemInDarkTheme()
        ThemePrefs.THEME_MODE_DARK -> true
        else -> false
    },
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val preset = themeConfig.backgroundPreset
    val colorScheme = remember(context, configuration, dynamicColor, darkTheme, preset) {
        resolveScheduleColorScheme(preset, darkTheme) { useDarkPalette ->
            when {
                dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                    if (useDarkPalette) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
                }
                useDarkPalette -> DarkColorScheme
                else -> LightColorScheme
            }
        }
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            // Edge-to-edge: 透明系统栏,仅调整图标明暗
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            val lightBars = colorScheme.background.luminance() >= DARK_BACKGROUND_THRESHOLD
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = lightBars
            insetsController.isAppearanceLightNavigationBars = lightBars
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = ScheduleShapes,
        content = content
    )
}
