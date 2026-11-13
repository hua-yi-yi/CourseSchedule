package com.chen.schedule.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.chen.schedule.ui.theme.TimetableWallpaper

internal enum class SettingsSection(
    val route: String,
    val title: String,
    val subtitle: String
) {
    APPEARANCE("settings/appearance", "外观与壁纸", "主题、课表背景与预览"),
    REMINDERS("settings/reminders", "上课提醒", "课前通知、提前时间与权限"),
    WIDGETS("settings/widgets", "桌面小组件", "添加小组件、背景与文字对比度"),
    DATA("settings/data", "数据管理", "备份、恢复与日历同步"),
    UPDATES("settings/updates", "版本与更新", "检查更新与下载线路"),
    LOGS("settings/logs", "日志与诊断", "查看运行日志、导入日志与导出")
}

@Composable
internal fun SettingsScreen(
    onOpenSection: (SettingsSection) -> Unit,
    onNavigateToScheduleConfig: () -> Unit,
    onNavigateBack: () -> Unit,
    updateAvailable: Boolean
) {
    SettingsPage(title = "设置", onNavigateBack = onNavigateBack) {
        SettingsGroup {
            SettingsItem(
                title = "学期与作息",
                subtitle = "学期、作息方案与节次时间",
                onClick = onNavigateToScheduleConfig
            )
            SettingsSection.entries.takeWhile { it != SettingsSection.DATA }.forEach { section ->
                GroupDivider()
                SettingsItem(section.title, section.subtitle) { onOpenSection(section) }
            }
        }
        Spacer(Modifier.height(12.dp))
        SettingsGroup {
            SettingsSection.entries.dropWhile { it != SettingsSection.DATA }.forEachIndexed { index, section ->
                if (index > 0) GroupDivider()
                val subtitle = if (section == SettingsSection.UPDATES && updateAvailable) {
                    "有新版本可用"
                } else {
                    section.subtitle
                }
                SettingsItem(section.title, subtitle) { onOpenSection(section) }
            }
        }
        Spacer(Modifier.height(12.dp))
        SupportSettings()
    }
}

@Composable
internal fun SettingsSectionScreen(
    section: SettingsSection,
    viewModel: SettingsViewModel,
    wallpaper: TimetableWallpaper?,
    onNavigateBack: () -> Unit
) {
    SettingsPage(title = section.title, onNavigateBack = onNavigateBack) {
        when (section) {
            SettingsSection.APPEARANCE -> AppearanceSettings(viewModel, wallpaper)
            SettingsSection.REMINDERS -> ReminderSettings(viewModel)
            SettingsSection.WIDGETS -> WidgetSettings(viewModel)
            SettingsSection.DATA -> DataSettings(viewModel)
            SettingsSection.UPDATES -> UpdateSettings(viewModel)
            SettingsSection.LOGS -> LogSettings(viewModel)
        }
    }
}

@Composable
private fun SupportSettings() {
    val context = LocalContext.current
    var showDonationDialog by rememberSaveable { mutableStateOf(false) }
    SettingsGroup(title = "支持与交流") {
        SettingsItem(
            title = "打赏作者",
            subtitle = "支持开发者的持续维护",
            onClick = { showDonationDialog = true }
        )
        GroupDivider()
        SettingsItem(
            title = "开发者 QQ",
            subtitle = "3180635398 · 点击复制",
            onClick = {
                val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return@SettingsItem
                clipboard.setPrimaryClip(ClipData.newPlainText("QQ", "3180635398"))
                Toast.makeText(context, "QQ 号已复制到剪贴板", Toast.LENGTH_SHORT).show()
            }
        )
    }
    if (showDonationDialog) DonationDialog { showDonationDialog = false }
}
