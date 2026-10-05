package com.chen.schedule.ui.settings

import android.Manifest
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
        viewModel.onSettingsResume()
    }
    val scope = rememberCoroutineScope()
    val downloadState by viewModel.downloadState.collectAsState()
    var pendingRestore by remember { mutableStateOf<Uri?>(null) }
    var showClearDialog by remember { mutableStateOf(false) }
    var showWidgetGuideDialog by remember { mutableStateOf(false) }
    var showMirrorDialog by remember { mutableStateOf(false) }
    var showDonationDialog by remember { mutableStateOf(false) }
    var showCalendarSyncDialog by remember { mutableStateOf(false) }

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
        uri?.let(viewModel::previewRestore)
    }

    // 此授权回调仅供课前提醒；灵动岛使用自己的授权回调，避免联动开启声音提醒。
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.updateReminderEnabled(granted)
        if (!granted) {
            Toast.makeText(context, "未授予通知权限,将收不到上课提醒", Toast.LENGTH_LONG).show()
        }
    }

    var showRecovery by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(Unit) { viewModel.refreshRecoveryPoints() }
    viewModel.restorePreview?.let { preview ->
        AlertDialog(onDismissRequest = viewModel::dismissRestore, title = { Text("确认恢复范围") },
            text = { Column { Text(preview.summary); if (viewModel.dataMessage.isNotBlank()) Text(viewModel.dataMessage) } },
            confirmButton = { TextButton(enabled = !viewModel.dataBusy, onClick = viewModel::confirmRestore) { Text(if (viewModel.dataBusy) "正在恢复…" else "确认恢复") } },
            dismissButton = { TextButton(enabled = !viewModel.dataBusy, onClick = viewModel::dismissRestore) { Text("取消") } })
    }
    if (showRecovery) AlertDialog(onDismissRequest = { showRecovery = false }, title = { Text("自动恢复点（最近 10 次）") },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) {
            if (viewModel.recoveryFiles.isEmpty()) Text("暂无恢复点；修改、删除、导入、清空及恢复前会自动保存。")
            viewModel.recoveryFiles.forEach { file ->
                TextButton(enabled = !viewModel.dataBusy, onClick = { showRecovery = false; viewModel.previewRestore(Uri.fromFile(file)) }) {
                    Text(java.text.SimpleDateFormat("MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(file.lastModified())) + " · " + file.name.substringAfter('-').substringBefore('-'))
                }
            }
        } }, confirmButton = { TextButton(onClick = { showRecovery = false }) { Text("关闭") } })
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

            AppearanceSettings(viewModel)

            // ===== 学期与作息 =====
            SettingsGroup(title = "学期与作息") {
                SettingsItem(
                    title = "学期与作息",
                    subtitle = "管理学期、作息方案与节次时间",
                    onClick = onNavigateToScheduleConfig
                )
            }

            Spacer(Modifier.height(16.dp))

            if (viewModel.dataBusy) Text("正在处理数据…")
            if (viewModel.dataMessage.isNotBlank()) Text(viewModel.dataMessage)
            // ===== 数据管理 =====
            SettingsGroup(title = "数据管理") {
                SettingsItem(title = "自动恢复点", subtitle = "最近 10 次修改前的数据，可预览后恢复",
                    onClick = { viewModel.refreshRecoveryPoints(); showRecovery = true })
                GroupDivider()
                SettingsItem(
                    title = "同步到系统日历",
                    subtitle = "直接写入手机系统日历，支持华为/小米/vivo/OPPO与手表",
                    onClick = {
                        viewModel.prepareCalendarSyncPreview()
                        showCalendarSyncDialog = true
                    }
                )
                GroupDivider()
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
                    title = "清空当前学期课程",
                    subtitle = "删除当前学期的所有课程数据",
                    isDanger = true,
                    onClick = { showClearDialog = true }
                )
            }

            Spacer(Modifier.height(16.dp))

            ReminderSettings(viewModel) { notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }

            Spacer(Modifier.height(16.dp))

            // ===== 胶囊灵动岛 =====
            IslandSettings()

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

            UpdateSettings(viewModel, downloadState) { showMirrorDialog = true }

            // ===== 支持与交流 =====
            SettingsGroup(title = "支持与交流") {
                SettingsItem(
                    title = "打赏作者",
                    subtitle = "如果觉得软件好用，欢迎支持开发者的持续维护 ☕",
                    trailing = {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                "❤️ 赞赏",
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    },
                    onClick = { showDonationDialog = true }
                )
                GroupDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                            clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("QQ", "3180635398"))
                            Toast.makeText(context, "QQ 号已复制到剪贴板", Toast.LENGTH_SHORT).show()
                        }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("开发者 QQ", style = MaterialTheme.typography.bodyMedium, fontSize = 14.sp)
                        Text(
                            "点击可快速复制，欢迎反馈与交流",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
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

    if (showClearDialog) ClearCoursesDialog(viewModel) { showClearDialog = false }
    if (showCalendarSyncDialog) CalendarSyncDialog(viewModel) { showCalendarSyncDialog = false }
    if (showWidgetGuideDialog) WidgetGuideDialog { showWidgetGuideDialog = false }
    AppUpdateDialog(viewModel, downloadState)
    if (showMirrorDialog) MirrorSelectionDialog(viewModel) { showMirrorDialog = false }
    if (showDonationDialog) DonationDialog { showDonationDialog = false }

}
