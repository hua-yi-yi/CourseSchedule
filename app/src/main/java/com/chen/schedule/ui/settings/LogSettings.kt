package com.chen.schedule.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chen.schedule.ui.import_.ImportSnapshotHistoryDialog
import com.chen.schedule.ui.theme.ScheduleStyle
import com.chen.schedule.util.DiagnosticReporter
import com.chen.schedule.util.log.ImportedLogMeta
import com.chen.schedule.util.log.LogCodec
import com.chen.schedule.util.log.LogEntry
import com.chen.schedule.util.log.LogLevel
import com.chen.schedule.util.log.LogStorageStats
import com.chen.schedule.util.log.LogUploader
import com.chen.schedule.util.log.ParsedLogDocument
import kotlinx.coroutines.launch

@Composable
internal fun LogSettings(
    viewModel: SettingsViewModel,
    onPickExternalLog: () -> Unit,
    onExportRuntimeLog: () -> Unit
) {
    var uploadTargetDoc by remember { mutableStateOf<ParsedLogDocument?>(null) }
    var showUploadDialog by remember { mutableStateOf(false) }
    var showImportSnapshotDialog by remember { mutableStateOf(false) }
    var showClearRuntimeConfirm by remember { mutableStateOf(false) }

    val stats = viewModel.logStats

    // ===== 1. 运行日志保存与自主上传 =====
    SettingsGroup(title = "运行日志与自主上传") {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "本地保存运行与崩溃日志",
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                val crashHint = if (stats.crashFileCount > 0) {
                    " · 含 ${stats.crashFileCount} 条崩溃记录" +
                        (stats.latestCrashTime?.let { " ($it)" } ?: "")
                } else {
                    " · 暂无崩溃记录"
                }
                Text(
                    if (viewModel.logSaveEnabled) {
                        "自动隐私脱敏并滚动清理 · 已占 ${stats.formatSize()}$crashHint"
                    } else {
                        "已暂停记录新日志 · 本地仍保留 ${stats.formatSize()}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.5.sp,
                    color = if (stats.crashFileCount > 0) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = viewModel.logSaveEnabled,
                onCheckedChange = viewModel::updateLogSaveEnabled
            )
        }

        GroupDivider()

        SettingsItem(
            title = "查看、导出与分享日志",
            subtitle = "应用内检索运行日志与报错堆栈，支持导出 .log 文件或分享发送",
            onClick = viewModel::openRuntimeLogViewer
        )

        GroupDivider()

        SettingsItem(
            title = "自主上传诊断日志",
            subtitle = "手动确认后将脱敏日志上传至指定服务器，或一键提交诊断报告",
            trailing = {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        "自主上传",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
            },
            onClick = {
                viewModel.clearLogUploadFeedback()
                uploadTargetDoc = null
                showUploadDialog = true
            }
        )
    }

    Spacer(Modifier.height(16.dp))

    // ===== 2. 专门的导入日志专区 =====
    SettingsGroup(title = "导入日志专区") {
        Text(
            "专门用于导入外部日志文件进行离线解析、报错定位与二次上报，同时集中管理本机课表导入日志。",
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 6.dp),
            style = MaterialTheme.typography.bodySmall,
            fontSize = 11.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        SettingsItem(
            title = "导入外部日志文件",
            subtitle = "选择 .log / .txt / .json 日志、Logcat 或诊断包，自动识别格式并解析",
            trailing = {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        "选择文件",
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
            },
            onClick = onPickExternalLog
        )

        if (viewModel.importedLogs.isNotEmpty()) {
            GroupDivider()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "已导入的外部日志 (${viewModel.importedLogs.size})",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(
                        onClick = viewModel::clearAllImportedLogs,
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                    ) {
                        Text("清空列表", fontSize = 11.5.sp, color = MaterialTheme.colorScheme.error)
                    }
                }

                viewModel.importedLogs.take(5).forEach { item ->
                    ImportedLogRow(
                        meta = item,
                        onOpen = { viewModel.openImportedLog(item) },
                        onDelete = { viewModel.deleteImportedLog(item.id) }
                    )
                }
            }
        }

        GroupDivider()

        SettingsItem(
            title = "课表导入日志（提取快照）",
            subtitle = if (viewModel.importSnapshotCount > 0) {
                "已保存最近 ${viewModel.importSnapshotCount} 次课表导入日志与脱敏表格片段 · 点击查看或上传"
            } else {
                "记录每次教务/文件导入的解析结果与脱敏源码（当前暂无记录）"
            },
            onClick = { showImportSnapshotDialog = true }
        )
    }

    Spacer(Modifier.height(16.dp))

    // ===== 日志查看器弹窗（兼容本机实时日志 & 外部导入日志） =====
    viewModel.activeLogDocument?.let { doc ->
        LogViewerDialog(
            document = doc,
            isRuntime = viewModel.activeLogIsRuntime,
            onDismiss = viewModel::closeLogViewer,
            onExportFile = onExportRuntimeLog,
            onShare = {
                if (viewModel.activeLogIsRuntime) viewModel.shareRuntimeLogs()
                else viewModel.shareLogDocument(doc)
            },
            onRequestUpload = {
                viewModel.clearLogUploadFeedback()
                uploadTargetDoc = if (viewModel.activeLogIsRuntime) null else doc
                showUploadDialog = true
            },
            onRequestClearRuntime = { showClearRuntimeConfirm = true }
        )
    }

    // ===== 清空本机日志确认弹窗 =====
    if (showClearRuntimeConfirm) {
        AlertDialog(
            onDismissRequest = { showClearRuntimeConfirm = false },
            title = { Text("清空本地运行日志", fontWeight = FontWeight.Bold) },
            text = { Text("确定要删除本机已保存的全部运行日志与崩溃日志记录吗？此操作不可撤销（不影响已导入的外部日志和课表数据）。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearRuntimeConfirm = false
                        viewModel.clearRuntimeLogs()
                    }
                ) {
                    Text("确认清空", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearRuntimeConfirm = false }) { Text("取消") }
            }
        )
    }

    // ===== 自主上传弹窗 =====
    if (showUploadDialog) {
        LogUploadDialog(
            viewModel = viewModel,
            targetDocument = uploadTargetDoc,
            onDismiss = { showUploadDialog = false }
        )
    }

    // ===== 课表导入日志历史快照弹窗 =====
    if (showImportSnapshotDialog) {
        ImportSnapshotHistoryDialog(
            onDismiss = {
                showImportSnapshotDialog = false
                viewModel.refreshLogState()
            }
        )
    }
}

