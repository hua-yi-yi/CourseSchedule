package com.chen.schedule.island

import android.animation.LayoutTransition
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.chen.schedule.R
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 胶囊灵动岛悬浮视窗根布局。
 * 纯原生代码构建，避免 WindowManager 跨上下文中的 XML 膨胀和主题丢失问题。
 */
class CapsuleIslandView(context: Context) : FrameLayout(context) {

    private val density = context.resources.displayMetrics.density
    private fun dp(value: Float): Int = (value * density).roundToInt()
    private fun sp(value: Float): Float = value

    private var currentState: IslandState = IslandState.None
    var isExpanded: Boolean = false
        private set

    var onToggleExpand: ((Boolean) -> Unit)? = null
    /** 每次回调仅传递自上次回调以来的位移，避免重复累计手势起点位移。 */
    var onDragPositionChanged: ((deltaX: Int, deltaY: Int) -> Unit)? = null
    var onOpenApp: (() -> Unit)? = null
    var onCloseRequested: (() -> Unit)? = null

    // ===== 紧凑胶囊组件 =====
    private val compactContainer: LinearLayout
    private val dotView: View
    private val tvCompactTitle: TextView
    private val tvCompactDivider: TextView
    private val tvCompactInfo: TextView

    // ===== 展开卡片组件 =====
    private val expandedContainer: LinearLayout
    private val tvBadge: TextView
    private val tvExpandedTitle: TextView
    private val btnMinimize: ImageView
    private val chipLocation: LinearLayout
    private val tvLocation: TextView
    private val chipTime: LinearLayout
    private val tvTime: TextView
    private val chipTeacher: LinearLayout
    private val tvTeacher: TextView
    private val progressTrack: FrameLayout
    private val progressIndicator: View
    private val tvProgressLeft: TextView
    private val tvProgressRight: TextView
    private val btnOpenApp: TextView
    private val btnCollapse: TextView

    // 拖拽手势判定
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var emittedDragX = 0
    private var emittedDragY = 0
    private var isDragging = false
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var availableWidth = context.resources.displayMetrics.widthPixels
    private var availableHeight = context.resources.displayMetrics.heightPixels

    init {
        layoutTransition = LayoutTransition().apply {
            enableTransitionType(LayoutTransition.CHANGING)
            setDuration(180)
        }

        // 1. 紧凑胶囊布局
        compactContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = ContextCompat.getDrawable(context, R.drawable.bg_island_pill)
            setPadding(dp(10f), dp(6f), dp(12f), dp(6f))
            elevation = dp(6f).toFloat()

            // 左侧状态指示灯
            dotView = View(context).apply {
                layoutParams = LinearLayout.LayoutParams(dp(8f), dp(8f)).apply {
                    marginEnd = dp(6f)
                }
                background = createCircleDrawable(Color.parseColor("#10B981"))
            }
            addView(dotView)

            // 课程标题
            tvCompactTitle = TextView(context).apply {
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, sp(12.5f))
                paint.isFakeBoldText = true
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            addView(tvCompactTitle)

            // 隔点
            tvCompactDivider = TextView(context).apply {
                text = " · "
                setTextColor(Color.parseColor("#94A3B8"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, sp(11.5f))
            }
            addView(tvCompactDivider)

            // 倒计时信息
            tvCompactInfo = TextView(context).apply {
                setTextColor(Color.parseColor("#38BDF8"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, sp(11.5f))
                maxLines = 1
            }
            addView(tvCompactInfo)
        }
        addView(compactContainer, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER))

        // 2. 展开卡片布局
        expandedContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = ContextCompat.getDrawable(context, R.drawable.bg_island_expanded)
            setPadding(dp(14f), dp(12f), dp(14f), dp(12f))
            elevation = dp(10f).toFloat()
            visibility = View.GONE

            // 顶部栏: 徽标 + 课程大标题 + 最小化按钮
            val topRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL

                tvBadge = TextView(context).apply {
                    background = ContextCompat.getDrawable(context, R.drawable.bg_island_badge)
                    setTextColor(Color.parseColor("#38BDF8"))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, sp(10.5f))
                    paint.isFakeBoldText = true
                    setPadding(dp(6f), dp(2f), dp(6f), dp(2f))
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { marginEnd = dp(8f) }
                }
                addView(tvBadge)

