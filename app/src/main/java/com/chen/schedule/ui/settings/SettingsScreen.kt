package com.chen.schedule.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.chen.schedule.ui.theme.TimetableWallpaper

internal enum class SettingsSection(
    val route: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector
) {
    APPEARANCE("settings/appearance", "外观与壁纸", "主题、课表背景与预览", Icons.Outlined.Palette),
    REMINDERS("settings/reminders", "上课提醒", "课前通知、提前时间与权限", Icons.Outlined.Notifications),
    WIDGETS("settings/widgets", "桌面小组件", "添加小组件、背景与文字对比度", Icons.Outlined.Dashboard),
    DATA("settings/data", "数据管理", "备份、恢复与日历同步", Icons.Outlined.CloudUpload),
    UPDATES("settings/updates", "版本与更新", "检查更新与下载线路", Icons.Outlined.SystemUpdate),
    LOGS("settings/logs", "日志与诊断", "查看运行日志、导入日志与导出", Icons.Outlined.Description),
    SUPPORT("settings/support", "支持与交流", "联系开发者、打赏作者", Icons.Outlined.Info)
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
            SettingsMenuItem(
                title = "学期与作息",
                subtitle = "学期、作息方案与节次时间",
                icon = Icons.Outlined.CalendarMonth,
                onClick = onNavigateToScheduleConfig
            )
            SettingsSection.entries.takeWhile { it != SettingsSection.DATA }.forEach { section ->
                GroupDivider()
                SettingsMenuItem(section.title, section.subtitle, section.icon) { onOpenSection(section) }
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
                SettingsMenuItem(section.title, subtitle, section.icon) { onOpenSection(section) }
            }
        }
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
            SettingsSection.SUPPORT -> SupportSettings()
        }
    }
}

@Composable
private fun SettingsMenuItem(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) {
    SettingsItem(
        title = title,
        subtitle = subtitle,
        leading = { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        trailing = {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        onClick = onClick
    )
}

@Composable
internal fun SupportSettings() {
    val context = LocalContext.current
    var showDonationDialog by rememberSaveable { mutableStateOf(false) }
    SettingsGroup {
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
