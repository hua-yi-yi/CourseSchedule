package com.chen.schedule.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.glance.ExperimentalGlanceApi
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.layout.RowScope
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.chen.schedule.data.local.entity.CourseEntity
import com.chen.schedule.di.DatabaseEntryPoint
import com.chen.schedule.domain.model.Course
import com.chen.schedule.domain.model.WeekType
import com.chen.schedule.util.WeekCalculator
import dagger.hilt.android.EntryPointAccessors

/** 4×4 周课表大组件的数据 */
data class WeekWidgetData(
    val semesterName: String,
    val currentWeek: Int,
    val slotCount: Int,
    val grid: List<List<WeekGridBuilder.Cell?>>
)

private val DAY_LABELS = listOf("一", "二", "三", "四", "五", "六", "日")

/**
 * 4×4 周课表大组件:展示当前周的完整课程网格(节次 × 周一~周日)。
 */
class WeekWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = loadWidgetData(context)
        provideContent {
            GlanceTheme {
                WeekWidgetContent(data)
            }
        }
    }

    private suspend fun loadWidgetData(context: Context): WeekWidgetData {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            DatabaseEntryPoint::class.java
        )
        val sem = entryPoint.semesterDao().getCurrentSemester()
        if (sem == null) {
            return WeekWidgetData("未设置学期", 0, WeekGridBuilder.MIN_SLOTS, emptyGrid())
        }

        val week = WeekCalculator.currentWeek(sem.startDate, sem.totalWeeks)
        val entities = entryPoint.courseDao().getCoursesBySemesterDirect(sem.id)
        val courses = entities.map { it.toDomain() }
        val configuredSlots = entryPoint.timeSlotDao().getTimeSlotsBySchemeDirect(sem.schemeId)
        val slotCount = maxOf(
            WeekGridBuilder.MIN_SLOTS,
            configuredSlots.maxOfOrNull { it.slotNumber } ?: 0,
            courses.maxOfOrNull { it.endSlot } ?: 0
        )
        return WeekWidgetData(
            semesterName = sem.name,
            currentWeek = week,
            slotCount = slotCount,
            grid = WeekGridBuilder.build(courses, week, slotCount)
        )
    }

    private fun emptyGrid(): List<List<WeekGridBuilder.Cell?>> =
        List(WeekGridBuilder.MAX_SLOTS) { List(7) { null } }

    private fun CourseEntity.toDomain(): Course = Course(
        id = id, name = name, teacher = teacher, classroom = classroom,
        dayOfWeek = dayOfWeek, startSlot = startSlot, endSlot = endSlot,
        startWeek = startWeek, endWeek = endWeek,
        weekType = when (weekType) {
            "odd" -> WeekType.ODD
            "even" -> WeekType.EVEN
            else -> WeekType.ALL
        },
        color = color, semesterId = semesterId, note = note
    )
}

@OptIn(ExperimentalGlanceApi::class)
@Composable
private fun WeekWidgetContent(data: WeekWidgetData) {
    Column(
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(GlanceTheme.colors.surface)
            .padding(10.dp)
    ) {
        // 标题:学期 · 第 N 周
        Text(
            text = "${data.semesterName} · 第${data.currentWeek}周",
            style = TextStyle(
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = GlanceTheme.colors.onSurface
            ),
            maxLines = 1
        )

        Spacer(modifier = GlanceModifier.height(4.dp))

        // 星期表头
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            repeat(7) { index ->
                Text(
                    text = DAY_LABELS[index],
                    style = TextStyle(
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = GlanceTheme.colors.primary
                    ),
                    modifier = GlanceModifier.defaultWeight(),
                    maxLines = 1
                )
            }
        }

        Spacer(modifier = GlanceModifier.height(2.dp))

        // 课表网格:每行一个节次
        for (slot in 0 until data.slotCount) {
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                for (day in 0 until 7) {
                    val cell = data.grid.getOrNull(slot)?.getOrNull(day)
                    WeekCell(cell)
                }
            }
        }
    }
}

@Composable
private fun RowScope.WeekCell(cell: WeekGridBuilder.Cell?) {
    Box(
        modifier = GlanceModifier
            .defaultWeight()
            .height(22.dp)
            .padding(1.dp)
            .background(
                if (cell != null) ColorProvider(Color(cell.color))
                else GlanceTheme.colors.surfaceVariant
            )
    ) {
        if (cell != null) {
            Text(
                text = if (cell.extraCount > 0) "${cell.name}+${cell.extraCount}" else cell.name,
                style = TextStyle(
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    color = ColorProvider(Color(0xFFFFFFFF))
                ),
                modifier = GlanceModifier.padding(2.dp),
                maxLines = 1
            )
        }
    }
}

/** 周课表组件接收器 */
class WeekWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = WeekWidget()
}

/** 数据变更后同时刷新今日课程与周课表组件 */
object WidgetUpdater {
    suspend fun refreshAll(context: Context) {
        runCatching {
            TodayWidget().updateAll(context)
            WeekWidget().updateAll(context)
        }
    }
}
