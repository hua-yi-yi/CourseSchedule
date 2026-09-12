package com.chen.schedule.ui.timetable

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.chen.schedule.domain.model.Course
import com.chen.schedule.domain.model.DayOfWeek
import com.chen.schedule.domain.model.TimeSlot
import com.chen.schedule.util.WeekCalculator
import com.chen.schedule.ui.schedule.StatusAmber
import com.chen.schedule.ui.schedule.StatusBadge
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimetableScreen(
    /** (semesterId, 预填位置) —— 位置为 null 表示从 FAB 进入的空白新增。 */
    onAddCourse: (Long, BlankClickTarget?) -> Unit,
    onEditCourse: (Long, Long) -> Unit,
    onNavigateToScheduleConfig: () -> Unit,
    onNavigateToSetupWizard: () -> Unit,
    onNavigateToSemesterSettings: () -> Unit,
    onNavigateToSchemeSettings: () -> Unit,
    onNavigateToImport: () -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: TimetableViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var selectedCourse by remember { mutableStateOf<Course?>(null) }
    var pendingDelete by remember { mutableStateOf<Course?>(null) }

    /** 统一的「点击空白格」入口:返回非 null 表示配置完整,可直接进入新增课程。 */
    val handleBlankClick: (dayOfWeek: Int, slotNumber: Int) -> Unit = { day, slot ->
        val target = viewModel.onBlankCellClick(day, slot)
        val semester = viewModel.state.value.currentSemester
        if (target != null && semester != null) {
            onAddCourse(semester.id, target)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            if (state.currentSemester != null) ExtendedFloatingActionButton(
                onClick = { state.currentSemester?.let { onAddCourse(it.id, null) } },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("添加课程") }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            val semester = state.currentSemester
            if (semester == null) {
                // 还没有学期 - 空状态(左上角菜单仍可用)
                TopMenu(
                    onNavigateToImport = onNavigateToImport,
                    onNavigateToSettings = onNavigateToSettings
                )
                EmptySemesterState(
                    modifier = Modifier.fillMaxSize(),
                    onNavigateToSetupWizard = onNavigateToSetupWizard,
                    onNavigateToScheduleConfig = onNavigateToScheduleConfig
                )
            } else {
                // 整页统一滚动:上滑时头部、周切换随内容一起向上滑出屏幕
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                // ===== 头部:学期信息 + 视图切换 =====
                val actualWeek = WeekCalculator.currentWeek(semester.startDate, semester.totalWeeks)
                HeaderSection(
                    semesterName = semester.name,
                    currentWeek = state.currentWeek,
                    totalWeeks = semester.totalWeeks,
                    isCurrentWeek = state.currentWeek == actualWeek,
                    isDayView = state.isDayView,
                    onToggleView = viewModel::toggleView,
                    onBackToToday = { viewModel.setWeek(actualWeek) },
                    onNavigateToImport = onNavigateToImport,
                    onNavigateToSettings = onNavigateToSettings
                )

                // ===== 周切换(单行紧凑) + 周末开关并排 =====
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    WeekSelector(
                        currentWeek = state.currentWeek,
                        totalWeeks = semester.totalWeeks,
                        onPrevWeek = viewModel::prevWeek,
                        onNextWeek = viewModel::nextWeek,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = viewModel::toggleWeekend,
                        modifier = Modifier.height(40.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp)
                    ) {
                        Text(
                            if (state.showWeekend) "隐藏周末" else "显示周末",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (state.isDayView) {
                    DaySelector(
                        selectedDay = state.selectedDay,
                        onDaySelected = viewModel::selectDay,
                        showWeekend = state.showWeekend
                    )
                    DayView(
                        isToday = state.currentWeek == actualWeek && state.selectedDay == LocalDate.now().dayOfWeek.value,
                        courses = viewModel.getFilteredCourses(),
                        timeSlots = state.timeSlots,
                        onCourseClick = { selectedCourse = it },
                        onBlankCellClick = { slotNumber ->
                            handleBlankClick(state.selectedDay, slotNumber)
                        }
                    )
                } else {
                    // 今日摘要卡:仅当正在查看的是「本周」时展示
                    if (state.currentWeek == actualWeek) {
                        TodaySummaryCard(
                            courses = viewModel.getFilteredCourses(),
                            timeSlots = state.timeSlots,
                            onOpenToday = viewModel::openToday
                        )
                    }
                    WeekView(
                        courses = viewModel.getFilteredCourses(),
                        timeSlots = state.timeSlots,
                        showWeekend = state.showWeekend,
                        semesterStartDate = semester.startDate,
                        currentWeek = state.currentWeek,
                        onCourseClick = { selectedCourse = it },
                        onBlankCellClick = { dayOfWeek, slotNumber ->
                            handleBlankClick(dayOfWeek, slotNumber)
                        }
                    )
                }
                } // 整页滚动结束
            }
        }
    }

    // ===== 「先完成课表设置」引导面板 =====
    // 从设置页返回(ON_RESUME)时重新核对状态并让面板重新出现,target 保持不变。
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshGuide() }

    state.guide?.takeIf { !it.hidden }?.let { guide ->
        ScheduleGuideDialog(
            guide = guide,
            onDismiss = viewModel::dismissGuide,
            onOpenSemesterSettings = {
                // 只隐藏,保留原点击位置,返回后仍可「继续添加课程」
                viewModel.hideGuidePreservingTarget()
                onNavigateToSemesterSettings()
            },
            onOpenSchemeSettings = {
                viewModel.hideGuidePreservingTarget()
                onNavigateToSchemeSettings()
            },
            onContinueAddCourse = {
                val target = viewModel.consumeGuideTarget()
                val semester = state.currentSemester
                if (semester != null) {
                    onAddCourse(semester.id, target)
                } else {
                    onNavigateToScheduleConfig()
                }
            }
        )
    }

    pendingDelete?.let { course ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除课程？") },
            text = { Text("将删除「${course.name}」的这条上课安排，此操作无法撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteCourse(course)
                    pendingDelete = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("保留课程") } }
        )
    }

    // 课程详情弹窗
    selectedCourse?.let { course ->
        CourseDetailDialog(
            course = course,
            onDismiss = { selectedCourse = null },
            onEdit = {
                state.currentSemester?.let { sem ->
                    onEditCourse(course.id, sem.id)
                    selectedCourse = null
                }
            },
            onDelete = {
                pendingDelete = course
                selectedCourse = null
            }
        )
    }
}

