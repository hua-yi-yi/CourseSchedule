package com.chen.schedule.ui.theme

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 预设背景色调(支持明暗自适应与用户自选)。
 */
enum class BackgroundPreset(
    val id: Int,
    val label: String,
    val lightColor: Long?,
    val darkColor: Long?,
    val lightSurface: Long? = null,
    val darkSurface: Long? = null
) {
    DEFAULT(0, "默认自适应", null, null),
    PURE_WHITE(1, "纯净雪白", 0xFFFFFFFF, 0xFF121212, 0xFFF8FAFC, 0xFF1E1E1E),
    WARM_BEIGE(2, "护眼暖杏", 0xFFFAF7F0, 0xFF1C1A17, 0xFFF5EFE4, 0xFF282520),
    ICE_BLUE(3, "清新冰蓝", 0xFFF1F5F9, 0xFF131A22, 0xFFE2E8F0, 0xFF1E2631),
    MINT_GREEN(4, "柔和薄荷", 0xFFF0FDF4, 0xFF121D17, 0xFFDCFCE7, 0xFF1B2C23),
    DEEP_BLACK(5, "极夜纯黑", 0xFF000000, 0xFF000000, 0xFF121212, 0xFF121212),
    CHARCOAL(6, "雅致炭灰", 0xFF1F2328, 0xFF1F2328, 0xFF2D333B, 0xFF2D333B);

    companion object {
        fun fromId(id: Int): BackgroundPreset = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}

/**
 * 桌面小组件背景风格模式。
 */
enum class WidgetBackgroundMode(val id: Int, val label: String) {
    DEFAULT(0, "跟随系统"),
    TRANSPARENT(1, "全透明"),
    TRANSLUCENT_LIGHT(2, "半透浅色"),
    TRANSLUCENT_DARK(3, "半透深色"),
    SOLID_WHITE(4, "纯白卡片"),
    SOLID_BLACK(5, "极夜纯黑"),
    APP_IMAGE(6, "使用应用壁纸"),
    CUSTOM_IMAGE(7, "独立小组件壁纸");

    companion object {
        fun fromId(id: Int): WidgetBackgroundMode = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}

/**
 * 桌面小组件文字高对比度选项。
 */
enum class WidgetTextContrast(val id: Int, val label: String) {
    AUTO(0, "自动适应"),
    LIGHT(1, "浅色文字 (适合暗色壁纸)"),
    DARK(2, "深色文字 (适合浅色壁纸)");

    companion object {
        fun fromId(id: Int): WidgetTextContrast = entries.firstOrNull { it.id == id } ?: AUTO
    }
}

data class ThemeConfig(
    val themeMode: Int = ThemePrefs.THEME_MODE_LIGHT,
    val backgroundPreset: BackgroundPreset = BackgroundPreset.DEFAULT,
    /** 应用自定义壁纸文件绝对路径 */
    val customBackgroundPath: String? = null,
    /** 壁纸遮罩暗度 (0.0f 完全无遮罩 ~ 0.8f 较深遮罩，默认 0.25f) */
    val backgroundDim: Float = 0.25f,
    /** 课表卡片不透明度 (0.2f 高透 ~ 1.0f 纯色不透，默认 0.85f) */
    val cardAlpha: Float = 0.85f,
    /** 小组件背景模式 */
    val widgetBackgroundMode: WidgetBackgroundMode = WidgetBackgroundMode.DEFAULT,
    /** 小组件专属壁纸绝对路径 (用于 CUSTOM_IMAGE 模式) */
    val widgetCustomBgPath: String? = null,
    /** 小组件底板不透明度 (0.0f - 1.0f) */
    val widgetOpacity: Float = 1.0f,
    /** 小组件文字对比度模式 */
    val widgetTextContrast: WidgetTextContrast = WidgetTextContrast.AUTO
)

/**
 * 外观主题、自定义背景与小组件背景偏好设置。
 */
class ThemePrefs(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)

    var themeMode: Int
        get() = prefs.getInt(KEY_THEME_MODE, THEME_MODE_LIGHT)
        set(value) {
            prefs.edit().putInt(KEY_THEME_MODE, value).apply()
            _state.value = currentConfig()
        }

    var backgroundPresetId: Int
        get() = prefs.getInt(KEY_BG_PRESET, BackgroundPreset.DEFAULT.id)
        set(value) {
            prefs.edit().putInt(KEY_BG_PRESET, value).apply()
            _state.value = currentConfig()
        }

    var customBackgroundPath: String?
        get() = prefs.getString(KEY_CUSTOM_BG_PATH, null)?.takeIf { it.isNotBlank() }
        set(value) {
            prefs.edit().putString(KEY_CUSTOM_BG_PATH, value).apply()
            _state.value = currentConfig()
        }

    var backgroundDim: Float
        get() = prefs.getFloat(KEY_BG_DIM, 0.25f)
        set(value) {
            prefs.edit().putFloat(KEY_BG_DIM, value.coerceIn(0f, 0.8f)).apply()
            _state.value = currentConfig()
        }

    var cardAlpha: Float
        get() = prefs.getFloat(KEY_CARD_ALPHA, 0.85f)
        set(value) {
            prefs.edit().putFloat(KEY_CARD_ALPHA, value.coerceIn(0.2f, 1.0f)).apply()
            _state.value = currentConfig()
        }

    var widgetBackgroundMode: WidgetBackgroundMode
        get() = WidgetBackgroundMode.fromId(prefs.getInt(KEY_WIDGET_BG_MODE, WidgetBackgroundMode.DEFAULT.id))
        set(value) {
            prefs.edit().putInt(KEY_WIDGET_BG_MODE, value.id).apply()
            _state.value = currentConfig()
        }

    var widgetCustomBgPath: String?
        get() = prefs.getString(KEY_WIDGET_CUSTOM_BG_PATH, null)?.takeIf { it.isNotBlank() }
        set(value) {
            prefs.edit().putString(KEY_WIDGET_CUSTOM_BG_PATH, value).apply()
            _state.value = currentConfig()
        }

    var widgetOpacity: Float
        get() = prefs.getFloat(KEY_WIDGET_OPACITY, 1.0f)
        set(value) {
            prefs.edit().putFloat(KEY_WIDGET_OPACITY, value.coerceIn(0f, 1.0f)).apply()
            _state.value = currentConfig()
        }

    var widgetTextContrast: WidgetTextContrast
        get() = WidgetTextContrast.fromId(prefs.getInt(KEY_WIDGET_TEXT_CONTRAST, WidgetTextContrast.AUTO.id))
        set(value) {
            prefs.edit().putInt(KEY_WIDGET_TEXT_CONTRAST, value.id).apply()
            _state.value = currentConfig()
        }

    fun currentConfig(): ThemeConfig = ThemeConfig(
        themeMode = themeMode,
        backgroundPreset = BackgroundPreset.fromId(backgroundPresetId),
        customBackgroundPath = customBackgroundPath,
        backgroundDim = backgroundDim,
        cardAlpha = cardAlpha,
        widgetBackgroundMode = widgetBackgroundMode,
        widgetCustomBgPath = widgetCustomBgPath,
        widgetOpacity = widgetOpacity,
        widgetTextContrast = widgetTextContrast
    )

    init {
        _state.value = currentConfig()
    }

    companion object {
        const val THEME_MODE_SYSTEM = 0
        const val THEME_MODE_LIGHT = 1
        const val THEME_MODE_DARK = 2

        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_BG_PRESET = "background_preset"
        private const val KEY_CUSTOM_BG_PATH = "custom_bg_path"
        private const val KEY_BG_DIM = "bg_dim"
        private const val KEY_CARD_ALPHA = "card_alpha"

        private const val KEY_WIDGET_BG_MODE = "widget_bg_mode"
        private const val KEY_WIDGET_CUSTOM_BG_PATH = "widget_custom_bg_path"
        private const val KEY_WIDGET_OPACITY = "widget_opacity"
        private const val KEY_WIDGET_TEXT_CONTRAST = "widget_text_contrast"

        private val _state = MutableStateFlow(ThemeConfig())
        val state: StateFlow<ThemeConfig> = _state.asStateFlow()

        @Volatile
        private var instance: ThemePrefs? = null

        fun init(context: Context): ThemePrefs {
            return instance ?: synchronized(this) {
                instance ?: ThemePrefs(context.applicationContext).also { instance = it }
            }
        }

        fun get(): ThemePrefs {
            return instance ?: throw IllegalStateException("ThemePrefs must be initialized via init(context)")
        }
    }
}
