package com.chen.schedule.ui.settings

import android.content.Intent
import android.provider.Settings
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun WidgetGuideDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    // 桌面小组件添加指引弹窗
    run {
        AlertDialog(
            onDismissRequest = { onDismiss() },
            shape = MaterialTheme.shapes.extraLarge,
            title = {
                Text(
                    "桌面小组件添加指引",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
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
                        "很多手机系统（如小米 HyperOS/MIUI、华为鸿蒙、vivo、OPPO 等）默认禁止第三方应用直接向桌面固定小组件。\n\n您可以通过以下两种方式添加到桌面：",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 12.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )

                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                "方法一：手机桌面长按添加（推荐）",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 13.sp
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "1. 返回手机主屏幕，长按桌面空白处（或双指在屏幕上向内捏合）；\n2. 点击屏幕下方出现的「添加微件 / 小组件 / 插件」；\n3. 在应用列表中找到「课程表」；\n4. 选择「今日课程看板 (3×3)」、「今日课程 (4×1)」或「周课表 (4×4)」将其拖动至桌面合适位置即可！",
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 12.sp,
                                lineHeight = 18.sp
                            )
                        }
                    }

                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                "方法二：开启系统权限后一键添加",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "点击下方「去权限设置」按钮，在权限管理中为「课程表」允许【桌面快捷方式】或【桌面微件】权限，返回应用后重新点击添加即可。",
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 12.sp,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDismiss()
                        try {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "无法打开系统设置，请手动前往设置 > 应用管理", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("去权限设置", fontSize = 13.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { onDismiss() }) {
                    Text("我知道了", fontSize = 13.sp)
                }
            }
        )
    }


}
