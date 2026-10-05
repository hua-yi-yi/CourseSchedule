package com.chen.schedule.island

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.chen.schedule.MainActivity
import com.chen.schedule.R
import com.chen.schedule.reminders.ClassReminderManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

enum class IslandSystemStatus { IDLE, BLOCKED, STANDARD, NATIVE, FAILED }

/** The only owner of notification 8888. Alarm payloads never directly publish or clear it. */
object IslandNotificationCoordinator {
    const val NOTIFICATION_ID = 8888
    private val _status = MutableStateFlow(IslandSystemStatus.IDLE)
    val status: StateFlow<IslandSystemStatus> = _status.asStateFlow()

    fun notificationsAllowed(context: Context): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        return (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context,
            Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            manager.areNotificationsEnabled() &&
            manager.getNotificationChannel(ClassReminderManager.CHANNEL_ONGOING_ID)?.importance != NotificationManager.IMPORTANCE_NONE
    }

    suspend fun reconcile(context: Context, state: IslandState,
        config: IslandConfig = IslandPrefs.init(context).currentConfig(),
        nowMillis: Long = System.currentTimeMillis(),
        snapshot: IslandScheduleSnapshot? = null): IslandSystemStatus = withContext(Dispatchers.IO) {
        ClassReminderManager.ensureChannels(context)
        val checkedAt = if (snapshot == null) nowMillis else System.currentTimeMillis()
        var liveState = snapshot?.copy(nowMillis = checkedAt)?.state(config.leadMinutes) ?: state
        val expiresAt = IslandPresentationPolicy.expiresAt(liveState)
        if (!IslandPresentationPolicy.shouldShowSystem(config, liveState) || expiresAt == null || expiresAt <= checkedAt) {
            dismiss(context)
            return@withContext IslandSystemStatus.IDLE
        }
        if (!notificationsAllowed(context)) {
            dismiss(context)
            _status.value = IslandSystemStatus.BLOCKED
            return@withContext IslandSystemStatus.BLOCKED
        }
        try {
            var builder = standardBuilder(context, liveState, expiresAt - checkedAt)
            var capability = try {
                IslandNativeAdapters.decorate(context, builder, liveState)
            } catch (error: Exception) {
                Log.w("CourseIsland", "Native adapter unavailable; keeping standard notification", error)
                null
            }
            // Binder/native checks may cross a course boundary or a settings change.
            // Recompute before committing; a stale native builder must never be reused.
            val commitTime = System.currentTimeMillis()
            val latestConfig = if (snapshot == null) config else IslandPrefs.init(context).currentConfig()
            if (expiresAt <= commitTime || latestConfig != config) {
                liveState = snapshot?.copy(nowMillis = commitTime)?.state(latestConfig.leadMinutes) ?: IslandState.None
                val newExpiry = IslandPresentationPolicy.expiresAt(liveState)
                if (!IslandPresentationPolicy.shouldShowSystem(latestConfig, liveState) ||
                    newExpiry == null || newExpiry <= commitTime) {
                    dismiss(context)
                    return@withContext IslandSystemStatus.IDLE
                }
                builder = standardBuilder(context, liveState, newExpiry - commitTime)
                capability = null
            } else builder.setTimeoutAfter(expiresAt - commitTime)
            if (!notificationsAllowed(context)) {
                dismiss(context)
                _status.value = IslandSystemStatus.BLOCKED
                return@withContext IslandSystemStatus.BLOCKED
            }
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.notify(NOTIFICATION_ID, builder.build())
            _status.value = if (capability?.nativeAvailable == true) IslandSystemStatus.NATIVE else IslandSystemStatus.STANDARD
        } catch (error: Exception) {
            Log.w("CourseIsland", "Could not publish course notification", error)
            dismiss(context)
            _status.value = IslandSystemStatus.FAILED
        }
        _status.value
    }

    fun dismiss(context: Context) {
        context.getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
        _status.value = IslandSystemStatus.IDLE
    }

    private fun standardBuilder(context: Context, state: IslandState, timeoutMillis: Long): Notification.Builder {
        val title: String
        val text: String
        when (state) {
            is IslandState.Ongoing -> {
                title = "正在上课 · ${state.courseName}"
                text = listOf(state.slotRange, "${state.startTime}–${state.endTime}",
                    state.classroom, state.teacher).filter { it.isNotBlank() }.joinToString(" · ")
            }
            is IslandState.Upcoming -> {
                title = "即将上课 · ${state.courseName}"
                text = listOf(state.slotRange, "${state.startTime}–${state.endTime}",
                    state.classroom, state.teacher).filter { it.isNotBlank() }.joinToString(" · ")
            }
            else -> error("Only finite course activities can be published")
        }
        val contentIntent = PendingIntent.getActivity(context, NOTIFICATION_ID,
            Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN_TIMETABLE, true)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return Notification.Builder(context, ClassReminderManager.CHANNEL_ONGOING_ID)
            .setSmallIcon(R.drawable.ic_notification).setContentTitle(title).setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text)).setContentIntent(contentIntent)
            .setOngoing(true).setAutoCancel(false).setOnlyAlertOnce(true).setShowWhen(false)
            .setVisibility(Notification.VISIBILITY_PRIVATE).setTimeoutAfter(timeoutMillis)
    }
}
