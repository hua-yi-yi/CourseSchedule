package com.chen.schedule.island

import com.chen.schedule.util.ScheduleStatus
import java.time.Instant

/** Schedule only state boundaries, never a repeating background countdown ticker. */
object IslandEventPlanner {
    fun boundaries(snapshot: IslandScheduleSnapshot, leadMinutes: Int): List<Long> {
        val week = snapshot.activeWeek ?: return emptyList()
        val date = Instant.ofEpochMilli(snapshot.nowMillis).atZone(snapshot.zone).toLocalDate()
        val dayStart = date.atStartOfDay(snapshot.zone).toInstant().toEpochMilli()
        val slots = snapshot.slots.associateBy { it.slotNumber }
        return snapshot.courses.asSequence()
            .filter { it.dayOfWeek == snapshot.dayOfWeek && it.appliesToWeek(week) }
            .flatMap { course ->
                val first = slots[course.startSlot]
                val last = slots[course.endSlot] ?: first
                val start = first?.let { ScheduleStatus.parseTime(it.startTime) }
                val end = last?.let { ScheduleStatus.parseTime(it.endTime) }
                if (start == null || end == null || !end.isAfter(start)) emptySequence()
                else {
                    val startMillis = date.atTime(start).atZone(snapshot.zone).toInstant().toEpochMilli()
                    val endMillis = date.atTime(end).atZone(snapshot.zone).toInstant().toEpochMilli()
                    sequenceOf((startMillis - leadMinutes.coerceIn(5, 120) * 60_000L).coerceAtLeast(dayStart),
                        startMillis, endMillis)
                }
            }
            .filter { it > snapshot.nowMillis }.distinct().sorted().toList()
    }
}

object IslandPresentationPolicy {
    fun shouldShowSystem(config: IslandConfig, state: IslandState): Boolean =
        config.enabled && config.mode == IslandDisplayMode.SYSTEM &&
            (state is IslandState.Upcoming || state is IslandState.Ongoing)
    fun expiresAt(state: IslandState): Long? = when (state) {
        is IslandState.Upcoming -> state.startMillis
        is IslandState.Ongoing -> state.endMillis
        else -> null
    }
}
