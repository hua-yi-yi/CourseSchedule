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

data class ThemeConfig(
    val themeMode: Int = ThemePrefs.THEME_MODE_LIGHT,
    val backgroundPreset: BackgroundPreset = BackgroundPreset.DEFAULT
)

/**
 * 外观主题与背景底色偏好设置。
 * 默认设为浅色模式(THEME_MODE_LIGHT)，彻底告别开屏纯黑背景。
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

    fun currentConfig(): ThemeConfig = ThemeConfig(
        themeMode = themeMode,
        backgroundPreset = BackgroundPreset.fromId(backgroundPresetId)
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
