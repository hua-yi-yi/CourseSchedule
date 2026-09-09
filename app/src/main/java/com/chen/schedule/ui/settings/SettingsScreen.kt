package com.chen.schedule.ui.settings

import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.chen.schedule.util.ScheduleBackup
import com.chen.schedule.util.SchemeSlots
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
    private val timeSchemeRepository: com.chen.schedule.data.repository.TimeSchemeRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    fun exportToUri(uri: Uri) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val data = database.withTransaction {
                        val sem = semesterRepository.getCurrentSemester() ?: error("没有当前学期")
                        val allSlots = timeSlotRepository.getAllTimeSlots().first()
                        val schemes = timeSchemeRepository.getAllSchemesDirect()
                            .filter { !it.isLegacy }
                        ScheduleBackup(
                            backupVersion = ScheduleBackup.CURRENT_VERSION,
                            semester = sem,
                            courses = courseRepository.getCoursesBySemester(sem.id).first(),
                            // v1 兼容字段:只放「原有作息」(schemeId = 0)的节次,避免多套作息
                            // 合并后出现重复编号;其他方案各自放在 schemeSlots 中。
                            timeSlots = allSlots.filter { it.schemeId == 0L },
                            schemes = schemes,
                            schemeSlots = allSlots
                                .groupBy { it.schemeId }
                                .map { (schemeId, slots) -> SchemeSlots(schemeId, slots) },
                            semesters = semesterRepository.getAllSemesters().first(),
                            allCourses = courseRepository.getAllCourses().first()
                        )
                    }
                    // encodeDefaults = true 才能写入 backupVersion=2 / 空列等,
                    // 否则 v2 与 v1 无法区分。
                    val json = Json { prettyPrint = true; encodeDefaults = true }
                        .encodeToString(ScheduleBackup.serializer(), data)
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
                // Pair(实际写入课程数, 是否完整恢复);仅课程 JSON 只替换当前学期
                val result = withContext(Dispatchers.IO) {
                    val jsonString = requireNotNull(context.contentResolver.openInputStream(uri)).bufferedReader().use { it.readText() }
                    val format = Json { ignoreUnknownKeys = true }
                    val element = format.parseToJsonElement(jsonString)
                    val backup = if (element is kotlinx.serialization.json.JsonObject && "backupVersion" in element)
                        format.decodeFromString(ScheduleBackup.serializer(), jsonString) else null
                    require(backup == null || backup.backupVersion in
                        ScheduleBackup.LEGACY_VERSION..ScheduleBackup.CURRENT_VERSION) { "不支持此备份版本" }
                    val courses = backup?.courses ?: JsonImporter.parse(jsonString).getOrThrow()
                    require(backup != null || courses.isNotEmpty()) { "未找到课程，未修改现有数据" }
                    // 完整备份要校验备份中的全部课程(跨学期),而不仅是当前学期
                    val coursesToValidate = backup?.let {
                        if (it.allCourses.isNotEmpty()) it.allCourses else it.courses
                    } ?: courses
                    require(coursesToValidate.all { it.name.isNotBlank() && it.dayOfWeek in 1..7 && it.startSlot > 0 && it.endSlot >= it.startSlot && it.startWeek > 0 && it.endWeek >= it.startWeek }) { "课程数据无效" }
                    backup?.let { data ->
                        require(data.semester.totalWeeks in 1..53) { "学期周数无效" }
                        val semestersInBackup = if (data.semesters.isNotEmpty()) data.semesters else listOf(data.semester)
                        require(semestersInBackup.all { it.totalWeeks in 1..53 }) { "学期周数无效" }
                        require(semestersInBackup.all { it.name.isNotBlank() }) { "学期名称无效" }
                        val isNewFormat = data.backupVersion >= 2
                        if (isNewFormat) {
                            // 新版:各方案各自校验(不同方案可以都有「第1节」,合在一起会误报重复)。
                            data.schemeSlots.forEach { entry ->
                                if (entry.slots.isNotEmpty()) {
                                    require(com.chen.schedule.util.ScheduleStatus.isSlotsValid(entry.slots)) {
                                        "作息方案(编号 ${entry.schemeId})节次无效"
                                    }
                                }
                            }
                            // 「原有作息」桶同样按单套校验
                            val legacy = data.schemeSlots.firstOrNull { it.schemeId == 0L }?.slots
                                ?: data.timeSlots.filter { it.schemeId == 0L }
                            if (legacy.isNotEmpty()) {
                                require(com.chen.schedule.util.ScheduleStatus.isSlotsValid(legacy)) {
                                    "原有作息节次无效"
                                }
                            }
                        } else {
                            // 旧备份:只有单套作息,走原有解析器校验
                            if (data.timeSlots.isNotEmpty()) com.chen.schedule.util.TimeSlotParser.parse(
                                data.timeSlots.joinToString("\n") { slot -> "${slot.slotNumber} ${slot.startTime}-${slot.endTime}" })
                        }
                    }
                    database.withTransaction {
                        val data = backup
                        if (data == null) {
                            // 仅课程 JSON(旧格式):替换当前学期课程,行为不变
                            val current = semesterRepository.getCurrentSemester() ?: error("请先创建学期")
                            courseRepository.deleteAllBySemester(current.id)
                            courseRepository.insertAll(courses.map { it.copy(id = 0, semesterId = current.id) })
                            courses.size to false
                        } else {
                            // ===== 完整恢复:学期 / 课程 / 作息方案全部按备份重建 =====

                            // 1) 先清空旧的自定义方案(避免重复恢复累积同名方案),再补齐内置模板
                            timeSchemeRepository.deleteAllCustomSchemes()
                            runCatching { timeSchemeRepository.ensureBuiltInSchemes() }
                            val builtInsByName = timeSchemeRepository.getAllSchemesDirect()
                                .filter { it.isBuiltIn }
                                .associateBy { it.name }
                            val schemeMap = mutableMapOf<Long, Long>()
                            val actions = com.chen.schedule.util.BackupRestorePlanner
                                .schemeActions(data, builtInsByName.keys)
                            actions.forEach { (oldSchemeId, action) ->
                                val newId = when (action) {
                                    is com.chen.schedule.util.BackupRestorePlanner.SchemeAction.ReuseBuiltIn ->
                                        builtInsByName.getValue(action.builtInName).id
                                    is com.chen.schedule.util.BackupRestorePlanner.SchemeAction.CreateCustom ->
                                        timeSchemeRepository.createCustomScheme(
                                            action.name, action.slots, setCurrent = false
                                        )
                                }
                                schemeMap[oldSchemeId] = newId
                            }

                            // 2) 用纯逻辑计划器算出「要建哪些学期、课程归哪个学期」
                            val plan = com.chen.schedule.util.BackupRestorePlanner.plan(data) { old ->
                                if (old == 0L) 0L else schemeMap[old] ?: 0L
                            }

                            // 3) 清空本地学期与课程,按备份完整重建
                            courseRepository.deleteAll()
                            semesterRepository.deleteAll()

                            val insertedIds = plan.semesters.map { semesterRepository.insert(it) }
                            val currentId = insertedIds.getOrNull(plan.currentSemesterIndex)
                                ?: error("备份中没有学期")
                            semesterRepository.setCurrentSemester(currentId)

                            // 4) 课程:按学期下标换成新插入的学期 id
                            val toInsert = plan.courses.mapNotNull { (index, course) ->
                                insertedIds.getOrNull(index)?.let { newSemesterId ->
                                    course.copy(semesterId = newSemesterId)
                                }
                            }
                            if (toInsert.isNotEmpty()) courseRepository.insertAll(toInsert)

                            // 5) 「原有作息」桶(空则清空,避免残留旧数据)
                            timeSlotRepository.replaceScheme(0L, plan.legacySlots)

                            // 6) 返回实际写入的课程数(全部学期)与「完整恢复」标记
                            toInsert.size to true
                        }
                    }
                }
                WidgetUpdater.refreshAll(context)
                val (count, fullRestore) = result
                val message = if (fullRestore) {
                    "成功恢复 $count 门课程(含全部学期)"
                } else {
                    "成功恢复 $count 门课程(当前学期)"
                }
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()

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
            text = { Text("完整备份会替换本机全部学期、课程与作息方案(含其他学期);仅课程 JSON 只替换当前学期课程。建议先备份现有数据。") },
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
                    title = "学期与作息",
                    subtitle = "管理学期、作息方案与节次时间",
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
