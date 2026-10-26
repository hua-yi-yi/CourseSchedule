package com.chen.schedule.widget

import android.content.Context
import com.chen.schedule.di.DatabaseEntryPoint
import com.chen.schedule.util.WeekCalculator
import dagger.hilt.android.EntryPointAccessors
import java.time.LocalDate

data class WidgetCourse(
    val id: Long = 0,
    val name: String,
    val classroom: String,
    val teacher: String = "",
    val startSlot: Int,
    val endSlot: Int,
    val startTime: String = "",
    val endTime: String = "",
    val color: Long = 0xFF4CAF50,
    val extraCount: Int = 0
) {
    /** 完整起止时间展示，例如 "08:00 - 09:40" 或在未设置具体时间时展示 "第1-2节" */
    val timeDisplay: String
        get() = if (startTime.isNotBlank() && endTime.isNotBlank()) {
            "$startTime - $endTime"
        } else {
            "第${startSlot}-${endSlot}节"
        }

    /** 完整节次标注，例如 "第1-2节" */
    val slotDisplay: String
        get() = "第${startSlot}-${endSlot}节"

    /** 完整地点与教师信息，长教室名称完整展示，例如 "公教楼B204 · 张老师" 或 "公教楼B204" */
    val locationAndTeacherDisplay: String
        get() {
            val place = classroom.ifBlank { "未设地点" }
            return if (teacher.isNotBlank()) "$place · $teacher" else place
        }
}

data class WidgetData(
    val semesterName: String,
    val currentWeek: Int,
    val dayOfWeekLabel: String,
    val dateLabel: String,
    val courses: List<WidgetCourse>
)

object TodayWidgetDataLoader {

    suspend fun load(context: Context): WidgetData {
        return runCatching {
            val entryPoint = EntryPointAccessors.fromApplication(
                context.applicationContext,
                DatabaseEntryPoint::class.java
            )
            val sem = entryPoint.semesterDao().getCurrentSemester()
            val now = LocalDate.now()
            val dateLabel = "${now.monthValue}月${now.dayOfMonth}日"

            if (sem == null) {
                return@runCatching WidgetData("未设置学期", 0, "", dateLabel, emptyList())
            }

            val week = WeekCalculator.activeWeek(sem.startDate, sem.totalWeeks)
                ?: return@runCatching WidgetData(sem.name, 0, "学期外", dateLabel, emptyList())
            val dayIndex = now.dayOfWeek.value
            val dayLabel = when (dayIndex) {
                1 -> "周一"; 2 -> "周二"; 3 -> "周三"; 4 -> "周四"
                5 -> "周五"; 6 -> "周六"; 7 -> "周日"; else -> ""
            }

            val entities = entryPoint.courseDao().getCoursesByDayDirect(sem.id, dayIndex)
            val slots = entryPoint.timeSlotDao().getTimeSlotsBySchemeDirect(sem.schemeId)
            val slotMap = slots.associateBy { it.slotNumber }

            val domainCourses = entities.map { it.toDomain() }
            val filtered = domainCourses.filter { it.appliesToWeek(week) }
            val preferredIds = com.chen.schedule.util.CourseConflictDetector.loadPreferredCourseIds(context)
            val clusters = com.chen.schedule.util.CourseConflictDetector.resolveClusters(filtered, preferredIds)

            val courses = clusters.map { cluster ->
                val c = cluster.primaryCourse
                val startSlot = slotMap[c.startSlot]
                val endSlot = slotMap[c.endSlot] ?: startSlot
                WidgetCourse(
                    id = c.id,
                    name = c.name,
                    classroom = c.classroom,
                    teacher = c.teacher,
                    startSlot = c.startSlot,
                    endSlot = c.endSlot,
                    startTime = startSlot?.startTime.orEmpty(),
                    endTime = endSlot?.endTime.orEmpty(),
                    color = c.color,
                    extraCount = cluster.allCourses.size - 1
                )
            }.sortedBy { it.startSlot }

            WidgetData(
                semesterName = sem.name,
                currentWeek = week,
                dayOfWeekLabel = dayLabel,
                dateLabel = dateLabel,
                courses = courses
            )
        }.getOrElse {
            val now = LocalDate.now()
            WidgetData("今日课程", 0, "", "${now.monthValue}月${now.dayOfMonth}日", emptyList())
        }
    }
}

private fun com.chen.schedule.data.local.entity.CourseEntity.toDomain() = com.chen.schedule.domain.model.Course(
    id = id,
    name = name,
    teacher = teacher,
    classroom = classroom,
    dayOfWeek = dayOfWeek,
    startSlot = startSlot,
    endSlot = endSlot,
    startWeek = startWeek,
    endWeek = endWeek,
    weekType = when (weekType) {
        "odd" -> com.chen.schedule.domain.model.WeekType.ODD
        "even" -> com.chen.schedule.domain.model.WeekType.EVEN
        else -> com.chen.schedule.domain.model.WeekType.ALL
    },
    color = color,
    semesterId = semesterId,
    note = note,
    importSource = importSource
)

