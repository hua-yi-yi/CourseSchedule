package com.chen.schedule.util

/**
 * 学期周次计算:课程表视图、桌面小组件共用的唯一实现。
 */
object WeekCalculator {

    private const val MILLIS_PER_DAY = 1000L * 60 * 60 * 24

    /**
     * 根据学期开学日期计算当前是第几周(1-based,并钳制在 [1, totalWeeks])。
     *
     * @param startDate 开学日期时间戳
     * @param totalWeeks 学期总周数
     * @param nowMillis 当前时间(可注入以便测试)
     */
    fun currentWeek(startDate: Long, totalWeeks: Int, nowMillis: Long = System.currentTimeMillis()): Int {
        val diffDays = (nowMillis - startDate) / MILLIS_PER_DAY
        val week = (diffDays / 7 + 1).toInt()
        return week.coerceIn(1, totalWeeks.coerceAtLeast(1))
    }
}
