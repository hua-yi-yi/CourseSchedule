package com.chen.schedule.island

import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class IslandDisplayMode { SYSTEM, OVERLAY }

data class IslandConfig(
    val enabled: Boolean = false,
    val inAppEnabled: Boolean = true,
    val onlyWhenClass: Boolean = true,
    val leadMinutes: Int = 30,
    val positionY: Int = 32,
    val mockMode: Boolean = false,
    val mockState: Int = 0,
    val mode: IslandDisplayMode = IslandDisplayMode.SYSTEM
)

/** Pure upgrade policy. Existing explicit overlay choices take precedence. */
object IslandPreferenceMigration {
    fun migrate(overlayEnabled: Boolean, reminderEnabled: Boolean, ongoingEnabled: Boolean,
        legacyMockMode: Boolean = false): IslandConfig =
        IslandConfig(
            enabled = (overlayEnabled && !legacyMockMode) || (reminderEnabled && ongoingEnabled),
            mode = if (overlayEnabled && !legacyMockMode) IslandDisplayMode.OVERLAY else IslandDisplayMode.SYSTEM
        )
}

/** Persistent user intent is separate from temporary preview and runtime permissions. */
class IslandPrefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("island_prefs", Context.MODE_PRIVATE)
    @Volatile private var previewEnabled = false
    @Volatile private var previewType = 0
    @Volatile private var previewExpiresAt = 0L

    init {
        if (prefs.getInt("display_schema", 0) < 1) {
            val reminders = context.applicationContext.getSharedPreferences("reminders", Context.MODE_PRIVATE)
            val migrated = IslandPreferenceMigration.migrate(
                prefs.getBoolean("island_enabled", false),
                reminders.getBoolean("enabled", false),
                reminders.getBoolean("ongoing_class_enabled", true),
                prefs.getBoolean("island_mock_mode", false)
            )
            // The marker and result are atomic. Never restore old mock mode.
            prefs.edit().putBoolean("island_enabled", migrated.enabled)
                .putString("display_mode", migrated.mode.name).putInt("display_schema", 1)
                .remove("island_mock_mode").remove("island_mock_state").apply()
        }
    }

    var enabled: Boolean
        get() = prefs.getBoolean("island_enabled", false)
        set(value) { prefs.edit().putBoolean("island_enabled", value).apply(); notifyChange() }
    var mode: IslandDisplayMode
        get() = runCatching { IslandDisplayMode.valueOf(prefs.getString("display_mode", "SYSTEM")!!) }
            .getOrDefault(IslandDisplayMode.SYSTEM)
        set(value) { prefs.edit().putString("display_mode", value.name).apply(); notifyChange() }
    var inAppEnabled: Boolean
        get() = prefs.getBoolean("island_in_app_enabled", true)
        set(value) { prefs.edit().putBoolean("island_in_app_enabled", value).apply(); notifyChange() }
    var onlyWhenClass: Boolean
        get() = prefs.getBoolean("island_only_when_class", true)
        set(value) { prefs.edit().putBoolean("island_only_when_class", value).apply(); notifyChange() }
    var leadMinutes: Int
        get() = prefs.getInt("island_lead_minutes", DEFAULT_LEAD_MINUTES).coerceIn(5, 120)
        set(value) { prefs.edit().putInt("island_lead_minutes", value.coerceIn(5, 120)).apply(); notifyChange() }
    var positionY: Int
        get() = prefs.getInt("island_pos_y", DEFAULT_POS_Y).coerceAtLeast(0)
        set(value) { prefs.edit().putInt("island_pos_y", value.coerceAtLeast(0)).apply(); notifyChange() }

    // Compatibility accessors stay in memory. Preview cannot survive process restart.
    var mockMode: Boolean
        get() { expirePreviewIfNeeded(); return previewEnabled }
        set(value) { setPreview(value, previewType) }
    var mockState: Int
        get() = previewType
        set(value) { previewType = value.coerceIn(0, 1); notifyChange() }
    fun setPreview(enabled: Boolean, type: Int = 0) {
        previewType = type.coerceIn(0, 1)
        previewExpiresAt = if (enabled) SystemClock.elapsedRealtime() + 60_000L else 0L
        previewEnabled = enabled
        notifyChange()
    }

    fun currentConfig(): IslandConfig {
        expirePreviewIfNeeded()
        return IslandConfig(enabled, inAppEnabled, onlyWhenClass, leadMinutes,
            positionY, previewEnabled, previewType, mode)
    }
    private fun expirePreviewIfNeeded() {
        if (previewEnabled && SystemClock.elapsedRealtime() >= previewExpiresAt) {
            previewEnabled = false
            notifyChange()
        }
    }
    private fun notifyChange() { if (this === instance) _state.value = currentConfig() }

    companion object {
        const val DEFAULT_LEAD_MINUTES = 30
        const val DEFAULT_POS_Y = 32
        private val _state = MutableStateFlow(IslandConfig())
        val state: StateFlow<IslandConfig> = _state.asStateFlow()
        @Volatile private var instance: IslandPrefs? = null
        fun init(context: Context): IslandPrefs = instance ?: synchronized(this) {
            instance ?: IslandPrefs(context.applicationContext).also {
                instance = it
                _state.value = it.currentConfig()
            }
        }
    }
}
