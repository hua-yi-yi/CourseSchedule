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
import androidx.glance.appwidget.updateAll
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
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
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.layout.RowScope
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.chen.schedule.data.local.entity.CourseEntity
import com.chen.schedule.data.local.entity.TimeSlotEntity
import com.chen.schedule.MainActivity
import com.chen.schedule.di.DatabaseEntryPoint
import com.chen.schedule.domain.model.Course
import com.chen.schedule.domain.model.TimeSlot
import com.chen.schedule.domain.model.WeekType
import com.chen.schedule.util.WeekCalculator
import dagger.hilt.android.EntryPointAccessors

/** 周课表中一行节次的数据 */
data class SlotRowData(
    val slotNumber: Int,
    val startTime: String = "",
    val endTime: String = "",
    val cells: List<WeekGridBuilder.Cell?> = emptyList()
)

/** 4×4 周课表大组件的数据 */
data class WeekWidgetData(
    val semesterName: String,
    val currentWeek: Int,
    val slotCount: Int,
    val grid: List<List<WeekGridBuilder.Cell?>> = emptyList(),
    val todayDayOfWeek: Int = 1,
    val rows: List<SlotRowData> = emptyList()
)

private val DAY_LABELS = listOf("一", "二", "三", "四", "五", "六", "日")

