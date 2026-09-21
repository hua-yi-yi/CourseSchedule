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

    /** 进行中课程信息模型。 */
    data class OngoingCourseInfo(
        val courseName: String,
        val classroom: String,
        val teacher: String,
        val slotRange: String,
        val startTime: String,
        val endTime: String,
        val startAtMillis: Long,
        val endAtMillis: Long
    )

    /** 正在上课的开始/结束触发事件。 */
    data class OngoingEvent(
        val triggerAtMillis: Long,
        val isStart: Boolean,
        val info: OngoingCourseInfo
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

    /**
     * 判断当前时刻是否有课程正在进行。
     * 若此时正好处于某门课的 [startTime, endTime) 之间，则返回该课程的进行中快照，否则返回 null。
     */
    fun findCurrentOngoingCourse(
        nowMillis: Long,
        slots: List<TimeSlot>,
        courses: List<Course>,
        currentWeek: Int,
        todayDayOfWeek: Int,
        zone: ZoneId = ZoneId.systemDefault()
    ): OngoingCourseInfo? {
        val slotByNumber = slots.associateBy { it.slotNumber }
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        return courses.asSequence()
            .filter { it.dayOfWeek == todayDayOfWeek }
            .filter { it.appliesToWeek(currentWeek) }
            .mapNotNull { course ->
                val startSlot = slotByNumber[course.startSlot] ?: return@mapNotNull null
                val endSlot = slotByNumber[course.endSlot] ?: startSlot
                val startTime = ScheduleStatus.parseTime(startSlot.startTime) ?: return@mapNotNull null
                val endTime = ScheduleStatus.parseTime(endSlot.endTime) ?: return@mapNotNull null
                val startMillis = today.atTime(startTime).atZone(zone).toInstant().toEpochMilli()
                val endMillis = today.atTime(endTime).atZone(zone).toInstant().toEpochMilli()
                if (nowMillis in startMillis until endMillis) {
                    OngoingCourseInfo(
                        courseName = course.name,
                        classroom = course.classroom,
                        teacher = course.teacher,
                        slotRange = if (course.endSlot > course.startSlot) {
                            "第 ${course.startSlot}-${course.endSlot} 节"
                        } else {
                            "第 ${course.startSlot} 节"
                        },
                        startTime = startSlot.startTime,
                        endTime = endSlot.endTime,
                        startAtMillis = startMillis,
                        endAtMillis = endMillis
                    )
                } else {
                    null
                }
            }
            .firstOrNull()
    }

    /**
     * 计算今天后续所有课程的开始与下课事件点（用于自动刷新/移除正在上课看板）。
     */
    fun planOngoingEvents(
        nowMillis: Long,
        slots: List<TimeSlot>,
        courses: List<Course>,
        currentWeek: Int,
        todayDayOfWeek: Int,
        zone: ZoneId = ZoneId.systemDefault(),
        maxEvents: Int = 64
    ): List<OngoingEvent> {
        val slotByNumber = slots.associateBy { it.slotNumber }
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        val events = mutableListOf<OngoingEvent>()
        for (course in courses) {
            if (course.dayOfWeek != todayDayOfWeek || !course.appliesToWeek(currentWeek)) continue
            val startSlot = slotByNumber[course.startSlot] ?: continue
            val endSlot = slotByNumber[course.endSlot] ?: startSlot
            val startTime = ScheduleStatus.parseTime(startSlot.startTime) ?: continue
            val endTime = ScheduleStatus.parseTime(endSlot.endTime) ?: continue
            val startMillis = today.atTime(startTime).atZone(zone).toInstant().toEpochMilli()
            val endMillis = today.atTime(endTime).atZone(zone).toInstant().toEpochMilli()
            val slotRange = if (course.endSlot > course.startSlot) {
                "第 ${course.startSlot}-${course.endSlot} 节"
            } else {
                "第 ${course.startSlot} 节"
            }
            val info = OngoingCourseInfo(
                courseName = course.name,
                classroom = course.classroom,
                teacher = course.teacher,
                slotRange = slotRange,
                startTime = startSlot.startTime,
                endTime = endSlot.endTime,
                startAtMillis = startMillis,
                endAtMillis = endMillis
            )
            if (startMillis > nowMillis) {
                events.add(OngoingEvent(startMillis, isStart = true, info = info))
            }
            if (endMillis > nowMillis) {
                events.add(OngoingEvent(endMillis, isStart = false, info = info))
            }
        }
        return events.sortedBy { it.triggerAtMillis }.take(maxEvents)
    }
}
