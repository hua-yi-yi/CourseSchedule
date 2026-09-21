package com.chen.schedule.reminders

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.chen.schedule.MainActivity
import com.chen.schedule.R
import com.chen.schedule.widget.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 上课提醒广播:处理三种动作——
 * 1. [ClassReminderManager.ACTION_CLASS_REMINDER]:发出提醒通知;
 * 2. [ClassReminderManager.ACTION_RESCHEDULE]:跨天闹钟,重排当天剩余提醒;
 * 3. 系统开机广播(BOOT_COMPLETED):重启后恢复提醒。
 */
class ClassReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        when (intent.action) {
            ClassReminderManager.ACTION_RESCHEDULE,
            Intent.ACTION_BOOT_COMPLETED -> {
                val pending = goAsync()
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    try {
                        ClassReminderManager.rescheduleNow(appContext)
                        // 跨天(零点)与开机时顺带强制刷新小组件:
                        // 部分桌面会拖延 30 分钟周期刷新,导致「今天」组件显示昨天的课
                        WidgetUpdater.refreshAll(appContext)
                    } finally {
                        pending.finish()
                    }
                }
            }
            ClassReminderManager.ACTION_CLASS_START -> {
                val courseName = intent.getStringExtra(ClassReminderManager.EXTRA_TITLE).orEmpty()
                val classroom = intent.getStringExtra(ClassReminderManager.EXTRA_CLASSROOM).orEmpty()
                val teacher = intent.getStringExtra(ClassReminderManager.EXTRA_TEACHER).orEmpty()
                val slotRange = intent.getStringExtra(ClassReminderManager.EXTRA_SLOT_RANGE).orEmpty()
                val startTime = intent.getStringExtra(ClassReminderManager.EXTRA_START_TIME).orEmpty()
                val endTime = intent.getStringExtra(ClassReminderManager.EXTRA_END_TIME).orEmpty()
                val prefs = ReminderPrefs(appContext)
                if (prefs.enabled && prefs.ongoingClassEnabled && courseName.isNotBlank()) {
                    ClassReminderManager.notifyOngoing(
                        appContext,
                        ClassReminderPlanner.OngoingCourseInfo(
                            courseName = courseName,
                            classroom = classroom,
                            teacher = teacher,
                            slotRange = slotRange,
                            startTime = startTime,
                            endTime = endTime,
                            startAtMillis = 0L,
                            endAtMillis = 0L
                        )
                    )
                }
            }
            ClassReminderManager.ACTION_CLASS_END -> {
                ClassReminderManager.clearOngoing(appContext)
                // 下课后异步触发一次重排以检查后续状态与小组件刷新
                ClassReminderManager.rescheduleAsync(appContext)
            }
            else -> notifyClass(appContext, intent)
        }
    }

    private fun notifyClass(context: Context, intent: Intent) {
        ClassReminderManager.ensureChannel(context)
        val prefs = ReminderPrefs(context)
        if (!prefs.enabled) return
        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 33 && !manager.areNotificationsEnabled()) return

        val requestCode = intent.getIntExtra(ClassReminderManager.EXTRA_REQUEST_CODE, 0)
        val title = intent.getStringExtra(ClassReminderManager.EXTRA_TITLE) ?: "快上课了"
        val text = intent.getStringExtra(ClassReminderManager.EXTRA_TEXT).orEmpty()

        val contentIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = Notification.Builder(context, ClassReminderManager.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()
        manager.notify(requestCode, notification)
    }
}
