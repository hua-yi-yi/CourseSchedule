package com.chen.schedule.util

import com.chen.schedule.util.update.VersionComparator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionComparatorTest {

    @Test
    fun testIsNewerVersion() {
        // 远程更高
        assertTrue(VersionComparator.isNewer("v1.2.25", "1.2.24"))
        assertTrue(VersionComparator.isNewer("1.2.25", "1.2.24"))
        assertTrue(VersionComparator.isNewer("v1.3.0", "1.2.24"))
        assertTrue(VersionComparator.isNewer("v2.0.0", "1.2.24"))
        assertTrue(VersionComparator.isNewer("1.2.24.1", "1.2.24"))

        // 相同版本
        assertFalse(VersionComparator.isNewer("v1.2.24", "1.2.24"))
        assertFalse(VersionComparator.isNewer("1.2.24", "1.2.24"))
        assertFalse(VersionComparator.isNewer("1.2.24", "v1.2.24"))

        // 远程更低
        assertFalse(VersionComparator.isNewer("v1.2.23", "1.2.24"))
        assertFalse(VersionComparator.isNewer("1.1.9", "1.2.24"))
        assertFalse(VersionComparator.isNewer("0.9.0", "1.2.24"))

        // 带后缀的情况
        assertTrue(VersionComparator.isNewer("v1.2.25-beta", "1.2.24"))
        assertFalse(VersionComparator.isNewer("v1.2.24-hotfix", "1.2.24"))
    }

    @Test
    fun testParseVersionNumbers() {
        assertEquals(listOf(1, 2, 24), VersionComparator.parseVersionNumbers("v1.2.24"))
        assertEquals(listOf(1, 2, 24), VersionComparator.parseVersionNumbers("1.2.24"))
        assertEquals(listOf(2, 0, 0), VersionComparator.parseVersionNumbers("V2.0.0-rc1"))
        assertEquals(listOf(1, 0), VersionComparator.parseVersionNumbers("1.0"))
    }

    @Test
    fun testFormatVersionName() {
        assertEquals("v1.2.25", VersionComparator.formatVersionName("1.2.25"))
        assertEquals("v1.2.25", VersionComparator.formatVersionName("v1.2.25"))
        assertEquals("v1.2.25", VersionComparator.formatVersionName("V1.2.25"))
    }
}
