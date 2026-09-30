package com.chen.schedule.util

import com.chen.schedule.domain.model.Course
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonNull

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
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }

    /** Any invalid record rejects the preview; no course is silently dropped or corrected. */
    fun parse(jsonString: String): Result<List<Course>> = runCatching {
        val element = json.parseToJsonElement(jsonString.removePrefix("\uFEFF"))
        val rows = when (element) {
            is JsonArray -> element
            is JsonObject -> element["courses"] as? JsonArray ?: error("JSON 必须包含 courses 数组")
            else -> error("JSON 必须为课程数组或包含 courses 的对象")
        }
        require(rows.isNotEmpty()) { "未找到课程" }
        rows.mapIndexed { index, row ->
            try {
                val obj = row as? JsonObject ?: error("课程必须是对象")
                val knownFields = setOf("name", "teacher", "classroom", "dayofweek", "startslot", "endslot", "startweek", "endweek", "weektype", "color", "note")
                val fields = obj.entries.filter { it.key.lowercase() in knownFields && it.value != JsonNull }.associate { (key, value) ->
                    val text = (value as? JsonPrimitive)?.content ?: error("字段 $key 必须是文本或数字")
                    key.lowercase() to text
                }
                CourseImportRules.fromFields(fields)
            } catch (error: Exception) {
                throw IllegalArgumentException("第 ${index + 1} 条课程：${error.message}；未导入任何课程", error)
            }
        }
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
