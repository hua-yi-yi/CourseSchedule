package com.chen.schedule.ui.import_

import android.content.ClipData
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
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chen.schedule.util.CsvImporter
import com.chen.schedule.util.JsonImporter
import androidx.compose.foundation.lazy.LazyListScope

internal fun LazyListScope.fileImportItems(
    viewModel: ImportViewModel,
    context: Context,
    clipboard: ClipboardManager,
    showJsonGuide: Boolean,
    showCsvGuide: Boolean,
    onToggleJsonGuide: () -> Unit,
    onToggleCsvGuide: () -> Unit,
    onScraperLogin: () -> Unit,
    onReadJson: (Array<String>) -> Unit,
    onReadCsv: (Array<String>) -> Unit
) {
    // AI 截图识别(折叠,小字)
    item {
        OtherMethodCard(
            title = "AI 截图识别",
            subtitle = "复制格式说明发给 AI,再把返回内容导入"
        ) {
            StepRow("1", "对课程表截图(教务系统、Excel 课表、纸质课表都可以)")
            StepRow("2", "点击下方按钮复制「格式说明」,发给任意 AI(ChatGPT / Kimi / 豆包 等)")
            StepRow("3", "把截图也一起发给 AI,AI 会生成对应内容")
            StepRow("4", "把返回内容粘贴到「粘贴文本导入」,或保存为 .json/.csv 文件导入")
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        clipboard.setPrimaryClip(ClipData.newPlainText("json_prompt", buildJsonPrompt()))
                        Toast.makeText(context, "JSON格式说明已复制！去粘贴给AI，同时发送截图", Toast.LENGTH_LONG).show()
                    },
                    modifier = Modifier.weight(1f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, "复制", modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("复制 JSON 格式", fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick = {
                        clipboard.setPrimaryClip(ClipData.newPlainText("csv_prompt", buildCsvPrompt()))
                        Toast.makeText(context, "CSV格式说明已复制！去粘贴给AI，同时发送截图", Toast.LENGTH_LONG).show()
                    },
                    modifier = Modifier.weight(1f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, "复制", modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("复制 CSV 格式", fontSize = 12.sp)
                }
            }
        }
    }

    // 粘贴文本导入(默认折叠)
    item {
        PasteImportCard(viewModel = viewModel, clipboard = clipboard, context = context)
    }

    // 文件导入(小按钮)
    item {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { onReadJson(arrayOf("application/json", "*/*")) },
                modifier = Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp)
            ) {
                Icon(Icons.Default.Description, "JSON", modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(4.dp))
                Text("导入 JSON 文件", fontSize = 12.sp)
            }
            OutlinedButton(
                onClick = { onReadCsv(arrayOf("text/csv", "text/comma-separated-values", "*/*")) },
                modifier = Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp)
            ) {
                Icon(Icons.Default.TableChart, "CSV", modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(4.dp))
                Text("导入 CSV 文件", fontSize = 12.sp)
            }
        }
    }

    // 正方教务系统(其他学校,小字行)
    item {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .clickable(onClick = onScraperLogin),
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.CloudDownload,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("正方教务系统(其他学校)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "填写教务地址 + 学号密码登录抓取",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = "进入",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }

    // ===== JSON 格式说明(折叠) =====
    item {
        Spacer(Modifier.height(8.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            )
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggleJsonGuide() },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Description, "JSON", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("JSON 格式说明", fontWeight = FontWeight.Bold)
                    }
                    Icon(
                        if (showJsonGuide) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        "展开",
                        modifier = Modifier.size(18.dp)
                    )
                }

                AnimatedVisibility(visible = showJsonGuide) {
                    Column {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "创建一个 .json 文件，内容格式如下。可用手机文本编辑器或电脑记事本创建。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))

                        val jsonSample = JsonImporter.getSampleJson()
                        CodeBlock(text = jsonSample)

                        Spacer(Modifier.height(8.dp))

                        Text("字段说明：", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        JsonFieldTable()

                        Spacer(Modifier.height(8.dp))

                        OutlinedButton(
                            onClick = {
                                clipboard.setPrimaryClip(ClipData.newPlainText("sample", jsonSample))
                                Toast.makeText(context, "JSON 示例已复制到剪贴板", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Icon(Icons.Default.ContentCopy, "复制", modifier = Modifier.size(13.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("复制 JSON 示例", fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }

    // ===== CSV 格式说明(折叠) =====
    item {
        Spacer(Modifier.height(8.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            )
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggleCsvGuide() },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.TableChart, "CSV", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("CSV 格式说明", fontWeight = FontWeight.Bold)
                    }
                    Icon(
                        if (showCsvGuide) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        "展开",
                        modifier = Modifier.size(18.dp)
                    )
                }

                AnimatedVisibility(visible = showCsvGuide) {
                    Column {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "CSV 文件可用 Excel、WPS 或 Google Sheets 创建，编辑后导出为 .csv。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))

                        val csvSample = CsvImporter.getSampleCsv()
                        CodeBlock(text = csvSample)

                        Spacer(Modifier.height(8.dp))

                        Text("列说明：", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        CsvFieldTable()

                        Spacer(Modifier.height(8.dp))

                        OutlinedButton(
                            onClick = {
                                clipboard.setPrimaryClip(ClipData.newPlainText("sample", csvSample))
                                Toast.makeText(context, "CSV 示例已复制到剪贴板", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Icon(Icons.Default.ContentCopy, "复制", modifier = Modifier.size(13.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("复制 CSV 示例", fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}
