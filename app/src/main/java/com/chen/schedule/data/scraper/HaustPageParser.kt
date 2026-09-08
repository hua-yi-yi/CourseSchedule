package com.chen.schedule.data.scraper

import com.chen.schedule.domain.model.Course
import com.chen.schedule.domain.model.WeekType
import org.jsoup.Jsoup

/** Parses the rendered HAUST EAMS grid, including merged rows and changing rooms. */
object HaustPageParser {
    fun parse(html: String): List<Course> {
        val table = Jsoup.parse(html).getElementById("manualArrangeCourseTable")
            ?: throw ScraperException("未找到课表，请进入「教学信息 → 我的课表」")
        val occupied = mutableMapOf<Pair<Int, Int>, Boolean>()
        val courses = mutableListOf<Course>()
        val rows = table.select("tr")
        rows.forEachIndexed { row, tr ->
            var column = 0
            tr.children().toList().filter { it.tagName() in listOf("td", "th") }.forEach { cell ->
                while (occupied[row to column] == true) column++
                val height = cell.attr("rowspan").toIntOrNull()?.coerceAtLeast(1) ?: 1
                val width = cell.attr("colspan").toIntOrNull()?.coerceAtLeast(1) ?: 1
                for (dr in 0 until height) for (dc in 0 until width) occupied[(row + dr) to (column + dc)] = true
                if (column in 1..7 && cell.tagName() == "td" && cell.text().isNotBlank()) {
                    val copy = cell.clone()
                    copy.select("br").forEach { it.before("\n") }
                    val lines = copy.wholeText().lines().map { it.trim() }.filter { it.isNotBlank() }
                    require(lines.size % 2 == 0) { "课程格式不完整，请选择全部教学周后重试" }
                    lines.chunked(2).forEach { pair ->
                        val header = Regex("^(.*)\\(([A-Za-z0-9_.-]+)\\)\\s*\\(([^()]*)\\)$").matchEntire(pair[0])
                            ?: throw ScraperException("无法识别课程名称: ${pair[0]}")
                        val detail = Regex("^[（(]([单双]?)([0-9,，、\\-–\\s]+)周[,，]\\s*(\\d+)(?:\\s*-\\s*(\\d+))?\\s*节(.*)$")
                            .matchEntire(pair[1]) ?: throw ScraperException("无法识别课程周次或节次: ${pair[1]}")
                        val type = when (detail.groupValues[1]) { "单" -> WeekType.ODD; "双" -> WeekType.EVEN; else -> WeekType.ALL }
                        val start = detail.groupValues[3].toInt()
                        val end = detail.groupValues[4].toIntOrNull() ?: start
                        require(start > 0 && end >= start) { "节次范围无效" }
                        var room = detail.groupValues[5].trim().removePrefix(",").removePrefix("，").trim()
                        // Some rendered cells omit the outer closing parenthesis; preserve the campus suffix.
                        while (room.endsWith(")") && room.count { it == ')' } > room.count { it == '(' }) room = room.dropLast(1)
                        while (room.endsWith("）") && room.count { it == '）' } > room.count { it == '（' }) room = room.dropLast(1)
                        detail.groupValues[2].split(Regex("[,，、]")).forEach { range ->
                            val numbers = range.trim().split(Regex("[-–]")).map { it.trim().toInt() }
                            require(numbers.size in 1..2 && numbers.first() in 1..53 && numbers.last() in numbers.first()..53) { "周次范围无效" }
                            courses += Course(name = header.groupValues[1].trim(), teacher = header.groupValues[3],
                                classroom = room, dayOfWeek = column, startSlot = start, endSlot = end,
                                startWeek = numbers.first(), endWeek = numbers.last(), weekType = type,
                                note = "教务课程序号: ${header.groupValues[2]}")
                        }
                    }
                }
                column += width
            }
        }
        if (courses.isEmpty()) throw ScraperException("当前课表没有已排课课程，请检查所选学期")
        return courses.distinct()
    }
}
