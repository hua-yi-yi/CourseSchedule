package com.chen.schedule.reminders

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Restores exact alarms after a system grant, including grants made while the app is closed. */
class ExactAlarmPermissionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED) return
        val appContext = context.applicationContext
        // A grant can already have been revoked by the time the broadcast arrives.
        if (!ReminderStatus.exactAllowed(appContext)) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                ClassReminderManager.rescheduleNow(appContext)
            } catch (error: Exception) {
                Log.w("ExactAlarmPermission", "Could not reschedule reminders after permission grant", error)
            } finally {
                pending.finish()
            }
        }
    }
}
