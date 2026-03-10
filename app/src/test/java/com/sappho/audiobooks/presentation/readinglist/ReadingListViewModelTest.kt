package com.sappho.audiobooks.presentation.readinglist

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.sappho.audiobooks.data.remote.ReorderFavoritesRequest
import com.sappho.audiobooks.data.remote.SapphoApi
import com.sappho.audiobooks.data.repository.AuthRepository
import com.sappho.audiobooks.domain.model.Audiobook
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class ReadingListViewModelTest {

    private lateinit var viewModel: ReadingListViewModel
    private lateinit var api: SapphoApi
    private lateinit var authRepository: AuthRepository

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        api = mockk(relaxed = true)
        authRepository = mockk(relaxed = true)

        every { authRepository.getServerUrlSync() } returns "https://test.sappho.com"
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): ReadingListViewModel {
        return ReadingListViewModel(api, authRepository)
    }

    private fun createBook(
        id: Int,
        title: String,
        author: String? = null,
        duration: Int? = null
    ) = Audiobook(
        id = id,
        title = title,
        subtitle = null,
        author = author,
        narrator = null,
        series = null,
        seriesPosition = null,
        duration = duration,
        genre = null,
        tags = null,
        publishYear = null,
        copyrightYear = null,
        publisher = null,
        isbn = null,
        asin = null,
        language = null,
        rating = null,
        userRating = null,
        averageRating = null,
        abridged = null,
        description = null,
        coverImage = null,
        fileCount = 0,
        filePath = null,
        isMultiFile = null,
        createdAt = null,
        progress = null,
        chapters = null,
        isFavorite = true
    )

    @Test
    fun `loadReadingList calls getFavorites with current sort option`() = runTest {
        // Given
        val books = listOf(createBook(1, "Book A"), createBook(2, "Book B"))
        coEvery { api.getFavorites(any()) } returns Response.success(books)

        // When
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then - default sort is "custom"
        coVerify { api.getFavorites("custom") }
        viewModel.books.test {
            assertThat(awaitItem()).isEqualTo(books)
        }
    }

    @Test
    fun `setSortOption reloads list with new sort`() = runTest {
        // Given - initial load with default sort
        val customBooks = listOf(createBook(1, "Book A"), createBook(2, "Book B"))
        val titleBooks = listOf(createBook(2, "Book B"), createBook(1, "Book A"))
        coEvery { api.getFavorites("custom") } returns Response.success(customBooks)
        coEvery { api.getFavorites("title") } returns Response.success(titleBooks)

        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        // When
        viewModel.setSortOption("title")
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        coVerify { api.getFavorites("title") }
        viewModel.sortOption.test {
            assertThat(awaitItem()).isEqualTo("title")
        }
        viewModel.books.test {
            assertThat(awaitItem()).isEqualTo(titleBooks)
        }
    }

    @Test
    fun `reorderBooks updates local list and syncs to server`() = runTest {
        // Given
        val book1 = createBook(1, "Book A")
        val book2 = createBook(2, "Book B")
        val book3 = createBook(3, "Book C")
        coEvery { api.getFavorites(any()) } returns Response.success(listOf(book1, book2, book3))
        coEvery { api.reorderFavorites(any()) } returns Response.success(Unit)

        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        // When - move item from index 0 to index 2
        viewModel.reorderBooks(0, 2)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then - local list should be reordered: [B, C, A]
        viewModel.books.test {
            val result = awaitItem()
            assertThat(result).hasSize(3)
            assertThat(result[0].id).isEqualTo(2)
            assertThat(result[1].id).isEqualTo(3)
            assertThat(result[2].id).isEqualTo(1)
        }

        // Then - server should be called with new order
        coVerify { api.reorderFavorites(ReorderFavoritesRequest(listOf(2, 3, 1))) }
    }

    @Test
    fun `removeBook removes from local list and calls server`() = runTest {
        // Given
        val book1 = createBook(1, "Book A")
        val book2 = createBook(2, "Book B")
        val book3 = createBook(3, "Book C")
        coEvery { api.getFavorites(any()) } returns Response.success(listOf(book1, book2, book3))
        coEvery { api.removeFavorite(any()) } returns Response.success(Unit)

        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        // When
        viewModel.removeBook(2)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then - book 2 should be removed from local list
        viewModel.books.test {
            val result = awaitItem()
            assertThat(result).hasSize(2)
            assertThat(result.map { it.id }).containsExactly(1, 3).inOrder()
        }

        // Then - server should be called
        coVerify { api.removeFavorite(2) }
    }

    @Test
    fun `removeBook reloads list on server failure`() = runTest {
        // Given
        val book1 = createBook(1, "Book A")
        val book2 = createBook(2, "Book B")
        val allBooks = listOf(book1, book2)
        coEvery { api.getFavorites(any()) } returns Response.success(allBooks)
        coEvery { api.removeFavorite(any()) } throws RuntimeException("Network error")

        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        // When
        viewModel.removeBook(2)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then - should reload from server (restoring original list)
        // getFavorites called: once in init, once on failure reload
        coVerify(atLeast = 2) { api.getFavorites(any()) }
    }

    @Test
    fun `sortOption defaults to custom`() = runTest {
        // Given
        coEvery { api.getFavorites(any()) } returns Response.success(emptyList())

        // When
        viewModel = createViewModel()

        // Then
        viewModel.sortOption.test {
            assertThat(awaitItem()).isEqualTo("custom")
        }
    }

    @Test
    fun `reorderBooks handles same index gracefully`() = runTest {
        // Given
        val book1 = createBook(1, "Book A")
        val book2 = createBook(2, "Book B")
        coEvery { api.getFavorites(any()) } returns Response.success(listOf(book1, book2))

        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        // When - reorder to same position
        viewModel.reorderBooks(0, 0)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then - no server call for reorder since nothing changed
        coVerify(exactly = 0) { api.reorderFavorites(any()) }
    }
}
