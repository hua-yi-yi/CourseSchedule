package com.chen.schedule.ui.settings


import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
import com.chen.schedule.ui.theme.WidgetBackgroundMode
import com.chen.schedule.ui.theme.WidgetTextContrast
import com.chen.schedule.util.BackgroundFileManager
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
import com.chen.schedule.reminders.ExactAlarmPermissionObserver
import com.chen.schedule.reminders.ReminderPrefs
import com.chen.schedule.reminders.ReminderStatus
import com.chen.schedule.util.ImportSnapshotManager
import com.chen.schedule.util.log.AppLogger
import com.chen.schedule.util.log.ImportedLogMeta
import com.chen.schedule.util.log.LogCodec
import com.chen.schedule.util.log.LogLevel
import com.chen.schedule.util.log.LogStorageStats
import com.chen.schedule.util.log.LogUploadPayload
import com.chen.schedule.util.log.LogUploader
import com.chen.schedule.util.log.ParsedLogDocument
import dagger.hilt.android.qualifiers.ApplicationContext

private val wallpaperMutex = Mutex()

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
    private val exactAlarmPermissionObserver = ExactAlarmPermissionObserver(
        isAllowed = { ReminderStatus.exactAllowed(context) },
        onGranted = { ClassReminderManager.rescheduleAsync(context) }
    )

    val themeConfig: StateFlow<ThemeConfig> = ThemePrefs.state

    var reminderStatus by mutableStateOf(""); private set
    var nextReminder by mutableStateOf(""); private set
    fun onSettingsResume() {
        exactAlarmPermissionObserver.onResume()
        refreshReminderStatus()
        refreshRecoveryPoints()
        refreshLogState()
    }

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

        autoCheckUpdate = updatePrefs.autoCheckUpdate
        useMirror = updatePrefs.useMirror
        selectedMirror = updatePrefs.selectedMirror
        updateLastCheckSummary()
        refreshLogState()

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
                            val message = if (res.isMirrorUsed) "暂未发现更新 (${res.currentVersion})$mirrorTag"
                            else "当前已是最新版本 (${res.currentVersion})"
                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
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

    fun setCustomBackground(uri: Uri) = updateWallpaper(uri, isWidget = false)

    fun clearCustomBackground() = updateWallpaper(null, isWidget = false)

    fun updateBackgroundDim(dim: Float) {
        themePrefs.backgroundDim = dim
    }

    fun updateCardAlpha(alpha: Float) {
        themePrefs.cardAlpha = alpha
    }

    fun updateWidgetBackgroundMode(mode: WidgetBackgroundMode) {
        themePrefs.widgetBackgroundMode = mode
        viewModelScope.launch { WidgetUpdater.refreshAll(context) }
    }

    fun setWidgetCustomBackground(uri: Uri) = updateWallpaper(uri, isWidget = true)

    fun clearWidgetCustomBackground() = updateWallpaper(null, isWidget = true)

    private fun updateWallpaper(uri: Uri?, isWidget: Boolean) {
        viewModelScope.launch {
            wallpaperMutex.withLock {
                // 离开设置页时也要完成已经开始的文件与偏好设置更新。
                withContext(NonCancellable) {
                    val path = if (uri == null) {
                        val cleared = withContext(Dispatchers.IO) {
                            BackgroundFileManager.clearBackground(context, isWidget)
                        }
                        if (!cleared) {
                            Toast.makeText(context, "移除壁纸失败，请重试", Toast.LENGTH_SHORT).show()
                            return@withContext
                        }
                        null
                    } else {
                        val savedPath = withContext(Dispatchers.IO) {
                            BackgroundFileManager.saveBackgroundFromUri(context, uri, isWidget)
                        }
                        if (savedPath == null) {
                            val message = if (isWidget) "小组件壁纸加载失败，请重试" else "图片加载失败，请重试"
                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                            return@withContext
                        }
                        savedPath
                    }

                    if (isWidget) {
                        themePrefs.widgetCustomBgPath = path
                        if (path != null) {
                            themePrefs.widgetBackgroundMode = WidgetBackgroundMode.CUSTOM_IMAGE
                        } else if (themePrefs.widgetBackgroundMode == WidgetBackgroundMode.CUSTOM_IMAGE) {
                            themePrefs.widgetBackgroundMode = WidgetBackgroundMode.DEFAULT
                        }
                    } else {
                        themePrefs.customBackgroundPath = path
                    }
                    if (isWidget || themePrefs.widgetBackgroundMode == WidgetBackgroundMode.APP_IMAGE ||
                        themePrefs.widgetBackgroundMode == WidgetBackgroundMode.CUSTOM_IMAGE) {
                        WidgetUpdater.refreshAll(context)
                    }
                }
            }
        }
    }

    fun updateWidgetOpacity(opacity: Float) {
        themePrefs.widgetOpacity = opacity
        viewModelScope.launch { WidgetUpdater.refreshAll(context) }
    }

    fun updateWidgetTextContrast(contrast: WidgetTextContrast) {
        themePrefs.widgetTextContrast = contrast
        viewModelScope.launch { WidgetUpdater.refreshAll(context) }
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

    // ===== 系统日历直接同步 =====
    data class CalendarSyncPreview(
        val semesterName: String,
        val courseCount: Int,
        val totalEvents: Int,
        val syncedCount: Int
    )

    var calendarSyncPreview by mutableStateOf<CalendarSyncPreview?>(null); private set
    var isCalendarSyncing by mutableStateOf(false); private set

    fun prepareCalendarSyncPreview() {
        viewModelScope.launch {
            calendarSyncPreview = null
            try {
                calendarSyncPreview = withContext(Dispatchers.IO) {
                    val sem = semesterRepository.getCurrentSemester() ?: return@withContext null
                    val courses = courseRepository.getCoursesBySemester(sem.id).first()
                    val slots = timeSlotRepository.getTimeSlotsBySchemeDirect(sem.schemeId)
                    val planned = com.chen.schedule.calendar.CalendarPlanner.planEvents(sem, courses, slots)
                    val syncedCount = if (com.chen.schedule.calendar.CalendarSyncManager.hasCalendarPermissions(context)) {
                        com.chen.schedule.calendar.CalendarSyncManager.querySyncedEventCount(context, sem.id)
                    } else 0
                    CalendarSyncPreview(sem.name, courses.size, planned.size, syncedCount)
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Toast.makeText(context, "无法预览同步：${error.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun syncToCalendar(reminderMinutes: Int, onComplete: (Boolean, String) -> Unit) {
        if (isCalendarSyncing) return
        isCalendarSyncing = true
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val sem = semesterRepository.getCurrentSemester() ?: error("未找到当前学期")
                    val courses = courseRepository.getCoursesBySemester(sem.id).first()
                    val slots = timeSlotRepository.getTimeSlotsBySchemeDirect(sem.schemeId)
                    val planned = com.chen.schedule.calendar.CalendarPlanner.planEvents(sem, courses, slots)
                    com.chen.schedule.calendar.CalendarSyncManager.syncSemesterEvents(
                        context = context,
                        semester = sem,
                        events = planned,
                        reminderMinutes = reminderMinutes
                    ).getOrThrow()
                }
            }
            isCalendarSyncing = false
            if (result.isSuccess) {
                val count = result.getOrDefault(0)
                prepareCalendarSyncPreview()
                onComplete(true, "成功将 $count 条课程日程同步至系统日历")
            } else {
                val errorMsg = result.exceptionOrNull()?.message ?: "同步失败"
                onComplete(false, "同步失败: $errorMsg")
            }
        }
    }

    fun clearSyncedCalendar(onComplete: (Boolean, String) -> Unit) {
        if (isCalendarSyncing) return
        isCalendarSyncing = true
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val sem = semesterRepository.getCurrentSemester() ?: error("未找到当前学期")
                    com.chen.schedule.calendar.CalendarSyncManager.deleteCalendar(context, sem.id)
                }
            }
            isCalendarSyncing = false
            if (result.isSuccess) {
                prepareCalendarSyncPreview()
                onComplete(true, "已清除系统日历中的课表日程")
            } else {
                val errorMsg = result.exceptionOrNull()?.message ?: "清除失败"
                onComplete(false, "清除日历失败: $errorMsg")
            }
        }
    }

    // ===== 运行日志保存、自主上传与导入日志专区 =====
    var logSaveEnabled by mutableStateOf(true); private set
    var logStats by mutableStateOf(LogStorageStats()); private set
    var importedLogs by mutableStateOf<List<ImportedLogMeta>>(emptyList()); private set
    var logServerUrl by mutableStateOf(""); private set
    var importSnapshotCount by mutableStateOf(0); private set

    var isLoadingLogs by mutableStateOf(false); private set
    var activeLogDocument by mutableStateOf<ParsedLogDocument?>(null); private set
    var activeLogIsRuntime by mutableStateOf(false); private set

    var isUploadingLog by mutableStateOf(false); private set
    var logUploadMessage by mutableStateOf<String?>(null); private set
    var logUploadSuccess by mutableStateOf(false); private set

    fun refreshLogState() {
        logSaveEnabled = AppLogger.isSaveEnabled(context)
        logServerUrl = AppLogger.getServerUrl(context)
        viewModelScope.launch {
            runCatching {
                val stats = AppLogger.getStorageStats(context)
                val imported = AppLogger.listImportedLogs(context)
                val snapshots = ImportSnapshotManager.listSnapshots(context).size
                Triple(stats, imported, snapshots)
            }.onSuccess { (stats, imported, snapshots) ->
                logStats = stats
                importedLogs = imported
                importSnapshotCount = snapshots
            }
        }
    }

    fun updateLogSaveEnabled(enabled: Boolean) {
        AppLogger.setSaveEnabled(context, enabled)
        logSaveEnabled = enabled
        refreshLogState()
    }

    fun updateLogServerUrl(url: String) {
        logServerUrl = url
        AppLogger.setServerUrl(context, url)
    }

    fun openRuntimeLogViewer() {
        if (isLoadingLogs) return
        isLoadingLogs = true
        viewModelScope.launch {
            try {
                val entries = AppLogger.readRecentEntries(context, limit = 500)
                val exportText = AppLogger.buildExportText(context, includeSnapshots = true, limit = 500)
                activeLogIsRuntime = true
                activeLogDocument = ParsedLogDocument(
                    title = "本机运行与崩溃日志",
                    formatLabel = "本机实时日志",
                    metadata = AppLogger.buildDeviceMetadata(),
                    entries = entries,
                    rawText = exportText
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Toast.makeText(context, "读取本地日志失败: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                isLoadingLogs = false
            }
        }
    }

    fun closeLogViewer() {
        activeLogDocument = null
    }

    fun importExternalLog(uri: Uri) {
        if (isLoadingLogs) return
        isLoadingLogs = true
        viewModelScope.launch {
            try {
                val (meta, doc) = AppLogger.importLogFromUri(context, uri)
                refreshLogState()
                activeLogIsRuntime = false
                activeLogDocument = doc
                Toast.makeText(
                    context,
                    "成功导入日志「${meta.fileName}」，解析到 ${doc.entries.size} 条记录",
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Toast.makeText(context, "导入日志失败: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                isLoadingLogs = false
            }
        }
    }

    fun openImportedLog(meta: ImportedLogMeta) {
        if (isLoadingLogs) return
        isLoadingLogs = true
        viewModelScope.launch {
            try {
                val doc = AppLogger.loadImportedLog(context, meta.id)
                if (doc != null) {
                    activeLogIsRuntime = false
                    activeLogDocument = doc
                } else {
                    Toast.makeText(context, "该导入日志文件已不存在", Toast.LENGTH_SHORT).show()
                    refreshLogState()
                }
            } finally {
                isLoadingLogs = false
            }
        }
    }

    fun deleteImportedLog(id: String) {
        viewModelScope.launch {
            AppLogger.deleteImportedLog(context, id)
            refreshLogState()
            Toast.makeText(context, "已移除该条导入日志", Toast.LENGTH_SHORT).show()
        }
    }

    fun clearAllImportedLogs() {
        viewModelScope.launch {
            AppLogger.clearImportedLogs(context)
            refreshLogState()
            Toast.makeText(context, "已清空全部导入日志记录", Toast.LENGTH_SHORT).show()
        }
    }

    fun clearRuntimeLogs() {
        viewModelScope.launch {
            AppLogger.clearRuntimeLogs(context)
            if (activeLogIsRuntime && activeLogDocument != null) {
                activeLogDocument = activeLogDocument?.copy(entries = emptyList(), rawText = "")
            }
            refreshLogState()
            Toast.makeText(context, "已清空本机运行与崩溃日志", Toast.LENGTH_SHORT).show()
        }
    }

    fun exportRuntimeLogToUri(uri: Uri) {
        viewModelScope.launch {
            try {
                AppLogger.exportToUri(context, uri, includeSnapshots = true)
                refreshLogState()
                Toast.makeText(context, "运行日志导出成功", Toast.LENGTH_SHORT).show()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Toast.makeText(context, "导出日志失败: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun shareRuntimeLogs() {
        viewModelScope.launch {
            try {
                val content = AppLogger.buildExportText(context, includeSnapshots = true, limit = 500)
                val stamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US)
                    .format(java.util.Date())
                AppLogger.shareLogFile(
                    context = context,
                    fileName = "course_schedule_log_$stamp.log",
                    content = content,
                    chooserTitle = "分享应用诊断日志"
                )
            } catch (e: Exception) {
                Toast.makeText(context, "分享日志失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun shareLogDocument(doc: ParsedLogDocument) {
        viewModelScope.launch {
            try {
                val content = doc.rawText.ifBlank {
                    LogCodec.buildExportDocument(doc.entries, doc.metadata)
                }
                val fileName = if (doc.title.contains('.')) doc.title else "${doc.title}.log"
                AppLogger.shareLogFile(
                    context = context,
                    fileName = fileName,
                    content = content,
                    chooserTitle = "分享日志文件"
                )
            } catch (e: Exception) {
                Toast.makeText(context, "分享失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun clearLogUploadFeedback() {
        logUploadMessage = null
        logUploadSuccess = false
    }

    suspend fun prepareUploadPayload(
        userNote: String,
        includeSnapshots: Boolean,
        targetDocument: ParsedLogDocument? = null
    ): LogUploadPayload {
        val entries = targetDocument?.entries ?: AppLogger.readRecentEntries(context, limit = 400)
        val snapshots = if (includeSnapshots && targetDocument == null) {
            AppLogger.loadSnapshotBriefs(context)
        } else {
            emptyList()
        }
        val sourceLabel = if (targetDocument != null) {
            "imported:${targetDocument.title}"
        } else {
            "runtime"
        }
        return LogUploader.buildPayload(
            entries = entries,
            appVersion = com.chen.schedule.BuildConfig.VERSION_NAME,
            versionCode = com.chen.schedule.BuildConfig.VERSION_CODE,
            androidSdk = android.os.Build.VERSION.SDK_INT,
            deviceModel = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}",
            userNote = userNote,
            sourceLabel = sourceLabel,
            importSnapshots = snapshots
        )
    }

    fun uploadLogs(
        serverUrl: String,
        userNote: String,
        includeSnapshots: Boolean,
        targetDocument: ParsedLogDocument? = null
    ) {
        if (isUploadingLog) return
        updateLogServerUrl(serverUrl)
        isUploadingLog = true
        logUploadMessage = null
        logUploadSuccess = false

        viewModelScope.launch {
            try {
                val payload = prepareUploadPayload(userNote, includeSnapshots, targetDocument)
                if (payload.entries.isEmpty() && payload.importSnapshots.isEmpty() && userNote.isBlank()) {
                    logUploadSuccess = false
                    logUploadMessage = "当前暂无可上传的日志记录，请先复现问题或导入日志文件"
                    return@launch
                }
                val res = LogUploader.upload(serverUrl, payload)
                res.fold(
                    onSuccess = { msg ->
                        logUploadSuccess = true
                        logUploadMessage = msg
                        AppLogger.i("LogUploader", "用户自主上传日志成功: $msg")
                        refreshLogState()
                    },
                    onFailure = { err ->
                        logUploadSuccess = false
                        logUploadMessage = err.message ?: "上传失败"
                        AppLogger.w("LogUploader", "自主上传日志未成功: ${err.message}")
                        refreshLogState()
                    }
                )
            } finally {
                isUploadingLog = false
            }
        }
    }
}
