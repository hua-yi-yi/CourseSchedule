package com.chen.schedule.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
internal fun UpdateSettings(viewModel: SettingsViewModel, downloadState: DownloadState, onShowMirrors: () -> Unit) {
    // ===== 版本与更新 =====
    SettingsGroup(title = "版本与更新") {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "当前版本",
                        style = MaterialTheme.typography.bodyMedium,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    if (viewModel.updateResult is UpdateCheckResult.HasUpdate) {
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.primary,
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                "新版可用",
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Text(
                    "v${com.chen.schedule.BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                "Android 课程表",
                style = MaterialTheme.typography.bodySmall,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        GroupDivider()

        // 自动检测更新开关
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "自动检测最新版本",
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    "开启后进入设置时自动在后台静默检测",
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = viewModel.autoCheckUpdate,
                onCheckedChange = { viewModel.updateAutoCheckUpdate(it) }
            )
        }

        GroupDivider()

        // GitHub 镜像加速开关
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "GitHub 镜像加速",
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    if (viewModel.useMirror) "当前: ${viewModel.selectedMirror.displayName}" else "使用官方直连 (外网良好/有代理时使用)",
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = viewModel.useMirror,
                onCheckedChange = { viewModel.updateUseMirror(it) }
            )
        }

        if (viewModel.useMirror) {
            GroupDivider()
            SettingsItem(
                title = "镜像节点测速与选择",
                subtitle = "测试 GitHub 加速镜像延迟并选择最优节点",
                onClick = {
                    viewModel.testAllMirrors()
                    onShowMirrors()
                }
            )
        }

        GroupDivider()

        // 检查更新触发项
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = !viewModel.isCheckingUpdate) {
                    if (downloadState is DownloadState.Downloading || downloadState is DownloadState.Completed) {
                        viewModel.reopenUpdateDialog()
                    } else {
                        viewModel.checkUpdate(manual = true)
                    }
                }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "检查更新",
                        style = MaterialTheme.typography.bodyMedium,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    if (downloadState is DownloadState.Downloading) {
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = MaterialTheme.shapes.extraSmall
                        ) {
                            Text(
                                "后台下载中",
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    } else if (downloadState is DownloadState.Completed) {
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.primary,
                            shape = MaterialTheme.shapes.extraSmall
                        ) {
                            Text(
                                "已下载完成",
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }
                }

                when (val dl = downloadState) {
                    is DownloadState.Downloading -> {
                        val progressText = if (dl.totalBytes > 0) {
                            val downMb = String.format(java.util.Locale.US, "%.1f", dl.bytesDownloaded / (1024.0 * 1024.0))
                            val totMb = String.format(java.util.Locale.US, "%.1f", dl.totalBytes / (1024.0 * 1024.0))
                            "${dl.progress}% ($downMb MB / $totMb MB) · 点击查看"
                        } else {
                            "正在静默下载更新包... · 点击查看"
                        }
                        Spacer(Modifier.height(3.dp))
                        Text(
                            progressText,
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(5.dp))
                        if (dl.progress >= 0) {
                            LinearProgressIndicator(
                                progress = { dl.progress / 100f },
                                modifier = Modifier
                                    .fillMaxWidth(0.9f)
                                    .height(4.dp)
                            )
                        } else {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth(0.9f)
                                    .height(4.dp)
                            )
                        }
                    }
                    is DownloadState.Completed -> {
                        Spacer(Modifier.height(3.dp))
                        Text(
                            "安装包已就绪，点击立即安装",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    else -> {
                        Text(
                            viewModel.lastCheckSummary,
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            if (viewModel.isCheckingUpdate) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                Text(
                    when (downloadState) {
                        is DownloadState.Downloading -> "查看进度"
                        is DownloadState.Completed -> "立即安装"
                        else -> "立即检测"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 12.5.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }

    Spacer(Modifier.height(16.dp))
}
