package com.chen.schedule.island

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class OverlayRuntimeStatus { STOPPED, STARTING, RUNNING, FAILED }

/** 兼容悬浮模式入口；正式展示意愿只由设置页面和通知停止操作修改。 */
object CapsuleIslandManager {
    private const val TAG = "CapsuleIslandManager"
    const val PREVIEW_DURATION_MILLIS = 60_000L
    private val handler = Handler(Looper.getMainLooper())
    private var previewEnd: Runnable? = null
    private val mutableStatus = MutableStateFlow(OverlayRuntimeStatus.STOPPED)
    val status: StateFlow<OverlayRuntimeStatus> = mutableStatus.asStateFlow()
    var failureMessage: String? = null
        private set

    fun canDrawOverlays(context: Context): Boolean =
        Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(context)

    fun requestOverlayPermission(context: Context) {
        if (Build.VERSION.SDK_INT < 23) return
        try {
            context.startActivity(Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + context.packageName)
            ).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK })
        } catch (error: Exception) {
            Log.w(TAG, "Unable to open overlay permission page", error)
            context.startActivity(Intent(Settings.ACTION_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            })
        }
    }

    /** 在偏好变更、授权返回和数据刷新后执行；系统模式绝不会启动悬浮服务。 */
    fun sync(context: Context) {
        val appContext = context.applicationContext
        val config = IslandPrefs.init(appContext).currentConfig()
        val shouldRun = config.mockMode || (config.enabled && config.mode == IslandDisplayMode.OVERLAY)
        if (!shouldRun) {
            stop(appContext)
            return
        }
        if (!canDrawOverlays(appContext)) {
            stop(appContext)
            onFailure("尚未授予悬浮窗权限")
            return
        }
        try {
            if (mutableStatus.value != OverlayRuntimeStatus.RUNNING) {
                failureMessage = null
                mutableStatus.value = OverlayRuntimeStatus.STARTING
            }
            val intent = Intent(appContext, CapsuleIslandService::class.java).apply {
                action = CapsuleIslandService.ACTION_REFRESH
            }
            if (Build.VERSION.SDK_INT >= 26) appContext.startForegroundService(intent)
            else appContext.startService(intent)
        } catch (error: Exception) {
            Log.e(TAG, "Unable to start compatible overlay", error)
            onFailure("系统未允许启动悬浮服务，请返回应用重试")
        }
    }

    fun start(context: Context) = sync(context)

    /** 仅停止服务，不修改正式开关或模式。 */
    fun stop(context: Context) {
        context.applicationContext.stopService(Intent(context, CapsuleIslandService::class.java))
        onStopped()
    }

    fun refresh(context: Context) = sync(context)

    /** 临时预览不落盘，最多运行一分钟；到期后恢复正式模式。 */
    fun setMockTest(context: Context, enabled: Boolean, type: Int = 0) {
        val appContext = context.applicationContext
        previewEnd?.let(handler::removeCallbacks)
        previewEnd = null
        IslandPrefs.init(appContext).setPreview(enabled, type)
        if (enabled) {
            previewEnd = Runnable {
                previewEnd = null
                IslandPrefs.init(appContext).setPreview(false)
                sync(appContext)
            }.also { handler.postDelayed(it, PREVIEW_DURATION_MILLIS) }
        }
        sync(appContext)
    }

    internal fun onRunning() {
        failureMessage = null
        mutableStatus.value = OverlayRuntimeStatus.RUNNING
    }

    internal fun onStopped() {
        if (mutableStatus.value != OverlayRuntimeStatus.FAILED) {
            mutableStatus.value = OverlayRuntimeStatus.STOPPED
        }
    }

    internal fun onFailure(message: String) {
        failureMessage = message
        mutableStatus.value = OverlayRuntimeStatus.FAILED
    }
}
