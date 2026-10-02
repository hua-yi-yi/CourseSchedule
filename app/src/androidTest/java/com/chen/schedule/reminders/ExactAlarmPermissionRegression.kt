package com.chen.schedule.reminders

import android.app.AlarmManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent

/** Read-only device check for the system-grant entry point; does not change alarm permissions. */
@Suppress("DEPRECATION")
fun exactAlarmPermissionReceiverRegression(context: Context) {
    val component = ComponentName(context, ExactAlarmPermissionReceiver::class.java)
    val receiver = context.packageManager.getReceiverInfo(component, 0)
    check(!receiver.exported) { "Exact alarm permission receiver must remain internal" }

    val grantIntent = Intent(AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED)
        .setPackage(context.packageName)
    val matchingReceivers = context.packageManager.queryBroadcastReceivers(grantIntent, 0)
    check(matchingReceivers.any { it.activityInfo.name == component.className }) {
        "The installed manifest does not route exact alarm grants to the permission receiver"
    }

    // An unrelated broadcast must not read app data, schedule alarms, or require an async result.
    ExactAlarmPermissionReceiver().onReceive(context, Intent("unknown"))
}
