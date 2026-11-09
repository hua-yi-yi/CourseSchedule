package com.chen.schedule.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ExperimentalGlanceApi
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
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
import androidx.glance.layout.fillMaxHeight
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

/** 4×1 今日课程小组件。 */
class TodayWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = TodayWidgetDataLoader.load(context)
        provideContent {
            ScheduleGlanceTheme {
                TodayWidgetContent(data)
            }
        }
    }
}

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = TodayWidget()
}

@OptIn(ExperimentalGlanceApi::class)
@Composable
internal fun TodayWidgetContent(data: WidgetData) {
    val style = currentWidgetVisualStyle()
    val titleColor = style.titleColor ?: GlanceTheme.colors.onSurface
    val subtitleColor = style.subtitleColor ?: GlanceTheme.colors.onSurfaceVariant
    val headerSurface = style.headerBg ?: WidgetStyle.HeaderSurface
    val emptySurface = WidgetStyle.emptySurface(style)
    val height = LocalSize.current.height
    val frameContent = GlanceModifier.fillMaxSize().padding(WidgetStyle.FrameInset)

    WidgetFrame(style) {
        if (height < 80.dp) {
            // The short widget gives the scrollable list the full available height.
            Row(modifier = frameContent, verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = GlanceModifier.width(44.dp).fillMaxHeight()
                        .cornerRadius(WidgetStyle.HeaderRadius)
                        .background(headerSurface)
                        .padding(2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    TodayDateTile(data, titleColor, subtitleColor, GlanceModifier.fillMaxWidth())
                }
                Spacer(modifier = GlanceModifier.width(3.dp))
                TodayCourses(
                    data.courses, emptySurface, titleColor, subtitleColor,
                    modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
                    maxLines = 1,
                    showFullDetails = height >= 54.dp
                )
            }
        } else {
            Column(modifier = frameContent) {
                Row(
                    modifier = GlanceModifier.fillMaxWidth()
                        .cornerRadius(WidgetStyle.HeaderRadius)
                        .background(headerSurface)
                        .padding(3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TodayDateTile(data, titleColor, subtitleColor, GlanceModifier.width(38.dp))
                    Spacer(modifier = GlanceModifier.width(5.dp))
                    Column(modifier = GlanceModifier.defaultWeight()) {
                        Text(data.semesterName, style = TextStyle(
                            fontSize = 11.sp, fontWeight = FontWeight.Bold, color = titleColor
                        ), maxLines = 1)
                        Text(
                            if (data.currentWeek > 0) "第 ${data.currentWeek} 周" else "今日课程",
                            style = TextStyle(fontSize = 9.sp, color = subtitleColor),
                            maxLines = 1
                        )
                    }
                    Box(
                        modifier = GlanceModifier.cornerRadius(WidgetStyle.EmptyRadius)
                            .background(style.itemCardBg ?: WidgetStyle.TimeSurface)
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(
                            if (data.courses.isEmpty()) "无课" else "共 ${data.courses.size} 门课",
                            style = TextStyle(fontSize = 9.sp, fontWeight = FontWeight.Medium, color = titleColor),
                            maxLines = 1
                        )
                    }
                }
                Spacer(modifier = GlanceModifier.height(3.dp))
                TodayCourses(
                    data.courses, emptySurface, titleColor, subtitleColor,
                    modifier = GlanceModifier.defaultWeight().fillMaxWidth(),
                    maxLines = 2,
                    showFullDetails = true
                )
            }
        }
    }
}

@Composable
private fun TodayDateTile(
    data: WidgetData,
    titleColor: ColorProvider,
    subtitleColor: ColorProvider,
    modifier: GlanceModifier
) {
    WidgetDateTile(
        label = data.dayOfWeekLabel.ifBlank { "今天" },
        date = data.dateLabel,
        highlighted = true,
        titleColor = titleColor,
        subtitleColor = subtitleColor,
        modifier = modifier
    )
}

@Composable
private fun TodayCourses(
    courses: List<WidgetCourse>,
    emptySurface: ColorProvider,
    titleColor: ColorProvider,
    subtitleColor: ColorProvider,
    modifier: GlanceModifier,
    maxLines: Int,
    showFullDetails: Boolean
) {
    if (courses.isEmpty()) {
        Box(
            modifier = modifier.cornerRadius(WidgetStyle.EmptyRadius)
                .background(emptySurface)
                .clickable(actionStartActivity<MainActivity>()),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "今日无课",
                style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = titleColor),
                maxLines = 1
            )
        }
    } else {
        LazyColumn(modifier = modifier) {
            items(courses) { course ->
                TodayCourseCard(course, titleColor, subtitleColor, maxLines, showFullDetails)
            }
        }
    }
}

@Composable
private fun TodayCourseCard(
    course: WidgetCourse,
    titleColor: ColorProvider,
    subtitleColor: ColorProvider,
    maxLines: Int,
    showFullDetails: Boolean
) {
    val time = if (course.startTime.isNotBlank() && course.endTime.isNotBlank()) {
        "${course.slotDisplay} · ${course.startTime}-${course.endTime}"
    } else {
        course.slotDisplay
    }
    val place = listOfNotNull(
        course.classroom.trim().takeIf { it.isNotEmpty() }?.let { "@$it" },
        course.teacher.trim().takeIf { it.isNotEmpty() }
    ).joinToString(" · ").ifEmpty { "未设地点" }

    Row(
        modifier = GlanceModifier.fillMaxWidth()
            .padding(vertical = 1.dp)
            .widgetCourseBackground(course.color)
            .clickable(actionStartActivity<MainActivity>())
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        WidgetCourseRail(course.color, GlanceModifier.height(if (showFullDetails) 34.dp else 22.dp))
        Spacer(modifier = GlanceModifier.width(4.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                widgetCourseTitle(course.name, course.extraCount),
                style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = titleColor),
                maxLines = maxLines
            )
            Text(
                if (showFullDetails) time else "$time · $place",
                style = TextStyle(fontSize = 9.sp, color = subtitleColor),
                maxLines = 1
            )
            if (showFullDetails) {
                Text(
                    place,
                    style = TextStyle(fontSize = 9.sp, fontWeight = FontWeight.Medium, color = titleColor),
                    maxLines = maxLines
                )
            }
        }
    }
}
