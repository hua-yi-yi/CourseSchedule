package com.chen.schedule.widget

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ExperimentalGlanceApi
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
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
 * 与主页及其他小组件保持一致的视觉规范（全局统一圆角、晴空蓝主题配色、课程柔和彩色底卡与胶囊徽标）。
 */
class TodayWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = TodayWidgetDataLoader.load(context)
        provideContent {
            ScheduleGlanceTheme {
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
    val context = LocalContext.current
    val isDarkTheme = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
        Configuration.UI_MODE_NIGHT_YES

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .background(GlanceTheme.colors.surface)
            .cornerRadius(16.dp)
            .padding(12.dp)
            .clickable(actionStartActivity<MainActivity>())
    ) {
        // 顶部信息区：日期·星期，学期·周次，右侧课程数胶囊徽标
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .clickable(actionStartActivity<MainActivity>()),
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

            // 右侧圆角胶囊徽标 (与主页 StatusBadge 视觉对齐)
            Box(
                modifier = GlanceModifier
                    .cornerRadius(10.dp)
                    .background(
                        if (data.courses.isNotEmpty()) GlanceTheme.colors.primaryContainer
                        else GlanceTheme.colors.surfaceVariant
                    )
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (data.courses.isNotEmpty()) "共 ${data.courses.size} 门课" else "无课",
                    style = TextStyle(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (data.courses.isNotEmpty()) GlanceTheme.colors.onPrimaryContainer
                        else GlanceTheme.colors.onSurfaceVariant
                    )
                )
            }
        }

        Spacer(modifier = GlanceModifier.height(8.dp))

        if (data.courses.isEmpty()) {
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .padding(top = 8.dp)
                    .cornerRadius(12.dp)
                    .background(GlanceTheme.colors.surfaceVariant)
                    .clickable(actionStartActivity<MainActivity>())
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "今日无课",
                        style = TextStyle(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = GlanceTheme.colors.onSurface
                        )
                    )
                    Spacer(modifier = GlanceModifier.height(3.dp))
                    Text(
                        text = "享受属于自己的自由时间吧",
                        style = TextStyle(
                            fontSize = 11.5.sp,
                            color = GlanceTheme.colors.onSurfaceVariant
                        )
                    )
                }
            }
        } else {
            LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                items(data.courses) { course ->
                    TodayCourseCard(course, isDarkTheme)
                }
            }
        }
    }
}

@Composable
private fun TodayCourseCard(course: WidgetCourse, isDarkTheme: Boolean) {
    val courseBackground = WeekGridBuilder.pastelColorFor(course.color, isDark = isDarkTheme)
    val textColor = WeekGridBuilder.textColorFor(courseBackground)

    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(vertical = 3.5.dp)
            .cornerRadius(12.dp)
            .background(ColorProvider(Color(courseBackground)))
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .clickable(actionStartActivity<MainActivity>()),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 左侧课程微条（圆角 2dp，与主页 DayView / WeekView 保持一致）
        Box(
            modifier = GlanceModifier
                .width(3.5.dp)
                .height(44.dp)
                .cornerRadius(2.dp)
                .background(ColorProvider(Color(course.color)))
        ) {}

        Spacer(modifier = GlanceModifier.width(9.dp))

        Column(modifier = GlanceModifier.defaultWeight()) {
            // 课程名称：完整显示，主文本粗体
            Text(
                text = course.name,
                style = TextStyle(
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = ColorProvider(Color(textColor))
                ),
                maxLines = 1
            )

            Spacer(modifier = GlanceModifier.height(2.5.dp))

            // 节次与时间：与主页风格对齐（如 第1-2节 · 08:00-09:40）
            val timeText = if (course.startTime.isNotBlank() && course.endTime.isNotBlank()) {
                "${course.slotDisplay} · ${course.startTime}-${course.endTime}"
            } else {
                course.slotDisplay
            }
            Text(
                text = timeText,
                style = TextStyle(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = ColorProvider(Color(textColor).copy(alpha = 0.78f))
                ),
                maxLines = 1
            )

            // 上课地点与教师：主页风格以 @地点 开头
            val placeText = if (course.classroom.isNotBlank()) {
                if (course.teacher.isNotBlank()) "@${course.classroom.trim()} · ${course.teacher.trim()}"
                else "@${course.classroom.trim()}"
            } else if (course.teacher.isNotBlank()) {
                course.teacher.trim()
            } else {
                "未设地点"
            }

            Spacer(modifier = GlanceModifier.height(1.5.dp))

            Text(
                text = placeText,
                style = TextStyle(
                    fontSize = 11.sp,
                    color = ColorProvider(Color(textColor).copy(alpha = 0.88f))
                ),
                maxLines = 1
            )
        }
    }
}
