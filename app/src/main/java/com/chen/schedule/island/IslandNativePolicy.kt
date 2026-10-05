package com.chen.schedule.island

import java.util.Locale

enum class IslandVendor { XIAOMI, OPPO, VIVO, HONOR, HUAWEI, GENERIC }

/** nativeAvailable 表示符合发送条件，不是 SystemUI 已实际展示的回执。 */
data class IslandCapability(
    val vendor: IslandVendor,
    val nativeAvailable: Boolean = false,
    val status: String,
    val protocolVersion: Int = 0
)

internal enum class IslandCoursePhase { UPCOMING, ONGOING }

/** 只能由获批后的安装包配置；不从用户偏好或课程数据读取业务标识。 */
internal data class XiaomiScenarioApproval(
    val approved: Boolean = false,
    val business: String = "",
    val approvedProtocols: Set<Int> = emptySet()
) {
    fun supports(protocol: Int): Boolean = approved && business.isNotBlank() && protocol in approvedProtocols
}

internal data class XiaomiApprovalConfig(
    val appId: String = "",
    val debugBuildFlag: Boolean? = null,
    val upcoming: XiaomiScenarioApproval = XiaomiScenarioApproval(),
    val ongoing: XiaomiScenarioApproval = XiaomiScenarioApproval()
) {
    fun scenario(phase: IslandCoursePhase): XiaomiScenarioApproval = when (phase) {
        IslandCoursePhase.UPCOMING -> upcoming
        IslandCoursePhase.ONGOING -> ongoing
    }
}

/** 无 Android 依赖的资格判断，供单元测试覆盖降级条件。 */
internal object IslandNativePolicy {
    fun vendor(manufacturer: String, brand: String): IslandVendor {
        val names = setOf(manufacturer.lowercase(Locale.ROOT), brand.lowercase(Locale.ROOT))
        return when {
            names.any { it in setOf("xiaomi", "redmi", "poco") } -> IslandVendor.XIAOMI
            names.any { it in setOf("oppo", "oneplus", "realme") } -> IslandVendor.OPPO
            names.any { it in setOf("vivo", "iqoo") } -> IslandVendor.VIVO
            "honor" in names -> IslandVendor.HONOR
            "huawei" in names -> IslandVendor.HUAWEI
            else -> IslandVendor.GENERIC
        }
    }

    fun pending(vendor: IslandVendor): IslandCapability = IslandCapability(
        vendor = vendor,
        status = when (vendor) {
            IslandVendor.XIAOMI -> "小米原生接入待审批，显示普通课程通知"
            IslandVendor.OPPO -> "OPPO／一加流体云待接入，显示普通课程通知"
            IslandVendor.VIVO -> "vivo 原生接口待确认，显示普通课程通知"
            IslandVendor.HONOR -> "荣耀全局触达待接入，显示普通课程通知"
            IslandVendor.HUAWEI -> "华为 Android 实况窗接口待确认，显示普通课程通知"
            IslandVendor.GENERIC -> "当前设备使用普通课程通知"
        }
    )

    fun xiaomi(
        protocol: Int,
        islandSupported: Boolean,
        focusPermission: Boolean?,
        config: XiaomiApprovalConfig,
        debugBuild: Boolean,
        phase: IslandCoursePhase? = null
    ): IslandCapability {
        fun fallback(message: String) = IslandCapability(IslandVendor.XIAOMI, status = message, protocolVersion = protocol)
        // 未来协议须重新核对模板，不能把未知版本当作 OS3。
        if (protocol !in setOf(2, 3)) return fallback("此系统未提供已适配的小米焦点通知协议，显示普通课程通知")
        if (protocol == 3 && !islandSupported) return fallback("此设备未确认支持小米超级岛，显示普通课程通知")
        val hasApproval = if (phase == null) {
            config.upcoming.supports(protocol) || config.ongoing.supports(protocol)
        } else config.scenario(phase).supports(protocol)
        if (!hasApproval) return fallback("小米课程场景待审批或配置，显示普通课程通知")
        if (config.appId.isBlank() || config.debugBuildFlag != debugBuild) {
            return fallback("小米鉴权配置未完成，显示普通课程通知")
        }
        if (focusPermission == null) return fallback("小米焦点通知权限查询失败，显示普通课程通知")
        if (!focusPermission) return fallback("小米焦点通知权限未开启，显示普通课程通知")
        val presentation = if (protocol == 2) "HyperOS 2 焦点通知" else "HyperOS 3 超级岛"
        return IslandCapability(IslandVendor.XIAOMI, true, "$presentation 发送条件已满足，展示由系统决定", protocol)
    }
}
