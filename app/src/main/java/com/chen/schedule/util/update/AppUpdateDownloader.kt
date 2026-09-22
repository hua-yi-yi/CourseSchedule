package com.chen.schedule.util.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import com.chen.schedule.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 应用内 APK 下载状态。
 */
sealed class DownloadState {
    object Idle : DownloadState()
    data class Downloading(
        val progress: Int,
        val bytesDownloaded: Long,
        val totalBytes: Long,
        val versionTag: String = ""
    ) : DownloadState()
    data class Completed(
        val file: File,
        val versionTag: String = ""
    ) : DownloadState()
    data class Failed(
        val error: String,
        val versionTag: String = ""
    ) : DownloadState()
}

/**
 * 应用内静默下载引擎与原生安装器。
 * 具备应用级全局生命周期，脱离界面 ViewModel 独立运行，后台退出与切页不中断。
 */
@Singleton
class AppUpdateDownloader @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

    private var activeCall: Call? = null
    private var downloadJob: Job? = null

    var currentVersionTag: String? = null
        private set
    var currentDownloadUrl: String? = null
        private set
    var currentRelease: AppReleaseInfo? = null
        private set

    /**
     * 检查本地是否已存在指定版本的完整有效 APK。
     */
    fun checkExistingCompletedApk(versionTag: String): File? {
        val cleanTag = versionTag.ifBlank { "latest" }
        val updatesDir = File(context.cacheDir, "updates")
        if (!updatesDir.exists()) return null

        val apkFile = File(updatesDir, "CourseSchedule-$cleanTag.apk")
        if (!apkFile.exists() || apkFile.length() <= 0) return null

        return try {
            val pm = context.packageManager
            val info = pm.getPackageArchiveInfo(apkFile.absolutePath, 0)
            if (info != null) {
                apkFile
            } else if (apkFile.length() > 2 * 1024 * 1024L) {
                // 部分测试环境可能没有完整 PackageManager 解析器，容底校验大小
                apkFile
            } else {
                null
            }
        } catch (_: Exception) {
            if (apkFile.length() > 2 * 1024 * 1024L) apkFile else null
        }
    }

    /**
     * 同步本地已下载状态。若已有完整安装包则直接置为 Completed。
     */
    fun syncExistingDownload(versionTag: String): Boolean {
        val existing = checkExistingCompletedApk(versionTag)
        if (existing != null) {
            currentVersionTag = versionTag
            _downloadState.value = DownloadState.Completed(existing, versionTag)
            return true
        }
        return false
    }

    /**
     * 启动应用内全局后台静默下载。
     * 脱离页面生命周期，在 Application 级协程中运行，即使离开设置页也能在后台继续平稳下载。
     */
    fun startDownload(url: String, versionTag: String, release: AppReleaseInfo? = null) {
        val cleanTag = versionTag.ifBlank { "latest" }
        currentVersionTag = cleanTag
        currentDownloadUrl = url
        if (release != null) currentRelease = release

        // 1. 如果本地已经存在完整 APK，直接就绪并调起安装
        val existing = checkExistingCompletedApk(cleanTag)
        if (existing != null) {
            _downloadState.value = DownloadState.Completed(existing, cleanTag)
            mainHandler.post {
                Toast.makeText(context, "新版本安装包已就绪，正在调起安装...", Toast.LENGTH_SHORT).show()
                installApk(context, existing)
            }
            return
        }

        // 2. 如果正在下载同版本，无需重复启动
        val current = _downloadState.value
        if (current is DownloadState.Downloading && current.versionTag == cleanTag && downloadJob?.isActive == true) {
            return
        }

        // 3. 取消正在执行的旧任务
        cancelOngoing()

        // 4. 启动后台协程
        downloadJob = applicationScope.launch {
            val updatesDir = File(context.cacheDir, "updates")
            if (!updatesDir.exists()) updatesDir.mkdirs()

            val apkFile = File(updatesDir, "CourseSchedule-$cleanTag.apk")
            val tmpFile = File(updatesDir, "CourseSchedule-$cleanTag.apk.tmp")

            if (tmpFile.exists()) tmpFile.delete()

            _downloadState.value = DownloadState.Downloading(0, 0L, -1L, cleanTag)

            try {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "CourseSchedule-App/${BuildConfig.VERSION_NAME}")
                    .build()

                val call = client.newCall(request)
                activeCall = call
                val response = call.execute()

                if (!response.isSuccessful) {
                    val err = "下载失败: HTTP ${response.code}"
                    _downloadState.value = DownloadState.Failed(err, cleanTag)
                    return@launch
                }

                val body = response.body ?: run {
                    val err = "服务器未返回安装包数据"
                    _downloadState.value = DownloadState.Failed(err, cleanTag)
                    return@launch
                }

                val totalBytes = body.contentLength()
                var bytesDownloaded = 0L

                body.byteStream().use { input ->
                    FileOutputStream(tmpFile).use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        var lastProgressTime = 0L

                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            bytesDownloaded += bytesRead

                            val now = System.currentTimeMillis()
                            if (now - lastProgressTime > 120 || (totalBytes > 0 && bytesDownloaded == totalBytes)) {
                                lastProgressTime = now
                                val progressPercent = if (totalBytes > 0) {
                                    ((bytesDownloaded * 100) / totalBytes).toInt().coerceIn(0, 100)
                                } else {
                                    -1
                                }
                                _downloadState.value = DownloadState.Downloading(
                                    progress = progressPercent,
                                    bytesDownloaded = bytesDownloaded,
                                    totalBytes = totalBytes,
                                    versionTag = cleanTag
                                )
                            }
                        }
                        output.flush()
                    }
                }

                // 下载完成，原子重命名 .tmp 为正式 .apk
                if (apkFile.exists()) apkFile.delete()
                val renameSuccess = tmpFile.renameTo(apkFile) || (tmpFile.copyTo(apkFile, overwrite = true).also { tmpFile.delete() }.exists())
                if (renameSuccess) {
                    _downloadState.value = DownloadState.Completed(apkFile, cleanTag)
                    mainHandler.post {
                        Toast.makeText(context, "新版本安装包下载完成，正在调起安装...", Toast.LENGTH_SHORT).show()
                        installApk(context, apkFile)
                    }
                } else {
                    _downloadState.value = DownloadState.Failed("文件保存失败，请检查存储空间", cleanTag)
                }
            } catch (e: Exception) {
                if (activeCall?.isCanceled() == true || e is java.io.InterruptedIOException || e is kotlinx.coroutines.CancellationException) {
                    // 主动取消，不更新为 Failed
                } else {
                    _downloadState.value = DownloadState.Failed(e.message ?: "下载过程中发生异常", cleanTag)
                }
            } finally {
                activeCall = null
                downloadJob = null
            }
        }
    }

    /**
     * 取消当前后台下载任务并清理临时文件。
     */
    fun cancelDownload() {
        val tag = currentVersionTag.orEmpty()
        cancelOngoing()
        try {
            val updatesDir = File(context.cacheDir, "updates")
            if (tag.isNotBlank()) {
                val tmpFile = File(updatesDir, "CourseSchedule-$tag.apk.tmp")
                if (tmpFile.exists()) tmpFile.delete()
            }
        } catch (_: Exception) {}
        _downloadState.value = DownloadState.Idle
    }

    private fun cancelOngoing() {
        try {
            activeCall?.cancel()
        } catch (_: Exception) {}
        activeCall = null

        downloadJob?.cancel()
        downloadJob = null
    }

    fun dismissOrResetState() {
        val current = _downloadState.value
        if (current is DownloadState.Completed || current is DownloadState.Failed) {
            _downloadState.value = DownloadState.Idle
        }
    }

    /**
     * 下载 APK 文件到应用内部缓存并实时回调进度（挂起兼容方法）。
     */
    suspend fun downloadApk(
        url: String,
        versionTag: String,
        onProgress: (DownloadState) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val updatesDir = File(context.cacheDir, "updates")
            if (!updatesDir.exists()) updatesDir.mkdirs()

            val cleanTag = versionTag.ifBlank { "latest" }
            val apkFile = File(updatesDir, "CourseSchedule-$cleanTag.apk")
            val tmpFile = File(updatesDir, "CourseSchedule-$cleanTag.apk.tmp")

            if (tmpFile.exists()) tmpFile.delete()

            withContext(Dispatchers.Main) {
                onProgress(DownloadState.Downloading(0, 0L, -1L, cleanTag))
            }

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "CourseSchedule-App/${BuildConfig.VERSION_NAME}")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                val err = "下载失败: HTTP ${response.code}"
                withContext(Dispatchers.Main) {
                    onProgress(DownloadState.Failed(err, cleanTag))
                }
                return@withContext Result.failure(Exception(err))
            }

            val body = response.body ?: run {
                val err = "服务器未返回安装包数据"
                withContext(Dispatchers.Main) {
                    onProgress(DownloadState.Failed(err, cleanTag))
                }
                return@withContext Result.failure(Exception(err))
            }

            val totalBytes = body.contentLength()
            var bytesDownloaded = 0L

            body.byteStream().use { input ->
                FileOutputStream(tmpFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var lastProgressTime = 0L

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        bytesDownloaded += bytesRead

                        val now = System.currentTimeMillis()
                        if (now - lastProgressTime > 120 || (totalBytes > 0 && bytesDownloaded == totalBytes)) {
                            lastProgressTime = now
                            val progressPercent = if (totalBytes > 0) {
                                ((bytesDownloaded * 100) / totalBytes).toInt().coerceIn(0, 100)
                            } else {
                                -1
                            }
                            withContext(Dispatchers.Main) {
                                onProgress(DownloadState.Downloading(progressPercent, bytesDownloaded, totalBytes, cleanTag))
                            }
                        }
                    }
                    output.flush()
                }
            }

            if (apkFile.exists()) apkFile.delete()
            val renameSuccess = tmpFile.renameTo(apkFile) || (tmpFile.copyTo(apkFile, overwrite = true).also { tmpFile.delete() }.exists())
            if (!renameSuccess) {
                val err = "文件重命名失败"
                withContext(Dispatchers.Main) {
                    onProgress(DownloadState.Failed(err, cleanTag))
                }
                return@withContext Result.failure(Exception(err))
            }

            withContext(Dispatchers.Main) {
                onProgress(DownloadState.Completed(apkFile, cleanTag))
            }
            Result.success(apkFile)
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                onProgress(DownloadState.Failed(e.message ?: "下载过程中发生异常", versionTag))
            }
            Result.failure(e)
        }
    }

    /**
     * 调用系统原生安装器，直接拉起应用更新弹窗，彻底不跳转外部浏览器。
     */
    fun installApk(targetContext: Context, file: File): Boolean {
        if (!file.exists()) {
            Toast.makeText(targetContext, "安装包不存在或已清理", Toast.LENGTH_SHORT).show()
            return false
        }

        return try {
            val uri: Uri = FileProvider.getUriForFile(
                targetContext,
                "${targetContext.packageName}.fileprovider",
                file
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            // Android 8.0+ 检查未知来源安装权限
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!targetContext.packageManager.canRequestPackageInstalls()) {
                    Toast.makeText(targetContext, "请在设置中允许安装未知来源应用以完成更新", Toast.LENGTH_LONG).show()
                    val manageIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${targetContext.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    targetContext.startActivity(manageIntent)
                }
            }

            targetContext.startActivity(intent)
            true
        } catch (e: Exception) {
            Toast.makeText(targetContext, "启动安装器失败: ${e.message}", Toast.LENGTH_LONG).show()
            false
        }
    }
}
