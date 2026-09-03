package com.chen.schedule.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.glance.ExperimentalGlanceApi
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.chen.schedule.di.DatabaseEntryPoint
import com.chen.schedule.util.WeekCalculator
import dagger.hilt.android.EntryPointAccessors
import java.time.LocalDate

data class WidgetCourse(
    val name: String,
    val classroom: String,
    val startSlot: Int,
    val endSlot: Int
)

data class WidgetData(
    val semesterName: String,
    val currentWeek: Int,
    val dayOfWeekLabel: String,
    val courses: List<WidgetCourse>
)

class TodayWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = loadWidgetData(context)
        provideContent {
            GlanceTheme {
                TodayWidgetContent(data)
            }
        }
    }

    private suspend fun loadWidgetData(context: Context): WidgetData {
        // 通过 EntryPoint 复用应用内 Hilt 提供的数据库单例,避免重复打开数据库
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            DatabaseEntryPoint::class.java
        )
        val sem = entryPoint.semesterDao().getCurrentSemester()
        if (sem == null) {
            return WidgetData("未设置学期", 0, "", emptyList())
        }

        val week = WeekCalculator.currentWeek(sem.startDate, sem.totalWeeks)
        val now = LocalDate.now()
        val dayIndex = now.dayOfWeek.value
        val dayLabel = when (dayIndex) {
            1 -> "周一"; 2 -> "周二"; 3 -> "周三"; 4 -> "周四"
            5 -> "周五"; 6 -> "周六"; 7 -> "周日"; else -> ""
        }

        val entities = entryPoint.courseDao().getCoursesByDayDirect(sem.id, dayIndex)
        val courses = entities
            .filter { course ->
                val weekMatch = when (course.weekType) {
                    "odd" -> week % 2 == 1
                    "even" -> week % 2 == 0
                    else -> true
                }
                weekMatch && course.startWeek <= week && course.endWeek >= week
            }
            .sortedBy { it.startSlot }
            .map { e ->
                WidgetCourse(
                    name = e.name,
                    classroom = e.classroom,
                    startSlot = e.startSlot,
                    endSlot = e.endSlot
                )
            }

        return WidgetData(sem.name, week, dayLabel, courses)
    }
}

/**
 * 课程数据变化后主动刷新桌面小组件。
 * (统一刷新入口见 WeekWidget.WidgetUpdater —— 它同时刷新今日课程与周课表组件)
 */
class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = TodayWidget()
}

@OptIn(ExperimentalGlanceApi::class)
@Composable
private fun TodayWidgetContent(data: WidgetData) {
    Column(
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(GlanceTheme.colors.surface)
            .padding(12.dp)
    ) {
        Text(
            text = "${data.semesterName} · 第${data.currentWeek}周 · ${data.dayOfWeekLabel}",
            style = TextStyle(
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = GlanceTheme.colors.onSurface
            )
        )

        Spacer(modifier = GlanceModifier.height(8.dp))

        if (data.courses.isEmpty()) {
            Text(
                text = "今日无课",
                style = TextStyle(
                    fontSize = 14.sp,
                    color = GlanceTheme.colors.onSurfaceVariant
                )
            )
        } else {
            // 课程较多时可在小组件内滚动,避免内容被裁切
            LazyColumn(modifier = GlanceModifier.fillMaxWidth()) {
                items(data.courses) { course ->
                    CourseRow(course)
                }
            }
        }
    }
}

@Composable
private fun CourseRow(course: WidgetCourse) {
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "${course.startSlot}-${course.endSlot}",
            style = TextStyle(
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = GlanceTheme.colors.primary
            ),
            modifier = GlanceModifier.width(32.dp)
        )

        Spacer(modifier = GlanceModifier.width(8.dp))

        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = course.name,
                style = TextStyle(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = GlanceTheme.colors.onSurface
                )
            )
            if (course.classroom.isNotBlank()) {
                Text(
                    text = course.classroom,
                    style = TextStyle(
                        fontSize = 11.sp,
                        color = GlanceTheme.colors.onSurfaceVariant
                    )
                )
            }
        }
    }
}
