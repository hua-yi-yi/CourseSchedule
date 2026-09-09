package com.chen.schedule.ui.schedule

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.chen.schedule.domain.model.Semester
import com.chen.schedule.util.ScheduleStatus

/**
 * 选择已有学期。
 * 用单选样式表示选中项,并另标注「当前使用」;确认后才切换,取消不修改。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SemesterListScreen(
    onNavigateBack: () -> Unit,
    onEditSemester: (Long) -> Unit,
    viewModel: ScheduleConfigViewModel = hiltViewModel()
) {
    val hub by viewModel.hub.collectAsState()
    var allSemesters by remember { mutableStateOf<List<Semester>>(emptyList()) }
    var selectedId by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(hub.semester?.id) {
        allSemesters = viewModel.loadAllSemesters()
        selectedId = hub.semester?.id
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("选择学期", fontWeight = FontWeight.Bold) },
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
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
            ) {
                Spacer(Modifier.height(8.dp))
                if (allSemesters.isEmpty()) {
                    EmptyHint("还没有学期,请先新建学期")
                }
                allSemesters.forEach { semester ->
                    SemesterPickRow(
                        semester = semester,
                        selected = selectedId == semester.id,
                        isCurrent = hub.semester?.id == semester.id,
                        dateText = viewModel.formatDate(semester.startDate),
                        onSelect = { selectedId = semester.id },
                        onEdit = { onEditSemester(semester.id) }
                    )
                }
                Spacer(Modifier.height(24.dp))
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.weight(1f)
                ) { Text("取消") }
                Button(
                    onClick = {
                        selectedId?.let { viewModel.switchSemester(it) }
                        onNavigateBack()
                    },
                    enabled = selectedId != null && selectedId != hub.semester?.id,
                    modifier = Modifier.weight(1f)
                ) { Text("确认切换") }
            }
        }
    }
}

@Composable
private fun SemesterPickRow(
    semester: Semester,
    selected: Boolean,
    isCurrent: Boolean,
    dateText: String,
    onSelect: () -> Unit,
    onEdit: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onSelect() },
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = selected, onClick = onSelect)
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        semester.name.ifBlank { "未命名学期" },
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (isCurrent) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "当前使用",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    "$dateText 起 · 共 ${semester.totalWeeks} 周",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onEdit) {
                Icon(
                    Icons.Default.DateRange,
                    contentDescription = "编辑 ${semester.name}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * 新建 / 编辑学期。
 * 开学日期使用日期选择器;「根据当前周推算开学日期」放在可折叠辅助区域。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SemesterEditScreen(
    semesterId: Long?,
    onDone: () -> Unit,
    onCancel: () -> Unit,
    viewModel: ScheduleConfigViewModel = hiltViewModel()
) {
    val form by viewModel.semesterForm.collectAsState()
    var showDatePicker by remember { mutableStateOf(false) }
    var showHelper by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }

    LaunchedEffect(semesterId) { viewModel.startSemesterForm(semesterId) }
    LaunchedEffect(form.saved) { if (form.saved) onDone() }

    val requestCancel: () -> Unit = {
        if (form.dirty) showDiscardDialog = true else onCancel()
    }
    androidx.activity.compose.BackHandler { requestCancel() }

    if (showDatePicker) {
        val initial = form.dateMillis ?: System.currentTimeMillis()
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initial)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.updateSemesterDate(pickerState.selectedDateMillis)
                    showDatePicker = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("取消") } }
        ) { DatePicker(state = pickerState) }
    }

    if (showDiscardDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("放弃未保存的修改?") },
            text = { Text("学期的修改尚未保存,返回将丢弃这些内容。") },
            confirmButton = {
                TextButton(onClick = {
                    showDiscardDialog = false
                    onCancel()
                }) { Text("放弃", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) { Text("继续编辑") }
            }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(if (form.isEditing) "编辑学期" else "新建学期", fontWeight = FontWeight.Bold)
                },
                navigationIcon = {
                    IconButton(onClick = requestCancel) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.saveSemesterForm() },
                        enabled = !form.saving
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "保存",
                            tint = MaterialTheme.colorScheme.primary
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
                .padding(16.dp)
        ) {
            form.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(8.dp))
            }

            OutlinedTextField(
                value = form.name,
                onValueChange = viewModel::updateSemesterName,
                label = { Text("学期名称") },
                singleLine = true,
                placeholder = { Text("例:2026–2027 第一学期") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))

            Text("开学日期", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showDatePicker = true },
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.DateRange,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        viewModel.formatDate(form.dateMillis),
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (form.dateMillis == null) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "选择",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = form.totalWeeks,
                onValueChange = viewModel::updateSemesterWeeks,
                label = { Text("总周数") },
                singleLine = true,
                supportingText = {
                    Text("有效范围 ${ScheduleStatus.MIN_WEEKS}–${ScheduleStatus.MAX_WEEKS}")
                },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))

            // 折叠辅助区域:根据当前周推算开学日期
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showHelper = !showHelper },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "根据当前周推算开学日期",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Icon(
                    if (showHelper) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            AnimatedVisibility(visible = showHelper) {
                Column {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        OutlinedTextField(
                            value = form.weekHint,
                            onValueChange = viewModel::updateWeekHint,
                            label = { Text("当前是第几周") },
                            singleLine = true,
                            placeholder = { Text("如 12") },
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = viewModel::computeStartDateFromWeek,
                            enabled = form.weekHint.isNotBlank(),
                            modifier = Modifier.height(56.dp)
                        ) { Text("推算") }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "推算结果会写入上方开学日期,可再手动调整",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = { viewModel.saveSemesterForm() },
                enabled = !form.saving,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (form.saving) "正在保存…" else if (form.isEditing) "保存修改" else "创建学期")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = requestCancel,
                modifier = Modifier.fillMaxWidth()
            ) { Text("取消") }
            Spacer(Modifier.height(24.dp))
        }
    }
}
