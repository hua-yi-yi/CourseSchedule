package com.chen.schedule.ui.schedule

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleConfigScreen(
    onNavigateBack: () -> Unit,
    viewModel: ScheduleConfigViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var pendingSeason by remember { mutableStateOf<String?>(null) }
    var importText by remember { mutableStateOf("") }
    var showFormatHelp by remember { mutableStateOf(false) }

    LaunchedEffect(state.message) {
        if (state.message.isNotBlank()) {
            Toast.makeText(context, state.message, Toast.LENGTH_SHORT).show()
            viewModel.clearMessage()
        }
    }

    pendingSeason?.let { season ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { pendingSeason = null },
            title = { Text("套用作息模板") },
            text = { Text("将替换当前全部作息时间，包括自定义时间。此操作为手动切换。") },
            confirmButton = { androidx.compose.material3.TextButton(onClick = {
                viewModel.applyTimeSlotSeason(season); pendingSeason = null
            }) { Text("替换") } },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { pendingSeason = null }) { Text("取消") } }
        )
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("作息时间与学期配置", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            // Quick week calculator card - at the very top, hard to miss
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "快捷推算开学日期",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "填写当前是第几周，自动反推出开学日期并填入下方日期栏",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Bottom
                        ) {
                            OutlinedTextField(
                                value = state.currentWeekHint,
                                onValueChange = viewModel::updateCurrentWeekHint,
                                label = { Text("当前是第几周") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                placeholder = { Text("如 12") }
                            )
                            Spacer(Modifier.width(8.dp))
                            Button(
                                onClick = viewModel::fillStartDateFromWeek,
                                enabled = state.currentWeekHint.isNotBlank(),
                                modifier = Modifier.height(56.dp)
                            ) {
                                Text("推算")
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            // Semester section
            item {
                Text("学期设置", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = state.semesterName,
                    onValueChange = viewModel::updateSemesterName,
                    label = { Text("学期名称") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("例：2025-2026 第一学期") }
                )

                Spacer(Modifier.height(8.dp))

                Text("开学日期", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = state.semesterYear,
                        onValueChange = viewModel::updateSemesterYear,
                        label = { Text("年") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        placeholder = { Text("2025") }
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = state.semesterMonth,
                        onValueChange = viewModel::updateSemesterMonth,
                        label = { Text("月") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        placeholder = { Text("09") }
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = state.semesterDay,
                        onValueChange = viewModel::updateSemesterDay,
                        label = { Text("日") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        placeholder = { Text("01") }
                    )
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = state.semesterTotalWeeks.toString(),
                    onValueChange = { it.toIntOrNull()?.let { w -> viewModel.updateSemesterTotalWeeks(w) } },
                    label = { Text("总周数") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(Modifier.height(8.dp))

                Button(
                    onClick = viewModel::saveSemester,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (state.semester != null) "更新学期" else "创建学期")
                }

                Spacer(Modifier.height(24.dp))
                HorizontalDivider()
                Spacer(Modifier.height(16.dp))
            }

            // Time slots section
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("作息时间（节次配置）", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }

                Spacer(Modifier.height(4.dp))

                // Season selector
                Row {
                    OutlinedButton(onClick = { pendingSeason = "summer" }) {
                        Text("夏季作息")
                    }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = { pendingSeason = "winter" }) {
                        Text("冬季作息")
                    }
                }

                Spacer(Modifier.height(12.dp))
            }

            // AI import card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    ),
                    border = CardDefaults.outlinedCardBorder().copy(
                        width = 1.dp,
                        brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary)
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Upload,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text(
                                "AI 截图识别导入作息时间",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Spacer(Modifier.height(8.dp))

                        // Step 1
                        StepRow("1", "截图作息时间表（课程表里的节次时间）")

                        // Step 2
                        StepRow("2", "点击下方按钮复制格式说明")

                        Spacer(Modifier.height(4.dp))
                        Button(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setText(buildTimeSlotPrompt())
                                Toast.makeText(context, "已复制，可发送给 AI", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(Icons.Default.ContentCopy, "复制", modifier = Modifier.padding(end = 6.dp))
                            Text("复制 AI 格式说明")
                        }

                        // Step 3
                        StepRow("3", "将截图 + 格式说明发送给 AI（DeepSeek / ChatGPT / 豆包等）")

                        // Step 4
                        StepRow("4", "将 AI 返回的文本粘贴到下方，点击导入")

                        Spacer(Modifier.height(8.dp))

                        OutlinedTextField(
                            value = importText,
                            onValueChange = { importText = it },
                            label = { Text("粘贴 AI 返回的作息时间文本") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 4,
                            maxLines = 8,
                            placeholder = {
                                Text(
                                    "1 08:00-08:45 第一节课\n" +
                                    "2 08:55-09:40 第二节课\n" +
                                    "3 10:00-10:45 第三节课\n" +
                                    "..."
                                )
                            }
                        )

                        Spacer(Modifier.height(8.dp))

                        Row {
                            OutlinedButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = clipboard.primaryClip
                                    if (clip != null && clip.itemCount > 0) {
                                        importText = clip.getItemAt(0).text?.toString() ?: ""
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.ContentPaste, "粘贴", modifier = Modifier.padding(end = 6.dp))
                                Text("从剪贴板粘贴")
                            }
                            Spacer(Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    viewModel.importTimeSlotsFromText(importText)
                                },
                                modifier = Modifier.weight(1f),
                                enabled = importText.isNotBlank()
                            ) {
                                Text("一键导入作息时间")
                            }
                        }

                        // Format help toggle
                        Spacer(Modifier.height(4.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showFormatHelp = !showFormatHelp },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "文本格式说明",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Icon(
                                if (showFormatHelp) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        AnimatedVisibility(visible = showFormatHelp) {
                            Column(modifier = Modifier.padding(top = 8.dp)) {
                                Text(
                                    "每行一个节次，格式：",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                CodeBlock(
                                    "1 08:00-08:45 第一节课\n" +
                                    "2 08:55-09:40 第二节课"
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "节次名称可省略（自动生成「第X节」），时间使用24小时制 HH:MM",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
            }

            // Time slot list
            items(state.timeSlots) { slot ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "${slot.slotNumber}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(
                            slot.name.ifBlank { "第${slot.slotNumber}节" },
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "${slot.startTime} - ${slot.endTime}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(8.dp))
                        IconButton(onClick = { viewModel.deleteTimeSlot(slot) }) {
                            Icon(
                                Icons.Default.Delete,
                                "删除",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            // Add time slot button
            item {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = viewModel::addTimeSlot,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, "添加节次")
                    Spacer(Modifier.width(8.dp))
                    Text("添加节次")
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun StepRow(number: String, text: String) {
    Row(
        modifier = Modifier.padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            number,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(end = 8.dp)
        )
        Text(
            text,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun CodeBlock(code: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1E1E1E)
        )
    ) {
        Text(
            code,
            modifier = Modifier.padding(12.dp),
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFFD4D4D4)
        )
    }
}

private fun buildTimeSlotPrompt(): String {
    return """请根据我提供的作息时间表截图，识别其中的节次和对应的时间，生成以下格式的文本。

格式要求（严格遵守）：
每行一个节次，格式为：节次编号 开始时间-结束时间 节次名称

例如：
1 08:00-08:45 第一节课
2 08:55-09:40 第二节课
3 10:00-10:45 第三节课
4 10:55-11:40 第四节课

重要规则：
- 时间使用24小时制，格式为HH:MM
- 节次名称可选，如不提供则自动生成"第X节"
- 只输出文本，不要输出任何解释文字
- 如果截图中有午休等非上课时间段，跳过不输出"""
}
