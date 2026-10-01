package com.chen.schedule.ui.import_

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.chen.schedule.domain.model.*
import com.chen.schedule.util.*

/** All import entrances use the same review, correction and explicit update flow. */
@Composable
fun ImportReviewDialog(incoming: List<Course>, context: ImportReviewContext, busy: Boolean,
    onDismiss: () -> Unit, onConfirm: (ImportSelection) -> Unit) {
    val draftSaver = remember { androidx.compose.runtime.saveable.listSaver<List<Course>, String>(
        save = { list -> list.map { kotlinx.serialization.json.Json.encodeToString(Course.serializer(), it) } },
        restore = { list -> list.map { kotlinx.serialization.json.Json.decodeFromString(Course.serializer(), it) } }) }
    val excludedSaver = remember { androidx.compose.runtime.saveable.listSaver<Set<Int>, Int>(save = { it.toList() }, restore = { it.toSet() }) }
    val replacementsSaver = remember { androidx.compose.runtime.saveable.listSaver<Map<Int, Long>, String>(
        save = { it.map { (index, id) -> "$index:$id" } },
        restore = { list -> list.associate { it.substringBefore(':').toInt() to it.substringAfter(':').toLong() } }) }
    val removalsSaver = remember { androidx.compose.runtime.saveable.listSaver<Set<Long>, Long>(save = { it.toList() }, restore = { it.toSet() }) }
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
    val obsolete = context.existing.filter { it.importSource == context.source && CourseImportRules.identity(it) !in exact && it.id !in replacements.values }
    val plan = CourseImportRules.plan(selected.filter { it !in replacements }.map { drafts[it] },
        context.existing.filter { it.id !in removals && it.id !in replacements.values })
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("核对课表 · ${selected.size} 条安排") },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("导入到：${context.semester.name} · 共 ${context.semester.totalWeeks} 周")
                Text("今天对应：" + (WeekCalculator.activeWeek(context.semester.startDate, context.semester.totalWeeks)?.let { "第 $it 周" } ?: "非教学期"))
                Row(Modifier.clickable(enabled = !busy) {
                    update = !update; replacements = if (update) ImportReview.suggestions(drafts, context.existing, context.source).filterKeys { index -> index !in excluded } else emptyMap(); removals = emptySet()
                }) { Checkbox(checked = update, enabled = !busy, onCheckedChange = {
                    update = it; replacements = if (it) ImportReview.suggestions(drafts, context.existing, context.source).filterKeys { index -> index !in excluded } else emptyMap(); removals = emptySet()
                }); Text("更新本入口以前导入的安排", Modifier.padding(top = 12.dp)) }
                Text("新增 ${plan.fresh.size} · 替换 ${replacements.keys.count { it in selected }} · 重复 ${plan.skipped} · 删除 ${removals.size}")
                Text("手动课程、手动修改及旧版本未标记来源的安排会保留。请核对教室、周次及冲突。", style = MaterialTheme.typography.bodySmall)
                drafts.forEachIndexed { index, course ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(8.dp)) {
                            Row { Checkbox(checked = index !in excluded, enabled = !busy, onCheckedChange = {
                                excluded = if (it) excluded - index else excluded + index
                                if (!it) replacements = replacements - index
                            }); Text(course.name, Modifier.padding(top = 12.dp)) }
                            Text(describeArrangement(course))
                            if (CourseConflictDetector.findConflicts(course, context.existing.filter { it.id != replacements[index] && it.id !in removals && CourseImportRules.identity(it) != CourseImportRules.identity(course) }).isNotEmpty())
                                Text("与已有课表存在重叠，请核对", color = MaterialTheme.colorScheme.error)
                            replacements[index]?.let { id -> context.existing.find { it.id == id }?.let {
                                Text("替换旧安排：${describeArrangement(it)}", color = MaterialTheme.colorScheme.primary)
                            } }
                            Row {
                                TextButton(enabled = !busy, onClick = { editing = index }) { Text("修改") }
                                if (update && index !in excluded) TextButton(enabled = !busy, onClick = { choosing = index }) { Text("选择替换 / 追加") }
                            }
                        }
                    }
                }
                if (update && obsolete.isNotEmpty()) {
                    Text("本次未匹配的旧安排（默认保留）")
                    obsolete.forEach { old ->
                        Row { Checkbox(checked = old.id in removals, enabled = !busy,
                            onCheckedChange = { removals = if (it) removals + old.id else removals - old.id });
                            Text("删除 ${old.name}：${describeArrangement(old)}", Modifier.weight(1f)) }
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton(enabled = !busy && selected.isNotEmpty(), onClick = {
            try {
                val mapping = selected.mapIndexedNotNull { newIndex, oldIndex -> replacements[oldIndex]?.let { newIndex to it } }.toMap()
                val selection = ImportSelection(selected.map { drafts[it] }, mapping, removals)
                CourseImportRules.validateForSemester(selection.courses, context.semester, context.slots)
                ImportReview.validateSelection(selection, context.existing, context.source)
                error = null; onConfirm(selection)
            } catch (e: IllegalArgumentException) { error = e.message }
        }) { Text(if (busy) "正在导入…" else "确认导入") } },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("返回") } })
    editing?.let { index -> CourseDraftDialog(drafts[index], context.semester.totalWeeks, onDismiss = { editing = null }, onSave = { edited ->
        drafts = drafts.toMutableList().also { it[index] = edited }; replacements = replacements - index; editing = null
    }) }
    choosing?.let { index ->
        AlertDialog(onDismissRequest = { choosing = null }, title = { Text("替换哪条旧安排？") },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) {
                TextButton(onClick = { replacements = replacements - index; choosing = null }) { Text("作为新安排追加") }
                context.existing.filter { it.importSource == context.source && (it.id !in replacements.values || it.id == replacements[index]) && it.id !in removals }.forEach { old ->
                    TextButton(onClick = { replacements = replacements + (index to old.id); choosing = null }) { Text("${old.name} · ${describeArrangement(old)}") }
                }
            } }, confirmButton = { TextButton(onClick = { choosing = null }) { Text("取消") } })
    }
}

