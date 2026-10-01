package com.chen.schedule.ui.settings


import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.content.Context
import android.net.Uri
import android.widget.Toast
import com.chen.schedule.util.update.AppReleaseInfo
import com.chen.schedule.util.update.AppUpdateChecker
import com.chen.schedule.util.update.AppUpdateDownloader
import com.chen.schedule.util.update.DownloadState
import com.chen.schedule.util.update.GithubMirror
import com.chen.schedule.util.update.UpdateCheckResult
import com.chen.schedule.util.update.UpdatePrefs
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.StateFlow
import com.chen.schedule.ui.theme.BackgroundPreset
import com.chen.schedule.ui.theme.ThemeConfig
import com.chen.schedule.ui.theme.ThemePrefs
import com.chen.schedule.data.repository.CourseRepository
import com.chen.schedule.data.repository.SemesterRepository
import com.chen.schedule.data.repository.TimeSlotRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chen.schedule.widget.WidgetUpdater
import com.chen.schedule.reminders.ClassReminderManager
import com.chen.schedule.reminders.ReminderPrefs
import dagger.hilt.android.qualifiers.ApplicationContext


@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val courseRepository: CourseRepository,
    private val semesterRepository: SemesterRepository,
    private val timeSlotRepository: TimeSlotRepository,
    private val updateChecker: AppUpdateChecker,
    private val updateDownloader: AppUpdateDownloader,
    private val backupService: ScheduleBackupService,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val reminderPrefs by lazy { ReminderPrefs(context) }
    private val themePrefs by lazy { ThemePrefs(context) }
    private val updatePrefs by lazy { UpdatePrefs(context) }

    val themeConfig: StateFlow<ThemeConfig> = ThemePrefs.state

    var reminderStatus by mutableStateOf(""); private set
    var nextReminder by mutableStateOf(""); private set
    fun refreshReminderStatus() {
        reminderStatus = com.chen.schedule.reminders.ReminderStatus.describe(context, reminderEnabled)
        viewModelScope.launch {
            val sem = semesterRepository.getCurrentSemester()
            if (sem == null || !reminderEnabled) { nextReminder = "暂无预计提醒"; return@launch }
            val courses = courseRepository.getCoursesBySemester(sem.id).first()
            val slots = timeSlotRepository.getTimeSlotsBySchemeDirect(sem.schemeId)
            val now = System.currentTimeMillis()
            val zone = java.time.ZoneId.systemDefault()
            val today = java.time.LocalDate.now()
            var next: com.chen.schedule.reminders.ClassReminderPlanner.ReminderPlan? = null
            for (offset in 0..(sem.totalWeeks * 7)) {
                val date = today.plusDays(offset.toLong())
                val millis = if (offset == 0) now else date.atStartOfDay(zone).toInstant().toEpochMilli()
                val week = com.chen.schedule.util.WeekCalculator.activeWeek(sem.startDate, sem.totalWeeks, millis) ?: continue
                next = com.chen.schedule.reminders.ClassReminderPlanner.planForToday(millis, slots, courses, week, date.dayOfWeek.value, reminderLead).firstOrNull()
                if (next != null) break
            }
            nextReminder = next?.let { "预计下一次：" + java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(it.triggerAtMillis)) + " · " + it.courseName }
                ?: "本学期没有待提醒的课程"
        }
    }
    fun testReminder() {
        try { com.chen.schedule.reminders.ReminderStatus.test(context); dataMessage = "已发送测试通知，请检查通知栏" }
        catch (e: Exception) { dataMessage = e.message.orEmpty() }
        refreshReminderStatus()
    }
    var reminderEnabled by androidx.compose.runtime.mutableStateOf(false); private set
    var reminderLead by androidx.compose.runtime.mutableStateOf(ReminderPrefs.DEFAULT_LEAD_MINUTES); private set
    var reminderOngoing by androidx.compose.runtime.mutableStateOf(true); private set

    var autoCheckUpdate by androidx.compose.runtime.mutableStateOf(true); private set
    var useMirror by androidx.compose.runtime.mutableStateOf(true); private set
    var selectedMirror by androidx.compose.runtime.mutableStateOf(GithubMirror.GHFAST); private set

    var isCheckingUpdate by androidx.compose.runtime.mutableStateOf(false); private set
    var updateResult by androidx.compose.runtime.mutableStateOf<UpdateCheckResult?>(null); private set
    var lastCheckSummary by androidx.compose.runtime.mutableStateOf("点击检查最新版本"); private set

    val downloadState: StateFlow<DownloadState> = updateDownloader.downloadState

    var isTestingMirrors by androidx.compose.runtime.mutableStateOf(false); private set
    var mirrorLatencies by androidx.compose.runtime.mutableStateOf<Map<GithubMirror, Long?>>(emptyMap()); private set

    init {
        reminderEnabled = reminderPrefs.enabled
        reminderLead = reminderPrefs.leadMinutes
        reminderOngoing = reminderPrefs.ongoingClassEnabled

        autoCheckUpdate = updatePrefs.autoCheckUpdate
        useMirror = updatePrefs.useMirror
        selectedMirror = updatePrefs.selectedMirror
        updateLastCheckSummary()

        val currentDl = updateDownloader.downloadState.value
        if (currentDl is DownloadState.Downloading || currentDl is DownloadState.Completed) {
            val cachedRelease = updateDownloader.currentRelease ?: updateChecker.lastHasUpdateResult?.release
            if (cachedRelease != null) {
                updateResult = UpdateCheckResult.HasUpdate(
                    release = cachedRelease,
                    currentVersion = "v${com.chen.schedule.BuildConfig.VERSION_NAME}",
                    isMirrorUsed = useMirror,
                    mirrorName = selectedMirror.displayName
                )
            }
        } else if (autoCheckUpdate) {
            checkUpdate(manual = false)
        }
    }

    fun updateAutoCheckUpdate(enabled: Boolean) {
        updatePrefs.autoCheckUpdate = enabled
        autoCheckUpdate = enabled
    }

    fun updateUseMirror(enabled: Boolean) {
        updatePrefs.useMirror = enabled
        useMirror = enabled
    }

    fun updateSelectedMirror(mirror: GithubMirror) {
        updatePrefs.selectedMirror = mirror
        selectedMirror = mirror
    }

    private fun updateLastCheckSummary() {
        val last = updatePrefs.lastCheckTime
        val src = updatePrefs.lastCheckSource
        lastCheckSummary = if (last > 0L) {
            val dateStr = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(last))
            if (src.isNotBlank()) "上次检查: $dateStr ($src)" else "上次检查: $dateStr"
        } else {
            "点击检查最新版本"
        }
    }

    fun checkUpdate(manual: Boolean = true) {
        if (isCheckingUpdate) return
        isCheckingUpdate = true
        viewModelScope.launch {
            try {
                val res = updateChecker.checkUpdate()
                updateResult = res
                updateLastCheckSummary()
                if (res is UpdateCheckResult.HasUpdate) {
                    updateDownloader.syncExistingDownload(res.release.versionTag)
                }
                if (manual) {
                    when (res) {
                        is UpdateCheckResult.UpToDate -> {
                            val mirrorTag = if (res.isMirrorUsed && !res.mirrorName.isNullOrBlank()) {
                                " · 经由「${res.mirrorName}」检测"
                            } else ""
                            Toast.makeText(context, "当前已是最新版本 (${res.currentVersion})$mirrorTag", Toast.LENGTH_SHORT).show()
                        }
                        is UpdateCheckResult.Error -> {
                            Toast.makeText(context, "检查更新失败: ${res.message}", Toast.LENGTH_LONG).show()
                        }
                        is UpdateCheckResult.HasUpdate -> {
                            // 保持在 updateResult，UI 弹窗展示
                        }
                    }
                }
            } catch (e: Exception) {
                if (manual) {
                    Toast.makeText(context, "检查更新异常: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } finally {
                isCheckingUpdate = false
            }
        }
    }

    fun dismissUpdateDialog() {
        updateResult = null
    }

    fun reopenUpdateDialog() {
        val release = updateDownloader.currentRelease ?: updateChecker.lastHasUpdateResult?.release
        if (release != null) {
            updateResult = UpdateCheckResult.HasUpdate(
                release = release,
                currentVersion = "v${com.chen.schedule.BuildConfig.VERSION_NAME}",
                isMirrorUsed = useMirror,
                mirrorName = selectedMirror.displayName
            )
        } else {
            checkUpdate(manual = true)
        }
    }

    /**
     * 应用内静默下载 APK 并在完成后自动拉起系统安装器。
     */
    fun startDownload(url: String, versionTag: String, release: AppReleaseInfo? = null) {
        updateDownloader.startDownload(url, versionTag, release)
    }

    fun cancelDownload() {
        updateDownloader.cancelDownload()
    }

    fun installDownloadedApk(file: java.io.File) {
        updateDownloader.installApk(context, file)
    }

    fun testAllMirrors() {
        if (isTestingMirrors) return
        isTestingMirrors = true
        viewModelScope.launch {
            try {
                mirrorLatencies = updateChecker.testAllMirrors()
            } catch (_: Exception) {
            } finally {
                isTestingMirrors = false
            }
        }
    }

    fun updateThemeMode(mode: Int) {
        themePrefs.themeMode = mode
    }

    fun updateBackgroundPreset(preset: BackgroundPreset) {
        themePrefs.backgroundPresetId = preset.id
    }

    /** 开关上课提醒:立即重排/取消今天的提醒闹钟。 */
    fun updateReminderEnabled(enabled: Boolean) {
        reminderPrefs.enabled = enabled
        reminderEnabled = enabled
        ClassReminderManager.rescheduleAsync(context)
        refreshReminderStatus()
    }

    /** 修改提前量:立即按新提前量重排今天的提醒。 */
    fun updateReminderLead(minutes: Int) {
        reminderPrefs.leadMinutes = minutes
        reminderLead = minutes
        ClassReminderManager.rescheduleAsync(context)
        refreshReminderStatus()
    }

    /** 开关上课中常驻看板:立即更新偏好并重排看板与闹钟。 */
    fun updateReminderOngoing(enabled: Boolean) {
        reminderPrefs.ongoingClassEnabled = enabled
        reminderOngoing = enabled
        ClassReminderManager.rescheduleAsync(context)
        refreshReminderStatus()
    }

    fun exportToUri(uri: Uri) {
        viewModelScope.launch {
            try {
                backupService.exportToUri(uri)
                Toast.makeText(context, "导出成功", Toast.LENGTH_SHORT).show()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Toast.makeText(context, "导出失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    var restorePreview by mutableStateOf<ScheduleBackupService.RestorePreview?>(null); private set
    var dataBusy by mutableStateOf(false); private set
    var dataMessage by mutableStateOf(""); private set
    var recoveryFiles by mutableStateOf<List<java.io.File>>(emptyList()); private set
    fun refreshRecoveryPoints() { recoveryFiles = backupService.recoveryPoints() }
    fun dismissRestore() { if (!dataBusy) restorePreview = null }
    fun previewRestore(uri: Uri) {
        if (dataBusy) return
        dataBusy = true; dataMessage = ""
        viewModelScope.launch {
            try { restorePreview = backupService.previewRestore(uri) }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { dataMessage = "读取失败：${e.message}" }
            finally { dataBusy = false }
        }
    }
    fun confirmRestore() {
        val preview = restorePreview ?: return
        importData(preview.uri, preview.digest)
    }
    fun importData(uri: Uri, digest: String? = null) {
        if (dataBusy) return
        dataBusy = true
        viewModelScope.launch {
            try {
                val result = backupService.importData(uri, digest)
                WidgetUpdater.refreshAll(context)
                val message = if (result.fullRestore) "成功恢复 ${result.count} 门课程(含全部学期)"
                    else "成功恢复 ${result.count} 门课程(当前学期)"
                dataMessage = message; restorePreview = null; refreshRecoveryPoints()
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                dataMessage = "恢复失败：${e.message}"
                Toast.makeText(context, dataMessage, Toast.LENGTH_SHORT).show()
            } finally { dataBusy = false; refreshRecoveryPoints() }
        }
    }
    fun exportIcsToUri(uri: Uri) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val sem = semesterRepository.getCurrentSemester() ?: error("没有当前学期")
                    val courses = courseRepository.getCoursesBySemester(sem.id).first()
                    val slots = timeSlotRepository.getTimeSlotsBySchemeDirect(sem.schemeId)
                    val icsString = com.chen.schedule.util.IcsExporter.export(
                        semester = sem,
                        courses = courses,
                        slots = slots,
                        alarmMinutes = reminderLead.takeIf { reminderEnabled } ?: 20
                    )
                    requireNotNull(context.contentResolver.openOutputStream(uri)).use {
                        it.write(icsString.toByteArray(Charsets.UTF_8))
                    }
                }
                android.widget.Toast.makeText(context, "日历文件导出成功", android.widget.Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                android.widget.Toast.makeText(context, "导出日历失败: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun clearAllData() {
        if (dataBusy) return
        dataBusy = true
        viewModelScope.launch {
            try {
                val sem = semesterRepository.getCurrentSemester() ?: error("没有当前学期")
                backupService.clearSemester(sem.id)
                WidgetUpdater.refreshAll(context)
                dataMessage = "当前学期已清空，可从自动恢复点找回"
                Toast.makeText(context, dataMessage, Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                dataMessage = "清空失败：${e.message}"
                Toast.makeText(context, dataMessage, Toast.LENGTH_SHORT).show()
            } finally { dataBusy = false; refreshRecoveryPoints() }
        }
    }

}
