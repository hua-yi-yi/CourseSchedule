package com.chen.schedule.widget

import com.chen.schedule.ui.theme.ScheduleStyle
import com.chen.schedule.ui.theme.Typography

/** Shared with the app; grid labels remain smaller to fit seven timetable columns. */
internal object WidgetStyle {
    val CardRadius = ScheduleStyle.CardRadius
    val ContentPadding = ScheduleStyle.PagePadding
    val TitleSize = Typography.titleSmall.fontSize
    val DetailSize = Typography.bodySmall.fontSize
    const val CourseTintAlpha = 0.15f
}
