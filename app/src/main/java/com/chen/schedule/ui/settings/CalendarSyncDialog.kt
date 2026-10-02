package com.chen.schedule.ui.settings

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chen.schedule.calendar.CalendarSyncManager

@Composable
internal fun CalendarSyncDialog(
    viewModel: SettingsViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val preview = viewModel.calendarSyncPreview
    val isSyncing = viewModel.isCalendarSyncing

    var selectedReminderMinutes by remember { mutableIntStateOf(20) }
    var showConfirmClearDialog by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val readGranted = permissions[Manifest.permission.READ_CALENDAR] ?: false
        val writeGranted = permissions[Manifest.permission.WRITE_CALENDAR] ?: false
        if (readGranted && writeGranted) {
            viewModel.syncToCalendar(selectedReminderMinutes) { success, msg ->
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                if (success) {
                    // 保持弹窗或关闭由用户决定
                }
            }
        } else {
            Toast.makeText(context, "未获得日历权限，无法写入系统日历", Toast.LENGTH_LONG).show()
        }
    }

    val onStartSync: () -> Unit = {
        if (CalendarSyncManager.hasCalendarPermissions(context)) {
            viewModel.syncToCalendar(selectedReminderMinutes) { _, msg ->
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            }
        } else {
            permissionLauncher.launch(
                arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
            )
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isSyncing) onDismiss() },
        shape = MaterialTheme.shapes.extraLarge,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.CalendarMonth,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Text("同步到系统日历", fontWeight = FontWeight.Bold, fontSize = 17.sp)
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (preview == null) {
                    Text("未找到当前学期，请先在「学期与作息」中配置当前学期。", fontSize = 13.sp)
                } else {
                    // 学期与排课概览卡片
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f))
                            .padding(12.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                preview.semesterName,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "课程：${preview.courseCount} 门",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    "预计生成：${preview.totalEvents} 条日程",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            if (preview.syncedCount > 0) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.padding(top = 2.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.CheckCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        "系统日历中已有该学期的 ${preview.syncedCount} 条日程",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    // 提醒时间选择
                    Text(
                        "日程提醒时间",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(6.dp))

                    val reminderOptions = listOf(
                        0 to "不提醒",
                        10 to "提前10分钟",
                        15 to "提前15分钟",
                        20 to "提前20分钟",
                        30 to "提前30分钟",
                        60 to "提前1小时"
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        reminderOptions.forEach { (minutes, label) ->
                            FilterChip(
                                selected = selectedReminderMinutes == minutes,
                                onClick = { selectedReminderMinutes = minutes },
                                label = { Text(label, fontSize = 12.sp) }
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // 说明提示
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(14.dp).padding(top = 2.dp)
                        )
                        Text(
                            "课程将写入专属独立日历分类（课程表 - ${preview.semesterName}），支持华为、小米、vivo、OPPO 等系统日历及手环/手表联动，与私人日程完全隔离。",
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }

                    if (isSyncing) {
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("正在写入系统日历...", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (preview != null && !isSyncing) {
                Button(
                    onClick = onStartSync,
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text(if (preview.syncedCount > 0) "重新覆盖同步" else "立即同步", fontSize = 13.sp)
                }
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (preview != null && preview.syncedCount > 0 && !isSyncing) {
                    OutlinedButton(
                        onClick = { showConfirmClearDialog = true },
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text("清除日历", color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                    }
                }
                TextButton(
                    onClick = onDismiss,
                    enabled = !isSyncing
                ) {
                    Text("关闭", fontSize = 13.sp)
                }
            }
        }
    )

    if (showConfirmClearDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmClearDialog = false },
            title = { Text("确认清除日历课程？", fontSize = 15.sp, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "将从系统日历中移除「${preview?.semesterName.orEmpty()}」对应的所有课程日程与提醒。不会影响应用内的课表数据和您的私人日历。",
                    fontSize = 12.5.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showConfirmClearDialog = false
                    viewModel.clearSyncedCalendar { _, msg ->
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Text("确认清除", color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmClearDialog = false }) {
                    Text("取消", fontSize = 13.sp)
                }
            }
        )
    }
}
