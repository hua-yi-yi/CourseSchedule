package com.chen.schedule.util
import com.chen.schedule.domain.model.TimeSlot
import java.time.LocalTime

object TimeSlotParser {
    fun parse(text: String): List<TimeSlot> {
        val slots = text.lineSequence().filter { it.isNotBlank() }.mapIndexed { index, line ->
            val parts = line.trim().split(Regex("\\s+"), limit = 3)
            require(parts.size >= 2) { "第 ${index + 1} 行格式错误" }
            val number = parts[0].toIntOrNull()
            require(number != null && number > 0) { "节次编号必须为正整数" }
            val times = parts[1].split("-")
            require(times.size == 2) { "时间范围格式错误" }
            val start = LocalTime.parse(times[0])
            val end = LocalTime.parse(times[1])
            require(start < end) { "结束时间必须晚于开始时间" }
            TimeSlot(slotNumber = number, startTime = start.toString(), endTime = end.toString(),
                name = parts.getOrElse(2) { "第${number}节" })
        }.sortedBy { it.slotNumber }.toList()
        require(slots.isNotEmpty()) { "未找到节次" }
        require(slots.map { it.slotNumber }.distinct().size == slots.size) { "节次编号不能重复" }
        require(slots.zipWithNext().all { (a, b) -> a.endTime <= b.startTime }) { "节次时间不能重叠或倒序" }
        return slots
    }
}
