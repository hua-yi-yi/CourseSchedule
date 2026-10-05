package com.chen.schedule.island

import android.app.Notification
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import androidx.annotation.WorkerThread
import com.chen.schedule.BuildConfig
import com.chen.schedule.R

/**
 * 各厂商实现的隔离接口。OPPO/荣耀获批后在此接入授权 SDK；现阶段无 SDK 运行依赖。
 * 通知 id、notify/cancel、系统通知权限和通知通道归协调器所有，本接口只修饰通知。
 */
internal interface NativeCourseNotificationAdapter {
    fun capability(context: Context): IslandCapability
    fun decorate(context: Context, builder: Notification.Builder, state: IslandState): IslandCapability
}

object IslandNativeAdapters {
    private val xiaomi = XiaomiNativeAdapter()

    /** 权限查询包含 Binder 调用，调用方须在 Dispatchers.IO 中执行。 */
    @WorkerThread
    fun capability(context: Context): IslandCapability = adapter().capability(context)

    @WorkerThread
    fun decorate(context: Context, builder: Notification.Builder, state: IslandState): IslandCapability =
        adapter().decorate(context, builder, state)

    private fun adapter(): NativeCourseNotificationAdapter {
        val vendor = IslandNativePolicy.vendor(Build.MANUFACTURER, Build.BRAND)
        return if (vendor == IslandVendor.XIAOMI) xiaomi else PendingNativeAdapter(vendor)
    }
}

private class PendingNativeAdapter(private val vendor: IslandVendor) : NativeCourseNotificationAdapter {
    override fun capability(context: Context): IslandCapability = IslandNativePolicy.pending(vendor)
    override fun decorate(context: Context, builder: Notification.Builder, state: IslandState): IslandCapability = capability(context)
}

private class XiaomiNativeAdapter : NativeCourseNotificationAdapter {
    override fun capability(context: Context): IslandCapability {
        if (onMainThread()) return deferred()
        val snapshot = inspect(context)
        return snapshot.capability()
    }

    override fun decorate(context: Context, builder: Notification.Builder, state: IslandState): IslandCapability {
        // Builder 若被复用，失去资格后也不能保留上一条课程的厂商扩展。
        clearExtras(builder)
        if (onMainThread()) return deferred()
        val phase = when (state) {
            is IslandState.Upcoming -> IslandCoursePhase.UPCOMING
            is IslandState.Ongoing -> IslandCoursePhase.ONGOING
            else -> return IslandCapability(IslandVendor.XIAOMI, status = "无候课或上课状态，不发送原生扩展")
        }
        return try {
            val snapshot = inspect(context)
            val capability = snapshot.capability(phase)
            if (!capability.nativeAvailable) return capability
            val payload = XiaomiCourseNotificationPayload.create(state, snapshot.protocol, snapshot.config.scenario(phase).business)
                ?: return capability.copy(nativeAvailable = false, status = "课程状态或展示时长不符合原生条件，显示普通课程通知")
            val extras = Bundle().apply {
                putString("miui.focus.param", payload)
                putBundle("miui.focus.pics", Bundle().apply {
                    putParcelable(XiaomiCourseNotificationPayload.ICON_KEY, Icon.createWithResource(context, R.mipmap.ic_launcher))
                })
            }
            builder.addExtras(extras)
            capability.copy(status = "小米原生通知参数已提交给协调器，实际展示待系统确认")
        } catch (_: Exception) {
            clearExtras(builder)
            IslandCapability(IslandVendor.XIAOMI, status = "小米扩展构建失败，显示普通课程通知")
        }
    }

    private fun inspect(context: Context): Snapshot {
        val protocol = runCatching { Settings.System.getInt(context.contentResolver, "notification_focus_protocol", 0) }.getOrDefault(0)
        val supported = protocol == 3 && runCatching {
            val properties = Class.forName("android.os.SystemProperties")
            properties.getDeclaredMethod("getBoolean", String::class.java, Boolean::class.javaPrimitiveType)
                .invoke(null, "persist.sys.feature.island", false) as? Boolean ?: false
        }.getOrDefault(false)
        val permission = if (protocol in setOf(2, 3)) runCatching {
            context.contentResolver.call(
                Uri.parse("content://miui.statusbar.notification.public"), "canShowFocus", null,
                Bundle().apply { putString("package", context.packageName) }
            )?.getBoolean("canShowFocus", false)
        }.getOrNull() else null
        return Snapshot(protocol, supported, permission, configuration(context))
    }

    @Suppress("DEPRECATION")
    private fun configuration(context: Context): XiaomiApprovalConfig = runCatching {
        val data = context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA).metaData ?: Bundle()
        fun scenario(name: String): XiaomiScenarioApproval {
            val prefix = "course_schedule.island.xiaomi.$name"
            return XiaomiScenarioApproval(
                approved = data.getBoolean("$prefix.approved", false),
                business = data.getString("$prefix.business").orEmpty().trim(),
                approvedProtocols = data.getString("$prefix.protocols").orEmpty().split(',').mapNotNull { it.trim().toIntOrNull() }.toSet()
            )
        }
        XiaomiApprovalConfig(
            appId = data.get("com.xiaomi.xms.APP_ID")?.toString().orEmpty().trim(),
            debugBuildFlag = if (data.containsKey("com.xiaomi.xms.BUILD_TYPE_DEBUG")) data.getBoolean("com.xiaomi.xms.BUILD_TYPE_DEBUG") else null,
            upcoming = scenario("upcoming"),
            ongoing = scenario("ongoing")
        )
    }.getOrDefault(XiaomiApprovalConfig())

    private fun onMainThread(): Boolean = Looper.myLooper() == Looper.getMainLooper()
    private fun deferred(): IslandCapability = IslandCapability(IslandVendor.XIAOMI, status = "原生能力需后台检查，显示普通课程通知")
    private fun clearExtras(builder: Notification.Builder) {
        builder.extras.remove("miui.focus.param")
        builder.extras.remove("miui.focus.pics")
    }

    private data class Snapshot(val protocol: Int, val islandSupported: Boolean, val permission: Boolean?, val config: XiaomiApprovalConfig) {
        fun capability(phase: IslandCoursePhase? = null): IslandCapability =
            IslandNativePolicy.xiaomi(protocol, islandSupported, permission, config, BuildConfig.DEBUG, phase)
    }
}
