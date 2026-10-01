package com.chen.schedule.ui.import_

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.SuggestionChip
import com.chen.schedule.data.scraper.SchoolPreset
import com.chen.schedule.data.scraper.SchoolSystemType
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.lazy.LazyListScope

internal fun LazyListScope.schoolImportItems(
    schoolSearchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    matchedPresets: List<SchoolPreset>,
    onHaustImport: () -> Unit,
    onScraperLogin: (String) -> Unit
) {
    // 高校搜索栏
    item {
        OutlinedTextField(
            value = schoolSearchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("搜索高校名称 / 拼音首字母 (如: 河科大、浙工大、正方)...", fontSize = 12.5.sp) },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = "搜索", modifier = Modifier.size(18.dp))
            },
            trailingIcon = {
                if (schoolSearchQuery.isNotBlank()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(Icons.Default.Close, contentDescription = "清空", modifier = Modifier.size(16.dp))
                    }
                }
            },
            singleLine = true
        )
        Spacer(Modifier.height(10.dp))
    }

    if (matchedPresets.isEmpty()) {
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("未找到匹配的高校预设", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "如果您的学校使用正方教务系统，可直接使用正方经典版入口输入教务地址进行登录导入；或使用「文件与文本导入」。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = { onScraperLogin("") }, modifier = Modifier.fillMaxWidth()) {
                        Text("使用正方经典版教务登录")
                    }
                }
            }
        }
    } else {
        items(matchedPresets, key = { it.id }) { preset ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(
                    containerColor = if (preset.systemType == SchoolSystemType.WEB_VPN_EAMS)
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                    else
                        MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.School,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            preset.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        if (preset.badge.isNotBlank()) {
                            SuggestionChip(
                                onClick = {},
                                label = { Text(preset.badge, fontSize = 11.sp) }
                            )
                        }
                    }
                    if (preset.description.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            preset.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = {
                            if (preset.systemType == SchoolSystemType.WEB_VPN_EAMS) {
                                onHaustImport()
                            } else {
                                onScraperLogin(preset.id)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.CloudDownload, "导入", modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (preset.systemType == SchoolSystemType.WEB_VPN_EAMS)
                                "进入 ${preset.name} · VPN/校内导入"
                            else
                                "进入教务登录抓取"
                        )
                    }
                }
            }
        }
    }
}
