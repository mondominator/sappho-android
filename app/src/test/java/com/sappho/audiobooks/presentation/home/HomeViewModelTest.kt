package com.sappho.audiobooks.presentation.home

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.sappho.audiobooks.data.remote.SapphoApi
import com.sappho.audiobooks.data.repository.AuthRepository
import com.sappho.audiobooks.domain.model.Audiobook
import com.sappho.audiobooks.domain.model.Progress
import com.sappho.audiobooks.download.DownloadManager
import com.sappho.audiobooks.download.PendingProgress

import com.sappho.audiobooks.util.NetworkMonitor
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private lateinit var viewModel: HomeViewModel
    private lateinit var api: SapphoApi
    private lateinit var authRepository: AuthRepository
    private lateinit var networkMonitor: NetworkMonitor
    private lateinit var downloadManager: DownloadManager
    private lateinit var syncStatusManager: com.sappho.audiobooks.sync.SyncStatusManager
    
    private val testDispatcher = StandardTestDispatcher()
    private val isOnlineFlow = MutableStateFlow(true)
    
    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        
        api = mockk(relaxed = true)
        authRepository = mockk(relaxed = true)
        networkMonitor = mockk(relaxed = true)
        downloadManager = mockk(relaxed = true)
        syncStatusManager = mockk(relaxed = true)
        
        every { authRepository.getServerUrlSync() } returns "https://test.sappho.com"
        every { authRepository.getTokenSync() } returns "test-token"
        every { networkMonitor.isOnline } returns isOnlineFlow
        every { downloadManager.getPendingProgressList() } returns emptyList()
        every { syncStatusManager.syncStatus } returns MutableStateFlow(com.sappho.audiobooks.sync.SyncStatus())
        
        // Create HomeViewModel with mocked performance monitor
        val performanceMonitor = mockk<com.sappho.audiobooks.util.PerformanceMonitor>(relaxed = true)
        viewModel = HomeViewModel(api, authRepository, downloadManager, networkMonitor, syncStatusManager, performanceMonitor)
    }
    
    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }
    
    @Test
    fun `loads data on initialization when online`() = runTest {
        // Given
        val continueBooks = listOf(createTestBook(1, "Book 1"))
        val recentBooks = listOf(createTestBook(2, "Book 2"))
        val finishedBooks = listOf(createTestBook(3, "Book 3"))
        
        coEvery { api.getInProgress(any()) } returns Response.success(continueBooks)
        coEvery { api.getRecentlyAdded(any()) } returns Response.success(recentBooks)
        coEvery { api.getFinished(any()) } returns Response.success(finishedBooks)
        coEvery { api.getFavorites() } returns Response.success(emptyList())
        
        // When - ViewModel already initialized
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then
        viewModel.inProgress.test {
            assertThat(awaitItem()).isEqualTo(continueBooks)
        }
        
        viewModel.recentlyAdded.test {
            assertThat(awaitItem()).isEqualTo(recentBooks)
        }
        
        viewModel.finished.test {
            assertThat(awaitItem()).isEqualTo(finishedBooks)
        }
    }
    
    @Test
    fun `does not load data when offline`() = runTest {
        // Given
        isOnlineFlow.value = false
        
        // When
        val performanceMonitor = mockk<com.sappho.audiobooks.util.PerformanceMonitor>(relaxed = true)
        val newViewModel = HomeViewModel(api, authRepository, downloadManager, networkMonitor, syncStatusManager, performanceMonitor)
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then - API should not be called
        coVerify(exactly = 0) { api.getInProgress(any()) }
        coVerify(exactly = 0) { api.getRecentlyAdded(any()) }
        coVerify(exactly = 0) { api.getFinished(any()) }
    }
    
    @Test
    fun `refresh loads all data`() = runTest {
        // Given
        val continueBooks = listOf(createTestBook(1, "Updated Book"))
        coEvery { api.getInProgress(any()) } returns Response.success(continueBooks)
        coEvery { api.getRecentlyAdded(any()) } returns Response.success(emptyList())
        coEvery { api.getFinished(any()) } returns Response.success(emptyList())
        coEvery { api.getFavorites() } returns Response.success(emptyList())
        
        // When
        viewModel.refresh()
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then
        viewModel.inProgress.test {
            assertThat(awaitItem()).isEqualTo(continueBooks)
        }
    }
    

    
    @Test
    fun `syncs pending progress when coming back online`() = runTest {
        // Given
        val pendingProgress = PendingProgress(
            audiobookId = 1,
            position = 300,
            timestamp = System.currentTimeMillis()
        )
        
        every { downloadManager.getPendingProgressList() } returns listOf(pendingProgress)
        every { syncStatusManager.triggerSync() } returns Unit
        coEvery { api.updateProgress(any(), any()) } returns Response.success(Unit)
        coEvery { api.getFavorites() } returns Response.success(emptyList())
        coEvery { api.getInProgress(any()) } returns Response.success(emptyList())
        coEvery { api.getRecentlyAdded(any()) } returns Response.success(emptyList())
        coEvery { api.getFinished(any()) } returns Response.success(emptyList())
        coEvery { api.getUpNext(any()) } returns Response.success(emptyList())
        
        // Start offline
        isOnlineFlow.value = false
        
        // When - Create ViewModel while offline
        val performanceMonitor = mockk<com.sappho.audiobooks.util.PerformanceMonitor>(relaxed = true)
        val newViewModel = HomeViewModel(api, authRepository, downloadManager, networkMonitor, syncStatusManager, performanceMonitor)
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Verify no API calls made while offline
        coVerify(exactly = 0) { api.updateProgress(any(), any()) }
        
        // When - Come back online
        isOnlineFlow.value = true
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then - Should trigger sync via SyncStatusManager
        io.mockk.verify { syncStatusManager.triggerSync() }
    }
    
    private fun createTestBook(id: Int, title: String): Audiobook {
        return Audiobook(
            id = id,
            title = title,
            subtitle = null,
            author = "Test Author",
            narrator = "Test Narrator",
            series = null,
            seriesPosition = null,
            duration = 3600,
            genre = "Fiction",
            tags = null,
            publishYear = 2024,
            copyrightYear = null,
            publisher = null,
            isbn = "1234567890",
            asin = "B000000000",
            language = "en",
            rating = null,
            userRating = null,
            averageRating = null,
            abridged = 0,
            description = "Test description",
            coverImage = null,
            fileCount = 1,
            isMultiFile = 0,
            createdAt = "2024-01-01",
            progress = null,
            chapters = null,
            isFavorite = false
        )
    }

    @Test
    fun `should clear all download errors correctly`() = runTest {
        // When
        viewModel.clearAllDownloadErrors()

        // Then
        io.mockk.verify { downloadManager.clearAllDownloadErrors() }
    }

}