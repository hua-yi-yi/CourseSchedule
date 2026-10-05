package com.chen.schedule.island

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IslandNativePolicyTest {
    private val approval = XiaomiScenarioApproval(true, "approved_course_business", setOf(2, 3))
    private val config = XiaomiApprovalConfig("issued_app_id", true, approval, approval)

    @Test
    fun `缺少审批与配置始终降级普通通知`() {
        val result = IslandNativePolicy.xiaomi(3, true, true, XiaomiApprovalConfig(), true)
        assertFalse(result.nativeAvailable)
        assertTrue(result.status.contains("待审批"))
    }

    @Test
    fun `候课和上课分别审批不交叉授权`() {
        val onlyUpcoming = config.copy(ongoing = XiaomiScenarioApproval())
        assertTrue(IslandNativePolicy.xiaomi(3, true, true, onlyUpcoming, true, IslandCoursePhase.UPCOMING).nativeAvailable)
        assertFalse(IslandNativePolicy.xiaomi(3, true, true, onlyUpcoming, true, IslandCoursePhase.ONGOING).nativeAvailable)
    }

    @Test
    fun `OS2资格不能启用OS3`() {
        val onlyOs2 = config.copy(upcoming = approval.copy(approvedProtocols = setOf(2)), ongoing = XiaomiScenarioApproval())
        assertTrue(IslandNativePolicy.xiaomi(2, false, true, onlyOs2, true).nativeAvailable)
        assertFalse(IslandNativePolicy.xiaomi(3, true, true, onlyOs2, true).nativeAvailable)
    }

    @Test
    fun `OS3必须同时确认岛硬件能力`() {
        assertFalse(IslandNativePolicy.xiaomi(3, false, true, config, true).nativeAvailable)
        assertTrue(IslandNativePolicy.xiaomi(3, true, true, config, true).nativeAvailable)
    }

    @Test
    fun `未知与OS1协议保守降级`() {
        for (protocol in listOf(0, 1, 4)) {
            val result = IslandNativePolicy.xiaomi(protocol, true, true, config, true)
            assertFalse(result.nativeAvailable)
            assertEquals(protocol, result.protocolVersion)
        }
    }

    @Test
    fun `权限关闭和查询失败都降级且说明原因`() {
        val denied = IslandNativePolicy.xiaomi(3, true, false, config, true)
        val failed = IslandNativePolicy.xiaomi(3, true, null, config, true)
        assertFalse(denied.nativeAvailable)
        assertTrue(denied.status.contains("未开启"))
        assertFalse(failed.nativeAvailable)
        assertTrue(failed.status.contains("查询失败"))
    }

    @Test
    fun `包标识或构建签名环境配置不匹配时降级`() {
        assertFalse(IslandNativePolicy.xiaomi(3, true, true, config.copy(appId = ""), true).nativeAvailable)
        assertFalse(IslandNativePolicy.xiaomi(3, true, true, config.copy(debugBuildFlag = null), true).nativeAvailable)
        assertFalse(IslandNativePolicy.xiaomi(3, true, true, config, false).nativeAvailable)
    }

    @Test
    fun `业务标识空白不能冒充已获批`() {
        val incomplete = config.copy(upcoming = approval.copy(business = " "), ongoing = XiaomiScenarioApproval())
        assertFalse(IslandNativePolicy.xiaomi(3, true, true, incomplete, true).nativeAvailable)
    }

    @Test
    fun `支持各厂商别名且所有待接入厂商使用普通通知`() {
        assertEquals(IslandVendor.XIAOMI, IslandNativePolicy.vendor("Xiaomi", "Redmi"))
        assertEquals(IslandVendor.XIAOMI, IslandNativePolicy.vendor("unknown", "POCO"))
        assertEquals(IslandVendor.OPPO, IslandNativePolicy.vendor("ONEPLUS", "OnePlus"))
        assertEquals(IslandVendor.VIVO, IslandNativePolicy.vendor("vivo", "iQOO"))
        assertEquals(IslandVendor.HONOR, IslandNativePolicy.vendor("HONOR", "HONOR"))
        assertEquals(IslandVendor.HUAWEI, IslandNativePolicy.vendor("HUAWEI", "HUAWEI"))
        assertEquals(IslandVendor.GENERIC, IslandNativePolicy.vendor("Google", "google"))
        for (vendor in IslandVendor.entries) {
            val result = IslandNativePolicy.pending(vendor)
            assertFalse(result.nativeAvailable)
            assertTrue(result.status.contains("普通课程通知"))
        }
    }
}
