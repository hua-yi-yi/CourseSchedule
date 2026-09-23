package com.chen.schedule.widget

import android.content.Context
import android.content.res.Configuration
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.graphics.Typeface
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.appwidget.SizeMode
import kotlin.math.ceil
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
import androidx.glance.layout.fillMaxHeight
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
import java.time.LocalDate

/** 星期表头的一天数据 */
data class DayHeaderData(
    val dayOfWeek: Int,
    val label: String,
    val dateDisplay: String = "",
    val isToday: Boolean = false
)

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
    val rows: List<SlotRowData> = emptyList(),
    val dayHeaders: List<DayHeaderData> = emptyList()
)

private val DAY_LABELS = listOf("一", "二", "三", "四", "五", "六", "日")

/** 课表背景使用分层的中性色，让课程卡片保持视觉焦点。 */
private data class WeekWidgetSurfaces(
    val shell: ColorProvider,
    val grid: ColorProvider,
    val time: ColorProvider,
    val empty: ColorProvider
) {
    companion object {
        fun forTheme(isDark: Boolean): WeekWidgetSurfaces = if (isDark) {
            WeekWidgetSurfaces(
                shell = ColorProvider(Color(0xFF121B2A)),
                grid = ColorProvider(Color(0xFF192638)),
                time = ColorProvider(Color(0xFF2A3B52)),
                empty = ColorProvider(Color(0xFF223147))
            )
        } else {
            WeekWidgetSurfaces(
                shell = ColorProvider(Color(0xFFEAF1FB)),
                grid = ColorProvider(Color(0xFFFBFCFF)),
                time = ColorProvider(Color(0xFFE4EDF9)),
                empty = ColorProvider(Color(0xFFF0F4FA))
            )
        }
    }
}

/**
 * 4×4 周课表大组件:展示当前周的完整课程网格(节次 × 周一~周日)，包含时间与地点。
 * 视觉语言与主页 WeekView 保持高度一致（柔和卡片背景、左侧专属调色条、双行信息、表头日期与今日高亮）。
 */
class WeekWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

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
            val today = LocalDate.now()
            val todayDayOfWeek = today.dayOfWeek.value

            val semesterMonday = WeekCalculator.semesterMonday(sem.startDate)
            val dayHeaders = (1..7).map { dayIndex ->
                val date = semesterMonday.plusDays(((week - 1) * 7 + (dayIndex - 1)).toLong())
                DayHeaderData(
                    dayOfWeek = dayIndex,
                    label = DAY_LABELS[dayIndex - 1],
                    dateDisplay = "${date.monthValue}/${date.dayOfMonth}",
                    isToday = date == today
                )
            }

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
                rows = rows,
                dayHeaders = dayHeaders
            )
        }.getOrElse {
            emptyWidgetData("周课表", 0)
        }
    }

    private fun emptyWidgetData(semesterName: String, week: Int): WeekWidgetData {
        val grid = List(WeekGridBuilder.MIN_SLOTS) { List(7) { null } }
        val today = LocalDate.now()
        val todayDayOfWeek = today.dayOfWeek.value
        val dayHeaders = (1..7).map { dayIndex ->
            DayHeaderData(
                dayOfWeek = dayIndex,
                label = DAY_LABELS[dayIndex - 1],
                dateDisplay = "",
                isToday = dayIndex == todayDayOfWeek
            )
        }
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
            rows = rows,
            dayHeaders = dayHeaders
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
    val context = LocalContext.current
    val isDarkTheme = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
        Configuration.UI_MODE_NIGHT_YES
    val surfaces = WeekWidgetSurfaces.forTheme(isDarkTheme)
    val widgetWidth = LocalSize.current.width.value
    val columns = (0..6).map { WeekGridBuilder.blocks(data.grid, it) }
    // 外边距 16dp + 网格内边距 4dp + 时间列 26dp，课程格再留出边距和色条。
    val textWidth = ((widgetWidth - 20f - 26f) / 7f - 10f).coerceAtLeast(1f)
    val heights = WeekGridBuilder.rowHeights(columns, data.slotCount, 38f) { cell ->
        courseTextHeight(context, cell, textWidth)
    }
    val bands = WeekGridBuilder.bands(columns, data.slotCount)
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .background(surfaces.shell)
            .cornerRadius(18.dp)
            .padding(8.dp)
            .clickable(actionStartActivity<MainActivity>())
    ) {
        // 表头与可滚动课表共用一整块底板，今日仍用主题主色突出。
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(surfaces.grid)
                .cornerRadius(10.dp)
                .padding(2.dp)
                .clickable(actionStartActivity<MainActivity>())
        ) {
            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .padding(vertical = 2.5.dp, horizontal = 2.dp)
                    .clickable(actionStartActivity<MainActivity>()),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = GlanceModifier
                        .width(26.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "节",
                        style = TextStyle(
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = GlanceTheme.colors.onSurfaceVariant
                        ),
                        maxLines = 1
                    )
                }

                data.dayHeaders.forEach { header ->
                    Box(
                        modifier = GlanceModifier
                            .defaultWeight()
                            .padding(horizontal = 1.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            modifier = GlanceModifier
                                .fillMaxWidth()
                                .cornerRadius(6.dp)
                                .background(
                                    if (header.isToday) GlanceTheme.colors.primary
                                    else ColorProvider(Color.Transparent)
                                )
                                .padding(vertical = 2.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = header.label,
                                style = TextStyle(
                                    fontSize = 10.sp,
                                    fontWeight = if (header.isToday) FontWeight.Bold else FontWeight.Medium,
                                    color = if (header.isToday) GlanceTheme.colors.onPrimary else GlanceTheme.colors.onSurface
                                ),
                                maxLines = 1
                            )
                            if (header.dateDisplay.isNotBlank()) {
                                Text(
                                    text = header.dateDisplay,
                                    style = TextStyle(
                                        fontSize = 7.5.sp,
                                        fontWeight = if (header.isToday) FontWeight.Medium else FontWeight.Normal,
                                        color = if (header.isToday) GlanceTheme.colors.onPrimary else GlanceTheme.colors.onSurfaceVariant
                                    ),
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }

            // 按安全分段滚动，每列独立绘制跨节课程块；不会在连堂课中间画分隔线。
            LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                items(bands, itemId = { it.first.toLong() }) { band ->
                    Row(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .clickable(actionStartActivity<MainActivity>())
                    ) {
                        Column(modifier = GlanceModifier.width(26.dp)) {
                            // Glance 的单个 Column 子节点数量有限，长跨节分成小组。
                            band.toList().chunked(6).forEach { chunk ->
                                Column {
                                    chunk.forEach { rowIndex ->
                                        val row = data.rows[rowIndex]
                                        SlotTimeCell(row.slotNumber, row.startTime, heights[rowIndex], surfaces.time)
                                    }
                                }
                            }
                        }
                        columns.forEach { blocks ->
                            Column(modifier = GlanceModifier.defaultWeight()) {
                                blocks.filter { it.startRow in band }.chunked(6).forEach { chunk ->
                                    Column(modifier = GlanceModifier.fillMaxWidth()) {
                                        chunk.forEach { block ->
                                            val height = (block.startRow until block.startRow + block.rowSpan)
                                                .sumOf { heights[it].toDouble() }.toFloat()
                                            WeekCell(block.cell, height, isDarkTheme, surfaces.empty)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SlotTimeCell(slotNumber: Int, startTime: String, height: Float, background: ColorProvider) {
    Box(
        modifier = GlanceModifier
            .width(26.dp)
            .height(height.dp)
            .padding(1.dp)
            .cornerRadius(8.dp)
            .background(background)
            .clickable(actionStartActivity<MainActivity>()),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$slotNumber",
                style = TextStyle(
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = GlanceTheme.colors.onSurfaceVariant
                ),
                maxLines = 1
            )
            if (startTime.isNotBlank()) {
                Text(
                    text = startTime,
                    style = TextStyle(
                        fontSize = 7.sp,
                        color = GlanceTheme.colors.onSurfaceVariant
                    ),
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun WeekCell(
    cell: WeekGridBuilder.Cell?,
    height: Float,
    isDarkTheme: Boolean,
    emptyBackground: ColorProvider
) {
    val courseBackground = cell?.let {
        WeekGridBuilder.pastelColorFor(it.color, isDark = isDarkTheme)
    }
    val courseTextColor = courseBackground?.let {
        Color(WeekGridBuilder.textColorFor(it))
    }
    Box(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(height.dp)
            .padding(1.dp)
            .cornerRadius(8.dp)
            .background(
                if (courseBackground != null) ColorProvider(Color(courseBackground))
                else emptyBackground
            )
            .clickable(actionStartActivity<MainActivity>()),
        contentAlignment = Alignment.Center
    ) {
        if (cell != null) {
            val mainText = if (cell.extraCount > 0) "${cell.name}+${cell.extraCount}" else cell.name
            val textColor = requireNotNull(courseTextColor)

            Row(
                modifier = GlanceModifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 左侧课程纯色微条 (2.5dp 宽度，与主页 CourseBlock 的左侧微条完全对应)
                Box(
                    modifier = GlanceModifier
                        .width(2.5.dp)
                        .fillMaxHeight()
                        .cornerRadius(1.dp)
                        .background(ColorProvider(Color(cell.color)))
                ) {}

                    Spacer(modifier = GlanceModifier.width(1.dp))

                // 课程内容文字: 主文本为 onSurface 粗体, 次文本为 onSurfaceVariant
                Column(
                    modifier = GlanceModifier
                        .defaultWeight()
                        .fillMaxHeight()
                        .padding(horizontal = 1.dp, vertical = 1.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = mainText,
                        style = TextStyle(
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = ColorProvider(textColor)
                        ),
                        maxLines = 2
                    )
                    if (cell.classroom.isNotBlank()) {
                        Spacer(modifier = GlanceModifier.height(3.dp))
                        Text(
                            text = cell.classroom.trim(),
                            style = TextStyle(
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = ColorProvider(textColor.copy(alpha = 0.86f))
                            ),
                            maxLines = Int.MAX_VALUE
                        )
                    }
                }
            }
        }
    }
}

/** 用与 RemoteViews 相同的系统文字排版估算高度，地点不截断、不缩成难读的小字。 */
private fun courseTextHeight(context: Context, cell: WeekGridBuilder.Cell, widthDp: Float): Float {
    val metrics = context.resources.displayMetrics
    fun measure(text: String, bold: Boolean, maxLines: Int): Float {
        val paint = TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            textSize = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, 9f, metrics)
            typeface = if (bold) Typeface.create("sans-serif", Typeface.BOLD)
                else Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint,
            (widthDp * metrics.density).toInt().coerceAtLeast(1))
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(true)
            .setMaxLines(maxLines)
            .build()
        return ceil(layout.height / metrics.density)
    }
    val title = if (cell.extraCount > 0) "${cell.name}+${cell.extraCount}" else cell.name
    val location = cell.classroom.trim()
    return measure(title, true, 2) +
        (if (location.isNotEmpty()) 3f + measure(location, false, Int.MAX_VALUE) else 0f) + 12f
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
