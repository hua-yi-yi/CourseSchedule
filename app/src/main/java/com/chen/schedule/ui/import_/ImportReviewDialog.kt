package com.chen.schedule.ui.import_

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chen.schedule.domain.model.*
import com.chen.schedule.util.*

/**
 * 导入核对对话框：统一核对、修正、选择替换与删除旧安排的交互流程。
 */
@Composable
fun ImportReviewDialog(
    incoming: List<Course>,
    context: ImportReviewContext,
    busy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (ImportSelection) -> Unit
) {
    val draftSaver = remember {
        androidx.compose.runtime.saveable.listSaver<List<Course>, String>(
            save = { list -> list.map { kotlinx.serialization.json.Json.encodeToString(Course.serializer(), it) } },
            restore = { list -> list.map { kotlinx.serialization.json.Json.decodeFromString(Course.serializer(), it) } }
        )
    }
    val excludedSaver = remember {
        androidx.compose.runtime.saveable.listSaver<Set<Int>, Int>(save = { it.toList() }, restore = { it.toSet() })
    }
    val replacementsSaver = remember {
        androidx.compose.runtime.saveable.listSaver<Map<Int, Long>, String>(
            save = { it.map { (index, id) -> "$index:$id" } },
            restore = { list -> list.associate { it.substringBefore(':').toInt() to it.substringAfter(':').toLong() } }
        )
    }
    val removalsSaver = remember {
        androidx.compose.runtime.saveable.listSaver<Set<Long>, Long>(save = { it.toList() }, restore = { it.toSet() })
    }

    var drafts by androidx.compose.runtime.saveable.rememberSaveable(incoming, stateSaver = draftSaver) { mutableStateOf(incoming) }
    var excluded by androidx.compose.runtime.saveable.rememberSaveable(incoming, stateSaver = excludedSaver) { mutableStateOf(emptySet<Int>()) }
    var update by androidx.compose.runtime.saveable.rememberSaveable(incoming) { mutableStateOf(false) }
    var replacements by androidx.compose.runtime.saveable.rememberSaveable(incoming, stateSaver = replacementsSaver) { mutableStateOf(emptyMap<Int, Long>()) }
    var removals by androidx.compose.runtime.saveable.rememberSaveable(incoming, stateSaver = removalsSaver) { mutableStateOf(emptySet<Long>()) }
    var editing by remember { mutableStateOf<Int?>(null) }
    var choosing by remember { mutableStateOf<Int?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    val selected = drafts.indices.filter { it !in excluded }
    val exact = selected.map { CourseImportRules.identity(drafts[it]) }.toSet()
    val obsolete = context.existing.filter {
        it.importSource == context.source && CourseImportRules.identity(it) !in exact && it.id !in replacements.values
    }
    val plan = CourseImportRules.plan(
        selected.filter { it !in replacements }.map { drafts[it] },
        context.existing.filter { it.id !in removals && it.id !in replacements.values }
    )

    val activeWeek = WeekCalculator.activeWeek(context.semester.startDate, context.semester.totalWeeks)
    val activeWeekText = activeWeek?.let { "第 $it 周" } ?: "非教学期"

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = {
            Column {
                Text("核对课表 · ${selected.size} 条安排", fontWeight = FontWeight.Bold)
                Text(
                    "导入到：${context.semester.name} · 共 ${context.semester.totalWeeks} 周 (今天对应 $activeWeekText)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 更新旧安排选项卡
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !busy) {
                                update = !update
                                replacements = if (update) ImportReview.suggestions(drafts, context.existing, context.source).filterKeys { it !in excluded } else emptyMap()
                                removals = emptySet()
                            }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = update,
                            enabled = !busy,
                            onCheckedChange = {
                                update = it
                                replacements = if (it) ImportReview.suggestions(drafts, context.existing, context.source).filterKeys { idx -> idx !in excluded } else emptyMap()
                                removals = emptySet()
                            }
                        )
                        Spacer(Modifier.width(6.dp))
                        Column {
                            Text("更新本入口以前导入的安排", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text("智能匹配相同课程并建议替换，手动排课和无标记项受保护保留", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                        }
                    }
                }

                // 统计数据栏
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    StatPill("新增 ${plan.fresh.size}", MaterialTheme.colorScheme.primary)
                    StatPill("替换 ${replacements.keys.count { it in selected }}", MaterialTheme.colorScheme.tertiary)
                    StatPill("重复 ${plan.skipped}", MaterialTheme.colorScheme.secondary)
                    if (removals.isNotEmpty()) {
                        StatPill("删除 ${removals.size}", MaterialTheme.colorScheme.error)
                    }
                }

                // 列表表头：课程数量与全选/全不选按钮
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "课程列表 (${selected.size}/${drafts.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(
                        enabled = !busy,
                        onClick = {
                            if (excluded.isEmpty()) {
                                excluded = drafts.indices.toSet()
                                replacements = emptyMap()
                            } else {
                                excluded = emptySet()
                                if (update) {
                                    replacements = ImportReview.suggestions(drafts, context.existing, context.source)
                                }
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(if (excluded.isEmpty()) "取消全选" else "全选", fontSize = 12.5.sp)
                    }
                }

                drafts.forEachIndexed { index, course ->
                    val isExcluded = index in excluded
                    val conflicts = CourseConflictDetector.findConflicts(
                        course,
                        context.existing.filter {
                            it.id != replacements[index] && it.id !in removals && CourseImportRules.identity(it) != CourseImportRules.identity(course)
                        }
                    )
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(
                            containerColor = if (isExcluded) MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.35f)
                            else MaterialTheme.colorScheme.surfaceContainerLow
                        )
                    ) {
                        Column(Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(MaterialTheme.shapes.small)
                                    .clickable(enabled = !busy) {
                                        excluded = if (isExcluded) excluded - index else excluded + index
                                        if (!isExcluded) replacements = replacements - index
                                    },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = !isExcluded,
                                    enabled = !busy,
                                    onCheckedChange = {
                                        excluded = if (it) excluded - index else excluded + index
                                        if (!it) replacements = replacements - index
                                    }
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    course.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isExcluded) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Text(
                                describeArrangement(course),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 36.dp)
                            )
                            if (conflicts.isNotEmpty()) {
                                Spacer(Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.padding(start = 36.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Warning,
                                        contentDescription = "冲突",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        "与已有课表存在时段冲突，请核对",
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                            replacements[index]?.let { id ->
                                context.existing.find { it.id == id }?.let { old ->
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        "替换旧安排：${describeArrangement(old)}",
                                        color = MaterialTheme.colorScheme.primary,
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(start = 36.dp)
                                    )
                                }
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 30.dp, top = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                TextButton(
                                    enabled = !busy,
                                    onClick = { editing = index },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) { Text("修改安排", fontSize = 12.sp) }
                                if (update && !isExcluded) {
                                    TextButton(
                                        enabled = !busy,
                                        onClick = { choosing = index },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                    ) { Text("选择替换 / 追加", fontSize = 12.sp) }
                                }
                            }
                        }
                    }
                }

                if (update && obsolete.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text("本次未匹配的旧安排（默认保留）", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    obsolete.forEach { old ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.small,
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = !busy) {
                                        removals = if (old.id in removals) removals - old.id else removals + old.id
                                    }
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = old.id in removals,
                                    enabled = !busy,
                                    onCheckedChange = {
                                        removals = if (it) removals + old.id else removals - old.id
                                    }
                                )
                                Spacer(Modifier.width(6.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("删除旧课程：${old.name}", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    Text(describeArrangement(old), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }

                error?.let {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(it, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.padding(10.dp), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !busy && selected.isNotEmpty(),
                onClick = {
                    try {
                        val mapping = selected.mapIndexedNotNull { newIndex, oldIndex -> replacements[oldIndex]?.let { newIndex to it } }.toMap()
                        val selection = ImportSelection(selected.map { drafts[it] }, mapping, removals)
                        CourseImportRules.validateForSemester(selection.courses, context.semester, context.slots)
                        ImportReview.validateSelection(selection, context.existing, context.source)
                        error = null
                        onConfirm(selection)
                    } catch (e: IllegalArgumentException) {
                        error = e.message
                    }
                }
            ) {
                Text(if (busy) "正在导入…" else "确认导入 (${selected.size}条)")
            }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = onDismiss) { Text("返回") }
        }
    )

    editing?.let { index ->
        CourseDraftDialog(
            course = drafts[index],
            totalWeeks = context.semester.totalWeeks,
            onDismiss = { editing = null },
            onSave = { edited ->
                drafts = drafts.toMutableList().also { it[index] = edited }
                replacements = replacements - index
                editing = null
            }
        )
    }

    choosing?.let { index ->
        AlertDialog(
            onDismissRequest = { choosing = null },
            title = { Text("替换哪条旧安排？") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { replacements = replacements - index; choosing = null }) {
                        Text("作为新安排追加", fontWeight = FontWeight.Bold)
                    }
                    context.existing.filter {
                        it.importSource == context.source && (it.id !in replacements.values || it.id == replacements[index]) && it.id !in removals
                    }.forEach { old ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { replacements = replacements + (index to old.id); choosing = null },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                        ) {
                            Column(Modifier.padding(10.dp)) {
                                Text(old.name, fontWeight = FontWeight.SemiBold)
                                Text(describeArrangement(old), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { choosing = null }) { Text("取消") } }
        )
    }
}

@Composable
private fun StatPill(text: String, color: androidx.compose.ui.graphics.Color) {
    Surface(
        color = color.copy(alpha = 0.12f),
        shape = MaterialTheme.shapes.extraSmall
    ) {
        Text(
            text = text,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = color,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
        )
    }
}

internal fun describeArrangement(course: Course): String =
    "${DayOfWeek.entries.find { it.index == course.dayOfWeek }?.label ?: "周${course.dayOfWeek}"} ${course.startSlot}–${course.endSlot}节 · ${course.startWeek}–${course.endWeek}周 ${course.weekType.label}\n教室：${course.classroom.ifBlank { "未填写" }} · 教师：${course.teacher.ifBlank { "未填写" }}"

/**
 * 修正单条课程草稿弹窗：
 * - 星期点选芯片，杜绝格式错误；
 * - 节次与周次范围成对排列，自动限制数字输入；
 * - 周类型单选芯片；
 * - 完整校验与明确错误提示。
 */
@Composable
fun CourseDraftDialog(
    course: Course,
    totalWeeks: Int,
    onDismiss: () -> Unit,
    onSave: (Course) -> Unit
) {
    var name by androidx.compose.runtime.saveable.rememberSaveable(course) { mutableStateOf(course.name) }
    var teacher by androidx.compose.runtime.saveable.rememberSaveable(course) { mutableStateOf(course.teacher) }
    var room by androidx.compose.runtime.saveable.rememberSaveable(course) { mutableStateOf(course.classroom) }
    var dayOfWeek by androidx.compose.runtime.saveable.rememberSaveable(course) { mutableIntStateOf(course.dayOfWeek) }
    var start by androidx.compose.runtime.saveable.rememberSaveable(course) { mutableStateOf(course.startSlot.toString()) }
    var end by androidx.compose.runtime.saveable.rememberSaveable(course) { mutableStateOf(course.endSlot.toString()) }
    var first by androidx.compose.runtime.saveable.rememberSaveable(course) { mutableStateOf(course.startWeek.toString()) }
    var last by androidx.compose.runtime.saveable.rememberSaveable(course) { mutableStateOf(course.endWeek.toString()) }
    var type by androidx.compose.runtime.saveable.rememberSaveable(course) { mutableStateOf(course.weekType) }
    var error by androidx.compose.runtime.saveable.rememberSaveable(course) { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("修改上课安排", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; error = null },
                    label = { Text("课程名称 *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = teacher,
                        onValueChange = { teacher = it },
                        label = { Text("教师") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = room,
                        onValueChange = { room = it },
                        label = { Text("教室") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                // 星期芯片组
                Text("上课星期", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    DayOfWeek.entries.forEach { d ->
                        FilterChip(
                            selected = dayOfWeek == d.index,
                            onClick = { dayOfWeek = d.index; error = null },
                            label = { Text(d.label, fontSize = 11.5.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // 节次范围
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = start,
                        onValueChange = { start = it.filter(Char::isDigit); error = null },
                        label = { Text("开始节次") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = end,
                        onValueChange = { end = it.filter(Char::isDigit); error = null },
                        label = { Text("结束节次") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                // 周次范围
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = first,
                        onValueChange = { first = it.filter(Char::isDigit); error = null },
                        label = { Text("开始周") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = last,
                        onValueChange = { last = it.filter(Char::isDigit); error = null },
                        label = { Text("结束周（最多 $totalWeeks）") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                // 周类型
                Text("周类型", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    WeekType.entries.forEach { weekType ->
                        FilterChip(
                            selected = type == weekType,
                            onClick = { type = weekType; error = null },
                            label = { Text(weekType.label) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                try {
                    val trimmedName = name.trim()
                    require(trimmedName.isNotBlank()) { "课程名称不能为空" }
                    val s = start.toIntOrNull() ?: throw IllegalArgumentException("开始节次必须为数字")
                    val e = end.toIntOrNull() ?: throw IllegalArgumentException("结束节次必须为数字")
                    val f = first.toIntOrNull() ?: throw IllegalArgumentException("开始周必须为数字")
                    val l = last.toIntOrNull() ?: throw IllegalArgumentException("结束周必须为数字")
                    require(s in 1..e) { "结束节次必须大于或等于开始节次" }
                    require(f in 1..l) { "结束周必须大于或等于开始周" }
                    require(l <= totalWeeks) { "结束周超过学期总周数 ($totalWeeks)" }
                    val edited = course.copy(
                        name = trimmedName,
                        teacher = teacher.trim(),
                        classroom = room.trim(),
                        dayOfWeek = dayOfWeek,
                        startSlot = s,
                        endSlot = e,
                        startWeek = f,
                        endWeek = l,
                        weekType = type
                    )
                    CourseImportRules.validate(edited)
                    onSave(edited)
                } catch (e: Exception) {
                    error = e.message ?: "请检查输入的数字范围"
                }
            }) {
                Text("保存修改")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
