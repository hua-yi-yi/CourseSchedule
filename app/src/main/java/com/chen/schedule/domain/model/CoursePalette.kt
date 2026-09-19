package com.chen.schedule.domain.model

/**
 * 课程颜色调色板:全局唯一来源。
 * 数据层(爬虫)、UI 层(主题、导入)统一从这里取色,避免重复定义。
 */
object CoursePalette {

    val colors: List<Long> = listOf(
        0xFFF27A7D, 0xFF4F95FF, 0xFF36C18C, 0xFFFFA238,
        0xFF9872F5, 0xFF1CB4C6, 0xFFF05D8A, 0xFF6078EA,
        0xFF48BB78, 0xFFB76EF0, 0xFFF5B025, 0xFF5B86A5
    )

    /**
     * 为课程列表分配颜色:同名课程颜色稳定一致(与导入顺序无关的结果之一)。
     */
    fun assignColors(courses: List<Course>): List<Course> {
        val nameToColor = mutableMapOf<String, Long>()
        var idx = 0
        return courses.map { course ->
            val color = nameToColor.getOrPut(course.name) {
                colors[idx++ % colors.size]
            }
            course.copy(color = color)
        }
    }
}
