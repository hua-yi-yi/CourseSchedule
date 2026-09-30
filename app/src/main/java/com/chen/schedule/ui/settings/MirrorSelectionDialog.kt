package com.chen.schedule.ui.settings

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.chen.schedule.util.update.GithubMirror
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun MirrorSelectionDialog(viewModel: SettingsViewModel, onDismiss: () -> Unit) {
    // 镜像节点测速与选择弹窗
    run {
        AlertDialog(
            onDismissRequest = { onDismiss() },
            shape = MaterialTheme.shapes.extraLarge,
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "GitHub 镜像测速与选择",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    if (viewModel.isTestingMirrors) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        TextButton(onClick = { viewModel.testAllMirrors() }) {
                            Text("重新测速", fontSize = 12.sp)
                        }
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        "针对国内网络环境，选择延迟较低的镜像节点可加速检测与安装包下载：",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))

                    GithubMirror.ALL_MIRRORS.forEach { mirror ->
                        val isSelected = viewModel.selectedMirror == mirror
                        val latency = viewModel.mirrorLatencies[mirror]

                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.updateSelectedMirror(mirror)
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = { viewModel.updateSelectedMirror(mirror) }
                                )
                                Spacer(Modifier.width(6.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        mirror.displayName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontSize = 13.5.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                    val desc = if (mirror == GithubMirror.DIRECT) "官方直连，需外网良好环境"
                                    else mirror.prefix ?: mirror.replaceDomain
                                    Text(
                                        desc,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                if (viewModel.isTestingMirrors && latency == null) {
                                    Text(
                                        "测速中...",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else if (latency != null) {
                                    val color = if (latency < 500) MaterialTheme.colorScheme.primary
                                    else if (latency < 1500) MaterialTheme.colorScheme.tertiary
                                    else MaterialTheme.colorScheme.error

                                    Text(
                                        "${latency}ms",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = color
                                    )
                                } else if (viewModel.mirrorLatencies.isNotEmpty()) {
                                    Text(
                                        "不可用/超时",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { onDismiss() }) {
                    Text("确定", fontSize = 13.sp)
                }
            }
        )
    }


}
