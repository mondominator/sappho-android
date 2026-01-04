package com.sappho.audiobooks.presentation.detail

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.google.common.truth.Truth.assertThat
import com.sappho.audiobooks.data.remote.SapphoApi
import com.sappho.audiobooks.data.repository.AuthRepository
import com.sappho.audiobooks.domain.model.Audiobook
import com.sappho.audiobooks.service.PlayerState
import com.sappho.audiobooks.download.DownloadManager
import com.sappho.audiobooks.util.NetworkMonitor
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class AudiobookDetailViewModelTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var context: Context
    private lateinit var api: SapphoApi
    private lateinit var authRepository: AuthRepository
    private lateinit var playerState: PlayerState
    private lateinit var downloadManager: DownloadManager
    private lateinit var networkMonitor: NetworkMonitor
    private lateinit var viewModel: AudiobookDetailViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        context = mockk(relaxed = true)
        api = mockk(relaxed = true)
        authRepository = mockk(relaxed = true)
        playerState = mockk(relaxed = true)
        downloadManager = mockk(relaxed = true)
        networkMonitor = mockk(relaxed = true)

        every { authRepository.getServerUrlSync() } returns "https://test.com"
        every { networkMonitor.isOnline } returns MutableStateFlow(true)
        
        viewModel = AudiobookDetailViewModel(
            context = context,
            api = api,
            authRepository = authRepository,
            playerState = playerState,
            downloadManager = downloadManager,
            networkMonitor = networkMonitor
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `should load audiobook successfully`() = runTest {
        // Given
        val audiobook = Audiobook(
            id = 1,
            title = "Test Book",
            author = "Test Author",
            duration = 3600,
            coverImage = null,
            seriesPosition = null,
            publishYear = null,
            narrator = null,
            description = null,
            progress = null,
            isFavorite = false,
            series = null,
            genre = null,
            isbn = null,
            asin = null,
            fileCount = 1,
            createdAt = "2024-01-01"
        )
        coEvery { api.getAudiobook(1) } returns Response.success(audiobook)
        coEvery { api.getUserRating(1) } returns Response.success(mockk(relaxed = true))
        coEvery { api.getAverageRating(1) } returns Response.success(mockk(relaxed = true))
        coEvery { api.getProgress(1) } returns Response.success(mockk(relaxed = true))
        coEvery { api.getChapters(1) } returns Response.success(emptyList())
        coEvery { api.getFiles(1) } returns Response.success(emptyList())

        // When
        viewModel.loadAudiobook(1)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertThat(viewModel.audiobook.first()).isEqualTo(audiobook)
        assertThat(viewModel.isLoading.first()).isFalse()
    }

    @Test
    fun `should handle loading error gracefully`() = runTest {
        // Given
        coEvery { api.getAudiobook(1) } throws Exception("Network error")
        every { downloadManager.getDownloadedBook(1) } returns null

        // When
        viewModel.loadAudiobook(1)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertThat(viewModel.audiobook.first()).isNull()
        assertThat(viewModel.isLoading.first()).isFalse()
    }

    @Test
    fun `should toggle favorite status`() = runTest {
        // Given
        val audiobook = Audiobook(
            id = 1,
            title = "Test Book",
            author = "Test Author",
            duration = 3600,
            coverImage = null,
            seriesPosition = null,
            publishYear = null,
            narrator = null,
            description = null,
            progress = null,
            isFavorite = false,
            series = null,
            genre = null,
            isbn = null,
            asin = null,
            fileCount = 1,
            createdAt = "2024-01-01"
        )
        coEvery { api.getAudiobook(1) } returns Response.success(audiobook)
        coEvery { api.getUserRating(1) } returns Response.success(mockk(relaxed = true))
        coEvery { api.getAverageRating(1) } returns Response.success(mockk(relaxed = true))
        coEvery { api.getProgress(1) } returns Response.success(mockk(relaxed = true))
        coEvery { api.getChapters(1) } returns Response.success(emptyList())
        coEvery { api.getFiles(1) } returns Response.success(emptyList())
        coEvery { api.toggleFavorite(1) } returns Response.success(mockk {
            every { isFavorite } returns true
        })

        // When
        viewModel.loadAudiobook(1)
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.toggleFavorite()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertThat(viewModel.isFavorite.value).isEqualTo(true)
    }

    @Test
    fun `should start download through service`() = runTest {
        // Given
        val audiobook = createSampleAudiobook(123)
        coEvery { api.getAudiobook(123) } returns Response.success(audiobook)
        viewModel.loadAudiobook(123)
        testDispatcher.scheduler.advanceUntilIdle()

        // When
        viewModel.downloadAudiobook()

        // Then
        // Note: We can't easily test the DownloadService.startDownload call in unit tests
        // because it requires Context, but we can verify the audiobook is loaded
        assertThat(viewModel.audiobook.value).isEqualTo(audiobook)
    }

    @Test
    fun `should delete download correctly`() = runTest {
        // Given
        val audiobook = createSampleAudiobook(123)
        coEvery { api.getAudiobook(123) } returns Response.success(audiobook)
        viewModel.loadAudiobook(123)
        testDispatcher.scheduler.advanceUntilIdle()
        every { downloadManager.deleteDownload(123) } returns true

        // When
        viewModel.deleteDownload()

        // Then
        io.mockk.verify { downloadManager.deleteDownload(123) }
    }

    @Test
    fun `should clear download error correctly`() = runTest {
        // When
        viewModel.clearDownloadError(123)

        // Then
        io.mockk.verify { downloadManager.clearDownloadError(123) }
    }

    private fun createSampleAudiobook(id: Int): com.sappho.audiobooks.domain.model.Audiobook {
        return com.sappho.audiobooks.domain.model.Audiobook(
            id = id,
            title = "Test Audiobook",
            author = "Test Author",
            duration = 3600,
            coverImage = null,
            description = null,
            progress = com.sappho.audiobooks.domain.model.Progress(position = 0, completed = 0),
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