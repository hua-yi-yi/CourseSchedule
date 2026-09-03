package com.chen.schedule.ui.timetable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chen.schedule.domain.model.Course
import com.chen.schedule.domain.model.DayOfWeek
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun TimetableScreen(
    onAddCourse: (Long) -> Unit,
    onEditCourse: (Long, Long) -> Unit,
    onNavigateToScheduleConfig: () -> Unit,
    viewModel: TimetableViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var selectedCourse by remember { mutableStateOf<Course?>(null) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { state.currentSemester?.let { onAddCourse(it.id) } }
            ) {
                Icon(Icons.Default.Add, contentDescription = "添加课程")
            }
        }
    ) { padding ->
        if (state.currentSemester == null) {
            // No semester - show empty state
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = padding.calculateBottomPadding()),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("还没有设置学期", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onNavigateToScheduleConfig) {
                        Text("去设置学期和作息时间")
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = padding.calculateBottomPadding())
            ) {
                // Top bar info + week selector
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    state.currentSemester?.let {
                        Text(
                            "${it.name} · 第${state.currentWeek}周",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = viewModel::toggleView) {
                        Text(
                            if (state.isDayView) "周视图" else "日视图",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }

                WeekSelector(
                    currentWeek = state.currentWeek,
                    totalWeeks = state.currentSemester?.totalWeeks ?: 16,
                    onPrevWeek = viewModel::prevWeek,
                    onNextWeek = viewModel::nextWeek,
                    onToggleWeekend = viewModel::toggleWeekend,
                    showWeekend = state.showWeekend
                )

                if (state.isDayView) {
                    // Day selector
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
                    WeekView(
                        courses = viewModel.getFilteredCourses(),
                        timeSlots = state.timeSlots,
                        showWeekend = state.showWeekend,
                        onCourseClick = { selectedCourse = it }
                    )
                }
            }
        }
    }

    // Course detail dialog
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

@Composable
private fun WeekSelector(
    currentWeek: Int,
    totalWeeks: Int,
    onPrevWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onToggleWeekend: () -> Unit,
    showWeekend: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(onClick = onPrevWeek) {
            Icon(Icons.Default.ChevronLeft, "上一周")
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "第 $currentWeek 周",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                "共 $totalWeeks 周",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        IconButton(onClick = onNextWeek) {
            Icon(Icons.Default.ChevronRight, "下一周")
        }

        TextButton(onClick = onToggleWeekend) {
            Text(if (showWeekend) "隐藏周末" else "显示周末")
        }
    }
}

@Composable
private fun DaySelector(
    selectedDay: Int,
    onDaySelected: (Int) -> Unit,
    showWeekend: Boolean = false
) {
    val days = DayOfWeek.entries.filter { showWeekend || it.index <= 5 }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        days.forEach { day ->
            TextButton(
                onClick = { onDaySelected(day.index) }
            ) {
                Text(
                    day.label,
                    fontWeight = if (selectedDay == day.index) FontWeight.Bold else FontWeight.Normal,
                    color = if (selectedDay == day.index)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun CourseDetailDialog(
    course: Course,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(course.name) },
        text = {
            Column {
                if (course.teacher.isNotBlank()) {
                    Text("教师：${course.teacher}")
                }
                if (course.classroom.isNotBlank()) {
                    Text("教室：${course.classroom}")
                }
                Text("时间：${DayOfWeek.entries.find { it.index == course.dayOfWeek }?.label ?: ""} ${course.startSlot}-${course.endSlot}节")
                Text("周次：第${course.startWeek}-${course.endWeek}周 ${course.weekType.label}")
                if (course.note.isNotBlank()) {
                    Text("备注：${course.note}")
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
