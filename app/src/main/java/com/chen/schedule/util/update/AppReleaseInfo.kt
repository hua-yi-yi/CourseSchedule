package com.chen.schedule.util.update

/**
 * 远端发布的最新版本信息。
 */
data class AppReleaseInfo(
    val versionName: String,
    val versionTag: String,
    val title: String,
    val changelog: String,
    val officialDownloadUrl: String,
    val mirrorDownloadUrl: String,
    val htmlUrl: String,
    val publishedAt: String = "",
    val fileSizeBytes: Long? = null
)

/**
 * 版本检测结果。
 */
sealed class UpdateCheckResult {
    /**
     * 发现新版本。
     */
    data class HasUpdate(
        val release: AppReleaseInfo,
        val currentVersion: String,
        val isMirrorUsed: Boolean,
        val mirrorName: String?
    ) : UpdateCheckResult()

    /**
     * 已经是最新版本。
     */
    data class UpToDate(
        val currentVersion: String,
        val latestVersion: String
    ) : UpdateCheckResult()

    /**
     * 检测失败（网络错误或解析异常）。
     */
    data class Error(
        val message: String,
        val canOpenWeb: Boolean = true
    ) : UpdateCheckResult()
}
