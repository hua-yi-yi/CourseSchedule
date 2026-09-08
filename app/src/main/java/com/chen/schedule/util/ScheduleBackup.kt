package com.chen.schedule.util

import com.chen.schedule.domain.model.Course
import com.chen.schedule.domain.model.Semester
import com.chen.schedule.domain.model.TimeSlot
import kotlinx.serialization.Serializable

@Serializable
data class ScheduleBackup(
    val backupVersion: Int = 1,
    val semester: Semester,
    val courses: List<Course>,
    val timeSlots: List<TimeSlot>
)
