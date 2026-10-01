package com.chen.schedule.reminders

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.chen.schedule.R

object ReminderStatus {
    fun notificationsAllowed(context: Context): Boolean {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            (manager.getNotificationChannel(ClassReminderManager.CHANNEL_ID)?.importance ?: NotificationManager.IMPORTANCE_DEFAULT) != NotificationManager.IMPORTANCE_NONE
    }
    fun exactAllowed(context: Context): Boolean = Build.VERSION.SDK_INT < 31 ||
        (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()
    fun describe(context: Context, enabled: Boolean): String = when {
        !enabled -> "提醒已关闭"
        !notificationsAllowed(context) -> "通知被系统禁止，暂时无法收到提醒"
        !exactAllowed(context) -> "提醒已开启；精确闹钟未获授权，可能延迟"
        else -> "提醒已开启，通知与精确闹钟可用"
    }
    fun test(context: Context) {
        ClassReminderManager.ensureChannels(context)
        require(notificationsAllowed(context)) { "请先允许通知，并开启上课提醒通知类别" }
        val notification = NotificationCompat.Builder(context, ClassReminderManager.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification).setContentTitle("课程表测试通知")
            .setContentText("通知通道可用；实际提醒时间仍取决于系统闹钟及后台设置。")
            .setAutoCancel(true).build()
        if (Build.VERSION.SDK_INT >= 33 && androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
            error("请先允许通知")
        try { NotificationManagerCompat.from(context).notify(8890, notification) }
        catch (e: SecurityException) { error("通知权限已变化，请重新授权") }
    }
}
