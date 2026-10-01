package com.chen.schedule.ui.settings

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

@Composable
internal fun ClearCoursesDialog(viewModel: SettingsViewModel, onDismiss: () -> Unit) {
    // 清空数据确认弹窗
    run {
        AlertDialog(
            onDismissRequest = { onDismiss() },
            shape = MaterialTheme.shapes.extraLarge,
            title = { Text("确认清空", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = { Text("确定要删除当前学期的所有课程数据吗？操作前会自动保存恢复点，可在「自动恢复点」中找回。", fontSize = 13.sp) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearAllData()
                    onDismiss()
                }) {
                    Text("确认清空", color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { onDismiss() }) {
                    Text("取消", fontSize = 13.sp)
                }
            }
        )
    }


}
