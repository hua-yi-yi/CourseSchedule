package com.chen.schedule.ui.import_

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.chen.schedule.domain.model.DayOfWeek
import com.chen.schedule.util.CsvImporter
import com.chen.schedule.util.JsonImporter

private fun buildJsonPrompt(): String {
    return """请根据我提供的课程表截图，识别其中的课程信息，生成以下格式的JSON代码。

格式要求（严格遵守）：
{
  "semesterName": "<学期名称，从截图推断或留空>",
  "courses": [
    {
      "name": "<课程名称>",
      "teacher": "<教师姓名，没有则留空>",
      "classroom": "<教室/地点，没有则留空>",
      "dayOfWeek": <星期几，1=周一 2=周二 ... 7=周日>,
      "startSlot": <开始节次，数字如1>,
      "endSlot": <结束节次，数字如2>,
      "startWeek": <起始周，数字如1>,
      "endWeek": <结束周，数字如16>,
      "weekType": "<all=每周 odd=单周 even=双周>",
      "note": "<备注/课程号等，没有则留空>"
    }
  ]
}

重要规则：
- dayOfWeek: 1=周一, 2=周二, 3=周三, 4=周四, 5=周五, 6=周六, 7=周日
- startSlot和endSlot必须分别为两个独立的数字，例如第1-2节课应写为 "startSlot": 1, "endSlot": 2。严禁写成 "startSlot": "1-2" 这种合并格式
- startWeek和endWeek同样是两个独立的数字，不要合并
- weekType: 如果截图没标注单双周，默认用 "all"
- 只输出JSON代码，不要输出任何解释文字""".trimIndent()
}

