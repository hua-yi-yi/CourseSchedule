package com.chen.schedule.ui.settings

import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chen.schedule.island.CapsuleIslandManager
import com.chen.schedule.island.IslandPrefs

@Composable
internal fun IslandSettings() {
    val context = LocalContext.current
    val prefs = IslandPrefs.init(context)
    val config by IslandPrefs.state.collectAsState()
    val hasOverlayPermission = CapsuleIslandManager.canDrawOverlays(context)

    SettingsGroup(title = "胶囊灵动岛") {
        // 1. 悬浮窗灵动岛总开关
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "桌面悬浮灵动岛",
                        style = MaterialTheme.typography.bodyMedium,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.padding(horizontal = 4.dp))
                    Surface(
                        shape = MaterialTheme.shapes.extraSmall,
                        color = if (hasOverlayPermission) Color(0x2210B981) else Color(0x22F59E0B)
                    ) {
                        Text(
                            text = if (hasOverlayPermission) "权限已就绪" else "需悬浮窗权限",
                            color = if (hasOverlayPermission) Color(0xFF10B981) else Color(0xFFF59E0B),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                        )
                    }
                }
                Text(
                    text = if (config.enabled && hasOverlayPermission) {
                        "已开启，在手机顶部常驻流体胶囊，支持点击展开与拖拽"
                    } else if (config.enabled && !hasOverlayPermission) {
                        "已开启但缺少悬浮窗权限，请点击下方「去授权」按钮"
                    } else {
                        "关闭状态，不在桌面显示悬浮灵动岛"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = config.enabled,
                onCheckedChange = { enabled ->
                    if (enabled) {
                        if (!hasOverlayPermission) {
                            CapsuleIslandManager.requestOverlayPermission(context)
                            Toast.makeText(context, "请在系统设置中允许本应用显示悬浮窗", Toast.LENGTH_LONG).show()
                        } else {
                            CapsuleIslandManager.start(context)
                        }
                    } else {
                        CapsuleIslandManager.stop(context)
                    }
                }
            )
        }

        // 权限引导按钮行
        if (!hasOverlayPermission) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = { CapsuleIslandManager.requestOverlayPermission(context) }) {
                    Text("前往开启悬浮窗权限", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        GroupDivider()

        // 2. 应用内灵动岛开关
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "应用内课表灵动岛",
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = if (config.inAppEnabled) {
                        "在课表主界面顶栏以流体胶囊显示正在进行或临近课程"
                    } else {
                        "已关闭，课表内不显示置顶胶囊"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = config.inAppEnabled,
                onCheckedChange = { prefs.inAppEnabled = it }
            )
        }

        GroupDivider()

        // 3. 仅在有课时显示开关
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "无课时自动静默隐藏",
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = if (config.onlyWhenClass) {
                        "仅在上课中或即将上课时出现，今日无后续课程时自动隐藏"
                    } else {
                        "始终常驻显示，无课时展示今日完成概况"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = config.onlyWhenClass,
                onCheckedChange = {
                    prefs.onlyWhenClass = it
                    CapsuleIslandManager.refresh(context)
                }
            )
        }

        GroupDivider()

        // 4. 提前显示时间选择
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                "即将上课提前量",
                style = MaterialTheme.typography.bodyMedium,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(15, 30, 45, 60).forEach { mins ->
                    FilterChip(
                        selected = config.leadMinutes == mins,
                        onClick = {
                            prefs.leadMinutes = mins
                            CapsuleIslandManager.refresh(context)
                        },
                        label = { Text("提前 $mins 分钟", fontSize = 12.sp) }
                    )
                }
            }
        }

        GroupDivider()

        // 5. 快速测试与预览按钮
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "灵动岛效果实时测试",
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    "无论当前是否有课，均可一键呼出模拟胶囊测试展开、点击与拖拽",
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row {
                TextButton(
                    onClick = {
                        val newMode = !(config.mockMode && config.mockState == 0)
                        CapsuleIslandManager.setMockTest(context, enabled = newMode, type = 0)
                        Toast.makeText(context, if (newMode) "已呼出「正在上课」测试灵动岛" else "已退出测试", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text(if (config.mockMode && config.mockState == 0) "关闭上课测试" else "上课测试", fontSize = 12.sp)
                }

                TextButton(
                    onClick = {
                        val newMode = !(config.mockMode && config.mockState == 1)
                        CapsuleIslandManager.setMockTest(context, enabled = newMode, type = 1)
                        Toast.makeText(context, if (newMode) "已呼出「即将上课」测试灵动岛" else "已退出测试", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text(if (config.mockMode && config.mockState == 1) "关闭候课测试" else "候课测试", fontSize = 12.sp)
                }
            }
        }
    }
}
