package com.chen.schedule.ui.import_

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chen.schedule.ui.theme.ScheduleStyle
import com.chen.schedule.util.ImportSnapshot
import com.chen.schedule.util.ImportSnapshotManager
import kotlinx.coroutines.launch

@Composable
fun ImportSnapshotHistoryDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var snapshots by remember { mutableStateOf<List<ImportSnapshot>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var expandedId by remember { mutableStateOf<String?>(null) }

    fun refreshList() {
        scope.launch {
            isLoading = true
            snapshots = ImportSnapshotManager.listSnapshots(context)
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        refreshList()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("导入历史快照 (${snapshots.size})", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                if (snapshots.isNotEmpty()) {
                    TextButton(
                        onClick = {
                            scope.launch {
                                ImportSnapshotManager.clearAll(context)
                                refreshList()
                                Toast.makeText(context, "已清空本地快照记录", Toast.LENGTH_SHORT).show()
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                    ) {
                        Text("清空", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "本地自动记录了最近导入的排课数据与脱敏表格片段（已隐藏姓名学号等隐私）。遇到提取不全时，可在此选择对应记录一键发送给开发者。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))

                if (isLoading) {
                    Box(modifier = Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp))
                    }
                } else if (snapshots.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "暂无本地快照记录。\n每次尝试导入课表时，都会自动在此保存最近 10 次记录。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .heightIn(max = 420.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(snapshots, key = { it.id }) { item ->
                            val isExpanded = expandedId == item.id
                            SnapshotCard(
                                snapshot = item,
                                isExpanded = isExpanded,
                                onToggleExpand = {
                                    expandedId = if (isExpanded) null else item.id
                                },
                                onShare = {
                                    ImportSnapshotManager.shareSnapshot(context, item)
                                },
                                onOpenGithub = {
                                    val url = ImportSnapshotManager.buildGithubIssueUrl(item)
                                    val report = ImportSnapshotManager.buildExportMarkdown(item)
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("课表快照", report))
                                    runCatching {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        context.startActivity(intent)
                                        Toast.makeText(context, "已打开 GitHub，快照数据已同步复制！", Toast.LENGTH_SHORT).show()
                                    }.onFailure {
                                        Toast.makeText(context, "无法打开浏览器，快照数据已复制到剪贴板！", Toast.LENGTH_LONG).show()
                                    }
                                },
                                onCopy = {
                                    val report = ImportSnapshotManager.buildExportMarkdown(item)
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("课表快照", report))
                                    Toast.makeText(context, "已复制快照完整报告！", Toast.LENGTH_SHORT).show()
                                },
                                onDelete = {
                                    scope.launch {
                                        ImportSnapshotManager.deleteSnapshot(context, item.id)
                                        refreshList()
                                        Toast.makeText(context, "已删除该条快照", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}

@Composable
private fun SnapshotCard(
    snapshot: ImportSnapshot,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onShare: () -> Unit,
    onOpenGithub: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = ScheduleStyle.CardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpand() },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            shape = MaterialTheme.shapes.extraSmall
                        ) {
                            Text(
                                text = snapshot.source,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(
                            snapshot.formattedTime.substringAfter(" "),
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (snapshot.error != null) "异常：${snapshot.error}" else "识别到 ${snapshot.courseCount} 门课程 · ${snapshot.semesterName}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = if (snapshot.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                    )
                }
                IconButton(onClick = onToggleExpand, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isExpanded) "收起" else "展开",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    if (snapshot.courses.isNotEmpty()) {
                        Text("课程列表预览：", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        snapshot.courses.take(6).forEach { c ->
                            Text(
                                "• ${c.name} (周${c.dayOfWeek} ${c.startSlot}-${c.endSlot}节, ${c.startWeek}-${c.endWeek}周)",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (snapshot.courses.size > 6) {
                            Text("… 等共 ${snapshot.courses.size} 门课", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(Modifier.height(6.dp))
                    }

                    if (snapshot.rawSnippet.isNotBlank()) {
                        Text("脱敏源码片段预览：", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 90.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                            shape = MaterialTheme.shapes.extraSmall
                        ) {
                            Text(
                                text = snapshot.rawSnippet.take(500) + (if (snapshot.rawSnippet.length > 500) "…" else ""),
                                fontSize = 10.sp,
                                modifier = Modifier.padding(6.dp).verticalScroll(rememberScrollState()),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(6.dp))

            // 操作按钮条
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onDelete,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text("删除", fontSize = 11.5.sp, color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.width(4.dp))
                TextButton(
                    onClick = onCopy,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text("复制报告", fontSize = 11.5.sp)
                }
                Spacer(Modifier.width(4.dp))
                TextButton(
                    onClick = onOpenGithub,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text("GitHub反馈", fontSize = 11.5.sp)
                }
                Spacer(Modifier.width(4.dp))
                Button(
                    onClick = onShare,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = "分享", modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("发给开发者", fontSize = 12.sp)
                }
            }
        }
    }
}
