package com.chen.schedule.island

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chen.schedule.domain.model.Course
import com.chen.schedule.ui.schedule.StatusAmber
import com.chen.schedule.ui.schedule.StatusGreen
import com.chen.schedule.ui.theme.ScheduleStyle
import com.chen.schedule.ui.theme.withTextContrast
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/**
 * 应用内嵌入式胶囊灵动岛组件。
 * 在主界面顶栏下方以流体胶囊形式展示当前或临近课程状态，支持点开展开卡片。
 */
@Composable
fun InAppCapsuleIsland(
    modifier: Modifier = Modifier,
    onCourseClick: ((Course) -> Unit)? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val prefs = remember(context) { IslandPrefs.init(context) }
    val config by IslandPrefs.state.collectAsState()
    var islandState by remember { mutableStateOf<IslandState>(IslandState.None) }
    var isExpanded by remember { mutableStateOf(false) }
    var dismissedForSession by remember { mutableStateOf(false) }

    // 与系统通知、悬浮胶囊共用真实日期状态，前台恢复时立即刷新。
    LaunchedEffect(lifecycleOwner, prefs, config.leadMinutes) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (isActive) {
                islandState = try {
                    withContext(Dispatchers.IO) { IslandStateRepository.load(context) }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    IslandState.None
                }
                delay(15_000L)
            }
        }
    }

    val activityKey = when (val state = islandState) {
        is IslandState.Ongoing -> "${state.course.id}:${state.startMillis}"
        is IslandState.Upcoming -> "${state.course.id}:${state.startMillis}"
        else -> null
    }
    LaunchedEffect(activityKey) {
        dismissedForSession = false
        isExpanded = false
    }
    val isVisible = !dismissedForSession && (islandState is IslandState.Ongoing || islandState is IslandState.Upcoming)

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            when (val state = islandState) {
                is IslandState.Ongoing -> {
                    OngoingIslandCard(
                        state = state,
                        isExpanded = isExpanded,
                        onToggleExpand = { isExpanded = !isExpanded },
                        onDismiss = { dismissedForSession = true },
                        onClickCourse = { onCourseClick?.invoke(state.course) }
                    )
                }
                is IslandState.Upcoming -> {
                    UpcomingIslandCard(
                        state = state,
                        isExpanded = isExpanded,
                        onToggleExpand = { isExpanded = !isExpanded },
                        onDismiss = { dismissedForSession = true },
                        onClickCourse = { onCourseClick?.invoke(state.course) }
                    )
                }
                else -> {}
            }
        }
    }
}

@Composable
private fun OngoingIslandCard(
    state: IslandState.Ongoing,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onDismiss: () -> Unit,
    onClickCourse: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val statusContainer = StatusGreen.copy(alpha = 0.14f).compositeOver(colors.surfaceContainerLow)
    val statusText = StatusGreen.withTextContrast(statusContainer, colors.onSurface)
    Card(
        shape = ScheduleStyle.CardShape,
        colors = CardDefaults.cardColors(
            containerColor = colors.surfaceContainerLow
        ),
        modifier = Modifier
            .fillMaxWidth()
            .border(0.5.dp, colors.outlineVariant, ScheduleStyle.CardShape)
            .clickable(onClick = onToggleExpand)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            // 顶层微胶囊行
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 呼吸绿灯指示器
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(statusText)
                )
                Spacer(Modifier.width(8.dp))

                // 正在上课胶囊标签
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = statusContainer
                ) {
                    Text(
                        "正在上课",
                        color = statusText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Spacer(Modifier.width(8.dp))

                Text(
                    state.courseName,
                    color = colors.onSurface,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Text(
                    "剩 ${state.remainingMinutes} 分钟",
                    color = colors.primary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )

                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier
                        .size(20.dp)
                        .padding(start = 4.dp)
                )
            }

            // 展开状态下的详细信息与进度条
            if (isExpanded) {
                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (state.classroom.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Place, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(2.dp))
                            Text(state.classroom, color = colors.onSurface, fontSize = 11.5.sp)
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Schedule, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(2.dp))
                        Text("${state.slotRange} (${state.startTime}-${state.endTime})", color = colors.onSurface, fontSize = 11.5.sp)
                    }

                    if (state.teacher.isNotBlank()) {
                        Text(state.teacher, color = colors.onSurfaceVariant, fontSize = 11.5.sp)
                    }
                }

                Spacer(Modifier.height(8.dp))

                // 上课进度条
                LinearProgressIndicator(
                    progress = { state.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = colors.primary,
                    trackColor = colors.surfaceContainerHighest
                )

                Spacer(Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${state.endTime} 下课",
                        color = colors.onSurfaceVariant,
                        fontSize = 11.sp
                    )

                    Row {
                        Text(
                            "查看课程详情",
                            color = colors.primary,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable(onClick = onClickCourse)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "暂时隐藏",
                            color = colors.onSurfaceVariant,
                            fontSize = 11.5.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable(onClick = onDismiss)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UpcomingIslandCard(
    state: IslandState.Upcoming,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onDismiss: () -> Unit,
    onClickCourse: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val statusContainer = StatusAmber.copy(alpha = 0.14f).compositeOver(colors.surfaceContainerLow)
    val statusText = StatusAmber.withTextContrast(statusContainer, colors.onSurface)
    Card(
        shape = ScheduleStyle.CardShape,
        colors = CardDefaults.cardColors(
            containerColor = colors.surfaceContainerLow
        ),
        modifier = Modifier
            .fillMaxWidth()
            .border(0.5.dp, colors.outlineVariant, ScheduleStyle.CardShape)
            .clickable(onClick = onToggleExpand)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(statusText)
                )
                Spacer(Modifier.width(8.dp))

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = statusContainer
                ) {
                    Text(
                        "即将上课",
                        color = statusText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Spacer(Modifier.width(8.dp))

                Text(
                    state.courseName,
                    color = colors.onSurface,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Text(
                    "${state.minutesUntilStart} 分钟后",
                    color = colors.primary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )

                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier
                        .size(20.dp)
                        .padding(start = 4.dp)
                )
            }

            if (isExpanded) {
                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (state.classroom.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Place, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(2.dp))
                            Text(state.classroom, color = colors.onSurface, fontSize = 11.5.sp)
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Schedule, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(2.dp))
                        Text("${state.slotRange} (${state.startTime} 开始)", color = colors.onSurface, fontSize = 11.5.sp)
                    }

                    if (state.teacher.isNotBlank()) {
                        Text(state.teacher, color = colors.onSurfaceVariant, fontSize = 11.5.sp)
                    }
                }

                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${state.startTime} 开始上课",
                        color = colors.onSurfaceVariant,
                        fontSize = 11.sp
                    )

                    Row {
                        Text(
                            "查看课程详情",
                            color = colors.primary,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable(onClick = onClickCourse)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "暂时隐藏",
                            color = colors.onSurfaceVariant,
                            fontSize = 11.5.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable(onClick = onDismiss)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}
