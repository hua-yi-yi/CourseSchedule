package com.chen.schedule.util

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/** Calendar weeks run Monday through Sunday in the device time zone. */
object WeekCalculator {
    fun semesterMonday(startDate: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDate =
        Instant.ofEpochMilli(startDate).atZone(zone).toLocalDate()
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    fun currentWeek(startDate: Long, totalWeeks: Int, nowMillis: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): Int {
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        val days = ChronoUnit.DAYS.between(semesterMonday(startDate, zone), today)
        return (Math.floorDiv(days, 7) + 1).coerceIn(1L, totalWeeks.coerceAtLeast(1).toLong()).toInt()
    }
}
