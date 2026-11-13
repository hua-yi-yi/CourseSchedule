package com.chen.schedule.ui.settings

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.chen.schedule.ui.theme.ScheduleStyle

@Composable
internal fun ReminderSettings(viewModel: SettingsViewModel) {
    val context = LocalContext.current
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onReminderSettingsResume() }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.updateReminderEnabled(granted)
        if (!granted) {
            Toast.makeText(context, "未授予通知权限，将收不到上课提醒", Toast.LENGTH_LONG).show()
        }
    }
    SettingsGroup {
        Text(viewModel.reminderStatus, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.primary)
        Text(viewModel.nextReminder, Modifier.padding(horizontal = ScheduleStyle.PagePadding), style = MaterialTheme.typography.bodySmall)
        Row(Modifier.fillMaxWidth()) {
            androidx.compose.material3.TextButton(onClick = {
                val intent = android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                context.startActivity(intent)
            }) { Text("通知设置") }
            if (android.os.Build.VERSION.SDK_INT >= 31 && !com.chen.schedule.reminders.ReminderStatus.exactAllowed(context))
                androidx.compose.material3.TextButton(onClick = {
                    context.startActivity(android.content.Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        android.net.Uri.parse("package:" + context.packageName)))
                }) { Text("闹钟授权") }
            androidx.compose.material3.TextButton(onClick = viewModel::testReminder) { Text("测试通知") }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("上课前提醒我", style = MaterialTheme.typography.bodyMedium, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text(
                    if (viewModel.reminderEnabled) {
                        "提前 ${viewModel.reminderLead} 分钟通知今天剩余的课程"
                    } else {
                        "关闭状态，不发送课前提醒"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = viewModel.reminderEnabled,
                onCheckedChange = { enabled ->
                    if (enabled && Build.VERSION.SDK_INT >= 33 && !androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else viewModel.updateReminderEnabled(enabled)
                }
            )
        }
        GroupDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(5, 10, 15, 20, 30).forEach { minutes ->
                FilterChip(
                    selected = viewModel.reminderLead == minutes,
                    onClick = { viewModel.updateReminderLead(minutes) },
                    label = { Text("提前 $minutes 分钟", fontSize = 12.sp) }
                )
            }
        }
    }

    if (viewModel.reminderTestMessage.isNotBlank()) {
        Text(viewModel.reminderTestMessage, Modifier.padding(8.dp), style = MaterialTheme.typography.bodySmall)
    }
}
