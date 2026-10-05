package com.chen.schedule.island

import android.app.Instrumentation
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.view.MotionEvent
import com.chen.schedule.domain.model.Course
import com.chen.schedule.reminders.ClassReminderManager
import com.chen.schedule.reminders.ReminderPrefs
import com.chen.schedule.reminders.ReminderStatus
import kotlinx.coroutines.runBlocking
import java.util.UUID

/** Isolated preference names and synthetic course facts; no user course database writes. */
fun islandRuntimeRegression(instrumentation: Instrumentation) = runBlocking {
    val context = instrumentation.targetContext
    val prefix = "island-regression-${UUID.randomUUID()}-"
    val isolated = object : ContextWrapper(context) {
        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
            super.getSharedPreferences(prefix + name, mode)
    }
    try {
        isolated.getSharedPreferences("island_prefs", Context.MODE_PRIVATE).edit()
            .putBoolean("island_enabled", true).putBoolean("island_mock_mode", true).commit()
        val migrated = IslandPrefs(isolated)
        check(!migrated.enabled && migrated.mode == IslandDisplayMode.SYSTEM && !migrated.mockMode)
        migrated.enabled = true
        migrated.mode = IslandDisplayMode.OVERLAY
        migrated.leadMinutes = 45
        migrated.setPreview(true, 1)
        check(migrated.currentConfig().mockMode)
        val reopened = IslandPrefs(isolated)
        check(reopened.enabled && reopened.mode == IslandDisplayMode.OVERLAY)
        check(reopened.leadMinutes == 45 && !reopened.mockMode) // idempotent, never saves preview
        reopened.enabled = false
        reopened.setPreview(true, 0)
        reopened.setPreview(false)
        check(!reopened.enabled)

        instrumentation.runOnMainSync {
            val view = CapsuleIslandView(context)
            var sumX = 0
            var sumY = 0
            view.onDragPositionChanged = { dx, dy -> sumX += dx; sumY += dy }
            val time = android.os.SystemClock.uptimeMillis()
            fun touch(action: Int, x: Float, y: Float) {
                MotionEvent.obtain(time, time, action, x, y, 0).also {
                    try { view.onTouchEvent(it) } finally { it.recycle() }
                }
            }
            touch(MotionEvent.ACTION_DOWN, 100f, 100f)
            touch(MotionEvent.ACTION_MOVE, 150f, 150f)
            touch(MotionEvent.ACTION_MOVE, 180f, 170f)
            touch(MotionEvent.ACTION_UP, 180f, 170f)
            check(sumX == 80 && sumY == 70) { "Drag callback must emit increments, not summed totals" }
            check(!view.isExpanded)
            check(view.performClick() && view.isExpanded)
        }

        ClassReminderManager.ensureChannels(context)
        check(IslandNotificationCoordinator.notificationsAllowed(context)) { "Grant notification permission on the isolated test emulator" }
        val reminders = ReminderPrefs(context)
        val previousSound = reminders.enabled
        try {
            reminders.enabled = false
            val config = IslandConfig(enabled = true, mode = IslandDisplayMode.SYSTEM)
            val first = sampleOngoing(System.currentTimeMillis(), "测试课程甲")
            check(IslandNotificationCoordinator.reconcile(context, first, config) == IslandSystemStatus.STANDARD)
            val manager = context.getSystemService(NotificationManager::class.java)
            check(manager.activeNotifications.count { it.id == 8888 } == 1)
            check(manager.activeNotifications.single { it.id == 8888 }.notification
                .extras.getCharSequence(Notification.EXTRA_TITLE).toString().contains("测试课程甲"))

            val second = sampleOngoing(System.currentTimeMillis(), "测试课程乙")
            IslandNotificationCoordinator.reconcile(context, second, config)
            val card = manager.activeNotifications.single { it.id == 8888 }
            check(card.notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString().contains("测试课程乙"))
            check(card.notification.timeoutAfter in 1..120_000L)
            check(!card.notification.extras.containsKey("miui.focus.param"))
            check(!reminders.enabled) // realtime publication cannot turn on sound

            IslandNotificationCoordinator.reconcile(context, second, config.copy(mode = IslandDisplayMode.OVERLAY))
            check(manager.activeNotifications.none { it.id == 8888 })
            IslandNotificationCoordinator.reconcile(context, second, config)
            IslandNotificationCoordinator.reconcile(context, second.copy(endMillis = System.currentTimeMillis() - 1), config)
            check(manager.activeNotifications.none { it.id == 8888 })
        } finally {
            reminders.enabled = previousSound
            IslandNotificationCoordinator.dismiss(context)
        }
    } finally {
        isolated.getSharedPreferences("island_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        isolated.getSharedPreferences("reminders", Context.MODE_PRIVATE).edit().clear().commit()
    }
}

fun islandPermissionProbe(context: Context, expectedSound: Boolean, expectedSystem: Boolean) = runBlocking {
    ClassReminderManager.ensureChannels(context)
    check(ReminderStatus.notificationsAllowed(context) == expectedSound) { "Unexpected sound channel gate" }
    check(IslandNotificationCoordinator.notificationsAllowed(context) == expectedSystem) { "Unexpected realtime channel gate" }
    val result = IslandNotificationCoordinator.reconcile(context, sampleOngoing(System.currentTimeMillis(), "权限验证"),
        IslandConfig(enabled = true, mode = IslandDisplayMode.SYSTEM))
    check(result == if (expectedSystem) IslandSystemStatus.STANDARD else IslandSystemStatus.BLOCKED)
    check(context.getSystemService(NotificationManager::class.java).activeNotifications.any { it.id == 8888 } == expectedSystem)
    IslandNotificationCoordinator.dismiss(context)
}

private fun sampleOngoing(now: Long, name: String): IslandState.Ongoing {
    val course = Course(id = 99001, name = name, classroom = "测试教室", teacher = "测试教师")
    return IslandState.Ongoing(course, name, course.classroom, course.teacher, "第 1–2 节",
        "08:00", "09:40", now - 60_000, now + 120_000, 2, 3, 1f / 3,
        course.color, name, "测试数据")
}
