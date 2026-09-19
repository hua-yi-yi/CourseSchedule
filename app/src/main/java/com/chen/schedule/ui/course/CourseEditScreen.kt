package com.chen.schedule.ui.course

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.chen.schedule.domain.model.DayOfWeek
import com.chen.schedule.domain.model.WeekType
import com.chen.schedule.ui.theme.courseColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseEditScreen(
    courseId: Long?,
    semesterId: Long,
    onNavigateBack: () -> Unit,
    /** 空白格点击带来的预填位置(null = FAB 进入或编辑已有课程)。 */
    prefillDay: Int? = null,
    prefillSlot: Int? = null,
    prefillWeek: Int? = null,
    prefillTotalWeeks: Int = 16,
    viewModel: CourseEditViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(semesterId, courseId) {
        viewModel.loadConfiguration(semesterId)
        if (courseId != null) {
            viewModel.loadCourse(courseId)
        } else if (prefillDay != null && prefillSlot != null && prefillWeek != null) {
            viewModel.applyPrefill(prefillDay, prefillSlot, prefillWeek)
        }
    }

    LaunchedEffect(state.saved) {
        if (state.saved) onNavigateBack()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(if (state.isEditing) "编辑课程" else "添加课程", fontWeight = FontWeight.Bold)
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", modifier = Modifier.size(20.dp))
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            if (state.name.isNotBlank()) {
                                viewModel.save(semesterId, courseId)
                            }
                        },
                        enabled = state.name.isNotBlank() && !state.isSaving
                    ) {
                        Icon(
                            Icons.Default.Check, "保存",
                            tint = if (state.name.isNotBlank())
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(20.dp)
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
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(8.dp))
            }
            if (state.isSaving) Text("正在保存…", color = MaterialTheme.colorScheme.primary)
            // Course name
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::updateName,
                label = { Text("课程名称 *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(Modifier.height(12.dp))

            // Teacher
            OutlinedTextField(
                value = state.teacher,
                onValueChange = viewModel::updateTeacher,
                label = { Text("教师") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(Modifier.height(12.dp))

            // Classroom
            OutlinedTextField(
                value = state.classroom,
                onValueChange = viewModel::updateClassroom,
                label = { Text("教室") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(Modifier.height(12.dp))

            // Day of week
            Text("星期", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth()
            ) {
                DayOfWeek.entries.forEach { day ->
                    val selected = state.dayOfWeek == day.index
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(2.dp)
                            .clip(MaterialTheme.shapes.small)
                            .background(
                                if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceContainerHighest
                            )
                            .clickable { viewModel.updateDayOfWeek(day.index) }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            day.label,
                            color = if (selected) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Time slots
            Row(modifier = Modifier.fillMaxWidth()) {
                NumberPicker(
                    label = "开始节次",
                    value = state.startSlot,
                    onValueChange = viewModel::updateStartSlot,
                    range = 1..(state.slotNumbers.maxOrNull() ?: 1),
                    choices = state.slotNumbers,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(12.dp))
                NumberPicker(
                    label = "结束节次",
                    value = state.endSlot,
                    onValueChange = viewModel::updateEndSlot,
                    range = 1..(state.slotNumbers.maxOrNull() ?: 1),
                    choices = state.slotNumbers,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(12.dp))

            // Week range
            Row(modifier = Modifier.fillMaxWidth()) {
                NumberPicker(
                    label = "起始周",
                    value = state.startWeek,
                    onValueChange = viewModel::updateStartWeek,
                    range = 1..state.totalWeeks,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(12.dp))
                NumberPicker(
                    label = "结束周",
                    value = state.endWeek,
                    onValueChange = viewModel::updateEndWeek,
                    range = 1..state.totalWeeks,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(12.dp))

            // Week type
            Text("周类型", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                WeekType.entries.forEach { wt ->
                    val selected = state.weekType == wt
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(2.dp)
                            .clip(MaterialTheme.shapes.small)
                            .background(
                                if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceContainerHighest
                            )
                            .clickable { viewModel.updateWeekType(wt) }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            wt.label,
                            color = if (selected) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }

            if (state.conflicts.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = "排课冲突",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "排课冲突提醒 (${state.conflicts.size})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        state.conflicts.forEach { conflict ->
                            Text(
                                "• 与《${conflict.existingCourse.name}》时间重叠：${conflict.weekDescription} · ${conflict.slotDescription}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Color picker
            Text("课程颜色", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    courseColors.chunked(6).forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            row.forEach { color ->
                                val selected = state.color == color
                                val itemColor = Color(color)
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(CircleShape)
                                        .then(
                                            if (selected) Modifier
                                                .border(2.5.dp, itemColor, CircleShape)
                                                .padding(3.5.dp)
                                            else Modifier.padding(3.5.dp)
                                        )
                                        .clip(CircleShape)
                                        .background(itemColor)
                                        .clickable { viewModel.updateColor(color) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (selected) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = "已选中",
                                            tint = Color.White,
                                            modifier = Modifier.size(13.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Note
            OutlinedTextField(
                value = state.note,
                onValueChange = viewModel::updateNote,
                label = { Text("备注") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NumberPicker(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    range: IntRange,
    choices: List<Int> = range.toList(),
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = value.toString(),
        onValueChange = {
            it.toIntOrNull()?.let { v ->
                if (v in range) onValueChange(v)
            }
        },
        label = { Text(label) },
        modifier = modifier,
        singleLine = true,
        readOnly = true,
        trailingIcon = {
            Box(modifier = Modifier.clickable { expanded = true }) {
                Icon(
                    Icons.Default.ArrowDropDown,
                    contentDescription = "选择",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp).padding(end = 4.dp)
                )
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    choices.forEach { n ->
                        DropdownMenuItem(
                            text = { Text("$n") },
                            onClick = {
                                onValueChange(n)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
    )
}
