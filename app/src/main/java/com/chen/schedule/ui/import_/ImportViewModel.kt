package com.chen.schedule.ui.import_

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chen.schedule.data.repository.CourseImportService
import com.chen.schedule.data.repository.SemesterRepository
import com.chen.schedule.data.repository.TimeSlotRepository
import com.chen.schedule.domain.model.Course
import com.chen.schedule.util.CourseImportRules
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
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

data class ImportState(
    val previewCourses: List<Course> = emptyList(),
    val message: String = "",
    val isError: Boolean = false,
    val isImporting: Boolean = false,
    /** 确认导入成功后置位,界面据此直接返回主页。 */
    val importDone: Boolean = false,
    val review: com.chen.schedule.util.ImportReviewContext? = null,
    val previewSemesterId: Long? = null,
    val previewSemesterName: String = "",
    val sampleJson: String = "",
    val sampleCsv: String = ""
)

@HiltViewModel
class ImportViewModel @Inject constructor(
    private val importService: CourseImportService,
    private val semesterRepository: SemesterRepository,
    private val timeSlotRepository: TimeSlotRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _state = MutableStateFlow(ImportState())
    val state: StateFlow<ImportState> = _state.asStateFlow()

    fun importFromJsonUri(uri: Uri) {
        if (_state.value.isImporting) return
        viewModelScope.launch {
            _state.update { it.copy(isImporting = true, message = "", isError = false, previewCourses = emptyList()) }
            try {
                withContext(Dispatchers.IO) {
                    val jsonString = requireNotNull(context.contentResolver.openInputStream(uri)).bufferedReader().use { it.readText() }
                    withContext(Dispatchers.Default) { parseJsonText(jsonString) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(message = "读取文件失败: ${e.message}", isError = true, isImporting = false) }
            }
        }
    }

    fun importFromCsvUri(uri: Uri) {
        if (_state.value.isImporting) return
        viewModelScope.launch {
            _state.update { it.copy(isImporting = true, message = "", isError = false, previewCourses = emptyList()) }
            try {
                withContext(Dispatchers.IO) {
                    val csvString = requireNotNull(context.contentResolver.openInputStream(uri)).bufferedReader().use { it.readText() }
                    withContext(Dispatchers.Default) { parseCsvText(csvString) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(message = "读取文件失败: ${e.message}", isError = true, isImporting = false) }
            }
        }
    }

    fun importFromJsonText(jsonString: String) {
        if (_state.value.isImporting) return
        viewModelScope.launch {
            _state.update { it.copy(isImporting = true, message = "", isError = false, previewCourses = emptyList()) }
            withContext(Dispatchers.Default) { parseJsonText(jsonString) }
        }
    }

    fun importFromCsvText(csvString: String) {
        if (_state.value.isImporting) return
        viewModelScope.launch {
            _state.update { it.copy(isImporting = true, message = "", isError = false, previewCourses = emptyList()) }
            withContext(Dispatchers.Default) { parseCsvText(csvString) }
        }
    }

    private suspend fun parseJsonText(jsonString: String) = preparePreview(JsonImporter.parse(jsonString))

    private suspend fun parseCsvText(csvString: String) = preparePreview(CsvImporter.parse(csvString))

    private suspend fun preparePreview(result: Result<List<Course>>) {
        try {
            val courses = result.getOrThrow()
            val semester = semesterRepository.getCurrentSemester() ?: error("请先创建学期")
            val review = importService.review(courses, "file")
            _state.update { it.copy(review = review, previewCourses = courses, previewSemesterId = semester.id,
                previewSemesterName = semester.name, isError = false,
                message = "解析成功，共 ${courses.size} 条课程安排", isImporting = false) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.update { it.copy(previewCourses = emptyList(), previewSemesterId = null,
                message = "读取失败：${e.message}；未导入任何课程", isError = true, isImporting = false) }
        }
    }

    fun dismissPreview() { if (!_state.value.isImporting) _state.update { it.copy(previewCourses = emptyList(), review = null) } }
    fun confirmImport(selection: com.chen.schedule.util.ImportSelection) {
        if (_state.value.isImporting || _state.value.previewCourses.isEmpty()) return
        val preview = _state.value.previewCourses
        val review = _state.value.review ?: return
        _state.update { it.copy(isImporting = true) }
        viewModelScope.launch {
            try {
                val result = importService.applyReviewed(selection, review)
                WidgetUpdater.refreshAll(context)
                _state.update {
                    it.copy(
                        previewCourses = emptyList(),
                        message = result.message,
                        isError = false,
                        importDone = true
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(message = "导入失败: ${e.message}", isError = true) }
            } finally {
                _state.update { it.copy(isImporting = false) }
            }
        }
    }

    fun clearMessage() {
        _state.update { it.copy(message = "", isError = false) }
    }
}
