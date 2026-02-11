package com.sappho.audiobooks.presentation.player

import android.app.Application
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.google.common.truth.Truth.assertThat
import com.sappho.audiobooks.data.remote.SapphoApi
import com.sappho.audiobooks.data.repository.AuthRepository
import com.sappho.audiobooks.domain.model.Audiobook
import com.sappho.audiobooks.service.PlayerState
import com.sappho.audiobooks.download.DownloadManager
import com.sappho.audiobooks.cast.CastHelper
import com.sappho.audiobooks.cast.CastManager
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
class PlayerViewModelTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var application: Application
    private lateinit var api: SapphoApi
    private lateinit var authRepository: AuthRepository
    private lateinit var sharedPlayerState: PlayerState
    private lateinit var downloadManager: DownloadManager
    private lateinit var castHelper: CastHelper
    private lateinit var castManager: CastManager
    private lateinit var viewModel: PlayerViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        application = mockk(relaxed = true)
        api = mockk(relaxed = true)
        authRepository = mockk(relaxed = true)
        sharedPlayerState = mockk(relaxed = true)
        downloadManager = mockk(relaxed = true)
        castHelper = mockk(relaxed = true)
        castManager = mockk(relaxed = true)

        every { authRepository.getServerUrlSync() } returns "https://test.com"

        viewModel = PlayerViewModel(
            application = application,
            api = api,
            authRepository = authRepository,
            sharedPlayerState = sharedPlayerState,
            downloadManager = downloadManager,
            castHelper = castHelper,
            castManager = castManager
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `should initialize with server URL from auth repository`() = runTest {
        // Then
        assertThat(viewModel.serverUrl.first()).isEqualTo("https://test.com")
    }

    @Test
    fun `should load audiobook details successfully`() = runTest {
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

        // When
        viewModel.loadAudiobookDetails(1)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertThat(viewModel.audiobook.first()).isEqualTo(audiobook)
    }

    @Test
    fun `should handle loading error and fall back to offline data`() = runTest {
        // Given
        coEvery { api.getAudiobook(1) } throws Exception("Network error")
        every { downloadManager.getDownloadedBook(1) } returns null

        // When
        viewModel.loadAudiobookDetails(1)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertThat(viewModel.audiobook.first()).isNull()
    }

    @Test
    fun `should load chapters successfully`() = runTest {
        // Given
        val chapters = listOf(
            mockk<com.sappho.audiobooks.domain.model.Chapter>(relaxed = true)
        )
        coEvery { api.getChapters(1) } returns Response.success(chapters)

        // When
        viewModel.loadChapters(1)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertThat(viewModel.chapters.first()).isEqualTo(chapters)
    }
}