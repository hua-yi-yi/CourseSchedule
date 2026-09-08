package com.chen.schedule.ui.schedule

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chen.schedule.data.repository.SemesterRepository
import com.chen.schedule.data.repository.TimeSlotRepository
import com.chen.schedule.domain.model.Semester
import com.chen.schedule.domain.model.TimeSlot
import com.chen.schedule.widget.WidgetUpdater
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class ScheduleConfigState(
    val timeSlots: List<TimeSlot> = emptyList(),
    val semester: Semester? = null,
    val semesterName: String = "",
    val semesterYear: String = "",
    val semesterMonth: String = "",
    val semesterDay: String = "",
    val semesterTotalWeeks: Int = 16,
    val currentWeekHint: String = "",
    val message: String = "",
    val isError: Boolean = false
)

@HiltViewModel
class ScheduleConfigViewModel @Inject constructor(
    private val timeSlotRepository: TimeSlotRepository,
    private val semesterRepository: SemesterRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _state = MutableStateFlow(ScheduleConfigState())
    val state: StateFlow<ScheduleConfigState> = _state.asStateFlow()

    init {
        loadTimeSlots()
        loadCurrentSemester()
    }

    private fun loadTimeSlots() {
        viewModelScope.launch {
            timeSlotRepository.getAllTimeSlots().collect { slots ->
                _state.update { it.copy(timeSlots = slots) }
            }
        }
    }

    private fun loadCurrentSemester() {
        viewModelScope.launch {
            semesterRepository.getCurrentSemester()?.let { sem ->
                val df = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val dateStr = df.format(Date(sem.startDate))
                val parts = dateStr.split("-")
                _state.update {
                    it.copy(
                        semester = sem,
                        semesterName = sem.name,
                        semesterYear = parts.getOrElse(0) { "" },
                        semesterMonth = parts.getOrElse(1) { "" },
                        semesterDay = parts.getOrElse(2) { "" },
                        semesterTotalWeeks = sem.totalWeeks
                    )
                }
            }
        }
    }

    fun updateSemesterName(name: String) = _state.update { it.copy(semesterName = name) }
    fun updateSemesterYear(y: String) = _state.update { it.copy(semesterYear = y) }
    fun updateSemesterMonth(m: String) = _state.update { it.copy(semesterMonth = m) }
    fun updateSemesterDay(d: String) = _state.update { it.copy(semesterDay = d) }
    fun updateSemesterTotalWeeks(weeks: Int) = _state.update { it.copy(semesterTotalWeeks = weeks) }
    fun updateCurrentWeekHint(w: String) = _state.update { it.copy(currentWeekHint = w) }

    fun fillStartDateFromWeek() {
        val week = _state.value.currentWeekHint.toIntOrNull() ?: return
        if (week < 1 || week > _state.value.semesterTotalWeeks) return
        val startMillis = System.currentTimeMillis() - (week - 1) * 7L * 86400000
        val df = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val dateStr = df.format(Date(startMillis))
        val parts = dateStr.split("-")
        _state.update {
            it.copy(
                semesterYear = parts[0],
                semesterMonth = parts[1],
                semesterDay = parts[2]
            )
        }
    }

    fun saveSemester() {
        viewModelScope.launch {
            val s = _state.value
            val startDate = try {
                require(s.semesterTotalWeeks in 1..53) { "总周数应为 1–53" }
                java.time.LocalDate.of(s.semesterYear.toInt(), s.semesterMonth.toInt(), s.semesterDay.toInt())
                    .atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            } catch (_: Exception) {
                _state.update { it.copy(message = "请输入有效日期及 1–53 的总周数", isError = true) }
                return@launch
            }

            val sem = Semester(
                id = s.semester?.id ?: 0,
                name = s.semesterName.ifBlank { "默认学期" },
                startDate = startDate,
                totalWeeks = s.semesterTotalWeeks,
                isCurrent = true
            )
            if (s.semester != null) {
                semesterRepository.update(sem)
                semesterRepository.setCurrentSemester(sem.id)
            } else {
                val newId = semesterRepository.insert(sem)
                semesterRepository.setCurrentSemester(newId)
                _state.update { it.copy(semester = sem.copy(id = newId)) }
            }
            WidgetUpdater.refreshAll(context)
        }
    }

    fun addTimeSlot() {
        viewModelScope.launch {
            val slots = _state.value.timeSlots
            val nextNumber = (slots.maxOfOrNull { it.slotNumber } ?: 0) + 1
            val lastSlot = slots.lastOrNull()
            val startTime = if (lastSlot != null) {
                val parts = lastSlot.endTime.split(":")
                val hour = parts.getOrElse(0) { "08" }.toIntOrNull() ?: 8
                val min = parts.getOrElse(1) { "00" }.toIntOrNull() ?: 0
                val newMin = min + 10
                val newHour = if (newMin >= 60) hour + 1 else hour
                String.format("%02d:%02d", newHour % 24, newMin % 60)
            } else "08:00"

            val endParts = startTime.split(":")
            val endHour = endParts.getOrElse(0) { "08" }.toIntOrNull() ?: 8
            val endMin = endParts.getOrElse(1) { "00" }.toIntOrNull() ?: 0
            val endTimeMin = endMin + 45
            val endTimeHour = if (endTimeMin >= 60) endHour + 1 else endHour
            val endTime = String.format("%02d:%02d", endTimeHour % 24, endTimeMin % 60)

            timeSlotRepository.insert(
                TimeSlot(
                    slotNumber = nextNumber,
                    startTime = startTime,
                    endTime = endTime,
                    name = "第${nextNumber}节"
                )
            )
            WidgetUpdater.refreshAll(context)
        }
    }

    fun deleteTimeSlot(timeSlot: TimeSlot) {
        viewModelScope.launch {
            timeSlotRepository.delete(timeSlot)
            WidgetUpdater.refreshAll(context)
        }
    }

    /** 切换作息套别:summer / winter. */
    fun applyTimeSlotSeason(season: String) {
        viewModelScope.launch {
            val slots = when (season) {
                "summer" -> generateSummerSlots()
                "winter" -> generateWinterSlots()
                else -> return@launch
            }
            timeSlotRepository.replaceAll(slots)
            WidgetUpdater.refreshAll(context)
        }
    }

    private fun generateSummerSlots(): List<TimeSlot> = buildDefaultSlots(SeasonConfig.SUMMER)

    private fun generateWinterSlots(): List<TimeSlot> = buildDefaultSlots(SeasonConfig.WINTER)

    fun importTimeSlotsFromText(text: String) {
        viewModelScope.launch {
            try {
                val slots = parseTimeSlotText(text)
                if (slots.isEmpty()) {
                    _state.update { it.copy(message = "未能解析出任何节次，请检查格式", isError = true) }
                    return@launch
                }
                timeSlotRepository.replaceAll(slots)
                WidgetUpdater.refreshAll(context)
                _state.update { it.copy(timeSlots = slots, message = "成功导入 ${slots.size} 个节次", isError = false) }
            } catch (e: Exception) {
                _state.update { it.copy(message = "解析失败: ${e.message}", isError = true) }
            }
        }
    }

    fun clearMessage() {
        _state.update { it.copy(message = "", isError = false) }
    }

    private fun parseTimeSlotText(text: String): List<TimeSlot> =
        com.chen.schedule.util.TimeSlotParser.parse(text)

}

