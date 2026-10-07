package com.chen.schedule.data.scraper

import com.chen.schedule.domain.model.Course
import com.chen.schedule.domain.model.WeekType
import org.jsoup.Jsoup

/**
 * Parses the rendered HAUST EAMS page, including the course grid, merged rows and changing rooms.
 *
 * 遗漏修复:课表页的「网格表」与「理论课程安排」明细表可能各含对方没有的安排
 * (例如实验课/体育课只在明细表中出现)。因此两路解析始终都执行并做并集,
 * 而不是仅在网格为空时才回落到明细表。
 *
 * 真实页面容错(2025-10 抓取样本):
 * - 网格周次段可能以空格分隔,如「(1-6 8-11周,…)」;也可能用「至/~/—」连接。
 * - 明细表已改为多列表格(序号|课程|教师|…|起止周|人数|类别|考核|周次|节次|地点),
 *   周次格存在「[1-6],[8-10]」「11」「2,4,6,8,10,12」等多种写法,且续行首格
 *   「[1-6],[8-10]」不能再被误认成课程单元格。
 */
object HaustPageParser {
    /** 网格明细行: (单双)(周次)周,节次,教室 */
    private val gridDetail = Regex(
        "^[（(]([单双]?)第?([0-9,，、\\-–—~～至\\s]+)周[,，]\\s*第?(\\d+)(?:\\s*[-–—~～至]\\s*(\\d+))?\\s*节(.*)$"
    )

    /** 「[1-6],[8-10]」这类整体只含周次符号的单元格,不能当作课程单元格。 */
    private val pureWeekCell = Regex("^[\\[［\\]］0-9,，、\\-–—~～至\\s]+$")

    /** 明细表裸周次格:「11」「2,4,6,8,10,12」(新表格周次列不加方括号)。 */
    private val bareWeekCell = Regex("^[0-9][0-9,，、\\-–—~～至\\s]*$")

    /** 括号包裹的周次段,一段一格,取全部匹配而不是第一个。 */
    private val bracketedWeeks = Regex("[［\\[]\\s*([单双]?)([0-9,，、\\-–—~～至\\s]+?)\\s*[］\\]]")

    fun parse(html: String): List<Course> {
        val document = Jsoup.parse(html)
        // 网格解析保持严格:畸形单元格会抛错,但只要明细表能解析出课程,就优先返回数据
        val gridResult = runCatching { parseGrid(document) }
        val grid = gridResult.getOrDefault(emptyList())
        val details = runCatching { parseArrangementDetails(document) }.getOrDefault(emptyList())
        val merged = union(grid, details)
        when {
            merged.isNotEmpty() -> return merged
            gridResult.isFailure -> {
                val cause = gridResult.exceptionOrNull() ?: ScraperException("当前页面没有可识别的课程安排")
                throw if (cause is ScraperException) cause
                else ScraperException("课表数据格式异常(${cause.javaClass.simpleName})，请截图反馈开发者")
            }
            else -> throw ScraperException("当前页面没有可识别的课程安排，请检查所选学期")
        }
    }

    /**
     * 并集去重:网格是权威数据源,明细表只补充网格没有的安排。
     * 明细课程若在名称/星期/节次/教室一致的前提下被某条网格课程完全覆盖周次,则跳过,
     * 避免同一节新课因周次写法不同(如「双2-12周」与「2,4,6,8,10,12」)而重复导入。
     * 教室比对容忍后缀差异:明细表常省略网格里的「(开元校区)」等校区括号。
     */
    private fun union(grid: List<Course>, details: List<Course>): List<Course> {
        if (grid.isEmpty()) return details.distinct()
        if (details.isEmpty()) return grid
        val merged = grid.toMutableList()
        details.forEach { d ->
            val weeks = (1..53).filter { d.appliesToWeek(it) }
            val covered = weeks.isNotEmpty() && merged.any { g ->
                // 名称忽略空白差异:网格 sup 拼接无空格,明细表可能多一个空格
                g.name.replace(Regex("\\s+"), "") == d.name.replace(Regex("\\s+"), "") &&
                    g.dayOfWeek == d.dayOfWeek &&
                    g.startSlot == d.startSlot && g.endSlot == d.endSlot &&
                    sameRoom(g.classroom, d.classroom) &&
                    weeks.all { g.appliesToWeek(it) }
            }
            if (!covered && merged.none { it == d }) merged += d
        }
        return merged
    }

    private fun sameRoom(a: String, b: String): Boolean = when {
        a.isBlank() || b.isBlank() -> true
        a == b -> true
        else -> a.startsWith(b) || b.startsWith(a)
    }

