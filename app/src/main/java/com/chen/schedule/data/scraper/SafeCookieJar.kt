package com.chen.schedule.data.scraper

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

class SafeCookieJar(private val nowMillis: () -> Long = System::currentTimeMillis) : CookieJar {
    private data class Key(val name: String, val domain: String, val path: String)
    private val cookieStore = mutableMapOf<Key, Cookie>()

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        removeExpired()
        cookies.forEach { cookie ->
            val key = Key(cookie.name, cookie.domain, cookie.path)
            if (cookie.expiresAt <= nowMillis()) cookieStore.remove(key)
            else cookieStore[key] = cookie
        }
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        removeExpired()
        return cookieStore.values.filter { it.matches(url) }.sortedByDescending { it.path.length }
    }

    @Synchronized
    fun clear() {
        cookieStore.clear()
    }

    private fun removeExpired() {
        val now = nowMillis()
        cookieStore.entries.removeAll { it.value.expiresAt <= now }
    }
}
