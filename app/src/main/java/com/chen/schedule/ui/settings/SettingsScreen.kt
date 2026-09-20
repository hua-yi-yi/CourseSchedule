package com.chen.schedule.ui.settings

import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.chen.schedule.util.ScheduleBackup
import com.chen.schedule.util.SchemeSlots
import android.content.Context
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import kotlinx.coroutines.flow.StateFlow
import com.chen.schedule.ui.theme.BackgroundPreset
import com.chen.schedule.ui.theme.ThemeConfig
import com.chen.schedule.ui.theme.ThemePrefs
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.chen.schedule.reminders.ClassReminderManager
import com.chen.schedule.reminders.ReminderPrefs
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

    private val reminderPrefs by lazy { ReminderPrefs(context) }
    private val themePrefs by lazy { ThemePrefs(context) }

    val themeConfig: StateFlow<ThemeConfig> = ThemePrefs.state

    var reminderEnabled by androidx.compose.runtime.mutableStateOf(false); private set
    var reminderLead by androidx.compose.runtime.mutableStateOf(ReminderPrefs.DEFAULT_LEAD_MINUTES); private set

    init {
        reminderEnabled = reminderPrefs.enabled
        reminderLead = reminderPrefs.leadMinutes
    }

    fun updateThemeMode(mode: Int) {
        themePrefs.themeMode = mode
    }

    fun updateBackgroundPreset(preset: BackgroundPreset) {
        themePrefs.backgroundPresetId = preset.id
    }

    /** 开关上课提醒:立即重排/取消今天的提醒闹钟。 */
    fun updateReminderEnabled(enabled: Boolean) {
        reminderPrefs.enabled = enabled
        reminderEnabled = enabled
        ClassReminderManager.rescheduleAsync(context)
    }

    /** 修改提前量:立即按新提前量重排今天的提醒。 */
    fun updateReminderLead(minutes: Int) {
        reminderPrefs.leadMinutes = minutes
        reminderLead = minutes
        ClassReminderManager.rescheduleAsync(context)
    }

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

    fun exportIcsToUri(uri: Uri) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val sem = semesterRepository.getCurrentSemester() ?: error("没有当前学期")
                    val courses = courseRepository.getCoursesBySemester(sem.id).first()
                    val slots = timeSlotRepository.getTimeSlotsBySchemeDirect(sem.schemeId)
                    val icsString = com.chen.schedule.util.IcsExporter.export(
                        semester = sem,
                        courses = courses,
                        slots = slots,
                        alarmMinutes = reminderLead.takeIf { reminderEnabled } ?: 20
                    )
                    requireNotNull(context.contentResolver.openOutputStream(uri)).use {
                        it.write(icsString.toByteArray(Charsets.UTF_8))
                    }
                }
                android.widget.Toast.makeText(context, "日历文件导出成功", android.widget.Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                android.widget.Toast.makeText(context, "导出日历失败: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
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
    var showWidgetGuideDialog by remember { mutableStateOf(false) }

    val requestAddWidget: (Boolean) -> Unit = { isWeek ->
        scope.launch {
            try {
                val manager = GlanceAppWidgetManager(context)
                val receiverClass = if (isWeek) WeekWidgetReceiver::class.java else TodayWidgetReceiver::class.java
                val widgetInstance = if (isWeek) WeekWidget() else TodayWidget()
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
                    subtitle = "备份当前学期、课程及作息时间",
                    onClick = { exportLauncher.launch("course_schedule_backup.json") }
                )
                GroupDivider()
                SettingsItem(
                    title = "恢复数据",
                    subtitle = "从备份替换恢复当前学期",
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
            }

            Spacer(Modifier.height(16.dp))

            // ===== 桌面小组件 =====
            SettingsGroup(title = "桌面小组件") {
                SettingsItem(
                    title = "添加今日课程小组件 (4×1)",
                    subtitle = "横条布局，显示今日课程 · 点击尝试添加",
                    onClick = { requestAddWidget(false) }
                )
                GroupDivider()
                SettingsItem(
                    title = "添加周课表小组件 (4×4)",
                    subtitle = "整周网格，概览周一至周日课程 · 点击尝试添加",
                    onClick = { requestAddWidget(true) }
                )
                GroupDivider()
                SettingsItem(
                    title = "小组件添加指引与帮助",
                    subtitle = "若点击无反应或系统拦截，查看手动添加教程与权限设置",
                    onClick = { showWidgetGuideDialog = true }
                )
            }

            Spacer(Modifier.height(16.dp))

            // ===== 关于 =====
            SettingsGroup(title = "关于") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "版本 ${com.chen.schedule.BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 12.5.sp
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "Android 课程表",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 12.5.sp,
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
                                "1. 返回手机主屏幕，长按桌面空白处（或双指在屏幕上向内捏合）；\n2. 点击屏幕下方出现的「添加微件 / 小组件 / 插件」；\n3. 在应用列表中找到「课程表」；\n4. 长按「今日课程」或「周课表」将其拖动至桌面合适位置即可！",
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
