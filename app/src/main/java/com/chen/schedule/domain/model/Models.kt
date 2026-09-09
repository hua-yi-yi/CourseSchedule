package com.chen.schedule.domain.model

@kotlinx.serialization.Serializable
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

@kotlinx.serialization.Serializable
data class Semester(
    val id: Long = 0,
    val name: String = "",
    val startDate: Long = System.currentTimeMillis(),
    val totalWeeks: Int = 16,
    val isCurrent: Boolean = true,
    /**
     * 关联的作息方案 id。0 = 沿用「原有作息」(全局方案,兼容旧数据);
     * >0 = [TimeScheme] 的 id。切换学期时据此恢复该学期使用的作息。
     */
    val schemeId: Long = 0
)

@kotlinx.serialization.Serializable
data class TimeSlot(
    val id: Long = 0,
    val slotNumber: Int = 1,
    val startTime: String = "08:00",
    val endTime: String = "08:45",
    val name: String = "",
    /** 作息套别:0=通用,1=夏季,2=冬季。 */
    val season: Int = 0,
    /** 归属性息方案 id:0 = 原有作息(全局),>0 = [TimeScheme] 的 id。 */
    val schemeId: Long = 0
)

/**
 * 作息方案:一组节次时间的可复用集合。
 *
 * - [TimeScheme.LEGACY_ID] (0) 表示「原有作息」——升级前的全局节次,不对应 time_schemes 表中的行。
 * - [kind] = 0 内置模板(夏季/冬季),= 1 自定义方案。
 * - 同一方案可被多个学期引用;删除/替换方案不得静默影响其他学期。
 */
@kotlinx.serialization.Serializable
data class TimeScheme(
    val id: Long = 0,
    val name: String = "",
    val kind: Int = KIND_CUSTOM,
    val season: Int = 0,
    val createTime: Long = System.currentTimeMillis()
) {
    val isBuiltIn: Boolean get() = kind == KIND_BUILT_IN
    val isLegacy: Boolean get() = id == LEGACY_ID

    companion object {
        /** 原有作息(升级前全局节次)的保留 id,不与自增主键冲突。 */
        const val LEGACY_ID: Long = 0L
        const val KIND_BUILT_IN: Int = 0
        const val KIND_CUSTOM: Int = 1
        const val LEGACY_NAME: String = "原有作息"
    }
}

enum class DayOfWeek(val label: String, val index: Int) {
    MON("周一", 1),
    TUE("周二", 2),
    WED("周三", 3),
    THU("周四", 4),
    FRI("周五", 5),
    SAT("周六", 6),
    SUN("周日", 7)
}
