package com.chen.schedule.ui.timetable

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import com.chen.schedule.domain.model.Course
import com.chen.schedule.domain.model.DayOfWeek
import com.chen.schedule.domain.model.TimeSlot
import com.chen.schedule.util.WeekCalculator
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimetableScreen(
    onAddCourse: (Long) -> Unit,
    onEditCourse: (Long, Long) -> Unit,
    onNavigateToScheduleConfig: () -> Unit,
    onNavigateToImport: () -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: TimetableViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var selectedCourse by remember { mutableStateOf<Course?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { state.currentSemester?.let { onAddCourse(it.id) } },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("添加课程") }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = padding.calculateBottomPadding())
                .statusBarsPadding()
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
                    onNavigateToScheduleConfig = onNavigateToScheduleConfig
                )
            } else {
                // ===== 头部:学期信息 + 视图切换 =====
                val actualWeek = WeekCalculator.currentWeek(semester.startDate, semester.totalWeeks)
                HeaderSection(
                    semesterName = semester.name,
                    currentWeek = state.currentWeek,
                    totalWeeks = semester.totalWeeks,
                    isCurrentWeek = state.currentWeek == actualWeek,
                    isDayView = state.isDayView,
                    onToggleView = viewModel::toggleView,
                    onNavigateToImport = onNavigateToImport,
                    onNavigateToSettings = onNavigateToSettings
                )

                // ===== 周切换 =====
                WeekSelector(
                    currentWeek = state.currentWeek,
                    totalWeeks = semester.totalWeeks,
                    showWeekend = state.showWeekend,
                    isCurrentWeek = state.currentWeek == actualWeek,
                    onPrevWeek = viewModel::prevWeek,
                    onNextWeek = viewModel::nextWeek,
                    onToggleWeekend = viewModel::toggleWeekend,
                    onGoToday = viewModel::openToday
                )

                if (state.isDayView) {
                    DaySelector(
                        selectedDay = state.selectedDay,
                        onDaySelected = viewModel::selectDay,
                        showWeekend = state.showWeekend
                    )
                    DayView(
                        courses = viewModel.getFilteredCourses(),
                        timeSlots = state.timeSlots,
                        showWeekend = state.showWeekend,
                        onCourseClick = { selectedCourse = it }
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
                        onCourseClick = { selectedCourse = it }
                    )
                }
            }
        }
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
                viewModel.deleteCourse(course)
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
    onNavigateToImport: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 12.dp),
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
                    style = MaterialTheme.typography.headlineSmall,
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
            Spacer(Modifier.height(2.dp))
            Text(
                "第 $currentWeek 周 / 共 $totalWeeks 周",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        SingleChoiceSegmentedButtonRow(modifier = Modifier) {
            SegmentedButton(
                selected = !isDayView,
                onClick = { if (isDayView) onToggleView() },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                icon = {}
            ) {
                Text("周视图", fontSize = 13.sp)
            }
            SegmentedButton(
                selected = isDayView,
                onClick = { if (!isDayView) onToggleView() },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                icon = {}
            ) {
                Text("日视图", fontSize = 13.sp)
            }
        }
    }
}

/* ===================== 周切换 ===================== */

@Composable
private fun WeekSelector(
    currentWeek: Int,
    totalWeeks: Int,
    showWeekend: Boolean,
    isCurrentWeek: Boolean,
    onPrevWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onToggleWeekend: () -> Unit,
    onGoToday: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onPrevWeek) {
                    Icon(
                        Icons.Default.ChevronLeft, "上一周",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "第",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "$currentWeek",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "周",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        "共 $totalWeeks 周",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(onClick = onNextWeek) {
                    Icon(
                        Icons.Default.ChevronRight, "下一周",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!isCurrentWeek) {
                    Button(
                        onClick = onGoToday,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(Icons.Default.Today, null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("回到本周", fontSize = 12.sp)
                    }
                } else {
                    Spacer(Modifier.weight(1f))
                }
                TextButton(
                    onClick = onToggleWeekend,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        if (showWeekend) "隐藏周末" else "显示周末",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
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
    if (todayCourses.isEmpty()) return

    val now = LocalTime.now()
    val currentSlotNumber = timeSlots.firstOrNull { slot ->
        try {
            val start = LocalTime.parse(slot.startTime, DateTimeFormatter.ofPattern("HH:mm"))
            val end = LocalTime.parse(slot.endTime, DateTimeFormatter.ofPattern("HH:mm"))
            now in start..end
        } catch (_: Exception) {
            false
        }
    }?.slotNumber

    val next = todayCourses.firstOrNull { it.startSlot >= (currentSlotNumber ?: 1) } ?: todayCourses.first()
    val nd = LocalDate.now()
    val summary = if (todayCourses.size == 1) {
        "共 1 节课 · 下一节 ${next.name}"
    } else {
        "共 ${todayCourses.size} 节课 · 下一节 ${next.name}"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable { onOpenToday() },
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
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = "查看今日课程",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/* ===================== 空状态 ===================== */

@Composable
private fun EmptySemesterState(
    modifier: Modifier = Modifier,
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
                "先设置学期名称与开学日期,就能创建课程表了",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = onNavigateToScheduleConfig) {
                Text("去设置学期和作息时间")
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
