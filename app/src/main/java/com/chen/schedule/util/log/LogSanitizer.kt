package com.chen.schedule.util.log

/**
 * 日志脱敏工具：确保落盘与上传的运行日志中不包含账号、密码、Cookie、令牌及个人身份隐私。
 */
object LogSanitizer {

    private val secretKeyValueRegex = Regex(
        """(?i)\b(password|passwd|pwd|txtSecretCode|TextBox2|secret|access_token|refresh_token|id_token|token|sessionid|JSESSIONID|ASP\.NET_SessionId)\b(\s*[:=]\s*["']?)([^"'\s&,;}\]]+)"""
    )

    private val headerSecretRegex = Regex(
        """(?i)\b(Authorization|Cookie|Set-Cookie)(\s*:\s*)([^\r\n]+)"""
    )

    private val labeledStudentIdRegex = Regex(
        """(?i)(学号|xh|xhxm|studentId|username|账号)(\s*[:：=]\s*["']?)([0-9a-zA-Z]{5,20})"""
    )

    // 常见高校 10~12 位年份开头学号（避开 8 位日期如 20261112 和 13/14 位时间戳）
    private val bareStudentIdRegex = Regex(
        """(?<!\d)(20[12]\d{7,9})(?!\d)"""
    )

    private val labeledNameRegex = Regex(
        """(?i)(姓名|xm)(\s*[:：=]\s*["']?)([\u4e00-\u9fa5]{2,4})"""
    )

    private val idCardRegex = Regex(
        """(?<!\d)\d{17}[\dXx](?!\d)"""
    )

    private val phoneRegex = Regex(
        """(?<!\d)1[3-9]\d{9}(?!\d)"""
    )

    fun sanitize(raw: String): String {
        if (raw.isBlank()) return raw
        var text = raw

        text = headerSecretRegex.replace(text) { m ->
            "${m.groupValues[1]}${m.groupValues[2]}[已脱敏]"
        }
        text = secretKeyValueRegex.replace(text) { m ->
            "${m.groupValues[1]}${m.groupValues[2]}[已脱敏]"
        }
        text = labeledStudentIdRegex.replace(text) { m ->
            "${m.groupValues[1]}${m.groupValues[2]}[已脱敏]"
        }
        text = bareStudentIdRegex.replace(text, "[学号已脱敏]")
        text = labeledNameRegex.replace(text) { m ->
            "${m.groupValues[1]}${m.groupValues[2]}[姓名已脱敏]"
        }
        text = idCardRegex.replace(text, "[身份证已脱敏]")
        text = phoneRegex.replace(text, "[手机号已脱敏]")

        return text
    }
}