    private fun parseGrid(document: org.jsoup.nodes.Document): List<Course> {
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
                        val detail = gridDetail.matchEntire(pair[1])
                            ?: throw ScraperException("无法识别课程周次或节次: ${pair[1]}")
                        val type = when (detail.groupValues[1]) { "单" -> WeekType.ODD; "双" -> WeekType.EVEN; else -> WeekType.ALL }
                        val start = detail.groupValues[3].toInt()
                        val end = detail.groupValues[4].toIntOrNull() ?: start
                        require(start > 0 && end >= start) { "节次范围无效" }
                        var room = detail.groupValues[5].trim().removePrefix(",").removePrefix("，").trim()
                        // Some rendered cells omit the outer closing parenthesis; preserve the campus suffix.
                        while (room.endsWith(")") && room.count { it == ')' } > room.count { it == '(' }) room = room.dropLast(1)
                        while (room.endsWith("）") && room.count { it == '）' } > room.count { it == '（' }) room = room.dropLast(1)
                        // 周次段之间除逗号外还可能直接用空格分隔(如「1-6 8-11」),一并拆开
                        detail.groupValues[2].split(Regex("[,，、\\s]+")).filter { it.isNotBlank() }.forEach { range ->
                            val numbers = range.split(Regex("[-–—~～至]")).map { it.trim().toIntOrNull() }
                            require(!numbers.any { it == null } && numbers.size in 1..2) { "周次范围无效" }
                            val from = numbers[0]!!.also { require(it in 1..53) { "周次范围无效" } }
                            val to = (numbers.getOrNull(1) ?: from).also { require(it in from..53) { "周次范围无效" } }
                            courses += Course(name = header.groupValues[1].trim(), teacher = header.groupValues[3],
                                classroom = room, dayOfWeek = column, startSlot = start, endSlot = end,
                                startWeek = from, endWeek = to, weekType = type,
                                note = "教务课程序号: ${header.groupValues[2]}")
                        }
                    }
                }
                column += width
            }
        }
        return courses.distinct()
    }

    private fun parseArrangementDetails(document: org.jsoup.nodes.Document): List<Course> {
        val result = mutableListOf<Course>()
        var currentName = ""
        var currentTeacher = ""
        var currentCode = ""
        val dayNumbers = mapOf('一' to 1, '二' to 2, '三' to 3, '四' to 4, '五' to 5, '六' to 6, '日' to 7, '天' to 7)
        val slotPattern = Regex("星期([一二三四五六日天]).*?[［\\[](\\d+)\\s*[-–—~～至]\\s*(\\d+)\\s*节?[］\\]]")

        document.select("tr").forEach { row ->
            val cells = row.children().toList().filter { it.tagName() == "td" }
            if (cells.isEmpty()) return@forEach
            val texts = cells.map { it.text().trim() }
            // 「[1-6],[8-10]」等纯周次格不是课程单元格;课程格形如「[rjxy_2611001]马克思主义基本原理」
            val courseIndex = texts.indexOfFirst {
                Regex("^\\[[^]]+]\\s*.+").containsMatchIn(it) && !pureWeekCell.matches(it)
            }
            if (courseIndex >= 0) {
                val match = Regex("^\\[([^]]+)]\\s*(.+)$").find(texts[courseIndex])!!
                currentCode = match.groupValues[1].trim()
                currentName = match.groupValues[2].trim()
                currentTeacher = texts.getOrNull(courseIndex + 1).orEmpty().trim()
            }
            val slotIndex = texts.indexOfFirst { slotPattern.containsMatchIn(it) }
            if (slotIndex < 0 || currentName.isBlank()) return@forEach
            val slot = slotPattern.find(texts[slotIndex]) ?: return@forEach
            // 新表格中周次格紧挨在节次格左侧,只看相邻一格,避免把学分/人数等数字列误当周次
            if (slotIndex == 0) return@forEach
            val weekText = texts[slotIndex - 1]
            val day = dayNumbers[slot.groupValues[1].first()] ?: return@forEach
            val startSlot = slot.groupValues[2].toInt()
            val endSlot = slot.groupValues[3].toInt()
            // 教室列可能缺失,不因此丢课
            val room = texts.getOrNull(slotIndex + 1).orEmpty().trim()
            if (startSlot <= 0 || endSlot < startSlot) return@forEach
            // 周次既可能是「[1-6],[8-10]」也可能是裸列表「2,4,6,8,10,12」;统一拆成 (单双, 周次段) 组
            val bracketed = bracketedWeeks.findAll(weekText).toList()
            val segments: List<Pair<String, String>> = if (bracketed.isNotEmpty()) {
                bracketed.map { it.groupValues[1] to it.groupValues[2] }
            } else if (bareWeekCell.matches(weekText)) {
                // 裸列表只有在全部是单个小周次且构成公差 2 的连续同奇偶序列时才合并,
                // 如「2,4,6,8,10,12」→ 双2-12周;其余(乱序列表、混合奇偶)逐周保留。
                val weeks = weekText.split(Regex("[,，、\\s]+")).mapNotNull { it.trim().toIntOrNull() }.sorted()
                val stepTwo = weeks.size >= 2 && weeks.zipWithNext().all { (a, b) -> b - a == 2 } &&
                    weeks.all { it % 2 == weeks.first() % 2 }
                if (stepTwo) {
                    val parity = if (weeks.first() % 2 == 1) "单" else "双"
                    listOf(parity to "${weeks.first()}-${weeks.last()}")
                } else {
                    listOf("" to weekText)
                }
            } else return@forEach
            segments.forEach { (parity, range) ->
                val type = when (parity) { "单" -> WeekType.ODD; "双" -> WeekType.EVEN; else -> WeekType.ALL }
                range.split(Regex("[,，、\\s]+")).filter { it.isNotBlank() }.forEach { part ->
                    val bounds = part.split(Regex("[-–—~～至]")).mapNotNull { it.trim().toIntOrNull() }
                    if (bounds.size in 1..2) {
                        val startWeek = bounds.first()
                        val endWeek = bounds.last()
                        if (startWeek in 1..53 && endWeek in startWeek..53) {
                            result += Course(
                                name = currentName, teacher = currentTeacher, classroom = room,
                                dayOfWeek = day, startSlot = startSlot, endSlot = endSlot,
                                startWeek = startWeek, endWeek = endWeek, weekType = type,
                                note = "教务课程序号: $currentCode"
                            )
                        }
                    }
                }
            }
        }
        return result
    }
}
