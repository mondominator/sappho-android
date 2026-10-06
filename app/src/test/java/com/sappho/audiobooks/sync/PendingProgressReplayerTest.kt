package com.sappho.audiobooks.sync

import com.google.common.truth.Truth.assertThat
import com.google.gson.Gson
import com.sappho.audiobooks.data.remote.ProgressUpdateRequest
import com.sappho.audiobooks.data.remote.SapphoApi
import com.sappho.audiobooks.download.DownloadManager
import com.sappho.audiobooks.download.PendingProgress
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Test
import retrofit2.Response
import java.io.IOException

class PendingProgressReplayerTest {

    private val api = mockk<SapphoApi>()
    private val downloadManager = mockk<DownloadManager>(relaxed = true)
    private val replayer = PendingProgressReplayer(api, downloadManager)
    private val entry = PendingProgress(audiobookId = 7, position = 1200, timestamp = 1L, accountKey = "s#1")

    @Test
    fun `replays are sent with isReplay true so the server guards against stale positions`() = runTest {
        every { downloadManager.getPendingProgressList() } returns listOf(entry)
        val sent = slot<ProgressUpdateRequest>()
        coEvery { api.updateProgress(7, capture(sent)) } returns Response.success(Unit)

        replayer.replayAll()

        val json = Gson().toJson(sent.captured)
        assertThat(json).contains("\"isReplay\":true")
        assertThat(sent.captured.position).isEqualTo(1200)
    }

    @Test
    fun `entry is removed only after the server confirms it`() = runTest {
        every { downloadManager.getPendingProgressList() } returns listOf(entry)
        coEvery { api.updateProgress(any(), any()) } returns Response.success(Unit)

        val result = replayer.replayAll()

        verify { downloadManager.clearPendingProgressIfUnchanged(entry) }
        assertThat(result.needsRetry).isFalse()
    }

    @Test
    fun `network failure keeps the entry and asks for a retry`() = runTest {
        every { downloadManager.getPendingProgressList() } returns listOf(entry)
        coEvery { api.updateProgress(any(), any()) } throws IOException("offline")

        val result = replayer.replayAll()

        verify(exactly = 0) { downloadManager.clearPendingProgressIfUnchanged(any()) }
        verify(exactly = 0) { downloadManager.clearPendingProgress(any()) }
        assertThat(result.needsRetry).isTrue()
    }

    @Test
    fun `server error keeps the entry`() = runTest {
        every { downloadManager.getPendingProgressList() } returns listOf(entry)
        coEvery { api.updateProgress(any(), any()) } returns Response.error(500, "".toResponseBody())

        val result = replayer.replayAll()

        verify(exactly = 0) { downloadManager.clearPendingProgressIfUnchanged(any()) }
        assertThat(result.retryable).isEqualTo(1)
    }

    @Test
    fun `permanent rejection drops the entry so it cannot block the queue`() = runTest {
        every { downloadManager.getPendingProgressList() } returns listOf(entry)
        coEvery { api.updateProgress(any(), any()) } returns Response.error(404, "".toResponseBody())

        val result = replayer.replayAll()

        verify { downloadManager.clearPendingProgressIfUnchanged(entry) }
        assertThat(result.dropped).isEqualTo(1)
        assertThat(result.needsRetry).isFalse()
    }

    @Test
    fun `auth failures are retryable, not dropped`() {
        assertThat(PendingProgressReplayer.isPermanentFailure(401)).isFalse()
        assertThat(PendingProgressReplayer.isPermanentFailure(429)).isFalse()
        assertThat(PendingProgressReplayer.isPermanentFailure(400)).isTrue()
    }
}
