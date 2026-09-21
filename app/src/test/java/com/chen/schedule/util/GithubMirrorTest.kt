package com.chen.schedule.util

import com.chen.schedule.util.update.GithubMirror
import org.junit.Assert.assertEquals
import org.junit.Test

class GithubMirrorTest {

    @Test
    fun testWrapUrlPrefixMirrors() {
        val rawUrl = "https://github.com/hua-yi-yi/CourseSchedule/releases/download/v1.2.24/CourseSchedule-v1.2.24.apk"

        // 官方直连
        assertEquals(rawUrl, GithubMirror.DIRECT.wrapUrl(rawUrl))

        // GHFast
        val ghfastExpected = "https://ghfast.top/$rawUrl"
        assertEquals(ghfastExpected, GithubMirror.GHFAST.wrapUrl(rawUrl))

        // GHProxy Net
        val ghproxyExpected = "https://ghproxy.net/$rawUrl"
        assertEquals(ghproxyExpected, GithubMirror.GHPROXY_NET.wrapUrl(rawUrl))
    }

    @Test
    fun testWrapUrlDomainReplaceMirrors() {
        val rawUrl = "https://github.com/hua-yi-yi/CourseSchedule/releases/tag/v1.2.24"
        val kkExpected = "https://kkgithub.com/hua-yi-yi/CourseSchedule/releases/tag/v1.2.24"
        assertEquals(kkExpected, GithubMirror.KKGITHUB.wrapUrl(rawUrl))
    }

    @Test
    fun testFromId() {
        assertEquals(GithubMirror.GHFAST, GithubMirror.fromId("ghfast"))
        assertEquals(GithubMirror.DIRECT, GithubMirror.fromId("direct"))
        assertEquals(GithubMirror.KKGITHUB, GithubMirror.fromId("kkgithub"))
        // 未知回退到 GHFAST
        assertEquals(GithubMirror.GHFAST, GithubMirror.fromId("unknown_mirror"))
    }
}
