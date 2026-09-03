package com.chen.schedule.domain.model

/**
 * 课程颜色调色板:全局唯一来源。
 * 数据层(爬虫)、UI 层(主题、导入)统一从这里取色,避免重复定义。
 */
object CoursePalette {

    val colors: List<Long> = listOf(
        0xFF4CAF50, 0xFF2196F3, 0xFFFF9800, 0xFFE91E63,
        0xFF9C27B0, 0xFF00BCD4, 0xFFFF5722, 0xFF607D8B,
        0xFF795548, 0xFF3F51B5, 0xFF009688, 0xFFCDDC39
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