/* ===================== 头部 ===================== */

/** 左上角菜单栏:包含「导入课表」与「设置」 */
@Composable
private fun TopMenu(
    onNavigateToImport: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { menuOpen = true }) {
            Icon(
                Icons.Default.Menu,
                contentDescription = "菜单",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false }
        ) {
            DropdownMenuItem(
                text = { Text("导入课表") },
                leadingIcon = { Icon(Icons.Default.FileDownload, contentDescription = null) },
                onClick = { menuOpen = false; onNavigateToImport() }
            )
            DropdownMenuItem(
                text = { Text("设置") },
                leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                onClick = { menuOpen = false; onNavigateToSettings() }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HeaderSection(
    semesterName: String,
    currentWeek: Int,
    totalWeeks: Int,
    isCurrentWeek: Boolean,
    isDayView: Boolean,
    onToggleView: () -> Unit,
    onBackToToday: () -> Unit,
    onNavigateToImport: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 左上角菜单栏
        TopMenu(
            onNavigateToImport = onNavigateToImport,
            onNavigateToSettings = onNavigateToSettings
        )

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    semesterName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!isCurrentWeek) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "第 ${currentWeek} 周",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
            Text(
                "第 $currentWeek 周 / 共 $totalWeeks 周",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!isCurrentWeek) {
                Text(
                    "回到本周",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(top = 1.dp)
                        .clickable(onClick = onBackToToday)
                )
            }
        }

        SingleChoiceSegmentedButtonRow(modifier = Modifier) {
            SegmentedButton(
                selected = !isDayView,
                onClick = { if (isDayView) onToggleView() },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                icon = {}
            ) {
                Text("周视图", fontSize = 12.sp)
            }
            SegmentedButton(
                selected = isDayView,
                onClick = { if (!isDayView) onToggleView() },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                icon = {}
            ) {
                Text("日视图", fontSize = 12.sp)
            }
        }
    }
}

/* ===================== 周切换 ===================== */

/** 单行紧凑周切换:左右箭头 + 「第 N 周 / 共 M 周」;周末开关在卡片右侧并排。 */
@Composable
private fun WeekSelector(
    currentWeek: Int,
    totalWeeks: Int,
    onPrevWeek: () -> Unit,
    onNextWeek: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onPrevWeek,
                enabled = currentWeek > 1,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Default.ChevronLeft, "上一周",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (currentWeek > 1) 1f else 0.3f)
                )
            }

            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "第",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(3.dp))
                Text(
                    "$currentWeek",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(3.dp))
                Text(
                    "周 / 共 $totalWeeks 周",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(
                onClick = onNextWeek,
                enabled = currentWeek < totalWeeks,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Default.ChevronRight, "下一周",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (currentWeek < totalWeeks) 1f else 0.3f)
                )
            }
        }
    }
}

/* ===================== 日选择 ===================== */

