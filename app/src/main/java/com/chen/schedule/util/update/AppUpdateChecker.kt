package com.chen.schedule.util.update

import android.content.Context
import com.chen.schedule.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 应用版本更新检测引擎。
 * 对接 GitHub Releases 接口，支持官方直连与国内加速镜像双通道智能回退。
 */
@Singleton
class AppUpdateChecker @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val prefs by lazy { UpdatePrefs(context) }

    companion object {
        const val REPO_OWNER = "hua-yi-yi"
        const val REPO_NAME = "CourseSchedule"
        const val OFFICIAL_API_URL = "https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/releases/latest"
        const val OFFICIAL_RELEASE_WEB = "https://github.com/$REPO_OWNER/$REPO_NAME/releases/latest"
    }

    /**
     * 检查最新版本（挂起函数，在 IO 协程中执行）。
     * 会按照用户的镜像设置优先请求，并在网络异常时自动回退至其他备用节点。
     */
    suspend fun checkUpdate(): UpdateCheckResult = withContext(Dispatchers.IO) {
        val selected = prefs.selectedMirror
        val useMirror = prefs.useMirror

        // 构造候选请求节点列表
        val candidateEndpoints = buildCandidateEndpoints(useMirror, selected)

        var lastError: Exception? = null
        var successJson: JSONObject? = null
        var usedMirrorName: String? = null

        for ((name, url) in candidateEndpoints) {
            try {
                val req = Request.Builder()
                    .url(url)
                    .header("User-Agent", "CourseSchedule-App/${BuildConfig.VERSION_NAME}")
                    .header("Accept", "application/vnd.github.v3+json")
                    .build()

                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val bodyStr = resp.body?.string().orEmpty()
                        if (bodyStr.isNotBlank() && bodyStr.trimStart().startsWith("{")) {
                            successJson = JSONObject(bodyStr)
                            usedMirrorName = name
                            return@use
                        }
                    }
                }
            } catch (e: Exception) {
                lastError = e
            }

            if (successJson != null) break
        }

        val json = successJson
        if (json == null) {
            val msg = lastError?.message ?: "无法连接到 GitHub 检查更新，请检查网络"
            return@withContext UpdateCheckResult.Error(msg, canOpenWeb = true)
        }

        try {
            val tagName = json.optString("tag_name", "")
            val title = json.optString("name", tagName).ifBlank { tagName }
            val changelog = json.optString("body", "").trim().ifBlank { "本次发布暂无详细更新说明" }
            val htmlUrl = json.optString("html_url", OFFICIAL_RELEASE_WEB)
            val publishedAtRaw = json.optString("published_at", "")
            val publishedAt = if (publishedAtRaw.contains("T")) publishedAtRaw.substringBefore("T") else publishedAtRaw

            // 解析 APK 附件下载直链
            val assets = json.optJSONArray("assets")
            var officialApkUrl = ""
            var apkSize: Long? = null
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.optJSONObject(i) ?: continue
                    val assetName = asset.optString("name", "")
                    if (assetName.endsWith(".apk", ignoreCase = true)) {
                        officialApkUrl = asset.optString("browser_download_url", "")
                        val size = asset.optLong("size", -1L)
                        if (size > 0) apkSize = size
                        break
                    }
                }
            }
            if (officialApkUrl.isBlank()) {
                officialApkUrl = htmlUrl
            }

            val currentVersion = BuildConfig.VERSION_NAME
            val isNew = VersionComparator.isNewer(tagName, currentVersion)

            // 格式化展示版本名称
            val cleanRemoteName = VersionComparator.formatVersionName(tagName)

            // 计算镜像下载直链
            val mirrorToUse = if (useMirror) selected else GithubMirror.DIRECT
            val mirrorApkUrl = mirrorToUse.wrapUrl(officialApkUrl)

            // 记录成功检测时间
            prefs.lastCheckTime = System.currentTimeMillis()

            if (isNew) {
                val releaseInfo = AppReleaseInfo(
                    versionName = cleanRemoteName,
                    versionTag = tagName,
                    title = title,
                    changelog = changelog,
                    officialDownloadUrl = officialApkUrl,
                    mirrorDownloadUrl = mirrorApkUrl,
                    htmlUrl = htmlUrl,
                    publishedAt = publishedAt,
                    fileSizeBytes = apkSize
                )
                UpdateCheckResult.HasUpdate(
                    release = releaseInfo,
                    currentVersion = "v$currentVersion",
                    isMirrorUsed = usedMirrorName != null && usedMirrorName != "官方直连",
                    mirrorName = usedMirrorName
                )
            } else {
                UpdateCheckResult.UpToDate(
                    currentVersion = "v$currentVersion",
                    latestVersion = cleanRemoteName
                )
            }
        } catch (e: Exception) {
            UpdateCheckResult.Error("解析更新信息失败: ${e.message}", canOpenWeb = true)
        }
    }

    /**
     * 测试所有镜像站节点的延迟 (ms)。
     */
    suspend fun testAllMirrors(): Map<GithubMirror, Long?> = coroutineScope {
        val jobs = GithubMirror.ALL_MIRRORS.map { mirror ->
            async {
                mirror to GithubMirror.testLatency(mirror, client)
            }
        }
        jobs.awaitAll().toMap()
    }

    private fun buildCandidateEndpoints(useMirror: Boolean, selected: GithubMirror): List<Pair<String, String>> {
        val list = mutableListOf<Pair<String, String>>()
        if (useMirror && selected != GithubMirror.DIRECT) {
            list.add(selected.displayName to selected.wrapApiUrl(OFFICIAL_API_URL))
            list.add("官方直连" to OFFICIAL_API_URL)
            // 追加备用镜像
            if (selected != GithubMirror.GHFAST) {
                list.add(GithubMirror.GHFAST.displayName to GithubMirror.GHFAST.wrapApiUrl(OFFICIAL_API_URL))
            }
            if (selected != GithubMirror.GHPROXY_NET) {
                list.add(GithubMirror.GHPROXY_NET.displayName to GithubMirror.GHPROXY_NET.wrapApiUrl(OFFICIAL_API_URL))
            }
        } else {
            list.add("官方直连" to OFFICIAL_API_URL)
            list.add(GithubMirror.GHFAST.displayName to GithubMirror.GHFAST.wrapApiUrl(OFFICIAL_API_URL))
            list.add(GithubMirror.GHPROXY_NET.displayName to GithubMirror.GHPROXY_NET.wrapApiUrl(OFFICIAL_API_URL))
        }
        return list
    }
}
