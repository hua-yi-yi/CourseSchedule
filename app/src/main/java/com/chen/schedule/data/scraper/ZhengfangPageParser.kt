package com.chen.schedule.data.scraper

import com.chen.schedule.domain.model.Course
import com.chen.schedule.domain.model.CoursePalette
import com.chen.schedule.domain.model.WeekType
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * 正方教务系统课表页面(经典版 xskbcx.aspx)解析器。
 *
 * 纯函数式、无网络、无状态,便于单元测试。
 * 兼容点:
 *  - 单元格内 <br> / <div> / <p> 分隔多行信息(课程名/教师/教室/周次)
 *  - rowspan 跨行大节(如 1-2 节连排)
 *  - 周次文本如 "1-16周" / "1-8周(单)" / "2-16周（双）" / "第3周"
 *  - 同一单元格内多门课程按 3 行一组识别
 *  - 无 rowspan 且相邻行重复同一课程时自动合并
 */
object ZhengfangPageParser {

    data class WeekInfo(
        val startWeek: Int,
        val endWeek: Int,
        val weekType: WeekType
    )

    private val DEFAULT_WEEK = WeekInfo(1, 16, WeekType.ALL)

    /** 周次范围:1-16周 / 1～16周 / 1—16周 / 1至16周,可带 (单)/(双) 标记 */
    private val WEEKS_RANGE_REGEX = Regex(
        """(\d{1,2})\s*[-—–~～－至]\s*(\d{1,2})\s*周\s*[（(]?\s*(单|双)?\s*[）)]?"""
    )

    /** 单周次:第3周 / 3周,可带 (单)/(双) 标记 */
    private val WEEKS_SINGLE_REGEX = Regex(
        """第?\s*(\d{1,2})\s*周\s*[（(]?\s*(单|双)?\s*[）)]?"""
    )

    /** 单元格内行分隔符 */
    private val LINE_BREAK_REGEX = Regex("""(?i)<\s*/?\s*(?:br|p|div)\s*/?>""")

    /**
     * 解析课表 HTML 中的周次文本。
     * 返回 null 表示该文本不含周次信息。
     */
    fun parseWeekInfo(text: String): WeekInfo? {
        val range = WEEKS_RANGE_REGEX.find(text)
        if (range != null) {
            val start = range.groupValues[1].toIntOrNull() ?: return null
            val end = range.groupValues[2].toIntOrNull() ?: start
            val type = when (range.groupValues[3]) {
                "单" -> WeekType.ODD
                "双" -> WeekType.EVEN
                else -> WeekType.ALL
            }
            return WeekInfo(start, end, type)
        }
        val single = WEEKS_SINGLE_REGEX.find(text)
        if (single != null) {
            val week = single.groupValues[1].toIntOrNull() ?: return null
            val type = when (single.groupValues[2]) {
                "单" -> WeekType.ODD
                "双" -> WeekType.EVEN
                else -> WeekType.ALL
            }
            return WeekInfo(week, week, type)
        }
        return null
    }

    /** 将单元格 HTML 拆成多行文本(兼容 <br>/<div>/<p>) */
    fun parseCellLines(html: String): List<String> =
        html.replace(LINE_BREAK_REGEX, "\n")
            .split("\n")
            .map { Jsoup.parseBodyFragment(it).text().replace('\u00A0', ' ').trim() }
            .filter { it.isNotBlank() }

    /** 解析课表 HTML 文档,返回课程列表 */
    fun parseCourses(html: String): List<Course> =
        parseCourses(Jsoup.parse(html))

