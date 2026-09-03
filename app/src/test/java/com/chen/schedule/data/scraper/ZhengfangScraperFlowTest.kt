package com.chen.schedule.data.scraper

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 对 ZhengfangScraper 的完整 HTTP 流程做运行时验证:
 * 用 MockWebServer 模拟正方教务系统,覆盖登录表单编码、验证码、抓取解析全链路。
 */
class ZhengfangScraperFlowTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun newScraper(): ZhengfangScraper {
        val base = server.url("/").toString().trimEnd('/')
        val scraper = ZhengfangScraper()
        scraper.config = scraper.config.copy(
            baseUrl = base,
            loginUrl = "$base/default2.aspx",
            scheduleUrl = "$base/xskbcx.aspx"
        )
        return scraper
    }

    @Test
    fun `登录成功-表单字段与GB2312编码正确`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                "<html><body><input name=\"__VIEWSTATE\" value=\"vs123\">" +
                    "<input name=\"__VIEWSTATEGENERATOR\" value=\"gen456\"></body></html>"
            )
        )
        server.enqueue(MockResponse().setBody("<html>学生主页 xs_main.aspx 欢迎</html>"))

        val result = newScraper().login("student01", "secret!", "8888")
        assertEquals(LoginResult.Success, result)

        // 第 1 个请求:GET 登录页
        val getReq = server.takeRequest()
        assertEquals("GET", getReq.method)
        assertEquals("/default2.aspx", getReq.path)

        // 第 2 个请求:POST 登录
        val postReq = server.takeRequest()
        assertEquals("POST", postReq.method)
        assertEquals("/default2.aspx", postReq.path)
        assertTrue(postReq.getHeader("Content-Type")!!.startsWith("application/x-www-form-urlencoded"))

        val body = postReq.body.readUtf8()
        assertTrue("应包含 __VIEWSTATE=vs123", body.contains("__VIEWSTATE=vs123"))
        assertTrue("应包含 __VIEWSTATEGENERATOR=gen456", body.contains("__VIEWSTATEGENERATOR=gen456"))
        assertTrue("应包含账号", body.contains("TextBox1=student01"))
        assertTrue("应包含密码", body.contains("TextBox2=secret%21"))
        assertTrue("应包含验证码", body.contains("TextBox3=8888"))
        assertTrue("身份'学生'应按 GB2312 编码(%D1%A7%C9%FA)", body.contains("RadioButtonList1=%D1%A7%C9%FA"))
    }

    @Test
    fun `验证码错误时返回具体失败原因`() = runBlocking {
        server.enqueue(MockResponse().setBody("<html><input name=\"__VIEWSTATE\" value=\"vs\"></html>"))
        server.enqueue(MockResponse().setBody("<html>验证码不正确,请重新输入</html>"))

        val result = newScraper().login("u", "p", "0000")
        assertTrue(result is LoginResult.Failure)
        assertTrue((result as LoginResult.Failure).reason.contains("验证码"))
    }

    @Test
    fun `密码错误时返回失败原因`() = runBlocking {
        server.enqueue(MockResponse().setBody("<html><input name=\"__VIEWSTATE\" value=\"vs\"></html>"))
        server.enqueue(MockResponse().setBody("<html>用户名或密码错误</html>"))

        val result = newScraper().login("u", "wrong", "1234")
        assertTrue(result is LoginResult.Failure)
        assertTrue((result as LoginResult.Failure).reason.contains("用户名或密码"))
    }

    @Test
    fun `验证码获取成功返回图片字节`() = runBlocking {
        val captchaPng = byteArrayOf(0x73, 0x6C, 0x7C, 0x2E, 0x2E, 0x2E, 0x73, 0x70, 0x6B)
        server.enqueue(MockResponse().setBody(Buffer().write(captchaPng)))

        val bytes = newScraper().fetchCaptcha()
        assertTrue("应返回验证码字节", bytes != null && bytes!!.contentEquals(captchaPng))
    }

    @Test
    fun `验证码接口404时依次尝试备选地址`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404))
        server.enqueue(MockResponse().setBody(Buffer().write(byteArrayOf(0x01, 0x02))))

        val bytes = newScraper().fetchCaptcha()
        assertTrue("备选地址应能兜底", bytes != null && bytes.size == 2)
        val firstPath = server.takeRequest().path
        val secondPath = server.takeRequest().path
        assertEquals("/CheckCode.aspx", firstPath)
        assertEquals("/default2.aspx/CheckCode.aspx", secondPath)
    }

    @Test
    fun `抓取课表-端到端解析`() = runBlocking {
        val html = """
            <table id="Table1">
            <tr><td>节次</td><td>星期一</td><td>星期二</td><td>星期三</td><td>星期四</td><td>星期五</td><td>星期六</td><td>星期日</td></tr>
            <tr><td>1</td><td>高等数学<br>张老师<br>教学楼101<br>1-16周</td><td></td><td></td><td></td><td></td><td></td><td></td></tr>
            <tr><td>2</td><td rowspan="2">数据结构<br>王老师<br>实验楼301<br>1-8周(单)</td><td></td><td></td><td></td><td></td><td></td><td></td></tr>
            <tr><td>3</td><td></td><td></td><td></td><td></td><td></td><td></td></tr>
            </table>
        """.trimIndent()
        server.enqueue(MockResponse().setBody(html))

        val result = newScraper().fetchCourses()
        assertEquals(2, result.courses.size)

        val math = result.courses.first { it.name == "高等数学" }
        assertEquals(1, math.dayOfWeek)
        assertEquals("张老师", math.teacher)
        assertEquals(16, math.endWeek)

        val ds = result.courses.first { it.name == "数据结构" }
        assertEquals(2, ds.startSlot)
        assertEquals(3, ds.endSlot) // rowspan=2 跨 2-3 节
        assertEquals(com.chen.schedule.domain.model.WeekType.ODD, ds.weekType)
        assertEquals(8, ds.endWeek)
    }

    @Test
    fun `抓取课表-解析为空时抛出带说明的异常`() = runBlocking {
        server.enqueue(MockResponse().setBody("<html><body>登录已过期,请重新登录</body></html>"))

        try {
            newScraper().fetchCourses()
            throw AssertionError("应当抛出 ScraperException")
        } catch (e: ScraperException) {
            assertTrue(e.message!!.contains("未能解析到课程数据"))
        }
    }
}
