package com.chen.schedule.ui.settings

import android.content.Intent
import android.provider.Settings
import android.Manifest
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.FilterChip
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.LinearProgressIndicator
import com.chen.schedule.util.update.DownloadState
import com.chen.schedule.util.update.GithubMirror
import com.chen.schedule.util.update.UpdateCheckResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.chen.schedule.ui.theme.BackgroundPreset
import com.chen.schedule.ui.theme.ThemePrefs
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.glance.appwidget.GlanceAppWidgetManager
import com.chen.schedule.widget.TodayWidget
import com.chen.schedule.widget.TodayWidgetReceiver
import com.chen.schedule.widget.Today3x3Widget
import com.chen.schedule.widget.Today3x3WidgetReceiver
import com.chen.schedule.widget.WeekWidget
import com.chen.schedule.widget.WeekWidgetReceiver

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateToScheduleConfig: () -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val downloadState by viewModel.downloadState.collectAsState()
    var pendingRestore by remember { mutableStateOf<Uri?>(null) }
    var showClearDialog by remember { mutableStateOf(false) }
    var showWidgetGuideDialog by remember { mutableStateOf(false) }
    var showMirrorDialog by remember { mutableStateOf(false) }

    val requestAddWidget: (Int) -> Unit = { widgetType ->
        scope.launch {
            try {
                val manager = GlanceAppWidgetManager(context)
                val (receiverClass, widgetInstance) = when (widgetType) {
                    0 -> Today3x3WidgetReceiver::class.java to Today3x3Widget()
                    1 -> TodayWidgetReceiver::class.java to TodayWidget()
                    else -> WeekWidgetReceiver::class.java to WeekWidget()
                }
                val pinned = manager.requestPinGlanceAppWidget(receiverClass, widgetInstance)
                if (pinned) {
                    Toast.makeText(context, "已发起添加请求，请在桌面弹出窗口中点击确认", Toast.LENGTH_LONG).show()
                } else {
                    showWidgetGuideDialog = true
                }
            } catch (e: Exception) {
                showWidgetGuideDialog = true
            }
        }
    }

    // Export launcher
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let { viewModel.exportToUri(it) }
    }

    // Export ICS launcher
    val exportIcsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/calendar")
    ) { uri ->
        uri?.let { viewModel.exportIcsToUri(it) }
    }

    // Import launcher
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        pendingRestore = uri
    }

    // 通知权限(Android 13+):开启上课提醒时申请
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Toast.makeText(context, "未授予通知权限,将收不到上课提醒", Toast.LENGTH_LONG).show()
        }
    }

    pendingRestore?.let { uri ->
        AlertDialog(onDismissRequest = { pendingRestore = null },
            title = { Text("恢复备份", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = { Text("完整备份会替换本机全部学期、课程与作息方案(含其他学期);仅课程 JSON 只替换当前学期课程。建议先备份现有数据。", fontSize = 13.sp) },
            confirmButton = { TextButton(onClick = { viewModel.importData(uri); pendingRestore = null }) { Text("恢复", fontSize = 13.sp) } },
            dismissButton = { TextButton(onClick = { pendingRestore = null }) { Text("取消", fontSize = 13.sp) } })
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("设置", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    TextButton(onClick = onNavigateBack) {
                        Text(
                            "返回",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            // ===== 外观与背景 =====
            val themeConfig by viewModel.themeConfig.collectAsState()
            SettingsGroup(title = "外观与背景") {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Text(
                        "主题外观",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            ThemePrefs.THEME_MODE_LIGHT to "浅色模式",
                            ThemePrefs.THEME_MODE_DARK to "深色模式",
                            ThemePrefs.THEME_MODE_SYSTEM to "跟随系统"
                        ).forEach { (mode, label) ->
                            FilterChip(
                                selected = themeConfig.themeMode == mode,
                                onClick = { viewModel.updateThemeMode(mode) },
                                label = { Text(label, fontSize = 12.sp) }
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    Text(
                        "背景底色",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        BackgroundPreset.entries.forEach { preset ->
                            FilterChip(
                                selected = themeConfig.backgroundPreset == preset,
                                onClick = { viewModel.updateBackgroundPreset(preset) },
                                label = { Text(preset.label, fontSize = 12.sp) }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ===== 学期与作息 =====
            SettingsGroup(title = "学期与作息") {
                SettingsItem(
                    title = "学期与作息",
                    subtitle = "管理学期、作息方案与节次时间",
                    onClick = onNavigateToScheduleConfig
                )
            }

            Spacer(Modifier.height(16.dp))

            // ===== 数据管理 =====
            SettingsGroup(title = "数据管理") {
                SettingsItem(
                    title = "导出为日历 (.ics)",
                    subtitle = "导出为日历事件，支持导入手机系统日历与手表",
                    onClick = { exportIcsLauncher.launch("课程表.ics") }
                )
                GroupDivider()
                SettingsItem(
                    title = "备份数据",
                    subtitle = "备份全部学期、课程及作息方案",
                    onClick = { exportLauncher.launch("course_schedule_backup.json") }
                )
                GroupDivider()
                SettingsItem(
                    title = "恢复数据",
                    subtitle = "完整备份替换全部数据；课程 JSON 仅替换当前学期课程",
                    onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) }
                )
                GroupDivider()
                SettingsItem(
                    title = "清空数据",
                    subtitle = "删除当前学期的所有课程数据",
                    isDanger = true,
                    onClick = { showClearDialog = true }
                )
            }

            Spacer(Modifier.height(16.dp))

            // ===== 上课提醒 =====
            SettingsGroup(title = "上课提醒") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("上课前提醒我", style = MaterialTheme.typography.bodyMedium, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text(
                            if (viewModel.reminderEnabled) {
                                "提前 ${viewModel.reminderLead} 分钟通知今天剩余的课程"
                            } else {
                                "关闭状态，不会发送任何通知"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = viewModel.reminderEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled && Build.VERSION.SDK_INT >= 33) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            viewModel.updateReminderEnabled(enabled)
                        }
                    )
                }
                GroupDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(5, 10, 15, 20, 30).forEach { minutes ->
                        FilterChip(
                            selected = viewModel.reminderLead == minutes,
                            onClick = { viewModel.updateReminderLead(minutes) },
                            label = { Text("提前 $minutes 分钟", fontSize = 12.sp) }
                        )
                    }
                }
                GroupDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("上课中常驻看板", style = MaterialTheme.typography.bodyMedium, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text(
                            if (!viewModel.reminderEnabled) {
                                "需先开启上方提醒总开关"
                            } else if (viewModel.reminderOngoing) {
                                "正在上课时常驻显示教室、节次与下课时间，下课后自动清除"
                            } else {
                                "已关闭，上课时不显示常驻卡片"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = viewModel.reminderOngoing,
                        enabled = viewModel.reminderEnabled,
                        onCheckedChange = { enabled ->
                            viewModel.updateReminderOngoing(enabled)
                        }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // ===== 桌面小组件 =====
            SettingsGroup(title = "桌面小组件") {
                SettingsItem(
                    title = "添加今日课程看板 (3×3)",
                    subtitle = "方形看板，完整展示上课时间与上课地点 · 点击尝试添加",
                    onClick = { requestAddWidget(0) }
                )
                GroupDivider()
                SettingsItem(
                    title = "添加今日课程小组件 (4×1)",
                    subtitle = "横条布局，显示今日课程 · 点击尝试添加",
                    onClick = { requestAddWidget(1) }
                )
                GroupDivider()
                SettingsItem(
                    title = "添加周课表小组件 (4×4)",
                    subtitle = "整周网格，概览周一至周日课程 · 点击尝试添加",
                    onClick = { requestAddWidget(2) }
                )
                GroupDivider()
                SettingsItem(
                    title = "小组件添加指引与帮助",
                    subtitle = "若点击无反应或系统拦截，查看手动添加教程与权限设置",
                    onClick = { showWidgetGuideDialog = true }
                )
            }

            Spacer(Modifier.height(16.dp))

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
                            showMirrorDialog = true
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

            // ===== 联系开发者 =====
            SettingsGroup(title = "联系开发者") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("QQ", style = MaterialTheme.typography.bodyMedium, fontSize = 14.sp)
                    Spacer(Modifier.weight(1f))
                    Text(
                        "3180635398",
                        style = MaterialTheme.typography.bodyMedium,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    // 清空数据确认弹窗
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            shape = MaterialTheme.shapes.extraLarge,
            title = { Text("确认清空", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = { Text("确定要删除当前学期的所有课程数据吗？此操作不可撤销。\n\n建议先备份数据。", fontSize = 13.sp) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearAllData()
                    showClearDialog = false
                }) {
                    Text("确认清空", color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("取消", fontSize = 13.sp)
                }
            }
        )
    }

    // 桌面小组件添加指引弹窗
    if (showWidgetGuideDialog) {
        AlertDialog(
            onDismissRequest = { showWidgetGuideDialog = false },
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
                                "方法一：手机桌面长按添加（推荐 · 100% 成功）",
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
                        showWidgetGuideDialog = false
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
                TextButton(onClick = { showWidgetGuideDialog = false }) {
                    Text("我知道了", fontSize = 13.sp)
                }
            }
        )
    }

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

    // 镜像节点测速与选择弹窗
    if (showMirrorDialog) {
        AlertDialog(
            onDismissRequest = { showMirrorDialog = false },
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
                TextButton(onClick = { showMirrorDialog = false }) {
                    Text("确定", fontSize = 13.sp)
                }
            }
        )
    }
}

@Composable
private fun SettingsGroup(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 2.dp)
            )
            content()
        }
    }
}

@Composable
private fun GroupDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
    )
}

@Composable
private fun SettingsItem(
    title: String,
    subtitle: String,
    isDanger: Boolean = false,
    onClick: () -> Unit
) {
    ListItem(
        modifier = Modifier.clickable { onClick() },
        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
        headlineContent = {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = if (isDanger) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurface
            )
        },
        supportingContent = {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                fontSize = 11.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    )
}
