package com.chen.schedule.ui.timetable

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.chen.schedule.domain.model.Course
import com.chen.schedule.domain.model.DayOfWeek
import com.chen.schedule.util.WeekCalculator
import com.chen.schedule.ui.schedule.StatusAmber
import com.chen.schedule.ui.schedule.StatusBadge
import com.chen.schedule.ui.theme.ThemePrefs
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimetableScreen(
    /** (semesterId, 预填位置) —— 位置为 null 表示从 FAB 进入的空白新增。 */
    onAddCourse: (Long, BlankClickTarget?) -> Unit,
    onEditCourse: (Long, Long, Int) -> Unit,
    onNavigateToScheduleConfig: () -> Unit,
    onNavigateToSetupWizard: () -> Unit,
    onNavigateToSemesterSettings: () -> Unit,
    onNavigateToSchemeSettings: () -> Unit,
    onNavigateToImport: () -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: TimetableViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val themeConfig by ThemePrefs.state.collectAsState()
    val hasCustomBg = !themeConfig.customBackgroundPath.isNullOrBlank()
    var selectedCluster by remember { mutableStateOf<List<Course>?>(null) }
    var activeCourseIndex by remember { mutableStateOf(0) }
    var pendingCancel by remember { mutableStateOf<Course?>(null) }
    val snackbar = remember { androidx.compose.material3.SnackbarHostState() }
    var pendingDelete by remember { mutableStateOf<Course?>(null) }

    // 切换课表上下文或数据变化时重新预选，不让旧位置直接触发添加。
    var pendingBlankCell by remember(
        state.currentSemester, state.currentWeek, state.selectedDay,
        state.isDayView, state.showWeekend, state.courses, state.timeSlots
    ) { mutableStateOf<PendingBlankCell?>(null) }

    val handleBlankClick: (Int, Int, Int, Offset) -> Unit = { week, day, slot, position ->
        val pending = pendingBlankCell
        if (pending != null && pending.week == week && pending.dayOfWeek == day && pending.slotNumber == slot) {
            pendingBlankCell = null
            viewModel.setWeek(week)
            val target = viewModel.onBlankCellClick(day, slot)
            val semester = viewModel.state.value.currentSemester
            if (target != null && semester != null) {
                onAddCourse(semester.id, target)
            }
        } else {
            pendingBlankCell = PendingBlankCell(week, day, slot, position)
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { pendingBlankCell = null }

    LaunchedEffect(state.message) {
        if (state.message.isNotBlank()) {
            val deleted = state.undoCourse
            val result = snackbar.showSnackbar(state.message, actionLabel = if (deleted != null) "撤销" else null,
                duration = androidx.compose.material3.SnackbarDuration.Long)
            if (result == androidx.compose.material3.SnackbarResult.ActionPerformed && deleted != null) viewModel.undoDelete(deleted)
            else viewModel.clearMessage()
        }
    }
    pendingCancel?.let { course -> AlertDialog(onDismissRequest = { pendingCancel = null },
        title = { Text("第 ${state.currentWeek} 周停课？") },
        text = { Text("仅取消这次「${course.name}」，其他周保留。修改前会自动保存恢复点。") },
        confirmButton = { TextButton(onClick = { viewModel.cancelOnce(course, state.currentWeek); pendingCancel = null }) { Text("确认停课") } },
        dismissButton = { TextButton(onClick = { pendingCancel = null }) { Text("保留") } }) }
    Scaffold(
        snackbarHost = { androidx.compose.material3.SnackbarHost(snackbar) },
        containerColor = if (hasCustomBg) Color.Transparent else MaterialTheme.colorScheme.background,
        floatingActionButton = {
            if (state.currentSemester != null) FloatingActionButton(
                onClick = {
                    pendingBlankCell = null
                    state.currentSemester?.let { onAddCourse(it.id, null) }
                },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "添加课程", tint = MaterialTheme.colorScheme.onPrimary)
            }
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
                // 整页统一滚动，紧凑顶栏随课表一起滑出屏幕。
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    // ===== 极简紧凑顶栏: 菜单+学期名 | ‹ 第 N 周 › 回本周 | 周末 | 视图切换 =====
                    val actualWeek = WeekCalculator.currentWeek(semester.startDate, semester.totalWeeks)
                    CompactTopBar(
                        onSelectSemester = onNavigateToSemesterSettings,
                        semesterName = semester.name,
                        currentWeek = state.currentWeek,
                        totalWeeks = semester.totalWeeks,
                        isCurrentWeek = state.currentWeek == actualWeek,
                        isDayView = state.isDayView,
                        showWeekend = state.showWeekend,
                        onPrevWeek = viewModel::prevWeek,
                        onNextWeek = viewModel::nextWeek,
                        onBackToToday = { viewModel.setWeek(actualWeek) },
                        onToggleWeekend = viewModel::toggleWeekend,
                        onToggleView = viewModel::toggleView,
                        onNavigateToImport = onNavigateToImport,
                        onNavigateToSettings = onNavigateToSettings
                    )

                    // ===== 应用内胶囊灵动岛 =====
                    val islandConfig by com.chen.schedule.island.IslandPrefs.state.collectAsState()
                    if (islandConfig.inAppEnabled) {
                        com.chen.schedule.island.InAppCapsuleIsland(
                            onCourseClick = { course ->
                                selectedCluster = listOf(course)
                                activeCourseIndex = 0
                            }
                        )
                    }

                    // 本学期暂无课程时显示直观的新手引导卡片
                    if (state.courses.isEmpty()) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Add,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "本学期暂无课程",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        "点击空白格子即可添加，或直接导入课表",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                TextButton(
                                    onClick = onNavigateToImport
                                ) {
                                    Text("导入课表")
                                }
                            }
                        }
                    }

                    // ===== 极度跟手的真实左右滑动切周 (HorizontalPager 实时跟随手指、惯性滑动与边缘回弹) =====
                    val pagerState = rememberPagerState(
                        initialPage = (state.currentWeek - 1).coerceIn(0, (semester.totalWeeks - 1).coerceAtLeast(0))
                    ) {
                        semester.totalWeeks.coerceAtLeast(1)
                    }

                    // 监听 Pager 滑动结束 (settledPage), 同步当前查看周次到 ViewModel
                    LaunchedEffect(pagerState) {
                        snapshotFlow { pagerState.settledPage }
                            .collect { settledPage ->
                                val newWeek = settledPage + 1
                                if (newWeek != state.currentWeek) {
                                    viewModel.setWeek(newWeek)
                                }
                            }
                    }

                    // 监听外部按键切周 (如顶栏 ‹ / › / 回到本周按键点击), 平滑滚动 Pager
                    LaunchedEffect(state.currentWeek) {
                        val targetPage = (state.currentWeek - 1).coerceIn(0, (semester.totalWeeks - 1).coerceAtLeast(0))
                        if (pagerState.currentPage != targetPage && targetPage < pagerState.pageCount) {
                            pagerState.animateScrollToPage(targetPage)
                        }
                    }

                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxWidth(),
                        key = { page -> page }
                    ) { page ->
                        val week = page + 1
                        if (state.isDayView) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                DaySelector(
                                    selectedDay = state.selectedDay,
                                    onDaySelected = viewModel::selectDay,
                                    showWeekend = state.showWeekend,
                                    semesterStartDate = semester.startDate,
                                    currentWeek = week
                                )
                                DayView(
                                    isToday = week == WeekCalculator.activeWeek(semester.startDate, semester.totalWeeks) && state.selectedDay == LocalDate.now().dayOfWeek.value,
                                    clusters = viewModel.getCourseClusters(week),
                                    timeSlots = state.timeSlots,
                                    pendingCell = pendingBlankCell?.takeIf { it.week == week && it.dayOfWeek == state.selectedDay },
                                    cardAlpha = if (hasCustomBg) themeConfig.cardAlpha else 1.0f,
                                    onCourseClick = { primary, cluster ->
                                        pendingBlankCell = null
                                        selectedCluster = cluster
                                        activeCourseIndex = cluster.indexOfFirst { it.id == primary.id }.coerceAtLeast(0)
                                    },
                                    onBlankCellClick = { slotNumber, position ->
                                        handleBlankClick(week, state.selectedDay, slotNumber, position)
                                    }
                                )
                            }
                        } else {
                            WeekView(
                                clusters = viewModel.getCourseClusters(week),
                                timeSlots = state.timeSlots,
                                showWeekend = state.showWeekend,
                                semesterStartDate = semester.startDate,
                                currentWeek = week,
                                pendingCell = pendingBlankCell,
                                cardAlpha = if (hasCustomBg) themeConfig.cardAlpha else 1.0f,
                                onCourseClick = { primary, cluster ->
                                    pendingBlankCell = null
                                    selectedCluster = cluster
                                    activeCourseIndex = cluster.indexOfFirst { it.id == primary.id }.coerceAtLeast(0)
                                },
                                onBlankCellClick = { dayOfWeek, slotNumber, position ->
                                    handleBlankClick(week, dayOfWeek, slotNumber, position)
                                }
                            )
                        }
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
            text = { Text("将删除「${course.name}」的这条上课安排，删除后可撤销，也会保存自动恢复点。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteCourse(course)
                    pendingDelete = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("保留课程") } }
        )
    }

    // 课程详情弹窗 (支持重叠课程左右滑动与退出记忆)
    selectedCluster?.let { cluster ->
        CourseDetailDialog(
            cluster = cluster,
            initialIndex = activeCourseIndex,
            onDismiss = { closedCourse ->
                viewModel.setPreferredCourse(closedCourse, cluster)
                selectedCluster = null
            },
            onEdit = { course ->
                state.currentSemester?.let { sem ->
                    viewModel.setPreferredCourse(course, cluster)
                    onEditCourse(course.id, sem.id, state.currentWeek)
                    selectedCluster = null
                }
            },
            onCancelOnce = { course -> pendingCancel = course; selectedCluster = null },
            onDelete = { course ->
                viewModel.setPreferredCourse(course, cluster)
                pendingDelete = course
                selectedCluster = null
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
        IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(34.dp)) {
            Icon(
                Icons.Default.Menu,
                contentDescription = "菜单",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(22.dp)
            )
        }
        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false }
        ) {
            DropdownMenuItem(
                text = { Text("导入课表", fontSize = 13.5.sp) },
                leadingIcon = { Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp)) },
                onClick = { menuOpen = false; onNavigateToImport() }
            )
            DropdownMenuItem(
                text = { Text("设置", fontSize = 13.5.sp) },
                leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp)) },
                onClick = { menuOpen = false; onNavigateToSettings() }
            )
        }
    }
}

