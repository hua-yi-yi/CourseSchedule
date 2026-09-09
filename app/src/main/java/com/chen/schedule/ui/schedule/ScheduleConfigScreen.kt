package com.chen.schedule.ui.schedule

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.chen.schedule.domain.model.Semester
import com.chen.schedule.domain.model.TimeScheme

/**
 * 「学期与作息」首页:两张菜单卡片,只显示摘要与完成状态,不在此展开编辑表单。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleConfigScreen(
    onNavigateBack: () -> Unit,
    onOpenSemesterMenu: () -> Unit,
    onOpenSchemeMenu: () -> Unit,
    viewModel: ScheduleConfigViewModel = hiltViewModel()
) {
    val hub by viewModel.hub.collectAsState()

    LaunchedEffect(Unit) { viewModel.recomputeStatus() }
    LaunchedEffect(hub.semester?.id, hub.currentSlots, hub.currentCourses) {
        viewModel.recomputeStatus()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("学期与作息", fontWeight = FontWeight.Bold) },
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
            Spacer(Modifier.height(8.dp))

            ScheduleMenuCard(
                title = "当前学期",
                summary = semesterSummary(hub.semester, viewModel),
                done = hub.semesterDone,
                icon = {
                    Icon(
                        Icons.Default.CalendarMonth,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                },
                onClick = onOpenSemesterMenu
            )

            Spacer(Modifier.height(12.dp))

            ScheduleMenuCard(
                title = "当前作息",
                summary = schemeSummary(hub.currentScheme, hub.currentSlots.size, viewModel),
                done = hub.schemeDone,
                icon = {
                    Icon(
                        Icons.Default.Schedule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                },
                onClick = onOpenSchemeMenu
            )

            if (!hub.schemeDone && hub.schemeCheck.missingSlots.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "作息未覆盖课程使用的节次:${hub.schemeCheck.missingSlots.joinToString("、") { "第${it}节" }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = StatusAmber
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

/** 学期卡片摘要:名称 · 日期 · 总周数(取不到时给出明确占位)。 */
private fun semesterSummary(semester: Semester?, viewModel: ScheduleConfigViewModel): String {
    if (semester == null) return "尚未选择"
    val date = viewModel.formatDate(semester.startDate)
    val name = semester.name.ifBlank { "未命名学期" }
    return "$name · $date · 共 ${semester.totalWeeks} 周"
}

private fun schemeSummary(
    scheme: TimeScheme?,
    slotCount: Int,
    viewModel: ScheduleConfigViewModel
): String {
    if (scheme == null) return "尚未选择"
    val count = if (slotCount > 0) "共 $slotCount 节" else "暂无节次"
    return "${scheme.name} · $count"
}

// ==================== 二级菜单:当前学期 ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SemesterMenuScreen(
    onNavigateBack: () -> Unit,
    onSelectExisting: () -> Unit,
    onCreateNew: () -> Unit,
    onEditCurrent: (Long) -> Unit,
    viewModel: ScheduleConfigViewModel = hiltViewModel()
) {
    val hub by viewModel.hub.collectAsState()
    val currentId = hub.semester?.id

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("当前学期", fontWeight = FontWeight.Bold) },
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
            Spacer(Modifier.height(8.dp))
            MenuEntryRow(
                icon = { Icon(Icons.Default.Checklist, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(15.dp)) },
                title = "选择已有学期",
                subtitle = "在已有学期之间切换,每个学期保留独立课程数据",
                onClick = onSelectExisting
            )
            MenuEntryRow(
                icon = { Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(15.dp)) },
                title = "新建学期",
                subtitle = "填写名称、开学日期与总周数",
                onClick = onCreateNew
            )
            MenuEntryRow(
                icon = { Icon(Icons.Default.Edit, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(15.dp)) },
                title = "编辑当前学期",
                subtitle = if (currentId == null) "请先选择或新建学期" else "修改当前学期的名称、日期与周数",
                onClick = { currentId?.let(onEditCurrent) }
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ==================== 二级菜单:当前作息 ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchemeMenuScreen(
    onNavigateBack: () -> Unit,
    onSelectExisting: () -> Unit,
    onCreateNew: () -> Unit,
    onEditCurrent: (Long) -> Unit,
    viewModel: ScheduleConfigViewModel = hiltViewModel()
) {
    val hub by viewModel.hub.collectAsState()
    val currentId = hub.semester?.schemeId ?: 0L

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("当前作息", fontWeight = FontWeight.Bold) },
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
            Spacer(Modifier.height(8.dp))
            MenuEntryRow(
                icon = { Icon(Icons.Default.SwapHoriz, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(15.dp)) },
                title = "选择作息方案",
                subtitle = "内置模板与我的方案,选择后先预览再启用",
                onClick = onSelectExisting
            )
            MenuEntryRow(
                icon = { Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(15.dp)) },
                title = "新建作息方案",
                subtitle = "从模板复制、手动填写或文本导入",
                onClick = onCreateNew
            )
            MenuEntryRow(
                icon = { Icon(Icons.Default.Edit, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(15.dp)) },
                title = "编辑当前方案",
                subtitle = "内置模板会先复制为自定义方案,不影响其他学期",
                onClick = { onEditCurrent(currentId) }
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}
