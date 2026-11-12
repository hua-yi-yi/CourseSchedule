package com.chen.schedule.util.log

import kotlinx.serialization.Serializable

@Serializable
enum class LogLevel(val shortCode: String, val label: String, val priority: Int) {
    DEBUG("D", "调试", 1),
    INFO("I", "信息", 2),
    WARN("W", "警告", 3),
    ERROR("E", "错误", 4),
    CRASH("C", "崩溃", 5);

    companion object {
        fun fromShortCode(code: String): LogLevel {
            return when (code.uppercase()) {
                "D", "DEBUG", "V", "VERBOSE" -> DEBUG
                "I", "INFO" -> INFO
                "W", "WARN", "WARNING" -> WARN
                "E", "ERROR" -> ERROR
                "C", "CRASH", "F", "FATAL", "WTF" -> CRASH
                else -> INFO
            }
        }
    }
}

@Serializable
data class LogEntry(
    val timestamp: Long,
    val formattedTime: String,
    val level: LogLevel,
    val tag: String,
    val message: String,
    val stackTrace: String? = null
)

data class LogStorageStats(
    val runtimeFileCount: Int = 0,
    val crashFileCount: Int = 0,
    val importedFileCount: Int = 0,
    val totalBytes: Long = 0L,
    val latestCrashTime: String? = null
) {
    fun formatSize(): String = formatByteSize(totalBytes)

    companion object {
        fun formatByteSize(bytes: Long): String {
            if (bytes <= 0L) return "0 B"
            if (bytes < 1024L) return "$bytes B"
            val kb = bytes / 1024.0
            if (kb < 1024.0) return String.format(java.util.Locale.US, "%.1f KB", kb)
            val mb = kb / 1024.0
            return String.format(java.util.Locale.US, "%.2f MB", mb)
        }
    }
}

@Serializable
data class ImportedLogMeta(
    val id: String,
    val fileName: String,
    val importedAt: Long,
    val formattedImportedAt: String,
    val fileSizeBytes: Long,
    val formatLabel: String,
    val totalEntries: Int,
    val errorAndCrashCount: Int,
    val warnCount: Int
)

data class ParsedLogDocument(
    val title: String,
    val formatLabel: String,
    val metadata: Map<String, String>,
    val entries: List<LogEntry>,
    val rawText: String
) {
    val errorAndCrashCount: Int
        get() = entries.count { it.level == LogLevel.ERROR || it.level == LogLevel.CRASH }

    val warnCount: Int
        get() = entries.count { it.level == LogLevel.WARN }
}

@Serializable
data class ImportSnapshotBrief(
    val formattedTime: String,
    val source: String,
    val semesterName: String,
    val courseCount: Int,
    val error: String? = null
)

@Serializable
data class LogUploadPayload(
    val reportId: String,
    val uploadedAt: Long,
    val formattedTime: String,
    val appVersion: String,
    val versionCode: Int,
    val androidSdk: Int,
    val deviceModel: String,
    val userNote: String = "",
    val sourceLabel: String = "runtime",
    val entryCount: Int,
    val errorAndCrashCount: Int,
    val entries: List<LogEntry>,
    val importSnapshots: List<ImportSnapshotBrief> = emptyList()
)
