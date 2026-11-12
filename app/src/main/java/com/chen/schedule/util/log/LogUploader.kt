package com.chen.schedule.util.log

import com.chen.schedule.util.DiagnosticReporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.URLEncoder
import java.net.UnknownHostException
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * 日志自主上传器：支持将本地运行/崩溃日志或导入的外部日志打包，由用户主动上报至指定服务器或生成 GitHub Issue。
 */
object LogUploader {

    private const val MAX_UPLOAD_ENTRIES = 300
    private const val MAX_STACK_CHARS = 4000

    private val defaultClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    fun normalizeServerUrl(rawUrl: String): String? {
        val trimmed = rawUrl.trim()
        if (trimmed.isEmpty()) return null
        val candidate = if (trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
        ) {
            trimmed
        } else {
            "http://$trimmed"
        }
        val parsed = candidate.toHttpUrlOrNull() ?: return null
        if (parsed.host.isBlank()) return null
        return parsed.toString()
    }

    /**
     * 构建经过隐私脱敏与体积裁剪的上传数据包（优先保留崩溃与错误日志，再附上最新上下文）。
     */
    fun buildPayload(
        entries: List<LogEntry>,
        appVersion: String,
        versionCode: Int,
        androidSdk: Int,
        deviceModel: String,
        userNote: String = "",
        sourceLabel: String = "runtime",
        importSnapshots: List<ImportSnapshotBrief> = emptyList(),
        nowMillis: Long = System.currentTimeMillis()
    ): LogUploadPayload {
        val selected = selectEntriesForUpload(entries, MAX_UPLOAD_ENTRIES).map { e ->
            e.copy(
                message = LogSanitizer.sanitize(e.message),
                stackTrace = e.stackTrace?.let { LogSanitizer.sanitize(it).take(MAX_STACK_CHARS) }
            )
        }
        val errorCount = selected.count { it.level == LogLevel.ERROR || it.level == LogLevel.CRASH }

        return LogUploadPayload(
            reportId = UUID.randomUUID().toString(),
            uploadedAt = nowMillis,
            formattedTime = LogCodec.formatTimestamp(nowMillis),
            appVersion = appVersion,
            versionCode = versionCode,
            androidSdk = androidSdk,
            deviceModel = deviceModel,
            userNote = LogSanitizer.sanitize(userNote.trim()),
            sourceLabel = sourceLabel,
            entryCount = selected.size,
            errorAndCrashCount = errorCount,
            entries = selected,
            importSnapshots = importSnapshots.take(10)
        )
    }

    private fun selectEntriesForUpload(entries: List<LogEntry>, maxCount: Int): List<LogEntry> {
        if (entries.size <= maxCount) return entries
        val important = entries.filter { it.level == LogLevel.CRASH || it.level == LogLevel.ERROR }
        if (important.size >= maxCount) return important.take(maxCount)
        val remainingQuota = maxCount - important.size
        val importantSet = important.toHashSet()
        val others = entries.filter { it !in importantSet }.take(remainingQuota)
        return (important + others).sortedByDescending { it.timestamp }
    }

    suspend fun upload(
        serverUrl: String,
        payload: LogUploadPayload,
        client: OkHttpClient = defaultClient
    ): Result<String> = withContext(Dispatchers.IO) {
        val normalizedUrl = normalizeServerUrl(serverUrl)
            ?: return@withContext Result.failure(
                IllegalArgumentException("上传地址格式不正确，请填写完整的 http:// 或 https:// 接口地址")
            )

        try {
            val bodyJson = LogCodec.json.encodeToString(LogUploadPayload.serializer(), payload)
            val request = Request.Builder()
                .url(normalizedUrl)
                .header("X-CourseSchedule-Report-Id", payload.reportId)
                .header("X-App-Version", payload.appVersion)
                .post(bodyJson.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                val respBody = response.body?.string().orEmpty().trim()
                if (response.isSuccessful) {
                    val serverMsg = extractServerMessage(respBody)
                    val shortId = payload.reportId.take(8)
                    Result.success(
                        if (!serverMsg.isNullOrBlank()) serverMsg
                        else "日志上传成功（编号: $shortId）"
                    )
                } else {
                    val hint = respBody.take(120).ifBlank { response.message }
                    Result.failure(
                        IllegalStateException(
                            if (hint.isNotBlank()) "服务器返回 HTTP ${response.code}: $hint"
                            else "服务器返回错误状态码: HTTP ${response.code}"
                        )
                    )
                }
            }
        } catch (e: SocketTimeoutException) {
            Result.failure(IllegalStateException("上传超时，请检查日志服务器是否在线或网络是否通畅", e))
        } catch (e: ConnectException) {
            Result.failure(IllegalStateException("无法连接到日志服务器，请确认服务器地址与端口已开启", e))
        } catch (e: UnknownHostException) {
            Result.failure(IllegalStateException("无法解析服务器域名，请检查网络连接或服务器地址", e))
        } catch (e: Exception) {
            Result.failure(IllegalStateException("上传失败: ${e.message ?: e.javaClass.simpleName}", e))
        }
    }

