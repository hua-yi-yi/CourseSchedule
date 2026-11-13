package com.chen.schedule.widget

import android.content.Context
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.ExperimentalGlanceApi
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.currentState
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
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.unit.ColorProvider
import com.chen.schedule.MainActivity
import com.chen.schedule.data.local.entity.CourseEntity
import com.chen.schedule.data.local.entity.TimeSlotEntity
import com.chen.schedule.di.DatabaseEntryPoint
import com.chen.schedule.domain.model.Course
import com.chen.schedule.domain.model.TimeSlot
import com.chen.schedule.domain.model.WeekType
import com.chen.schedule.util.WeekCalculator
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CancellationException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.ceil

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
private val WEEK_DATA_REVISION = longPreferencesKey("week_data_revision")

/**
 * 4×4 周课表大组件:展示当前周的完整课程网格(节次 × 周一~周日)，包含时间与地点。
 * 视觉语言与主页 WeekView 保持高度一致（全局统一圆角、柔和卡片背景、左侧专属调色条、双行信息、表头日期与今日高亮）。
 */
class WeekWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact
    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val initialData = loadWidgetData(context)
        provideContent {
            val revision = currentState(WEEK_DATA_REVISION)
            var data by remember { mutableStateOf(initialData) }
            // Glance can reuse a running composition when an existing widget is updated.
            LaunchedEffect(revision) { data = loadWidgetData(context) }
            ScheduleGlanceTheme {
                WeekWidgetContent(data)
            }
        }
    }

    internal suspend fun refreshAll(context: Context) {
        val ids = GlanceAppWidgetManager(context).getGlanceIds(WeekWidget::class.java)
        for (id in ids) {
            updateAppWidgetState(context, id) { preferences ->
                preferences[WEEK_DATA_REVISION] = (preferences[WEEK_DATA_REVISION] ?: 0L) + 1
            }
            update(context, id)
        }
    }

    private suspend fun loadWidgetData(context: Context): WeekWidgetData {
        val zone = ZoneId.systemDefault()
        val now = System.currentTimeMillis()
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        return try {
            val entryPoint = EntryPointAccessors.fromApplication(
                context.applicationContext,
                DatabaseEntryPoint::class.java
            )
            val sem = entryPoint.semesterDao().getCurrentSemester()
            if (sem == null) {
                return emptyWidgetData("未设置学期", today)
            }

            val week = WeekCalculator.activeWeek(sem.startDate, sem.totalWeeks, now, zone)
                ?: return emptyWidgetData("${sem.name} · 学期外", today)
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

            val preferredIds = com.chen.schedule.util.CourseConflictDetector.loadPreferredCourseIds(context)
            val grid = WeekGridBuilder.build(courses, week, slotCount, configuredSlots, preferredIds)
            val todayDayOfWeek = today.dayOfWeek.value

            val semesterMonday = WeekCalculator.semesterMonday(sem.startDate, zone)
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
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("WeekWidget", "Could not load the weekly timetable", e)
            emptyWidgetData("周课表", today)
        }
    }

    private fun emptyWidgetData(semesterName: String, today: LocalDate): WeekWidgetData {
        val grid = List(WeekGridBuilder.MIN_SLOTS) { List(7) { null } }
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
            currentWeek = 0,
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
    val visualStyle = currentWidgetVisualStyle()
    val headerBg = visualStyle.headerBg ?: WidgetStyle.HeaderSurface
    val timeBg = visualStyle.itemCardBg ?: WidgetStyle.TimeSurface
    val emptyBg = WidgetStyle.emptySurface(visualStyle)

    val titleColor = visualStyle.titleColor ?: GlanceTheme.colors.onSurface
    val subtitleColor = visualStyle.subtitleColor ?: GlanceTheme.colors.onSurfaceVariant

    val widgetWidth = LocalSize.current.width.value
    val widgetHeight = LocalSize.current.height.value
    val columns = (0..6).map { WeekGridBuilder.blocks(data.grid, it) }
    // 背景铺满组件；除时间列与左右间距外，课程格只需为文字和色条预留宽度。
    val textWidth = ((widgetWidth - 34f) / 7f - 8f).coerceAtLeast(1f)
    // 节次行至少填满可见高度，较长的课程地点仍可把对应行撑高并滚动。
    val headerHeight = if (data.dayHeaders.any { it.dateDisplay.isNotBlank() }) 36f else 26f
    val minimumRowHeight = ((widgetHeight - headerHeight) / data.slotCount).coerceAtLeast(38f)
    val heights = WeekGridBuilder.rowHeights(columns, data.slotCount, minimumRowHeight) { cell ->
        courseTextHeight(context, cell, textWidth)
    }
    val bands = WeekGridBuilder.bands(columns, data.slotCount)

    WidgetFrame(visualStyle) {
        Column(modifier = GlanceModifier.fillMaxSize()) {
            // 表头与可滚动课表直接绘制在铺满组件的圆角底板上。
            // 表头增加圆角容器 (8dp)，与主页 WeekView 星期栏视觉完全对齐
            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .padding(
                        start = WidgetStyle.FrameInset,
                        end = WidgetStyle.FrameInset,
                        top = WidgetStyle.FrameInset,
                        bottom = 3.dp
                    )
                    .cornerRadius(WidgetStyle.HeaderRadius)
                    .background(headerBg)
                    .padding(vertical = 2.5.dp)
                    .clickable(actionStartActivity<MainActivity>()),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = GlanceModifier.width(26.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "节",
                        style = TextStyle(
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = subtitleColor
                        ),
                        maxLines = 1
                    )
                }

                data.dayHeaders.forEach { header ->
                    Box(
                        modifier = GlanceModifier
                            .defaultWeight()
                            .padding(horizontal = 0.5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        WidgetDateTile(
                            label = header.label,
                            date = header.dateDisplay,
                            highlighted = header.isToday,
                            titleColor = titleColor,
                            subtitleColor = subtitleColor,
                            modifier = GlanceModifier.fillMaxWidth()
                        )
                    }
                }
            }

            // 按安全分段滚动，每列独立绘制跨节圆角课程卡片；具有统一的呼吸感与边缘微距。
            LazyColumn(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .padding(
                        start = WidgetStyle.FrameInset,
                        end = WidgetStyle.FrameInset,
                        bottom = WidgetStyle.FrameInset
                    )
            ) {
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
                                        SlotTimeCell(
                                            slotNumber = row.slotNumber,
                                            startTime = row.startTime,
                                            height = heights[rowIndex],
                                            background = timeBg,
                                            titleColor = titleColor,
                                            subtitleColor = subtitleColor
                                        )
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
                                            WeekCell(block.cell, height, emptyBg, titleColor)
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
private fun SlotTimeCell(
    slotNumber: Int,
    startTime: String,
    height: Float,
    background: ColorProvider,
    titleColor: ColorProvider = GlanceTheme.colors.onSurface,
    subtitleColor: ColorProvider = GlanceTheme.colors.onSurfaceVariant
) {
    Box(
        modifier = GlanceModifier
            .width(26.dp)
            .height(height.dp)
            .padding(horizontal = 1.dp, vertical = 1.dp)
            .cornerRadius(6.dp)
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
                    color = titleColor
                ),
                maxLines = 1
            )
            if (startTime.isNotBlank()) {
                Text(
                    text = startTime,
                    style = TextStyle(
                        fontSize = 7.sp,
                        color = subtitleColor
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
    emptyBackground: ColorProvider,
    titleColor: ColorProvider
) {
    Box(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(height.dp)
            .clickable(actionStartActivity<MainActivity>()),
        contentAlignment = Alignment.Center
    ) {
        if (cell != null) {
            val mainText = widgetCourseTitle(cell.name, cell.extraCount)
            val textColor = titleColor

            Row(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .padding(horizontal = 1.dp, vertical = 1.dp)
                    .widgetCourseBackground(cell.color)
                    .padding(horizontal = 2.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                WidgetCourseRail(cell.color, GlanceModifier.fillMaxHeight())

                Spacer(modifier = GlanceModifier.width(1.5.dp))

                // 课程内容文字: 主文本为粗体, 地点加 @ 前缀 (与主页 "@${course.classroom}" 保持一致)
                Column(
                    modifier = GlanceModifier
                        .defaultWeight()
                        .fillMaxHeight()
                        .padding(horizontal = 0.5.dp, vertical = 0.5.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = mainText,
                        style = TextStyle(
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        ),
                        maxLines = 2
                    )
                    if (cell.classroom.isNotBlank()) {
                        Spacer(modifier = GlanceModifier.height(1.5.dp))
                        Text(
                            text = "@${cell.classroom.trim()}",
                            style = TextStyle(
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Medium,
                                color = textColor
                            ),
                            maxLines = Int.MAX_VALUE
                        )
                    }
                }
            }
        } else {
            // 空白网格单元: 圆角微底纹, 增强整体卡片化与呼吸感
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .padding(horizontal = 1.dp, vertical = 1.dp)
                    .cornerRadius(WidgetStyle.EmptyRadius)
                    .background(emptyBackground)
            ) {}
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
    val title = widgetCourseTitle(cell.name, cell.extraCount)
    val location = if (cell.classroom.isNotBlank()) "@${cell.classroom.trim()}" else ""
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
        try {
            WeekWidget().refreshAll(context)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("WeekWidget", "Could not refresh the weekly timetable", e)
        }
        runCatching {
            TodayWidget().updateAll(context)
            Today3x3Widget().updateAll(context)
        }
        // 课表数据变化后同步重排上课提醒(设置页/导入/编辑等所有写路径都会走到这里)
        com.chen.schedule.reminders.ClassReminderManager.rescheduleAsync(context)
        // 同步刷新胶囊灵动岛
        com.chen.schedule.island.CapsuleIslandManager.refresh(context)
    }
}
