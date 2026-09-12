package com.chen.schedule.reminders

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
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
 * - 每次重排同时注册次日零点过 5 秒的跨天闹钟,保证每天自动滚动;
 * - 关闭提醒或没有当前学期时,取消全部课程闹钟;
 * - 精确闹钟不可用时(Android 14+ 默认未授予)回退到 1 分钟窗口的非精确闹钟。
 */
object ClassReminderManager {

    const val CHANNEL_ID = "class_reminder"
    const val ACTION_CLASS_REMINDER = "com.chen.schedule.reminders.CLASS_REMINDER"
    const val ACTION_RESCHEDULE = "com.chen.schedule.reminders.RESCHEDULE"

    const val EXTRA_REQUEST_CODE = "request_code"
    const val EXTRA_TITLE = "title"
    const val EXTRA_TEXT = "text"

    private const val REQUEST_MIDNIGHT = 998
    private const val REQUEST_BASE = 1000
    private const val MAX_PLANS = 64
    private const val WINDOW_MILLIS = 60_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 异步重排(数据变化 / 应用启动 / 开机时调用,不阻塞调用方)。 */
    fun rescheduleAsync(context: Context) {
        val appContext = context.applicationContext
        scope.launch { runCatching { rescheduleNow(appContext) } }
    }

    /** 读取当前学期数据,重排今天剩余的提醒闹钟与跨天闹钟。 */
    suspend fun rescheduleNow(context: Context) {
        val appContext = context.applicationContext
        ensureChannel(appContext)
        val prefs = ReminderPrefs(appContext)
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        cancelAllClassAlarms(appContext)
        scheduleMidnightRollover(appContext, alarmManager)
        if (!prefs.enabled) return

        val entry = EntryPointAccessors.fromApplication(appContext, DatabaseEntryPoint::class.java)
        val semester = entry.semesterRepository().getCurrentSemester() ?: return
        val courses = entry.courseRepository().getCoursesBySemester(semester.id).first()
        val slots = entry.timeSlotRepository().getTimeSlotsByScheme(semester.schemeId).first()

        val plans = ClassReminderPlanner.planForToday(
            nowMillis = System.currentTimeMillis(),
            slots = slots,
            courses = courses,
            currentWeek = WeekCalculator.currentWeek(semester.startDate, semester.totalWeeks),
            todayDayOfWeek = LocalDate.now().dayOfWeek.value,
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
    }

    /** 取消全部课程提醒闹钟(跨天闹钟保留)。 */
    fun cancelAllClassAlarms(context: Context) {
        val alarmManager = context.applicationContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        for (code in REQUEST_BASE until REQUEST_BASE + MAX_PLANS) {
            val pi = PendingIntent.getBroadcast(
                context, code, classReminderIntent(context),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            alarmManager.cancel(pi)
        }
    }

    fun ensureChannel(context: Context) {
        val manager = context.applicationContext.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "上课提醒",
            NotificationManager.IMPORTANCE_HIGH
        ).apply { description = "上课前几分钟的提醒通知" }
        manager.createNotificationChannel(channel)
    }

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
