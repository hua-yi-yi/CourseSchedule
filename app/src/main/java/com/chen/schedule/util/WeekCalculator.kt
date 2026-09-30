package com.chen.schedule.util

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/** Calendar weeks run Monday through Sunday in the device time zone. */
object WeekCalculator {
    /** Real teaching week, or null outside the semester. UI browsing still uses currentWeek. */
    fun activeWeek(startDate: Long, totalWeeks: Int, nowMillis: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): Int? {
        if (startDate <= 0L || totalWeeks !in 1..53) return null
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        val days = ChronoUnit.DAYS.between(semesterMonday(startDate, zone), today)
        val week = Math.floorDiv(days, 7) + 1
        return week.takeIf { it in 1L..totalWeeks.toLong() }?.toInt()
    }

    fun semesterMonday(startDate: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDate =
        Instant.ofEpochMilli(startDate).atZone(zone).toLocalDate()
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    fun currentWeek(startDate: Long, totalWeeks: Int, nowMillis: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): Int {
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        val days = ChronoUnit.DAYS.between(semesterMonday(startDate, zone), today)
        return (Math.floorDiv(days, 7) + 1).coerceIn(1L, totalWeeks.coerceAtLeast(1).toLong()).toInt()
    }
}
