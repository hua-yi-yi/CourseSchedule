package com.chen.schedule.ui.schedule

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.chen.schedule.domain.model.Semester
import com.chen.schedule.domain.model.TimeScheme
import com.chen.schedule.domain.model.TimeSlot
import com.chen.schedule.util.TimeSchemeTemplates
import kotlinx.coroutines.launch

/**
 * 选择作息方案。
 * 分组展示「内置模板」与「我的方案」;选中后先预览各节起止时间,点击「使用此方案」才生效。
 * 使用模板不会删除任何自定义方案。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchemeListScreen(
    onNavigateBack: () -> Unit,
    onEditScheme: (Long) -> Unit,
    viewModel: ScheduleConfigViewModel = hiltViewModel()
) {
    val hub by viewModel.hub.collectAsState()
    val scope = rememberCoroutineScope()
    var previewScheme by remember { mutableStateOf<TimeScheme?>(null) }
    var previewSlots by remember { mutableStateOf<List<TimeSlot>>(emptyList()) }
    var pendingDelete by remember { mutableStateOf<TimeScheme?>(null) }
    var deleteImpact by remember { mutableStateOf<List<Semester>>(emptyList()) }
    /** 引用关系是否已加载完成;加载期间禁用「删除」,避免误判为「无人引用」。 */
    var deleteImpactLoaded by remember { mutableStateOf(false) }
    /** 删除被引用的方案时,用户选定的替代方案 id。 */
    var replacementChoice by remember { mutableStateOf<Long?>(null) }

    val builtIns = hub.schemes.filter { it.isBuiltIn }
    val customs = hub.schemes.filter { !it.isBuiltIn }

    LaunchedEffect(Unit) { viewModel.recomputeStatus() }

    pendingDelete?.let { scheme ->
        val replacementCandidates = hub.schemes.filter { it.id != scheme.id }
        AlertDialog(
            onDismissRequest = { pendingDelete = null; deleteImpactLoaded = false },
            title = { Text("删除方案「${scheme.name}」?") },
            text = {
                Column {
                    if (!deleteImpactLoaded) {
                        Text("正在检查使用该方案的学期…")
                    } else if (deleteImpact.isEmpty()) {
                        Text("当前没有学期使用该方案,可以直接删除。此操作不可撤销。")
                    } else {
                        Text(
                            "有 ${deleteImpact.size} 个学期正在使用该方案:" +
                                deleteImpact.joinToString("、") { it.name.ifBlank { "未命名学期" } },
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "请先为这些学期选择替代作息(被删方案的节次不会并入「原有作息」):",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        replacementCandidates.forEach { candidate ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { replacementChoice = candidate.id },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = replacementChoice == candidate.id,
                                    onClick = { replacementChoice = candidate.id }
                                )
                                Text(candidate.name, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val replacement = replacementCandidates.firstOrNull { it.id == replacementChoice }
                        viewModel.deleteScheme(scheme, replacement)
                        pendingDelete = null
                        replacementChoice = null
                        deleteImpactLoaded = false
                    },
                    enabled = deleteImpactLoaded && (deleteImpact.isEmpty() || replacementChoice != null)
                ) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    replacementChoice = null
                    deleteImpactLoaded = false
                }) { Text("取消") }
            }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("选择作息方案", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
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
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(8.dp))
            SectionLabel("内置模板")
            if (builtIns.isEmpty()) {
                EmptyHint("正在准备内置模板…")
            }
            builtIns.forEach { scheme ->
                SchemePickRow(
                    scheme = scheme,
                    slots = if (hub.currentScheme?.id == scheme.id) hub.currentSlots else emptyList(),
                    selected = previewScheme?.id == scheme.id,
                    isCurrent = hub.semester?.schemeId == scheme.id,
                    onSelect = {
                        previewScheme = scheme
                        scope.launch { previewSlots = viewModel.loadSchemeSlots(scheme.id) }
                    },
                    onEdit = { onEditScheme(scheme.id) }
                )
            }

            Spacer(Modifier.height(12.dp))
            SectionLabel("我的方案")
            val customToShow = customs.filter { it.id != TimeScheme.LEGACY_ID } +
                customs.filter { it.id == TimeScheme.LEGACY_ID }
            if (customToShow.isEmpty()) {
                EmptyHint("还没有自定义方案")
            }
            customToShow.forEach { scheme ->
                SchemePickRow(
                    scheme = scheme,
                    slots = if (hub.currentScheme?.id == scheme.id) hub.currentSlots else emptyList(),
                    selected = previewScheme?.id == scheme.id,
                    isCurrent = hub.semester?.schemeId == scheme.id,
                    onSelect = {
                        previewScheme = scheme
                        scope.launch { previewSlots = viewModel.loadSchemeSlots(scheme.id) }
                    },
                    onEdit = { onEditScheme(scheme.id) },
                    onDelete = if (scheme.id == TimeScheme.LEGACY_ID || scheme.isBuiltIn) null else {
                        {
                            pendingDelete = scheme
                            replacementChoice = null
                            deleteImpact = emptyList()
                            deleteImpactLoaded = false
                            scope.launch {
                                deleteImpact = viewModel.semestersUsingScheme(scheme.id)
                                deleteImpactLoaded = true
                            }
                        }
                    }
                )
            }

            Spacer(Modifier.height(16.dp))

            // 预览 + 使用
            previewScheme?.let { scheme ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            "预览:${scheme.name}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(8.dp))
                        if (previewSlots.isEmpty()) {
                            Text(
                                "该方案暂无节次",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            previewSlots.forEach { slot ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "第${slot.slotNumber}节",
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.width(56.dp)
                                    )
                                    Text(
                                        "${slot.startTime}–${slot.endTime}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (slot.name.isNotBlank() && slot.name != "第${slot.slotNumber}节") {
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            slot.name,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = {
                                viewModel.useSchemeForCurrentSemester(scheme.id)
                                previewScheme = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(if (hub.semester?.schemeId == scheme.id) "当前已在使用" else "使用此方案") }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun SchemePickRow(
    scheme: TimeScheme,
    slots: List<TimeSlot>,
    selected: Boolean,
    isCurrent: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onSelect() },
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = selected, onClick = onSelect)
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        scheme.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (isCurrent) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "当前使用",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    schemeSubtitle(scheme, slots),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onEdit) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "编辑 ${scheme.name}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            onDelete?.let {
                IconButton(onClick = it) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "删除 ${scheme.name}",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

private fun schemeSubtitle(scheme: TimeScheme, slots: List<TimeSlot>): String {
    if (scheme.isLegacy) return "升级前保存的作息,可继续使用或另存为新方案"
    if (scheme.isBuiltIn) return "内置模板 · ${if (scheme.season == 2) "冬季" else "夏季"}作息"
    return if (slots.isEmpty()) "自定义方案" else "自定义方案 · 共 ${slots.size} 节"
}

/**
 * 新建 / 编辑作息方案。
 * 创建方式:从模板复制、快速生成(点选第一节开始时间)、文本导入;节次时间用时间选择器点选。
 * 保存方案与「设为当前使用」分开表达。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SchemeEditScreen(
    schemeId: Long?,
    onDone: () -> Unit,
    onCancel: () -> Unit,
    viewModel: ScheduleConfigViewModel = hiltViewModel()
) {
    val form by viewModel.schemeForm.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showTemplatePicker by remember { mutableStateOf(false) }
    var showImport by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    var pendingRemoveSlot by remember { mutableStateOf<TimeSlot?>(null) }
    var removeImpact by remember { mutableStateOf(0) }
    var sharedWith by remember { mutableStateOf<List<Semester>>(emptyList()) }
    // 快速生成:第一节开始时间 + 作息节奏
    var genStart by remember { mutableStateOf<String?>(null) }
    var genCustom by remember { mutableStateOf(false) }
    var genSeason by remember { mutableStateOf(TimeSchemeTemplates.SEASON_SUMMER) }
    var showCustomStartPicker by remember { mutableStateOf(false) }
    val presetStarts = remember { listOf("08:00", "08:10", "08:30") }

    LaunchedEffect(schemeId) {
        if (schemeId == null) viewModel.startNewSchemeForm() else viewModel.startSchemeEditForm(schemeId)
    }
    // 编辑共享方案时,提示会影响哪些学期
    LaunchedEffect(schemeId, form.started) {
        sharedWith = if (schemeId != null && schemeId > 0) viewModel.semestersUsingScheme(schemeId) else emptyList()
    }
    LaunchedEffect(form.saved) { if (form.saved) onDone() }

    val requestCancel: () -> Unit = {
        if (form.dirty) showDiscardDialog = true else onCancel()
    }
    androidx.activity.compose.BackHandler { requestCancel() }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("放弃未保存的修改?") },
            text = { Text("方案的修改尚未保存,返回将丢弃这些内容。") },
            confirmButton = {
                TextButton(onClick = {
                    showDiscardDialog = false
                    onCancel()
                }) { Text("放弃", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDiscardDialog = false }) { Text("继续编辑") } }
        )
    }

    if (showTemplatePicker) {
        AlertDialog(
            onDismissRequest = { showTemplatePicker = false },
            title = { Text("从模板复制") },
            text = {
                Column {
                    Text(
                        "选择模板会用其节次填充当前方案(尚未保存,可继续修改)。原有自定义方案不受影响。",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(8.dp))
                    listOf("夏季作息", "冬季作息").forEach { name ->
                        TextButton(onClick = {
                            viewModel.startSchemeFromTemplate(name)
                            showTemplatePicker = false
                        }) { Text(name) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showTemplatePicker = false }) { Text("取消") } }
        )
    }

    if (showCustomStartPicker) {
        TimePickerDialog24(
            title = "第一节开始时间",
            initial = genStart ?: "08:00",
            onConfirm = { picked ->
                genStart = picked
                genCustom = true
                viewModel.generateSlots(picked, genSeason)
            },
            onDismiss = { showCustomStartPicker = false }
        )
    }

    pendingRemoveSlot?.let { slot ->
        AlertDialog(
            onDismissRequest = { pendingRemoveSlot = null },
            title = { Text("删除第${slot.slotNumber}节?") },
            text = {
                Text(
                    if (removeImpact > 0)
                        "有 $removeImpact 门课程使用了该节次,删除后这些课程可能无法正确显示。建议先调整课程。"
                    else "当前没有课程使用该节次,可以安全删除。"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removeSlot(slot)
                    pendingRemoveSlot = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pendingRemoveSlot = null }) { Text("取消") } }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when {
                            form.isEditingBuiltInCopy -> "编辑模板(复制为新方案)"
                            form.isNew -> "新建作息方案"
                            else -> "编辑方案"
                        },
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = requestCancel) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.saveSchemeForm() }, enabled = !form.saving) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "保存",
                            tint = MaterialTheme.colorScheme.primary
                        )
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
            form.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(8.dp))
            }
            if (form.isEditingBuiltInCopy) {
                Text(
                    "内置模板不可直接修改。保存后会生成一个自定义方案,模板本身与其他学期不受影响。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
            }

            OutlinedTextField(
                value = form.name,
                onValueChange = viewModel::updateSchemeName,
                label = { Text("方案名称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // 共享方案提示 + 另存为新方案
            if (sharedWith.size > 1) {
                Spacer(Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            "该方案正被 ${sharedWith.size} 个学期使用:${sharedWith.joinToString("、") { it.name.ifBlank { "未命名学期" } }}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "保存修改会影响以上所有学期。若只想改当前学期,请另存为新方案。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                val source = viewModel.hub.value.schemes.find { it.id == schemeId }
                                if (source != null) viewModel.startSchemeFromExisting(source)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("另存为新方案") }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // 创建方式(仅新建时突出显示)
            SectionLabel("创建方式")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { showTemplatePicker = true },
                    modifier = Modifier.weight(1f)
                ) { Text("从模板复制") }
                OutlinedButton(
                    onClick = { showImport = !showImport },
                    modifier = Modifier.weight(1f)
                ) { Text("文本导入") }
            }
            Spacer(Modifier.height(8.dp))

            AnimatedVisibility(visible = showImport) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = form.importText,
                            onValueChange = viewModel::updateImportText,
                            label = { Text("粘贴作息文本") },
                            minLines = 3,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        IconButton(onClick = {
                            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                                as android.content.ClipboardManager
                            val text = clipboard.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
                            viewModel.updateImportText(text)
                        }) {
                            Icon(Icons.Default.ContentPaste, contentDescription = "从剪贴板粘贴")
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { viewModel.previewImportText() },
                            enabled = form.importText.isNotBlank(),
                            modifier = Modifier.weight(1f)
                        ) { Text("解析预览") }
                        Button(
                            onClick = { viewModel.confirmImportPreview() },
                            enabled = form.importPreview.isNotEmpty(),
                            modifier = Modifier.weight(1f)
                        ) { Text("确认导入") }
                    }
                    form.importError?.let {
                        Spacer(Modifier.height(4.dp))
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    if (form.importPreview.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "预览(${form.importPreview.size} 节)",
                            style = MaterialTheme.typography.labelMedium
                        )
                        form.importPreview.forEach { slot ->
                            Text(
                                "第${slot.slotNumber}节  ${slot.startTime}–${slot.endTime}  ${slot.name}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }

            SectionLabel("快速生成节次")
            Text(
                "选择第一节开始时间,按内置节奏生成整套节次(每节 45 分钟),之后可逐节调整",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                presetStarts.forEach { start ->
                    FilterChip(
                        selected = genStart == start && !genCustom,
                        onClick = {
                            genStart = start
                            genCustom = false
                            viewModel.generateSlots(start, genSeason)
                        },
                        label = { Text(start) }
                    )
                }
                FilterChip(
                    selected = genCustom,
                    onClick = { showCustomStartPicker = true },
                    label = { Text("自定义") }
                )
            }
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = genSeason == TimeSchemeTemplates.SEASON_SUMMER,
                    onClick = {
                        genSeason = TimeSchemeTemplates.SEASON_SUMMER
                        genStart?.let { viewModel.generateSlots(it, genSeason) }
                    },
                    label = { Text("夏季节奏") }
                )
                FilterChip(
                    selected = genSeason == TimeSchemeTemplates.SEASON_WINTER,
                    onClick = {
                        genSeason = TimeSchemeTemplates.SEASON_WINTER
                        genStart?.let { viewModel.generateSlots(it, genSeason) }
                    },
                    label = { Text("冬季节奏") }
                )
            }

            Spacer(Modifier.height(12.dp))

            SectionLabel("节次(${form.slots.size})")
            form.slots.sortedBy { it.slotNumber }.forEach { slot ->
                SlotEditRow(
                    slot = slot,
                    onSlotChange = viewModel::updateSlot,
                    onRemove = {
                        pendingRemoveSlot = slot
                        removeImpact = 0
                        scope.launch { removeImpact = viewModel.countCoursesUsingSlot(slot.slotNumber) }
                    }
                )
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { viewModel.addBlankSlot() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("添加节次")
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("设为当前使用", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "保存后立即用于当前学期",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = form.setCurrent, onCheckedChange = viewModel::updateSchemeSetCurrent)
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = { viewModel.saveSchemeForm() },
                enabled = !form.saving,
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (form.saving) "正在保存…" else "保存方案") }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = requestCancel, modifier = Modifier.fillMaxWidth()) { Text("取消") }

            Spacer(Modifier.height(24.dp))
        }
    }
}

/** 单个节次行:编号 + 名称 + 起止时间(点开时间选择器)。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SlotEditRow(
    slot: TimeSlot,
    onSlotChange: (TimeSlot) -> Unit,
    onRemove: () -> Unit
) {
    /** null = 未打开;true = 编辑开始时间,false = 编辑结束时间。 */
    var editingStart by remember { mutableStateOf<Boolean?>(null) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "${slot.slotNumber}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(
                    value = slot.name,
                    onValueChange = { onSlotChange(slot.copy(name = it)) },
                    label = { Text("名称") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(4.dp))
                IconButton(onClick = onRemove) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "删除第${slot.slotNumber}节",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TimeFieldBox(
                    label = "开始",
                    value = slot.startTime,
                    modifier = Modifier.weight(1f),
                    onClick = { editingStart = true }
                )
                Spacer(Modifier.width(8.dp))
                TimeFieldBox(
                    label = "结束",
                    value = slot.endTime,
                    modifier = Modifier.weight(1f),
                    onClick = { editingStart = false }
                )
            }
        }
    }
    editingStart?.let { isStart ->
        TimePickerDialog24(
            title = "第${slot.slotNumber}节${if (isStart) "开始" else "结束"}时间",
            initial = if (isStart) slot.startTime else slot.endTime,
            onConfirm = { picked ->
                onSlotChange(if (isStart) slot.copy(startTime = picked) else slot.copy(endTime = picked))
                editingStart = null
            },
            onDismiss = { editingStart = null }
        )
    }
}

/** 只读时间字段卡片,点开时间选择器。 */
@Composable
private fun TimeFieldBox(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = MaterialTheme.shapes.small,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(value, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

/** 24 小时制时间选择对话框,确认后回调 "HH:mm"。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog24(
    title: String,
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val parts = initial.split(":")
    val state = rememberTimePickerState(
        initialHour = parts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: 8,
        initialMinute = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: 0,
        is24Hour = true
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { TimePicker(state = state) },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(String.format("%02d:%02d", state.hour, state.minute))
            }) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
