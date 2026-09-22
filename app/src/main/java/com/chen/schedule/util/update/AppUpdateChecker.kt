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
 * 对接 GitHub Releases 接口与 Raw 镜像元数据，支持全节点高速镜像加速与双通道容错。
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

    var lastHasUpdateResult: UpdateCheckResult.HasUpdate? = null
        private set

    companion object {
        const val REPO_OWNER = "hua-yi-yi"
        const val REPO_NAME = "CourseSchedule"
        const val OFFICIAL_API_URL = "https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/releases/latest"
        const val OFFICIAL_RELEASE_WEB = "https://github.com/$REPO_OWNER/$REPO_NAME/releases/latest"
        const val OFFICIAL_RAW_VERSION_URL = "https://raw.githubusercontent.com/$REPO_OWNER/$REPO_NAME/main/version.json"
    }

    private data class EndpointCandidate(
        val name: String,
        val url: String,
        val isRawVersion: Boolean
    )

    /**
     * 检查最新版本（挂起函数，在 IO 协程中执行）。
     * 会按照用户的镜像设置优先请求，并在网络异常时自动回退至其他备用节点。
     */
    suspend fun checkUpdate(): UpdateCheckResult = withContext(Dispatchers.IO) {
        val selected = prefs.selectedMirror
        val useMirror = prefs.useMirror

        // 构造候选请求节点列表（双轨加速架构：优先通过镜像拉取 raw version.json，规避 api.github.com 403 拦截）
        val candidateEndpoints = buildCandidateEndpoints(useMirror, selected)

        var lastError: Exception? = null
        var successJson: JSONObject? = null
        var usedMirrorName: String? = null

        for (candidate in candidateEndpoints) {
            try {
                val req = Request.Builder()
                    .url(candidate.url)
                    .header("User-Agent", "CourseSchedule-App/${BuildConfig.VERSION_NAME}")
                    .apply {
                        if (!candidate.isRawVersion) {
                            header("Accept", "application/vnd.github.v3+json")
                        }
                    }
                    .build()

                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val bodyStr = resp.body?.string().orEmpty()
                        if (bodyStr.isNotBlank() && bodyStr.trimStart().startsWith("{")) {
                            successJson = JSONObject(bodyStr)
                            usedMirrorName = candidate.name
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
            val msg = lastError?.message ?: "无法连接到更新服务器，请检查网络"
            return@withContext UpdateCheckResult.Error(msg, canOpenWeb = true)
        }

        try {
            val tagName = json.optString("versionTag").ifBlank { json.optString("tag_name", "") }
            val title = json.optString("title").ifBlank { json.optString("name", tagName) }.ifBlank { tagName }
            val changelog = json.optString("changelog").ifBlank { json.optString("body", "").trim() }.ifBlank { "本次发布暂无详细更新说明" }
            val htmlUrl = json.optString("htmlUrl").ifBlank { json.optString("html_url", OFFICIAL_RELEASE_WEB) }
            val publishedAtRaw = json.optString("publishedAt").ifBlank { json.optString("published_at", "") }
            val publishedAt = if (publishedAtRaw.contains("T")) publishedAtRaw.substringBefore("T") else publishedAtRaw

            // 解析 APK 附件下载直链
            var officialApkUrl = json.optString("officialDownloadUrl").ifBlank { json.optString("apkUrl", "") }
            var apkSize: Long? = json.optLong("fileSizeBytes", -1L).takeIf { it > 0 }

            if (officialApkUrl.isBlank()) {
                val assets = json.optJSONArray("assets")
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.optJSONObject(i) ?: continue
                        val assetName = asset.optString("name", "")
                        if (assetName.endsWith(".apk", ignoreCase = true)) {
                            officialApkUrl = asset.optString("browser_download_url", "")
                            val size = asset.optLong("size", -1L)
                            if (size > 0 && apkSize == null) apkSize = size
                            break
                        }
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

            // 记录成功检测时间与来源节点
            val cleanSourceName = usedMirrorName ?: "官方直连"
            prefs.lastCheckTime = System.currentTimeMillis()
            prefs.lastCheckSource = cleanSourceName

            val isMirrorUsed = !cleanSourceName.contains("官方直连")

            val result = if (isNew) {
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
                    isMirrorUsed = isMirrorUsed,
                    mirrorName = cleanSourceName
                ).also { lastHasUpdateResult = it }
            } else {
                lastHasUpdateResult = null
                UpdateCheckResult.UpToDate(
                    currentVersion = "v$currentVersion",
                    latestVersion = cleanRemoteName,
                    isMirrorUsed = isMirrorUsed,
                    mirrorName = cleanSourceName
                )
            }
            result
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

    private fun buildCandidateEndpoints(useMirror: Boolean, selected: GithubMirror): List<EndpointCandidate> {
        val list = mutableListOf<EndpointCandidate>()
        if (useMirror && selected != GithubMirror.DIRECT) {
            // 1. 首选: 用户指定的镜像站 -> 拉取 version.json (国内全节点 100% 极速加速 raw)
            list.add(
                EndpointCandidate(
                    name = selected.displayName,
                    url = selected.wrapUrl(OFFICIAL_RAW_VERSION_URL),
                    isRawVersion = true
                )
            )
            // 2. 若用户指定的是 GH-Proxy，可尝试其完整的 API 反代
            if (selected == GithubMirror.GH_PROXY_COM) {
                list.add(
                    EndpointCandidate(
                        name = "${selected.displayName} (API)",
                        url = "https://gh-proxy.com/$OFFICIAL_API_URL",
                        isRawVersion = false
                    )
                )
            }
            // 3. 备用镜像 Raw 通道
            val backupMirrors = listOf(GithubMirror.GHFAST, GithubMirror.GHPROXY_NET, GithubMirror.GH_PROXY_COM)
                .filter { it != selected }
            for (m in backupMirrors) {
                list.add(
                    EndpointCandidate(
                        name = "${m.displayName} (备用)",
                        url = m.wrapUrl(OFFICIAL_RAW_VERSION_URL),
                        isRawVersion = true
                    )
                )
            }
            // 4. GH-Proxy API 备用通道
            if (selected != GithubMirror.GH_PROXY_COM) {
                list.add(
                    EndpointCandidate(
                        name = "GH-Proxy 镜像 (API)",
                        url = "https://gh-proxy.com/$OFFICIAL_API_URL",
                        isRawVersion = false
                    )
                )
            }
            // 5. 官方直连保底
            list.add(
                EndpointCandidate(
                    name = "官方直连",
                    url = OFFICIAL_API_URL,
                    isRawVersion = false
                )
            )
            list.add(
                EndpointCandidate(
                    name = "官方直连 (Raw)",
                    url = OFFICIAL_RAW_VERSION_URL,
                    isRawVersion = true
                )
            )
        } else {
            // 官方直连模式
            list.add(
                EndpointCandidate(
                    name = "官方直连",
                    url = OFFICIAL_API_URL,
                    isRawVersion = false
                )
            )
            list.add(
                EndpointCandidate(
                    name = "官方直连 (Raw)",
                    url = OFFICIAL_RAW_VERSION_URL,
                    isRawVersion = true
                )
            )
            // 备用镜像通道
            list.add(
                EndpointCandidate(
                    name = "GHFast 节点 (备用)",
                    url = GithubMirror.GHFAST.wrapUrl(OFFICIAL_RAW_VERSION_URL),
                    isRawVersion = true
                )
            )
            list.add(
                EndpointCandidate(
                    name = "GH-Proxy 节点 (API备用)",
                    url = "https://gh-proxy.com/$OFFICIAL_API_URL",
                    isRawVersion = false
                )
            )
        }
        return list
    }
}
