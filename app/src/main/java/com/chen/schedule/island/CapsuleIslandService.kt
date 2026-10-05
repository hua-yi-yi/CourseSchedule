package com.chen.schedule.island

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.drawable.Icon
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.DisplayMetrics
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import com.chen.schedule.MainActivity
import com.chen.schedule.R
import com.chen.schedule.domain.model.Course
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** 仅用于用户选择的兼容悬浮或临时预览，不负责课程通知 8888。 */
class CapsuleIslandService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var tickerJob: Job? = null
    private var screenReceiver: BroadcastReceiver? = null
    private lateinit var windowManager: WindowManager
    private lateinit var prefs: IslandPrefs
    private var capsuleView: CapsuleIslandView? = null
    private var windowParams: WindowManager.LayoutParams? = null
    private var isViewAttached = false
    private var isReady = false
    private var appliedX = 0
    private var appliedY = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        prefs = IslandPrefs.init(this)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        if (!shouldRun()) {
            stopSelf()
            return
        }
        try {
            startForegroundServiceNotification()
            setupFloatingWindow()
            isReady = true
            CapsuleIslandManager.onRunning()
            registerScreenReceiver()
            if (getSystemService(PowerManager::class.java).isInteractive) startTicker()
        } catch (error: Exception) {
            failAndStop("悬浮服务未能启动，请检查系统权限后重试", error)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            // 通知中的停止操作关闭正式展示意愿；预览退出不修改正式模式。
            if (prefs.currentConfig().mockMode) {
                CapsuleIslandManager.setMockTest(this, false)
            } else {
                prefs.enabled = false
                CapsuleIslandManager.sync(this)
            }
            if (isReady && shouldRun()) {
                scope.launch { updateIslandState() }
                return START_STICKY
            }
            stopSelf()
            return START_NOT_STICKY
        }
        if (!isReady || !shouldRun()) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_REFRESH) {
            try {
                startForegroundServiceNotification()
            } catch (error: Exception) {
                failAndStop("悬浮服务通知更新失败，请检查系统权限后重试", error)
                return START_NOT_STICKY
            }
            scope.launch { updateIslandState() }
        }
        return START_STICKY
    }

    private fun shouldRun(): Boolean {
        val config = prefs.currentConfig()
        return CapsuleIslandManager.canDrawOverlays(this) &&
            (config.mockMode || (config.enabled && config.mode == IslandDisplayMode.OVERLAY))
    }

    private fun startForegroundServiceNotification() {
        val manager = getSystemService(NotificationManager::class.java)
            ?: error("Notification manager is unavailable")
        manager.createNotificationChannel(NotificationChannel(
            CHANNEL_ID, "兼容悬浮服务", NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "用户主动开启的课程悬浮窗口；可在通知中停止"
            setShowBadge(false)
        })
        val openIntent = PendingIntent.getActivity(this, NOTIFICATION_ID,
            Intent(this, MainActivity::class.java).apply {
                putExtra(MainActivity.EXTRA_OPEN_TIMETABLE, true)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stopIntent = PendingIntent.getService(this, NOTIFICATION_ID,
            Intent(this, CapsuleIslandService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(if (prefs.currentConfig().mockMode) "课程悬浮预览中" else "课程兼容悬浮运行中")
            .setContentText("点击打开课表，或选择停止")
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(Notification.Action.Builder(
                Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel),
                "停止", stopIntent
            ).build())
            .build()
        // 不吞掉前台服务权限或启动失败：调用者会终止服务并报告实际状态。
        startForeground(NOTIFICATION_ID, notification)
    }

    private fun setupFloatingWindow() {
        check(CapsuleIslandManager.canDrawOverlays(this)) { "Overlay permission is missing" }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = 0
            y = (prefs.positionY * resources.displayMetrics.density).roundToInt()
            if (Build.VERSION.SDK_INT >= 30) {
                setFitInsetsTypes(0)
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            } else if (Build.VERSION.SDK_INT >= 28) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        windowParams = params
        val view = CapsuleIslandView(this).apply {
            visibility = View.GONE
            onToggleExpand = { post { constrainWindow() } }
            onDragPositionChanged = { dx, dy ->
                params.x += dx
                params.y += dy
                constrainWindow(savePosition = true)
            }
            onOpenApp = {
                try {
                    startActivity(Intent(this@CapsuleIslandService, MainActivity::class.java).apply {
                        putExtra(MainActivity.EXTRA_OPEN_TIMETABLE, true)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    })
                } catch (error: Exception) {
                    Log.w(TAG, "Unable to open schedule", error)
                }
                setExpanded(false, animate = false)
            }
            onCloseRequested = { setExpanded(false, animate = true) }
            addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
                if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) {
                    constrainWindow()
                }
            }
            setOnApplyWindowInsetsListener { _, insets ->
                post { constrainWindow() }
                insets
            }
        }
        capsuleView = view
        val area = screenArea()
        view.setAvailableBounds(area.bounds.width, area.bounds.height)
        windowManager.addView(view, params)
        appliedX = params.x
        appliedY = params.y
        isViewAttached = true
        check(constrainWindow()) { "Unable to place overlay in the safe screen area" }
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (isActive) {
                updateIslandState()
                delay(30_000L)
            }
        }
    }

    private suspend fun updateIslandState() {
        if (!isReady || !shouldRun()) {
            stopSelf()
            return
        }
        val view = capsuleView ?: return
        val config = prefs.currentConfig()
        try {
            val state = if (config.mockMode) generateMockState(config.mockState)
            else withContext(Dispatchers.IO) { IslandStateRepository.load(this@CapsuleIslandService) }
            // 读取过程中模式可能已切换，不显示旧请求的结果。
            if (!isReady || !shouldRun() || prefs.currentConfig().mockMode != config.mockMode) return
            if (state is IslandState.None ||
                (config.onlyWhenClass && state is IslandState.Idle)) {
                view.visibility = View.GONE
            } else {
                view.visibility = View.VISIBLE
                view.updateState(state)
                view.post { constrainWindow() }
            }
        } catch (error: Exception) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            view.visibility = View.GONE
            Log.e(TAG, "Unable to read current island state", error)
        }
    }

    private fun generateMockState(type: Int): IslandState {
        val now = System.currentTimeMillis()
        val dummyCourse = Course(
            name = if (type == 0) "高等数学 (模拟)" else "大学物理实验 (模拟)",
            classroom = if (type == 0) "教学楼 A201" else "实验中心 302",
            teacher = if (type == 0) "张教授" else "李老师",
            color = if (type == 0) 0xFF3B82F6 else 0xFF10B981
        )
        return if (type == 0) IslandState.Ongoing(
            course = dummyCourse, courseName = dummyCourse.name,
            classroom = dummyCourse.classroom, teacher = dummyCourse.teacher,
            slotRange = "第 1-2 节", startTime = "08:00", endTime = "09:40",
            startMillis = now - 40 * 60 * 1000L, endMillis = now + 60 * 60 * 1000L,
            remainingMinutes = 60, totalMinutes = 100, progress = 0.40f,
            color = dummyCourse.color, compactText = "${dummyCourse.name} · 剩60分",
            subText = "@${dummyCourse.classroom} · 09:40下课"
        ) else IslandState.Upcoming(
            course = dummyCourse, courseName = dummyCourse.name,
            classroom = dummyCourse.classroom, teacher = dummyCourse.teacher,
            slotRange = "第 3-4 节", startTime = "10:00", endTime = "11:40",
            startMillis = now + 15 * 60 * 1000L, minutesUntilStart = 15,
            color = dummyCourse.color, compactText = "15分后 · ${dummyCourse.name}",
            subText = "@${dummyCourse.classroom} · 10:00上课"
        )
    }

    private fun registerScreenReceiver() {
        screenReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_OFF -> tickerJob?.cancel()
                    Intent.ACTION_SCREEN_ON -> if (isReady) startTicker()
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(screenReceiver, filter, RECEIVER_NOT_EXPORTED)
        else registerReceiver(screenReceiver, filter)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (isReady) capsuleView?.post { constrainWindow(savePosition = true) }
    }

    private data class ScreenArea(val screenWidth: Int, val bounds: OverlaySafeBounds)

    private fun screenArea(): ScreenArea {
        if (Build.VERSION.SDK_INT >= 30) {
            val metrics = windowManager.maximumWindowMetrics
            val insets = metrics.windowInsets.getInsetsIgnoringVisibility(
                WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
            )
            val width = metrics.bounds.width()
            val height = metrics.bounds.height()
            return ScreenArea(width, OverlaySafeBounds(
                insets.left, insets.top, width - insets.right, height - insets.bottom
            ))
        }
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)
        val insets = capsuleView?.rootWindowInsets
        val cutout = if (Build.VERSION.SDK_INT >= 28) insets?.displayCutout else null
        @Suppress("DEPRECATION")
        val top = maxOf(insets?.systemWindowInsetTop ?: systemDimension("status_bar_height", 24),
            if (Build.VERSION.SDK_INT >= 28) cutout?.safeInsetTop ?: 0 else 0)
        @Suppress("DEPRECATION")
        val bottom = maxOf(insets?.systemWindowInsetBottom ?: systemDimension("navigation_bar_height", 48),
            if (Build.VERSION.SDK_INT >= 28) cutout?.safeInsetBottom ?: 0 else 0)
        @Suppress("DEPRECATION")
        val left = maxOf(insets?.systemWindowInsetLeft ?: 0,
            if (Build.VERSION.SDK_INT >= 28) cutout?.safeInsetLeft ?: 0 else 0)
        @Suppress("DEPRECATION")
        val right = maxOf(insets?.systemWindowInsetRight ?: 0,
            if (Build.VERSION.SDK_INT >= 28) cutout?.safeInsetRight ?: 0 else 0)
        return ScreenArea(metrics.widthPixels, OverlaySafeBounds(
            left, top, metrics.widthPixels - right, metrics.heightPixels - bottom
        ))
    }

    private fun systemDimension(name: String, fallbackDp: Int): Int {
        val identifier = resources.getIdentifier(name, "dimen", "android")
        return if (identifier != 0) resources.getDimensionPixelSize(identifier)
        else (fallbackDp * resources.displayMetrics.density).roundToInt()
    }

    /** 展开、拖动及旋转都以当前屏幕安全区重新夹取位置。 */
    private fun constrainWindow(savePosition: Boolean = false): Boolean {
        val view = capsuleView ?: return false
        val params = windowParams ?: return false
        if (!isViewAttached) return false
        try {
            val area = screenArea()
            view.setAvailableBounds(area.bounds.width, area.bounds.height)
            val point = OverlayGeometry.clampCentered(
                params.x, params.y, view.width, view.height, area.screenWidth, area.bounds
            )
            val moved = appliedX != point.x || appliedY != point.y
            params.x = point.x
            params.y = point.y
            if (savePosition) prefs.positionY = (params.y / resources.displayMetrics.density).roundToInt()
            if (moved) {
                windowManager.updateViewLayout(view, params)
                appliedX = params.x
                appliedY = params.y
            }
            return true
        } catch (error: Exception) {
            failAndStop("悬浮窗口更新失败，请重新授权后重试", error)
            return false
        }
    }

    private fun failAndStop(message: String, error: Exception) {
        isReady = false
        tickerJob?.cancel()
        Log.e(TAG, message, error)
        CapsuleIslandManager.onFailure(message)
        stopSelf()
    }

    override fun onDestroy() {
        isReady = false
        tickerJob?.cancel()
        scope.cancel()
        screenReceiver?.let {
            try { unregisterReceiver(it) }
            catch (error: Exception) { Log.w(TAG, "Unable to unregister screen receiver", error) }
        }
        if (isViewAttached) {
            try { capsuleView?.let(windowManager::removeView) }
            catch (error: Exception) { Log.w(TAG, "Unable to remove floating view", error) }
            finally { isViewAttached = false }
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        CapsuleIslandManager.onStopped()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "CapsuleIslandService"
        const val CHANNEL_ID = "capsule_island_service"
        const val NOTIFICATION_ID = 9527
        const val ACTION_REFRESH = "com.chen.schedule.island.ACTION_REFRESH"
        const val ACTION_STOP = "com.chen.schedule.island.ACTION_STOP"
    }
}

internal data class OverlaySafeBounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = (right - left).coerceAtLeast(1)
    val height: Int get() = (bottom - top).coerceAtLeast(1)
}

internal data class OverlayPosition(val x: Int, val y: Int)

/** x 使用 TOP|CENTER_HORIZONTAL 的中心偏移，y 使用屏幕顶边坐标。 */
internal object OverlayGeometry {
    fun clampCentered(
        x: Int, y: Int, windowWidth: Int, windowHeight: Int,
        screenWidth: Int, bounds: OverlaySafeBounds
    ): OverlayPosition {
        val width = windowWidth.coerceIn(0, bounds.width)
        val height = windowHeight.coerceIn(0, bounds.height)
        val centeredLeft = (screenWidth - width) / 2
        val safeLeft = (centeredLeft + x).coerceIn(bounds.left, (bounds.right - width).coerceAtLeast(bounds.left))
        val safeTop = y.coerceIn(bounds.top, (bounds.bottom - height).coerceAtLeast(bounds.top))
        return OverlayPosition(safeLeft - centeredLeft, safeTop)
    }
}
