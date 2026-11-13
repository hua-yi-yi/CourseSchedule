package com.chen.schedule.ui.timetable

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import com.chen.schedule.ui.theme.contrastingTextColor
import com.chen.schedule.ui.theme.withTextContrast

internal val ColorScheme.timetableControls: Color
    get() = surfaceContainerLow

internal fun ColorScheme.timetableGrid(opacity: Float): Color = surface.copy(alpha = opacity)

internal data class CourseCardColors(
    val container: Color,
    val accent: Color,
    val supportingText: Color,
    val accentText: Color,
    val badgeText: Color
)

internal fun ColorScheme.courseCardColors(courseColor: Color, base: Color = surface): CourseCardColors {
    val accent = courseColor.compositeOver(base)
    val container = accent.copy(alpha = 0.16f).compositeOver(base)
    return CourseCardColors(
        container = container,
        accent = accent,
        supportingText = onSurfaceVariant.withTextContrast(container, onSurface),
        accentText = accent.withTextContrast(container, onSurface),
        badgeText = contrastingTextColor(accent)
    )
}
