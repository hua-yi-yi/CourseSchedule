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
            totalBytes = 10 * 1024 * 1024L
        )
        assertEquals(50, state.progress)
        assertEquals(5242880L, state.bytesDownloaded)
        assertEquals(10485760L, state.totalBytes)
    }

    @Test
    fun `download state completed carries valid file`() {
        val tempFile = File.createTempFile("test_update", ".apk")
        tempFile.deleteOnExit()

        val state = DownloadState.Completed(tempFile)
        assertEquals(tempFile.absolutePath, state.file.absolutePath)
        assertTrue(state.file.exists())
    }

    @Test
    fun `download state failed carries error message`() {
        val state = DownloadState.Failed("网络连接超时")
        assertEquals("网络连接超时", state.error)
    }
}
