package com.chen.schedule.reminders

/** Detects grants when returning from system settings without rebuilding alarms on every resume. */
class ExactAlarmPermissionObserver(
    private val isAllowed: () -> Boolean,
    private val onGranted: () -> Unit
) {
    private var wasAllowed = isAllowed()

    fun onResume() {
        val allowed = isAllowed()
        val becameAllowed = allowed && !wasAllowed
        wasAllowed = allowed
        if (becameAllowed) onGranted()
    }
}
