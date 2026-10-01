package com.chen.schedule.ui.import_

import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import com.chen.schedule.data.scraper.SchoolRegistry
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.chen.schedule.domain.model.DayOfWeek

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(
    initialMode: Int = 0,
    onHaustImport: () -> Unit,
    onScraperLogin: (String) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: ImportViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var showJsonGuide by remember { mutableStateOf(false) }
    var showCsvGuide by remember { mutableStateOf(false) }
    // 导入方式二选一:0 = 高校教务系统导入, 1 = 文件与文本导入
    var importMode by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(initialMode) }
    var schoolSearchQuery by remember { mutableStateOf("") }
    val matchedPresets = remember(schoolSearchQuery) { SchoolRegistry.search(schoolSearchQuery) }

    // 确认导入成功后直接返回主页
    LaunchedEffect(state.importDone) {
        if (state.importDone) {
            Toast.makeText(context, state.message, Toast.LENGTH_LONG).show()
            onNavigateBack()
        }
    }

    val jsonLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? -> uri?.let { viewModel.importFromJsonUri(it) } }

    val csvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? -> uri?.let { viewModel.importFromCsvUri(it) } }

    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("导入课表", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", modifier = Modifier.size(20.dp))
                    }
                }
            )
        },
        snackbarHost = {
            if (state.message.isNotBlank()) {
                Snackbar(
                    modifier = Modifier.padding(16.dp),
                    containerColor = if (state.isError)
                        MaterialTheme.colorScheme.errorContainer
                    else
                        MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(state.message)
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {

            if (state.isImporting) item { Text("正在读取 / 导入，请稍候…", color = MaterialTheme.colorScheme.primary) }
            // ===== 导入方式二选一 =====
            item {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = importMode == 0,
                        onClick = { importMode = 0 },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                    ) { Text("高校教务导入", fontSize = 13.sp) }
                    SegmentedButton(
                        selected = importMode == 1,
                        onClick = { importMode = 1 },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                    ) { Text("文件与文本导入", fontSize = 13.sp) }
                }
                Spacer(Modifier.height(10.dp))
            }

            if (importMode == 0) {
                schoolImportItems(schoolSearchQuery, { schoolSearchQuery = it }, matchedPresets, onHaustImport, onScraperLogin)
            } else {
                fileImportItems(viewModel, context, clipboard, showJsonGuide, showCsvGuide,
                    { showJsonGuide = !showJsonGuide }, { showCsvGuide = !showCsvGuide },
                    { onScraperLogin("") },
                    { jsonLauncher.launch(it) }, { csvLauncher.launch(it) })


            }

        }
    }
    state.review?.takeIf { state.previewCourses.isNotEmpty() }?.let { review ->
        ImportReviewDialog(state.previewCourses, review, state.isImporting, viewModel::dismissPreview, viewModel::confirmImport)
    }
}
