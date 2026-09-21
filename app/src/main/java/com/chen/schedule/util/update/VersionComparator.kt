package com.chen.schedule.util.update

/**
 * 语义化版本号比对工具。
 * 比较远端版本与当前本地版本，判断是否有更新。
 */
object VersionComparator {

    /**
     * 判断 [remoteVersion] 是否比 [currentVersion] 更新。
     * 例如:
     * - remote "v1.2.25", current "1.2.24" -> true
     * - remote "1.2.24", current "1.2.24" -> false
     * - remote "v1.2.23", current "1.2.24" -> false
     * - remote "v1.3.0", current "1.2.24" -> true
     * - remote "v2.0.0", current "1.2.24" -> true
     */
    fun isNewer(remoteVersion: String, currentVersion: String): Boolean {
        val remoteParts = parseVersionNumbers(remoteVersion)
        val currentParts = parseVersionNumbers(currentVersion)

        if (remoteParts.isEmpty() || currentParts.isEmpty()) {
            return false
        }

        val maxLen = maxOf(remoteParts.size, currentParts.size)
        for (i in 0 until maxLen) {
            val r = remoteParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }

    /**
     * 将形如 "v1.2.24" 或 "1.2.24-beta" 解析为数字列表 [1, 2, 24]
     */
    fun parseVersionNumbers(versionStr: String): List<Int> {
        val clean = versionStr.trim()
            .removePrefix("v")
            .removePrefix("V")
            .substringBefore("-")
            .substringBefore("+")

        return clean.split(".")
            .mapNotNull { it.trim().toIntOrNull() }
    }

    /**
     * 清理版本号展示名称（统一保留标准形如 "v1.2.25"）
     */
    fun formatVersionName(raw: String): String {
        val trimmed = raw.trim()
        val withoutV = if (trimmed.startsWith("v", ignoreCase = true)) trimmed.substring(1) else trimmed
        return "v$withoutV"
    }
}