    private fun extractServerMessage(respBody: String): String? {
        if (respBody.isBlank()) return null
        if (respBody.startsWith("{")) {
            val obj = runCatching { LogCodec.json.parseToJsonElement(respBody) as? JsonObject }.getOrNull()
            if (obj != null) {
                val msg = obj["message"]?.jsonPrimitive?.contentOrNull
                    ?: obj["msg"]?.jsonPrimitive?.contentOrNull
                if (!msg.isNullOrBlank()) return msg
            }
        }
        return if (respBody.length <= 100 && !respBody.startsWith("<")) respBody else null
    }

    fun buildMarkdownReport(payload: LogUploadPayload): String {
        return buildString {
            appendLine("### 应用运行日志与诊断反馈")
            appendLine("- **报告编号**: `${payload.reportId.take(8)}`")
            appendLine("- **生成时间**: ${payload.formattedTime}")
            appendLine("- **App 版本**: ${payload.appVersion} (${payload.versionCode})")
            appendLine("- **系统/设备**: Android SDK ${payload.androidSdk} (${payload.deviceModel})")
            appendLine("- **日志来源**: `${payload.sourceLabel}`")
            appendLine("- **日志统计**: 共 ${payload.entryCount} 条（错误/崩溃 ${payload.errorAndCrashCount} 条）")
            if (payload.userNote.isNotBlank()) {
                appendLine("- **补充说明**: ${payload.userNote}")
            }
            if (payload.importSnapshots.isNotEmpty()) {
                appendLine()
                appendLine("#### 最近课表导入记录摘要")
                payload.importSnapshots.take(5).forEach { s ->
                    val status = if (s.error.isNullOrBlank()) "成功 (${s.courseCount} 门)" else "异常: ${s.error}"
                    appendLine("- `${s.formattedTime}` · ${s.source} · ${s.semesterName} · $status")
                }
            }
            appendLine()
            appendLine("#### 关键日志摘录")
            appendLine("```text")
            val previewEntries = payload.entries.take(40)
            if (previewEntries.isEmpty()) {
                appendLine("（暂无日志条目）")
            } else {
                previewEntries.forEach { e ->
                    append(LogCodec.formatEntry(e))
                }
            }
            appendLine("```")
        }
    }

    fun buildGithubIssueUrl(payload: LogUploadPayload, repo: String = DiagnosticReporter.GITHUB_REPO): String {
        val title = "【日志诊断反馈】v${payload.appVersion} - 错误/崩溃 ${payload.errorAndCrashCount} 条"
        var body = buildMarkdownReport(payload)
        if (body.length > 3200) {
            val compactPayload = payload.copy(entries = payload.entries.take(8))
            body = buildMarkdownReport(compactPayload) +
                "\n\n*(日志较长，完整内容已同步复制到剪贴板，请在下方直接粘贴补充)*"
        }
        val encodedTitle = URLEncoder.encode(title, "UTF-8").replace("+", "%20")
        val encodedBody = URLEncoder.encode(body, "UTF-8").replace("+", "%20")
        return "https://github.com/$repo/issues/new?title=$encodedTitle&body=$encodedBody"
    }
}
