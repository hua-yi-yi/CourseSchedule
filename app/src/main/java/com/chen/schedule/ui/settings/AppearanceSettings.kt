package com.chen.schedule.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chen.schedule.ui.theme.BackgroundPreset
import com.chen.schedule.ui.theme.ThemePrefs
import com.chen.schedule.ui.theme.WidgetBackgroundMode
import com.chen.schedule.ui.theme.WidgetTextContrast
import com.chen.schedule.util.BackgroundFileManager

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AppearanceSettings(viewModel: SettingsViewModel) {
    val themeConfig by viewModel.themeConfig.collectAsState()

    // 相册选择器：应用背景
    val appBgPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.setCustomBackground(uri)
        }
    }

    // 相册选择器：小组件独立壁纸
    val widgetBgPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.setWidgetCustomBackground(uri)
        }
    }

    // ===== 1. 应用外观与背景 =====
    SettingsGroup(title = "应用外观与背景") {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text(
                "主题模式",
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

            Spacer(Modifier.height(14.dp))
            Text(
                "课表背景类型",
                style = MaterialTheme.typography.bodySmall,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(6.dp))

            val hasCustomBg = themeConfig.customBackgroundPath != null
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = !hasCustomBg,
                    onClick = {
                        if (hasCustomBg) viewModel.clearCustomBackground()
                    },
                    label = { Text("预设底色", fontSize = 12.sp) }
                )
                FilterChip(
                    selected = hasCustomBg,
                    onClick = {
                        if (!hasCustomBg) {
                            appBgPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                    },
                    label = { Text(if (hasCustomBg) "自定义相册壁纸" else "选择相册壁纸", fontSize = 12.sp) }
                )
            }

            // 预设底色列表
            AnimatedVisibility(visible = !hasCustomBg) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    Text(
                        "预设底色调",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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

            // 自定义壁纸配置
            AnimatedVisibility(visible = hasCustomBg) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    val appBitmap = remember(themeConfig.customBackgroundPath) {
                        BackgroundFileManager.loadBitmap(themeConfig.customBackgroundPath, 400, 240)?.asImageBitmap()
                    }

                    // 缩略图与操作
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (appBitmap != null) {
                                Box(
                                    modifier = Modifier
                                        .size(width = 80.dp, height = 70.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                                ) {
                                    Image(
                                        bitmap = appBitmap,
                                        contentDescription = "当前壁纸预览",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    if (themeConfig.backgroundDim > 0f) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(Color.Black.copy(alpha = themeConfig.backgroundDim))
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("当前壁纸生效中", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Text("已安全保存至应用内部存储", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(6.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = {
                                            appBgPickerLauncher.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        }
                                    ) {
                                        Text("更换", fontSize = 12.sp)
                                    }
                                    TextButton(onClick = { viewModel.clearCustomBackground() }) {
                                        Text("移除壁纸", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    // 壁纸遮罩暗度
                    Text(
                        "壁纸遮罩暗度 (压暗壁纸以保证课程文字清晰)",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            0.0f to "0% 无遮罩",
                            0.15f to "15% 极轻",
                            0.25f to "25% 推荐",
                            0.40f to "40% 适中",
                            0.60f to "60% 深度"
                        ).forEach { (dim, label) ->
                            FilterChip(
                                selected = kotlin.math.abs(themeConfig.backgroundDim - dim) < 0.05f,
                                onClick = { viewModel.updateBackgroundDim(dim) },
                                label = { Text(label, fontSize = 11.5.sp) }
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    // 课表卡片透明度
                    Text(
                        "课表底板透明度 (透出背景壁纸)",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            1.0f to "100% 纯色不透",
                            0.85f to "85% 轻微透出",
                            0.65f to "65% 优雅半透",
                            0.45f to "45% 通透晶莹"
                        ).forEach { (alpha, label) ->
                            FilterChip(
                                selected = kotlin.math.abs(themeConfig.cardAlpha - alpha) < 0.05f,
                                onClick = { viewModel.updateCardAlpha(alpha) },
                                label = { Text(label, fontSize = 11.5.sp) }
                            )
                        }
                    }
                }
            }
        }
    }

    Spacer(Modifier.height(16.dp))

    // ===== 2. 桌面小组件背景设置 =====
    SettingsGroup(title = "桌面小组件背景风格") {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text(
                "小组件背景样式",
                style = MaterialTheme.typography.bodySmall,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(6.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                WidgetBackgroundMode.entries.forEach { mode ->
                    FilterChip(
                        selected = themeConfig.widgetBackgroundMode == mode,
                        onClick = {
                            if (mode == WidgetBackgroundMode.CUSTOM_IMAGE && themeConfig.widgetCustomBgPath == null) {
                                widgetBgPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            } else {
                                viewModel.updateWidgetBackgroundMode(mode)
                            }
                        },
                        label = { Text(mode.label, fontSize = 12.sp) }
                    )
                }
            }

            // 若选择「独立小组件壁纸」
            AnimatedVisibility(visible = themeConfig.widgetBackgroundMode == WidgetBackgroundMode.CUSTOM_IMAGE) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    val widgetBitmap = remember(themeConfig.widgetCustomBgPath) {
                        BackgroundFileManager.loadWidgetBitmap(themeConfig.widgetCustomBgPath, 300, 300)?.asImageBitmap()
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (widgetBitmap != null) {
                                Box(
                                    modifier = Modifier
                                        .size(60.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                                ) {
                                    Image(
                                        bitmap = widgetBitmap,
                                        contentDescription = "小组件壁纸预览",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("小组件专属壁纸", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Text("专门针对桌面分辨率进行轻量优化", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(4.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = {
                                            widgetBgPickerLauncher.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        }
                                    ) { Text("更换壁纸", fontSize = 11.5.sp) }
                                    TextButton(onClick = { viewModel.clearWidgetCustomBackground() }) {
                                        Text("移除", fontSize = 11.5.sp, color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 小组件底板不透明度 (在半透或纯色模式生效)
            val showOpacitySlider = themeConfig.widgetBackgroundMode in listOf(
                WidgetBackgroundMode.TRANSLUCENT_LIGHT,
                WidgetBackgroundMode.TRANSLUCENT_DARK,
                WidgetBackgroundMode.SOLID_WHITE,
                WidgetBackgroundMode.SOLID_BLACK
            )
            AnimatedVisibility(visible = showOpacitySlider) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    Text(
                        "底板不透明度",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            1.0f to "100% 纯色",
                            0.80f to "80% 轻透",
                            0.60f to "60% 半透",
                            0.40f to "40% 薄透"
                        ).forEach { (op, label) ->
                            FilterChip(
                                selected = kotlin.math.abs(themeConfig.widgetOpacity - op) < 0.05f,
                                onClick = { viewModel.updateWidgetOpacity(op) },
                                label = { Text(label, fontSize = 11.5.sp) }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // 小组件文字与图标对比度
            Text(
                "文字与图标对比度 (适配手机桌面壁纸)",
                style = MaterialTheme.typography.bodySmall,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(6.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                WidgetTextContrast.entries.forEach { contrast ->
                    FilterChip(
                        selected = themeConfig.widgetTextContrast == contrast,
                        onClick = { viewModel.updateWidgetTextContrast(contrast) },
                        label = { Text(contrast.label, fontSize = 12.sp) }
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                "✦ 设置后桌面今日课程 (4×1)、今日看板 (3×3) 及周课表 (4×4) 将实时同步刷新",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    Spacer(Modifier.height(16.dp))
}
