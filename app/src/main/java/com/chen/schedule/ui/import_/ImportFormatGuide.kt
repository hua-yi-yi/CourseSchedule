package com.chen.schedule.ui.import_

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun CodeBlock(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.9f))
            .horizontalScroll(rememberScrollState())
            .padding(12.dp)
    ) {
        Text(
            text = text,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            lineHeight = 16.sp,
            color = MaterialTheme.colorScheme.inverseOnSurface
        )
    }
}

@Composable
internal fun JsonFieldTable() {
    Column {
        FieldRow("name", "课程名称（必填）", "高等数学")
        FieldRow("teacher", "教师姓名", "张老师")
        FieldRow("classroom", "教室/地点", "教学楼101")
        FieldRow("dayOfWeek", "星期几 (1=周一)", "1")
        FieldRow("startSlot", "开始节次", "1")
        FieldRow("endSlot", "结束节次", "2")
        FieldRow("startWeek", "起始周", "1")
        FieldRow("endWeek", "结束周", "16")
        FieldRow("weekType", "all/odd/even", "all")
        FieldRow("color", "颜色值（可省略）", "0xFF4CAF50")
        FieldRow("note", "备注（可省略）", "")
    }
}

@Composable
internal fun CsvFieldTable() {
    Column {
        FieldRow("name", "课程名称（必填）", "高等数学")
        FieldRow("teacher", "教师姓名", "张老师")
        FieldRow("classroom", "教室", "教学楼101")
        FieldRow("dayOfWeek", "星期: 1~7", "1")
        FieldRow("startSlot", "开始节次", "1")
        FieldRow("endSlot", "结束节次", "2")
        FieldRow("startWeek", "起始周", "1")
        FieldRow("endWeek", "结束周", "16")
        FieldRow("weekType", "all/odd/even", "all")
        FieldRow("note", "备注", "")
    }
}

@Composable
internal fun FieldRow(field: String, desc: String, example: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp)
    ) {
        Text(
            field,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(90.dp)
        )
        Text(
            desc,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            "例: $example",
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.width(90.dp)
        )
    }
}