    /**
     * 解析课表文档。
     * 表格结构假设(经典正方 Table1):
     *  - 第一列为节次号(数字,表头/时间行会被跳过)
     *  - 其后每列对应周一~周日,rowspan 表示课程跨多个小节
     */
    fun parseCourses(doc: Document): List<Course> {
        val table = doc.select("table#Table1").firstOrNull()
            ?: doc.select("table[id*=Table]").firstOrNull()
            ?: doc.select("table").firstOrNull()
            ?: return emptyList()

        val rows = table.select("tr")
        val courses = mutableListOf<Course>()
        // day(1..7) -> 后续仍被 rowspan 覆盖的行数(不含已渲染的当前行)
        val pendingRowspan = mutableMapOf<Int, Int>()

        for (row in rows) {
            val tds = row.select("td")
            if (tds.size < 2) continue

            // 节次列:非纯数字视为表头/时间行,跳过
            val slot = tds[0].text().replace('\u00A0', ' ').trim().toIntOrNull() ?: continue
            if (slot < 1) continue

            // 计算本行被上文的 rowspan 占据的星期列
            val occupiedDays = mutableSetOf<Int>()
            for (d in 1..7) {
                val remain = pendingRowspan[d] ?: 0
                if (remain > 0) {
                    occupiedDays.add(d)
                    pendingRowspan[d] = remain - 1
                }
            }

            var day = 1
            for (i in 1 until tds.size) {
                while (day <= 7 && day in occupiedDays) day++
                if (day > 7) break

                val td = tds[i]
                val text = td.text().replace('\u00A0', ' ').trim()
                if (text.isNotEmpty()) {
                    parseCell(td, day, slot).forEach { courses.add(it) }
                }
                val rowSpan = td.attr("rowspan").toIntOrNull() ?: 1
                if (rowSpan > 1) pendingRowspan[day] = rowSpan - 1
                day++
            }
        }

        return CoursePalette.assignColors(
            mergeAdjacentDuplicates(courses)
                .sortedWith(compareBy({ it.dayOfWeek }, { it.startSlot }))
        )
    }

    /**
     * 解析单个单元格,支持其中包含多门课程(每 3 行一组:<br> 分隔)。
     */
    private fun parseCell(td: Element, day: Int, slot: Int): List<Course> {
        val lines = parseCellLines(td.html())
        if (lines.isEmpty()) return emptyList()

        // 分离周次行与信息行,避免"1-16周"被当作教师/教室
        val weekLines = lines.mapNotNull { parseWeekInfo(it) }
        val infoLines = lines.filter { parseWeekInfo(it) == null }
        val weekInfo = weekLines.firstOrNull() ?: DEFAULT_WEEK

        val rowSpan = td.attr("rowspan").toIntOrNull()?.coerceAtLeast(1) ?: 1
        val endSlot = (slot + rowSpan - 1).coerceAtLeast(slot)

        // 多门课程:信息行数为 3 的倍数且 > 3 时按 3 行一组切分
        val groups: List<List<String>> =
            if (infoLines.size > 3 && infoLines.size % 3 == 0) infoLines.chunked(3)
            else listOf(infoLines)

        return groups.mapNotNull { info ->
            val name = info.getOrNull(0) ?: return@mapNotNull null
            if (name.isBlank()) return@mapNotNull null
            Course(
                name = name,
                teacher = info.getOrNull(1) ?: "",
                classroom = info.getOrNull(2) ?: "",
                dayOfWeek = day,
                startSlot = slot,
                endSlot = endSlot,
                startWeek = weekInfo.startWeek,
                endWeek = weekInfo.endWeek,
                weekType = weekInfo.weekType
            )
        }
    }

    /** 合并无 rowspan 时相邻行重复出现的同一课程(如 1、2 节重复渲染) */
    private fun mergeAdjacentDuplicates(courses: List<Course>): List<Course> {
        val merged = mutableListOf<Course>()
        for (course in courses) {
            val last = merged.lastOrNull()
            if (last != null &&
                last.dayOfWeek == course.dayOfWeek &&
                sameCourseKey(last, course) &&
                course.startSlot <= last.endSlot + 1
            ) {
                merged[merged.size - 1] = last.copy(endSlot = maxOf(last.endSlot, course.endSlot))
            } else {
                merged += course
            }
        }
        return merged
    }

    private fun sameCourseKey(a: Course, b: Course): Boolean =
        a.name == b.name &&
            a.teacher == b.teacher &&
            a.classroom == b.classroom &&
            a.startWeek == b.startWeek &&
            a.endWeek == b.endWeek &&
            a.weekType == b.weekType
}