/**
 * 两套作息模板。
 * - 夏季：下午起 14:30，晚上起 19:30（5/1 起执行）
 * - 冬季：下午起 14:00，晚上起 19:00（10/1 起执行）
 * 每套 12 节。
 */
private fun buildDefaultSlots(config: SeasonConfig): List<TimeSlot> {
    val startTimes = config.startTimes
    return startTimes.mapIndexed { i, start ->
        val parts = start.split(":")
        val hour = parts[0].toInt()
        val minute = parts[1].toInt()
        val durationMinutes = 45
        val total = minute + durationMinutes
        val endH = hour + total / 60
        val endM = total % 60
        TimeSlot(
            slotNumber = i + 1,
            startTime = start,
            endTime = String.format("%02d:%02d", endH, endM),
            name = "第${i + 1}节",
            season = config.seasonValue
        )
    }
}

/**
 * 作息套别配置。每套对应一组固定的开始时间与节次数量。
 * seasonalValue: 对应 TimeSlot.season 存储值。
 */
private enum class SeasonConfig(
    val seasonValue: Int,
    val label: String,
    val startTimes: List<String>
) {
    SUMMER(
        seasonValue = 1,
        label = "夏季作息",
        startTimes = listOf(
            "08:00", "08:55", "10:00", "10:55",
            "14:30", "15:20", "16:25", "17:20", "18:10",
            "19:30", "20:20", "21:10"
        )
    ),
    WINTER(
        seasonValue = 2,
        label = "冬季作息",
        startTimes = listOf(
            "08:00", "08:55", "10:00", "10:55",
            "14:00", "14:50", "15:55", "16:50", "17:40",
            "19:00", "19:50", "20:40"
        )
    );

    companion object {
        fun byValue(value: Int): SeasonConfig =
            entries.firstOrNull { it.seasonValue == value } ?: SUMMER
    }
}
