package com.chen.schedule.ui.settings

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.chen.schedule.ui.theme.BackgroundPreset
import com.chen.schedule.ui.theme.ThemePrefs
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun AppearanceSettings(viewModel: SettingsViewModel) {
    // ===== 外观与背景 =====
    val themeConfig by viewModel.themeConfig.collectAsState()
    SettingsGroup(title = "外观与背景") {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text(
                "主题外观",
                style = MaterialTheme.typography.bodySmall,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    ThemePrefs.THEME_MODE_LIGHT to "浅色模式",
                    ThemePrefs.THEME_MODE_DARK to "深色模式",
                    ThemePrefs.THEME_MODE_SYSTEM to "跟随系统"
                ).forEach { (mode, label) ->
                    FilterChip(
                        selected = themeConfig.themeMode == mode,
                        onClick = { viewModel.updateThemeMode(mode) },
                        label = { Text(label, fontSize = 12.sp) }
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            Text(
                "背景底色",
                style = MaterialTheme.typography.bodySmall,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                BackgroundPreset.entries.forEach { preset ->
                    FilterChip(
                        selected = themeConfig.backgroundPreset == preset,
                        onClick = { viewModel.updateBackgroundPreset(preset) },
                        label = { Text(preset.label, fontSize = 12.sp) }
                    )
                }
            }
        }
    }

    Spacer(Modifier.height(16.dp))
}