                tvExpandedTitle = TextView(context).apply {
                    setTextColor(Color.WHITE)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, sp(15f))
                    paint.isFakeBoldText = true
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
                addView(tvExpandedTitle)

                btnMinimize = ImageView(context).apply {
                    contentDescription = "收起课程胶囊"
                    setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_island_close))
                    setColorFilter(Color.parseColor("#94A3B8"))
                    layoutParams = LinearLayout.LayoutParams(dp(24f), dp(24f))
                    setPadding(dp(3f), dp(3f), dp(3f), dp(3f))
                    setOnClickListener {
                        setExpanded(false, animate = true)
                    }
                }
                addView(btnMinimize)
            }
            addView(topRow)

            // 信息卡片行 (地点、时间、教师)
            val infoRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dp(8f)
                    bottomMargin = dp(8f)
                }

                val locationPair = createInfoChip(R.drawable.ic_island_location)
                chipLocation = locationPair.first
                tvLocation = locationPair.second
                addView(chipLocation)

                val timePair = createInfoChip(R.drawable.ic_island_time)
                chipTime = timePair.first
                tvTime = timePair.second
                (chipTime.layoutParams as LinearLayout.LayoutParams).marginStart = dp(6f)
                addView(chipTime)

                val teacherPair = createInfoChip(null)
                chipTeacher = teacherPair.first
                tvTeacher = teacherPair.second
                (chipTeacher.layoutParams as LinearLayout.LayoutParams).marginStart = dp(6f)
                addView(chipTeacher)
            }
            addView(infoRow)

            // 进度条轨道
            progressTrack = FrameLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(5f)
                ).apply {
                    topMargin = dp(2f)
                    bottomMargin = dp(4f)
                }
                background = ContextCompat.getDrawable(context, R.drawable.bg_island_progress_track)

                progressIndicator = View(context).apply {
                    layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT)
                    background = createRoundedRectDrawable(Color.parseColor("#38BDF8"), dp(3f).toFloat())
                }
                addView(progressIndicator)
            }
            addView(progressTrack)

            // 进度条下方文字
            val progressLabels = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )

                tvProgressLeft = TextView(context).apply {
                    setTextColor(Color.parseColor("#94A3B8"))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, sp(10.5f))
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
                addView(tvProgressLeft)

                tvProgressRight = TextView(context).apply {
                    setTextColor(Color.parseColor("#CBD5E1"))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, sp(10.5f))
                    paint.isFakeBoldText = true
                }
                addView(tvProgressRight)
            }
            addView(progressLabels)

            // 底部操作栏: 打开课表 + 收起胶囊
            val actionRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dp(10f)
                }

                btnCollapse = TextView(context).apply {
                    text = "收起"
                    setTextColor(Color.parseColor("#94A3B8"))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, sp(12f))
                    setPadding(dp(12f), dp(6f), dp(12f), dp(6f))
                    setOnClickListener {
                        setExpanded(false, animate = true)
                    }
                }
                addView(btnCollapse)

                btnOpenApp = TextView(context).apply {
                    text = "打开课表"
                    setTextColor(Color.WHITE)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, sp(12f))
                    paint.isFakeBoldText = true
                    background = createRoundedRectDrawable(Color.parseColor("#0284C7"), dp(12f).toFloat())
                    setPadding(dp(14f), dp(6f), dp(14f), dp(6f))
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { marginStart = dp(8f) }
                    setOnClickListener {
                        onOpenApp?.invoke()
                    }
                }
                addView(btnOpenApp)
            }
            addView(actionRow)
        }
        addView(
            expandedContainer,
            LayoutParams(dp(310f), LayoutParams.WRAP_CONTENT, Gravity.CENTER)
        )
    }

    private fun createInfoChip(iconRes: Int?): Pair<LinearLayout, TextView> {
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = ContextCompat.getDrawable(context, R.drawable.bg_island_chip)
            setPadding(dp(6f), dp(3.5f), dp(7f), dp(3.5f))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        if (iconRes != null) {
            val icon = ImageView(context).apply {
                setImageDrawable(ContextCompat.getDrawable(context, iconRes))
                setColorFilter(Color.parseColor("#94A3B8"))
                layoutParams = LinearLayout.LayoutParams(dp(12f), dp(12f)).apply {
                    marginEnd = dp(3.5f)
                }
            }
            layout.addView(icon)
        }
        val text = TextView(context).apply {
            setTextColor(Color.parseColor("#E2E8F0"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sp(10.5f))
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        layout.addView(text)
        return layout to text
    }

    /** WindowManager 的窗口内容也必须适配安全区，不能只夹取左上角。 */
    fun setAvailableBounds(width: Int, height: Int) {
        val safeWidth = width.coerceAtLeast(1)
        val safeHeight = height.coerceAtLeast(1)
        if (availableWidth == safeWidth && availableHeight == safeHeight) return
        availableWidth = safeWidth
        availableHeight = safeHeight
        expandedContainer.layoutParams = expandedContainer.layoutParams.apply {
            this.width = dp(310f).coerceAtMost(safeWidth)
        }
        tvCompactTitle.maxWidth = (safeWidth - dp(110f)).coerceAtLeast(1).coerceAtMost(dp(190f))
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        fun boundedSpec(spec: Int, limit: Int): Int {
            val mode = MeasureSpec.getMode(spec)
            val size = if (mode == MeasureSpec.UNSPECIFIED) limit
            else MeasureSpec.getSize(spec).coerceAtMost(limit)
            return MeasureSpec.makeMeasureSpec(size, if (mode == MeasureSpec.EXACTLY) mode else MeasureSpec.AT_MOST)
        }
        super.onMeasure(boundedSpec(widthMeasureSpec, availableWidth), boundedSpec(heightMeasureSpec, availableHeight))
    }

    fun updateState(state: IslandState) {
        currentState = state
        when (state) {
            is IslandState.Ongoing -> {
                visibility = View.VISIBLE
                dotView.background = createCircleDrawable(Color.parseColor("#10B981"))
                tvCompactTitle.text = state.courseName
                tvCompactInfo.text = "剩${state.remainingMinutes}分"
                tvCompactInfo.setTextColor(Color.parseColor("#34D399"))

                tvBadge.text = "正在上课"
                tvBadge.setTextColor(Color.parseColor("#34D399"))
                tvExpandedTitle.text = state.courseName

                bindLocation(state.classroom)
                tvTime.text = "${state.slotRange} · ${state.startTime}-${state.endTime}"
                bindTeacher(state.teacher)

                progressTrack.visibility = View.VISIBLE
                val progressPercent = (state.progress * 100).roundToInt()
                tvProgressLeft.text = "已进行 $progressPercent%"
                tvProgressRight.text = "${state.endTime} 下课 · 剩 ${state.remainingMinutes} 分钟"
                post { updateProgressWidth(state.progress) }
            }
            is IslandState.Upcoming -> {
                visibility = View.VISIBLE
                dotView.background = createCircleDrawable(Color.parseColor("#F59E0B"))
                tvCompactTitle.text = state.courseName
                tvCompactInfo.text = "${state.minutesUntilStart}分后"
                tvCompactInfo.setTextColor(Color.parseColor("#FBBF24"))

                tvBadge.text = "即将上课"
                tvBadge.setTextColor(Color.parseColor("#FBBF24"))
                tvExpandedTitle.text = state.courseName

                bindLocation(state.classroom)
                tvTime.text = "${state.slotRange} · ${state.startTime} 上课"
                bindTeacher(state.teacher)

                progressTrack.visibility = View.GONE
                tvProgressLeft.text = "还有 ${state.minutesUntilStart} 分钟开始"
                tvProgressRight.text = "${state.startTime} 上课"
            }
            is IslandState.Idle -> {
                dotView.background = createCircleDrawable(Color.parseColor("#38BDF8"))
                tvCompactTitle.text = state.compactText
                tvCompactInfo.text = if (state.nextCourse != null) "准备中" else "完成"
                tvCompactInfo.setTextColor(Color.parseColor("#38BDF8"))

                tvBadge.text = "今日概览"
                tvBadge.setTextColor(Color.parseColor("#38BDF8"))
                tvExpandedTitle.text = state.compactText

                chipLocation.visibility = View.GONE
                chipTeacher.visibility = View.GONE
                tvTime.text = state.subText
                progressTrack.visibility = View.GONE
                tvProgressLeft.text = "今日共 ${state.todayTotalCourses} 门课"
                tvProgressRight.text = "已完成 ${state.finishedCourses} 门"
            }
            is IslandState.None -> {
                visibility = View.GONE
            }
        }
    }

    private fun bindLocation(classroom: String) {
        val trimmed = classroom.trim()
        if (trimmed.isNotBlank()) {
            chipLocation.visibility = View.VISIBLE
            tvLocation.text = trimmed
        } else {
            chipLocation.visibility = View.GONE
        }
    }

    private fun bindTeacher(teacher: String) {
        val trimmed = teacher.trim()
        if (trimmed.isNotBlank()) {
            chipTeacher.visibility = View.VISIBLE
            tvTeacher.text = trimmed
        } else {
            chipTeacher.visibility = View.GONE
        }
    }

    private fun updateProgressWidth(progress: Float) {
        val totalWidth = progressTrack.width
        if (totalWidth > 0) {
            val targetWidth = (totalWidth * progress.coerceIn(0f, 1f)).roundToInt()
            val lp = progressIndicator.layoutParams
            lp.width = targetWidth
            progressIndicator.layoutParams = lp
        }
    }

    fun setExpanded(expanded: Boolean, animate: Boolean = true) {
        if (isExpanded == expanded) return
        isExpanded = expanded
        if (expanded) {
            compactContainer.visibility = View.GONE
            expandedContainer.visibility = View.VISIBLE
            // 展开时刷新进度条宽度
            if (currentState is IslandState.Ongoing) {
                post { updateProgressWidth((currentState as IslandState.Ongoing).progress) }
            }
        } else {
            expandedContainer.visibility = View.GONE
            compactContainer.visibility = View.VISIBLE
        }
        onToggleExpand?.invoke(expanded)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                initialTouchX = ev.rawX
                initialTouchY = ev.rawY
                emittedDragX = 0
                emittedDragY = 0
                isDragging = false
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = abs(ev.rawX - initialTouchX)
                val dy = abs(ev.rawY - initialTouchY)
                if (dx > touchSlop || dy > touchSlop) {
                    isDragging = true
                    return true
                }
            }
        }
        return false
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                initialTouchX = event.rawX
                initialTouchY = event.rawY
                emittedDragX = 0
                emittedDragY = 0
                isDragging = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - initialTouchX
                val dy = event.rawY - initialTouchY
                if (isDragging || abs(dx) > touchSlop || abs(dy) > touchSlop) {
                    isDragging = true
                    val totalX = dx.roundToInt()
                    val totalY = dy.roundToInt()
                    onDragPositionChanged?.invoke(totalX - emittedDragX, totalY - emittedDragY)
                    emittedDragX = totalX
                    emittedDragY = totalY
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (!isDragging) {
                    // 点击切换展开 / 折叠
                    performClick()
                }
                isDragging = false
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                isDragging = false
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        setExpanded(!isExpanded, animate = true)
        return true
    }

    private fun createCircleDrawable(color: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
        }
    }

    private fun createRoundedRectDrawable(color: Int, radius: Float): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(color)
        }
    }
}
