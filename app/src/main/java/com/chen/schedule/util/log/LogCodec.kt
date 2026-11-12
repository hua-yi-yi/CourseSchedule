package com.chen.schedule.util.log

import com.chen.schedule.util.DiagnosticPayload
import com.chen.schedule.util.ImportSnapshot
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 日志序列化、文本编码与多格式外部日志导入解析器。
 *
 * 支持解析：
 * 1. CourseSchedule 标准运行/崩溃日志 (.log / .txt)
 * 2. CourseSchedule 导出的日志上传包 / 提取诊断 JSON / 导入快照 JSON (.json)
 * 3. Markdown 诊断反馈报告 (含内嵌 JSON 或键值摘要)
 * 4. Android 标准 adb logcat 导出文本
 * 5. 普通文本与异常堆栈日志
 */
object LogCodec {

    private const val TIME_PATTERN = "yyyy-MM-dd HH:mm:ss.SSS"

    val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        prettyPrint = false
    }

    val prettyJson = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    // 标准行格式: 2026-11-12 14:30:05.123 [I/Tag] Message
    private val standardLineRegex = Regex(
        """^(\d{4}-\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}(?:\.\d{1,3})?)\s+\[([DIWEC])/([^\]]+)]\s?(.*)$"""
    )

    // Android logcat threadtime: 11-12 14:30:05.123  1234  5678 E Tag : Message (或带年份)
    private val logcatThreadtimeRegex = Regex(
        """^(?:(\d{4}-)?(\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}\.\d{3}))\s+\d+\s+\d+\s+([VDIWEF])\s+([^:]+?)\s*:\s?(.*)$"""
    )

    // Android logcat brief: E/Tag( 1234): Message 或 2026-11-12 14:30:05.123 E/Tag: Message
    private val logcatBriefRegex = Regex(
        """^(?:(\d{4}-\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}(?:\.\d{1,3})?)\s+)?([VDIWEF])/([^(:]+)(?:\(\s*\d+\))?:\s?(.*)$"""
    )

    private val headerCommentRegex = Regex("""^#\s*([^:：]+)\s*[:：]\s*(.+)$""")

    private val stackContinuationRegex = Regex(
        """^(\s+at\s+\S+.*|Caused by:\s+.*|Suppressed:\s+.*|\s+\.\.\.\s+\d+\s+more.*)$"""
    )

    fun formatTimestamp(millis: Long): String {
        return SimpleDateFormat(TIME_PATTERN, Locale.US).format(Date(millis))
    }

    fun parseTimestampOrZero(formatted: String): Long {
        val normalized = if (formatted.contains('.')) formatted else "$formatted.000"
        return runCatching {
            SimpleDateFormat(TIME_PATTERN, Locale.US).parse(normalized)?.time ?: 0L
        }.getOrDefault(0L)
    }

    fun throwableToString(throwable: Throwable?): String? {
        if (throwable == null) return null
        val sw = StringWriter()
        PrintWriter(sw).use { throwable.printStackTrace(it) }
        return LogSanitizer.sanitize(sw.toString().trimEnd())
    }

    fun formatEntry(entry: LogEntry): String {
        val sanitizedMsg = LogSanitizer.sanitize(entry.message)
        val msgLines = sanitizedMsg.lines()
        return buildString {
            val firstLine = msgLines.firstOrNull().orEmpty()
            append(entry.formattedTime)
            append(" [")
            append(entry.level.shortCode)
            append('/')
            append(entry.tag.replace(']', '_'))
            append("] ")
            appendLine(firstLine)

            for (i in 1 until msgLines.size) {
                append("  > ")
                appendLine(msgLines[i])
            }

            if (!entry.stackTrace.isNullOrBlank()) {
                val sanitizedStack = LogSanitizer.sanitize(entry.stackTrace)
                sanitizedStack.lineSequence().forEach { line ->
                    append("  | ")
                    appendLine(line)
                }
            }
        }
    }

    fun buildExportDocument(
        entries: List<LogEntry>,
        metadata: Map<String, String>,
        importSnapshots: List<ImportSnapshotBrief> = emptyList()
    ): String {
        return buildString {
            appendLine("# === CourseSchedule Diagnostic Log ===")
            metadata.forEach { (key, value) ->
                appendLine("# $key: $value")
            }
            appendLine("# 导出条数: ${entries.size}")
            appendLine("# =====================================")
            appendLine()

            if (importSnapshots.isNotEmpty()) {
                appendLine("# --- 最近课表导入快照摘要 (${importSnapshots.size}) ---")
                importSnapshots.forEach { snap ->
                    val status = if (snap.error.isNullOrBlank()) "识别 ${snap.courseCount} 门课" else "异常: ${snap.error}"
                    appendLine("# [${snap.formattedTime}] 来源=${snap.source} | 学期=${snap.semesterName} | $status")
                }
                appendLine()
            }

            if (entries.isEmpty()) {
                appendLine("# （暂无运行日志记录）")
            } else {
                entries.forEach { entry ->
                    append(formatEntry(entry))
                }
            }
        }
    }

    /**
     * 解析标准 `.log` 文件内容为结构化 [LogEntry] 列表。
     */
    fun parseStandardEntries(text: String): List<LogEntry> {
        if (text.isBlank()) return emptyList()
        val results = ArrayList<LogEntry>()
        var currentTimeStr = ""
        var currentTimestamp = 0L
        var currentLevel = LogLevel.INFO
        var currentTag = "App"
        val msgBuilder = StringBuilder()
        val stackBuilder = StringBuilder()
        var hasOpenEntry = false

        fun flush() {
            if (!hasOpenEntry) return
            results.add(
                LogEntry(
                    timestamp = currentTimestamp,
                    formattedTime = currentTimeStr,
                    level = currentLevel,
                    tag = currentTag,
                    message = msgBuilder.toString().trimEnd(),
                    stackTrace = stackBuilder.toString().trimEnd().ifBlank { null }
                )
            )
            msgBuilder.setLength(0)
            stackBuilder.setLength(0)
            hasOpenEntry = false
        }

        text.lineSequence().forEach { rawLine ->
            val line = rawLine.trimEnd('\r')
            if (line.isBlank() && !hasOpenEntry) return@forEach
            if (line.startsWith("#") && !hasOpenEntry) return@forEach

            val match = standardLineRegex.matchEntire(line)
            if (match != null) {
                flush()
                currentTimeStr = match.groupValues[1]
                currentTimestamp = parseTimestampOrZero(currentTimeStr)
                currentLevel = LogLevel.fromShortCode(match.groupValues[2])
                currentTag = match.groupValues[3]
                msgBuilder.append(match.groupValues[4])
                hasOpenEntry = true
            } else if (hasOpenEntry) {
                when {
                    line.startsWith("  > ") -> {
                        msgBuilder.append('\n').append(line.removePrefix("  > "))
                    }
                    line.startsWith("  | ") -> {
                        if (stackBuilder.isNotEmpty()) stackBuilder.append('\n')
                        stackBuilder.append(line.removePrefix("  | "))
                    }
                    line.startsWith("#") -> {
                        // 忽略文件内部分隔注释行
                    }
                    stackContinuationRegex.matches(line) || stackBuilder.isNotEmpty() -> {
                        if (line.isNotBlank()) {
                            if (stackBuilder.isNotEmpty()) stackBuilder.append('\n')
                            stackBuilder.append(line)
                        }
                    }
                    else -> {
                        if (line.isNotBlank()) {
                            msgBuilder.append('\n').append(line)
                        }
                    }
                }
            }
        }
        flush()
        return results
    }

    /**
     * 导入外部任意受支持的日志文件文本，自动识别格式并结构化解析。
     */
    fun parseExternalLog(fileName: String, rawContent: String): ParsedLogDocument {
        val trimmed = rawContent.trimStart('\uFEFF').trim()
        if (trimmed.isEmpty()) {
            return ParsedLogDocument(
                title = fileName,
                formatLabel = "空文件",
                metadata = emptyMap(),
                entries = emptyList(),
                rawText = ""
            )
        }

        // 1. 尝试识别纯 JSON 格式（日志上传包 / 导入快照 / 提取诊断报告）
        if (trimmed.startsWith("{")) {
            parseJsonDocument(fileName, trimmed)?.let { return it }
        }

        // 2. 尝试识别 Markdown 诊断报告（如果内嵌 ```json 块则优先提取，同时保留原文）
        val embeddedJsonMatch = Regex("""```json\s*([\s\S]*?)```""").find(trimmed)
        if (embeddedJsonMatch != null) {
            val embeddedJson = embeddedJsonMatch.groupValues[1].trim()
            parseJsonDocument(fileName, embeddedJson, rawOverride = trimmed, formatPrefix = "Markdown ")
                ?.let { return it }
        }

        // 3. 尝试识别 CourseSchedule 标准日志格式
        val metadata = LinkedHashMap<String, String>()
        trimmed.lineSequence().take(30).forEach { line ->
            val m = headerCommentRegex.matchEntire(line.trim())
            if (m != null) {
                val key = m.groupValues[1].trim()
                val value = m.groupValues[2].trim()
                if (!key.startsWith("===") && !key.startsWith("---")) {
                    metadata[key] = value
                }
            }
        }
        val standardEntries = parseStandardEntries(trimmed)
        if (standardEntries.isNotEmpty()) {
            return ParsedLogDocument(
                title = fileName,
                formatLabel = "应用标准日志",
                metadata = metadata,
                entries = standardEntries,
                rawText = trimmed
            )
        }

        // 4. 尝试识别 Android Logcat 格式
        val logcatEntries = parseLogcatEntries(trimmed)
        if (logcatEntries.isNotEmpty()) {
            return ParsedLogDocument(
                title = fileName,
                formatLabel = "Android Logcat 日志",
                metadata = metadata,
                entries = logcatEntries,
                rawText = trimmed
            )
        }

        // 5. 兜底按通用文本与异常堆栈逐段解析
        val plainEntries = parsePlainTextEntries(trimmed)
        return ParsedLogDocument(
            title = fileName,
            formatLabel = "通用文本日志",
            metadata = metadata,
            entries = plainEntries,
            rawText = trimmed
        )
    }

    private fun parseJsonDocument(
        fileName: String,
        jsonText: String,
        rawOverride: String? = null,
        formatPrefix: String = ""
    ): ParsedLogDocument? {
        val element = runCatching { json.parseToJsonElement(jsonText) }.getOrNull()
        val obj: JsonObject = element as? JsonObject ?: return null

        // Case A: LogUploadPayload
        if ("entries" in obj && "reportId" in obj) {
            val payload = runCatching {
                json.decodeFromString(LogUploadPayload.serializer(), jsonText)
            }.getOrNull()
            if (payload != null) {
                val meta = linkedMapOf(
                    "报告ID" to payload.reportId,
                    "上传/生成时间" to payload.formattedTime,
                    "App版本" to "${payload.appVersion} (${payload.versionCode})",
                    "设备环境" to "${payload.deviceModel} (SDK ${payload.androidSdk})"
                )
                if (payload.userNote.isNotBlank()) {
                    meta["用户说明"] = payload.userNote
                }
                return ParsedLogDocument(
                    title = fileName,
                    formatLabel = "${formatPrefix}诊断日志包 (JSON)",
                    metadata = meta,
                    entries = payload.entries,
                    rawText = rawOverride ?: jsonText
                )
            }
        }

        // Case B: ImportSnapshot
        if ("rawSnippet" in obj && "courseCount" in obj) {
            val snapshot = runCatching {
                json.decodeFromString(ImportSnapshot.serializer(), jsonText)
            }.getOrNull()
            if (snapshot != null) {
                val meta = linkedMapOf(
                    "快照时间" to snapshot.formattedTime,
                    "导入来源" to snapshot.source,
                    "目标学期" to "${snapshot.semesterName} (${snapshot.totalWeeks}周)",
                    "识别课程数" to "${snapshot.courseCount} 门"
                )
                val entries = ArrayList<LogEntry>()
                if (!snapshot.error.isNullOrBlank()) {
                    entries.add(
                        LogEntry(
                            timestamp = snapshot.timestamp,
                            formattedTime = snapshot.formattedTime,
                            level = LogLevel.ERROR,
                            tag = "ImportSnapshot",
                            message = "导入异常 (${snapshot.source}): ${snapshot.error}"
                        )
                    )
                } else {
                    entries.add(
                        LogEntry(
                            timestamp = snapshot.timestamp,
                            formattedTime = snapshot.formattedTime,
                            level = LogLevel.INFO,
                            tag = "ImportSnapshot",
                            message = "导入快照 (${snapshot.source}): 成功识别 ${snapshot.courseCount} 门课程 · ${snapshot.semesterName}"
                        )
                    )
                }
                snapshot.courses.forEachIndexed { idx, c ->
                    entries.add(
                        LogEntry(
                            timestamp = snapshot.timestamp,
                            formattedTime = snapshot.formattedTime,
                            level = LogLevel.DEBUG,
                            tag = "ParsedCourse#${idx + 1}",
                            message = "${c.name} | 教师:${c.teacher} | 教室:${c.classroom} | 周${c.dayOfWeek} ${c.startSlot}-${c.endSlot}节 (${c.startWeek}-${c.endWeek}周 ${c.weekType})"
                        )
                    )
                }
                if (snapshot.rawSnippet.isNotBlank()) {
                    entries.add(
                        LogEntry(
                            timestamp = snapshot.timestamp,
                            formattedTime = snapshot.formattedTime,
                            level = LogLevel.INFO,
                            tag = "SanitizedRaw",
                            message = "脱敏原始片段 (${snapshot.rawSnippet.length} 字符)",
                            stackTrace = snapshot.rawSnippet.take(4000)
                        )
                    )
                }
                return ParsedLogDocument(
                    title = fileName,
                    formatLabel = "${formatPrefix}课表导入快照 (JSON)",
                    metadata = meta,
                    entries = entries,
                    rawText = rawOverride ?: jsonText
                )
            }
        }

        // Case C: DiagnosticPayload
        if ("incomingCount" in obj && "courses" in obj) {
            val diag = runCatching {
                json.decodeFromString(DiagnosticPayload.serializer(), jsonText)
            }.getOrNull()
            if (diag != null) {
                val now = System.currentTimeMillis()
                val nowStr = formatTimestamp(now)
                val meta = linkedMapOf(
                    "App版本" to diag.appVersion,
                    "设备环境" to "${diag.deviceModel} (SDK ${diag.androidVersion})",
                    "导入来源" to diag.source,
                    "目标学期" to "${diag.semesterName} (${diag.totalWeeks}周)",
                    "识别课程数" to "${diag.incomingCount} 门"
                )
                if (diag.extraNote.isNotBlank()) {
                    meta["用户说明"] = diag.extraNote
                }
                val entries = ArrayList<LogEntry>()
                if (diag.error.isNotBlank()) {
                    entries.add(
                        LogEntry(
                            timestamp = now,
                            formattedTime = nowStr,
                            level = LogLevel.ERROR,
                            tag = "Diagnostic",
                            message = "提取报错 (${diag.source}): ${diag.error}"
                        )
                    )
                } else {
                    entries.add(
                        LogEntry(
                            timestamp = now,
                            formattedTime = nowStr,
                            level = LogLevel.INFO,
                            tag = "Diagnostic",
                            message = "课表提取诊断 (${diag.source}): 共识别 ${diag.incomingCount} 门课"
                        )
                    )
                }
                if (diag.extraNote.isNotBlank()) {
                    entries.add(
                        LogEntry(
                            timestamp = now,
                            formattedTime = nowStr,
                            level = LogLevel.WARN,
                            tag = "UserNote",
                            message = "补充说明: ${diag.extraNote}"
                        )
                    )
                }
                diag.courses.forEachIndexed { idx, c ->
                    entries.add(
                        LogEntry(
                            timestamp = now,
                            formattedTime = nowStr,
                            level = LogLevel.DEBUG,
                            tag = "Course#${idx + 1}",
                            message = "${c.name} | 教师:${c.teacher} | 教室:${c.classroom} | 周${c.dayOfWeek} ${c.startSlot}-${c.endSlot}节 (${c.startWeek}-${c.endWeek}周 ${c.weekType})"
                        )
                    )
                }
                return ParsedLogDocument(
                    title = fileName,
                    formatLabel = "${formatPrefix}提取诊断报告 (JSON)",
                    metadata = meta,
                    entries = entries,
                    rawText = rawOverride ?: jsonText
                )
            }
        }

        return null
    }

    private fun parseLogcatEntries(text: String): List<LogEntry> {
        val entries = ArrayList<LogEntry>()
        var matchedLines = 0
        var openEntry: LogEntry? = null
        val stackBuilder = StringBuilder()

        fun flush() {
            val e = openEntry ?: return
            val stack = stackBuilder.toString().trimEnd().ifBlank { null }
            entries.add(if (stack != null) e.copy(stackTrace = stack) else e)
            openEntry = null
            stackBuilder.setLength(0)
        }

        text.lineSequence().forEach { rawLine ->
            val line = rawLine.trimEnd('\r')
            val m1 = logcatThreadtimeRegex.matchEntire(line)
            val m2 = if (m1 == null) logcatBriefRegex.matchEntire(line) else null

            if (m1 != null) {
                matchedLines++
                val timePart = (m1.groupValues[1] + m1.groupValues[2]).trim()
                val level = LogLevel.fromShortCode(m1.groupValues[3])
                val tag = m1.groupValues[4].trim()
                val msg = m1.groupValues[5]

                val prev = openEntry
                if (prev != null && prev.tag == tag && stackContinuationRegex.matches(msg)) {
                    if (stackBuilder.isNotEmpty()) stackBuilder.append('\n')
                    stackBuilder.append(msg)
                } else {
                    flush()
                    openEntry = LogEntry(
                        timestamp = parseTimestampOrZero(timePart),
                        formattedTime = timePart,
                        level = level,
                        tag = tag,
                        message = msg
                    )
                }
            } else if (m2 != null) {
                matchedLines++
                val timePart = m2.groupValues[1].ifBlank { "-" }
                val level = LogLevel.fromShortCode(m2.groupValues[2])
                val tag = m2.groupValues[3].trim()
                val msg = m2.groupValues[4]

                val prev = openEntry
                if (prev != null && prev.tag == tag && stackContinuationRegex.matches(msg)) {
                    if (stackBuilder.isNotEmpty()) stackBuilder.append('\n')
                    stackBuilder.append(msg)
                } else {
                    flush()
                    openEntry = LogEntry(
                        timestamp = parseTimestampOrZero(timePart),
                        formattedTime = timePart,
                        level = level,
                        tag = tag,
                        message = msg
                    )
                }
            } else if (openEntry != null && stackContinuationRegex.matches(line)) {
                if (stackBuilder.isNotEmpty()) stackBuilder.append('\n')
                stackBuilder.append(line)
            }
        }
        flush()
        return if (matchedLines >= 1) entries else emptyList()
    }

    private fun parsePlainTextEntries(text: String): List<LogEntry> {
        val entries = ArrayList<LogEntry>()
        var currentMsg = ""
        var currentLevel = LogLevel.INFO
        var currentLineNo = 1
        val stackBuilder = StringBuilder()

        fun inferLevel(line: String): LogLevel {
            val upper = line.uppercase()
            return when {
                upper.contains("FATAL EXCEPTION") || upper.contains("UNCAUGHT") || upper.contains("[CRASH]") -> LogLevel.CRASH
                upper.contains("EXCEPTION") || upper.contains("ERROR") || upper.contains("失败") || upper.contains("报错") -> LogLevel.ERROR
                upper.contains("WARN") || upper.contains("警告") -> LogLevel.WARN
                else -> LogLevel.INFO
            }
        }

        fun flush() {
            if (currentMsg.isBlank()) return
            entries.add(
                LogEntry(
                    timestamp = 0L,
                    formattedTime = "L$currentLineNo",
                    level = currentLevel,
                    tag = "Imported",
                    message = currentMsg,
                    stackTrace = stackBuilder.toString().trimEnd().ifBlank { null }
                )
            )
            currentMsg = ""
            stackBuilder.setLength(0)
        }

        var idx = 0
        text.lineSequence().forEach { rawLine ->
            idx++
            val line = rawLine.trimEnd('\r')
            if (line.isBlank()) return@forEach
            if (currentMsg.isNotEmpty() && stackContinuationRegex.matches(line)) {
                if (stackBuilder.isNotEmpty()) stackBuilder.append('\n')
                stackBuilder.append(line)
                if (currentLevel.priority < LogLevel.ERROR.priority) {
                    currentLevel = LogLevel.ERROR
                }
            } else {
                flush()
                currentLineNo = idx
                currentMsg = line
                currentLevel = inferLevel(line)
            }
        }
        flush()
        return entries
    }
}
