package com.chen.schedule.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.LinearProgressIndicator
import com.chen.schedule.util.update.DownloadState
import com.chen.schedule.util.update.UpdateCheckResult
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun AppUpdateDialog(viewModel: SettingsViewModel, downloadState: DownloadState) {
    // 新版本更新弹窗
    val currentUpdate = viewModel.updateResult
    if (currentUpdate is UpdateCheckResult.HasUpdate) {
        val release = currentUpdate.release
        AlertDialog(
            onDismissRequest = { viewModel.dismissUpdateDialog() },
            shape = MaterialTheme.shapes.extraLarge,
            title = {
                Text(
                    "发现新版本 ${release.versionName}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        if (release.publishedAt.isNotBlank()) {
                            Text(
                                "发布: ${release.publishedAt}",
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (release.fileSizeBytes != null && release.fileSizeBytes > 0) {
                            val sizeMb = String.format(java.util.Locale.US, "%.1f MB", release.fileSizeBytes / (1024.0 * 1024.0))
                            Text(
                                "安装包: $sizeMb",
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (currentUpdate.isMirrorUsed && !currentUpdate.mirrorName.isNullOrBlank()) {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                        ) {
                            Text(
                                "经由「${currentUpdate.mirrorName}」检测成功",
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Text(
                        "更新日志：",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )

                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(10.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                release.changelog,
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 12.sp,
                                lineHeight = 18.sp
                            )
                        }
                    }

                    // 下载状态与进度展示
                    when (val dl = downloadState) {
                        is DownloadState.Downloading -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (dl.progress >= 0) {
                                    LinearProgressIndicator(
                                        progress = { dl.progress / 100f },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(6.dp)
                                    )
                                } else {
                                    LinearProgressIndicator(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(6.dp)
                                    )
                                }
                                val progressDetail = if (dl.totalBytes > 0) {
                                    val downMb = String.format(java.util.Locale.US, "%.1f", dl.bytesDownloaded / (1024.0 * 1024.0))
                                    val totMb = String.format(java.util.Locale.US, "%.1f", dl.totalBytes / (1024.0 * 1024.0))
                                    "${dl.progress}% ($downMb MB / $totMb MB)"
                                } else {
                                    "正在下载更新包..."
                                }
                                Text(
                                    "正在静默下载: $progressDetail",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = 11.5.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                        is DownloadState.Completed -> {
                            Text(
                                "安装包已下载完成，若系统未自动弹出安装器，请点击下方「立即安装」。",
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        is DownloadState.Failed -> {
                            Text(
                                "下载失败: ${dl.error}",
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        DownloadState.Idle -> {
                            Text(
                                "应用内静默下载安装包，下载完成后自动呼出系统安装器，无需跳转浏览器。",
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            },
            confirmButton = {
                when (val dl = downloadState) {
                    is DownloadState.Downloading -> {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = { viewModel.cancelDownload() }) {
                                Text("取消", fontSize = 12.5.sp)
                            }
                            Button(onClick = { viewModel.dismissUpdateDialog() }) {
                                Text("后台下载", fontSize = 12.5.sp)
                            }
                        }
                    }
                    is DownloadState.Completed -> {
                        Button(onClick = { viewModel.installDownloadedApk(dl.file) }) {
                            Text("立即安装", fontSize = 12.5.sp)
                        }
                    }
                    is DownloadState.Failed -> {
                        Button(onClick = {
                            val url = if (viewModel.useMirror) release.mirrorDownloadUrl else release.officialDownloadUrl
                            viewModel.startDownload(url, release.versionTag, release)
                        }) {
                            Text("重试下载", fontSize = 12.5.sp)
                        }
                    }
                    DownloadState.Idle -> {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (viewModel.useMirror) {
                                Button(
                                    onClick = {
                                        viewModel.startDownload(release.mirrorDownloadUrl, release.versionTag, release)
                                    }
                                ) {
                                    Text("高速静默下载", fontSize = 12.5.sp)
                                }
                            }

                            FilledTonalButton(
                                onClick = {
                                    val targetUrl = if (viewModel.useMirror) release.officialDownloadUrl else release.mirrorDownloadUrl
                                    viewModel.startDownload(targetUrl, release.versionTag, release)
                                }
                            ) {
                                Text(if (viewModel.useMirror) "官方直连下载" else "立即静默下载", fontSize = 12.5.sp)
                            }
                        }
                    }
                }
            },
            dismissButton = {
                if (downloadState !is DownloadState.Downloading) {
                    TextButton(onClick = { viewModel.dismissUpdateDialog() }) {
                        Text("稍后再说", fontSize = 13.sp)
                    }
                }
            }
        )
    }


}
