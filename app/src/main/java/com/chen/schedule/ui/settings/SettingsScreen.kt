package com.chen.schedule.ui.settings

import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.chen.schedule.util.ScheduleBackup
import android.content.Context
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chen.schedule.data.repository.CourseRepository
import com.chen.schedule.data.repository.SemesterRepository
import com.chen.schedule.data.repository.TimeSlotRepository
import com.chen.schedule.domain.model.Course
import com.chen.schedule.domain.model.WeekType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chen.schedule.util.CourseJson
import com.chen.schedule.util.CourseImportData
import com.chen.schedule.util.JsonImporter
import com.chen.schedule.widget.TodayWidget
import com.chen.schedule.widget.TodayWidgetReceiver
import com.chen.schedule.widget.WeekWidget
import com.chen.schedule.widget.WeekWidgetReceiver
import com.chen.schedule.widget.WidgetUpdater
import dagger.hilt.android.qualifiers.ApplicationContext

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val database: com.chen.schedule.data.local.AppDatabase,
    private val courseRepository: CourseRepository,
    private val semesterRepository: SemesterRepository,
    private val timeSlotRepository: TimeSlotRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    fun exportToUri(uri: Uri) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val data = database.withTransaction {
                        val sem = semesterRepository.getCurrentSemester() ?: error("没有当前学期")
                        ScheduleBackup(semester = sem,
                            courses = courseRepository.getCoursesBySemester(sem.id).first(),
                            timeSlots = timeSlotRepository.getAllTimeSlots().first())
                    }
                    val json = Json { prettyPrint = true }.encodeToString(ScheduleBackup.serializer(), data)
                    requireNotNull(context.contentResolver.openOutputStream(uri)).use {
                        it.write(json.toByteArray(Charsets.UTF_8))
                    }
                }
                android.widget.Toast.makeText(context, "导出成功", android.widget.Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                android.widget.Toast.makeText(context, "导出失败: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            try {
                val sem = semesterRepository.getCurrentSemester() ?: error("没有当前学期")
                courseRepository.deleteAllBySemester(sem.id)
                WidgetUpdater.refreshAll(context)
                Toast.makeText(context, "数据已清空", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "清空失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun importData(uri: Uri) {
        viewModelScope.launch {
            try {
                val count = withContext(Dispatchers.IO) {
                    val jsonString = requireNotNull(context.contentResolver.openInputStream(uri)).bufferedReader().use { it.readText() }
                    val format = Json { ignoreUnknownKeys = true }
                    val element = format.parseToJsonElement(jsonString)
                    val backup = if (element is kotlinx.serialization.json.JsonObject && "backupVersion" in element)
                        format.decodeFromString(ScheduleBackup.serializer(), jsonString) else null
                    require(backup == null || backup.backupVersion == 1) { "不支持此备份版本" }
                    val courses = backup?.courses ?: JsonImporter.parse(jsonString).getOrThrow()
                    require(backup != null || courses.isNotEmpty()) { "未找到课程，未修改现有数据" }
                    require(courses.all { it.name.isNotBlank() && it.dayOfWeek in 1..7 && it.startSlot > 0 && it.endSlot >= it.startSlot && it.startWeek > 0 && it.endWeek >= it.startWeek }) { "课程数据无效" }
                    backup?.let {
                        require(it.semester.totalWeeks in 1..53) { "学期周数无效" }
                        if (it.timeSlots.isNotEmpty()) com.chen.schedule.util.TimeSlotParser.parse(
                            it.timeSlots.joinToString("\n") { slot -> "${slot.slotNumber} ${slot.startTime}-${slot.endTime}" })
                    }
                    database.withTransaction {
                        val current = semesterRepository.getCurrentSemester()
                        val id = current?.id ?: semesterRepository.insert(
                            backup?.semester?.copy(id = 0, isCurrent = true) ?: error("请先创建学期"))
                        backup?.let {
                            semesterRepository.update(it.semester.copy(id = id, isCurrent = true))
                            timeSlotRepository.replaceAll(it.timeSlots.map { slot -> slot.copy(id = 0) })
                        }
                        courseRepository.deleteAllBySemester(id)
                        courseRepository.insertAll(courses.map { it.copy(id = 0, semesterId = id) })
                    }
                    courses.size
                }
                WidgetUpdater.refreshAll(context)
                Toast.makeText(context, "成功恢复 $count 门课程", Toast.LENGTH_SHORT).show()

            } catch (e: Exception) {
                android.widget.Toast.makeText(context, "导入失败: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateToScheduleConfig: () -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingRestore by remember { mutableStateOf<Uri?>(null) }
    var showClearDialog by remember { mutableStateOf(false) }

    // Export launcher
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let { viewModel.exportToUri(it) }
    }

    // Import launcher
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        pendingRestore = uri
    }

    pendingRestore?.let { uri ->
        AlertDialog(onDismissRequest = { pendingRestore = null },
            title = { Text("恢复备份") },
            text = { Text("将替换当前学期的课程；完整备份还会恢复学期信息与作息时间。建议先备份现有数据。") },
            confirmButton = { TextButton(onClick = { viewModel.importData(uri); pendingRestore = null }) { Text("恢复") } },
            dismissButton = { TextButton(onClick = { pendingRestore = null }) { Text("取消") } })
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("设置", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
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

            // ===== 学期与作息 =====
            SettingsGroup(title = "学期与作息") {
                SettingsItem(
                    icon = Icons.Default.Schedule,
                    title = "作息时间配置",
                    subtitle = "设置学期信息与节次时间",
                    showChevron = true,
                    onClick = onNavigateToScheduleConfig
                )
            }

            Spacer(Modifier.height(16.dp))

            // ===== 数据管理 =====
            SettingsGroup(title = "数据管理") {
                SettingsItem(
                    icon = Icons.Default.Backup,
                    title = "备份数据",
                    subtitle = "备份当前学期、课程及作息时间",
                    showChevron = true,
                    onClick = { exportLauncher.launch("course_schedule_backup.json") }
                )
                GroupDivider()
                SettingsItem(
                    icon = Icons.Default.Restore,
                    title = "恢复数据",
                    subtitle = "从备份替换恢复当前学期",
                    showChevron = true,
                    onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) }
                )
                GroupDivider()
                SettingsItem(
                    icon = Icons.Default.DeleteForever,
                    title = "清空数据",
                    subtitle = "删除当前学期的所有课程数据",
                    showChevron = true,
                    dangerIcon = true,
                    onClick = { showClearDialog = true }
                )
            }

            Spacer(Modifier.height(16.dp))

            // ===== 桌面小组件 =====
            SettingsGroup(title = "桌面小组件") {
                SettingsItem(
                    icon = Icons.Default.Widgets,
                    title = "今日课程小组件",
                    subtitle = "4×1 横条,显示今天的课程",
                    showChevron = true,
                    onClick = {
                        scope.launch {
                            val pinned = GlanceAppWidgetManager(context).requestPinGlanceAppWidget(
                                TodayWidgetReceiver::class.java,
                                TodayWidget()
                            )
                            Toast.makeText(
                                context,
                                if (pinned == true) "请在弹出窗口中确认添加" else "当前桌面不支持固定小组件",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                )
                GroupDivider()
                SettingsItem(
                    icon = Icons.Default.CalendarMonth,
                    title = "周课表小组件",
                    subtitle = "4×4 大组件,展示整周课程网格",
                    showChevron = true,
                    onClick = {
                        scope.launch {
                            val pinned = GlanceAppWidgetManager(context).requestPinGlanceAppWidget(
                                WeekWidgetReceiver::class.java,
                                WeekWidget()
                            )
                            Toast.makeText(
                                context,
                                if (pinned == true) "请在弹出窗口中确认添加" else "当前桌面不支持固定小组件",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                )
            }

            Spacer(Modifier.height(16.dp))

            // ===== 关于 =====
            SettingsGroup(title = "关于") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("版本 ${com.chen.schedule.BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.weight(1f))
                    Text(
                        "Android 课程表",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // ===== 联系开发者 =====
            SettingsGroup(title = "联系开发者") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("QQ", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.weight(1f))
                    Text(
                        "3180635398",
                        style = MaterialTheme.typography.bodyLarge,
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
            title = { Text("确认清空", fontWeight = FontWeight.Bold) },
            text = { Text("确定要删除当前学期的所有课程数据吗？此操作不可撤销。\n\n建议先备份数据。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearAllData()
                    showClearDialog = false
                }) {
                    Text("确认清空", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("取消")
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
                style = MaterialTheme.typography.titleSmall,
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
        modifier = Modifier.padding(start = 56.dp, end = 0.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    )
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    showChevron: Boolean,
    dangerIcon: Boolean = false,
    onClick: () -> Unit
) {
    ListItem(
        modifier = Modifier.clickable { onClick() },
        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
        leadingContent = {
            Icon(
                icon,
                contentDescription = null,
                tint = if (dangerIcon) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 4.dp)
            )
        },
        headlineContent = {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (dangerIcon) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurface
            )
        },
        supportingContent = {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingContent = if (showChevron) {
            {
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            null
        }
    )
}
