package com.chen.schedule.widget

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.glance.unit.ColorProvider
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
        val mode = config.widgetBackgroundMode
        val opacity = config.widgetOpacity.coerceIn(0f, 1f)
        val contrast = config.widgetTextContrast

        var containerColor: Color? = null
        var bgBitmap: Bitmap? = null

        when (mode) {
            WidgetBackgroundMode.DEFAULT -> {
                containerColor = null
            }
            WidgetBackgroundMode.TRANSPARENT -> {
                containerColor = Color.Transparent
            }
            WidgetBackgroundMode.TRANSLUCENT_LIGHT -> {
                containerColor = Color.White.copy(alpha = opacity * 0.78f)
            }
            WidgetBackgroundMode.TRANSLUCENT_DARK -> {
                containerColor = Color(0xFF1E1E1E).copy(alpha = opacity * 0.78f)
            }
            WidgetBackgroundMode.SOLID_WHITE -> {
                containerColor = Color.White.copy(alpha = opacity)
            }
            WidgetBackgroundMode.SOLID_BLACK -> {
                containerColor = Color(0xFF121212).copy(alpha = opacity)
            }
            WidgetBackgroundMode.APP_IMAGE -> {
                containerColor = Color.Transparent
                val path = config.customBackgroundPath
                bgBitmap = BackgroundFileManager.loadWidgetBitmap(path, 720, 720)
            }
            WidgetBackgroundMode.CUSTOM_IMAGE -> {
                containerColor = Color.Transparent
                val path = config.widgetCustomBgPath ?: config.customBackgroundPath
                bgBitmap = BackgroundFileManager.loadWidgetBitmap(path, 720, 720)
            }
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

        val titleColor = if (isDefault) null else {
            if (shouldUseLightText) ColorProvider(Color(0xFFF8FAFC)) else ColorProvider(Color(0xFF0F172A))
        }

        val subtitleColor = if (isDefault) null else {
            if (shouldUseLightText) ColorProvider(Color(0xFFCBD5E1)) else ColorProvider(Color(0xFF475569))
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
            shouldUseLightText -> ColorProvider(Color(0x4038BDF8))
            else -> ColorProvider(Color(0x300284C7))
        }

        val badgeTextColor = when {
            isDefault -> null
            shouldUseLightText -> ColorProvider(Color(0xFFBAE6FD))
            else -> ColorProvider(Color(0xFF0369A1))
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
