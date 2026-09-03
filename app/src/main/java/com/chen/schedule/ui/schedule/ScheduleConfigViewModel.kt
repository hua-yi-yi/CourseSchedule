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
            val df = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val dateStr = "${s.semesterYear}-${s.semesterMonth.padStart(2, '0')}-${s.semesterDay.padStart(2, '0')}"
            val startDate = try {
                df.parse(dateStr)?.time ?: System.currentTimeMillis()
            } catch (_: Exception) { System.currentTimeMillis() }

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
        }
    }

    fun deleteTimeSlot(timeSlot: TimeSlot) {
        viewModelScope.launch {
            timeSlotRepository.delete(timeSlot)
        }
    }

    fun applyPreset(preset: String) {
        viewModelScope.launch {
            timeSlotRepository.deleteAll()
            val slots = when (preset) {
                "45min" -> generate45MinSlots()
                "40min" -> generate40MinSlots()
                else -> emptyList()
            }
            timeSlotRepository.insertAll(slots)
        }
    }

    private fun generate45MinSlots(): List<TimeSlot> {
        val startTimes = listOf(
            "08:00", "08:55", "10:00", "10:55",
            "14:00", "14:55", "16:00", "16:55",
            "19:00", "19:55"
        )
        return startTimes.mapIndexed { i, start ->
            val endParts = start.split(":")
            val h = endParts[0].toInt()
            val m = endParts[1].toInt() + 45
            val endH = h + m / 60
            val endM = m % 60
            TimeSlot(
                slotNumber = i + 1,
                startTime = start,
                endTime = String.format("%02d:%02d", endH, endM),
                name = "第${i + 1}节"
            )
        }
    }

    fun importTimeSlotsFromText(text: String) {
        viewModelScope.launch {
            try {
                val slots = parseTimeSlotText(text)
                if (slots.isEmpty()) {
                    _state.update { it.copy(message = "未能解析出任何节次，请检查格式", isError = true) }
                    return@launch
                }
                timeSlotRepository.deleteAll()
                timeSlotRepository.insertAll(slots)
                _state.update { it.copy(timeSlots = slots, message = "成功导入 ${slots.size} 个节次", isError = false) }
            } catch (e: Exception) {
                _state.update { it.copy(message = "解析失败: ${e.message}", isError = true) }
            }
        }
    }

    fun clearMessage() {
        _state.update { it.copy(message = "", isError = false) }
    }

    private fun parseTimeSlotText(text: String): List<TimeSlot> {
        val slots = mutableListOf<TimeSlot>()
        for (line in text.lines()) {
            val trimmed = line.trim()
            if (trimmed.isBlank()) continue
            // Format: "1 08:00-08:45" or "1 08:00-08:45 第一节课"
            val parts = trimmed.split("\\s+".toRegex(), limit = 3)
            if (parts.size < 2) continue
            val slotNumber = parts[0].toIntOrNull() ?: continue
            val timeRange = parts[1]
            val timeParts = timeRange.split("-")
            if (timeParts.size != 2) continue
            val startTime = timeParts[0].trim()
            val endTime = timeParts[1].trim()
            if (!startTime.matches("\\d{2}:\\d{2}".toRegex()) || !endTime.matches("\\d{2}:\\d{2}".toRegex())) continue
            val name = if (parts.size >= 3) parts[2].trim() else "第${slotNumber}节"
            slots.add(TimeSlot(slotNumber = slotNumber, startTime = startTime, endTime = endTime, name = name))
        }
        return slots.sortedBy { it.slotNumber }
    }

    private fun generate40MinSlots(): List<TimeSlot> {
        val startTimes = listOf(
            "08:00", "08:50", "09:50", "10:40",
            "14:00", "14:50", "15:50", "16:40",
            "19:00", "19:50"
        )
        return startTimes.mapIndexed { i, start ->
            val endParts = start.split(":")
            val h = endParts[0].toInt()
            val m = endParts[1].toInt() + 40
            val endH = h + m / 60
            val endM = m % 60
            TimeSlot(
                slotNumber = i + 1,
                startTime = start,
                endTime = String.format("%02d:%02d", endH, endM),
                name = "第${i + 1}节"
            )
        }
    }
}