/* ===================== 极简单行顶栏 ===================== */

@Composable
private fun CompactTopBar(
    onSelectSemester: () -> Unit,
    semesterName: String,
    currentWeek: Int,
    totalWeeks: Int,
    isCurrentWeek: Boolean,
    isDayView: Boolean,
    showWeekend: Boolean,
    onPrevWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onBackToToday: () -> Unit,
    onToggleWeekend: () -> Unit,
    onToggleView: () -> Unit,
    onNavigateToImport: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 6.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. 菜单 + 学期名(带切换提示图标)
        TopMenu(
            onNavigateToImport = onNavigateToImport,
            onNavigateToSettings = onNavigateToSettings
        )
        Row(
            modifier = Modifier
                .weight(1f, fill = false)
                .clip(RoundedCornerShape(6.dp))
                .clickable(onClick = onSelectSemester)
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                semesterName,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Icon(
                Icons.Default.ArrowDropDown,
                contentDescription = "切换学期",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }

        Spacer(Modifier.weight(1f))

        // 2. 中间: 周切换 ‹ 第 N 周 › (+ 回本周微胶囊)
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onPrevWeek,
                enabled = currentWeek > 1,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Default.ChevronLeft, "上一周",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (currentWeek > 1) 1f else 0.25f),
                    modifier = Modifier.size(20.dp)
                )
            }
            Text(
                "第$currentWeek",
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                "/$totalWeeks",
                fontSize = 10.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            IconButton(
                onClick = onNextWeek,
                enabled = currentWeek < totalWeeks,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Default.ChevronRight, "下一周",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (currentWeek < totalWeeks) 1f else 0.25f),
                    modifier = Modifier.size(20.dp)
                )
            }
            if (!isCurrentWeek) {
                Text(
                    "本周",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable(onClick = onBackToToday)
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                )
            }
        }

        Spacer(Modifier.weight(1f))

        // 3. 右侧: 周末开关微按钮 + 视图切换微按钮
        Text(
            text = if (showWeekend) "周末" else "五天",
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Medium,
            color = if (showWeekend) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(if (showWeekend) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else Color.Transparent)
                .clickable(onClick = onToggleWeekend)
                .padding(horizontal = 5.dp, vertical = 2.5.dp)
        )

        Spacer(Modifier.width(2.dp))

        IconButton(onClick = onToggleView, modifier = Modifier.size(34.dp)) {
            Icon(
                if (isDayView) Icons.AutoMirrored.Filled.Notes else Icons.Default.DateRange,
                contentDescription = if (isDayView) "切换周视图" else "切换日视图",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/* ===================== 日选择 ===================== */

@Composable
private fun DaySelector(
    selectedDay: Int,
    onDaySelected: (Int) -> Unit,
    showWeekend: Boolean = false,
    semesterStartDate: Long? = null,
    currentWeek: Int = 1
) {
    val days = DayOfWeek.entries.filter { showWeekend || it.index <= 5 }
    val today = LocalDate.now()
    val semesterMonday = semesterStartDate?.let {
        com.chen.schedule.util.WeekCalculator.semesterMonday(it)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        days.forEach { day ->
            val selected = selectedDay == day.index
            val date = semesterMonday?.plusDays(((currentWeek - 1) * 7 + (day.index - 1)).toLong())
            val isToday = date == today || (date == null && day.index == today.dayOfWeek.value)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        when {
                            selected -> MaterialTheme.colorScheme.primary
                            isToday -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                            else -> Color.Transparent
                        }
                    )
                    .clickable { onDaySelected(day.index) }
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        day.label,
                        fontSize = 11.5.sp,
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
                    if (date != null) {
                        Text(
                            "${date.monthValue}/${date.dayOfMonth}",
                            fontSize = 9.sp,
                            color = when {
                                selected -> MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                                isToday -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            }
                        )
                    }
                }
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
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.DateRange,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(26.dp)
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
    cluster: List<Course>,
    initialIndex: Int = 0,
    onDismiss: (Course) -> Unit,
    onEdit: (Course) -> Unit,
    onCancelOnce: (Course) -> Unit,
    onDelete: (Course) -> Unit
) {
    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, (cluster.size - 1).coerceAtLeast(0))
    ) {
        cluster.size
    }
    val coroutineScope = rememberCoroutineScope()
    val currentCourse = cluster.getOrNull(pagerState.currentPage) ?: cluster.first()

    AlertDialog(
        onDismissRequest = { onDismiss(currentCourse) },
        shape = RoundedCornerShape(22.dp),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (cluster.size > 1) {
                    IconButton(
                        onClick = {
                            if (pagerState.currentPage > 0) {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                }
                            }
                        },
                        enabled = pagerState.currentPage > 0,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = "上一门")
                    }

                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(horizontal = 10.dp, vertical = 3.dp)
                    ) {
                        Text(
                            "第 ${pagerState.currentPage + 1}/${cluster.size} 门 (左右滑动)",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    IconButton(
                        onClick = {
                            if (pagerState.currentPage < cluster.size - 1) {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                }
                            }
                        },
                        enabled = pagerState.currentPage < cluster.size - 1,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.ChevronRight, contentDescription = "下一门")
                    }
                } else {
                    Text(
                        "课程详情",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                IconButton(
                    onClick = { onDismiss(currentCourse) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "关闭",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        },
        text = {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth(),
                key = { page -> "${cluster[page].id}_$page" }
            ) { page ->
                val course = cluster[page]
                val accent = Color(course.color)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(accent.copy(alpha = 0.18f))
                                .border(1.2.dp, accent.copy(alpha = 0.40f), RoundedCornerShape(14.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                course.name.take(1),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = accent
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(
                            course.name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(Modifier.height(2.dp))

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
            }
        },
        confirmButton = {
            Button(onClick = { onEdit(currentCourse) }) {
                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("编辑 / 调课")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { onDelete(currentCourse) }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = { onCancelOnce(currentCourse) }) {
                    Text("本周停课")
                }
                TextButton(onClick = { onDismiss(currentCourse) }) {
                    Text("关闭")
                }
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.7f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(14.dp)
            )
        }
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
                fontWeight = FontWeight.Medium,
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
                modifier = Modifier.size(14.dp)
            )
        }
    }
}