@Composable
private fun DaySelector(
    selectedDay: Int,
    onDaySelected: (Int) -> Unit,
    showWeekend: Boolean = false
) {
    val days = DayOfWeek.entries.filter { showWeekend || it.index <= 5 }
    val todayIndex = LocalDate.now().dayOfWeek.value
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        days.forEach { day ->
            val selected = selectedDay == day.index
            val isToday = day.index == todayIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(CircleShape)
                    .background(
                        when {
                            selected -> MaterialTheme.colorScheme.primary
                            isToday -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                            else -> Color.Transparent
                        }
                    )
                    .clickable { onDaySelected(day.index) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    day.label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = when {
                        selected -> FontWeight.Bold
                        isToday -> FontWeight.SemiBold
                        else -> FontWeight.Normal
                    },
                    color = when {
                        selected -> MaterialTheme.colorScheme.onPrimary
                        isToday -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}

/* ===================== 今日摘要卡 ===================== */

@Composable
private fun TodaySummaryCard(
    courses: List<Course>,
    timeSlots: List<TimeSlot>,
    onOpenToday: () -> Unit
) {
    val todayIndex = LocalDate.now().dayOfWeek.value
    val todayCourses = courses.filter { it.dayOfWeek == todayIndex }.sortedBy { it.startSlot }
    val nd = LocalDate.now()
    val now by produceState(initialValue = LocalTime.now()) {
        while (true) {
            value = LocalTime.now()
            kotlinx.coroutines.delay(30_000)
        }
    }
    val summary = com.chen.schedule.util.TodaySummary.describe(todayCourses, timeSlots, now)

    // 今日无课时卡片不可点击(无内容可跳转),隐藏右侧箭头
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .then(
                if (todayCourses.isEmpty()) Modifier
                else Modifier.clickable { onOpenToday() }
            ),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Today,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "今日 ${nd.monthValue}月${nd.dayOfMonth}日 · ${DayOfWeek.entries.first { it.index == todayIndex }.label}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (todayCourses.isNotEmpty()) {
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = "查看今日课程",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/* ===================== 空状态 ===================== */

@Composable
private fun EmptySemesterState(
    modifier: Modifier = Modifier,
    onNavigateToSetupWizard: () -> Unit,
    onNavigateToScheduleConfig: () -> Unit
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.DateRange,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "还没有设置学期",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "点选几步就能完成初始设置,开始使用课程表",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = onNavigateToSetupWizard) {
                Text("开始初始设置")
            }
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = onNavigateToScheduleConfig) {
                Text("手动设置学期和作息时间", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/* ===================== 课程详情弹窗 ===================== */

@Composable
private fun CourseDetailDialog(
    course: Course,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val accent = Color(course.color)
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.extraLarge,
        icon = {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    course.name.take(1),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = accent
                )
            }
        },
        title = {
            Text(
                course.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (course.teacher.isNotBlank()) {
                    DetailRow(Icons.Default.Person, "教师", course.teacher)
                }
                if (course.classroom.isNotBlank()) {
                    DetailRow(Icons.Default.Place, "教室", course.classroom)
                }
                DetailRow(
                    Icons.Default.Schedule, "时间",
                    "${DayOfWeek.entries.find { it.index == course.dayOfWeek }?.label ?: ""} ${course.startSlot}-${course.endSlot} 节"
                )
                DetailRow(
                    Icons.Default.DateRange, "周次",
                    "第 ${course.startWeek}-${course.endWeek} 周 · ${course.weekType.label}"
                )
                if (course.note.isNotBlank()) {
                    DetailRow(Icons.AutoMirrored.Filled.Notes, "备注", course.note)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onEdit) { Text("编辑") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = onDismiss) { Text("关闭") }
            }
        }
    )
}

@Composable
private fun DetailRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/* ===================== 引导设置面板 ===================== */

/**
 * 学期/作息未配置时点击空白格弹出的「先完成课表设置」面板。
 * 已完成项显示绿色对号与「已完成」,未完成项显示黄色感叹号与「待设置」;
 * 每项可直接进入对应设置;配置完成后由用户点击「继续添加课程」。
 */
@Composable
private fun ScheduleGuideDialog(
    guide: ScheduleGuide,
    onDismiss: () -> Unit,
    onOpenSemesterSettings: () -> Unit,
    onOpenSchemeSettings: () -> Unit,
    onContinueAddCourse: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.extraLarge,
        title = {
            Text("先完成课表设置", fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                Text(
                    "点击的位置已为你保留,完成设置后即可继续添加课程。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                GuideItem(
                    title = "学期",
                    done = guide.semesterDone,
                    onClick = onOpenSemesterSettings
                )
                Spacer(Modifier.height(8.dp))
                GuideItem(
                    title = "作息时间",
                    done = guide.schemeDone,
                    onClick = onOpenSchemeSettings
                )
                if (!guide.schemeDone && guide.missingSlots.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "缺少节次:${guide.missingSlots.joinToString("、") { "第${it}节" }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = StatusAmber
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onContinueAddCourse,
                enabled = guide.allDone
            ) { Text("继续添加课程") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("稍后再说") }
        }
    )
}

@Composable
private fun GuideItem(
    title: String,
    done: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            StatusBadge(done = done, compact = true)
            Spacer(Modifier.width(6.dp))
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = "进入$title 设置",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
