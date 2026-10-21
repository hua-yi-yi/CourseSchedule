package com.chen.schedule.widget

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.ExperimentalGlanceApi
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.chen.schedule.MainActivity

/**
 * 3×3 今日课程看板小组件：
 * 专为方形桌面卡片打造，与主页及应用设计系统保持完全一致（全局统一圆角、晴空蓝主题配色、课程柔和彩色底卡与胶囊徽标）。
 */
class Today3x3Widget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = TodayWidgetDataLoader.load(context)
        provideContent {
            ScheduleGlanceTheme {
                Today3x3WidgetContent(data)
            }
        }
    }
}

class Today3x3WidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = Today3x3Widget()
}

@OptIn(ExperimentalGlanceApi::class)
@Composable
private fun Today3x3WidgetContent(data: WidgetData) {
    val context = LocalContext.current
    val isDarkTheme = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
        Configuration.UI_MODE_NIGHT_YES
    val visualStyle = WidgetThemeHelper.resolve(context, isDarkTheme)

    val titleColor = visualStyle.titleColor ?: GlanceTheme.colors.onSurface
    val subtitleColor = visualStyle.subtitleColor ?: GlanceTheme.colors.onSurfaceVariant
    val badgeBg = visualStyle.badgeBg ?: (if (data.courses.isNotEmpty()) GlanceTheme.colors.primaryContainer else GlanceTheme.colors.surface)
    val badgeTextColor = visualStyle.badgeTextColor ?: (if (data.courses.isNotEmpty()) GlanceTheme.colors.onPrimaryContainer else GlanceTheme.colors.onSurfaceVariant)
    val emptyCardBg = visualStyle.itemCardBg ?: GlanceTheme.colors.surface

    val baseModifier = GlanceModifier
        .fillMaxSize()
        .appWidgetBackground()
        .cornerRadius(WidgetStyle.CardRadius)
        .clickable(actionStartActivity<MainActivity>())

    val backgroundModifier = when {
        visualStyle.containerColor != null -> baseModifier.background(visualStyle.containerColor)
        visualStyle.backgroundBitmap != null -> baseModifier
        else -> baseModifier.background(GlanceTheme.colors.background)
    }

    Box(modifier = backgroundModifier) {
        if (visualStyle.backgroundBitmap != null) {
            Image(
                provider = ImageProvider(visualStyle.backgroundBitmap),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = GlanceModifier.fillMaxSize().cornerRadius(WidgetStyle.CardRadius)
            )
        }

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(WidgetStyle.ContentPadding)
        ) {
            // 顶部信息区：日期 · 星期与学期周次，右侧圆角胶囊徽标
            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .clickable(actionStartActivity<MainActivity>()),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Text(
                        text = "${data.dateLabel} · ${data.dayOfWeekLabel}",
                        style = TextStyle(
                            fontSize = WidgetStyle.TitleSize,
                            fontWeight = FontWeight.Bold,
                            color = titleColor
                        )
                    )
                    Text(
                        text = if (data.currentWeek > 0) "${data.semesterName} · 第${data.currentWeek}周" else data.semesterName,
                        style = TextStyle(
                            fontSize = WidgetStyle.DetailSize,
                            color = subtitleColor
                        ),
                        maxLines = 1
                    )
                }

                // 右侧圆角胶囊徽标 (与主页 StatusBadge 视觉对齐)
                Box(
                    modifier = GlanceModifier
                        .cornerRadius(10.dp)
                        .background(badgeBg)
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (data.courses.isNotEmpty()) "共 ${data.courses.size} 门课" else "无课",
                        style = TextStyle(
                            fontSize = WidgetStyle.DetailSize,
                            fontWeight = FontWeight.Medium,
                            color = badgeTextColor
                        )
                    )
                }
            }

            Spacer(modifier = GlanceModifier.height(8.dp))

            if (data.courses.isEmpty()) {
                Box(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .padding(top = 16.dp)
                        .cornerRadius(WidgetStyle.CardRadius)
                        .background(emptyCardBg)
                        .clickable(actionStartActivity<MainActivity>())
                        .padding(horizontal = 16.dp, vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "今日无课",
                            style = TextStyle(
                                fontSize = WidgetStyle.TitleSize,
                                fontWeight = FontWeight.Bold,
                                color = titleColor
                            )
                        )
                        Spacer(modifier = GlanceModifier.height(4.dp))
                        Text(
                            text = "享受属于自己的自由时间吧",
                            style = TextStyle(
                                fontSize = WidgetStyle.DetailSize,
                                color = subtitleColor
                            )
                        )
                    }
                }
            } else {
                LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                    items(data.courses) { course ->
                        WidgetCourseCard(course, visualStyle, maxLines = 2)
                    }
                }
            }
        }
    }
}
