package com.chen.schedule.ui.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.chen.schedule.island.CapsuleIslandManager
import com.chen.schedule.island.IslandCapability
import com.chen.schedule.island.IslandDisplayMode
import com.chen.schedule.island.IslandNativeAdapters
import com.chen.schedule.island.IslandNotificationCoordinator
import com.chen.schedule.island.IslandPrefs
import com.chen.schedule.island.IslandSystemStatus
import com.chen.schedule.island.IslandVendor
import com.chen.schedule.island.OverlayRuntimeStatus
import com.chen.schedule.reminders.ClassReminderManager
import com.chen.schedule.reminders.ReminderStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun IslandSettings() {
    val context = LocalContext.current
    val prefs = remember(context) { IslandPrefs.init(context) }
    val config by IslandPrefs.state.collectAsState()
    val systemStatus by IslandNotificationCoordinator.status.collectAsState()
    val overlayStatus by CapsuleIslandManager.status.collectAsState()
    val scope = rememberCoroutineScope()
    var notificationReady by remember { mutableStateOf(IslandNotificationCoordinator.notificationsAllowed(context)) }
    var overlayReady by remember { mutableStateOf(CapsuleIslandManager.canDrawOverlays(context)) }
    var exactReady by remember { mutableStateOf(ReminderStatus.exactAllowed(context)) }
    var capability by remember { mutableStateOf<IslandCapability?>(null) }
    var pendingEnableMode by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingPreviewType by rememberSaveable { mutableStateOf<Int?>(null) }
    var notificationRequestInFlight by rememberSaveable { mutableStateOf(false) }

    val syncSettings = {
        ClassReminderManager.rescheduleAsync(context)
        CapsuleIslandManager.sync(context)
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        notificationRequestInFlight = false
        notificationReady = IslandNotificationCoordinator.notificationsAllowed(context)
        if (pendingEnableMode == IslandDisplayMode.SYSTEM.name) {
            pendingEnableMode = null
            if (granted && notificationReady && prefs.currentConfig().mode == IslandDisplayMode.SYSTEM) {
                prefs.enabled = true
                syncSettings()
            } else if (granted && !notificationReady) {
                Toast.makeText(context, "请在通知设置中开启课程状态通知，再开启灵动岛", Toast.LENGTH_LONG).show()
            }
        }
    }
    val requestModePermission: (IslandDisplayMode) -> Unit = { mode ->
        pendingPreviewType = null
        pendingEnableMode = mode.name
        if (mode == IslandDisplayMode.OVERLAY) {
            CapsuleIslandManager.requestOverlayPermission(context)
        } else if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED) {
            notificationRequestInFlight = true
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            openIslandNotificationSettings(context)
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        ClassReminderManager.ensureChannels(context)
        notificationReady = IslandNotificationCoordinator.notificationsAllowed(context)
        overlayReady = CapsuleIslandManager.canDrawOverlays(context)
        exactReady = ReminderStatus.exactAllowed(context)
        val current = prefs.currentConfig()
        if (!notificationRequestInFlight) {
            pendingEnableMode?.let { pendingMode ->
                val ready = if (pendingMode == IslandDisplayMode.SYSTEM.name) notificationReady else overlayReady
                if (ready && current.mode.name == pendingMode) {
                    prefs.enabled = true
                }
                pendingEnableMode = null
            }
        }
        pendingPreviewType?.let { type ->
            if (overlayReady) {
                CapsuleIslandManager.setMockTest(context, enabled = true, type = type)
            }
            pendingPreviewType = null
        }
        // 厂商能力查询可能访问系统服务，始终放到 IO 线程。
        scope.launch {
            capability = withContext(Dispatchers.IO) {
                IslandNativeAdapters.capability(context)
            }
        }
        syncSettings()
    }

    SettingsGroup(title = "胶囊灵动岛") {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text("显示方式", fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(IslandDisplayMode.SYSTEM, IslandDisplayMode.OVERLAY).forEach { mode ->
                    FilterChip(
                        selected = config.mode == mode,
                        onClick = {
                            if (config.mode != mode) {
                                pendingEnableMode = null
                                prefs.mode = mode
                                val ready = if (mode == IslandDisplayMode.SYSTEM) {
                                    IslandNotificationCoordinator.notificationsAllowed(context)
                                } else CapsuleIslandManager.canDrawOverlays(context)
                                if (config.enabled && !ready) {
                                    prefs.enabled = false
                                    requestModePermission(mode)
                                }
                                syncSettings()
                            }
                        },
                        label = { Text(if (mode == IslandDisplayMode.SYSTEM) "系统通知（推荐）" else "悬浮胶囊", fontSize = 12.sp) }
                    )
                }
            }
            val device = capability?.vendor?.let { vendor ->
                when (vendor) {
                    IslandVendor.XIAOMI -> "小米"
                    IslandVendor.OPPO -> "OPPO / 一加"
                    IslandVendor.VIVO -> "vivo"
                    IslandVendor.HONOR -> "荣耀"
                    IslandVendor.HUAWEI -> "华为"
                    IslandVendor.GENERIC -> "当前设备"
                }
            } ?: "当前设备"
            Text(
                text = if (config.mode == IslandDisplayMode.SYSTEM) {
                    capability?.status ?: "正在检查系统支持；未接入原生胶囊时显示普通课程通知"
                } else "使用本应用悬浮胶囊，需要悬浮窗权限，可点击展开与拖拽",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (config.mode == IslandDisplayMode.SYSTEM && capability != null) {
                Text("$device · ${if (capability?.nativeAvailable == true) "原生接口可用，展示由系统决定" else "普通课程通知"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (config.mode == IslandDisplayMode.SYSTEM && !exactReady) {
                Text("精确闹钟未获授权，课程状态更新可能延迟",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = {
                    if (Build.VERSION.SDK_INT >= 31) {
                        try {
                            context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                android.net.Uri.parse("package:" + context.packageName)))
                        } catch (_: Exception) {
                            context.startActivity(Intent(Settings.ACTION_SETTINGS))
                        }
                    }
                }) { Text("闹钟授权", fontSize = 12.sp) }
            }
            if (config.enabled) {
                val runtimeText = if (config.mode == IslandDisplayMode.SYSTEM) {
                    when (systemStatus) {
                        IslandSystemStatus.IDLE -> "等待当前或临近课程"
                        IslandSystemStatus.BLOCKED -> "课程状态通知被系统关闭"
                        IslandSystemStatus.STANDARD -> "已发送普通课程通知"
                        IslandSystemStatus.NATIVE -> "已提交原生通知扩展，展示由系统决定"
                        IslandSystemStatus.FAILED -> "课程状态通知发送失败，请检查通知设置"
                    }
                } else overlayRuntimeText(overlayStatus)
                Text(runtimeText, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        GroupDivider()
        IslandSettingSwitch(
            title = "开启课程灵动岛",
            subtitle = if (config.mode == IslandDisplayMode.SYSTEM) {
                if (notificationReady) "即将上课与上课中显示课程状态；课前声音提醒单独设置"
                else "需要允许应用通知，并开启课程状态通知类别"
            } else if (overlayReady) "在其他应用上方显示当前或临近课程"
            else "需要允许本应用显示悬浮窗",
            checked = config.enabled,
            onCheckedChange = { enabled ->
                pendingEnableMode = null
                if (!enabled) {
                    pendingPreviewType = null
                    CapsuleIslandManager.setMockTest(context, enabled = false)
                    prefs.enabled = false
                    syncSettings()
                } else {
                    val ready = if (config.mode == IslandDisplayMode.SYSTEM) IslandNotificationCoordinator.notificationsAllowed(context)
                        else CapsuleIslandManager.canDrawOverlays(context)
                    if (ready) {
                        prefs.enabled = true
                        syncSettings()
                    } else requestModePermission(config.mode)
                }
            }
        )
        if (config.mode == IslandDisplayMode.SYSTEM && !notificationReady) {
            TextButton(onClick = { requestModePermission(IslandDisplayMode.SYSTEM) }, modifier = Modifier.padding(horizontal = 8.dp)) {
                Text("开启通知权限", fontSize = 12.sp)
            }
        } else if (config.mode == IslandDisplayMode.OVERLAY && !overlayReady) {
            TextButton(onClick = { requestModePermission(IslandDisplayMode.OVERLAY) }, modifier = Modifier.padding(horizontal = 8.dp)) {
                Text("开启悬浮窗权限", fontSize = 12.sp)
            }
        }

        GroupDivider()
        IslandSettingSwitch(
            title = "应用内课表灵动岛",
            subtitle = "在课表顶栏显示实际日期的当前或临近课程，浏览其他周不影响状态",
            checked = config.inAppEnabled,
            onCheckedChange = { prefs.inAppEnabled = it }
        )
        GroupDivider()
        IslandSettingSwitch(
            title = "悬浮胶囊无课时隐藏",
            subtitle = "系统通知只展示当前或临近课程；悬浮模式关闭此项可显示今日完成概况",
            checked = config.onlyWhenClass,
            onCheckedChange = {
                prefs.onlyWhenClass = it
                syncSettings()
            }
        )
        GroupDivider()
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text("即将上课提前量", fontSize = 13.5.sp, fontWeight = FontWeight.Medium)
            Text("与课前声音提醒的提前时间独立", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(15, 30, 45, 60).forEach { minutes ->
                    FilterChip(
                        selected = config.leadMinutes == minutes,
                        onClick = {
                            prefs.leadMinutes = minutes
                            syncSettings()
                        },
                        label = { Text("提前 $minutes 分钟", fontSize = 12.sp) }
                    )
                }
            }
        }
        GroupDivider()
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text("悬浮效果预览", fontSize = 13.5.sp, fontWeight = FontWeight.Medium)
            Text("仅预览本应用悬浮样式，60 秒后恢复原设置；不会验证厂商原生胶囊", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (config.mockMode) {
                Text(overlayRuntimeText(overlayStatus), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(0 to "上课", 1 to "候课").forEach { (type, label) ->
                    TextButton(onClick = {
                        if (config.mockMode && config.mockState == type) {
                            CapsuleIslandManager.setMockTest(context, enabled = false)
                        } else if (CapsuleIslandManager.canDrawOverlays(context)) {
                            CapsuleIslandManager.setMockTest(context, enabled = true, type = type)
                        } else {
                            pendingEnableMode = null
                            pendingPreviewType = type
                            CapsuleIslandManager.requestOverlayPermission(context)
                        }
                    }) { Text(if (config.mockMode && config.mockState == type) "关闭${label}预览" else "${label}预览", fontSize = 12.sp) }
                }
            }
        }
    }
    Spacer(Modifier.height(16.dp))
}

@Composable
private fun IslandSettingSwitch(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, fontSize = 11.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private fun overlayRuntimeText(status: OverlayRuntimeStatus): String = when (status) {
    OverlayRuntimeStatus.STOPPED -> "悬浮服务已停止"
    OverlayRuntimeStatus.STARTING -> "正在启动悬浮服务"
    OverlayRuntimeStatus.RUNNING -> "悬浮服务已运行；无课时按设置隐藏"
    OverlayRuntimeStatus.FAILED -> CapsuleIslandManager.failureMessage ?: "悬浮服务启动失败，请检查系统权限"
}

private fun openIslandNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .putExtra(Settings.EXTRA_CHANNEL_ID, "class_ongoing")
    try {
        context.startActivity(intent)
    } catch (_: Exception) {
        context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
    }
}
