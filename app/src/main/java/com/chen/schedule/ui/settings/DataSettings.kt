package com.chen.schedule.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
internal fun DataSettings(viewModel: SettingsViewModel) {
    var showRecovery by rememberSaveable { mutableStateOf(false) }
    var showClearDialog by rememberSaveable { mutableStateOf(false) }
    var showCalendarSyncDialog by rememberSaveable { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let(viewModel::exportToUri) }
    val exportIcsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/calendar")
    ) { uri -> uri?.let(viewModel::exportIcsToUri) }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(viewModel::previewRestore) }

    if (viewModel.dataBusy) Text("正在处理数据…", Modifier.padding(8.dp))
    if (viewModel.dataMessage.isNotBlank()) Text(viewModel.dataMessage, Modifier.padding(8.dp))

    SettingsGroup {
        SettingsItem(
            title = "自动恢复点",
            subtitle = "最近 10 次修改前的数据，可预览后恢复",
            onClick = { showRecovery = true }
        )
        GroupDivider()
        SettingsItem(
            title = "同步到系统日历",
            subtitle = "直接写入手机系统日历",
            onClick = {
                viewModel.prepareCalendarSyncPreview()
                showCalendarSyncDialog = true
            }
        )
        GroupDivider()
        SettingsItem(
            title = "导出为日历 (.ics)",
            subtitle = "导出日历事件文件",
            onClick = { exportIcsLauncher.launch("课程表.ics") }
        )
        GroupDivider()
        SettingsItem(
            title = "备份数据",
            subtitle = "备份全部学期、课程及作息方案",
            onClick = { exportLauncher.launch("course_schedule_backup.json") }
        )
        GroupDivider()
        SettingsItem(
            title = "恢复数据",
            subtitle = "完整备份替换全部数据，课程 JSON 仅替换当前学期课程",
            onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) }
        )
        GroupDivider()
        SettingsItem(
            title = "清空当前学期课程",
            subtitle = "删除当前学期的所有课程数据",
            isDanger = true,
            onClick = { showClearDialog = true }
        )
    }

    if (showRecovery) {
        LaunchedEffect(Unit) { viewModel.refreshRecoveryPoints() }
        AlertDialog(
            onDismissRequest = { showRecovery = false },
            title = { Text("自动恢复点（最近 10 次）") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    if (viewModel.recoveryFiles.isEmpty()) {
                        Text("暂无恢复点。修改、导入、删除、清空及恢复前会自动保存。")
                    }
                    val formatter = SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault())
                    viewModel.recoveryFiles.forEach { file ->
                        TextButton(
                            enabled = !viewModel.dataBusy,
                            onClick = {
                                showRecovery = false
                                viewModel.previewRestore(Uri.fromFile(file))
                            }
                        ) {
                            val timestamp = formatter.format(file.lastModified())
                            val label = file.name.substringAfter('-').substringBefore('-')
                            Text("$timestamp · $label")
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showRecovery = false }) { Text("关闭") } }
        )
    }
    viewModel.restorePreview?.let { preview ->
        AlertDialog(
            onDismissRequest = viewModel::dismissRestore,
            title = { Text("确认恢复范围") },
            text = {
                Column {
                    Text(preview.summary)
                    if (viewModel.dataMessage.isNotBlank()) Text(viewModel.dataMessage)
                }
            },
            confirmButton = {
                TextButton(enabled = !viewModel.dataBusy, onClick = viewModel::confirmRestore) {
                    Text(if (viewModel.dataBusy) "正在恢复…" else "确认恢复")
                }
            },
            dismissButton = {
                TextButton(enabled = !viewModel.dataBusy, onClick = viewModel::dismissRestore) { Text("取消") }
            }
        )
    }
    if (showClearDialog) ClearCoursesDialog(viewModel) { showClearDialog = false }
    if (showCalendarSyncDialog) CalendarSyncDialog(viewModel) { showCalendarSyncDialog = false }
}
