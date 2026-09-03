package com.chen.schedule.util

import com.chen.schedule.domain.model.Course
import com.chen.schedule.domain.model.WeekType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

@Serializable
data class CourseJson(
    val name: String,
    val teacher: String = "",
    val classroom: String = "",
    val dayOfWeek: Int = 1,
    val startSlot: Int = 1,
    val endSlot: Int = 2,
    val startWeek: Int = 1,
    val endWeek: Int = 16,
    val weekType: String = "all",
    val color: Long = 0xFF4CAF50,
    val note: String = ""
)

@Serializable
data class CourseImportData(
    val semesterName: String = "",
    val courses: List<CourseJson> = emptyList()
)

object JsonImporter {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun parse(jsonString: String): Result<List<Course>> {
        // Try structured parsing first
        try {
            val data = json.decodeFromString<CourseImportData>(jsonString)
            val courses = data.courses.map { toCourse(it) }
            return Result.success(courses)
        } catch (_: Exception) {}
        // Try flat list
        try {
            val courses = json.decodeFromString<List<CourseJson>>(jsonString).map { toCourse(it) }
            return Result.success(courses)
        } catch (_: Exception) {}
        // Fallback: lenient JsonElement parsing
        return try {
            val element = json.parseToJsonElement(jsonString)
            val coursesJson = when (element) {
                is JsonObject -> element["courses"]?.jsonArray ?: JsonArray(emptyList())
                is JsonArray -> element
                else -> JsonArray(emptyList())
            }
            val courses = coursesJson.mapNotNull { el ->
                try { parseCourseLeniently(el.jsonObject) } catch (_: Exception) { null }
            }
            if (courses.isEmpty()) Result.failure(Exception("未能解析任何课程"))
            else Result.success(courses)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun toCourse(c: CourseJson) = Course(
        name = c.name,
        teacher = c.teacher,
        classroom = c.classroom,
        dayOfWeek = c.dayOfWeek,
        startSlot = c.startSlot,
        endSlot = c.endSlot,
        startWeek = c.startWeek,
        endWeek = c.endWeek,
        weekType = parseWeekType(c.weekType),
        color = c.color,
        note = c.note
    )

    private fun parseWeekType(s: String) = when (s.lowercase()) {
        "odd", "单周" -> WeekType.ODD
        "even", "双周" -> WeekType.EVEN
        else -> WeekType.ALL
    }

    private fun parseCourseLeniently(obj: JsonObject): Course? {
        val name = strField(obj, "name") ?: return null
        val teacher = strField(obj, "teacher") ?: ""
        val classroom = strField(obj, "classroom") ?: ""
        val dayOfWeek = intField(obj, "dayOfWeek") ?: intField(obj, "dayofweek") ?: 1
        val startWeek = intField(obj, "startWeek") ?: intField(obj, "startweek") ?: 1
        val endWeek = intField(obj, "endWeek") ?: intField(obj, "endweek") ?: 16
        val weekType = strField(obj, "weekType") ?: strField(obj, "weektype") ?: "all"
        val color = strField(obj, "color")?.toLongOrNull() ?: 0xFF4CAF50
        val note = strField(obj, "note") ?: ""

        // Handle "1-2" format in startSlot/endSlot
        val startSlotRaw = strField(obj, "startSlot") ?: strField(obj, "startslot") ?: "1"
        val endSlotRaw = strField(obj, "endSlot") ?: strField(obj, "endslot") ?: "2"

        val (startSlot, endSlot) = parseSlotRange(startSlotRaw, endSlotRaw)

        return Course(
            name = name,
            teacher = teacher,
            classroom = classroom,
            dayOfWeek = dayOfWeek.coerceIn(1, 7),
            startSlot = startSlot,
            endSlot = endSlot,
            startWeek = startWeek,
            endWeek = endWeek,
            weekType = parseWeekType(weekType),
            color = color,
            note = note
        )
    }

    private fun strField(obj: JsonObject, key: String): String? {
        val elem = obj[key] ?: return null
        return if (elem is JsonPrimitive) elem.content else null
    }

    private fun intField(obj: JsonObject, key: String): Int? {
        val elem = obj[key] ?: return null
        return when {
            elem is JsonPrimitive && elem.isString -> {
                elem.content.split("-").firstOrNull()?.trim()?.toIntOrNull()
            }
            elem is JsonPrimitive -> elem.content.toIntOrNull()
            else -> null
        }
    }

    /** Parse "1-2" style slot ranges into (start, end) pair */
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
        val start = startRaw.trim().toIntOrNull() ?: 1
        var end = endRaw.trim().toIntOrNull() ?: (start + 1)
        if (end < start) end = start
        return Pair(start, end)
    }

    fun getSampleJson(): String {
        val sample = CourseImportData(
            semesterName = "2025-2026 第一学期",
            courses = listOf(
                CourseJson("高等数学", "张老师", "教学楼101", 1, 1, 2, 1, 16, "all", 0xFF4CAF50),
                CourseJson("大学英语", "李老师", "教学楼202", 2, 3, 4, 1, 16, "odd", 0xFF2196F3),
                CourseJson("数据结构", "王老师", "实验楼301", 3, 1, 2, 1, 16, "all", 0xFFFF9800),
                CourseJson("体育", "赵老师", "体育馆", 4, 5, 6, 1, 16, "even", 0xFFE91E63)
            )
        )
        return json.encodeToString(CourseImportData.serializer(), sample)
    }
}
