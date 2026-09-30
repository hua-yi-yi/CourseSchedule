package com.chen.schedule.util

import com.chen.schedule.domain.model.Course

object CsvImporter {
    private val aliases = mapOf(
        "name" to listOf("name", "课程名称", "课程名"),
        "teacher" to listOf("teacher", "教师", "老师"),
        "classroom" to listOf("classroom", "教室", "地点"),
        "dayofweek" to listOf("dayofweek", "day", "星期", "星期几"),
        "startslot" to listOf("startslot", "开始节次", "起始节次"),
        "endslot" to listOf("endslot", "结束节次"),
        "startweek" to listOf("startweek", "起始周"),
        "endweek" to listOf("endweek", "结束周"),
        "weektype" to listOf("weektype", "周类型"),
        "note" to listOf("note", "备注")
    )

    fun parse(csvString: String): Result<List<Course>> = runCatching {
        val records = parseRecords(csvString.removePrefix("\uFEFF"))
        require(records.isNotEmpty()) { "CSV 为空" }
        val columns = records.first().map { it.trim().lowercase() }
        val indices = aliases.mapValues { (_, names) ->
            val matches = columns.indices.filter { columns[it] in names }
            require(matches.size <= 1) { "CSV 表头包含重复字段" }
            matches.singleOrNull()
        }
        require(indices["name"] != null && indices["dayofweek"] != null) { "CSV 必须包含课程名称和星期列" }
        val courses = records.drop(1).mapIndexed { index, values ->
            try {
                require(values.size <= columns.size) { "字段数量超过表头，请将带逗号的内容用双引号包裹" }
                val fields = indices.mapNotNull { (key, column) ->
                    column?.let { values.getOrNull(it) }?.takeIf { it.isNotBlank() }?.let { key to it }
                }.toMap()
                CourseImportRules.fromFields(fields)
            } catch (error: Exception) {
                throw IllegalArgumentException("第 ${index + 1} 条课程：${error.message}；未导入任何课程", error)
            }
        }
        require(courses.isNotEmpty()) { "未找到课程" }
        courses
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

    /** CSV records can contain escaped quotes, commas and embedded newlines. */
    private fun parseRecords(text: String): List<List<String>> {
        val records = mutableListOf<List<String>>()
        val fields = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var closedQuote = false
        var index = 0
        fun finishField() {
            fields += field.toString()
            field.setLength(0)
            closedQuote = false
        }
        fun finishRecord() {
            finishField()
            if (fields.any { it.isNotBlank() }) records += fields.toList()
            fields.clear()
        }
        while (index < text.length) {
            val ch = text[index]
            if (quoted) {
                if (ch == '"') {
                    if (text.getOrNull(index + 1) == '"') { field.append('"'); index++ }
                    else { quoted = false; closedQuote = true }
                } else field.append(ch)
            } else when (ch) {
                '"' -> {
                    require(!closedQuote && field.isBlank()) { "CSV 双引号位置无效" }
                    field.setLength(0)
                    quoted = true
                }
                ',' -> finishField()
                '\r', '\n' -> {
                    finishRecord()
                    if (ch == '\r' && text.getOrNull(index + 1) == '\n') index++
                }
                else -> {
                    require(!closedQuote || ch.isWhitespace()) { "CSV 闭合引号后有无效内容" }
                    if (!closedQuote) field.append(ch)
                }
            }
            index++
        }
        require(!quoted) { "CSV 双引号未闭合" }
        if (fields.isNotEmpty() || field.isNotEmpty() || closedQuote) finishRecord()
        return records
    }
}
