package com.chen.schedule.island

import com.chen.schedule.domain.model.Course

/**
 * 胶囊灵动岛实时状态定义。
 */
sealed interface IslandState {

    /**
     * 正在上课状态。
     */
    data class Ongoing(
        val course: Course,
        val courseName: String,
        val classroom: String,
        val teacher: String,
        val slotRange: String,
        val startTime: String,
        val endTime: String,
        val startMillis: Long,
        val endMillis: Long,
        val remainingMinutes: Int,
        val totalMinutes: Int,
        val progress: Float,
        val color: Long,
        val compactText: String,
        val subText: String
    ) : IslandState

    /**
     * 即将上课状态 (上课前 [leadMinutes] 分钟内)。
     */
    data class Upcoming(
        val course: Course,
        val courseName: String,
        val classroom: String,
        val teacher: String,
        val slotRange: String,
        val startTime: String,
        val endTime: String,
        val startMillis: Long,
        val minutesUntilStart: Int,
        val color: Long,
        val compactText: String,
        val subText: String
    ) : IslandState

    /**
     * 空闲状态 (今日有课但尚未进入临近窗口，或今日课程已全部结束)。
     */
    data class Idle(
        val todayTotalCourses: Int,
        val finishedCourses: Int,
        val nextCourse: Course? = null,
        val nextCourseStartTime: String? = null,
        val compactText: String,
        val subText: String
    ) : IslandState

    /**
     * 无课程信息或未设置学期。
     */
    data object None : IslandState
}
