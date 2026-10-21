package com.chen.schedule.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** 全局形状:统一圆角,卡片 12dp / 控件 8dp / 弹窗 22dp */
val ScheduleShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = ScheduleStyle.CardShape,
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(22.dp)
)