internal fun describeArrangement(course: Course): String =
    "${DayOfWeek.entries.find { it.index == course.dayOfWeek }?.label} ${course.startSlot}–${course.endSlot}节 · ${course.startWeek}–${course.endWeek}周 ${course.weekType.label}\n教室：${course.classroom.ifBlank { "未填写" }} · 教师：${course.teacher.ifBlank { "未填写" }}"

@Composable
fun CourseDraftDialog(course: Course, totalWeeks: Int, onDismiss: () -> Unit, onSave: (Course) -> Unit) {
    var name by androidx.compose.runtime.saveable.rememberSaveable(course) { mutableStateOf(course.name) }; var teacher by androidx.compose.runtime.saveable.rememberSaveable(course) { mutableStateOf(course.teacher) }
    var room by androidx.compose.runtime.saveable.rememberSaveable(course) { mutableStateOf(course.classroom) }; var day by androidx.compose.runtime.saveable.rememberSaveable(course) { mutableStateOf(course.dayOfWeek.toString()) }
    var start by androidx.compose.runtime.saveable.rememberSaveable(course) { mutableStateOf(course.startSlot.toString()) }; var end by androidx.compose.runtime.saveable.rememberSaveable(course) { mutableStateOf(course.endSlot.toString()) }
    var first by androidx.compose.runtime.saveable.rememberSaveable(course) { mutableStateOf(course.startWeek.toString()) }; var last by androidx.compose.runtime.saveable.rememberSaveable(course) { mutableStateOf(course.endWeek.toString()) }
    var type by androidx.compose.runtime.saveable.rememberSaveable(course) { mutableStateOf(course.weekType) }; var error by androidx.compose.runtime.saveable.rememberSaveable(course) { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("修改上课安排") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("课程名称") }, singleLine = true)
            OutlinedTextField(teacher, { teacher = it }, label = { Text("教师") }, singleLine = true)
            OutlinedTextField(room, { room = it }, label = { Text("教室") }, singleLine = true)
            OutlinedTextField(day, { day = it }, label = { Text("星期（1–7）") }, singleLine = true)
            OutlinedTextField(start, { start = it }, label = { Text("开始节次") }, singleLine = true)
            OutlinedTextField(end, { end = it }, label = { Text("结束节次") }, singleLine = true)
            OutlinedTextField(first, { first = it }, label = { Text("开始周") }, singleLine = true)
            OutlinedTextField(last, { last = it }, label = { Text("结束周（最多 $totalWeeks）") }, singleLine = true)
            WeekType.entries.forEach { weekType -> Row { RadioButton(selected = type == weekType, onClick = { type = weekType }); Text(weekType.label, Modifier.padding(top = 12.dp)) } }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { TextButton(onClick = {
        try {
            val edited = course.copy(name = name.trim(), teacher = teacher, classroom = room, dayOfWeek = day.toInt(), startSlot = start.toInt(), endSlot = end.toInt(), startWeek = first.toInt(), endWeek = last.toInt(), weekType = type)
            CourseImportRules.validate(edited); require(edited.endWeek <= totalWeeks) { "结束周超过学期周数" }; onSave(edited)
        } catch (e: IllegalArgumentException) { error = e.message ?: "请检查数字" }
    }) { Text("保存修改") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}
