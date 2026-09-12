package com.chen.schedule.ui.schedule

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

/**
 * 首启「初始设置」向导状态:分页式,每页一项任务。
 * 总周数不做选择,固定取上限([ScheduleStatus.MAX_WEEKS])。
 */
data class SetupWizardState(
    val name: String = "",
    val suggestions: List<String> = emptyList(),
    val dateMillis: Long? = null,
    val totalWeeks: Int = ScheduleStatus.MAX_WEEKS,
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
            // 默认选第一个内置模板,用户可直接一路「下一步」完成
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

    /** 完成:校验 → 建学期(带所选作息,总周数取上限) → 置为当前 → 刷新小组件。 */
    fun complete() {
        val s = _state.value
        if (s.saving) return
        val name = s.name.trim()
        val error = when {
            name.isBlank() -> "请选择或填写学期名称"
            s.dateMillis == null || s.dateMillis <= 0L -> "请选择开学日期"
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
 * 首启「初始设置」向导:分页式,每页一项任务——
 * 第 1 页选学期名称,第 2 页选开学日期,第 3 页选作息方案,第 4 页选择是否立即导入课表。
 * 「完成设置」一键建好学期;若选择导入,则直接进入导入页(此时学期已存在,导入结果写入当前学期)。
 * 每页内容在可用空间内垂直居中,避免大片空白;作息页内容较长,单独可滚动。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupWizardScreen(
    onDone: () -> Unit,
    onGoImport: () -> Unit,
    onGoHaustImport: () -> Unit,
    onCancel: () -> Unit,
    viewModel: SetupWizardViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var step by remember { mutableIntStateOf(0) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showCustomName by remember { mutableStateOf(false) }
    // 第 4 页的导入方式(与主页导入页一致):0 = 河南科技大学教务导入,1 = 其他方式
    var importMode by remember { mutableIntStateOf(0) }
    /** 选择「先不导入」:完成设置后仅返回课表。 */
    var skipImport by remember { mutableStateOf(false) }

    LaunchedEffect(state.saved) {
        if (state.saved) {
            when {
                skipImport -> onDone()
                importMode == 0 -> onGoHaustImport()
                else -> onGoImport()
            }
        }
    }

    val stepTitles = remember { listOf("学期名称", "开学日期", "作息方案", "导入课表") }

    // 第一步按返回退出向导,其余步骤先回上一页
    BackHandler { if (step > 0) step-- else onCancel() }

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
                title = {
                    Text(
                        "初始设置 ${step + 1}/${stepTitles.size} · ${stepTitles[step]}",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { if (step > 0) step-- else onCancel() }) {
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
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(8.dp))
            // 步骤圆点 + 进度
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                stepTitles.indices.forEach { index ->
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                if (index == step) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                            )
                    )
                    if (index != stepTitles.lastIndex) Spacer(Modifier.width(6.dp))
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "第 ${step + 1} 步",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 任务区:内容少时垂直居中,作息页内容长时可滚动(顶部对齐)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                when (step) {
                    0 -> NameStep(
                        state = state,
                        showCustomName = showCustomName,
                        onToggleCustomName = { showCustomName = !showCustomName },
                        onPickSuggestion = {
                            showCustomName = false
                            viewModel.updateName(it)
                        },
                        onCustomNameChange = viewModel::updateName
                    )
                    1 -> DateStep(
                        state = state,
                        onQuickPick = viewModel::applyQuickStartDate,
                        onOpenDatePicker = { showDatePicker = true },
                        formatDate = viewModel::formatDate,
                        quickStartMillis = viewModel::quickStartDateMillis
                    )
                    2 -> Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(top = 16.dp, bottom = 8.dp)
                    ) {
                        SchemeStep(
                            state = state,
                            onSelectScheme = viewModel::selectScheme
                        )
                    }
                    else -> ImportStep(
                        importMode = importMode,
                        onPickMode = { importMode = it }
                    )
                }
            }

            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(6.dp))
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (step > 0) {
                    OutlinedButton(
                        onClick = { step-- },
                        enabled = !state.saving,
                        modifier = Modifier.weight(1f)
                    ) { Text("上一步") }
                }
                val pickedDate = state.dateMillis
                Button(
                    onClick = { if (step < stepTitles.lastIndex) step++ else viewModel.complete() },
                    enabled = when (step) {
                        0 -> state.name.isNotBlank()
                        1 -> pickedDate != null && pickedDate > 0L
                        else -> state.selectedSchemeId != null
                    } && !state.saving,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        when {
                            state.saving -> "正在创建…"
                            step < stepTitles.lastIndex -> "下一步"
                            importMode == 0 -> "完成并进入教务导入"
                            else -> "完成并前往导入页"
                        }
                    )
                }
            }
            if (step == stepTitles.lastIndex) {
                TextButton(
                    onClick = {
                        skipImport = true
                        viewModel.complete()
                    },
                    enabled = !state.saving,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "先不导入,直接完成设置",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

/** 整行选项卡:单选 + 标题 + 可选副标题,点整行即选中。 */
@Composable
private fun OptionCard(
    title: String,
    subtitle: String?,
    selected: Boolean,
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
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = selected, onClick = onClick)
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** 第 1 页:选学期名称(推荐选项卡 + 可选自定义)。 */
@Composable
private fun NameStep(
    state: SetupWizardState,
    showCustomName: Boolean,
    onToggleCustomName: () -> Unit,
    onPickSuggestion: (String) -> Unit,
    onCustomNameChange: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("选择学期名称", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(
            "按当前日期推荐,点选即可;也可以自定义。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(10.dp))
        state.suggestions.forEachIndexed { index, suggestion ->
            OptionCard(
                title = suggestion,
                subtitle = if (index == 0) "推荐 · 与当前日期匹配" else null,
                selected = state.name == suggestion && !showCustomName,
                onClick = { onPickSuggestion(suggestion) }
            )
        }
        OptionCard(
            title = "自定义名称",
            subtitle = null,
            selected = showCustomName,
            onClick = onToggleCustomName
        )
        if (showCustomName) {
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = state.name,
                onValueChange = onCustomNameChange,
                label = { Text("学期名称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/** 第 2 页:选开学日期(快捷周 chips + 完整日历)。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DateStep(
    state: SetupWizardState,
    onQuickPick: (Int) -> Unit,
    onOpenDatePicker: () -> Unit,
    formatDate: (Long?) -> String,
    quickStartMillis: (Int) -> Long
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("选择开学日期", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(
            "学期从周一开始;不确定就选「本周一」,之后可再调整。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        val selectedDate = state.dateMillis
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("上周一" to -1, "本周一" to 0, "下周一" to 1).forEach { (label, offset) ->
                FilterChip(
                    selected = selectedDate != null && selectedDate == quickStartMillis(offset),
                    onClick = { onQuickPick(offset) },
                    label = { Text(label) }
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenDatePicker),
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.DateRange,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    formatDate(state.dateMillis),
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
        Spacer(Modifier.height(6.dp))
        Text(
            "也可以点开日历,选择第 1 周的具体周一。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 第 3 页:选作息方案(内置两套按执行日期区分 + 已有自定义方案)。 */
@Composable
private fun SchemeStep(
    state: SetupWizardState,
    onSelectScheme: (Long) -> Unit
) {
    Text("选择作息方案", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(4.dp))
    Text(
        "两套内置模板按执行日期区分;完成后可在「学期与作息」中修改。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(10.dp))
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
            onClick = { onSelectScheme(scheme.id) }
        )
    }
}

/** 第 4 页:选择导入方式(与主页「导入课表」一致的二选一),完成设置后直达所选方式。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImportStep(
    importMode: Int,
    onPickMode: (Int) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("导入课表", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(
            "与主页「导入课表」一致;完成设置后将直接进入所选方式。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(10.dp))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = importMode == 0,
                onClick = { onPickMode(0) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
            ) { Text("河南科技大学导入", fontSize = 13.sp) }
            SegmentedButton(
                selected = importMode == 1,
                onClick = { onPickMode(1) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
            ) { Text("其他方式", fontSize = 13.sp) }
        }
        Spacer(Modifier.height(10.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(
                containerColor = if (importMode == 0) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                else MaterialTheme.colorScheme.surface
            )
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    if (importMode == 0) "河南科技大学教务系统导入" else "其他方式导入",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    if (importMode == 0) {
                        "完成设置后进入 VPN 教务,登录并读取课表,自动导入当前学期。"
                    } else {
                        "完成设置后前往导入页:AI 截图识别、粘贴文本、JSON/CSV 文件或正方教务系统。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
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
    scheme.isBuiltIn -> "内置模板 · ${TimeSchemeTemplates.noteFor(scheme.season) ?: "通用作息"}"
    else -> "自定义方案"
}
