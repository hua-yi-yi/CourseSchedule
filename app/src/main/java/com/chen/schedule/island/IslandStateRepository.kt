package com.chen.schedule.island

import android.content.Context
import com.chen.schedule.di.DatabaseEntryPoint
import com.chen.schedule.domain.model.Course
import com.chen.schedule.domain.model.Semester
import com.chen.schedule.domain.model.TimeSlot
import com.chen.schedule.util.WeekCalculator
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId

data class IslandScheduleSnapshot(
    val semester: Semester?,
    val courses: List<Course>,
    val slots: List<TimeSlot>,
    val nowMillis: Long,
    val zone: ZoneId = ZoneId.systemDefault()
) {
    val activeWeek: Int? get() = semester?.let {
        WeekCalculator.activeWeek(it.startDate, it.totalWeeks, nowMillis, zone)
    }
    val dayOfWeek: Int get() = Instant.ofEpochMilli(nowMillis).atZone(zone).dayOfWeek.value
    fun state(leadMinutes: Int): IslandState {
        val week = activeWeek ?: return IslandState.None
        return IslandStateCalculator.calculate(nowMillis, slots, courses, week, dayOfWeek, leadMinutes, zone)
    }
}

/** A single actual-date source: browsing a timetable week cannot change live status. */
object IslandStateRepository {
    suspend fun snapshot(context: Context, nowMillis: Long? = null): IslandScheduleSnapshot =
        withContext(Dispatchers.IO) {
            val entry = EntryPointAccessors.fromApplication(context.applicationContext, DatabaseEntryPoint::class.java)
            val semester = entry.semesterRepository().getCurrentSemester()
            IslandScheduleSnapshot(
                semester,
                if (semester == null) emptyList() else entry.courseRepository().getCoursesBySemester(semester.id).first(),
                if (semester == null) emptyList() else entry.timeSlotRepository().getTimeSlotsByScheme(semester.schemeId).first(),
                nowMillis ?: System.currentTimeMillis()
            )
        }
    suspend fun load(context: Context, nowMillis: Long? = null): IslandState =
        snapshot(context, nowMillis).state(IslandPrefs.init(context).leadMinutes)
}
