package com.chen.schedule.widget

import androidx.compose.runtime.Composable
import androidx.glance.GlanceTheme
import androidx.glance.material3.ColorProviders
import com.chen.schedule.ui.theme.DarkColorScheme
import com.chen.schedule.ui.theme.LightColorScheme

/**
 * 桌面小组件统一主题:
 * 将小组件的色彩与主页 (LightColorScheme / DarkColorScheme) 完全对齐，保证所有桌面组件具有一致的品牌色调与观感。
 */
val ScheduleGlanceColors = ColorProviders(
    light = LightColorScheme,
    dark = DarkColorScheme
)

@Composable
fun ScheduleGlanceTheme(content: @Composable () -> Unit) {
    GlanceTheme(colors = ScheduleGlanceColors, content = content)
}
