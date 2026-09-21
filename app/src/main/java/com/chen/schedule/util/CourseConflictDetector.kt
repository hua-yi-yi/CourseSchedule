package com.chen.schedule.util

import com.chen.schedule.domain.model.Course

/**
 * 课程排课冲突检测工具。
 *
 * 判定两门课程是否在同一时间维度发生重叠：
 * 1. 同一星期几 (dayOfWeek 相同)
 * 2. 节次范围有交集 (max(startSlot) <= min(endSlot))
 * 3. 存在至少一个共同生效的教学周 (考虑 startWeek..endWeek 及单双周规则)
 */
object CourseConflictDetector {

    data class CourseConflict(
        val existingCourse: Course,
        val overlappingWeeks: List<Int>,
        val overlapStartSlot: Int,
        val overlapEndSlot: Int
    ) {
        /** 冲突节次描述，如 "第 1-2 节" 或 "第 3 节" */
        val slotDescription: String
            get() = if (overlapStartSlot == overlapEndSlot) {
                "第 $overlapStartSlot 节"
            } else {
                "第 $overlapStartSlot-$overlapEndSlot 节"
            }

        /** 冲突周次描述，如 "第 1-8 周" 或 "第 1, 3, 5 周" */
        val weekDescription: String
            get() {
                if (overlappingWeeks.isEmpty()) return ""
                // 检查是否为连续整数
                val isContinuous = overlappingWeeks.zipWithNext().all { (a, b) -> b == a + 1 }
                return if (isContinuous && overlappingWeeks.size > 1) {
                    "第 ${overlappingWeeks.first()}-${overlappingWeeks.last()} 周"
                } else if (overlappingWeeks.size <= 4) {
                    "第 ${overlappingWeeks.joinToString(", ")} 周"
                } else {
                    "第 ${overlappingWeeks.first()}..${overlappingWeeks.last()} 周 (共 ${overlappingWeeks.size} 周)"
                }
            }
    }

    /**
     * 检测两门课程是否存在冲突。如果存在，返回冲突详情；否则返回 null。
     */
    fun checkConflict(a: Course, b: Course): CourseConflict? {
        // 排除同一门课程自身
        if (a.id != 0L && a.id == b.id) return null
        if (a.dayOfWeek != b.dayOfWeek) return null

        val slotStart = maxOf(a.startSlot, b.startSlot)
        val slotEnd = minOf(a.endSlot, b.endSlot)
        if (slotStart > slotEnd) return null

        val weekStart = maxOf(a.startWeek, b.startWeek)
        val weekEnd = minOf(a.endWeek, b.endWeek)
        if (weekStart > weekEnd) return null

        val commonWeeks = (weekStart..weekEnd).filter { week ->
            a.appliesToWeek(week) && b.appliesToWeek(week)
        }

        if (commonWeeks.isEmpty()) return null

        return CourseConflict(
            existingCourse = b,
            overlappingWeeks = commonWeeks,
            overlapStartSlot = slotStart,
            overlapEndSlot = slotEnd
        )
    }

    /**
     * 在一组既有课程中查找与 [target] 存在排课冲突的所有课程。
     */
    fun findConflicts(target: Course, existingCourses: List<Course>): List<CourseConflict> {
        return existingCourses.mapNotNull { existing ->
            checkConflict(target, existing)
        }
    }

    /**
     * 课程聚类模型：代表同一时段的课程簇。
     * [primaryCourse] 为当前在课表格面上展示的代表课程；
     * [allCourses] 包含该时段所有重叠的课程列表。
     */
    data class CourseCluster(
        val primaryCourse: Course,
        val allCourses: List<Course>
    ) {
        val isOverlapping: Boolean get() = allCourses.size > 1
        val overlapCount: Int get() = allCourses.size
    }

    /**
     * 将同一天的课程按节次重叠关系进行连通图聚类。
     * 若两门课程节次有交集，则归入同一个聚类。
     */
    fun groupOverlappingCourses(courses: List<Course>): List<List<Course>> {
        if (courses.isEmpty()) return emptyList()
        val visited = BooleanArray(courses.size)
        val clusters = mutableListOf<List<Course>>()

        fun overlaps(a: Course, b: Course): Boolean =
            maxOf(a.startSlot, b.startSlot) <= minOf(a.endSlot, b.endSlot)

        for (i in courses.indices) {
            if (visited[i]) continue
            val cluster = mutableListOf<Course>()
            val queue = ArrayDeque<Int>()
            queue.add(i)
            visited[i] = true
            while (queue.isNotEmpty()) {
                val currIdx = queue.removeFirst()
                cluster.add(courses[currIdx])
                for (j in courses.indices) {
                    if (!visited[j] && overlaps(courses[currIdx], courses[j])) {
                        visited[j] = true
                        queue.add(j)
                    }
                }
            }
            clusters.add(cluster.sortedWith(compareBy({ it.startSlot }, { it.endSlot }, { it.id })))
        }
        return clusters.sortedBy { it.first().startSlot }
    }

    /**
     * 将课程列表按星期和节次聚类，并根据用户偏好 [preferredCourseIds] 选定当前展示的 primaryCourse。
     */
    fun resolveClusters(
        courses: List<Course>,
        preferredCourseIds: Set<Long> = emptySet()
    ): List<CourseCluster> {
        val byDay = courses.groupBy { it.dayOfWeek }
        val result = mutableListOf<CourseCluster>()
        for ((_, dayCourses) in byDay) {
            val clusters = groupOverlappingCourses(dayCourses)
            for (clusterCourses in clusters) {
                val primary = clusterCourses.find { it.id in preferredCourseIds } ?: clusterCourses.first()
                result.add(CourseCluster(primaryCourse = primary, allCourses = clusterCourses))
            }
        }
        return result
    }
}
