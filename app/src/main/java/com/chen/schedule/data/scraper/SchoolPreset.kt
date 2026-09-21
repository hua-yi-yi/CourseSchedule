package com.chen.schedule.data.scraper

/**
 * 教务系统类型枚举。
 */
enum class SchoolSystemType(val label: String) {
    /** 正方教务经典版 (default2.aspx，支持表单 + 验证码直连登录) */
    ZHENGFANG_CLASSIC("正方经典版"),

    /** WebVPN / CAS 统一身份认证网页抓取 (如河南科技大学) */
    WEB_VPN_EAMS("WebVPN / CAS 统一身份认证"),

    /** 用户自定义正方教务系统 */
    CUSTOM_ZHENGFANG("自定义教务系统")
}

/**
 * 高校预设模型。
 *
 * @param id 唯一标识 (如 "haust", "zhengfang_default")
 * @param name 高校全称 (如 "河南科技大学")
 * @param pinyin 全拼与简拼助记 (如 "henankejidaxue hkd")
 * @param keywords 搜索关键词列表 (如 ["河科大", "haust", "洛阳"])
 * @param systemType 对应的教务系统类型
 * @param baseUrl 基础教务或 WebVPN 域名
 * @param loginUrl 登录入口完整或相对地址
 * @param description 简要说明提示
 * @param badge 界面标签徽章 (如 "认证网页", "直连抓取")
 */
data class SchoolPreset(
    val id: String,
    val name: String,
    val pinyin: String,
    val keywords: List<String> = emptyList(),
    val systemType: SchoolSystemType,
    val baseUrl: String = "",
    val loginUrl: String = "",
    val description: String = "",
    val badge: String = ""
) {
    /** 检查是否匹配搜索词 (支持中文名、拼音、关键词模糊匹配，忽略大小写) */
    fun matches(query: String): Boolean {
        val q = query.trim().lowercase()
        if (q.isBlank()) return true
        if (name.lowercase().contains(q)) return true
        if (pinyin.lowercase().contains(q)) return true
        if (id.lowercase().contains(q)) return true
        if (keywords.any { it.lowercase().contains(q) }) return true
        return false
    }
}

/**
 * 高校教务预设与规则中心。
 * 统一管理高校元数据与系统适配规则，便于集中扩展新高校。
 */
object SchoolRegistry {

    private val PRESETS = listOf(
        SchoolPreset(
            id = "haust",
            name = "河南科技大学",
            pinyin = "henankejidaxue hkd",
            keywords = listOf("河科大", "haust", "科大", "洛阳"),
            systemType = SchoolSystemType.WEB_VPN_EAMS,
            baseUrl = "https://cas.haust.edu.cn",
            loginUrl = "https://cas.haust.edu.cn/cas/login?service=https%3A%2F%2Fvpn.haust.edu.cn%3A443%2Fpassport%2Fv1%2Fauth%2Fcas%3FsfDomain%3Dxkp",
            description = "支持校外 WebVPN / 校内直连登录，自动提取已排课表",
            badge = "统一认证"
        ),
        SchoolPreset(
            id = "zf_classic_template",
            name = "正方教务系统 (经典版)",
            pinyin = "zhengfangjiaowu zf",
            keywords = listOf("正方", "zhengfang", "default2", "经典版"),
            systemType = SchoolSystemType.ZHENGFANG_CLASSIC,
            baseUrl = "",
            loginUrl = "default2.aspx",
            description = "适用于大多数经典正方教务，输入学校教务系统地址后账号密码登录",
            badge = "直连抓取"
        ),
        SchoolPreset(
            id = "zjut_zf",
            name = "浙江工业大学 (正方经典版示例)",
            pinyin = "zhejianggongyedaxue zjut",
            keywords = listOf("浙工大", "zjut", "工大"),
            systemType = SchoolSystemType.ZHENGFANG_CLASSIC,
            baseUrl = "http://www.gdgljyw.zjut.edu.cn",
            loginUrl = "default2.aspx",
            description = "正方经典系统典型配置示例",
            badge = "正方经典"
        ),
        SchoolPreset(
            id = "custom_zf",
            name = "其他正方高校 (手动输入地址)",
            pinyin = "qitagaozhengfang zf custom",
            keywords = listOf("其他", "自定义", "手动", "zhengfang"),
            systemType = SchoolSystemType.CUSTOM_ZHENGFANG,
            baseUrl = "",
            loginUrl = "",
            description = "输入任意支持正方经典版 default2.aspx 的高校教务地址",
            badge = "自定义"
        )
    )

    /** 获取所有预设高校 */
    fun allPresets(): List<SchoolPreset> = PRESETS

    /** 根据 ID 查询高校预设 */
    fun findById(id: String): SchoolPreset? = PRESETS.firstOrNull { it.id.equals(id, ignoreCase = true) }

    /**
     * 搜索高校：按中文名、拼音、关键词匹配，空查询返回全部。
     */
    fun search(query: String): List<SchoolPreset> {
        val q = query.trim()
        if (q.isBlank()) return PRESETS
        return PRESETS.filter { it.matches(q) }
    }
}
