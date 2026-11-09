package com.chen.schedule.widget

import android.annotation.SuppressLint
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ExperimentalGlanceApi
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.chen.schedule.MainActivity
import com.chen.schedule.R
import com.chen.schedule.ui.theme.ScheduleStyle

/** Shared surfaces and shapes based on the weekly timetable widget. */
@SuppressLint("RestrictedApi")
internal object WidgetStyle {
    val FrameRadius = ScheduleStyle.CardRadius
    val FrameInset = 4.dp
    val HeaderRadius = 8.dp
    val EmptyRadius = 4.dp

    val Canvas = ColorProvider(R.color.week_widget_canvas)
    val HeaderSurface = ColorProvider(R.color.week_widget_header)
    val TimeSurface = ColorProvider(R.color.week_widget_time)
    private val EmptySurface = ColorProvider(R.color.week_widget_empty)

    fun emptySurface(style: WidgetVisualStyle): ColorProvider =
        if (style.containerColor == Color.Transparent || style.backgroundBitmap != null) {
            ColorProvider(Color.Transparent)
        } else {
            EmptySurface
        }
}

@Composable
internal fun currentWidgetVisualStyle(): WidgetVisualStyle {
    val context = LocalContext.current
    val nightMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
    return WidgetThemeHelper.resolve(context, nightMode == Configuration.UI_MODE_NIGHT_YES)
}

@OptIn(ExperimentalGlanceApi::class)
@Composable
internal fun WidgetFrame(style: WidgetVisualStyle, content: @Composable () -> Unit) {
    val base = GlanceModifier.fillMaxSize()
        .appWidgetBackground()
        .cornerRadius(WidgetStyle.FrameRadius)
        .clickable(actionStartActivity<MainActivity>())
    val background = when {
        style.containerColor != null -> base.background(style.containerColor)
        style.backgroundBitmap != null -> base
        else -> base.background(WidgetStyle.Canvas)
    }

    Box(modifier = background) {
        style.backgroundBitmap?.let { bitmap ->
            Image(
                provider = ImageProvider(bitmap),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = GlanceModifier.fillMaxSize().cornerRadius(WidgetStyle.FrameRadius)
            )
        }
        content()
    }
}

@Composable
internal fun WidgetDateTile(
    label: String,
    date: String,
    highlighted: Boolean,
    titleColor: ColorProvider,
    subtitleColor: ColorProvider,
    modifier: GlanceModifier
) {
    val onHighlight = GlanceTheme.colors.onPrimary
    Column(
        modifier = modifier
            .cornerRadius(6.dp)
            .background(if (highlighted) GlanceTheme.colors.primary else ColorProvider(Color.Transparent))
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = TextStyle(
                fontSize = 10.sp,
                fontWeight = if (highlighted) FontWeight.Bold else FontWeight.Medium,
                color = if (highlighted) onHighlight else titleColor
            ),
            maxLines = 1
        )
        if (date.isNotBlank()) {
            Text(
                text = date,
                style = TextStyle(
                    fontSize = 7.5.sp,
                    fontWeight = if (highlighted) FontWeight.Medium else FontWeight.Normal,
                    color = if (highlighted) onHighlight else subtitleColor
                ),
                maxLines = 1
            )
        }
    }
}

internal fun GlanceModifier.widgetCourseBackground(color: Long): GlanceModifier =
    cornerRadius(8.dp).background(ColorProvider(Color(color).copy(alpha = 0.15f)))

@Composable
internal fun WidgetCourseRail(color: Long, modifier: GlanceModifier) {
    Box(
        modifier = modifier.width(2.5.dp)
            .cornerRadius(1.5.dp)
            .background(ColorProvider(Color(color)))
    ) {}
}

internal fun widgetCourseTitle(name: String, extraCount: Int): String =
    if (extraCount > 0) "$name+$extraCount" else name
