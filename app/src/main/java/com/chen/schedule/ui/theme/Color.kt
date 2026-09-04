package com.chen.schedule.ui.theme

import androidx.compose.ui.graphics.Color
import com.chen.schedule.domain.model.CoursePalette

/*
 * 品牌色板:晴空蓝(Clear Skies Blue)。
 * 静态色板保证所有设备观感一致,dynamicColor 仅在 Android 12+ 显式开启时生效。
 */

// ---------- Light ----------
val md_theme_light_primary = Color(0xFF1E6BE0)
val md_theme_light_onPrimary = Color(0xFFFFFFFF)
val md_theme_light_primaryContainer = Color(0xFFD5E4FF)
val md_theme_light_onPrimaryContainer = Color(0xFF002F63)

val md_theme_light_secondary = Color(0xFF57606F)
val md_theme_light_onSecondary = Color(0xFFFFFFFF)
val md_theme_light_secondaryContainer = Color(0xFFDAE4F5)
val md_theme_light_onSecondaryContainer = Color(0xFF141C29)

val md_theme_light_tertiary = Color(0xFF6E5D8A)
val md_theme_light_onTertiary = Color(0xFFFFFFFF)
val md_theme_light_tertiaryContainer = Color(0xFFEADDFF)
val md_theme_light_onTertiaryContainer = Color(0xFF21005D)

val md_theme_light_error = Color(0xFFB3261E)
val md_theme_light_onError = Color(0xFFFFFFFF)
val md_theme_light_errorContainer = Color(0xFFF9DEDC)
val md_theme_light_onErrorContainer = Color(0xFF410E0B)

// Neutral surfaces: 背景带一点蓝灰,让白色卡片凸显层次
val md_theme_light_background = Color(0xFFF3F5F9)
val md_theme_light_onBackground = Color(0xFF1A1C1E)
val md_theme_light_surface = Color(0xFFFDFDFF)
val md_theme_light_onSurface = Color(0xFF1A1C1E)
val md_theme_light_surfaceVariant = Color(0xFFE1E6EE)
val md_theme_light_onSurfaceVariant = Color(0xFF45494F)
val md_theme_light_outline = Color(0xFF757A80)
val md_theme_light_outlineVariant = Color(0xFFC5CACF)

val md_theme_light_surfaceContainerLowest = Color(0xFFFFFFFF)
val md_theme_light_surfaceContainerLow = Color(0xFFF7F9FC)
val md_theme_light_surfaceContainer = Color(0xFFF1F4F8)
val md_theme_light_surfaceContainerHigh = Color(0xFFEBEFF4)
val md_theme_light_surfaceContainerHighest = Color(0xFFE3E8EE)

val md_theme_light_inverseSurface = Color(0xFF2F3033)
val md_theme_light_inverseOnSurface = Color(0xFFF1F0F4)
val md_theme_light_inversePrimary = Color(0xFFA9C9FF)
val md_theme_light_scrim = Color(0xFF000000)

// ---------- Dark ----------
val md_theme_dark_primary = Color(0xFFA9C9FF)
val md_theme_dark_onPrimary = Color(0xFF003370)
val md_theme_dark_primaryContainer = Color(0xFF004B87)
val md_theme_dark_onPrimaryContainer = Color(0xFFD5E4FF)

val md_theme_dark_secondary = Color(0xFFBEC7D6)
val md_theme_dark_onSecondary = Color(0xFF283141)
val md_theme_dark_secondaryContainer = Color(0xFF3E485B)
val md_theme_dark_onSecondaryContainer = Color(0xFFDAE4F5)

val md_theme_dark_tertiary = Color(0xFFD2BFEF)
val md_theme_dark_onTertiary = Color(0xFF37295A)
val md_theme_dark_tertiaryContainer = Color(0xFF4E3D72)
val md_theme_dark_onTertiaryContainer = Color(0xFFEADDFF)

val md_theme_dark_error = Color(0xFFF2B8B5)
val md_theme_dark_onError = Color(0xFF601410)
val md_theme_dark_errorContainer = Color(0xFF8C1D18)
val md_theme_dark_onErrorContainer = Color(0xFFF9DEDC)

val md_theme_dark_background = Color(0xFF111417)
val md_theme_dark_onBackground = Color(0xFFE1E2E6)
val md_theme_dark_surface = Color(0xFF111417)
val md_theme_dark_onSurface = Color(0xFFE1E2E6)
val md_theme_dark_surfaceVariant = Color(0xFF44484E)
val md_theme_dark_onSurfaceVariant = Color(0xFFC2C7D0)
val md_theme_dark_outline = Color(0xFF8C9199)
val md_theme_dark_outlineVariant = Color(0xFF44484E)

val md_theme_dark_surfaceContainerLowest = Color(0xFF0C0E11)
val md_theme_dark_surfaceContainerLow = Color(0xFF191C20)
val md_theme_dark_surfaceContainer = Color(0xFF1D2125)
val md_theme_dark_surfaceContainerHigh = Color(0xFF272B30)
val md_theme_dark_surfaceContainerHighest = Color(0xFF32363B)

val md_theme_dark_inverseSurface = Color(0xFFE1E2E6)
val md_theme_dark_inverseOnSurface = Color(0xFF2F3033)
val md_theme_dark_inversePrimary = Color(0xFF1E6BE0)
val md_theme_dark_scrim = Color(0xFF000000)

// Course colors (唯一来源: CoursePalette)
val courseColors: List<Long> = CoursePalette.colors
