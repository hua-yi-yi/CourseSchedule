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
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chen.schedule.domain.model.Course
import com.chen.schedule.domain.model.TimeSlot
import kotlinx.coroutines.delay

/**
 * 应用内嵌入式胶囊灵动岛组件。
 * 在主界面顶栏下方以流体胶囊形式展示当前或临近课程状态，支持点开展开卡片。
 */
@Composable
fun InAppCapsuleIsland(
    courses: List<Course>,
    timeSlots: List<TimeSlot>,
    currentWeek: Int,
    leadMinutes: Int = 30,
    modifier: Modifier = Modifier,
    onCourseClick: ((Course) -> Unit)? = null
) {
    var nowMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    var isExpanded by remember { mutableStateOf(false) }
    var dismissedForSession by remember { mutableStateOf(false) }

    // 每 15 秒更新一次当前时刻以驱动倒计时与进度条刷新
    LaunchedEffect(Unit) {
        while (true) {
            delay(15_000L)
            nowMillis = System.currentTimeMillis()
        }
    }

    val todayDow = remember(nowMillis) {
        java.time.LocalDate.now().dayOfWeek.value
    }

    val islandState = remember(courses, timeSlots, currentWeek, todayDow, nowMillis, leadMinutes) {
        IslandStateCalculator.calculate(
            nowMillis = nowMillis,
            slots = timeSlots,
            courses = courses,
            currentWeek = currentWeek,
            todayDayOfWeek = todayDow,
            leadMinutes = leadMinutes
        )
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
                .padding(horizontal = 12.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            when (islandState) {
                is IslandState.Ongoing -> {
                    OngoingIslandCard(
                        state = islandState,
                        isExpanded = isExpanded,
                        onToggleExpand = { isExpanded = !isExpanded },
                        onDismiss = { dismissedForSession = true },
                        onClickCourse = { onCourseClick?.invoke(islandState.course) }
                    )
                }
                is IslandState.Upcoming -> {
                    UpcomingIslandCard(
                        state = islandState,
                        isExpanded = isExpanded,
                        onToggleExpand = { isExpanded = !isExpanded },
                        onDismiss = { dismissedForSession = true },
                        onClickCourse = { onCourseClick?.invoke(islandState.course) }
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
    Card(
        shape = RoundedCornerShape(if (isExpanded) 18.dp else 24.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xF20F172A)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0x3338BDF8), RoundedCornerShape(if (isExpanded) 18.dp else 24.dp))
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
                        .background(Color(0xFF10B981))
                )
                Spacer(Modifier.width(8.dp))

                // 正在上课胶囊标签
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0x3310B981)
                ) {
                    Text(
                        "正在上课",
                        color = Color(0xFF34D399),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Spacer(Modifier.width(8.dp))

                Text(
                    state.courseName,
                    color = Color.White,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Text(
                    "剩 ${state.remainingMinutes} 分钟",
                    color = Color(0xFF38BDF8),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )

                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = Color(0xFF94A3B8),
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
                            Icon(Icons.Default.Place, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(2.dp))
                            Text(state.classroom, color = Color(0xFFE2E8F0), fontSize = 11.5.sp)
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Schedule, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(2.dp))
                        Text("${state.slotRange} (${state.startTime}-${state.endTime})", color = Color(0xFFE2E8F0), fontSize = 11.5.sp)
                    }

                    if (state.teacher.isNotBlank()) {
                        Text(state.teacher, color = Color(0xFF94A3B8), fontSize = 11.5.sp)
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
                    color = Color(0xFF38BDF8),
                    trackColor = Color(0x33FFFFFF)
                )

                Spacer(Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${state.endTime} 下课",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp
                    )

                    Row {
                        Text(
                            "查看课程详情",
                            color = Color(0xFF38BDF8),
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
                            color = Color(0xFF64748B),
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
    Card(
        shape = RoundedCornerShape(if (isExpanded) 18.dp else 24.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xF20F172A)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0x33F59E0B), RoundedCornerShape(if (isExpanded) 18.dp else 24.dp))
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
                        .background(Color(0xFFF59E0B))
                )
                Spacer(Modifier.width(8.dp))

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0x33F59E0B)
                ) {
                    Text(
                        "即将上课",
                        color = Color(0xFFFBBF24),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Spacer(Modifier.width(8.dp))

                Text(
                    state.courseName,
                    color = Color.White,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Text(
                    "${state.minutesUntilStart} 分钟后",
                    color = Color(0xFFFBBF24),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )

                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = Color(0xFF94A3B8),
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
                            Icon(Icons.Default.Place, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(2.dp))
                            Text(state.classroom, color = Color(0xFFE2E8F0), fontSize = 11.5.sp)
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Schedule, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(2.dp))
                        Text("${state.slotRange} (${state.startTime} 开始)", color = Color(0xFFE2E8F0), fontSize = 11.5.sp)
                    }

                    if (state.teacher.isNotBlank()) {
                        Text(state.teacher, color = Color(0xFF94A3B8), fontSize = 11.5.sp)
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
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp
                    )

                    Row {
                        Text(
                            "查看课程详情",
                            color = Color(0xFFFBBF24),
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
                            color = Color(0xFF64748B),
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
