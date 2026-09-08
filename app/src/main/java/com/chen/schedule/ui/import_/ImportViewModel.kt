package com.chen.schedule.ui.import_

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chen.schedule.data.repository.CourseRepository
import com.chen.schedule.data.repository.SemesterRepository
import com.chen.schedule.domain.model.Course
import com.chen.schedule.domain.model.CoursePalette
import com.chen.schedule.util.CsvImporter
import com.chen.schedule.util.JsonImporter
import com.chen.schedule.widget.WidgetUpdater
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class ImportState(
    val previewCourses: List<Course> = emptyList(),
    val message: String = "",
    val isError: Boolean = false,
    val isImporting: Boolean = false,
    val sampleJson: String = "",
    val sampleCsv: String = ""
)

@HiltViewModel
class ImportViewModel @Inject constructor(
    private val courseRepository: CourseRepository,
    private val semesterRepository: SemesterRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _state = MutableStateFlow(ImportState())
    val state: StateFlow<ImportState> = _state.asStateFlow()

    fun importFromJsonUri(uri: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(isImporting = true, message = "", isError = false, previewCourses = emptyList()) }
            try {
                withContext(Dispatchers.IO) {
                    val jsonString = requireNotNull(context.contentResolver.openInputStream(uri)).bufferedReader().use { it.readText() }
                    withContext(Dispatchers.Default) { parseJsonText(jsonString) }
                }
            } catch (e: Exception) {
                _state.update { it.copy(message = "读取文件失败: ${e.message}", isError = true, isImporting = false) }
            }
        }
    }

    fun importFromCsvUri(uri: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(isImporting = true, message = "", isError = false, previewCourses = emptyList()) }
            try {
                withContext(Dispatchers.IO) {
                    val csvString = requireNotNull(context.contentResolver.openInputStream(uri)).bufferedReader().use { it.readText() }
                    withContext(Dispatchers.Default) { parseCsvText(csvString) }
                }
            } catch (e: Exception) {
                _state.update { it.copy(message = "读取文件失败: ${e.message}", isError = true, isImporting = false) }
            }
        }
    }

    fun importFromJsonText(jsonString: String) {
        viewModelScope.launch {
            _state.update { it.copy(isImporting = true, message = "", isError = false, previewCourses = emptyList()) }
            withContext(Dispatchers.Default) { parseJsonText(jsonString) }
        }
    }

    fun importFromCsvText(csvString: String) {
        viewModelScope.launch {
            _state.update { it.copy(isImporting = true, message = "", isError = false, previewCourses = emptyList()) }
            withContext(Dispatchers.Default) { parseCsvText(csvString) }
        }
    }

    private fun parseJsonText(jsonString: String) {
        val result = JsonImporter.parse(jsonString)
        result.fold(
            onSuccess = { courses ->
                _state.update { it.copy(previewCourses = courses, isError = false, message = "解析成功，共 ${courses.size} 门课程", isImporting = false) }
            },
            onFailure = { e ->
                _state.update { it.copy(message = "JSON 解析失败: ${e.message}", isError = true, isImporting = false) }
            }
        )
    }

    private fun parseCsvText(csvString: String) {
        val result = CsvImporter.parse(csvString)
        result.fold(
            onSuccess = { courses ->
                _state.update { it.copy(previewCourses = courses, isError = false, message = "解析成功，共 ${courses.size} 门课程", isImporting = false) }
            },
            onFailure = { e ->
                _state.update { it.copy(message = "CSV 解析失败: ${e.message}", isError = true, isImporting = false) }
            }
        )
    }

    fun confirmImport() {
        if (_state.value.isImporting || _state.value.previewCourses.isEmpty()) return
        val preview = _state.value.previewCourses
        _state.update { it.copy(isImporting = true) }
        viewModelScope.launch {
            try {
                val currentSemester = semesterRepository.getCurrentSemester() ?: error("请先在设置中创建学期")
                val courses = CoursePalette.assignColors(preview).map { it.copy(semesterId = currentSemester.id) }
                courseRepository.insertAll(courses)
                _state.update { it.copy(previewCourses = emptyList(), message = "成功导入 ${courses.size} 门课程", isError = false) }
                WidgetUpdater.refreshAll(context)
            } catch (e: Exception) {
                _state.update { it.copy(message = "导入失败: ${e.message}", isError = true) }
            } finally {
                _state.update { it.copy(isImporting = false) }
            }
        }
    }

    fun setPreviewCourses(courses: List<Course>) {
        _state.update { it.copy(previewCourses = courses) }
    }

    fun clearMessage() {
        _state.update { it.copy(message = "", isError = false) }
    }
}
