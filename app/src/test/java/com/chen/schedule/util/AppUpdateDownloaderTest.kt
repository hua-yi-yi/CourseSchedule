package com.chen.schedule.util

import com.chen.schedule.util.update.DownloadState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AppUpdateDownloaderTest {

    @Test
    fun `download state handles downloading progress calculations correctly`() {
        val state = DownloadState.Downloading(
            progress = 50,
            bytesDownloaded = 5 * 1024 * 1024L,
            totalBytes = 10 * 1024 * 1024L,
            versionTag = "v1.2.32"
        )
        assertEquals(50, state.progress)
        assertEquals(5242880L, state.bytesDownloaded)
        assertEquals(10485760L, state.totalBytes)
        assertEquals("v1.2.32", state.versionTag)
    }

    @Test
    fun `download state completed carries valid file and version tag`() {
        val tempFile = File.createTempFile("test_update", ".apk")
        tempFile.deleteOnExit()

        val state = DownloadState.Completed(tempFile, "v1.2.32")
        assertEquals(tempFile.absolutePath, state.file.absolutePath)
        assertTrue(state.file.exists())
        assertEquals("v1.2.32", state.versionTag)
    }

    @Test
    fun `download state failed carries error message and version tag`() {
        val state = DownloadState.Failed("网络连接超时", "v1.2.32")
        assertEquals("网络连接超时", state.error)
        assertEquals("v1.2.32", state.versionTag)
    }

    @Test
    fun `download state idle object equality and default tag handling`() {
        val idle1 = DownloadState.Idle
        val idle2 = DownloadState.Idle
        assertEquals(idle1, idle2)

        val defaultTagDownloading = DownloadState.Downloading(0, 0L, -1L)
        assertEquals("", defaultTagDownloading.versionTag)
    }
}
