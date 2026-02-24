package com.sappho.audiobooks.presentation.player

import android.app.Application
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.google.common.truth.Truth.assertThat
import com.sappho.audiobooks.data.remote.SapphoApi
import com.sappho.audiobooks.data.repository.AuthRepository
import com.sappho.audiobooks.domain.model.Audiobook
import com.sappho.audiobooks.domain.model.Progress
import com.sappho.audiobooks.service.AudioPlaybackService
import com.sappho.audiobooks.service.PlayerState
import com.sappho.audiobooks.download.DownloadManager
import com.sappho.audiobooks.cast.CastHelper
import com.sappho.audiobooks.cast.CastManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
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
        sharedPlayerState = PlayerState()
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

    // --- checkForUpdatedProgress tests ---

    @Test
    fun `checkForUpdatedProgress updates position when service is dead`() = runTest {
        // Given - service is null (dead), local position is 100
        sharedPlayerState.updatePosition(100L)
        val progress = Progress(position = 500, completed = 0)
        coEvery { api.getProgress(1) } returns Response.success(progress)

        // Ensure AudioPlaybackService.instance is null (default in tests)
        mockkObject(AudioPlaybackService.Companion)
        every { AudioPlaybackService.instance } returns null

        // When
        viewModel.checkForUpdatedProgress(1)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then - PlayerState should be updated directly
        assertThat(sharedPlayerState.currentPosition.value).isEqualTo(500L)

        unmockkObject(AudioPlaybackService.Companion)
    }

    @Test
    fun `checkForUpdatedProgress does not interrupt active playback`() = runTest {
        // Given - service is playing, local position is 100
        sharedPlayerState.updatePosition(100L)
        val progress = Progress(position = 500, completed = 0)
        coEvery { api.getProgress(1) } returns Response.success(progress)

        val mockService = mockk<AudioPlaybackService>(relaxed = true)
        every { mockService.isCurrentlyPlaying() } returns true

        mockkObject(AudioPlaybackService.Companion)
        every { AudioPlaybackService.instance } returns mockService

        // When
        viewModel.checkForUpdatedProgress(1)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then - position should NOT be changed, seekTo should NOT be called
        assertThat(sharedPlayerState.currentPosition.value).isEqualTo(100L)
        verify(exactly = 0) { mockService.seekTo(any()) }

        unmockkObject(AudioPlaybackService.Companion)
    }

    @Test
    fun `checkForUpdatedProgress skips completed books`() = runTest {
        // Given - server says book is completed
        sharedPlayerState.updatePosition(100L)
        val progress = Progress(position = 500, completed = 1)
        coEvery { api.getProgress(1) } returns Response.success(progress)

        mockkObject(AudioPlaybackService.Companion)
        every { AudioPlaybackService.instance } returns null

        // When
        viewModel.checkForUpdatedProgress(1)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then - position should NOT be updated
        assertThat(sharedPlayerState.currentPosition.value).isEqualTo(100L)

        unmockkObject(AudioPlaybackService.Companion)
    }

    @Test
    fun `checkForUpdatedProgress handles network failure gracefully`() = runTest {
        // Given - network fails
        sharedPlayerState.updatePosition(100L)
        coEvery { api.getProgress(1) } throws Exception("Network error")

        mockkObject(AudioPlaybackService.Companion)
        every { AudioPlaybackService.instance } returns null

        // When
        viewModel.checkForUpdatedProgress(1)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then - position unchanged, no crash
        assertThat(sharedPlayerState.currentPosition.value).isEqualTo(100L)

        unmockkObject(AudioPlaybackService.Companion)
    }

    @Test
    fun `checkForUpdatedProgress seeks service when paused and position differs`() = runTest {
        // Given - service alive but paused, positions differ by >2s
        sharedPlayerState.updatePosition(100L)
        val progress = Progress(position = 200, completed = 0)
        coEvery { api.getProgress(1) } returns Response.success(progress)

        val mockService = mockk<AudioPlaybackService>(relaxed = true)
        every { mockService.isCurrentlyPlaying() } returns false

        mockkObject(AudioPlaybackService.Companion)
        every { AudioPlaybackService.instance } returns mockService

        // When
        viewModel.checkForUpdatedProgress(1)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then - service should be seeked to server position
        verify { mockService.seekTo(200L) }

        unmockkObject(AudioPlaybackService.Companion)
    }

    // --- togglePlayPauseWithGuard tests ---

    @Test
    fun `togglePlayPauseWithGuard pauses immediately even when stale`() = runTest {
        // Given - service is currently playing (pause should be instant)
        val mockService = mockk<AudioPlaybackService>(relaxed = true)
        every { mockService.isCurrentlyPlaying() } returns true
        every { mockService.togglePlayPause() } returns true

        mockkObject(AudioPlaybackService.Companion)
        every { AudioPlaybackService.instance } returns mockService

        // When
        viewModel.togglePlayPauseWithGuard(1)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then - togglePlayPause called immediately, no API call
        verify { mockService.togglePlayPause() }
        coVerify(exactly = 0) { api.getProgress(any()) }

        unmockkObject(AudioPlaybackService.Companion)
    }

    @Test
    fun `togglePlayPauseWithGuard fetches server progress when stale`() = runTest {
        // Given - lastActiveTimestamp is 0 (stale), service exists but paused
        // sharedPlayerState.lastActiveTimestamp defaults to 0L (stale)
        sharedPlayerState.updatePosition(100L)
        val progress = Progress(position = 500, completed = 0)
        coEvery { api.getProgress(1) } returns Response.success(progress)

        val mockService = mockk<AudioPlaybackService>(relaxed = true)
        every { mockService.isCurrentlyPlaying() } returns false
        every { mockService.togglePlayPause() } returns true

        mockkObject(AudioPlaybackService.Companion)
        every { AudioPlaybackService.instance } returns mockService

        // When
        viewModel.togglePlayPauseWithGuard(1)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then - should have fetched progress and seeked before toggling
        coVerify { api.getProgress(1) }
        verify { mockService.seekTo(500L) }
        verify { mockService.togglePlayPause() }

        unmockkObject(AudioPlaybackService.Companion)
    }

    @Test
    fun `togglePlayPauseWithGuard skips fetch when recently active`() = runTest {
        // Given - recently active (not stale)
        sharedPlayerState.updateLastActiveTimestamp() // sets to current time
        sharedPlayerState.updatePosition(100L)

        val mockService = mockk<AudioPlaybackService>(relaxed = true)
        every { mockService.isCurrentlyPlaying() } returns false
        every { mockService.togglePlayPause() } returns true

        mockkObject(AudioPlaybackService.Companion)
        every { AudioPlaybackService.instance } returns mockService

        // When
        viewModel.togglePlayPauseWithGuard(1)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then - no API call, toggle directly
        coVerify(exactly = 0) { api.getProgress(any()) }
        verify { mockService.togglePlayPause() }

        unmockkObject(AudioPlaybackService.Companion)
    }
}
