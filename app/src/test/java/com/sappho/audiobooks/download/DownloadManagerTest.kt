package com.sappho.audiobooks.download

import android.content.Context
import com.google.common.truth.Truth.assertThat
import com.sappho.audiobooks.data.remote.SapphoApi
import com.sappho.audiobooks.data.repository.AuthRepository
import com.sappho.audiobooks.domain.model.Audiobook
import com.sappho.audiobooks.domain.model.Chapter
import com.sappho.audiobooks.domain.model.Progress
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.io.File

class DownloadManagerTest {

    private lateinit var downloadManager: DownloadManager
    private val context = mockk<Context>(relaxed = true)
    private val authRepository = mockk<AuthRepository>()
    private val api = mockk<SapphoApi>()
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        // Mock context file directory
        val filesDir = mockk<File> {
            every { mkdirs() } returns true
            every { exists() } returns true
        }
        every { context.filesDir } returns filesDir

        downloadManager = DownloadManager(context, authRepository, api)
    }

    @Test
    fun `should track download state correctly`() = runTest(testDispatcher) {
        // Given
        val audiobookId = 123

        // When
        downloadManager.updateDownloadStateExternal(audiobookId, DownloadState(
            audiobookId = audiobookId,
            progress = 0.5f,
            isDownloading = true,
            isCompleted = false
        ))

        // Then
        val states = downloadManager.downloadStates.value
        assertThat(states[audiobookId]?.progress).isEqualTo(0.5f)
        assertThat(states[audiobookId]?.isDownloading).isTrue()
    }

    @Test
    fun `should clear download error correctly`() = runTest(testDispatcher) {
        // Given
        val audiobookId = 123
        downloadManager.updateDownloadStateExternal(audiobookId, DownloadState(
            audiobookId = audiobookId,
            progress = 0f,
            isDownloading = false,
            isCompleted = false,
            error = "Download failed"
        ))

        // When
        downloadManager.clearDownloadError(audiobookId)

        // Then
        val states = downloadManager.downloadStates.value
        assertThat(states[audiobookId]?.error).isNull()
    }

    @Test
    fun `should clear all download errors correctly`() = runTest(testDispatcher) {
        // Given
        downloadManager.updateDownloadStateExternal(123, DownloadState(
            audiobookId = 123,
            progress = 0f,
            isDownloading = false,
            isCompleted = false,
            error = "Error 1"
        ))
        downloadManager.updateDownloadStateExternal(456, DownloadState(
            audiobookId = 456,
            progress = 0f,
            isDownloading = false,
            isCompleted = false,
            error = "Error 2"
        ))

        // When
        downloadManager.clearAllDownloadErrors()

        // Then
        val states = downloadManager.downloadStates.value
        assertThat(states[123]?.error).isNull()
        assertThat(states[456]?.error).isNull()
    }

    @Test
    fun `should save offline progress correctly`() = runTest(testDispatcher) {
        // Given
        val audiobookId = 123
        val position = 1500

        // When
        downloadManager.saveOfflineProgress(audiobookId, position)

        // Then
        val pendingProgress = downloadManager.pendingProgress.value
        assertThat(pendingProgress[audiobookId]?.position).isEqualTo(position)
        assertThat(downloadManager.hasPendingProgress()).isTrue()
        assertThat(downloadManager.getPendingProgressCount()).isEqualTo(1)
    }

    @Test
    fun `should clear pending progress correctly`() = runTest(testDispatcher) {
        // Given
        val audiobookId = 123
        downloadManager.saveOfflineProgress(audiobookId, 1500)

        // When
        downloadManager.clearPendingProgress(audiobookId)

        // Then
        assertThat(downloadManager.hasPendingProgress()).isFalse()
        assertThat(downloadManager.getPendingProgressCount()).isEqualTo(0)
    }

    @Test
    fun `should check download status correctly`() {
        // Given
        val audiobook = createSampleAudiobook(123)
        downloadManager.saveDownloadedBook(
            audiobook = audiobook,
            filePath = "/path/to/file.m4b",
            fileSize = 1000000L,
            chapters = emptyList()
        )

        // When & Then
        assertThat(downloadManager.isDownloaded(123)).isTrue()
        assertThat(downloadManager.isDownloaded(456)).isFalse()
        assertThat(downloadManager.getLocalFilePath(123)).isEqualTo("/path/to/file.m4b")
        assertThat(downloadManager.getLocalFilePath(456)).isNull()
    }

    @Test
    fun `should delete download correctly`() = runTest(testDispatcher) {
        // Given
        val audiobook = createSampleAudiobook(123)
        val mockFile = mockk<File> {
            every { exists() } returns true
            every { delete() } returns true
        }
        
        downloadManager.saveDownloadedBook(
            audiobook = audiobook,
            filePath = "/path/to/file.m4b",
            fileSize = 1000000L,
            chapters = emptyList()
        )

        // When
        val result = downloadManager.deleteDownload(123)

        // Then
        assertThat(result).isTrue()
        assertThat(downloadManager.isDownloaded(123)).isFalse()
    }

    private fun createSampleAudiobook(id: Int): Audiobook {
        return Audiobook(
            id = id,
            title = "Test Audiobook",
            author = "Test Author",
            duration = 3600,
            coverImage = null,
            description = null,
            progress = Progress(position = 0, completed = 0),
            isMultiFile = 0,
            narrator = null,
            series = null,
            seriesPosition = null,
            genre = null,
            tags = null,
            publishYear = null,
            copyrightYear = null,
            publisher = null,
            isbn = null,
            asin = null,
            language = null,
            rating = null,
            subtitle = null,
            abridged = null,
            fileCount = 1,
            createdAt = "2024-01-01"
        )
    }
}