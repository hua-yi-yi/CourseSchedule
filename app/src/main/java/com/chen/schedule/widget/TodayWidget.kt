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
 * 与 3×3、4×4 保持完全一致的视觉规范（18dp 大圆角、统一双层顶栏、内嵌卡片底衬）。
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
            .cornerRadius(18.dp)
            .padding(12.dp)
            .clickable(actionStartActivity<MainActivity>())
    ) {
        // 顶部信息区：完全与 3×3 对齐（主标题日期·星期，副标题学期·周次，右侧课程数）
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = "${data.dateLabel} · ${data.dayOfWeekLabel}",
                    style = TextStyle(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = GlanceTheme.colors.onSurface
                    )
                )
                Text(
                    text = if (data.currentWeek > 0) "${data.semesterName} · 第${data.currentWeek}周" else data.semesterName,
                    style = TextStyle(
                        fontSize = 11.sp,
                        color = GlanceTheme.colors.onSurfaceVariant
                    ),
                    maxLines = 1
                )
            }
            Text(
                text = if (data.courses.isNotEmpty()) "共 ${data.courses.size} 门课" else "无课",
                style = TextStyle(
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = GlanceTheme.colors.onSurfaceVariant
                )
            )
        }

        Spacer(modifier = GlanceModifier.height(8.dp))

        if (data.courses.isEmpty()) {
            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .padding(top = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "今日无课",
                    style = TextStyle(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = GlanceTheme.colors.onSurfaceVariant
                    )
                )
                Spacer(modifier = GlanceModifier.height(4.dp))
                Text(
                    text = "享受属于自己的自由时间吧",
                    style = TextStyle(
                        fontSize = 11.5.sp,
                        color = GlanceTheme.colors.onSurfaceVariant
                    )
                )
            }
        } else {
            LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                items(data.courses) { course ->
                    TodayCourseCard(course)
                }
            }
        }
    }
}

@Composable
private fun TodayCourseCard(course: WidgetCourse) {
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(vertical = 3.5.dp)
            .background(GlanceTheme.colors.surfaceVariant)
            .cornerRadius(10.dp)
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 左侧课程微条（与应用内调色盘颜色完全对应）
        Box(
            modifier = GlanceModifier
                .width(3.dp)
                .height(44.dp)
                .background(ColorProvider(Color(course.color)))
                .cornerRadius(1.5.dp)
        ) {}

        Spacer(modifier = GlanceModifier.width(8.dp))

        Column(modifier = GlanceModifier.defaultWeight()) {
            // 课程名称：完整显示
            Text(
                text = course.name,
                style = TextStyle(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = GlanceTheme.colors.onSurface
                ),
                maxLines = 1
            )

            Spacer(modifier = GlanceModifier.height(2.dp))

            // 上课时间：完整写下具体起止时间与节次
            Text(
                text = if (course.startTime.isNotBlank() && course.endTime.isNotBlank()) {
                    "时间: ${course.startTime} - ${course.endTime} (${course.slotDisplay})"
                } else {
                    "时间: ${course.slotDisplay}"
                },
                style = TextStyle(
                    fontSize = 11.sp,
                    color = GlanceTheme.colors.onSurfaceVariant
                ),
                maxLines = 1
            )

            Spacer(modifier = GlanceModifier.height(1.5.dp))

            // 上课地点：完整写下教室地点（支持软折行不截断）与任课教师
            Text(
                text = "地点: ${course.locationAndTeacherDisplay}",
                style = TextStyle(
                    fontSize = 11.sp,
                    color = GlanceTheme.colors.onSurfaceVariant
                ),
                maxLines = 2
            )
        }
    }
}
