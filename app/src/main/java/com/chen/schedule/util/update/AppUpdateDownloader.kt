package com.chen.schedule.util.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import com.chen.schedule.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    data class Downloading(val progress: Int, val bytesDownloaded: Long, val totalBytes: Long) : DownloadState()
    data class Completed(val file: File) : DownloadState()
    data class Failed(val error: String) : DownloadState()
}

/**
 * 应用内静默下载引擎与原生安装器。
 * 直接在应用内下载 APK 并拉起系统安装界面，避免跳转外部浏览器。
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

    /**
     * 下载 APK 文件到应用内部缓存并实时回调进度。
     *
     * @param url 下载链接 (官方直连或国内加速镜像)
     * @param versionTag 版本标识，如 "v1.2.27"
     * @param onProgress 进度回调 (在主线程派发)
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

            if (apkFile.exists()) apkFile.delete()

            withContext(Dispatchers.Main) {
                onProgress(DownloadState.Downloading(0, 0L, -1L))
            }

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "CourseSchedule-App/${BuildConfig.VERSION_NAME}")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                val err = "下载失败: HTTP ${response.code}"
                withContext(Dispatchers.Main) {
                    onProgress(DownloadState.Failed(err))
                }
                return@withContext Result.failure(Exception(err))
            }

            val body = response.body ?: run {
                val err = "服务器未返回安装包数据"
                withContext(Dispatchers.Main) {
                    onProgress(DownloadState.Failed(err))
                }
                return@withContext Result.failure(Exception(err))
            }

            val totalBytes = body.contentLength()
            var bytesDownloaded = 0L

            body.byteStream().use { input ->
                FileOutputStream(apkFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var lastProgressTime = 0L

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        bytesDownloaded += bytesRead

                        val now = System.currentTimeMillis()
                        if (now - lastProgressTime > 120 || bytesDownloaded == totalBytes) {
                            lastProgressTime = now
                            val progressPercent = if (totalBytes > 0) {
                                ((bytesDownloaded * 100) / totalBytes).toInt().coerceIn(0, 100)
                            } else {
                                -1
                            }
                            withContext(Dispatchers.Main) {
                                onProgress(DownloadState.Downloading(progressPercent, bytesDownloaded, totalBytes))
                            }
                        }
                    }
                    output.flush()
                }
            }

            withContext(Dispatchers.Main) {
                onProgress(DownloadState.Completed(apkFile))
            }
            Result.success(apkFile)
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                onProgress(DownloadState.Failed(e.message ?: "下载过程中发生异常"))
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
