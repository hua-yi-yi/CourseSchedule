package com.chen.schedule.reminders

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.chen.schedule.MainActivity
import com.chen.schedule.R
import dagger.hilt.android.EntryPointAccessors
import com.chen.schedule.di.DatabaseEntryPoint
import com.chen.schedule.util.WeekCalculator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/**
 * 上课提醒的调度中心:
 * - 每次课表数据变化 / 应用启动 / 开机 / 跨天时,重排「今天剩余课程」的闹钟;
 * - 支持上课前预警通知 (CHANNEL_ID) 与 上课期间常驻看板通知 (CHANNEL_ONGOING_ID);
 * - 每次重排同时注册次日零点过 5 秒的跨天闹钟,保证每天自动滚动;
 * - 关闭提醒或没有当前学期时,取消全部课程闹钟与常驻卡片;
 * - 精确闹钟不可用时(Android 14+ 默认未授予)回退到 1 分钟窗口的非精确闹钟。
 */
object ClassReminderManager {

    const val CHANNEL_ID = "class_reminder"
    const val CHANNEL_ONGOING_ID = "class_ongoing"

    const val ACTION_CLASS_REMINDER = "com.chen.schedule.reminders.CLASS_REMINDER"
    const val ACTION_RESCHEDULE = "com.chen.schedule.reminders.RESCHEDULE"
    const val ACTION_CLASS_START = "com.chen.schedule.reminders.CLASS_START"
    const val ACTION_CLASS_END = "com.chen.schedule.reminders.CLASS_END"

    const val EXTRA_REQUEST_CODE = "request_code"
    const val EXTRA_TITLE = "title"
    const val EXTRA_TEXT = "text"
    const val EXTRA_CLASSROOM = "classroom"
    const val EXTRA_TEACHER = "teacher"
    const val EXTRA_SLOT_RANGE = "slot_range"
    const val EXTRA_START_TIME = "start_time"
    const val EXTRA_END_TIME = "end_time"

    const val ONGOING_NOTIFICATION_ID = 8888

    private const val REQUEST_MIDNIGHT = 998
    private const val REQUEST_BASE = 1000
    private const val REQUEST_ONGOING_BASE = 2000
    private const val MAX_PLANS = 64
    private const val MAX_ONGOING_EVENTS = 64
    private const val WINDOW_MILLIS = 60_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 异步重排(数据变化 / 应用启动 / 开机时调用,不阻塞调用方)。 */
    fun rescheduleAsync(context: Context) {
        val appContext = context.applicationContext
        scope.launch { runCatching { rescheduleNow(appContext) } }
    }

