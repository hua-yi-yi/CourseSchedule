package com.chen.schedule.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent

/** 3×3 今日课程看板。 */
class Today3x3Widget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = TodayWidgetDataLoader.load(context)
        provideContent {
            ScheduleGlanceTheme {
                TodayWidgetContent(data)
            }
        }
    }
}

class Today3x3WidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = Today3x3Widget()
}
