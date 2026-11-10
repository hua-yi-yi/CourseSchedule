package com.chen.schedule.widget

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.glance.unit.ColorProvider
import com.chen.schedule.ui.theme.DarkColorScheme
import com.chen.schedule.ui.theme.LightColorScheme
import com.chen.schedule.ui.theme.ThemeConfig
import com.chen.schedule.ui.theme.ThemePrefs
import com.chen.schedule.ui.theme.WidgetBackgroundMode
import com.chen.schedule.ui.theme.WidgetTextContrast
import com.chen.schedule.util.BackgroundFileManager

/**
 * 桌面小组件外观渲染样式参数。
 */
data class WidgetVisualStyle(
    /** 底板颜色 (若为 null 则使用默认 GlanceTheme.colors.surface / canvas 资源色) */
    val containerColor: Color?,
    /** 图像壁纸 Bitmap (若不为 null 则作为底层背景图裁剪铺满绘制) */
    val backgroundBitmap: Bitmap?,
    /** 主标题颜色 */
    val titleColor: ColorProvider?,
    /** 副标题/次要信息颜色 */
    val subtitleColor: ColorProvider?,
    /** 条目卡片底色 (用于无课提示/今日列表课程背景) */
    val itemCardBg: ColorProvider?,
    /** 胶囊徽标底色 */
    val badgeBg: ColorProvider?,
    /** 胶囊徽标文字颜色 */
    val badgeTextColor: ColorProvider?,
    /** 表头底色 (针对周课表表头栏) */
    val headerBg: ColorProvider?,
    /** 是否使用浅色文字模式 */
    val isLightText: Boolean
)

/**
 * 桌面小组件主题与背景计算器。
 */
object WidgetThemeHelper {

    fun resolve(context: Context, isDarkTheme: Boolean): WidgetVisualStyle {
        val config = ThemePrefs.init(context).currentConfig()
        return resolveWithConfig(context, config, isDarkTheme)
    }

    fun resolveWithConfig(context: Context? = null, config: ThemeConfig, isDarkTheme: Boolean): WidgetVisualStyle {
        val requestedMode = config.widgetBackgroundMode
        val bgBitmap = when (requestedMode) {
            WidgetBackgroundMode.APP_IMAGE ->
                BackgroundFileManager.loadWidgetBitmap(config.customBackgroundPath, 720, 720)
            WidgetBackgroundMode.CUSTOM_IMAGE ->
                BackgroundFileManager.loadWidgetBitmap(config.widgetCustomBgPath, 720, 720)
                    ?: BackgroundFileManager.loadWidgetBitmap(config.customBackgroundPath, 720, 720)
            else -> null
        }
        val missingWallpaper = bgBitmap == null &&
            (requestedMode == WidgetBackgroundMode.APP_IMAGE || requestedMode == WidgetBackgroundMode.CUSTOM_IMAGE)
        val mode = if (missingWallpaper) WidgetBackgroundMode.DEFAULT else requestedMode
        val opacity = config.widgetOpacity.coerceIn(0f, 1f)
        val contrast = if (missingWallpaper) WidgetTextContrast.AUTO else config.widgetTextContrast

        val containerColor = when (mode) {
            WidgetBackgroundMode.DEFAULT -> null
            WidgetBackgroundMode.TRANSPARENT,
            WidgetBackgroundMode.APP_IMAGE,
            WidgetBackgroundMode.CUSTOM_IMAGE -> Color.Transparent
            WidgetBackgroundMode.TRANSLUCENT_LIGHT -> Color.White.copy(alpha = opacity * 0.78f)
            WidgetBackgroundMode.TRANSLUCENT_DARK -> DarkColorScheme.surface.copy(alpha = opacity * 0.78f)
            WidgetBackgroundMode.SOLID_WHITE -> Color.White.copy(alpha = opacity)
            WidgetBackgroundMode.SOLID_BLACK -> Color(0xFF121212).copy(alpha = opacity)
        }

        val shouldUseLightText = when (contrast) {
            WidgetTextContrast.LIGHT -> true
            WidgetTextContrast.DARK -> false
            WidgetTextContrast.AUTO -> when (mode) {
                WidgetBackgroundMode.TRANSLUCENT_DARK,
                WidgetBackgroundMode.SOLID_BLACK -> true
                WidgetBackgroundMode.TRANSLUCENT_LIGHT,
                WidgetBackgroundMode.SOLID_WHITE -> false
                WidgetBackgroundMode.TRANSPARENT,
                WidgetBackgroundMode.APP_IMAGE,
                WidgetBackgroundMode.CUSTOM_IMAGE -> isDarkTheme
                WidgetBackgroundMode.DEFAULT -> isDarkTheme
            }
        }

        val isDefault = mode == WidgetBackgroundMode.DEFAULT && contrast == WidgetTextContrast.AUTO
        val palette = if (shouldUseLightText) DarkColorScheme else LightColorScheme

        val titleColor = if (isDefault) null else {
            ColorProvider(palette.onSurface)
        }

        val subtitleColor = if (isDefault) null else {
            ColorProvider(palette.onSurfaceVariant)
        }

        val itemCardBg = when {
            isDefault -> null
            mode == WidgetBackgroundMode.TRANSPARENT -> {
                if (shouldUseLightText) ColorProvider(Color(0x38FFFFFF)) else ColorProvider(Color(0x22000000))
            }
            shouldUseLightText -> ColorProvider(Color(0x2AFFFFFF))
            else -> ColorProvider(Color(0x1A000000))
        }

        val badgeBg = when {
            isDefault -> null
            else -> ColorProvider(palette.primaryContainer)
        }

        val badgeTextColor = when {
            isDefault -> null
            else -> ColorProvider(palette.onPrimaryContainer)
        }

        val headerBg = when {
            isDefault -> null
            mode == WidgetBackgroundMode.TRANSPARENT -> {
                if (shouldUseLightText) ColorProvider(Color(0x33FFFFFF)) else ColorProvider(Color(0x1F000000))
            }
            shouldUseLightText -> ColorProvider(Color(0x24FFFFFF))
            else -> ColorProvider(Color(0x18000000))
        }

        return WidgetVisualStyle(
            containerColor = containerColor,
            backgroundBitmap = bgBitmap,
            titleColor = titleColor,
            subtitleColor = subtitleColor,
            itemCardBg = itemCardBg,
            badgeBg = badgeBg,
            badgeTextColor = badgeTextColor,
            headerBg = headerBg,
            isLightText = shouldUseLightText
        )
    }
}
