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
    val color: Long = 0xFF4CAF50
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

            val week = WeekCalculator.currentWeek(sem.startDate, sem.totalWeeks)
            val dayIndex = now.dayOfWeek.value
            val dayLabel = when (dayIndex) {
                1 -> "周一"; 2 -> "周二"; 3 -> "周三"; 4 -> "周四"
                5 -> "周五"; 6 -> "周六"; 7 -> "周日"; else -> ""
            }

            val entities = entryPoint.courseDao().getCoursesByDayDirect(sem.id, dayIndex)
            val slots = entryPoint.timeSlotDao().getTimeSlotsBySchemeDirect(sem.schemeId)
            val slotMap = slots.associateBy { it.slotNumber }

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
                    val startSlot = slotMap[e.startSlot]
                    val endSlot = slotMap[e.endSlot] ?: startSlot
                    WidgetCourse(
                        id = e.id,
                        name = e.name,
                        classroom = e.classroom,
                        teacher = e.teacher,
                        startSlot = e.startSlot,
                        endSlot = e.endSlot,
                        startTime = startSlot?.startTime.orEmpty(),
                        endTime = endSlot?.endTime.orEmpty(),
                        color = e.color
                    )
                }

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
