package com.chen.schedule.ui.settings

import android.os.Build
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun ReminderSettings(viewModel: SettingsViewModel, requestNotificationPermission: () -> Unit) {
    // ===== 上课提醒 =====
    SettingsGroup(title = "上课提醒") {
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
                        "关闭状态，不会发送任何通知"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = viewModel.reminderEnabled,
                onCheckedChange = { enabled ->
                    if (enabled && Build.VERSION.SDK_INT >= 33) {
                        requestNotificationPermission()
                    }
                    viewModel.updateReminderEnabled(enabled)
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
        GroupDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("上课中常驻看板", style = MaterialTheme.typography.bodyMedium, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text(
                    if (!viewModel.reminderEnabled) {
                        "需先开启上方提醒总开关"
                    } else if (viewModel.reminderOngoing) {
                        "正在上课时常驻显示教室、节次与下课时间，下课后自动清除"
                    } else {
                        "已关闭，上课时不显示常驻卡片"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = viewModel.reminderOngoing,
                enabled = viewModel.reminderEnabled,
                onCheckedChange = { enabled ->
                    viewModel.updateReminderOngoing(enabled)
                }
            )
        }
    }

    Spacer(Modifier.height(16.dp))
}
