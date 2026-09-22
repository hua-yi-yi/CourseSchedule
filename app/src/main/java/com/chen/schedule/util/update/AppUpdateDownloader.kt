package com.chen.schedule.util.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import com.chen.schedule.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.*
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

internal fun isCompatibleApkVersion(actual: String?, expected: String?): Boolean =
    expected.isNullOrBlank() || actual == expected.removePrefix("v")

sealed class DownloadState {
    object Idle : DownloadState()
    data class Downloading(val progress: Int, val bytesDownloaded: Long, val totalBytes: Long,
        val versionTag: String = "") : DownloadState()
    data class Completed(val file: File, val versionTag: String = "") : DownloadState()
    data class Failed(val error: String, val versionTag: String = "") : DownloadState()
}

/** 页面切换不取消下载；进程终止后需要重新下载未完成的文件。 */
@Singleton
class AppUpdateDownloader @Inject constructor(@ApplicationContext private val context: Context) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(120, TimeUnit.SECONDS)
        .followRedirects(true).build()
    private val transport = ApkDownloadTransport(client)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val lock = Any()
    private var generation = 0L
    private var activeJob: Job? = null
    private var activeCall: Call? = null
    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

    @Volatile var currentVersionTag: String? = null
        private set
    @Volatile var currentDownloadUrl: String? = null
        private set
    @Volatile var currentRelease: AppReleaseInfo? = null
        private set

    private fun cleanTag(tag: String) = tag.ifBlank { "latest" }
        .also { require(it.matches(Regex("[A-Za-z0-9._-]+"))) { "版本标识无效" } }

    private fun apkFile(tag: String) = File(File(context.cacheDir, "updates"), "CourseSchedule-$tag.apk")

    fun checkExistingCompletedApk(versionTag: String): File? = runCatching {
        val tag = cleanTag(versionTag)
        apkFile(tag).takeIf { isValidApk(it, tag) }
    }.getOrNull()

    fun syncExistingDownload(versionTag: String): Boolean {
        val existing = checkExistingCompletedApk(versionTag) ?: return false
        return synchronized(lock) {
            if (activeJob?.isActive == true) return@synchronized false
            currentVersionTag = cleanTag(versionTag)
            _downloadState.value = DownloadState.Completed(existing, currentVersionTag!!)
            true
        }
    }

    fun startDownload(url: String, versionTag: String, release: AppReleaseInfo? = null) {
        val tag = runCatching { cleanTag(versionTag) }.getOrElse {
            synchronized(lock) {
                cancelLocked()
                _downloadState.value = DownloadState.Failed("版本标识无效", versionTag)
            }
            return
        }
        synchronized(lock) {
            if (activeJob?.isActive == true && currentVersionTag == tag) return
            cancelLocked()
            val owner = generation
            currentVersionTag = tag
            currentDownloadUrl = url
            currentRelease = release
            _downloadState.value = DownloadState.Downloading(0, 0, -1, tag)
            // LAZY 确保任务启动前 activeJob 已发布。
            val job = scope.launch(start = CoroutineStart.LAZY) {
                var temp: File? = null
                try {
                    val destination = apkFile(tag)
                    if (!isValidApk(destination, tag)) {
                        check(destination.parentFile!!.isDirectory || destination.parentFile!!.mkdirs()) {
                            "无法创建下载目录"
                        }
                        val partial = File(destination.parentFile, "${destination.name}.${UUID.randomUUID()}.tmp")
                        temp = partial
                        val request = Request.Builder().url(url)
                            .header("User-Agent", "CourseSchedule-App/${BuildConfig.VERSION_NAME}").build()
                        val call = client.newCall(request)
                        synchronized(lock) {
                            if (generation != owner) throw CancellationException()
                            activeCall = call
                        }
                        var lastProgress = 0L
                        transport.download(call, partial, validate = { isValidApk(it, tag) }) { bytes, total ->
                            val now = System.nanoTime()
                            if (now - lastProgress >= 120_000_000 || bytes == total) {
                                lastProgress = now
                                synchronized(lock) {
                                    if (generation == owner) {
                                        val percent = if (total > 0) ((bytes * 100 / total).toInt()).coerceIn(0, 100) else -1
                                        _downloadState.value = DownloadState.Downloading(percent, bytes, total, tag)
                                    }
                                }
                            }
                        }.getOrThrow()
                        synchronized(lock) {
                            if (generation != owner) throw CancellationException()
                            // 提交和任务所有权检查保持原子。
                            if (destination.exists() && !destination.delete()) error("无法替换旧安装包")
                            check(partial.renameTo(destination)) { "文件保存失败，请检查存储空间" }
                        }
                    }
                    synchronized(lock) {
                        if (generation != owner) throw CancellationException()
                        _downloadState.value = DownloadState.Completed(destination, tag)
                    }
                    mainHandler.post {
                        synchronized(lock) {
                            if (generation == owner && _downloadState.value is DownloadState.Completed) {
                                installApk(context, destination)
                            }
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    synchronized(lock) {
                        if (generation == owner) {
                            val message = if (error is java.io.InterruptedIOException) "下载超时，请重试"
                                else error.message ?: "下载失败，请重试"
                            _downloadState.value = DownloadState.Failed(message, tag)
                        }
                    }
                } finally {
                    temp?.delete()
                    synchronized(lock) {
                        if (generation == owner) {
                            activeCall = null
                            activeJob = null
                        }
                    }
                }
            }
            activeJob = job
            job.start()
        }
    }

    /** 调用时必须持有 lock；旧任务只清理自己的 UUID 临时文件。 */
    private fun cancelLocked() {
        generation++
        activeCall?.cancel()
        activeJob?.cancel()
        activeCall = null
        activeJob = null
    }

    fun cancelDownload() = synchronized(lock) {
        cancelLocked()
        _downloadState.value = DownloadState.Idle
    }

    fun dismissOrResetState() = synchronized(lock) {
        if (_downloadState.value is DownloadState.Completed || _downloadState.value is DownloadState.Failed) {
            generation++
            _downloadState.value = DownloadState.Idle
        }
    }

    @Suppress("DEPRECATION")
    private fun isValidApk(file: File, tag: String): Boolean {
        if (!hasIntactApkZip(file)) return false
        return try {
            val pm = context.packageManager
            val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES
                else PackageManager.GET_SIGNATURES
            val archive = pm.getPackageArchiveInfo(file.absolutePath, flags) ?: return false
            val installed = pm.getPackageInfo(context.packageName, flags)
            if (archive.packageName != context.packageName) return false
            if (tag != "latest" && !isCompatibleApkVersion(archive.versionName, tag)) return false
            fun code(info: PackageInfo) = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
            if (code(archive) < code(installed)) return false
            if (Build.VERSION.SDK_INT >= 28) {
                val candidate = archive.signingInfo ?: return false
                val current = installed.signingInfo ?: return false
                val newSigners = candidate.apkContentsSigners?.toSet().orEmpty()
                val oldSigners = current.apkContentsSigners?.toSet().orEmpty()
                if (newSigners.isEmpty() || oldSigners.isEmpty()) return false
                if (candidate.hasMultipleSigners() || current.hasMultipleSigners()) newSigners == oldSigners
                else candidate.signingCertificateHistory?.toSet().orEmpty().containsAll(oldSigners)
            } else {
                val candidate = archive.signatures?.toSet().orEmpty()
                candidate.isNotEmpty() && candidate == installed.signatures?.toSet()
            }
        } catch (_: Exception) { false }
    }

    fun installApk(targetContext: Context, file: File): Boolean {
        if (!file.exists()) {
            Toast.makeText(targetContext, "安装包不存在或已清理", Toast.LENGTH_SHORT).show()
            return false
        }
        return try {
            if (!targetContext.packageManager.canRequestPackageInstalls()) {
                Toast.makeText(targetContext, "允许安装后，请返回点击立即安装", Toast.LENGTH_LONG).show()
                targetContext.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${targetContext.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return false
            }
            val uri = FileProvider.getUriForFile(targetContext, "${targetContext.packageName}.fileprovider", file)
            targetContext.startActivity(Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (error: Exception) {
            Toast.makeText(targetContext, "启动安装器失败: ${error.message}", Toast.LENGTH_LONG).show()
            false
        }
    }
}
