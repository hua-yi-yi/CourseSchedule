package com.chen.schedule.ui.settings

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
    private val courseRepository: CourseRepository,
    private val semesterRepository: SemesterRepository,
    private val timeSlotRepository: TimeSlotRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    fun exportToUri(uri: Uri) {
        viewModelScope.launch {
            try {
                val sem = semesterRepository.getCurrentSemester() ?: run {
                    android.widget.Toast.makeText(context, "没有当前学期", android.widget.Toast.LENGTH_SHORT).show()
                    return@launch
                }
                val list = courseRepository.getCoursesBySemester(sem.id).first()
                if (list.isEmpty()) {
                    android.widget.Toast.makeText(context, "当前学期没有课程数据", android.widget.Toast.LENGTH_SHORT).show()
                    return@launch
                }
                val data = CourseImportData(
                    semesterName = sem.name,
                    courses = list.map { c ->
                        CourseJson(
                            name = c.name,
                            teacher = c.teacher,
                            classroom = c.classroom,
                            dayOfWeek = c.dayOfWeek,
                            startSlot = c.startSlot,
                            endSlot = c.endSlot,
                            startWeek = c.startWeek,
                            endWeek = c.endWeek,
                            weekType = when (c.weekType) {
                                WeekType.ODD -> "odd"
                                WeekType.EVEN -> "even"
                                else -> "all"
                            },
                            color = c.color,
                            note = c.note
                        )
                    }
                )
                val json = Json { prettyPrint = true }.encodeToString(CourseImportData.serializer(), data)
                context.contentResolver.openOutputStream(uri)?.use { os ->
                    os.write(json.toByteArray())
                }
                android.widget.Toast.makeText(context, "导出成功", android.widget.Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                android.widget.Toast.makeText(context, "导出失败: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            val sem = semesterRepository.getCurrentSemester() ?: return@launch
            courseRepository.deleteAllBySemester(sem.id)
            WidgetUpdater.refreshAll(context)
        }
    }

    fun importData(uri: Uri) {
        viewModelScope.launch {
            try {
                val jsonString = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: ""
                val courses = JsonImporter.parse(jsonString).getOrThrow()
                val currentSemester = semesterRepository.getCurrentSemester()
                if (currentSemester == null) {
                    android.widget.Toast.makeText(context, "请先创建学期", android.widget.Toast.LENGTH_SHORT).show()
                    return@launch
                }
                courseRepository.insertAll(courses.map { it.copy(semesterId = currentSemester.id) })
                WidgetUpdater.refreshAll(context)
                android.widget.Toast.makeText(context, "成功导入 ${courses.size} 门课程", android.widget.Toast.LENGTH_SHORT).show()
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
        uri?.let { viewModel.importData(it) }
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
                    subtitle = "导出课程数据为 JSON 文件",
                    showChevron = true,
                    onClick = { exportLauncher.launch("course_schedule_backup.json") }
                )
                GroupDivider()
                SettingsItem(
                    icon = Icons.Default.Restore,
                    title = "恢复数据",
                    subtitle = "从 JSON 文件导入课程数据",
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
                    Text("版本 1.0.1", style = MaterialTheme.typography.bodyMedium)
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
                    Toast.makeText(context, "数据已清空", Toast.LENGTH_SHORT).show()
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