/**
 * 4×4 周课表大组件:展示当前周的完整课程网格(节次 × 周一~周日)，包含时间与地点。
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
        return runCatching {
            val entryPoint = EntryPointAccessors.fromApplication(
                context.applicationContext,
                DatabaseEntryPoint::class.java
            )
            val sem = entryPoint.semesterDao().getCurrentSemester()
            if (sem == null) {
                return@runCatching emptyWidgetData("未设置学期", 0)
            }

            val week = WeekCalculator.currentWeek(sem.startDate, sem.totalWeeks)
            val entities = entryPoint.courseDao().getCoursesBySemesterDirect(sem.id)
            val courses = entities.map { it.toDomain() }
            val slotEntities = entryPoint.timeSlotDao().getTimeSlotsBySchemeDirect(sem.schemeId)
            val configuredSlots = slotEntities.map { it.toDomain() }
            // 行数必须有上限:异常数据(如导入的节次号过大)曾把 4×4 组件撑成几十行碎格子
            val slotCount = maxOf(
                WeekGridBuilder.MIN_SLOTS,
                configuredSlots.maxOfOrNull { it.slotNumber } ?: 0,
                courses.maxOfOrNull { it.endSlot } ?: 0
            ).coerceAtMost(WeekGridBuilder.MAX_SLOTS)

            val grid = WeekGridBuilder.build(courses, week, slotCount, configuredSlots)
            val todayDayOfWeek = java.time.LocalDate.now().dayOfWeek.value

            val rows = (0 until slotCount).map { slotIdx ->
                val slotNum = slotIdx + 1
                val ts = configuredSlots.find { it.slotNumber == slotNum }
                SlotRowData(
                    slotNumber = slotNum,
                    startTime = ts?.startTime ?: "",
                    endTime = ts?.endTime ?: "",
                    cells = grid.getOrNull(slotIdx) ?: List(7) { null }
                )
            }

            WeekWidgetData(
                semesterName = sem.name,
                currentWeek = week,
                slotCount = slotCount,
                grid = grid,
                todayDayOfWeek = todayDayOfWeek,
                rows = rows
            )
        }.getOrElse {
            emptyWidgetData("周课表", 0)
        }
    }

    private fun emptyWidgetData(semesterName: String, week: Int): WeekWidgetData {
        val grid = List(WeekGridBuilder.MIN_SLOTS) { List(7) { null } }
        val todayDayOfWeek = java.time.LocalDate.now().dayOfWeek.value
        val rows = (0 until WeekGridBuilder.MIN_SLOTS).map { slotIdx ->
            SlotRowData(
                slotNumber = slotIdx + 1,
                startTime = "",
                endTime = "",
                cells = grid[slotIdx]
            )
        }
        return WeekWidgetData(
            semesterName = semesterName,
            currentWeek = week,
            slotCount = WeekGridBuilder.MIN_SLOTS,
            grid = grid,
            todayDayOfWeek = todayDayOfWeek,
            rows = rows
        )
    }

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

    private fun TimeSlotEntity.toDomain(): TimeSlot = TimeSlot(
        id = id,
        slotNumber = slotNumber,
        startTime = startTime,
        endTime = endTime,
        name = name,
        season = season,
        schemeId = schemeId
    )
}

@OptIn(ExperimentalGlanceApi::class)
@Composable
private fun WeekWidgetContent(data: WeekWidgetData) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .background(GlanceTheme.colors.surface)
            .cornerRadius(18.dp)
            .padding(12.dp)
            .clickable(actionStartActivity<MainActivity>())
    ) {
        // 顶部信息区：与其他小组件完全对齐（左侧主标题+副标题，右侧整周视图角标）
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = "周课表",
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
                text = "整周视图",
                style = TextStyle(
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = GlanceTheme.colors.onSurfaceVariant
                )
            )
        }

        Spacer(modifier = GlanceModifier.height(6.dp))

        // 星期表头（左侧节次列头 + 周一至周日，当日高亮突出）
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = GlanceModifier
                    .width(24.dp)
                    .padding(horizontal = 1.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "节次",
                    style = TextStyle(
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = GlanceTheme.colors.onSurfaceVariant
                    ),
                    maxLines = 1
                )
            }

            repeat(7) { index ->
                val isToday = (index + 1) == data.todayDayOfWeek
                Box(
                    modifier = GlanceModifier
                        .defaultWeight()
                        .padding(horizontal = 1.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = DAY_LABELS[index],
                        style = TextStyle(
                            fontSize = if (isToday) 9.5.sp else 9.sp,
                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium,
                            color = if (isToday) GlanceTheme.colors.primary else GlanceTheme.colors.onSurfaceVariant
                        ),
                        maxLines = 1
                    )
                }
            }
        }

        Spacer(modifier = GlanceModifier.height(3.dp))

        // 课表网格: 每行一个节次，包含左侧节次时段与右侧 7 天课程单元格（支持自适应平滑滚动）
        LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
            items(data.rows, itemId = { it.slotNumber.toLong() }) { row ->
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SlotTimeCell(row.slotNumber, row.startTime)
                    row.cells.forEach { cell ->
                        WeekCell(cell)
                    }
                }
            }
        }
    }
}

@Composable
private fun SlotTimeCell(slotNumber: Int, startTime: String) {
    Box(
        modifier = GlanceModifier
            .width(24.dp)
            .height(26.dp)
            .padding(1.dp)
            .cornerRadius(3.dp)
            .background(GlanceTheme.colors.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$slotNumber",
                style = TextStyle(
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = GlanceTheme.colors.onSurface
                ),
                maxLines = 1
            )
            if (startTime.isNotBlank()) {
                Text(
                    text = startTime,
                    style = TextStyle(
                        fontSize = 6.5.sp,
                        color = GlanceTheme.colors.onSurfaceVariant
                    ),
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun RowScope.WeekCell(cell: WeekGridBuilder.Cell?) {
    Box(
        modifier = GlanceModifier
            .defaultWeight()
            .height(26.dp)
            .padding(1.dp)
            .cornerRadius(3.dp)
            .background(
                if (cell != null) ColorProvider(Color(cell.color))
                else GlanceTheme.colors.surfaceVariant
            ),
        contentAlignment = Alignment.Center
    ) {
        if (cell != null) {
            val textColor = ColorProvider(Color(WeekGridBuilder.textColorFor(cell.color)))
            val (mainText, subText) = WeekGridBuilder.formatCellText(cell)

            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .padding(horizontal = 1.dp, vertical = 0.5.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = mainText,
                    style = TextStyle(
                        fontSize = 7.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    ),
                    maxLines = 1
                )
                if (subText.isNotBlank()) {
                    Text(
                        text = subText,
                        style = TextStyle(
                            fontSize = 6.5.sp,
                            color = textColor
                        ),
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/** 周课表组件接收器 */
class WeekWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = WeekWidget()
}

/** 数据变更后同时刷新今日课程(4×1)、今日看板(3×3)与周课表(4×4)组件 */
object WidgetUpdater {
    suspend fun refreshAll(context: Context) {
        runCatching {
            TodayWidget().updateAll(context)
            Today3x3Widget().updateAll(context)
            WeekWidget().updateAll(context)
        }
        // 课表数据变化后同步重排上课提醒(设置页/导入/编辑等所有写路径都会走到这里)
        com.chen.schedule.reminders.ClassReminderManager.rescheduleAsync(context)
    }
}
