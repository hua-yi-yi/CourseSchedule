package com.chen.schedule.data.scraper

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

class SafeCookieJar : CookieJar {
    private val cookieStore = mutableMapOf<String, MutableList<Cookie>>()

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        cookieStore[url.host] = cookies.toMutableList()
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        return cookieStore[url.host] ?: emptyList()
    }

    @Synchronized
    fun clear() {
        cookieStore.clear()
    }
}
