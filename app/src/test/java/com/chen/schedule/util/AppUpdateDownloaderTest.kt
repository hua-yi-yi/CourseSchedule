package com.chen.schedule.util

import com.chen.schedule.util.update.isCompatibleApkVersion
import com.chen.schedule.util.update.hasIntactApkZip
import com.chen.schedule.util.update.ApkDownloadTransport
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.InterruptedIOException
import java.io.File
import java.util.concurrent.TimeUnit

class AppUpdateDownloaderTest {

    @Test
    fun `cache validation accepts unknown expected version but rejects mismatched release`() {
        assertTrue(isCompatibleApkVersion("1.2.33", null))
        assertTrue(isCompatibleApkVersion("1.2.33", "1.2.33"))
        assertFalse(isCompatibleApkVersion("1.2.32", "1.2.33"))
    }

    @Test
    fun `transport closes HTTP errors and writes successful response`() = runBlocking<Unit> {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(503).setBody("busy"))
            server.enqueue(MockResponse().setResponseCode(200).setBody("apk-bytes"))
            val transport = ApkDownloadTransport(OkHttpClient())
            val target = File.createTempFile("transport", ".apk")
            assertTrue(transport.download(server.url("/error").toString(), target).isFailure)
            assertTrue(transport.download(server.url("/ok").toString(), target).isSuccess)
            assertTrue(target.readText() == "apk-bytes")
            target.delete()
        }
    }

    @Test
    fun `transport cancellation removes partial response`() = runBlocking<Unit> {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("x".repeat(1024 * 1024)).throttleBody(1, 5, TimeUnit.MILLISECONDS))
            val target = File.createTempFile("transport", ".apk")
            val job: Job = launch {
                ApkDownloadTransport(OkHttpClient()).download(server.url("/slow").toString(), target)
            }
            delay(30)
            job.cancel()
            job.join()
            assertTrue(!target.exists() || target.length() == 0L)
            target.delete()
        }
    }

    @Test
    fun `transport reports read timeout as failure`() = runBlocking<Unit> {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBodyDelay(250, TimeUnit.MILLISECONDS).setBody("late"))
            val client = OkHttpClient.Builder().readTimeout(30, TimeUnit.MILLISECONDS).build()
            val target = File.createTempFile("transport", ".apk")
            val result = ApkDownloadTransport(client).download(server.url("/timeout").toString(), target)
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is InterruptedIOException)
            assertTrue(!target.exists() || target.length() == 0L)
            target.delete()
        }
    }

    @Test
    fun `invalid package response is rejected before success and removed`() = runBlocking<Unit> {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("<html>mirror error</html>"))
            val client = OkHttpClient()
            val target = File.createTempFile("invalid", ".apk")
            val call = client.newCall(okhttp3.Request.Builder().url(server.url("/fake.apk")).build())
            val result = ApkDownloadTransport(client).download(call, target, ::hasIntactApkZip)
            assertTrue(result.isFailure)
            assertFalse(target.exists())
        }
    }

    @Test
    fun `cancelled slow response cannot overwrite restarted download`() = runBlocking<Unit> {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("old".repeat(10000)).throttleBody(3, 20, TimeUnit.MILLISECONDS))
            server.enqueue(MockResponse().setBody("new package"))
            val transport = ApkDownloadTransport(OkHttpClient())
            val old = File.createTempFile("old-task", ".tmp")
            val fresh = File.createTempFile("new-task", ".tmp")
            val started = kotlinx.coroutines.CompletableDeferred<Unit>()
            val client = OkHttpClient()
            val job = launch {
                transport.download(client.newCall(okhttp3.Request.Builder().url(server.url("/old")).build()), old) { _, _ ->
                    started.complete(Unit)
                }
            }
            kotlinx.coroutines.withTimeout(5000) { started.await() }
            job.cancel()
            assertTrue(transport.download(server.url("/new").toString(), fresh).isSuccess)
            job.join()
            assertTrue(fresh.readText() == "new package")
            assertFalse(old.exists())
            fresh.delete()
        }
    }

    @Test
    fun `archive validation rejects truncated and corrupted entry data`() {
        val target = File.createTempFile("archive", ".apk")
        try {
            val bytes = "manifest fixture".toByteArray()
            java.util.zip.ZipOutputStream(target.outputStream()).use { zip ->
                val entry = java.util.zip.ZipEntry("AndroidManifest.xml").apply {
                    method = java.util.zip.ZipEntry.STORED
                    size = bytes.size.toLong()
                    compressedSize = size
                    crc = java.util.zip.CRC32().also { it.update(bytes) }.value
                }
                zip.putNextEntry(entry)
                zip.write(bytes)
                zip.closeEntry()
            }
            assertTrue(hasIntactApkZip(target))
            val original = target.readBytes()
            val corrupted = original.copyOf()
            val offset = 30 + "AndroidManifest.xml".length
            corrupted[offset] = (corrupted[offset].toInt() xor 1).toByte()
            target.writeBytes(corrupted)
            assertFalse(hasIntactApkZip(target))
            target.writeBytes(original.copyOf(original.size / 2))
            assertFalse(hasIntactApkZip(target))
        } finally { target.delete() }
    }
}
