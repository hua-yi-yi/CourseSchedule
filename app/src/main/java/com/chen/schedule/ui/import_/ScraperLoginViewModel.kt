package com.chen.schedule.ui.import_

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chen.schedule.data.repository.CourseRepository
import com.chen.schedule.data.repository.SemesterRepository
import com.chen.schedule.data.scraper.LoginResult
import com.chen.schedule.data.scraper.ScraperAdapter
import com.chen.schedule.data.scraper.ScraperException
import com.chen.schedule.data.scraper.ScraperManager
import com.chen.schedule.data.scraper.ZhengfangScraper
import com.chen.schedule.domain.model.CoursePalette
import com.chen.schedule.widget.WidgetUpdater
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ScraperLoginState(
    val url: String = "https://jwgl.haust.edu.cn/eams/homeExt.action",
    val username: String = "",
    val password: String = "",
    val captchaBytes: ByteArray? = null,
    val captchaCode: String = "",
    val isLoading: Boolean = false,
    val message: String = "",
    val isError: Boolean = false
)

/**
 * 教务系统导入流程的 ViewModel:
 * 输入地址 → 获取验证码 → 登录 → 抓取课表 → 写入当前学期并刷新小组件。
 */
@HiltViewModel
class ScraperLoginViewModel @Inject constructor(
    private val scraperManager: ScraperManager,
    private val courseRepository: CourseRepository,
    private val semesterRepository: SemesterRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _state = MutableStateFlow(ScraperLoginState())
    val state: StateFlow<ScraperLoginState> = _state.asStateFlow()

    private val scraper: ScraperAdapter
        get() = scraperManager.adapter(ZhengfangScraper.NAME)
            ?: error("未注册教务系统适配器: ${ZhengfangScraper.NAME}")

    fun onUrlChange(value: String) = _state.update { it.copy(url = value) }
    fun onUsernameChange(value: String) = _state.update { it.copy(username = value) }
    fun onPasswordChange(value: String) = _state.update { it.copy(password = value) }
    fun onCaptchaChange(value: String) = _state.update { it.copy(captchaCode = value) }

    /** 根据用户输入的地址更新抓取器配置;返回错误信息或 null */
    private fun applyScraperConfig(): String? {
        val raw = _state.value.url.trim()
        if (raw.isEmpty()) return "请输入教务系统地址"
        val base = if (raw.startsWith("http://") || raw.startsWith("https://")) raw.trimEnd('/')
        else "https://${raw.trimEnd('/')}"
        val parsed = android.net.Uri.parse(base)
        if (parsed.host.equals("jwgl.haust.edu.cn", ignoreCase = true) ||
            parsed.path.orEmpty().startsWith("/eams", ignoreCase = true)) {
            return "河南科技大学请使用上方「VPN / 校内导入」入口；此账号表单仅适用于正方经典版"
        }
        scraper.config = scraper.config.copy(
            baseUrl = base,
            loginUrl = "$base/default2.aspx",
            scheduleUrl = "$base/xskbcx.aspx"
        )
        return null
    }

    fun fetchCaptcha() {
        viewModelScope.launch {
            val configError = applyScraperConfig()
            if (configError != null) {
                _state.update { it.copy(message = configError, isError = true) }
                return@launch
            }
            _state.update { it.copy(isLoading = true, message = "", isError = false) }
            try {
                val bytes = scraper.fetchCaptcha()
                if (bytes == null || bytes.isEmpty()) {
                    // 部分学校无验证码或地址不对,提示但不阻断登录
                    _state.update {
                        it.copy(isLoading = false, message = "未获取到验证码,可直接尝试登录", isError = false)
                    }
                } else {
                    _state.update {
                        it.copy(isLoading = false, captchaBytes = bytes, captchaCode = "")
                    }
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(isLoading = false, message = "获取验证码失败:${e.message}", isError = true)
                }
            }
        }
    }

    fun loginAndImport() {
        val s = _state.value
        if (s.url.isBlank() || s.username.isBlank() || s.password.isBlank()) {
            _state.update { it.copy(message = "请填写教务系统地址、账号和密码", isError = true) }
            return
        }
        viewModelScope.launch {
            val configError = applyScraperConfig()
            if (configError != null) {
                _state.update { it.copy(message = configError, isError = true) }
                return@launch
            }
            _state.update { it.copy(isLoading = true, message = "", isError = false) }
            try {
                val loginResult = scraper.login(
                    _state.value.username.trim(),
                    _state.value.password,
                    _state.value.captchaCode.trim().ifBlank { null }
                )
                if (loginResult is LoginResult.Failure) {
                    _state.update {
                        it.copy(isLoading = false, message = "登录失败:${loginResult.reason}", isError = true)
                    }
                    return@launch
                }

                _state.update { it.copy(message = "登录成功,正在获取课表…") }
                val fetched = scraper.fetchCourses()
                if (fetched.courses.isEmpty()) {
                    _state.update {
                        it.copy(
                            isLoading = false,
                            message = "未获取到课程数据,请检查教务系统地址,或改用 JSON/CSV 导入",
                            isError = true
                        )
                    }
                    return@launch
                }

                val semester = semesterRepository.getCurrentSemester()
                if (semester == null) {
                    _state.update {
                        it.copy(
                            isLoading = false,
                            message = "请先在「设置 → 作息时间与学期配置」中创建学期",
                            isError = true
                        )
                    }
                    return@launch
                }

                val courses = CoursePalette.assignColors(fetched.courses)
                    .map { it.copy(semesterId = semester.id) }
                courseRepository.insertAll(courses)
                WidgetUpdater.refreshAll(context)
                _state.update {
                    it.copy(
                        isLoading = false,
                        message = "成功导入 ${courses.size} 门课程,可返回查看课程表",
                        isError = false
                    )
                }
            } catch (e: ScraperException) {
                _state.update { it.copy(isLoading = false, message = e.message ?: "导入失败", isError = true) }
            } catch (e: Exception) {
                _state.update {
                    it.copy(isLoading = false, message = "连接失败:${e.message}", isError = true)
                }
            }
        }
    }
}
