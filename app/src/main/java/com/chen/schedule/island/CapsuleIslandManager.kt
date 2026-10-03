package com.chen.schedule.island

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * 胶囊灵动岛管理调度入口。
 */
object CapsuleIslandManager {

    /** 检查是否已授予悬浮窗权限。 */
    fun canDrawOverlays(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= 23) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    /** 跳转系统设置授予悬浮窗权限。 */
    fun requestOverlayPermission(context: Context) {
        if (Build.VERSION.SDK_INT >= 23) {
            try {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + context.packageName)
                ).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (_: Exception) {
                val fallbackIntent = Intent(Settings.ACTION_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(fallbackIntent)
            }
        }
    }

    /** 启动桌面灵动岛悬浮服务。 */
    fun start(context: Context) {
        val prefs = IslandPrefs.init(context)
        if (!canDrawOverlays(context)) {
            // 没有权限则仅记录配置，不强制启动以免系统抛出安全异常
            return
        }
        val intent = Intent(context, CapsuleIslandService::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
        prefs.enabled = true
    }

    /** 停止桌面灵动岛悬浮服务。 */
    fun stop(context: Context) {
        val prefs = IslandPrefs.init(context)
        prefs.enabled = false
        val intent = Intent(context, CapsuleIslandService::class.java).apply {
            action = CapsuleIslandService.ACTION_STOP
        }
        context.stopService(intent)
    }

    /** 课表数据或设置变更时刷新灵动岛。 */
    fun refresh(context: Context) {
        val prefs = IslandPrefs.init(context)
        if (!prefs.enabled || !canDrawOverlays(context)) return
        val intent = Intent(context, CapsuleIslandService::class.java).apply {
            action = CapsuleIslandService.ACTION_REFRESH
        }
        try {
            context.startService(intent)
        } catch (_: Exception) {}
    }

    /** 切换模拟测试状态。 */
    fun setMockTest(context: Context, enabled: Boolean, type: Int = 0) {
        val prefs = IslandPrefs.init(context)
        prefs.mockMode = enabled
        prefs.mockState = type
        if (enabled && canDrawOverlays(context)) {
            start(context)
        }
        refresh(context)
    }
}