@Composable
private fun ImportedLogRow(
    meta: ImportedLogMeta,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen() },
        shape = ScheduleStyle.CardShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        meta.fileName,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(Modifier.width(6.dp))
                    Surface(
                        color = if (meta.errorAndCrashCount > 0) {
                            MaterialTheme.colorScheme.errorContainer
                        } else {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        },
                        shape = MaterialTheme.shapes.extraSmall
                    ) {
                        Text(
                            if (meta.errorAndCrashCount > 0) "错误 ${meta.errorAndCrashCount}"
                            else "${meta.totalEntries} 条",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (meta.errorAndCrashCount > 0) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    "${meta.formatLabel} · ${LogStorageStats.formatByteSize(meta.fileSizeBytes)} · ${meta.formattedImportedAt.substringAfter(' ')}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "删除导入日志",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LogViewerDialog(
    document: ParsedLogDocument,
    isRuntime: Boolean,
    onDismiss: () -> Unit,
    onExportFile: () -> Unit,
    onShare: () -> Unit,
    onRequestUpload: () -> Unit,
    onRequestClearRuntime: () -> Unit
) {
    val context = LocalContext.current
    // 0 = 全部, 1 = 错误与崩溃, 2 = 警告及以上
    var filterTab by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var expandedIndex by remember { mutableStateOf<Int?>(null) }

    val errorCount = document.errorAndCrashCount
    val warnAndUpCount = document.entries.count { it.level.priority >= LogLevel.WARN.priority }

    val filteredEntries = remember(document.entries, filterTab, searchQuery) {
        val q = searchQuery.trim()
        document.entries.filter { entry ->
            val levelOk = when (filterTab) {
                1 -> entry.level == LogLevel.ERROR || entry.level == LogLevel.CRASH
                2 -> entry.level.priority >= LogLevel.WARN.priority
                else -> true
            }
            if (!levelOk) return@filter false
            if (q.isEmpty()) return@filter true
            entry.message.contains(q, ignoreCase = true) ||
                entry.tag.contains(q, ignoreCase = true) ||
                (entry.stackTrace?.contains(q, ignoreCase = true) == true)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    document.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${document.formatLabel} · 共 ${document.entries.size} 条（错误/崩溃 $errorCount 条）",
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (document.metadata.isNotEmpty()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = ScheduleStyle.CardShape,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            document.metadata.entries.take(4).forEach { (k, v) ->
                                Text(
                                    "$k：$v",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("搜索关键词、Tag 或异常名…", fontSize = 12.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Clear, contentDescription = "清除", modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = filterTab == 0,
                        onClick = { filterTab = 0 },
                        label = { Text("全部 (${document.entries.size})", fontSize = 11.5.sp) }
                    )
                    FilterChip(
                        selected = filterTab == 1,
                        onClick = { filterTab = 1 },
                        label = { Text("错误/崩溃 ($errorCount)", fontSize = 11.5.sp) }
                    )
                    FilterChip(
                        selected = filterTab == 2,
                        onClick = { filterTab = 2 },
                        label = { Text("警告及以上 ($warnAndUpCount)", fontSize = 11.5.sp) }
                    )
                }

                if (filteredEntries.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (document.entries.isEmpty()) "当前暂无日志记录"
                            else "没有符合筛选条件的日志条目",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .heightIn(max = 340.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(
                            count = filteredEntries.size,
                            key = { idx -> "${idx}_${filteredEntries[idx].formattedTime}_${filteredEntries[idx].tag}" }
                        ) { idx ->
                            val entry = filteredEntries[idx]
                            val isExpanded = expandedIndex == idx ||
                                (expandedIndex == null && entry.level == LogLevel.CRASH && idx == 0)
                            LogEntryItemCard(
                                entry = entry,
                                expanded = isExpanded,
                                onToggle = {
                                    expandedIndex = if (expandedIndex == idx) -1 else idx
                                }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalArrangement = Arrangement.Center
                ) {
                    if (isRuntime && document.entries.isNotEmpty()) {
                        TextButton(
                            onClick = onRequestClearRuntime,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("清空", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                        }
                    }
                    TextButton(
                        onClick = {
                            val copyText = document.rawText.ifBlank {
                                LogCodec.buildExportDocument(document.entries, document.metadata)
                            }
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            clipboard?.setPrimaryClip(ClipData.newPlainText("诊断日志", copyText.take(150_000)))
                            Toast.makeText(context, "日志内容已复制到剪贴板", Toast.LENGTH_SHORT).show()
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text("复制", fontSize = 12.sp)
                    }
                    if (isRuntime) {
                        TextButton(
                            onClick = onExportFile,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("导出文件", fontSize = 12.sp)
                        }
                    }
                    TextButton(
                        onClick = onShare,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(3.dp))
                        Text("分享", fontSize = 12.sp)
                    }
                    TextButton(
                        onClick = onRequestUpload,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text("自主上传", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text("关闭") }
        }
    )
}

@Composable
private fun LogEntryItemCard(
    entry: LogEntry,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    val badgeBg = when (entry.level) {
        LogLevel.CRASH, LogLevel.ERROR -> MaterialTheme.colorScheme.errorContainer
        LogLevel.WARN -> MaterialTheme.colorScheme.tertiaryContainer
        LogLevel.INFO -> MaterialTheme.colorScheme.primaryContainer
        LogLevel.DEBUG -> MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val badgeFg = when (entry.level) {
        LogLevel.CRASH, LogLevel.ERROR -> MaterialTheme.colorScheme.error
        LogLevel.WARN -> MaterialTheme.colorScheme.onTertiaryContainer
        LogLevel.INFO -> MaterialTheme.colorScheme.primary
        LogLevel.DEBUG -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !entry.stackTrace.isNullOrBlank()) { onToggle() },
        shape = ScheduleStyle.CardShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = badgeBg, shape = MaterialTheme.shapes.extraSmall) {
                    Text(
                        "${entry.level.shortCode}/${entry.tag}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = badgeFg,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                }
                Spacer(Modifier.width(6.dp))
                Text(
                    entry.formattedTime,
                    fontSize = 10.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                if (!entry.stackTrace.isNullOrBlank()) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                entry.message,
                style = MaterialTheme.typography.bodySmall,
                fontSize = 12.sp,
                color = if (entry.level.priority >= LogLevel.ERROR.priority) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
            AnimatedVisibility(visible = expanded && !entry.stackTrace.isNullOrBlank()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                        .heightIn(max = 140.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
                    shape = MaterialTheme.shapes.extraSmall
                ) {
                    Text(
                        text = entry.stackTrace.orEmpty(),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        lineHeight = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(6.dp)
                            .verticalScroll(rememberScrollState())
                            .horizontalScroll(rememberScrollState())
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LogUploadDialog(
    viewModel: SettingsViewModel,
    targetDocument: ParsedLogDocument?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var serverUrl by remember(viewModel.logServerUrl) { mutableStateOf(viewModel.logServerUrl) }
    var userNote by remember { mutableStateOf("") }
    var includeSnapshots by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = { if (!viewModel.isUploadingLog) onDismiss() },
        title = {
            Text(
                if (targetDocument != null) "上传已导入的日志" else "自主上传诊断日志",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    if (targetDocument != null) {
                        "即将上传外部日志「${targetDocument.title}」（共 ${targetDocument.entries.size} 条，已自动脱敏隐私字段）。"
                    } else {
                        "仅在您点击「立即上传」时才会发送脱敏后的运行日志、崩溃堆栈与设备环境信息，不含账号密码。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = { serverUrl = it },
                    label = { Text("日志接收服务器地址 (HTTP POST)") },
                    placeholder = { Text(DiagnosticReporter.DEFAULT_SERVER_URL, fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (serverUrl.trim() != DiagnosticReporter.DEFAULT_SERVER_URL) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(
                            onClick = { serverUrl = DiagnosticReporter.DEFAULT_SERVER_URL },
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("恢复默认地址", fontSize = 11.5.sp)
                        }
                    }
                }

                OutlinedTextField(
                    value = userNote,
                    onValueChange = { userNote = it },
                    label = { Text("问题补充说明（选填，如触发步骤）") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )

                if (targetDocument == null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { includeSnapshots = !includeSnapshots },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = includeSnapshots,
                            onCheckedChange = { includeSnapshots = it }
                        )
                        Text(
                            "同时附带最近课表导入日志摘要",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                viewModel.logUploadMessage?.let { feedback ->
                    Surface(
                        color = if (viewModel.logUploadSuccess) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.errorContainer
                        },
                        shape = ScheduleStyle.CardShape,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = feedback,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (viewModel.logUploadSuccess) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onErrorContainer
                            },
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        enabled = !viewModel.isUploadingLog,
                        onClick = {
                            scope.launch {
                                val payload = viewModel.prepareUploadPayload(userNote, includeSnapshots, targetDocument)
                                val md = LogUploader.buildMarkdownReport(payload)
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                clipboard?.setPrimaryClip(ClipData.newPlainText("日志诊断报告", md))
                                Toast.makeText(context, "已复制完整诊断报告文本", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Text("复制报告文本", fontSize = 12.sp)
                    }
                    TextButton(
                        enabled = !viewModel.isUploadingLog,
                        onClick = {
                            scope.launch {
                                val payload = viewModel.prepareUploadPayload(userNote, includeSnapshots, targetDocument)
                                val md = LogUploader.buildMarkdownReport(payload)
                                val url = LogUploader.buildGithubIssueUrl(payload)
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                clipboard?.setPrimaryClip(ClipData.newPlainText("日志诊断报告", md))
                                runCatching {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                    )
                                    Toast.makeText(context, "已打开 GitHub，完整报告已同步复制", Toast.LENGTH_SHORT).show()
                                }.onFailure {
                                    Toast.makeText(context, "无法打开浏览器，报告已复制到剪贴板", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    ) {
                        Text("GitHub 反馈", fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !viewModel.isUploadingLog,
                onClick = {
                    viewModel.uploadLogs(
                        serverUrl = serverUrl,
                        userNote = userNote,
                        includeSnapshots = includeSnapshots,
                        targetDocument = targetDocument
                    )
                }
            ) {
                if (viewModel.isUploadingLog) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("上传中…")
                } else {
                    Text("立即上传")
                }
            }
        },
        dismissButton = {
            TextButton(
                enabled = !viewModel.isUploadingLog,
                onClick = onDismiss
            ) {
                Text("关闭")
            }
        }
    )
}
