package com.chen.schedule.util

import com.chen.schedule.domain.model.Course
import com.chen.schedule.domain.model.WeekType
import java.io.BufferedReader
import java.io.StringReader

object CsvImporter {

    fun parse(csvString: String): Result<List<Course>> {
        return try {
            val reader = BufferedReader(StringReader(csvString))
            val header = reader.readLine() ?: return Result.failure(Exception("Empty CSV"))
            val columns = header.split(",").map { it.trim().lowercase() }

            val nameIdx = columns.indexOfFirst { it in listOf("name", "课程名称", "课程名") }
            val teacherIdx = columns.indexOfFirst { it in listOf("teacher", "教师", "老师") }
            val classroomIdx = columns.indexOfFirst { it in listOf("classroom", "教室", "地点") }
            val dayIdx = columns.indexOfFirst { it in listOf("dayofweek", "day", "星期", "星期几") }
            val startSlotIdx = columns.indexOfFirst { it in listOf("startslot", "开始节次", "起始节次") }
            val endSlotIdx = columns.indexOfFirst { it in listOf("endslot", "结束节次") }
            val startWeekIdx = columns.indexOfFirst { it in listOf("startweek", "起始周") }
            val endWeekIdx = columns.indexOfFirst { it in listOf("endweek", "结束周") }
            val weekTypeIdx = columns.indexOfFirst { it in listOf("weektype", "周类型") }
            val noteIdx = columns.indexOfFirst { it in listOf("note", "备注") }

            if (nameIdx == -1 || dayIdx == -1) {
                return Result.failure(Exception("CSV must contain 'name' and 'dayOfWeek' columns"))
            }

            val courses = mutableListOf<Course>()
            reader.forEachLine { line ->
                if (line.isBlank()) return@forEachLine
                val values = parseCsvLine(line)
                if (values.size <= nameIdx) return@forEachLine

                val weekType = if (weekTypeIdx >= 0 && weekTypeIdx < values.size) {
                    when (values[weekTypeIdx].trim().lowercase()) {
                        "odd", "单周" -> WeekType.ODD
                        "even", "双周" -> WeekType.EVEN
                        else -> WeekType.ALL
                    }
                } else WeekType.ALL

                val startSlotRaw = if (startSlotIdx >= 0 && startSlotIdx < values.size) values[startSlotIdx].trim() else "1"
                val endSlotRaw = if (endSlotIdx >= 0 && endSlotIdx < values.size) values[endSlotIdx].trim() else ""
                val (startSlot, endSlot) = parseSlotRange(startSlotRaw, endSlotRaw)

                val startWeekRaw = if (startWeekIdx >= 0 && startWeekIdx < values.size) values[startWeekIdx].trim() else "1"
                val endWeekRaw = if (endWeekIdx >= 0 && endWeekIdx < values.size) values[endWeekIdx].trim() else ""
                val (startWeek, endWeek) = parseSlotRange(startWeekRaw, endWeekRaw)

                courses.add(
                    Course(
                        name = values[nameIdx].trim(),
                        teacher = if (teacherIdx >= 0 && teacherIdx < values.size) values[teacherIdx].trim() else "",
                        classroom = if (classroomIdx >= 0 && classroomIdx < values.size) values[classroomIdx].trim() else "",
                        dayOfWeek = values[dayIdx].trim().toIntOrNull() ?: 1,
                        startSlot = startSlot,
                        endSlot = endSlot,
                        startWeek = startWeek,
                        endWeek = endWeek,
                        weekType = weekType,
                        note = if (noteIdx >= 0 && noteIdx < values.size) values[noteIdx].trim() else ""
                    )
                )
            }

            Result.success(courses)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getSampleCsv(): String {
        return """
name,teacher,classroom,dayOfWeek,startSlot,endSlot,startWeek,endWeek,weekType,note
高等数学,张老师,教学楼101,1,1,2,1,16,all,
大学英语,李老师,教学楼202,2,3,4,1,16,odd,
数据结构,王老师,实验楼301,3,1,2,1,16,all,
体育,赵老师,体育馆,4,5,6,1,16,even,
        """.trimIndent()
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        var current = StringBuilder()
        var inQuotes = false
        for (ch in line) {
            when {
                ch == '"' -> inQuotes = !inQuotes
                ch == ',' && !inQuotes -> {
                    result.add(current.toString())
                    current = StringBuilder()
                }
                else -> current.append(ch)
            }
        }
        result.add(current.toString())
        return result
    }

    private fun parseSlotRange(startRaw: String, endRaw: String): Pair<Int, Int> {
        // If startRaw contains a range like "1-2", split it
        if (startRaw.contains("-")) {
            val parts = startRaw.split("-")
            val start = parts[0].trim().toIntOrNull() ?: 1
            val end = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: (start + 1)
            return Pair(start, end)
        }
        if (endRaw.contains("-")) {
            val parts = endRaw.split("-")
            val start = parts[0].trim().toIntOrNull() ?: 1
            val end = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: (start + 1)
            return Pair(start, end)
        }
        val start = startRaw.toIntOrNull() ?: 1
        var end = endRaw.toIntOrNull() ?: (start + 1)
        if (end < start) end = start
        return Pair(start, end)
    }
}
