package com.chen.schedule.util

import java.time.LocalDate

/**
 * 根据当前日期推荐常见学期名称,初始设置时点选即可,不必手输。
 *
 * 学年以 7 月为界:7–12 月属于「YYYY–YYYY+1」学年的第一学期,1–6 月属于该学年的第二学期。
 * 返回顺序即推荐顺序(第一个为当前所处学期)。
 */
object SemesterNameSuggestions {

    fun generate(today: LocalDate): List<String> {
        val ayStart = if (today.monthValue >= 7) today.year else today.year - 1
        return if (today.monthValue >= 7) {
            listOf(
                "$ayStart–${ayStart + 1}学年第一学期",
                "$ayStart–${ayStart + 1}学年第二学期",
                "${ayStart - 1}–${ayStart}学年第一学期"
            )
        } else {
            listOf(
                "$ayStart–${ayStart + 1}学年第二学期",
                "$ayStart–${ayStart + 1}学年第一学期",
                "${ayStart - 1}–${ayStart}学年第二学期"
            )
        }
    }
}
