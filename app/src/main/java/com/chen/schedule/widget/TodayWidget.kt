package com.chen.schedule.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ExperimentalGlanceApi
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.chen.schedule.MainActivity

/**
 * 4×1 今日课程小组件：
 * 横条紧凑排版，完整展示上课时间、节次与上课地点。
 */
class TodayWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = TodayWidgetDataLoader.load(context)
        provideContent {
            GlanceTheme {
                TodayWidgetContent(data)
            }
        }
    }
}

/**
 * 课程数据变化后主动刷新桌面小组件。
 * (统一刷新入口见 WeekWidget.WidgetUpdater —— 它同时刷新 4×1、3×3 与周课表组件)
 */
class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = TodayWidget()
}

@OptIn(ExperimentalGlanceApi::class)
@Composable
private fun TodayWidgetContent(data: WidgetData) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .background(GlanceTheme.colors.surface)
            .cornerRadius(16.dp)
            .padding(10.dp)
            .clickable(actionStartActivity<MainActivity>())
    ) {
        // 顶栏：学期 · 周次 · 星期
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (data.currentWeek <= 0) data.semesterName
                else "${data.semesterName} · 第${data.currentWeek}周 · ${data.dayOfWeekLabel}",
                style = TextStyle(
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = GlanceTheme.colors.onSurface
                ),
                modifier = GlanceModifier.defaultWeight(),
                maxLines = 1
            )
            if (data.courses.isNotEmpty()) {
                Text(
                    text = "${data.courses.size}门课",
                    style = TextStyle(
                        fontSize = 11.sp,
                        color = GlanceTheme.colors.onSurfaceVariant
                    )
                )
            }
        }

        Spacer(modifier = GlanceModifier.height(6.dp))

        if (data.courses.isEmpty()) {
            Text(
                text = "今日无课",
                style = TextStyle(
                    fontSize = 13.sp,
                    color = GlanceTheme.colors.onSurfaceVariant
                )
            )
        } else {
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
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 课程颜色微条
        Box(
            modifier = GlanceModifier
                .width(3.dp)
                .height(30.dp)
                .background(ColorProvider(Color(course.color)))
                .cornerRadius(1.5.dp)
        ) {}

        Spacer(modifier = GlanceModifier.width(6.dp))

        // 节次与时间区间
        Column(modifier = GlanceModifier.width(68.dp)) {
            Text(
                text = if (course.startTime.isNotBlank()) course.startTime else "第${course.startSlot}节",
                style = TextStyle(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = GlanceTheme.colors.primary
                ),
                maxLines = 1
            )
            Text(
                text = course.slotDisplay,
                style = TextStyle(
                    fontSize = 9.5.sp,
                    color = GlanceTheme.colors.onSurfaceVariant
                ),
                maxLines = 1
            )
        }

        Spacer(modifier = GlanceModifier.width(6.dp))

        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = course.name,
                style = TextStyle(
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = GlanceTheme.colors.onSurface
                ),
                maxLines = 1
            )
            Text(
                text = if (course.startTime.isNotBlank() && course.endTime.isNotBlank()) {
                    "${course.startTime}-${course.endTime} · ${course.locationAndTeacherDisplay}"
                } else {
                    course.locationAndTeacherDisplay
                },
                style = TextStyle(
                    fontSize = 10.5.sp,
                    color = GlanceTheme.colors.onSurfaceVariant
                ),
                maxLines = 2
            )
        }
    }
}
