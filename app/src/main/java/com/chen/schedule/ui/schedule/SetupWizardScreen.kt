package com.chen.schedule.ui.schedule

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chen.schedule.data.repository.SemesterRepository
import com.chen.schedule.data.repository.TimeSchemeRepository
import com.chen.schedule.domain.model.Semester
import com.chen.schedule.domain.model.TimeScheme
import com.chen.schedule.domain.model.TimeSlot
import com.chen.schedule.util.ScheduleStatus
import com.chen.schedule.util.SemesterNameSuggestions
import com.chen.schedule.util.TimeSchemeTemplates
import com.chen.schedule.widget.WidgetUpdater
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

/** 首启「初始设置」向导状态:全部点选即可完成。 */
data class SetupWizardState(
    val name: String = "",
    val suggestions: List<String> = emptyList(),
    val dateMillis: Long? = null,
    val totalWeeks: Int = 16,
    val schemes: List<TimeScheme> = emptyList(),
    val selectedSchemeId: Long? = null,
    /** 选中方案的节次预览(异步加载)。 */
    val schemeSlots: List<TimeSlot> = emptyList(),
    val schemeSlotsLoaded: Boolean = false,
    val saving: Boolean = false,
    val saved: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class SetupWizardViewModel @Inject constructor(
    private val semesterRepository: SemesterRepository,
    private val timeSchemeRepository: TimeSchemeRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _state = MutableStateFlow(SetupWizardState())
    val state: StateFlow<SetupWizardState> = _state.asStateFlow()

    private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    init {
        viewModelScope.launch {
            runCatching { timeSchemeRepository.ensureBuiltInSchemes() }
            val schemes = runCatching { timeSchemeRepository.getAllSchemesDirect() }.getOrDefault(emptyList())
            val suggestions = SemesterNameSuggestions.generate(LocalDate.now())
            // 默认选第一个内置模板,用户可直接点「完成设置」
            val first = schemes.firstOrNull { it.isBuiltIn } ?: schemes.firstOrNull()
            _state.update {
                it.copy(
                    name = suggestions.firstOrNull().orEmpty(),
                    suggestions = suggestions,
                    schemes = schemes,
                    selectedSchemeId = first?.id
                )
            }
            first?.let { loadSchemePreview(it.id) }
        }
    }

    fun updateName(value: String) = _state.update { it.copy(name = value, error = null) }

    fun quickStartDateMillis(offsetWeeks: Int): Long {
        val monday = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val date = monday.plusWeeks(offsetWeeks.toLong())
        return date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    fun applyQuickStartDate(offsetWeeks: Int) =
        _state.update { it.copy(dateMillis = quickStartDateMillis(offsetWeeks), error = null) }

    fun updateDate(millis: Long?) =
        _state.update { it.copy(dateMillis = millis, error = null) }

    fun applyWeeks(weeks: Int) {
        if (weeks in ScheduleStatus.MIN_WEEKS..ScheduleStatus.MAX_WEEKS) {
            _state.update { it.copy(totalWeeks = weeks, error = null) }
        }
    }

    fun selectScheme(schemeId: Long) {
        _state.update {
            it.copy(
                selectedSchemeId = schemeId,
                schemeSlots = emptyList(),
                schemeSlotsLoaded = false,
                error = null
            )
        }
        loadSchemePreview(schemeId)
    }

    private fun loadSchemePreview(schemeId: Long) {
        viewModelScope.launch {
            val slots = runCatching { timeSchemeRepository.getSlotsOfSchemeDirect(schemeId) }
                .getOrDefault(emptyList())
            _state.update {
                if (it.selectedSchemeId == schemeId) {
                    it.copy(schemeSlots = slots, schemeSlotsLoaded = true)
                } else it
            }
        }
    }

    fun formatDate(millis: Long?): String = millis?.let {
        Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate().format(dateFormat)
    } ?: "未选择"

    /** 完成:校验 → 建学期(带所选作息) → 置为当前 → 刷新小组件。 */
    fun complete() {
        val s = _state.value
        if (s.saving) return
        val name = s.name.trim()
        val error = when {
            name.isBlank() -> "请选择或填写学期名称"
            s.dateMillis == null || s.dateMillis <= 0L -> "请选择开学日期"
            s.totalWeeks !in ScheduleStatus.MIN_WEEKS..ScheduleStatus.MAX_WEEKS ->
                "总周数应为 ${ScheduleStatus.MIN_WEEKS}–${ScheduleStatus.MAX_WEEKS} 的整数"
            else -> null
        }
        if (error != null) {
            _state.update { it.copy(error = error) }
            return
        }
        val schemeId = s.selectedSchemeId ?: TimeScheme.LEGACY_ID
        val dateMillis = s.dateMillis!!
        val totalWeeks = s.totalWeeks
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            try {
                val slots = runCatching { timeSchemeRepository.getSlotsOfSchemeDirect(schemeId) }
                    .getOrDefault(emptyList())
                if (slots.isEmpty()) {
                    _state.update { it.copy(saving = false, error = "所选作息没有节次,请换一个方案") }
                    return@launch
                }
                val newId = semesterRepository.insert(
                    Semester(
                        id = 0,
                        name = name,
                        startDate = dateMillis,
                        totalWeeks = totalWeeks,
                        isCurrent = false,
                        schemeId = schemeId
                    )
                )
                semesterRepository.setCurrentSemester(newId)
                WidgetUpdater.refreshAll(context)
                _state.update { it.copy(saving = false, saved = true) }
            } catch (e: Exception) {
                _state.update {
                    it.copy(saving = false, error = "设置失败:${e.message ?: "未知错误"}")
                }
            }
        }
    }
}

/**
 * 首启「初始设置」向导:学期名称、开学日期、总周数、作息方案四步全部点选,一键完成。
 * 从主界面空状态进入;完成后返回课表页即可开始添加课程。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SetupWizardScreen(
    onDone: () -> Unit,
    onCancel: () -> Unit,
    viewModel: SetupWizardViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var showDatePicker by remember { mutableStateOf(false) }
    var showCustomName by remember { mutableStateOf(false) }

    LaunchedEffect(state.saved) { if (state.saved) onDone() }

    if (showDatePicker) {
        val initial = state.dateMillis ?: System.currentTimeMillis()
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initial)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.updateDate(pickerState.selectedDateMillis)
                    showDatePicker = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("取消") } }
        ) { DatePicker(state = pickerState) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("初始设置", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(
                "全部点选即可完成初始设置,之后随时可在「学期与作息」中修改。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))

            // ① 学期名称
            Text("① 学期名称", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                state.suggestions.forEach { suggestion ->
                    FilterChip(
                        selected = state.name == suggestion,
                        onClick = { viewModel.updateName(suggestion) },
                        label = { Text(suggestion) }
                    )
                }
            }
            TextButton(onClick = { showCustomName = !showCustomName }) {
                Text(if (showCustomName) "收起自定义名称" else "自定义名称")
            }
            if (showCustomName) {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = viewModel::updateName,
                    label = { Text("学期名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(Modifier.height(12.dp))

            // ② 开学日期
            Text("② 开学日期", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("上周一" to -1, "本周一" to 0, "下周一" to 1).forEach { (label, offset) ->
                    FilterChip(
                        selected = state.dateMillis != null &&
                            state.dateMillis == viewModel.quickStartDateMillis(offset),
                        onClick = { viewModel.applyQuickStartDate(offset) },
                        label = { Text(label) }
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showDatePicker = true },
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.DateRange,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        viewModel.formatDate(state.dateMillis),
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (state.dateMillis == null) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "选日期",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            // ③ 总周数
            Text("③ 总周数", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(16, 18, 20, 24).forEach { weeks ->
                    FilterChip(
                        selected = state.totalWeeks == weeks,
                        onClick = { viewModel.applyWeeks(weeks) },
                        label = { Text("$weeks 周") }
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            // ④ 作息方案
            Text("④ 作息方案", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            if (state.schemes.isEmpty()) {
                Text(
                    "正在准备作息模板…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            state.schemes.forEach { scheme ->
                WizardSchemeRow(
                    scheme = scheme,
                    selected = state.selectedSchemeId == scheme.id,
                    slots = if (state.selectedSchemeId == scheme.id) state.schemeSlots else emptyList(),
                    slotsLoaded = state.selectedSchemeId == scheme.id && state.schemeSlotsLoaded,
                    onClick = { viewModel.selectScheme(scheme.id) }
                )
            }

            state.error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = viewModel::complete,
                enabled = !state.saving,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (state.saving) "正在创建…" else "完成设置")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth()
            ) { Text("稍后再说") }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun WizardSchemeRow(
    scheme: TimeScheme,
    selected: Boolean,
    slots: List<TimeSlot>,
    slotsLoaded: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = selected, onClick = onClick)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    scheme.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    wizardSchemeSubtitle(scheme),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (selected && slotsLoaded) {
                    Spacer(Modifier.height(4.dp))
                    if (slots.isEmpty()) {
                        Text(
                            "该方案暂无节次",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else {
                        val preview = slots.sortedBy { it.slotNumber }
                            .take(3)
                            .joinToString("  ") { "第${it.slotNumber}节 ${it.startTime}" } +
                            if (slots.size > 3) " …共${slots.size}节" else ""
                        Text(
                            preview,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

private fun wizardSchemeSubtitle(scheme: TimeScheme): String = when {
    scheme.isLegacy -> "升级前保存的作息"
    scheme.isBuiltIn -> "内置模板 · ${if (scheme.season == TimeSchemeTemplates.SEASON_WINTER) "冬季" else "夏季"}作息"
    else -> "自定义方案"
}
