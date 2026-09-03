package com.chen.schedule.domain.model

data class Course(
    val id: Long = 0,
    val name: String = "",
    val teacher: String = "",
    val classroom: String = "",
    val dayOfWeek: Int = 1,
    val startSlot: Int = 1,
    val endSlot: Int = 2,
    val startWeek: Int = 1,
    val endWeek: Int = 16,
    val weekType: WeekType = WeekType.ALL,
    val color: Long = 0xFF4CAF50,
    val semesterId: Long = 0,
    val note: String = ""
) {
    /** 判断该课程在第 [week] 周是否有课(按周类型与周次范围) */
    fun appliesToWeek(week: Int): Boolean {
        val typeMatch = when (weekType) {
            WeekType.ODD -> week % 2 == 1
            WeekType.EVEN -> week % 2 == 0
            WeekType.ALL -> true
        }
        return typeMatch && week >= startWeek && week <= endWeek
    }
}

enum class WeekType(val label: String) {
    ALL("每周"),
    ODD("单周"),
    EVEN("双周")
}

data class Semester(
    val id: Long = 0,
    val name: String = "",
    val startDate: Long = System.currentTimeMillis(),
    val totalWeeks: Int = 16,
    val isCurrent: Boolean = true
)

data class TimeSlot(
    val id: Long = 0,
    val slotNumber: Int = 1,
    val startTime: String = "08:00",
    val endTime: String = "08:45",
    val name: String = ""
)

enum class DayOfWeek(val label: String, val index: Int) {
    MON("周一", 1),
    TUE("周二", 2),
    WED("周三", 3),
    THU("周四", 4),
    FRI("周五", 5),
    SAT("周六", 6),
    SUN("周日", 7)
}
