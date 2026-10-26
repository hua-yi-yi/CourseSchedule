package com.chen.schedule.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.chen.schedule.domain.model.Course
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

@Serializable
data class ImportSnapshot(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val formattedTime: String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(timestamp)),
    val source: String,
    val semesterName: String,
    val totalWeeks: Int,
    val courseCount: Int,
    val error: String? = null,
    val rawSnippet: String,
    val courses: List<DiagnosticCourseItem> = emptyList()
)

object ImportSnapshotManager {
    private const val SNAPSHOT_DIR_NAME = "import_snapshots"
    private const val MAX_SNAPSHOT_COUNT = 10
    private const val MAX_SNIPPET_LENGTH = 100_000

    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        prettyPrint = false
    }

    private fun getSnapshotDir(context: Context): File {
        val dir = File(context.filesDir, SNAPSHOT_DIR_NAME)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * 对原始网页 HTML 或 JSON/CSV 进行脱敏：
     * 1. 剔除 <head>、<script>、<style> 及顶栏个人信息区；
     * 2. 优先提取课程表格主体（如 Table1 或 table 标签）；
     * 3. 严格正则掩码学号、姓名、身份证、手机号等个人隐私；
     * 4. 截断控制在 100KB 以内，防止过度占用空间。
     */
    fun sanitizeRawContent(raw: String): String {
        if (raw.isBlank()) return ""

        var text = raw

        // 清理无用的样式和脚本
        text = text.replace(Regex("""<script[\s\S]*?</script>""", RegexOption.IGNORE_CASE), "")
        text = text.replace(Regex("""<style[\s\S]*?</style>""", RegexOption.IGNORE_CASE), "")

        // 若是 HTML，优先尝试截取包含课表的 table，丢弃顶栏
        val table1Match = Regex("""<table[^>]*id=["']?Table1["']?[^>]*>[\s\S]*?</table>""", RegexOption.IGNORE_CASE).find(text)
        if (table1Match != null) {
            text = table1Match.value
        } else {
            val tables = Regex("""<table[^>]*>[\s\S]*?</table>""", RegexOption.IGNORE_CASE).findAll(text).toList()
            if (tables.isNotEmpty()) {
                val tableJoined = tables.joinToString("\n\n") { it.value }
                if (tableJoined.length > 300) {
                    text = tableJoined
                }
            }
        }

        // 脱敏学号
        text = text.replace(Regex("""(学号|xh|xhxm|studentId|xh=)[\s:："'=]*([0-9a-zA-Z]{6,16})""", RegexOption.IGNORE_CASE)) {
            "${it.groupValues[1]}:[已脱敏]"
        }
        text = text.replace(Regex("""\b(20[12]\d{7,10})\b"""), "[学号已脱敏]")

        // 脱敏姓名
        text = text.replace(Regex("""(姓名|xm)[\s:："'=]*([\u4e00-\u9fa5]{2,4})""", RegexOption.IGNORE_CASE)) {
            "${it.groupValues[1]}:[姓名已脱敏]"
        }
        text = text.replace(Regex("""([\u4e00-\u9fa5]{2,4})同学""")) {
            "[同学已脱敏]"
        }

        // 脱敏身份证
        text = text.replace(Regex("""\b\d{17}[\dXx]\b"""), "[身份证已脱敏]")

        // 脱敏手机号
        text = text.replace(Regex("""\b1[3-9]\d{9}\b"""), "[手机号已脱敏]")

        // 截断保护
        if (text.length > MAX_SNIPPET_LENGTH) {
            text = text.substring(0, MAX_SNIPPET_LENGTH) + "\n<!-- [源码过长，已截取前100KB] -->"
        }

        return text
    }

    /**
     * 异步记录一次导入快照，并在超出上限时自动淘汰旧快照（保留最近 10 次）
     */
    suspend fun record(
        context: Context,
        source: String,
        semesterName: String,
        totalWeeks: Int,
        courses: List<Course>,
        rawContent: String,
        error: String? = null
    ): ImportSnapshot = withContext(Dispatchers.IO) {
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
        val sanitized = sanitizeRawContent(rawContent)
        val snapshot = ImportSnapshot(
            source = source,
            semesterName = semesterName,
            totalWeeks = totalWeeks,
            courseCount = courses.size,
            error = error,
            rawSnippet = sanitized,
            courses = courseItems
        )

        runCatching {
            val dir = getSnapshotDir(context)
            val file = File(dir, "snapshot_${snapshot.timestamp}_${snapshot.id.take(8)}.json")
            file.writeText(json.encodeToString(ImportSnapshot.serializer(), snapshot), Charsets.UTF_8)

            // 清理超出 10 个的旧文件
            val files = dir.listFiles { f -> f.name.startsWith("snapshot_") && f.name.endsWith(".json") } ?: emptyArray()
            if (files.size > MAX_SNAPSHOT_COUNT) {
                files.sortedByDescending { it.lastModified() }
                    .drop(MAX_SNAPSHOT_COUNT)
                    .forEach { it.delete() }
            }
        }

        snapshot
    }

    /**
     * 读取本地保存的所有快照（按时间倒序）
     */
    suspend fun listSnapshots(context: Context): List<ImportSnapshot> = withContext(Dispatchers.IO) {
        runCatching {
            val dir = getSnapshotDir(context)
            val files = dir.listFiles { f -> f.name.startsWith("snapshot_") && f.name.endsWith(".json") } ?: emptyArray()
            files.sortedByDescending { it.lastModified() }
                .mapNotNull { file ->
                    runCatching {
                        json.decodeFromString(ImportSnapshot.serializer(), file.readText(Charsets.UTF_8))
                    }.getOrNull()
                }
        }.getOrDefault(emptyList())
    }

    /**
     * 删除指定快照
     */
    suspend fun deleteSnapshot(context: Context, id: String) = withContext(Dispatchers.IO) {
        runCatching {
            val dir = getSnapshotDir(context)
            dir.listFiles { f -> f.name.contains(id.take(8)) }?.forEach { it.delete() }
        }
    }

    /**
     * 清空全部本地快照
     */
    suspend fun clearAll(context: Context) = withContext(Dispatchers.IO) {
        runCatching {
            val dir = getSnapshotDir(context)
            dir.listFiles()?.forEach { it.delete() }
        }
    }

    /**
     * 生成适合发送/分享给开发者的排查 Markdown 文本
     */
    fun buildExportMarkdown(snapshot: ImportSnapshot): String {
        val courseRows = if (snapshot.courses.isEmpty()) {
            "*（未识别到任何有效课程安排）*"
        } else {
            val header = "| 课程名 | 教师 | 教室 | 星期 | 节次 | 周次 | 类型 |\n| :--- | :--- | :--- | :--- | :--- | :--- | :--- |\n"
            val rows = snapshot.courses.joinToString("\n") { c ->
                "| ${c.name.replace("|", "/")} | ${c.teacher.replace("|", "/")} | ${c.classroom.replace("|", "/")} | 周${c.dayOfWeek} | ${c.startSlot}-${c.endSlot} | ${c.startWeek}-${c.endWeek} | ${c.weekType} |"
            }
            header + rows
        }

        return buildString {
            appendLine("### 课表导入本地快照诊断报告")
            appendLine("- **快照时间**: ${snapshot.formattedTime}")
            appendLine("- **导入来源**: `${snapshot.source}`")
            appendLine("- **目标学期**: ${snapshot.semesterName} (总周数: ${snapshot.totalWeeks})")
            appendLine("- **提取结果**: 成功识别 ${snapshot.courseCount} 门课程安排")
            if (!snapshot.error.isNullOrBlank()) {
                appendLine("- **错误信息**: `${snapshot.error}`")
            }
            appendLine()
            appendLine("#### 已解析课程列表")
            appendLine(courseRows)
            appendLine()
            if (snapshot.rawSnippet.isNotBlank()) {
                appendLine("<details>")
                appendLine("<summary>点击展开脱敏后的原始网页/表格片段源码</summary>")
                appendLine()
                appendLine("```html")
                appendLine(snapshot.rawSnippet)
                appendLine("```")
                appendLine("</details>")
            }
        }
    }

    /**
     * 生成直达 GitHub Issue 的链接（附带快照数据）
     */
    fun buildGithubIssueUrl(snapshot: ImportSnapshot, repo: String = DiagnosticReporter.GITHUB_REPO): String {
        val title = "【课表提取快照反馈】${snapshot.source} - ${snapshot.courseCount}门课"
        var body = buildExportMarkdown(snapshot)
        // 链接长度安全保护
        if (body.length > 3500) {
            body = buildString {
                appendLine("### 课表导入本地快照诊断报告")
                appendLine("- **快照时间**: ${snapshot.formattedTime}")
                appendLine("- **导入来源**: `${snapshot.source}`")
                appendLine("- **目标学期**: ${snapshot.semesterName} (总周数: ${snapshot.totalWeeks})")
                appendLine("- **提取结果**: 成功识别 ${snapshot.courseCount} 门课程安排")
                if (!snapshot.error.isNullOrBlank()) {
                    appendLine("- **错误信息**: `${snapshot.error}`")
                }
                appendLine()
                appendLine("*(注意：由于表格代码较长，请在 App 快照详情中点击【分享 / 发送给开发者】或【复制完整报告】粘贴在此处)*")
            }
        }
        val encodedTitle = URLEncoder.encode(title, "UTF-8").replace("+", "%20")
        val encodedBody = URLEncoder.encode(body, "UTF-8").replace("+", "%20")
        return "https://github.com/$repo/issues/new?title=$encodedTitle&body=$encodedBody"
    }

    /**
     * 调起系统分享面板（可直接分享到微信、QQ、邮件等任意应用发送给开发者）
     */
    fun shareSnapshot(context: Context, snapshot: ImportSnapshot) {
        val text = buildExportMarkdown(snapshot)
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "课表导入快照诊断报告 - ${snapshot.source}")
            putExtra(Intent.EXTRA_TEXT, text)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = Intent.createChooser(sendIntent, "分享课表诊断快照给开发者").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }
}
