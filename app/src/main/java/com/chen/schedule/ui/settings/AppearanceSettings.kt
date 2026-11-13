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
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chen.schedule.ui.theme.BackgroundPreset
import com.chen.schedule.ui.theme.ScheduleStyle
import com.chen.schedule.ui.theme.ThemePrefs
import com.chen.schedule.ui.theme.TimetableWallpaper
import com.chen.schedule.ui.theme.WidgetBackgroundMode
import com.chen.schedule.ui.theme.WidgetTextContrast
import com.chen.schedule.ui.theme.resolveScheduleColorScheme
import com.chen.schedule.util.BackgroundFileManager

@Composable
internal fun AppearanceSettings(viewModel: SettingsViewModel, wallpaper: TimetableWallpaper?) {
    val themeConfig by viewModel.themeConfig.collectAsState()

    // 相册选择器：应用背景
    val appBgPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.setCustomBackground(uri)
        }
    }

    SettingsGroup {
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
                    BackgroundPresetPicker(
                        selected = themeConfig.backgroundPreset,
                        themeMode = themeConfig.themeMode,
                        onSelect = viewModel::updateBackgroundPreset
                    )
                }
            }

            // 自定义壁纸配置
            AnimatedVisibility(visible = hasCustomBg) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    if (wallpaper != null) {
                        WallpaperPreview(wallpaper, themeConfig.backgroundDim, themeConfig.cardAlpha)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                    ) {
                        OutlinedButton(
                            onClick = {
                                appBgPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                        ) {
                            Text("更换壁纸")
                        }
                        TextButton(onClick = { viewModel.clearCustomBackground() }) {
                            Text("移除壁纸", color = MaterialTheme.colorScheme.error)
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    // 壁纸遮罩暗度
                    Text(
                        "壁纸遮罩",
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
                            0.0f to "无",
                            0.15f to "15%",
                            0.25f to "25%",
                            0.40f to "40%",
                            0.60f to "60%"
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
                        "课表网格不透明度",
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
                            1.0f to "100%",
                            0.85f to "85%",
                            0.65f to "65%",
                            0.45f to "45%"
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

}

@Composable
private fun BackgroundPresetPicker(
    selected: BackgroundPreset,
    themeMode: Int,
    onSelect: (BackgroundPreset) -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemePrefs.THEME_MODE_SYSTEM -> isSystemInDarkTheme()
        ThemePrefs.THEME_MODE_DARK -> true
        else -> false
    }
    val previews = remember(darkTheme) {
        BackgroundPreset.entries.associateWith { resolveScheduleColorScheme(it, darkTheme).background }
    }
    val colors = MaterialTheme.colorScheme
    val spacing = 8.dp
    val shape = ScheduleStyle.CardShape
    val swatchShape = RoundedCornerShape(6.dp)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val twoColumns = maxWidth >= 250.dp * LocalDensity.current.fontScale.coerceAtLeast(1f)
        Column(
            modifier = Modifier.fillMaxWidth().selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(spacing)
        ) {
            BackgroundPreset.entries.chunked(if (twoColumns) 2 else 1).forEach { presets ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing)
                ) {
                    presets.forEach { preset ->
                        val isSelected = selected == preset
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .clip(shape)
                                .background(if (isSelected) colors.secondaryContainer else colors.surface)
                                .border(
                                    if (isSelected) 2.dp else 1.dp,
                                    if (isSelected) colors.primary else colors.outlineVariant,
                                    shape
                                )
                                .selectable(
                                    selected = isSelected,
                                    role = Role.RadioButton,
                                    onClick = { onSelect(preset) }
                                )
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier.size(24.dp)
                                    .background(previews.getValue(preset), swatchShape)
                                    .border(1.dp, colors.outline, swatchShape)
                            )
                            Text(
                                text = preset.label,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isSelected) colors.onSecondaryContainer else colors.onSurface
                            )
                        }
                    }
                    if (twoColumns && presets.size == 1) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WidgetAppearanceSettings(viewModel: SettingsViewModel) {
    val themeConfig by viewModel.themeConfig.collectAsState()
    val widgetBgPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let(viewModel::setWidgetCustomBackground) }

    SettingsGroup(title = "小组件外观") {
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
                    val widgetBitmap = remember(themeConfig.widgetCustomBgPath, themeConfig.widgetCustomBgRevision) {
                        BackgroundFileManager.loadWidgetBitmap(themeConfig.widgetCustomBgPath, 300, 300)?.asImageBitmap()
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        shape = ScheduleStyle.CardShape
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

}
