package com.chen.schedule.data.scraper

import javax.inject.Inject
import javax.inject.Singleton

/**
 * 教务系统适配器注册中心。
 * 需要支持新的教务系统时,实现 [ScraperAdapter] 并在构造时登记即可。
 */
@Singleton
class ScraperManager @Inject constructor(
    private val zhengfangScraper: ZhengfangScraper
) {
    private val adapters: Map<String, ScraperAdapter> by lazy {
        listOf(zhengfangScraper).associateBy { it.name }
    }

    /** 按名称取适配器;未注册返回 null */
    fun adapter(name: String): ScraperAdapter? = adapters[name]

    /** 所有可用适配器 */
    fun available(): List<ScraperAdapter> = adapters.values.toList()
}