    /** 读取当前学期数据,重排今天剩余的提醒闹钟、进行中看板与跨天闹钟。 */
    suspend fun rescheduleNow(context: Context) {
        val appContext = context.applicationContext
        ensureChannels(appContext)
        val prefs = ReminderPrefs(appContext)
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        cancelAllClassAlarms(appContext)
        scheduleMidnightRollover(appContext, alarmManager)
        if (!prefs.enabled) {
            clearOngoing(appContext)
            return
        }

        val entry = EntryPointAccessors.fromApplication(appContext, DatabaseEntryPoint::class.java)
        val semester = entry.semesterRepository().getCurrentSemester() ?: return
        val courses = entry.courseRepository().getCoursesBySemester(semester.id).first()
        val slots = entry.timeSlotRepository().getTimeSlotsByScheme(semester.schemeId).first()
        val now = System.currentTimeMillis()
        val currentWeek = WeekCalculator.currentWeek(semester.startDate, semester.totalWeeks)
        val todayDow = LocalDate.now().dayOfWeek.value

        // 1. 课程前提前预警闹钟
        val plans = ClassReminderPlanner.planForToday(
            nowMillis = now,
            slots = slots,
            courses = courses,
            currentWeek = currentWeek,
            todayDayOfWeek = todayDow,
            leadMinutes = prefs.leadMinutes,
            maxPlans = MAX_PLANS
        )
        plans.forEachIndexed { index, plan ->
            val intent = classReminderIntent(appContext)
                .putExtra(EXTRA_REQUEST_CODE, REQUEST_BASE + index)
                .putExtra(EXTRA_TITLE, "再过 ${prefs.leadMinutes} 分钟上课")
                .putExtra(
                    EXTRA_TEXT,
                    buildString {
                        append(plan.courseName)
                        append(" · ")
                        append(plan.slotRange)
                        append(' ')
                        append(plan.startTime)
                        if (plan.classroom.isNotBlank()) {
                            append(" · ")
                            append(plan.classroom)
                        }
                    }
                )
            val pi = PendingIntent.getBroadcast(
                appContext, REQUEST_BASE + index, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            setAlarmCompat(alarmManager, plan.triggerAtMillis, pi)
        }

        // 2. 上课中常驻看板调度
        if (prefs.ongoingClassEnabled) {
            val currentOngoing = ClassReminderPlanner.findCurrentOngoingCourse(
                nowMillis = now,
                slots = slots,
                courses = courses,
                currentWeek = currentWeek,
                todayDayOfWeek = todayDow
            )
            if (currentOngoing != null) {
                notifyOngoing(appContext, currentOngoing)
            } else {
                clearOngoing(appContext)
            }

            val ongoingEvents = ClassReminderPlanner.planOngoingEvents(
                nowMillis = now,
                slots = slots,
                courses = courses,
                currentWeek = currentWeek,
                todayDayOfWeek = todayDow,
                maxEvents = MAX_ONGOING_EVENTS
            )
            ongoingEvents.forEachIndexed { index, event ->
                val action = if (event.isStart) ACTION_CLASS_START else ACTION_CLASS_END
                val intent = Intent(appContext, ClassReminderReceiver::class.java)
                    .setAction(action)
                    .putExtra(EXTRA_REQUEST_CODE, REQUEST_ONGOING_BASE + index)
                    .putExtra(EXTRA_TITLE, event.info.courseName)
                    .putExtra(EXTRA_CLASSROOM, event.info.classroom)
                    .putExtra(EXTRA_TEACHER, event.info.teacher)
                    .putExtra(EXTRA_SLOT_RANGE, event.info.slotRange)
                    .putExtra(EXTRA_START_TIME, event.info.startTime)
                    .putExtra(EXTRA_END_TIME, event.info.endTime)

                val pi = PendingIntent.getBroadcast(
                    appContext, REQUEST_ONGOING_BASE + index, intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                setAlarmCompat(alarmManager, event.triggerAtMillis, pi)
            }
        } else {
            clearOngoing(appContext)
        }
    }

    /** 取消全部课程提醒闹钟与常驻卡片闹钟(跨天闹钟保留)。 */
    fun cancelAllClassAlarms(context: Context) {
        val alarmManager = context.applicationContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        for (code in REQUEST_BASE until REQUEST_BASE + MAX_PLANS) {
            val pi = PendingIntent.getBroadcast(
                context, code, classReminderIntent(context),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            alarmManager.cancel(pi)
        }
        for (code in REQUEST_ONGOING_BASE until REQUEST_ONGOING_BASE + MAX_ONGOING_EVENTS) {
            val startIntent = Intent(context, ClassReminderReceiver::class.java).setAction(ACTION_CLASS_START)
            val piStart = PendingIntent.getBroadcast(
                context, code, startIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            alarmManager.cancel(piStart)

            val endIntent = Intent(context, ClassReminderReceiver::class.java).setAction(ACTION_CLASS_END)
            val piEnd = PendingIntent.getBroadcast(
                context, code, endIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            alarmManager.cancel(piEnd)
        }
    }

    /** 发送或更新进行中课程常驻通知。 */
    fun notifyOngoing(context: Context, info: ClassReminderPlanner.OngoingCourseInfo) {
        ensureChannels(context)
        val manager = context.applicationContext.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= 33 && !manager.areNotificationsEnabled()) return

        val contentIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val title = "正在上课 · ${info.courseName}"
        val subText = buildString {
            append(info.slotRange)
            append(" (${info.startTime}-${info.endTime})")
            if (info.classroom.isNotBlank()) {
                append(" · ")
                append(info.classroom)
            }
            if (info.teacher.isNotBlank()) {
                append(" · ")
                append(info.teacher)
            }
        }
        val notification = Notification.Builder(context, CHANNEL_ONGOING_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(subText)
            .setStyle(Notification.BigTextStyle().bigText(subText))
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setAutoCancel(false)
            .build()
        manager.notify(ONGOING_NOTIFICATION_ID, notification)
    }

    /** 移除进行中常驻通知。 */
    fun clearOngoing(context: Context) {
        val manager = context.applicationContext.getSystemService(NotificationManager::class.java) ?: return
        manager.cancel(ONGOING_NOTIFICATION_ID)
    }

    fun ensureChannels(context: Context) {
        val manager = context.applicationContext.getSystemService(NotificationManager::class.java) ?: return
        val reminderChannel = NotificationChannel(
            CHANNEL_ID,
            "上课前预警提醒",
            NotificationManager.IMPORTANCE_HIGH
        ).apply { description = "上课前几分钟发出的预警提醒通知" }

        val ongoingChannel = NotificationChannel(
            CHANNEL_ONGOING_ID,
            "进行中课程看板",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "正在上课时常驻显示课程地点、节次与下课时间"
            setShowBadge(false)
        }

        manager.createNotificationChannel(reminderChannel)
        manager.createNotificationChannel(ongoingChannel)
    }

    /** 兼容旧引用 */
    fun ensureChannel(context: Context) = ensureChannels(context)

    private fun classReminderIntent(context: Context): Intent =
        Intent(context, ClassReminderReceiver::class.java).setAction(ACTION_CLASS_REMINDER)

    private fun setAlarmCompat(alarmManager: AlarmManager, triggerAtMillis: Long, pi: PendingIntent) {
        val exactAllowed = Build.VERSION.SDK_INT < 31 || alarmManager.canScheduleExactAlarms()
        try {
            if (exactAllowed) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
            } else {
                alarmManager.setWindow(AlarmManager.RTC_WAKEUP, triggerAtMillis, WINDOW_MILLIS, pi)
            }
        } catch (_: SecurityException) {
            alarmManager.setWindow(AlarmManager.RTC_WAKEUP, triggerAtMillis, WINDOW_MILLIS, pi)
        }
    }

    /** 次日 00:00:05 的跨天闹钟:触发后重排新一天的提醒。 */
    private fun scheduleMidnightRollover(context: Context, alarmManager: AlarmManager) {
        val zone = ZoneId.systemDefault()
        val nextMidnight = LocalDate.now().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() + 5_000L
        val intent = Intent(context, ClassReminderReceiver::class.java).setAction(ACTION_RESCHEDULE)
        val pi = PendingIntent.getBroadcast(
            context, REQUEST_MIDNIGHT, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        setAlarmCompat(alarmManager, nextMidnight, pi)
    }
}
