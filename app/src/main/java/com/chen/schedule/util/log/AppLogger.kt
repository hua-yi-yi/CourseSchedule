package com.chen.schedule.util.log

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import com.chen.schedule.BuildConfig
import com.chen.schedule.util.DiagnosticReporter
import com.chen.schedule.util.ImportSnapshotManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 全局应用日志门面与崩溃捕获管理器。
 *
 * - 支持异步单线程顺序落盘，不阻塞主线程；
 * - 注册 UncaughtExceptionHandler，发生闪退时同步保存完整堆栈与设备环境；
 * - 所有写入内容均经过 [LogSanitizer] 自动脱敏；
 * - 支持在设置中开关本地保存、导出/分享、导入外部日志解析以及用户自主上传。
 */
object AppLogger {

    private const val PREFS_NAME = "diagnostic_prefs"
    private const val KEY_LOG_SAVE_ENABLED = "log_save_enabled"
    private const val MAX_PRE_INIT_BUFFER = 60

    private val initialized = AtomicBoolean(false)
    private val crashHookInstalled = AtomicBoolean(false)
    private val preInitBuffer = ConcurrentLinkedQueue<LogEntry>()

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var fileStore: LogFileStore? = null

    @Volatile
    private var saveEnabled: Boolean = true

    private val writerDispatcher = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "CourseSchedule-Logger").apply { isDaemon = true }
    }.asCoroutineDispatcher()

    private val writerScope = CoroutineScope(SupervisorJob() + writerDispatcher)

    fun init(context: Context) {
        val appCtx = context.applicationContext
        appContext = appCtx
        val prefs = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        saveEnabled = prefs.getBoolean(KEY_LOG_SAVE_ENABLED, true)

        val store = LogFileStore(File(appCtx.filesDir, "logs"))
        fileStore = store

        installCrashHandlerIfNeeded(store)

        if (initialized.compareAndSet(false, true)) {
            writerScope.launch {
                while (true) {
                    val buffered = preInitBuffer.poll() ?: break
                    if (saveEnabled) {
                        runCatching { store.appendEntry(buffered) }
                    }
                }
            }
            i(
                "AppStartup",
                "应用初始化完成: v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}), SDK ${Build.VERSION.SDK_INT}, ${Build.MANUFACTURER} ${Build.MODEL}"
            )
        }
    }

    private fun installCrashHandlerIfNeeded(store: LogFileStore) {
        if (!crashHookInstalled.compareAndSet(false, true)) return
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                if (saveEnabled) {
                    val now = System.currentTimeMillis()
                    val crashEntry = LogEntry(
                        timestamp = now,
                        formattedTime = LogCodec.formatTimestamp(now),
                        level = LogLevel.CRASH,
                        tag = "UncaughtCrash",
                        message = "线程 [${thread.name}] 未捕获异常: ${throwable.javaClass.name}: ${throwable.message.orEmpty()}",
                        stackTrace = LogCodec.throwableToString(throwable)
                    )
                    store.recordCrashSync(crashEntry, buildDeviceMetadata())
                }
            }
            previousHandler?.uncaughtException(thread, throwable)
        }
    }

    fun isSaveEnabled(context: Context? = appContext): Boolean {
        val ctx = context?.applicationContext ?: return saveEnabled
        val enabled = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_LOG_SAVE_ENABLED, true)
        saveEnabled = enabled
        return enabled
    }

    fun setSaveEnabled(context: Context, enabled: Boolean) {
        saveEnabled = enabled
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_LOG_SAVE_ENABLED, enabled)
            .apply()
        if (enabled) {
            i("LogSettings", "用户已开启本地运行日志自动保存")
        }
    }

    fun getServerUrl(context: Context): String = DiagnosticReporter.getServerUrl(context)

    fun setServerUrl(context: Context, url: String) = DiagnosticReporter.setServerUrl(context, url)

    fun d(tag: String, message: String) = log(LogLevel.DEBUG, tag, message, null)

    fun i(tag: String, message: String) = log(LogLevel.INFO, tag, message, null)

    fun w(tag: String, message: String, throwable: Throwable? = null) =
        log(LogLevel.WARN, tag, message, throwable)

    fun e(tag: String, message: String, throwable: Throwable? = null) =
        log(LogLevel.ERROR, tag, message, throwable)

    private fun log(level: LogLevel, tag: String, message: String, throwable: Throwable?) {
        val sanitizedMessage = LogSanitizer.sanitize(message)
        emitToLogcat(level, tag, sanitizedMessage, throwable)

        if (!saveEnabled) return

        val now = System.currentTimeMillis()
        val entry = LogEntry(
            timestamp = now,
            formattedTime = LogCodec.formatTimestamp(now),
            level = level,
            tag = tag,
            message = sanitizedMessage,
            stackTrace = LogCodec.throwableToString(throwable)
        )

        val store = fileStore
        if (store == null) {
            if (preInitBuffer.size < MAX_PRE_INIT_BUFFER) {
                preInitBuffer.offer(entry)
            }
            return
        }

        writerScope.launch {
            runCatching { store.appendEntry(entry) }
        }
    }

    private fun emitToLogcat(level: LogLevel, tag: String, message: String, throwable: Throwable?) {
        runCatching {
            when (level) {
                LogLevel.DEBUG -> Log.d(tag, message)
                LogLevel.INFO -> Log.i(tag, message)
                LogLevel.WARN -> if (throwable != null) Log.w(tag, message, throwable) else Log.w(tag, message)
                LogLevel.ERROR, LogLevel.CRASH -> if (throwable != null) Log.e(tag, message, throwable) else Log.e(tag, message)
            }
        }
    }

    private fun storeFor(context: Context): LogFileStore {
        return fileStore ?: LogFileStore(File(context.applicationContext.filesDir, "logs")).also {
            fileStore = it
        }
    }

    fun buildDeviceMetadata(): Map<String, String> = linkedMapOf(
        "App版本" to "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
        "系统环境" to "Android SDK ${Build.VERSION.SDK_INT} (${Build.VERSION.RELEASE})",
        "设备型号" to "${Build.MANUFACTURER} ${Build.MODEL}",
        "导出时间" to LogCodec.formatTimestamp(System.currentTimeMillis())
    )

    suspend fun getStorageStats(context: Context): LogStorageStats = withContext(Dispatchers.IO) {
        storeFor(context).getStats()
    }

    suspend fun readRecentEntries(context: Context, limit: Int = 500): List<LogEntry> =
        withContext(Dispatchers.IO) {
            storeFor(context).readRecentEntries(limit)
        }

    suspend fun clearRuntimeLogs(context: Context) = withContext(Dispatchers.IO) {
        storeFor(context).clearRuntimeAndCrashLogs()
    }

    suspend fun listImportedLogs(context: Context): List<ImportedLogMeta> =
        withContext(Dispatchers.IO) {
            storeFor(context).listImportedLogs()
        }

    suspend fun loadImportedLog(context: Context, id: String): ParsedLogDocument? =
        withContext(Dispatchers.IO) {
            storeFor(context).loadImportedLog(id)
        }

    suspend fun deleteImportedLog(context: Context, id: String) = withContext(Dispatchers.IO) {
        storeFor(context).deleteImportedLog(id)
    }

    suspend fun clearImportedLogs(context: Context) = withContext(Dispatchers.IO) {
        storeFor(context).clearImportedLogs()
    }

    suspend fun importLogFromUri(context: Context, uri: Uri): Pair<ImportedLogMeta, ParsedLogDocument> =
        withContext(Dispatchers.IO) {
            val fileName = resolveDisplayName(context, uri)
            val rawText = context.contentResolver.openInputStream(uri)?.use { stream ->
                // 限制最大读取字节数（2MB），防止误选超大二进制文件导致内存溢出
                val maxBytes = 2 * 1024 * 1024
                val buffer = ByteArray(8192)
                val out = java.io.ByteArrayOutputStream()
                var total = 0
                while (total < maxBytes) {
                    val read = stream.read(buffer, 0, minOf(buffer.size, maxBytes - total))
                    if (read <= 0) break
                    out.write(buffer, 0, read)
                    total += read
                }
                out.toString(Charsets.UTF_8.name())
            } ?: error("无法打开所选日志文件")

            if (rawText.isBlank()) {
                error("所选日志文件为空")
            }
            // 校验是否含有大量二进制控制字符（例如误选了图片或 APK）
            val controlChars = rawText.take(1024).count { ch ->
                ch.code < 32 && ch != '\n' && ch != '\r' && ch != '\t'
            }
            if (controlChars > 32) {
                error("所选文件不是有效的文本或 JSON 日志文件")
            }

            val result = storeFor(context).saveImportedLog(fileName, rawText)
            i("LogImport", "成功导入外部日志文件: $fileName (${result.second.formatLabel}, ${result.second.entries.size} 条)")
            result
        }

    private fun resolveDisplayName(context: Context, uri: Uri): String {
        if (uri.scheme == "content") {
            runCatching {
                context.contentResolver.query(
                    uri,
                    arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val name = cursor.getString(0)
                        if (!name.isNullOrBlank()) return name
                    }
                }
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/')?.ifBlank { null } ?: "imported_log.log"
    }

    suspend fun buildExportText(
        context: Context,
        includeSnapshots: Boolean = true,
        limit: Int = 500
    ): String = withContext(Dispatchers.IO) {
        val entries = storeFor(context).readRecentEntries(limit).asReversed() // 导出文件按时间正序更易阅读
        val snapshots = if (includeSnapshots) loadSnapshotBriefs(context) else emptyList()
        LogCodec.buildExportDocument(
            entries = entries,
            metadata = buildDeviceMetadata(),
            importSnapshots = snapshots
        )
    }

    suspend fun loadSnapshotBriefs(context: Context): List<ImportSnapshotBrief> =
        withContext(Dispatchers.IO) {
            ImportSnapshotManager.listSnapshots(context).map { s ->
                ImportSnapshotBrief(
                    formattedTime = s.formattedTime,
                    source = s.source,
                    semesterName = s.semesterName,
                    courseCount = s.courseCount,
                    error = s.error
                )
            }
        }

    suspend fun exportToUri(context: Context, uri: Uri, includeSnapshots: Boolean = true) =
        withContext(Dispatchers.IO) {
            val content = buildExportText(context, includeSnapshots = includeSnapshots)
            val out = context.contentResolver.openOutputStream(uri) ?: error("无法写入目标文件")
            out.use { it.write(content.toByteArray(Charsets.UTF_8)) }
            i("LogExport", "已导出运行日志到本地文件 (${content.length} 字符)")
        }

    suspend fun shareLogFile(
        context: Context,
        fileName: String,
        content: String,
        chooserTitle: String = "分享诊断日志"
    ) = withContext(Dispatchers.IO) {
        val shareIntent = runCatching {
            val exportDir = File(context.cacheDir, "log_exports").apply { mkdirs() }
            // 清理过期临时分享缓存
            exportDir.listFiles()?.forEach { old ->
                if (System.currentTimeMillis() - old.lastModified() > 24 * 3600 * 1000L) {
                    old.delete()
                }
            }
            val safeFileName = fileName.replace(Regex("""[^0-9a-zA-Z._\-\u4e00-\u9fa5]"""), "_")
            val tempFile = File(exportDir, safeFileName)
            tempFile.writeText(content, Charsets.UTF_8)
            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                tempFile
            )
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, safeFileName)
                putExtra(Intent.EXTRA_STREAM, contentUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }.getOrElse {
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, fileName)
                putExtra(Intent.EXTRA_TEXT, content.take(80_000))
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }

        withContext(Dispatchers.Main) {
            val chooser = Intent.createChooser(shareIntent, chooserTitle).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        }
    }
}
