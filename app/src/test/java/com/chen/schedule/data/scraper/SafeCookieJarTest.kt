package com.chen.schedule.data.scraper

import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.*
import org.junit.Test

class SafeCookieJarTest {
    private val login = "https://school.example/login".toHttpUrl()
    private fun cookie(name: String, value: String, path: String = "/", secure: Boolean = false, domain: Boolean = false, expires: Long? = null) =
        Cookie.Builder().name(name).value(value).path(path).apply {
            if (domain) domain(login.host) else hostOnlyDomain(login.host)
            if (secure) secure()
            expires?.let { expiresAt(it) }
        }.build()

    @Test fun successiveResponsesMergeAndReplaceOnlyMatchingCookie() {
        val jar = SafeCookieJar()
        jar.saveFromResponse(login, listOf(cookie("session", "one")))
        jar.saveFromResponse(login, listOf(cookie("captcha", "two")))
        jar.saveFromResponse(login, listOf(cookie("session", "updated")))
        assertEquals(mapOf("session" to "updated", "captcha" to "two"), jar.loadForRequest(login).associate { it.name to it.value })
    }
    @Test fun emptyResponseDoesNotEraseAuthentication() {
        val jar = SafeCookieJar()
        jar.saveFromResponse(login, listOf(cookie("session", "one")))
        jar.saveFromResponse(login, emptyList())
        assertEquals(1, jar.loadForRequest(login).size)
    }
    @Test fun pathAndSecureRestrictionsAreApplied() {
        val jar = SafeCookieJar()
        val secure = cookie("session", "secret", "/private", secure = true)
        jar.saveFromResponse(login, listOf(secure))
        assertTrue(jar.loadForRequest(login).isEmpty())
        assertTrue(jar.loadForRequest("http://school.example/private/page".toHttpUrl()).isEmpty())
        assertEquals(secure, jar.loadForRequest("https://school.example/private/page".toHttpUrl()).single())
    }
    @Test fun domainCookiesReachSubdomainsButHostOnlyCookiesDoNot() {
        val jar = SafeCookieJar()
        jar.saveFromResponse(login, listOf(cookie("host", "one"), cookie("domain", "two", domain = true)))
        assertEquals("domain", jar.loadForRequest("https://sub.school.example/".toHttpUrl()).single().name)
        assertTrue(jar.loadForRequest("https://otherschool.example/".toHttpUrl()).isEmpty())
    }
    @Test fun expiryAndServerDeletionRemoveOnlyTheirOwnCookie() {
        var now = 1_000L
        val jar = SafeCookieJar { now }
        jar.saveFromResponse(login, listOf(cookie("expires", "one", expires = 2_000), cookie("keep", "two")))
        now = 2_001
        assertEquals("keep", jar.loadForRequest(login).single().name)
        jar.saveFromResponse(login, listOf(cookie("keep", "", expires = 1)))
        assertTrue(jar.loadForRequest(login).isEmpty())
    }
    @Test fun sameNameAtDifferentPathsIsPreservedAndClearLogsOut() {
        val jar = SafeCookieJar()
        jar.saveFromResponse(login, listOf(cookie("session", "root"), cookie("session", "private", "/private")))
        assertEquals(listOf("private", "root"), jar.loadForRequest("https://school.example/private/page".toHttpUrl()).map { it.value })
        jar.clear()
        assertTrue(jar.loadForRequest(login).isEmpty())
    }
}
