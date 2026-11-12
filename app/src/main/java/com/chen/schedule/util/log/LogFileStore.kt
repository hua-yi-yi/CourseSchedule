package com.chen.schedule.util.log

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * 本地日志滚动存储引擎（纯 File 实现，支持运行时日志、崩溃日志与外部导入日志隔离管理）。
 *
 * 目录结构 (`filesDir/logs/`)：
 * - `runtime/`：日常运行日志，单文件超过 512KB 自动滚动切分，最多保留最近 7 个文件。
 * - `crash/`：未捕获崩溃专属留存，独立保留最近 10 次崩溃记录，不被日常日志冲刷。
 * - `imported/`：用户在设置页专门导入的外部日志文件与元数据，保留最近 10 份以便随时对照排查。
 */
class LogFileStore(
    private val baseDir: File,
    private val maxSingleFileBytes: Long = DEFAULT_MAX_SINGLE_FILE_BYTES,
    private val maxRuntimeFiles: Int = DEFAULT_MAX_RUNTIME_FILES,
    private val maxCrashFiles: Int = DEFAULT_MAX_CRASH_FILES,
    private val maxImportedFiles: Int = DEFAULT_MAX_IMPORTED_FILES
) {
    companion object {
        const val DEFAULT_MAX_SINGLE_FILE_BYTES = 512 * 1024L
        const val DEFAULT_MAX_RUNTIME_FILES = 7
        const val DEFAULT_MAX_CRASH_FILES = 10
        const val DEFAULT_MAX_IMPORTED_FILES = 10
        const val MAX_IMPORTED_TEXT_CHARS = 500_000
    }

    private val lock = Any()

    private val runtimeDir: File get() = File(baseDir, "runtime").also { if (!it.exists()) it.mkdirs() }
    private val crashDir: File get() = File(baseDir, "crash").also { if (!it.exists()) it.mkdirs() }
    private val importedDir: File get() = File(baseDir, "imported").also { if (!it.exists()) it.mkdirs() }

    fun appendEntry(entry: LogEntry) {
        val formatted = LogCodec.formatEntry(entry)
        synchronized(lock) {
            val dayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(entry.timestamp))
            var target = File(runtimeDir, "app-$dayStr.log")
            if (target.exists() && target.length() >= maxSingleFileBytes) {
                val rollSuffix = SimpleDateFormat("HHmmss-SSS", Locale.US).format(Date(entry.timestamp))
                val rolled = File(runtimeDir, "app-${dayStr}_$rollSuffix.log")
                target.renameTo(rolled)
                target = File(runtimeDir, "app-$dayStr.log")
            }
            target.appendText(formatted, Charsets.UTF_8)
            pruneFiles(runtimeDir, prefix = "app-", suffix = ".log", keepCount = maxRuntimeFiles)
        }
    }

    fun recordCrashSync(entry: LogEntry, deviceMetadata: Map<String, String>) {
        val crashEntry = if (entry.level != LogLevel.CRASH) entry.copy(level = LogLevel.CRASH) else entry
        val crashDoc = LogCodec.buildExportDocument(
            entries = listOf(crashEntry),
            metadata = deviceMetadata
        )
        synchronized(lock) {
            val stamp = SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US).format(Date(crashEntry.timestamp))
            val crashFile = File(crashDir, "crash-$stamp.log")
            crashFile.writeText(crashDoc, Charsets.UTF_8)
            pruneFiles(crashDir, prefix = "crash-", suffix = ".log", keepCount = maxCrashFiles)
        }
        // 同时写入当前运行日志流，保证按时间线回看时上下文连贯
        runCatching { appendEntry(crashEntry) }
    }

    /**
     * 读取最近的运行与崩溃日志（按时间倒序排列：最新在最前）。
     */
    fun readRecentEntries(limit: Int = 500): List<LogEntry> {
        if (limit <= 0) return emptyList()
        return synchronized(lock) {
            val runtimeFiles = listSortedOldestFirst(runtimeDir, "app-", ".log")
            val chronological = ArrayList<LogEntry>()
            runtimeFiles.forEach { file ->
                runCatching {
                    chronological.addAll(LogCodec.parseStandardEntries(file.readText(Charsets.UTF_8)))
                }
            }

            // 补充可能因日常日志轮转而仅存于 crashDir 的历史崩溃条目
            val existingCrashKeys = chronological
                .asSequence()
                .filter { it.level == LogLevel.CRASH }
                .map { "${it.formattedTime}|${it.message}" }
                .toHashSet()

            val extraCrashes = ArrayList<LogEntry>()
            listSortedOldestFirst(crashDir, "crash-", ".log").forEach { file ->
                runCatching {
                    LogCodec.parseStandardEntries(file.readText(Charsets.UTF_8)).forEach { c ->
                        val key = "${c.formattedTime}|${c.message}"
                        if (existingCrashKeys.add(key)) {
                            extraCrashes.add(c)
                        }
                    }
                }
            }

            val merged = if (extraCrashes.isEmpty()) {
                chronological
            } else {
                (extraCrashes + chronological).sortedBy { it.timestamp }
            }

            merged.takeLast(limit).asReversed()
        }
    }

    fun getStats(): LogStorageStats {
        return synchronized(lock) {
            val rFiles = runtimeDir.listFiles { f -> f.isFile && f.name.endsWith(".log") } ?: emptyArray()
            val cFiles = crashDir.listFiles { f -> f.isFile && f.name.endsWith(".log") } ?: emptyArray()
            val iMetaFiles = importedDir.listFiles { f -> f.isFile && f.name.endsWith(".meta.json") } ?: emptyArray()
            val iAllFiles = importedDir.listFiles { f -> f.isFile } ?: emptyArray()

            val totalBytes = rFiles.sumOf { it.length() } +
                cFiles.sumOf { it.length() } +
                iAllFiles.sumOf { it.length() }

            val latestCrashFile = cFiles.maxByOrNull { it.lastModified() }
            val latestCrashTime = latestCrashFile?.let {
                SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(it.lastModified()))
            }

            LogStorageStats(
                runtimeFileCount = rFiles.size,
                crashFileCount = cFiles.size,
                importedFileCount = iMetaFiles.size,
                totalBytes = totalBytes,
                latestCrashTime = latestCrashTime
            )
        }
    }

    fun clearRuntimeAndCrashLogs() {
        synchronized(lock) {
            runtimeDir.listFiles()?.forEach { it.delete() }
            crashDir.listFiles()?.forEach { it.delete() }
        }
    }

    /**
     * 保存并解析从外部导入的日志文件（保留最近 [maxImportedFiles] 份）。
     */
    fun saveImportedLog(fileName: String, rawContent: String): Pair<ImportedLogMeta, ParsedLogDocument> {
        val safeName = fileName.ifBlank { "imported.log" }
        val boundedContent = if (rawContent.length > MAX_IMPORTED_TEXT_CHARS) {
            rawContent.substring(0, MAX_IMPORTED_TEXT_CHARS) +
                "\n# [提示：外部日志文件较大，已截取前 ${MAX_IMPORTED_TEXT_CHARS / 1000}K 字符]"
        } else {
            rawContent
        }
        val sanitizedContent = LogSanitizer.sanitize(boundedContent)
        val parsed = LogCodec.parseExternalLog(safeName, sanitizedContent)
        val now = System.currentTimeMillis()
        val id = "${now}_${UUID.randomUUID().toString().take(8)}"
        val meta = ImportedLogMeta(
            id = id,
            fileName = safeName,
            importedAt = now,
            formattedImportedAt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(now)),
            fileSizeBytes = sanitizedContent.toByteArray(Charsets.UTF_8).size.toLong(),
            formatLabel = parsed.formatLabel,
            totalEntries = parsed.entries.size,
            errorAndCrashCount = parsed.errorAndCrashCount,
            warnCount = parsed.warnCount
        )

        synchronized(lock) {
            val rawFile = File(importedDir, "$id.raw.txt")
            val metaFile = File(importedDir, "$id.meta.json")
            rawFile.writeText(sanitizedContent, Charsets.UTF_8)
            metaFile.writeText(LogCodec.json.encodeToString(ImportedLogMeta.serializer(), meta), Charsets.UTF_8)
            pruneImportedLogsLocked()
        }
        return meta to parsed
    }

    fun listImportedLogs(): List<ImportedLogMeta> {
        return synchronized(lock) {
            val metaFiles = importedDir.listFiles { f -> f.isFile && f.name.endsWith(".meta.json") } ?: emptyArray()
            metaFiles.mapNotNull { file ->
                runCatching {
                    LogCodec.json.decodeFromString(ImportedLogMeta.serializer(), file.readText(Charsets.UTF_8))
                }.getOrNull()
            }.sortedByDescending { it.importedAt }
        }
    }

    fun loadImportedLog(id: String): ParsedLogDocument? {
        val safeId = id.replace(Regex("""[^0-9a-zA-Z_-]"""), "")
        if (safeId.isBlank()) return null
        return synchronized(lock) {
            val metaFile = File(importedDir, "$safeId.meta.json")
            val rawFile = File(importedDir, "$safeId.raw.txt")
            if (!rawFile.exists()) return@synchronized null
            val fileName = runCatching {
                LogCodec.json.decodeFromString(ImportedLogMeta.serializer(), metaFile.readText(Charsets.UTF_8)).fileName
            }.getOrDefault("$safeId.log")
            val rawText = runCatching { rawFile.readText(Charsets.UTF_8) }.getOrNull() ?: return@synchronized null
            LogCodec.parseExternalLog(fileName, rawText)
        }
    }

    fun deleteImportedLog(id: String) {
        val safeId = id.replace(Regex("""[^0-9a-zA-Z_-]"""), "")
        if (safeId.isBlank()) return
        synchronized(lock) {
            File(importedDir, "$safeId.meta.json").delete()
            File(importedDir, "$safeId.raw.txt").delete()
        }
    }

    fun clearImportedLogs() {
        synchronized(lock) {
            importedDir.listFiles()?.forEach { it.delete() }
        }
    }

    private fun pruneImportedLogsLocked() {
        val metas = listImportedLogs()
        if (metas.size > maxImportedFiles) {
            metas.drop(maxImportedFiles).forEach { old ->
                File(importedDir, "${old.id}.meta.json").delete()
                File(importedDir, "${old.id}.raw.txt").delete()
            }
        }
    }

    private fun listSortedOldestFirst(dir: File, prefix: String, suffix: String): List<File> {
        val files = dir.listFiles { f -> f.isFile && f.name.startsWith(prefix) && f.name.endsWith(suffix) }
            ?: return emptyList()
        return files.sortedWith(compareBy<File> { it.lastModified() }.thenBy { it.name })
    }

    private fun pruneFiles(dir: File, prefix: String, suffix: String, keepCount: Int) {
        val files = dir.listFiles { f -> f.isFile && f.name.startsWith(prefix) && f.name.endsWith(suffix) }
            ?: return
        if (files.size <= keepCount) return
        files.sortedWith(compareByDescending<File> { it.lastModified() }.thenByDescending { it.name })
            .drop(keepCount)
            .forEach { it.delete() }
    }
}
