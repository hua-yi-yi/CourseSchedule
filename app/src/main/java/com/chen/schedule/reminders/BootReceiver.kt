package com.chen.schedule.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.chen.schedule.widget.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** The only public reminder entry point accepts the system's protected boot action. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                ClassReminderManager.rescheduleNow(appContext)
                WidgetUpdater.refreshAll(appContext)
                if (com.chen.schedule.island.IslandPrefs.init(appContext).enabled) {
                    com.chen.schedule.island.CapsuleIslandManager.start(appContext)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
