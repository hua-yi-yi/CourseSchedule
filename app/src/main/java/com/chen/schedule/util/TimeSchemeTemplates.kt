package com.chen.schedule.util

import com.chen.schedule.domain.model.TimeScheme
import com.chen.schedule.domain.model.TimeSlot

/**
 * 内置作息模板(夏季/冬季)。
 *
 * 与旧实现不同,这里只提供「模板定义」,不再直接删除并重写全部节次。
 * 选择模板时由 [com.chen.schedule.data.repository.TimeSchemeRepository] 在事务内
 * 把模板节次写入对应方案(内置方案写入自己的 schemeId 空间),其他方案与学期不受影响。
 */
object TimeSchemeTemplates {

    const val SEASON_NONE = 0
    const val SEASON_SUMMER = 1
    const val SEASON_WINTER = 2

    /** 每节时长(分钟),与旧版本保持一致。 */
    private const val DURATION_MINUTES = 45

    val SUMMER_START_TIMES = listOf(
        "08:00", "08:55", "10:00", "10:55",
        "14:30", "15:20", "16:25", "17:20", "18:10",
        "19:30", "20:20", "21:10"
    )

    val WINTER_START_TIMES = listOf(
        "08:00", "08:55", "10:00", "10:55",
        "14:00", "14:50", "15:55", "16:50", "17:40",
        "19:00", "19:50", "20:40"
    )

    /** 内置模板的规范名称,唯一标识一个模板。 */
    const val SUMMER_NAME = "夏季作息"
    const val WINTER_NAME = "冬季作息"

    /** 全部内置模板的 (名称, 季节) 定义,顺序即展示顺序。 */
    val builtIns: List<BuiltIn> = listOf(
        BuiltIn(SUMMER_NAME, SEASON_SUMMER, SUMMER_START_TIMES),
        BuiltIn(WINTER_NAME, SEASON_WINTER, WINTER_START_TIMES)
    )

    data class BuiltIn(val name: String, val season: Int, val startTimes: List<String>)

    /** 由开始时间列表生成整套节次(schemeId 由调用方补齐)。 */
    fun slotsOf(startTimes: List<String>, schemeId: Long = TimeScheme.LEGACY_ID): List<TimeSlot> =
        startTimes.mapIndexed { index, start ->
            val number = index + 1
            TimeSlot(
                slotNumber = number,
                startTime = start,
                endTime = addMinutes(start, DURATION_MINUTES),
                name = "第${number}节",
                schemeId = schemeId
            )
        }

    /** "HH:mm" + 分钟,支持跨小时,不跨天回绕(超过 24:00 时按 23:59 截断)。 */
    private fun addMinutes(time: String, minutes: Int): String {
        val parts = time.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: 8
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
        val total = hour * 60 + minute + minutes
        val dayMinutes = 24 * 60 - 1
        val clamped = total.coerceIn(0, dayMinutes)
        return String.format("%02d:%02d", clamped / 60, clamped % 60)
    }
}
