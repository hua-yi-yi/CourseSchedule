package com.chen.schedule.util.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * GitHub 镜像站节点定义与加速转换工具。
 * 针对国内网络环境，将 GitHub Releases 页面与 APK 下载直链转换为高速镜像。
 */
enum class GithubMirror(
    val id: String,
    val displayName: String,
    val prefix: String?,
    val isDomainReplace: Boolean = false,
    val replaceDomain: String = ""
) {
    GHFAST("ghfast", "GHFast 节点 (推荐)", "https://ghfast.top/"),
    GHPROXY_NET("ghproxy_net", "GHProxy 节点", "https://ghproxy.net/"),
    KKGITHUB("kkgithub", "KKGitHub 节点", null, isDomainReplace = true, replaceDomain = "kkgithub.com"),
    GH_PROXY_COM("gh_proxy_com", "GH-Proxy 节点", "https://gh-proxy.com/"),
    DIRECT("direct", "官方直连 (GitHub)", null);

    /**
     * 将给定的 GitHub URL (如 release 下载直链) 转换为当前镜像站加速 URL。
     */
    fun wrapUrl(originalUrl: String): String {
        if (this == DIRECT) return originalUrl
        if (isDomainReplace && replaceDomain.isNotBlank()) {
            return originalUrl.replace("https://github.com", "https://$replaceDomain")
                .replace("http://github.com", "https://$replaceDomain")
                .replace("https://raw.githubusercontent.com", "https://raw.$replaceDomain")
        }
        val p = prefix ?: return originalUrl
        val cleanPrefix = if (p.endsWith("/")) p else "$p/"
        return "$cleanPrefix$originalUrl"
    }

    /**
     * 将 GitHub API URL 转换为加速镜像 URL
     */
    fun wrapApiUrl(apiUrl: String): String {
        if (this == DIRECT) return apiUrl
        val p = prefix ?: return apiUrl
        val cleanPrefix = if (p.endsWith("/")) p else "$p/"
        return "$cleanPrefix$apiUrl"
    }

    companion object {
        val ALL_MIRRORS = listOf(GHFAST, GHPROXY_NET, KKGITHUB, GH_PROXY_COM, DIRECT)

        fun fromId(id: String): GithubMirror = entries.firstOrNull { it.id == id } ?: GHFAST

        /**
         * 探测给定镜像站的 HTTP 连通性与响应延迟 (ms)。
         * 超时设定为 4 秒，失败或超时返回 null。
         */
        suspend fun testLatency(mirror: GithubMirror, client: OkHttpClient): Long? {
            val testTarget = if (mirror == DIRECT) {
                "https://api.github.com"
            } else if (mirror.prefix != null) {
                mirror.prefix
            } else if (mirror.isDomainReplace && mirror.replaceDomain.isNotBlank()) {
                "https://${mirror.replaceDomain}"
            } else {
                "https://api.github.com"
            }

            return withContext(Dispatchers.IO) {
                val start = System.currentTimeMillis()
                try {
                    val req = Request.Builder()
                        .url(testTarget)
                        .header("User-Agent", "CourseSchedule-App/1.0")
                        .head()
                        .build()

                    val timeoutClient = client.newBuilder()
                        .connectTimeout(4, TimeUnit.SECONDS)
                        .readTimeout(4, TimeUnit.SECONDS)
                        .build()

                    timeoutClient.newCall(req).execute().use { resp ->
                        if (resp.isSuccessful || resp.code in 200..404) {
                            System.currentTimeMillis() - start
                        } else {
                            null
                        }
                    }
                } catch (e: Exception) {
                    null
                }
            }
        }
    }
}
