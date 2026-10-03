package com.chen.schedule.island

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class IslandConfig(
    val enabled: Boolean = false,
    val inAppEnabled: Boolean = true,
    val onlyWhenClass: Boolean = true,
    val leadMinutes: Int = 30,
    val positionY: Int = 32,
    val mockMode: Boolean = false,
    val mockState: Int = 0 // 0: Ongoing, 1: Upcoming
)

/**
 * 胶囊灵动岛偏好设置存储。
 */
class IslandPrefs(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("island_prefs", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_ENABLED, value).apply()
            notifyChange()
        }

    var inAppEnabled: Boolean
        get() = prefs.getBoolean(KEY_IN_APP_ENABLED, true)
        set(value) {
            prefs.edit().putBoolean(KEY_IN_APP_ENABLED, value).apply()
            notifyChange()
        }

    var onlyWhenClass: Boolean
        get() = prefs.getBoolean(KEY_ONLY_WHEN_CLASS, true)
        set(value) {
            prefs.edit().putBoolean(KEY_ONLY_WHEN_CLASS, value).apply()
            notifyChange()
        }

    var leadMinutes: Int
        get() = prefs.getInt(KEY_LEAD_MINUTES, DEFAULT_LEAD_MINUTES)
        set(value) {
            prefs.edit().putInt(KEY_LEAD_MINUTES, value.coerceIn(5, 120)).apply()
            notifyChange()
        }

    var positionY: Int
        get() = prefs.getInt(KEY_POS_Y, DEFAULT_POS_Y)
        set(value) {
            prefs.edit().putInt(KEY_POS_Y, value).apply()
            notifyChange()
        }

    var mockMode: Boolean
        get() = prefs.getBoolean(KEY_MOCK_MODE, false)
        set(value) {
            prefs.edit().putBoolean(KEY_MOCK_MODE, value).apply()
            notifyChange()
        }

    var mockState: Int
        get() = prefs.getInt(KEY_MOCK_STATE, 0)
        set(value) {
            prefs.edit().putInt(KEY_MOCK_STATE, value).apply()
            notifyChange()
        }

    fun currentConfig(): IslandConfig = IslandConfig(
        enabled = enabled,
        inAppEnabled = inAppEnabled,
        onlyWhenClass = onlyWhenClass,
        leadMinutes = leadMinutes,
        positionY = positionY,
        mockMode = mockMode,
        mockState = mockState
    )

    private fun notifyChange() {
        _state.value = currentConfig()
    }

    companion object {
        const val DEFAULT_LEAD_MINUTES = 30
        const val DEFAULT_POS_Y = 32

        private const val KEY_ENABLED = "island_enabled"
        private const val KEY_IN_APP_ENABLED = "island_in_app_enabled"
        private const val KEY_ONLY_WHEN_CLASS = "island_only_when_class"
        private const val KEY_LEAD_MINUTES = "island_lead_minutes"
        private const val KEY_POS_Y = "island_pos_y"
        private const val KEY_MOCK_MODE = "island_mock_mode"
        private const val KEY_MOCK_STATE = "island_mock_state"

        private val _state = MutableStateFlow(IslandConfig())
        val state: StateFlow<IslandConfig> = _state.asStateFlow()

        @Volatile
        private var instance: IslandPrefs? = null

        fun init(context: Context): IslandPrefs {
            return instance ?: synchronized(this) {
                instance ?: IslandPrefs(context.applicationContext).also {
                    instance = it
                    _state.value = it.currentConfig()
                }
            }
        }
    }
}
