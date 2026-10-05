package com.chen.schedule.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.chen.schedule.widget.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Protected system broadcasts restore state from current data, not cached alarm payloads. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED,
                Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED)) return
        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                ClassReminderManager.rescheduleNow(appContext)
                WidgetUpdater.refreshAll(appContext)
                withContext(Dispatchers.Main) { com.chen.schedule.island.CapsuleIslandManager.sync(appContext) }
            } catch (error: Exception) {
                Log.w("CourseRestore", "Could not restore course presentation", error)
            } finally {
                pending.finish()
            }
        }
    }
}
