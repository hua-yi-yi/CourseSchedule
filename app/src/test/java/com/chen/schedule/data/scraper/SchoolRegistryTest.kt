package com.chen.schedule.data.scraper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SchoolRegistryTest {

    @Test
    fun `预设高校列表不为空且包含主要高校`() {
        val presets = SchoolRegistry.allPresets()
        assertTrue("预设高校至少包含3所", presets.size >= 3)
        assertNotNull("必须包含河南科技大学", SchoolRegistry.findById("haust"))
        assertNotNull("必须包含正方通用模板", SchoolRegistry.findById("zf_classic_template"))
    }

    @Test
    fun `搜索河南科技大学全称与简称`() {
        val byFullName = SchoolRegistry.search("河南科技大学")
        assertEquals(1, byFullName.size)
        assertEquals("haust", byFullName[0].id)

        val byShort = SchoolRegistry.search("河科大")
        assertEquals(1, byShort.size)
        assertEquals("haust", byShort[0].id)

        val byEnglish = SchoolRegistry.search("haust")
        assertEquals(1, byEnglish.size)
        assertEquals("haust", byEnglish[0].id)
    }

    @Test
    fun `搜索拼音与不区分大小写`() {
        val byPinyin = SchoolRegistry.search("henan")
        assertTrue(byPinyin.any { it.id == "haust" })

        val byCaps = SchoolRegistry.search("HAUST")
        assertEquals(1, byCaps.size)
        assertEquals("haust", byCaps[0].id)
    }

    @Test
    fun `搜索正方教务关键字`() {
        val zfResults = SchoolRegistry.search("正方")
        assertTrue("包含正方关键字的高校预设", zfResults.size >= 2)
    }

    @Test
    fun `空关键字或空白字符返回全部预设`() {
        val all = SchoolRegistry.allPresets()
        val searchEmpty = SchoolRegistry.search("")
        val searchSpaces = SchoolRegistry.search("   ")
        assertEquals(all.size, searchEmpty.size)
        assertEquals(all.size, searchSpaces.size)
    }

    @Test
    fun `不存在的高校返回空列表`() {
        val results = SchoolRegistry.search("不存在的高校名称xyz123")
        assertTrue(results.isEmpty())
    }
}