private fun buildCsvPrompt(): String {
    return """请根据我提供的课程表截图，识别其中的课程信息，生成CSV内容。

第一行必须是表头（不要省略）：
name,teacher,classroom,dayOfWeek,startSlot,endSlot,startWeek,endWeek,weekType,note

字段说明：
- name: 课程名称（必填）
- teacher: 教师姓名（没有则留空）
- classroom: 教室/地点（没有则留空）
- dayOfWeek: 1=周一, 2=周二, 3=周三, 4=周四, 5=周五, 6=周六, 7=周日
- startSlot: 开始节次数字，如 1
- endSlot: 结束节次数字，如 2
- startWeek: 起始周数字，如 1
- endWeek: 结束周数字，如 16
- weekType: all=每周, odd=单周, even=双周（不确定就填all）
- note: 备注/课程号（没有则留空）

重要规则：
- 每个课程一行
- 字段之间用英文逗号分隔
- startSlot和endSlot必须分别为两个独立数字，例如第1-2节课应写为 1,2。严禁写成 "1-2" 合并格式
- startWeek和endWeek同理，必须分开
- 如果字段内容包含逗号，用英文双引号包裹
- 只输出CSV内容，不要输出任何解释文字""".trimIndent()
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(
    onScraperLogin: () -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: ImportViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var showJsonGuide by remember { mutableStateOf(false) }
    var showCsvGuide by remember { mutableStateOf(false) }

    val jsonLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? -> uri?.let { viewModel.importFromJsonUri(it) } }

    val csvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? -> uri?.let { viewModel.importFromCsvUri(it) } }

    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("导入课表", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                }
            )
        },
        snackbarHost = {
            if (state.message.isNotBlank()) {
                Snackbar(
                    modifier = Modifier.padding(16.dp),
                    containerColor = if (state.isError)
                        MaterialTheme.colorScheme.errorContainer
                    else
                        MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(state.message)
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {

            // ===== AI 截图识别导入 =====
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            1.5.dp,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                            MaterialTheme.shapes.large
                        ),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                    ),
                    shape = MaterialTheme.shapes.large
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // Title row
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "AI 截图识别导入",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Spacer(Modifier.height(8.dp))

                        Text(
                            "截图课程表 → 发给 AI → 生成文件 → 导入",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )

                        Spacer(Modifier.height(8.dp))

                        // Step by step
                        StepRow("1", "对课程表截图（教务系统、Excel课表、纸质课表都可以）")
                        StepRow("2", "点击下面按钮复制「格式说明」，发给任意 AI（ChatGPT / Claude / Kimi / 豆包 等）")
                        StepRow("3", "把截图也一起发给 AI，AI 会生成对应的文件内容")
                        StepRow("4", "把 AI 返回的内容保存为 .json 或 .csv 文件，再用下方按钮导入")

                        Spacer(Modifier.height(12.dp))

                        // Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    clipboard.setPrimaryClip(ClipData.newPlainText("json_prompt", buildJsonPrompt()))
                                    Toast.makeText(context, "JSON格式说明已复制！去粘贴给AI，同时发送截图", Toast.LENGTH_LONG).show()
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Icon(Icons.Default.ContentCopy, "复制", modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("复制 JSON 格式", fontSize = 13.sp)
                            }

                            OutlinedButton(
                                onClick = {
                                    clipboard.setPrimaryClip(ClipData.newPlainText("csv_prompt", buildCsvPrompt()))
                                    Toast.makeText(context, "CSV格式说明已复制！去粘贴给AI，同时发送截图", Toast.LENGTH_LONG).show()
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.ContentCopy, "复制", modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("复制 CSV 格式", fontSize = 13.sp)
                            }
                        }

                        Spacer(Modifier.height(4.dp))

                        Text(
                            "提示：点击按钮后打开 ChatGPT/Claude/Kimi 等 AI 工具，粘贴 + 发送截图即可",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // ===== 粘贴文本导入（优先） =====
            item {
                Spacer(Modifier.height(20.dp))
                Text("粘贴文本导入", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(
                    "把 AI 返回的 JSON 或 CSV 文本直接粘贴到下方，自动识别格式导入",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                PasteImportCard(viewModel = viewModel, clipboard = clipboard, context = context)
            }

            // ===== 手动导入文件 =====
            item {
                Spacer(Modifier.height(20.dp))
                Text("手动导入文件", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(
                    "选择已准备好的 JSON 或 CSV 文件导入课程表",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { jsonLauncher.launch(arrayOf("application/json", "*/*")) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Description, "JSON")
                        Spacer(Modifier.width(8.dp))
                        Text("导入 JSON")
                    }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = { csvLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "*/*")) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.TableChart, "CSV")
                        Spacer(Modifier.width(8.dp))
                        Text("导入 CSV")
                    }
                }
            }

            // ===== JSON 格式说明 =====
            item {
                Spacer(Modifier.height(12.dp))

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
                                .clickable { showJsonGuide = !showJsonGuide },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Description, "JSON", tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(8.dp))
                                Text("JSON 格式说明", fontWeight = FontWeight.Bold)
                            }
                            Icon(
                                if (showJsonGuide) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                "展开"
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
                                    Icon(Icons.Default.ContentCopy, "复制", modifier = Modifier.height(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("复制 JSON 示例", fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
            }

            // ===== CSV 格式说明 =====
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
                                .clickable { showCsvGuide = !showCsvGuide },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.TableChart, "CSV", tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(8.dp))
                                Text("CSV 格式说明", fontWeight = FontWeight.Bold)
                            }
                            Icon(
                                if (showCsvGuide) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                "展开"
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
                                    Icon(Icons.Default.ContentCopy, "复制", modifier = Modifier.height(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("复制 CSV 示例", fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
            }

            // ===== 预览区域 =====
            if (state.previewCourses.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(24.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("预览 (${state.previewCourses.size} 门课程)", style = MaterialTheme.typography.titleMedium)
                        Button(onClick = viewModel::confirmImport) {
                            Text("确认导入")
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                items(state.previewCourses) { course ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(course.name, fontWeight = FontWeight.Bold)
                            Row {
                                if (course.teacher.isNotBlank()) {
                                    Text(course.teacher, style = MaterialTheme.typography.bodySmall)
                                    Text(" · ", style = MaterialTheme.typography.bodySmall)
                                }
                                Text(
                                    "${DayOfWeek.entries.find { it.index == course.dayOfWeek }?.label ?: ""} ${course.startSlot}-${course.endSlot}节",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Text(
                                "${course.startWeek}-${course.endWeek}周 ${course.weekType.label}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                item { Spacer(Modifier.height(16.dp)) }
            }

            // ===== 教务系统导入 =====
            item {
                HorizontalDivider()
                Spacer(Modifier.height(24.dp))

                Text("教务系统自动导入", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(
                    "通过适配器自动从学校教务系统抓取课程表，首次使用需填写教务地址并登录",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.CloudDownload,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 12.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text("正方教务系统", fontWeight = FontWeight.Bold)
                            Text(
                                "适用于使用正方教务系统的高校",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                Button(
                    onClick = onScraperLogin,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.UploadFile, "导入")
                    Spacer(Modifier.width(8.dp))
                    Text("教务系统登录导入")
                }
            }
        }
    }
}

@Composable
private fun StepRow(num: String, text: String) {
    Row(
        modifier = Modifier.padding(vertical = 2.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            Text(
                num,
                color = MaterialTheme.colorScheme.onPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun PasteImportCard(
    viewModel: ImportViewModel,
    clipboard: ClipboardManager,
    context: Context
) {
    var pasteText by remember { mutableStateOf("") }
    var showPasteArea by remember { mutableStateOf(true) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showPasteArea = !showPasteArea },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ContentCopy, "粘贴", tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("粘贴文本导入", fontWeight = FontWeight.Bold)
                }
                Icon(
                    if (showPasteArea) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    "展开"
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
                            Icon(Icons.Default.ContentCopy, "粘贴", modifier = Modifier.size(16.dp))
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
                            Icon(Icons.Default.UploadFile, "导入", modifier = Modifier.size(16.dp))
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

@Composable
private fun CodeBlock(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.9f))
            .horizontalScroll(rememberScrollState())
            .padding(12.dp)
    ) {
        Text(
            text = text,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            lineHeight = 16.sp,
            color = MaterialTheme.colorScheme.inverseOnSurface
        )
    }
}

@Composable
private fun JsonFieldTable() {
    Column {
        FieldRow("name", "课程名称（必填）", "高等数学")
        FieldRow("teacher", "教师姓名", "张老师")
        FieldRow("classroom", "教室/地点", "教学楼101")
        FieldRow("dayOfWeek", "星期几 (1=周一)", "1")
        FieldRow("startSlot", "开始节次", "1")
        FieldRow("endSlot", "结束节次", "2")
        FieldRow("startWeek", "起始周", "1")
        FieldRow("endWeek", "结束周", "16")
        FieldRow("weekType", "all/odd/even", "all")
        FieldRow("color", "颜色值（可省略）", "0xFF4CAF50")
        FieldRow("note", "备注（可省略）", "")
    }
}

@Composable
private fun CsvFieldTable() {
    Column {
        FieldRow("name", "课程名称（必填）", "高等数学")
        FieldRow("teacher", "教师姓名", "张老师")
        FieldRow("classroom", "教室", "教学楼101")
        FieldRow("dayOfWeek", "星期: 1~7", "1")
        FieldRow("startSlot", "开始节次", "1")
        FieldRow("endSlot", "结束节次", "2")
        FieldRow("startWeek", "起始周", "1")
        FieldRow("endWeek", "结束周", "16")
        FieldRow("weekType", "all/odd/even", "all")
        FieldRow("note", "备注", "")
    }
}

@Composable
private fun FieldRow(field: String, desc: String, example: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp)
    ) {
        Text(
            field,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(90.dp)
        )
        Text(
            desc,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            "例: $example",
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.width(90.dp)
        )
    }
}
