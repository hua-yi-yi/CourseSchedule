package com.chen.schedule.util.update

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import java.io.File
import java.io.IOException
import java.util.zip.CRC32
import java.util.zip.ZipFile
import kotlin.coroutines.resume

/** 生产和测试共用的 HTTP 边界，取消协程会立即关闭同一个 OkHttp Call。 */
internal class ApkDownloadTransport(private val client: OkHttpClient) {
    suspend fun download(url: String, target: File): Result<Long> =
        download(client.newCall(Request.Builder().url(url).build()), target)

    suspend fun download(
        call: Call,
        target: File,
        validate: (File) -> Boolean = { true },
        onProgress: (Long, Long) -> Unit = { _, _ -> }
    ): Result<Long> = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { call.cancel(); target.delete() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, error: IOException) {
                target.delete()
                if (continuation.isActive) continuation.resume(Result.failure(error))
            }
            override fun onResponse(call: Call, response: Response) {
                val result = runCatching {
                    response.use {
                        if (!response.isSuccessful) throw IOException("下载失败: HTTP ${response.code}")
                        val body = response.body ?: throw IOException("服务器未返回安装包")
                        val total = body.contentLength()
                        var bytes = 0L
                        body.byteStream().use { input ->
                            target.outputStream().use { output ->
                                val buffer = ByteArray(8192)
                                while (true) {
                                    if (!continuation.isActive) throw IOException("下载已取消")
                                    val count = input.read(buffer)
                                    if (count < 0) break
                                    output.write(buffer, 0, count)
                                    bytes += count
                                    onProgress(bytes, total)
                                }
                            }
                        }
                        if (bytes == 0L || (total >= 0 && total != bytes)) throw IOException("安装包下载不完整")
                        if (!validate(target)) throw IOException("安装包损坏或包名、版本、签名不匹配")
                        bytes
                    }
                }
                if (result.isFailure || !continuation.isActive) target.delete()
                if (continuation.isActive) continuation.resume(result)
            }
        })
    }
}

/** 检查 ZIP 各条目的长度/CRC；安装时仍由系统执行最终签名验证。 */
internal fun hasIntactApkZip(file: File): Boolean = runCatching {
    ZipFile(file).use { zip ->
        if (zip.getEntry("AndroidManifest.xml") == null) return false
        val entries = zip.entries()
        var totalExpanded = 0L
        val buffer = ByteArray(8192)
        while (entries.hasMoreElements()) {
            val entry = entries.nextElement()
            if (entry.isDirectory) continue
            val crc = CRC32()
            var size = 0L
            zip.getInputStream(entry).use { input ->
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    size += count
                    totalExpanded += count
                    if (totalExpanded > 512L * 1024 * 1024) return false
                    crc.update(buffer, 0, count)
                }
            }
            if (size != entry.size || crc.value != entry.crc) return false
        }
        true
    }
}.getOrDefault(false)
