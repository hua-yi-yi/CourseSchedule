package com.chen.schedule.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chen.schedule.domain.model.CoursePalette
import com.chen.schedule.ui.theme.ScheduleStyle
import com.chen.schedule.ui.theme.TimetableWallpaper
import com.chen.schedule.ui.theme.TimetableWallpaperImage
import com.chen.schedule.ui.timetable.courseCardColors
import com.chen.schedule.ui.timetable.timetableControls
import com.chen.schedule.ui.timetable.timetableGrid

@Composable
internal fun WallpaperPreview(wallpaper: TimetableWallpaper, dim: Float, gridOpacity: Float) {
    val colors = MaterialTheme.colorScheme
    val shape = ScheduleStyle.CardShape
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(0.5.dp, colors.outlineVariant, shape)
            .clearAndSetSemantics { contentDescription = "课表壁纸预览" }
    ) {
        TimetableWallpaperImage(wallpaper, dim, Modifier.matchParentSize())
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().background(colors.timetableControls, shape)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("课表预览", style = MaterialTheme.typography.labelMedium, color = colors.onSurface)
                Text("第 1 周", style = MaterialTheme.typography.labelMedium, color = colors.primary)
            }
            Column(
                modifier = Modifier.fillMaxWidth().clip(shape).background(colors.timetableGrid(gridOpacity))
                    .border(0.5.dp, colors.outlineVariant, shape)
            ) {
                Row(Modifier.fillMaxWidth().background(colors.timetableControls)) {
                    listOf("周一", "周二", "周三").forEach { day ->
                        Box(Modifier.weight(1f).padding(4.dp), contentAlignment = Alignment.Center) {
                            Text(day, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    PreviewCourse("高等数学", "教学楼 201", Color(CoursePalette.colors[1]), Modifier.weight(1f))
                    Box(Modifier.weight(1f))
                    PreviewCourse("大学英语", "教学楼 305", Color(CoursePalette.colors[2]), Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun PreviewCourse(name: String, classroom: String, accent: Color, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val course = colors.courseCardColors(accent)
    val shape = ScheduleStyle.CardShape
    Column(
        modifier = modifier.background(course.container, shape)
            .border(0.5.dp, course.accent.copy(alpha = 0.5f), shape).padding(6.dp)
    ) {
        Text(
            name,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = colors.onSurface
        )
        Text(classroom, style = MaterialTheme.typography.labelSmall, color = course.accentText)
    }
}
