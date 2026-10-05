package com.chen.schedule.reminders

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.chen.schedule.MainActivity
import com.chen.schedule.R
import com.chen.schedule.island.CapsuleIslandManager
import com.chen.schedule.island.IslandStateRepository
import com.chen.schedule.widget.WidgetUpdater
import com.chen.schedule.util.ScheduleStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant

/** Alarm extras are wake-up hints, never authoritative course-state snapshots. */
class ClassReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(ClassReminderManager.ACTION_RESCHEDULE,
                ClassReminderManager.ACTION_CLASS_START, ClassReminderManager.ACTION_CLASS_END,
                ClassReminderManager.ACTION_ISLAND_BOUNDARY, ClassReminderManager.ACTION_CLASS_REMINDER)) return
        val appContext = context.applicationContext
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                if (intent.action == ClassReminderManager.ACTION_CLASS_REMINDER) notifyClass(appContext, intent)
                ClassReminderManager.rescheduleNow(appContext)
                withContext(Dispatchers.Main) { CapsuleIslandManager.refresh(appContext) }
                if (intent.action == ClassReminderManager.ACTION_RESCHEDULE) WidgetUpdater.refreshAll(appContext)
            } catch (error: Exception) {
                Log.w("ClassReminder", "Course alarm reconciliation failed", error)
            } finally { pending.finish() }
        }
    }

    private suspend fun notifyClass(context: Context, intent: Intent) {
        ClassReminderManager.ensureChannels(context)
        if (!ReminderPrefs(context).enabled || !ReminderStatus.notificationsAllowed(context)) return
        val snapshot = IslandStateRepository.snapshot(context)
        val now = snapshot.nowMillis
        val week = snapshot.activeWeek ?: return
        val courseId = intent.getLongExtra(ClassReminderManager.EXTRA_COURSE_ID, -1)
        val start = intent.getLongExtra(ClassReminderManager.EXTRA_START_MILLIS, 0)
        val course = snapshot.courses.firstOrNull {
            it.id == courseId && it.appliesToWeek(week) && it.dayOfWeek == snapshot.dayOfWeek
        } ?: return
        // Skip expired reminders and notifications whose course time changed after scheduling.
        val firstSlot = snapshot.slots.firstOrNull { it.slotNumber == course.startSlot } ?: return
        val localStart = ScheduleStatus.parseTime(firstSlot.startTime) ?: return
        val actualStart = Instant.ofEpochMilli(now).atZone(snapshot.zone).toLocalDate()
            .atTime(localStart).atZone(snapshot.zone).toInstant().toEpochMilli()
        if (actualStart != start || now >= actualStart ||
            now < actualStart - ReminderPrefs(context).leadMinutes * 60_000L) return
        val pi = PendingIntent.getActivity(context, 0,
            Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN_TIMETABLE, true)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val slotRange = if (course.startSlot == course.endSlot) "第 ${course.startSlot} 节"
            else "第 ${course.startSlot}–${course.endSlot} 节"
        val text = listOf(slotRange, "${firstSlot.startTime}上课", course.classroom)
            .filter { it.isNotBlank() }.joinToString(" · ")
        val commitTime = System.currentTimeMillis()
        if (commitTime >= actualStart || !ReminderPrefs(context).enabled ||
            !ReminderStatus.notificationsAllowed(context)) return
        val notification = Notification.Builder(context, ClassReminderManager.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification).setContentTitle("上课提醒 · ${course.name}")
            .setContentText(text).setStyle(Notification.BigTextStyle().bigText(text)).setContentIntent(pi)
            .setAutoCancel(true).setTimeoutAfter(actualStart - commitTime).build()
        try { context.getSystemService(NotificationManager::class.java)
            .notify(intent.getIntExtra(ClassReminderManager.EXTRA_REQUEST_CODE, 0), notification) }
        catch (error: SecurityException) { Log.w("ClassReminder", "Notification permission changed", error) }
    }
}
