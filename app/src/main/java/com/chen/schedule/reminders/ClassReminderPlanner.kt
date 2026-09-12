package com.chen.schedule.reminders

import com.chen.schedule.domain.model.Course
import com.chen.schedule.domain.model.TimeSlot
import com.chen.schedule.util.ScheduleStatus
import java.time.Instant
import java.time.ZoneId

/**
 * 上课提醒计划器:根据今天的星期、当前教学周、当前作息与课程,
 * 计算「今天剩余的、需要提醒的上课时刻」(上课开始时间 - 提前量)。
 * 纯函数,便于单测。
 */
object ClassReminderPlanner {

    /** 一条提醒:什么时候响、响什么内容。 */
    data class ReminderPlan(
        val triggerAtMillis: Long,
        val courseName: String,
        val classroom: String,
        /** 例如 "第 3-4 节"。 */
        val slotRange: String,
        /** 上课开始时间,例如 "10:00"。 */
        val startTime: String
    )

    /**
     * @param nowMillis 当前时刻(早于等于它的提醒被过滤)
     * @param slots 当前学期作息方案的节次
     * @param courses 当前学期的全部课程(内部按今天星期与周次过滤)
     * @param currentWeek 当前教学周
     * @param todayDayOfWeek 今天是星期几(1=周一 .. 7=周日)
     * @param leadMinutes 提前量(分钟)
     * @param maxPlans 最多安排的提醒条数(闹钟 requestCode 区间上限)
     */
    fun planForToday(
        nowMillis: Long,
        slots: List<TimeSlot>,
        courses: List<Course>,
        currentWeek: Int,
        todayDayOfWeek: Int,
        leadMinutes: Int,
        zone: ZoneId = ZoneId.systemDefault(),
        maxPlans: Int = 64
    ): List<ReminderPlan> {
        if (leadMinutes <= 0) return emptyList()
        val slotByNumber = slots.associateBy { it.slotNumber }
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        return courses.asSequence()
            .filter { it.dayOfWeek == todayDayOfWeek }
            .filter { it.appliesToWeek(currentWeek) }
            .mapNotNull { course ->
                val slot = slotByNumber[course.startSlot] ?: return@mapNotNull null
                val start = ScheduleStatus.parseTime(slot.startTime) ?: return@mapNotNull null
                val triggerTime = start.minusMinutes(leadMinutes.toLong())
                // 提前量跨过零点(如 00:10 的课提前 30 分钟)无法在本日表达,跳过
                if (triggerTime.isAfter(start)) return@mapNotNull null
                val triggerMillis = today.atTime(triggerTime).atZone(zone).toInstant().toEpochMilli()
                if (triggerMillis <= nowMillis) return@mapNotNull null
                ReminderPlan(
                    triggerAtMillis = triggerMillis,
                    courseName = course.name,
                    classroom = course.classroom,
                    slotRange = if (course.endSlot > course.startSlot) {
                        "第 ${course.startSlot}-${course.endSlot} 节"
                    } else {
                        "第 ${course.startSlot} 节"
                    },
                    startTime = slot.startTime
                )
            }
            .sortedBy { it.triggerAtMillis }
            .take(maxPlans)
            .toList()
    }
}
