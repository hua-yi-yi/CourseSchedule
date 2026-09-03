package com.chen.schedule.data.scraper

import com.chen.schedule.domain.model.Course

data class ScraperConfig(
    val name: String,
    val baseUrl: String = "",
    val loginUrl: String = "",
    val scheduleUrl: String = ""
)

data class ScraperResult(
    val courses: List<Course>,
    val semesterName: String? = null
)

/** 登录结果:携带具体失败原因,便于 UI 直接展示 */
sealed class LoginResult {
    object Success : LoginResult()
    data class Failure(val reason: String) : LoginResult()
}

/** 爬虫业务异常(如解析失败),与网络异常区分 */
class ScraperException(message: String) : Exception(message)

/**
 * 教务系统适配器接口。
 * 登录分两步:先 [fetchCaptcha] 获取验证码(可选),再 [login] 提交。
 * 所有方法均为挂起函数,实现方负责在 IO 线程执行网络请求。
 */
interface ScraperAdapter {
    var config: ScraperConfig
    val name: String get() = config.name

    /** 获取登录验证码图片;无验证码的系统返回 null */
    suspend fun fetchCaptcha(): ByteArray?

    /** 登录。验证码为空时按无验证码系统处理 */
    suspend fun login(username: String, password: String, captchaCode: String?): LoginResult

    suspend fun fetchCourses(): ScraperResult

    suspend fun logout()
}
