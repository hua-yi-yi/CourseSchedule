package com.chen.schedule.ui.import_

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun PasteImportCard(
    viewModel: ImportViewModel,
    clipboard: ClipboardManager,
    context: Context
) {
    var pasteText by remember { mutableStateOf("") }
    var showPasteArea by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showPasteArea = !showPasteArea },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.ContentCopy,
                        "粘贴",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("粘贴文本导入", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            "粘贴 AI 返回的 JSON 或 CSV 文本,自动识别格式",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Icon(
                    if (showPasteArea) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    "展开",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(17.dp)
                )
            }

            AnimatedVisibility(visible = showPasteArea) {
                Column {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "把 AI 返回的 JSON 或 CSV 文本直接粘贴到下面，自动识别格式。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))

                    OutlinedTextField(
                        value = pasteText,
                        onValueChange = { pasteText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                        placeholder = {
                            Text(
                                "在此粘贴 AI 返回的 JSON 或 CSV 内容...\n\n支持格式：\n{\"semesterName\":...} 或 [{\"name\":...}]\nname,teacher,classroom,dayOfWeek...",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        },
                        textStyle = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp
                        )
                    )

                    Spacer(Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Paste from clipboard
                        OutlinedButton(
                            onClick = {
                                val clip = clipboard.primaryClip
                                if (clip != null && clip.itemCount > 0) {
                                    pasteText = clip.getItemAt(0).text?.toString() ?: ""
                                    Toast.makeText(context, "已粘贴剪贴板内容", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "剪贴板为空，请先复制AI返回的内容", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.ContentCopy, "粘贴", modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("从剪贴板粘贴", fontSize = 13.sp)
                        }

                        // Import button
                        Button(
                            onClick = {
                                if (pasteText.isBlank()) {
                                    Toast.makeText(context, "请先粘贴内容", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                val trimmed = pasteText.trim()
                                // Auto-detect format: JSON starts with { or [, CSV has comma-separated headers
                                when {
                                    trimmed.startsWith("{") || trimmed.startsWith("[") ->
                                         viewModel.importFromJsonText(trimmed)
                                    trimmed.contains(",") && !trimmed.contains("{") ->
                                        viewModel.importFromCsvText(trimmed)
                                    else ->
                                        viewModel.importFromJsonText(trimmed) // try JSON first
                                }
                                pasteText = ""
                            },
                            modifier = Modifier.weight(1f),
                            enabled = pasteText.isNotBlank()
                        ) {
                            Icon(Icons.Default.UploadFile, "导入", modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("导入文本", fontSize = 13.sp)
                        }
                    }

                    Spacer(Modifier.height(4.dp))
                    Text(
                        "自动识别：以 { 或 [ 开头按 JSON 解析，否则按 CSV 解析",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
