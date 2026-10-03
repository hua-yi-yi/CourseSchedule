package com.chen.schedule.island

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import com.chen.schedule.MainActivity
import com.chen.schedule.R
import com.chen.schedule.di.DatabaseEntryPoint
import com.chen.schedule.domain.model.Course
import com.chen.schedule.util.WeekCalculator
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * 桌面全局胶囊灵动岛前台悬浮窗服务。
 * 具备完善的生命周期安全保障与防崩溃机制。
 */
class CapsuleIslandService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var tickerJob: Job? = null
    private var screenReceiver: BroadcastReceiver? = null

    private lateinit var windowManager: WindowManager
    private lateinit var prefs: IslandPrefs
    private var capsuleView: CapsuleIslandView? = null
    private var windowParams: WindowManager.LayoutParams? = null

    private val windowLock = Any()
    @Volatile
    private var isViewAttached = false

    private var startDragX = 0
    private var startDragY = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        prefs = IslandPrefs.init(this)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        startForegroundServiceNotification()
        setupFloatingWindow()
        registerScreenReceiver()
        startTicker()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_REFRESH -> {
                scope.launch { updateIslandState() }
            }
        }
        return START_STICKY
    }

    private fun startForegroundServiceNotification() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "胶囊灵动岛服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "正在常驻运行桌面课程灵动岛"
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                this.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("课程灵动岛运行中")
                .setContentText("点击打开课表或调整灵动岛设置")
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("课程灵动岛运行中")
                .setContentText("点击打开课表")
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build()
        }

        try {
            startForeground(NOTIFICATION_ID, notification)
        } catch (_: Exception) {
            // Android 14+ FGS security fallback
        }
    }

    private fun setupFloatingWindow() {
        if (!Settings.canDrawOverlays(this)) {
            // 权限未授予，安全退出
            stopSelf()
            return
        }

        val density = resources.displayMetrics.density
        val topMarginPx = (prefs.positionY * density).toInt()

        val layoutType = if (Build.VERSION.SDK_INT >= 26) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = 0
            y = topMarginPx
        }
        windowParams = params

        val view = CapsuleIslandView(this).apply {
            onToggleExpand = { expanded ->
                windowParams?.let { p ->
                    if (expanded) {
                        p.flags = p.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL.inv()
                    } else {
                        p.flags = p.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    }
                    safeUpdateViewLayout(this, p)
                }
            }
            onDragPositionChanged = { dx, dy ->
                windowParams?.let { p ->
                    p.x = p.x + dx
                    p.y = (p.y + dy).coerceAtLeast(0)
                    prefs.positionY = (p.y / density).toInt()
                    safeUpdateViewLayout(this, p)
                }
            }
            onOpenApp = {
                val intent = Intent(this@CapsuleIslandService, MainActivity::class.java).apply {
                    this.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                startActivity(intent)
                setExpanded(false, animate = false)
            }
            onCloseRequested = {
                setExpanded(false, animate = true)
            }
        }
        capsuleView = view

        safeAddView(view, params)
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (isActive) {
                updateIslandState()
                delay(30_000L) // 每 30 秒核对一次课程状态与进度条
            }
        }
    }

    private suspend fun updateIslandState() {
        val view = capsuleView ?: return
        val currentPrefs = prefs.currentConfig()

        // 1. 测试模拟模式
        if (currentPrefs.mockMode) {
            val mockState = generateMockState(currentPrefs.mockState)
            withContext(Dispatchers.Main) {
                view.visibility = View.VISIBLE
                view.updateState(mockState)
            }
            return
        }

        // 2. 真实数据计算模式
        val entry = EntryPointAccessors.fromApplication(applicationContext, DatabaseEntryPoint::class.java)
        val semester = entry.semesterRepository().getCurrentSemester()
        if (semester == null) {
            withContext(Dispatchers.Main) {
                view.visibility = View.GONE
            }
            return
        }

        val courses = entry.courseRepository().getCoursesBySemester(semester.id).first()
        val slots = entry.timeSlotRepository().getTimeSlotsByScheme(semester.schemeId).first()
        val now = System.currentTimeMillis()
        val currentWeek = WeekCalculator.activeWeek(semester.startDate, semester.totalWeeks, now)

        if (currentWeek == null) {
            withContext(Dispatchers.Main) {
                view.visibility = View.GONE
            }
            return
        }

        val todayDow = LocalDate.now().dayOfWeek.value
        val state = IslandStateCalculator.calculate(
            nowMillis = now,
            slots = slots,
            courses = courses,
            currentWeek = currentWeek,
            todayDayOfWeek = todayDow,
            leadMinutes = currentPrefs.leadMinutes
        )

        withContext(Dispatchers.Main) {
            if (currentPrefs.onlyWhenClass && (state is IslandState.Idle || state is IslandState.None)) {
                // 用户设置「仅在有课或临近上课时显示」，此时隐藏
                view.visibility = View.GONE
            } else if (state is IslandState.None) {
                view.visibility = View.GONE
            } else {
                view.visibility = View.VISIBLE
                view.updateState(state)
            }
        }
    }

    private fun generateMockState(type: Int): IslandState {
        val dummyCourse = Course(
            name = if (type == 0) "高等数学 (模拟)" else "大学物理实验 (模拟)",
            classroom = if (type == 0) "教学楼 A201" else "实验中心 302",
            teacher = if (type == 0) "张教授" else "李老师",
            color = if (type == 0) 0xFF3B82F6 else 0xFF10B981
        )
        return if (type == 0) {
            IslandState.Ongoing(
                course = dummyCourse,
                courseName = dummyCourse.name,
                classroom = dummyCourse.classroom,
                teacher = dummyCourse.teacher,
                slotRange = "第 1-2 节",
                startTime = "08:00",
                endTime = "09:40",
                startMillis = System.currentTimeMillis() - 40 * 60 * 1000L,
                endMillis = System.currentTimeMillis() + 60 * 60 * 1000L,
                remainingMinutes = 60,
                totalMinutes = 100,
                progress = 0.40f,
                color = dummyCourse.color,
                compactText = "${dummyCourse.name} · 剩60分",
                subText = "@${dummyCourse.classroom} · 09:40下课"
            )
        } else {
            IslandState.Upcoming(
                course = dummyCourse,
                courseName = dummyCourse.name,
                classroom = dummyCourse.classroom,
                teacher = dummyCourse.teacher,
                slotRange = "第 3-4 节",
                startTime = "10:00",
                endTime = "11:40",
                startMillis = System.currentTimeMillis() + 15 * 60 * 1000L,
                minutesUntilStart = 15,
                color = dummyCourse.color,
                compactText = "15分后 · ${dummyCourse.name}",
                subText = "@${dummyCourse.classroom} · 10:00上课"
            )
        }
    }

    private fun registerScreenReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        screenReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_OFF -> {
                        tickerJob?.cancel() // 灭屏后立即停止轮询，0 耗电
                    }
                    Intent.ACTION_SCREEN_ON -> {
                        startTicker() // 亮屏后立即唤醒轮询
                    }
                }
            }
        }
        registerReceiver(screenReceiver, filter)
    }

    private fun safeAddView(view: View, params: WindowManager.LayoutParams) {
        synchronized(windowLock) {
            if (!isViewAttached) {
                try {
                    windowManager.addView(view, params)
                    isViewAttached = true
                } catch (_: Exception) {}
            }
        }
    }

    private fun safeUpdateViewLayout(view: View, params: WindowManager.LayoutParams) {
        synchronized(windowLock) {
            if (isViewAttached) {
                try {
                    windowManager.updateViewLayout(view, params)
                } catch (_: Exception) {}
            }
        }
    }

    private fun safeRemoveView(view: View?) {
        synchronized(windowLock) {
            if (isViewAttached && view != null) {
                try {
                    windowManager.removeView(view)
                } catch (_: Exception) {} finally {
                    isViewAttached = false
                }
            }
        }
    }

    override fun onDestroy() {
        tickerJob?.cancel()
        scope.cancel()
        screenReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (_: Exception) {}
        }
        safeRemoveView(capsuleView)
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "capsule_island_service"
        const val NOTIFICATION_ID = 9527
        const val ACTION_REFRESH = "com.chen.schedule.island.ACTION_REFRESH"
        const val ACTION_STOP = "com.chen.schedule.island.ACTION_STOP"
    }
}
