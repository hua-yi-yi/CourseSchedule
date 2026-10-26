package com.chen.schedule.util

import android.content.Context
import android.os.Build
import com.chen.schedule.domain.model.Course
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

@Serializable
data class DiagnosticCourseItem(
    val name: String,
    val teacher: String,
    val classroom: String,
    val dayOfWeek: Int,
    val startSlot: Int,
    val endSlot: Int,
    val startWeek: Int,
    val endWeek: Int,
    val weekType: String
)

@Serializable
data class DiagnosticPayload(
    val appVersion: String = com.chen.schedule.BuildConfig.VERSION_NAME,
    val androidVersion: Int = Build.VERSION.SDK_INT,
    val deviceModel: String = "${Build.MANUFACTURER} ${Build.MODEL}",
    val source: String,
    val semesterName: String,
    val totalWeeks: Int,
    val incomingCount: Int,
    val courses: List<DiagnosticCourseItem>,
    val error: String = "",
    val extraNote: String = ""
)

object DiagnosticReporter {
    private const val PREFS_NAME = "diagnostic_prefs"
    private const val KEY_SERVER_URL = "server_url"
    const val DEFAULT_SERVER_URL = "http://192.168.0.135:8765/api/report"
    const val GITHUB_REPO = "hua-yi-yi/CourseSchedule"

    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    fun getServerUrl(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_SERVER_URL, DEFAULT_SERVER_URL) ?: DEFAULT_SERVER_URL
    }

    fun setServerUrl(context: Context, url: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SERVER_URL, url.trim())
            .apply()
    }

    fun buildPayload(
        source: String,
        semesterName: String,
        totalWeeks: Int,
        courses: List<Course>,
        error: String = "",
        note: String = ""
    ): DiagnosticPayload {
        val courseItems = courses.map { c ->
            DiagnosticCourseItem(
                name = c.name,
                teacher = c.teacher,
                classroom = c.classroom,
                dayOfWeek = c.dayOfWeek,
                startSlot = c.startSlot,
                endSlot = c.endSlot,
                startWeek = c.startWeek,
                endWeek = c.endWeek,
                weekType = c.weekType.name
            )
        }
        return DiagnosticPayload(
            source = source,
            semesterName = semesterName,
            totalWeeks = totalWeeks,
            incomingCount = courses.size,
            courses = courseItems,
            error = error,
            extraNote = note
        )
    }

    fun toJsonString(payload: DiagnosticPayload): String {
        return json.encodeToString(DiagnosticPayload.serializer(), payload)
    }

    fun buildMarkdownReport(payload: DiagnosticPayload): String {
        val courseRows = if (payload.courses.isEmpty()) {
            "*（未识别到任何课程）*"
        } else {
            val header = "| 课程名 | 教师 | 教室 | 星期 | 节次 | 周次 | 类型 |\n| :--- | :--- | :--- | :--- | :--- | :--- | :--- |\n"
            val rows = payload.courses.joinToString("\n") { c ->
                "| ${c.name.replace("|", "/")} | ${c.teacher.replace("|", "/")} | ${c.classroom.replace("|", "/")} | 周${c.dayOfWeek} | ${c.startSlot}-${c.endSlot} | ${c.startWeek}-${c.endWeek} | ${c.weekType} |"
            }
            header + rows
        }

        return buildString {
            appendLine("### 课表提取问题诊断反馈")
            appendLine("- **App 版本**: ${payload.appVersion}")
            appendLine("- **系统/设备**: Android SDK ${payload.androidVersion} (${payload.deviceModel})")
            appendLine("- **导入来源**: `${payload.source}`")
            appendLine("- **目标学期**: ${payload.semesterName} (总周数: ${payload.totalWeeks})")
            appendLine("- **解析到的课程数**: ${payload.incomingCount} 门")
            if (payload.extraNote.isNotBlank()) {
                appendLine("- **用户说明**: ${payload.extraNote}")
            }
            if (payload.error.isNotBlank()) {
                appendLine("- **错误信息**: `${payload.error}`")
            }
            appendLine()
            appendLine("#### 已解析课程列表预览")
            appendLine(courseRows)
            appendLine()
            appendLine("<details>")
            appendLine("<summary>点击展开完整诊断 JSON 数据</summary>")
            appendLine()
            appendLine("```json")
            appendLine(toJsonString(payload))
            appendLine("```")
            appendLine("</details>")
        }
    }

    fun buildGithubIssueUrl(payload: DiagnosticPayload, repo: String = GITHUB_REPO): String {
        val title = "【提取异常】${payload.source} - 识别到${payload.incomingCount}门课"
        var body = buildMarkdownReport(payload)
        if (body.length > 3500 && payload.courses.size > 10) {
            val truncatedPayload = payload.copy(courses = payload.courses.take(10))
            body = buildMarkdownReport(truncatedPayload) + "\n\n*(注意：课程条目较多，URL已截取前10门预览，完整JSON请在App点击【复制诊断文本】补充贴上)*"
        }
        val encodedTitle = URLEncoder.encode(title, "UTF-8").replace("+", "%20")
        val encodedBody = URLEncoder.encode(body, "UTF-8").replace("+", "%20")
        return "https://github.com/$repo/issues/new?title=$encodedTitle&body=$encodedBody"
    }

    suspend fun upload(serverUrl: String, payload: DiagnosticPayload): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val bodyString = toJsonString(payload)
            val req = Request.Builder()
                .url(serverUrl)
                .post(bodyString.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()
            client.newCall(req).execute().use { res ->
                if (res.isSuccessful) {
                    res.body?.string() ?: "上报成功"
                } else {
                    error("服务器返回错误状态码: HTTP ${res.code}")
                }
            }
        }
    }
}
