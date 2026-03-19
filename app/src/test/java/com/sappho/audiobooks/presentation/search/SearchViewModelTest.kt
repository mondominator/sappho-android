package com.sappho.audiobooks.presentation.search

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.sappho.audiobooks.data.remote.SapphoApi
import com.sappho.audiobooks.data.repository.AuthRepository
import com.sappho.audiobooks.domain.model.Audiobook
import com.sappho.audiobooks.domain.model.AudiobooksResponse
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    private lateinit var viewModel: SearchViewModel
    private lateinit var api: SapphoApi
    private lateinit var authRepository: AuthRepository

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        api = mockk(relaxed = true)
        authRepository = mockk(relaxed = true)

        every { authRepository.getServerUrlSync() } returns "https://test.sappho.com"

        viewModel = SearchViewModel(api, authRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `should initialize with empty search query`() = runTest {
        viewModel.searchQuery.test {
            assertThat(awaitItem()).isEmpty()
        }
    }

    @Test
    fun `should initialize with empty results`() = runTest {
        viewModel.results.test {
            val item = awaitItem()
            assertThat(item.books).isEmpty()
            assertThat(item.series).isEmpty()
            assertThat(item.authors).isEmpty()
        }
    }

    @Test
    fun `should initialize with server URL from auth repository`() = runTest {
        viewModel.serverUrl.test {
            assertThat(awaitItem()).isEqualTo("https://test.sappho.com")
        }
    }

    @Test
    fun `should update search query when updateSearchQuery is called`() = runTest {
        // When
        viewModel.updateSearchQuery("test query")

        // Then
        viewModel.searchQuery.test {
            assertThat(awaitItem()).isEqualTo("test query")
        }
    }

    @Test
    fun `should clear results when search query is blank`() = runTest {
        // Given - first set a query so results might exist
        val books = listOf(createTestBook(1, "Test Book", author = "Author A"))
        coEvery { api.getAudiobooks(search = any(), limit = any()) } returns
            Response.success(AudiobooksResponse(books))

        viewModel.updateSearchQuery("test")
        advanceTimeBy(300)
        testDispatcher.scheduler.advanceUntilIdle()

        // When - clear with blank query
        viewModel.updateSearchQuery("")

        // Then
        viewModel.results.test {
            val item = awaitItem()
            assertThat(item.books).isEmpty()
            assertThat(item.series).isEmpty()
            assertThat(item.authors).isEmpty()
        }
        viewModel.isLoading.test {
            assertThat(awaitItem()).isFalse()
        }
    }

    @Test
    fun `should debounce search queries`() = runTest {
        // Given
        coEvery { api.getAudiobooks(search = any(), limit = any()) } returns
            Response.success(AudiobooksResponse(emptyList()))

        // When - rapid queries
        viewModel.updateSearchQuery("t")
        viewModel.updateSearchQuery("te")
        viewModel.updateSearchQuery("tes")
        viewModel.updateSearchQuery("test")

        // Advance past debounce delay (200ms)
        advanceTimeBy(250)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then - API should only be called once with final query
        coVerify(exactly = 1) { api.getAudiobooks(search = "test", limit = 100) }
    }

    @Test
    fun `should return search results with books`() = runTest {
        // Given
        val books = listOf(
            createTestBook(1, "The Great Gatsby", author = "F. Scott Fitzgerald"),
            createTestBook(2, "Great Expectations", author = "Charles Dickens")
        )
        coEvery { api.getAudiobooks(search = "great", limit = 100) } returns
            Response.success(AudiobooksResponse(books))

        // When
        viewModel.updateSearchQuery("great")
        advanceTimeBy(250)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        viewModel.results.test {
            val item = awaitItem()
            assertThat(item.books).hasSize(2)
        }
    }

    @Test
    fun `should extract series from search results`() = runTest {
        // Given
        val books = listOf(
            createTestBook(1, "Book 1", series = "Harry Potter"),
            createTestBook(2, "Book 2", series = "Harry Potter"),
            createTestBook(3, "Book 3", series = "Lord of the Rings")
        )
        coEvery { api.getAudiobooks(search = "harry", limit = 100) } returns
            Response.success(AudiobooksResponse(books))

        // When
        viewModel.updateSearchQuery("harry")
        advanceTimeBy(250)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        viewModel.results.test {
            val item = awaitItem()
            assertThat(item.series).contains("Harry Potter")
        }
    }

    @Test
    fun `should extract authors from search results`() = runTest {
        // Given
        val books = listOf(
            createTestBook(1, "Book A", author = "Author One"),
            createTestBook(2, "Book B", author = "Author Two"),
            createTestBook(3, "Book C", author = "Author One")
        )
        coEvery { api.getAudiobooks(search = "book", limit = 100) } returns
            Response.success(AudiobooksResponse(books))

        // When
        viewModel.updateSearchQuery("book")
        advanceTimeBy(250)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        viewModel.results.test {
            val item = awaitItem()
            assertThat(item.authors).contains("Author One")
            assertThat(item.authors).contains("Author Two")
        }
    }

    @Test
    fun `should limit books to 8 results`() = runTest {
        // Given
        val books = (1..15).map { createTestBook(it, "Book $it") }
        coEvery { api.getAudiobooks(search = any(), limit = 100) } returns
            Response.success(AudiobooksResponse(books))

        // When
        viewModel.updateSearchQuery("book")
        advanceTimeBy(250)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        viewModel.results.test {
            val item = awaitItem()
            assertThat(item.books).hasSize(8)
        }
    }

    @Test
    fun `should limit series to 5 results`() = runTest {
        // Given
        val books = (1..10).map {
            createTestBook(it, "Book $it", series = "Series $it")
        }
        coEvery { api.getAudiobooks(search = "series", limit = 100) } returns
            Response.success(AudiobooksResponse(books))

        // When
        viewModel.updateSearchQuery("series")
        advanceTimeBy(250)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        viewModel.results.test {
            val item = awaitItem()
            assertThat(item.series.size).isAtMost(5)
        }
    }

    @Test
    fun `should limit authors to 5 results`() = runTest {
        // Given
        val books = (1..10).map {
            createTestBook(it, "Book $it", author = "Author $it")
        }
        coEvery { api.getAudiobooks(search = "author", limit = 100) } returns
            Response.success(AudiobooksResponse(books))

        // When
        viewModel.updateSearchQuery("author")
        advanceTimeBy(250)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        viewModel.results.test {
            val item = awaitItem()
            assertThat(item.authors.size).isAtMost(5)
        }
    }

    @Test
    fun `should handle API failure gracefully`() = runTest {
        // Given
        coEvery { api.getAudiobooks(search = any(), limit = any()) } throws
            RuntimeException("Network error")

        // When
        viewModel.updateSearchQuery("test")
        advanceTimeBy(250)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then - results should be empty, not crashed
        viewModel.results.test {
            val item = awaitItem()
            assertThat(item.books).isEmpty()
            assertThat(item.series).isEmpty()
            assertThat(item.authors).isEmpty()
        }
        viewModel.isLoading.test {
            assertThat(awaitItem()).isFalse()
        }
    }

    @Test
    fun `should handle unsuccessful API response`() = runTest {
        // Given
        coEvery { api.getAudiobooks(search = any(), limit = any()) } returns
            Response.success(AudiobooksResponse(emptyList()))

        // When
        viewModel.updateSearchQuery("nonexistent")
        advanceTimeBy(250)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        viewModel.results.test {
            val item = awaitItem()
            assertThat(item.books).isEmpty()
        }
    }

    @Test
    fun `clearSearch should reset all state`() = runTest {
        // Given - perform a search first
        val books = listOf(createTestBook(1, "Test Book"))
        coEvery { api.getAudiobooks(search = any(), limit = any()) } returns
            Response.success(AudiobooksResponse(books))

        viewModel.updateSearchQuery("test")
        advanceTimeBy(250)
        testDispatcher.scheduler.advanceUntilIdle()

        // When
        viewModel.clearSearch()

        // Then
        viewModel.searchQuery.test {
            assertThat(awaitItem()).isEmpty()
        }
        viewModel.results.test {
            val item = awaitItem()
            assertThat(item.books).isEmpty()
        }
        viewModel.isLoading.test {
            assertThat(awaitItem()).isFalse()
        }
    }

    @Test
    fun `should sort books by relevance with exact title match first`() = runTest {
        // Given
        val books = listOf(
            createTestBook(1, "Something Gatsby", author = "Nobody"),
            createTestBook(2, "Gatsby", author = "Author"),
            createTestBook(3, "The Great Gatsby Sequel", author = "Another")
        )
        coEvery { api.getAudiobooks(search = "gatsby", limit = 100) } returns
            Response.success(AudiobooksResponse(books))

        // When
        viewModel.updateSearchQuery("gatsby")
        advanceTimeBy(250)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then - exact title match should be first
        viewModel.results.test {
            val item = awaitItem()
            assertThat(item.books.first().title).isEqualTo("Gatsby")
        }
    }

    @Test
    fun `should prioritize series matching the query`() = runTest {
        // Given
        val books = listOf(
            createTestBook(1, "Book 1", series = "Dune"),
            createTestBook(2, "Book 2", series = "Foundation"),
            createTestBook(3, "Book 3", series = "Dune Chronicles")
        )
        coEvery { api.getAudiobooks(search = "dune", limit = 100) } returns
            Response.success(AudiobooksResponse(books))

        // When
        viewModel.updateSearchQuery("dune")
        advanceTimeBy(250)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then - series containing "dune" should appear
        viewModel.results.test {
            val item = awaitItem()
            assertThat(item.series).contains("Dune")
        }
    }

    @Test
    fun `should not call API when query is whitespace only`() = runTest {
        // When
        viewModel.updateSearchQuery("   ")
        advanceTimeBy(250)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        coVerify(exactly = 0) { api.getAudiobooks(search = any(), limit = any()) }
    }

    private fun createTestBook(
        id: Int,
        title: String,
        author: String? = "Test Author",
        series: String? = null
    ): Audiobook {
        return Audiobook(
            id = id,
            title = title,
            subtitle = null,
            author = author,
            narrator = null,
            series = series,
            seriesPosition = null,
            duration = 3600,
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
            fileCount = 1,
            isMultiFile = null,
            createdAt = "2024-01-01",
            progress = null,
            chapters = null,
            isFavorite = false
        )
    }
}
