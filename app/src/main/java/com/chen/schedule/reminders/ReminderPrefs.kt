package com.chen.schedule.reminders

import android.content.Context
import android.content.SharedPreferences

/**
 * 上课提醒设置(SharedPreferences)。
 * 用 SharedPreferences 而非 DataStore:广播接收器里需要同步读取,避免协程开销。
 */
class ReminderPrefs(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("reminders", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    /** 上课前的提前分钟数(默认 10 分钟)。 */
    var leadMinutes: Int
        get() = prefs.getInt(KEY_LEAD, DEFAULT_LEAD_MINUTES)
        set(value) = prefs.edit().putInt(KEY_LEAD, value.coerceIn(1, 120)).apply()

    companion object {
        const val DEFAULT_LEAD_MINUTES = 10
        private const val KEY_ENABLED = "enabled"
        private const val KEY_LEAD = "lead_minutes"
    }
}
