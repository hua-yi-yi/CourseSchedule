package com.chen.schedule.ui.course

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
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
    viewModel: CourseEditViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(courseId) {
        courseId?.let { viewModel.loadCourse(it) }
    }

    LaunchedEffect(state.saved) {
        if (state.saved) onNavigateBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isEditing) "编辑课程" else "添加课程") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "返回")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            if (state.name.isNotBlank()) {
                                viewModel.save(semesterId, courseId)
                            }
                        },
                        enabled = state.name.isNotBlank()
                    ) {
                        Icon(Icons.Default.Check, "保存", tint = if (state.name.isNotBlank())
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
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
            Text("星期", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth()
            ) {
                DayOfWeek.entries.forEach { day ->
                    val selected = state.dayOfWeek == day.index
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .padding(2.dp)
                            .clickable { viewModel.updateDayOfWeek(day.index) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(8.dp)) {
                            Text(
                                day.label,
                                color = if (selected) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
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
                    range = 1..12,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(12.dp))
                NumberPicker(
                    label = "结束节次",
                    value = state.endSlot,
                    onValueChange = viewModel::updateEndSlot,
                    range = 1..12,
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
                    range = 1..20,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(12.dp))
                NumberPicker(
                    label = "结束周",
                    value = state.endWeek,
                    onValueChange = viewModel::updateEndWeek,
                    range = 1..20,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(12.dp))

            // Week type
            Text("周类型", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                WeekType.entries.forEach { wt ->
                    val selected = state.weekType == wt
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .padding(2.dp)
                            .clickable { viewModel.updateWeekType(wt) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(8.dp)) {
                            Text(
                                wt.label,
                                color = if (selected) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Color picker
            Text("课程颜色", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(4.dp))
            Column {
                courseColors.chunked(6).forEach { row ->
                    Row {
                        row.forEach { color ->
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .padding(3.dp)
                                    .clip(CircleShape)
                                    .then(
                                        if (state.color == color) Modifier
                                            .then(
                                                Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                            )
                                        else Modifier
                                    )
                                    .then(
                                        Modifier.background(Color(color), CircleShape)
                                    )
                                    .clickable { viewModel.updateColor(color) }
                            )
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
                Text("▼", modifier = Modifier.padding(8.dp))
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    range.forEach { n ->
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
