package com.chen.schedule.reminders

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.chen.schedule.island.IslandDisplayMode
import com.chen.schedule.island.IslandEventPlanner
import com.chen.schedule.island.IslandNotificationCoordinator
import com.chen.schedule.island.IslandPrefs
import com.chen.schedule.island.IslandStateRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.ZoneId

/** Sound reminders and live presentation have independent intent, permissions and lead times. */
object ClassReminderManager {
    const val CHANNEL_ID = "class_reminder"
    const val CHANNEL_ONGOING_ID = "class_ongoing"
    const val ACTION_CLASS_REMINDER = "com.chen.schedule.reminders.CLASS_REMINDER"
    const val ACTION_RESCHEDULE = "com.chen.schedule.reminders.RESCHEDULE"
    // Retain old action identities so delayed broadcasts only reconcile the current facts.
    const val ACTION_CLASS_START = "com.chen.schedule.reminders.CLASS_START"
    const val ACTION_CLASS_END = "com.chen.schedule.reminders.CLASS_END"
    const val ACTION_ISLAND_BOUNDARY = "com.chen.schedule.reminders.ISLAND_BOUNDARY"
    const val EXTRA_REQUEST_CODE = "request_code"
    const val EXTRA_COURSE_ID = "course_id"
    const val EXTRA_START_MILLIS = "start_millis"
    const val EXTRA_TITLE = "title"
    const val EXTRA_TEXT = "text"
    const val ONGOING_NOTIFICATION_ID = IslandNotificationCoordinator.NOTIFICATION_ID

    private const val REQUEST_MIDNIGHT = 998
    private const val REQUEST_BASE = 1000
    private const val REQUEST_ONGOING_BASE = 2000
    private const val REQUEST_BOUNDARY = 3000
    private const val MAX_PLANS = 64
    // API 31+ clips smaller setWindow windows to at least ten minutes.
    private const val WINDOW_MILLIS = 600_000L
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val rescheduleMutex = Mutex()

    fun rescheduleAsync(context: Context) {
        val appContext = context.applicationContext
        scope.launch {
            try { rescheduleNow(appContext) }
            catch (error: Exception) {
                Log.w("ClassReminder", "Could not reschedule course events", error)
                IslandNotificationCoordinator.dismiss(appContext)
            }
        }
    }

    suspend fun rescheduleNow(context: Context) = rescheduleMutex.withLock {
        val appContext = context.applicationContext
        ensureChannels(appContext)
        val reminderPrefs = ReminderPrefs(appContext)
        val islandPrefs = IslandPrefs.init(appContext)
        val alarmManager = appContext.getSystemService(AlarmManager::class.java)
        cancelAllClassAlarms(appContext)
        scheduleMidnightRollover(appContext, alarmManager)

        val snapshot = IslandStateRepository.snapshot(appContext)
        val config = islandPrefs.currentConfig()
        // Do not clear before a normal data reschedule: update in place without flicker.
        IslandNotificationCoordinator.reconcile(appContext, snapshot.state(config.leadMinutes),
            config, snapshot.nowMillis, snapshot)
        val week = snapshot.activeWeek ?: return@withLock

        if (reminderPrefs.enabled) {
            ClassReminderPlanner.planForToday(snapshot.nowMillis, snapshot.slots, snapshot.courses,
                week, snapshot.dayOfWeek, reminderPrefs.leadMinutes, snapshot.zone, MAX_PLANS)
                .forEachIndexed { index, plan ->
                    val intent = classReminderIntent(appContext)
                        .putExtra(EXTRA_REQUEST_CODE, REQUEST_BASE + index)
                        .putExtra(EXTRA_COURSE_ID, plan.courseId)
                        .putExtra(EXTRA_START_MILLIS, plan.startAtMillis)
                        .putExtra(EXTRA_TITLE, "上课提醒 · ${plan.courseName}")
                        .putExtra(EXTRA_TEXT, listOf(plan.slotRange, "${plan.startTime}上课",
                            plan.classroom).filter { it.isNotBlank() }.joinToString(" · "))
                    val pi = PendingIntent.getBroadcast(appContext, REQUEST_BASE + index, intent,
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                    setAlarmCompat(alarmManager, plan.triggerAtMillis, pi)
                }
        }
        val latestConfig = islandPrefs.currentConfig()
        if (latestConfig.enabled && latestConfig.mode == IslandDisplayMode.SYSTEM) {
            // One next boundary is enough; delivery recomputes and schedules its successor.
            IslandEventPlanner.boundaries(snapshot.copy(nowMillis = System.currentTimeMillis()),
                latestConfig.leadMinutes).firstOrNull()?.let { trigger ->
                val pi = PendingIntent.getBroadcast(appContext, REQUEST_BOUNDARY,
                    Intent(appContext, ClassReminderReceiver::class.java).setAction(ACTION_ISLAND_BOUNDARY),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                setAlarmCompat(alarmManager, trigger, pi)
            }
        }
    }

    /** Cancels prior sound/boundary alarms and old start/end identities, without posting anything. */
    fun cancelAllClassAlarms(context: Context) {
        val manager = context.getSystemService(AlarmManager::class.java)
        fun cancel(code: Int, action: String) {
            val pi = PendingIntent.getBroadcast(context, code,
                Intent(context, ClassReminderReceiver::class.java).setAction(action),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE)
            if (pi != null) { manager.cancel(pi); pi.cancel() }
        }
        for (index in 0 until MAX_PLANS) {
            cancel(REQUEST_BASE + index, ACTION_CLASS_REMINDER)
            cancel(REQUEST_ONGOING_BASE + index, ACTION_CLASS_START)
            cancel(REQUEST_ONGOING_BASE + index, ACTION_CLASS_END)
        }
        cancel(REQUEST_BOUNDARY, ACTION_ISLAND_BOUNDARY)
    }

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "上课前预警提醒",
            NotificationManager.IMPORTANCE_HIGH).apply { description = "上课前的声音或振动提醒" })
        manager.createNotificationChannel(NotificationChannel(CHANNEL_ONGOING_ID, "课程实时看板",
            NotificationManager.IMPORTANCE_LOW).apply {
                description = "候课或上课期间显示课程地点、节次与起止时间"
                setShowBadge(false)
            })
    }
    fun ensureChannel(context: Context) = ensureChannels(context)
    private fun classReminderIntent(context: Context) =
        Intent(context, ClassReminderReceiver::class.java).setAction(ACTION_CLASS_REMINDER)

    private fun setAlarmCompat(manager: AlarmManager, trigger: Long, pi: PendingIntent) {
        try {
            if (Build.VERSION.SDK_INT < 31 || manager.canScheduleExactAlarms())
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
            else manager.setWindow(AlarmManager.RTC_WAKEUP, trigger, WINDOW_MILLIS, pi)
        } catch (_: SecurityException) {
            manager.setWindow(AlarmManager.RTC_WAKEUP, trigger, WINDOW_MILLIS, pi)
        }
    }
    private fun scheduleMidnightRollover(context: Context, manager: AlarmManager) {
        val trigger = LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault())
            .toInstant().toEpochMilli() + 5_000L
        val pi = PendingIntent.getBroadcast(context, REQUEST_MIDNIGHT,
            Intent(context, ClassReminderReceiver::class.java).setAction(ACTION_RESCHEDULE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        setAlarmCompat(manager, trigger, pi)
    }
}
