package com.chen.schedule.data.scraper

import com.chen.schedule.domain.model.Course
import com.chen.schedule.domain.model.WeekType
import org.jsoup.Jsoup

/** Parses the rendered HAUST EAMS grid, including merged rows and changing rooms. */
object HaustPageParser {
    fun parse(html: String): List<Course> {
        val document = Jsoup.parse(html)
        val table = document.getElementById("manualArrangeCourseTable")
        val occupied = mutableMapOf<Pair<Int, Int>, Boolean>()
        val courses = mutableListOf<Course>()
        val rows = table?.select("tr").orEmpty()
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
        if (courses.isEmpty()) courses += parseArrangementDetails(document)
        if (courses.isEmpty()) throw ScraperException("当前页面没有可识别的课程安排，请检查所选学期")
        return courses.distinct()
    }

    private fun parseArrangementDetails(document: org.jsoup.nodes.Document): List<Course> {
        val result = mutableListOf<Course>()
        var currentName = ""
        var currentTeacher = ""
        var currentCode = ""
        val dayNumbers = mapOf('一' to 1, '二' to 2, '三' to 3, '四' to 4, '五' to 5, '六' to 6, '日' to 7, '天' to 7)
        val slotPattern = Regex("星期([一二三四五六日天]).*?[［\\[](\\d+)\\s*[-–—~～至]\\s*(\\d+)\\s*节?[］\\]]")
        val weekPattern = Regex("[［\\[]\\s*([单双]?)([0-9,，、\\-–—~～至\\s]+)\\s*[］\\]]")

        document.select("tr").forEach { row ->
            val cells = row.children().toList().filter { it.tagName() == "td" }
            if (cells.isEmpty()) return@forEach
            val texts = cells.map { it.text().trim() }
            val courseIndex = texts.indexOfFirst { Regex("^\\[[^]]+]\\s*.+").containsMatchIn(it) }
            if (courseIndex >= 0) {
                val match = Regex("^\\[([^]]+)]\\s*(.+)$").find(texts[courseIndex])!!
                currentCode = match.groupValues[1].trim()
                currentName = match.groupValues[2].trim()
                currentTeacher = texts.getOrNull(courseIndex + 1).orEmpty().trim()
            }
            val slotIndex = texts.indexOfFirst { slotPattern.containsMatchIn(it) }
            if (slotIndex < 0 || currentName.isBlank()) return@forEach
            val slot = slotPattern.find(texts[slotIndex]) ?: return@forEach
            val weekIndex = (slotIndex - 1 downTo 0).firstOrNull { weekPattern.matches(texts[it]) } ?: return@forEach
            val week = weekPattern.matchEntire(texts[weekIndex]) ?: return@forEach
            val day = dayNumbers[slot.groupValues[1].first()] ?: return@forEach
            val startSlot = slot.groupValues[2].toInt()
            val endSlot = slot.groupValues[3].toInt()
            val room = texts.getOrNull(slotIndex + 1).orEmpty()
            val weekType = when (week.groupValues[1]) { "单" -> WeekType.ODD; "双" -> WeekType.EVEN; else -> WeekType.ALL }
            week.groupValues[2].split(Regex("[,，、]")).forEach { part ->
                val bounds = part.trim().split(Regex("[-–—~～至]")).mapNotNull { it.trim().toIntOrNull() }
                if (bounds.size in 1..2) {
                    val startWeek = bounds.first()
                    val endWeek = bounds.last()
                    if (startWeek in 1..53 && endWeek in startWeek..53 && startSlot > 0 && endSlot >= startSlot) {
                        result += Course(
                            name = currentName, teacher = currentTeacher, classroom = room,
                            dayOfWeek = day, startSlot = startSlot, endSlot = endSlot,
                            startWeek = startWeek, endWeek = endWeek, weekType = weekType,
                            note = "教务课程序号: $currentCode"
                        )
                    }
                }
            }
        }
        return result
    }
}
