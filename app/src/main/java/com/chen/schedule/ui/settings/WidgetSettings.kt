package com.chen.schedule.ui.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import com.chen.schedule.widget.Today3x3Widget
import com.chen.schedule.widget.Today3x3WidgetReceiver
import com.chen.schedule.widget.TodayWidget
import com.chen.schedule.widget.TodayWidgetReceiver
import com.chen.schedule.widget.WeekWidget
import com.chen.schedule.widget.WeekWidgetReceiver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun WidgetSettings(viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showGuide by rememberSaveable { mutableStateOf(false) }

    fun requestPin(receiver: Class<out GlanceAppWidgetReceiver>, widget: GlanceAppWidget) {
        scope.launch {
            try {
                val manager = GlanceAppWidgetManager(context)
                if (manager.requestPinGlanceAppWidget(receiver, widget)) {
                    Toast.makeText(context, "请在桌面弹窗中确认添加", Toast.LENGTH_LONG).show()
                } else {
                    showGuide = true
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                showGuide = true
            }
        }
    }

    SettingsGroup(title = "添加小组件") {
        SettingsItem(title = "今日课程看板 (3×3)", subtitle = "方形看板，显示上课时间和地点") {
            requestPin(Today3x3WidgetReceiver::class.java, Today3x3Widget())
        }
        GroupDivider()
        SettingsItem(title = "今日课程 (4×1)", subtitle = "横条布局，显示今日课程") {
            requestPin(TodayWidgetReceiver::class.java, TodayWidget())
        }
        GroupDivider()
        SettingsItem(title = "周课表 (4×4)", subtitle = "整周网格，概览周一至周日课程") {
            requestPin(WeekWidgetReceiver::class.java, WeekWidget())
        }
        GroupDivider()
        SettingsItem(title = "添加指引与帮助", subtitle = "查看手动添加教程和权限设置") {
            showGuide = true
        }
    }
    Spacer(Modifier.height(12.dp))
    WidgetAppearanceSettings(viewModel)
    if (showGuide) WidgetGuideDialog { showGuide = false }
}
