package com.chen.schedule.data.scraper

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.Jsoup
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 正方教务系统(经典版 default2.aspx)抓取器。
 *
 * 登录流程:
 *  1. GET 登录页,解析 __VIEWSTATE 等隐藏字段
 *  2. GET CheckCode.aspx 获取验证码(由 UI 展示并让用户输入)
 *  3. POST 登录表单(GB2312 编码,与浏览器一致)
 *
 * 注意:所有网络请求均在 Dispatchers.IO 执行,避免阻塞主线程。
 */
@Singleton
class ZhengfangScraper @Inject constructor() : ScraperAdapter {

    override var config = ScraperConfig(name = NAME)

    private val client = OkHttpClient.Builder()
        .cookieJar(SafeCookieJar())
        .followRedirects(true)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    override suspend fun fetchCaptcha(): ByteArray? = withContext(Dispatchers.IO) {
        val base = config.baseUrl.ifBlank { config.loginUrl.substringBeforeLast("/") }
        if (base.isBlank()) return@withContext null
        // 正方经典版验证码通常在应用根目录;个别学校挂在 default2.aspx 子路径
        val candidates = listOf(
            "$base/CheckCode.aspx",
            "$base/default2.aspx/CheckCode.aspx"
        )
        for (candidate in candidates) {
            try {
                val req = Request.Builder().url(candidate).get().build()
                val bytes = client.newCall(req).execute().use { res ->
                    if (res.isSuccessful) res.body?.bytes() else null
                }
                if (bytes?.isNotEmpty() == true) return@withContext bytes
            } catch (_: Exception) {
                // 尝试下一个候选地址
            }
        }
        null
    }

    override suspend fun login(
        username: String,
        password: String,
        captchaCode: String?
    ): LoginResult = withContext(Dispatchers.IO) {
        if (config.loginUrl.isBlank()) {
            return@withContext LoginResult.Failure("未配置教务系统地址")
        }
        try {
            // Step 1: 获取登录页,解析隐藏字段
            val pageReq = Request.Builder().url(config.loginUrl).get().build()
            val pageHtml = client.newCall(pageReq).execute().use { res ->
                if (res.isSuccessful) res.body?.string() else null
            } ?: return@withContext LoginResult.Failure("无法访问教务系统登录页")

            val doc = Jsoup.parse(pageHtml)
            val viewState = doc.select("input[name=__VIEWSTATE]").firstOrNull()?.`val`() ?: ""
            val viewStateGen = doc.select("input[name=__VIEWSTATEGENERATOR]").firstOrNull()?.`val`() ?: ""

            // Step 2: 提交登录。用 GB2312 编码构建表单体,与浏览器行为一致
            val formPairs = listOf(
                "__VIEWSTATE" to viewState,
                "__VIEWSTATEGENERATOR" to viewStateGen,
                "TextBox1" to username,
                "TextBox2" to password,
                "TextBox3" to (captchaCode ?: ""),
                "RadioButtonList1" to "学生",
                "Button1" to "登录"
            )
            val bodyString = formPairs.joinToString("&") { (key, value) ->
                "${URLEncoder.encode(key, "GB2312")}=${URLEncoder.encode(value, "GB2312")}"
            }
            val formBody = bodyString.toRequestBody(
                "application/x-www-form-urlencoded; charset=GB2312".toMediaType()
            )

            val loginReq = Request.Builder()
                .url(config.loginUrl)
                .post(formBody)
                .build()
            val response = client.newCall(loginReq).execute()
            val html = response.use { it.body?.string() ?: "" }
            val finalUrl = response.request.url.toString()

            when {
                ERROR_PASSWORD.any { html.contains(it) } ->
                    LoginResult.Failure("用户名或密码错误")
                ERROR_CAPTCHA.any { html.contains(it) } ->
                    LoginResult.Failure("验证码不正确或已过期,请刷新后重试")
                html.contains("xs_main") || finalUrl.contains("xs_main") ||
                    html.contains("xk_main") || finalUrl.contains("xk_main") ->
                    LoginResult.Success
                else ->
                    LoginResult.Failure("登录失败,请检查地址、账号、密码与验证码")
            }
        } catch (e: Exception) {
            LoginResult.Failure("网络异常:${e.message ?: "未知错误"}")
        }
    }

    override suspend fun fetchCourses(): ScraperResult = withContext(Dispatchers.IO) {
        if (config.scheduleUrl.isBlank()) return@withContext ScraperResult(emptyList())
        try {
            val req = Request.Builder().url(config.scheduleUrl).get().build()
            val html = client.newCall(req).execute().use { res ->
                if (res.isSuccessful) res.body?.string() else null
            } ?: return@withContext ScraperResult(emptyList())

            val courses = ZhengfangPageParser.parseCourses(html)
            if (courses.isEmpty()) {
                throw ScraperException("未能解析到课程数据:请确认教务系统地址,或改用 JSON/CSV 导入")
            }
            ScraperResult(courses)
        } catch (e: ScraperException) {
            throw e
        } catch (_: Exception) {
            ScraperResult(emptyList())
        }
    }

    override suspend fun logout() {
        (client.cookieJar as? SafeCookieJar)?.clear()
    }

    companion object {
        const val NAME = "正方教务系统"

        private val ERROR_PASSWORD = listOf("用户名或密码错误", "密码错误", "账号或密码错误")
        private val ERROR_CAPTCHA = listOf("验证码不正确", "验证码错误", "验证码已过期", "验证码输入错误")
    }
}
