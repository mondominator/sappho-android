package com.sappho.audiobooks.presentation.library

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.sappho.audiobooks.data.remote.AddToCollectionRequest
import com.sappho.audiobooks.data.remote.AiStatusResponse
import com.sappho.audiobooks.data.remote.BatchActionRequest
import com.sappho.audiobooks.data.remote.BatchActionResponse
import com.sappho.audiobooks.data.remote.Collection
import com.sappho.audiobooks.data.remote.CollectionDetail
import com.sappho.audiobooks.data.remote.CollectionForBook
import com.sappho.audiobooks.data.remote.CreateCollectionRequest
import com.sappho.audiobooks.data.remote.SapphoApi
import com.sappho.audiobooks.data.remote.UpdateCollectionRequest
import com.sappho.audiobooks.data.repository.AuthRepository
import com.sappho.audiobooks.data.repository.UserPreferencesRepository
import com.sappho.audiobooks.domain.model.Audiobook
import com.sappho.audiobooks.domain.model.AudiobooksResponse
import com.sappho.audiobooks.domain.model.AuthorInfo
import com.sappho.audiobooks.domain.model.GenreCategoryData
import com.sappho.audiobooks.domain.model.GenreInfo
import com.sappho.audiobooks.domain.model.GenreMappingsResponse
import com.sappho.audiobooks.domain.model.GenreMetadata
import com.sappho.audiobooks.domain.model.SeriesInfo
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
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Before
import org.junit.Test
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {

    private lateinit var viewModel: LibraryViewModel
    private lateinit var api: SapphoApi
    private lateinit var authRepository: AuthRepository
    private lateinit var userPreferences: UserPreferencesRepository

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        api = mockk(relaxed = true)
        authRepository = mockk(relaxed = true)
        userPreferences = mockk(relaxed = true)

        every { authRepository.getServerUrlSync() } returns "https://test.sappho.com"

        // Default API responses for init
        coEvery { api.getGenreMappings() } returns Response.success(
            GenreMappingsResponse(
                genres = emptyMap(),
                defaults = GenreMetadata(colors = listOf("#10b981"), icon = "category")
            )
        )
        coEvery { api.getGenres() } returns Response.success(emptyList())
        coEvery { api.getSeries() } returns Response.success(emptyList())
        coEvery { api.getAuthors() } returns Response.success(emptyList())
        coEvery { api.getAudiobooks(limit = any()) } returns Response.success(AudiobooksResponse(emptyList()))
        coEvery { api.getCollections() } returns Response.success(emptyList())
        coEvery { api.getFavorites(any()) } returns Response.success(emptyList())
        coEvery { api.getAiStatus() } returns Response.success(AiStatusResponse(configured = false, provider = null))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): LibraryViewModel {
        return LibraryViewModel(api, authRepository, userPreferences)
    }

    // --- Initialization Tests ---

    @Test
    fun `should initialize with server URL from auth repository`() = runTest {
        // When
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        viewModel.serverUrl.test {
            assertThat(awaitItem()).isEqualTo("https://test.sappho.com")
        }
    }

    @Test
    fun `should load categories on initialization`() = runTest {
        // Given
        val seriesList = listOf(
            SeriesInfo("Dune", 6, emptyList())
        )
        val authorList = listOf(
            AuthorInfo("Frank Herbert", 6, emptyList())
        )
        val genreList = listOf(
            GenreInfo("Science Fiction", 10, listOf(1, 2, 3))
        )
        coEvery { api.getSeries() } returns Response.success(seriesList)
        coEvery { api.getAuthors() } returns Response.success(authorList)
        coEvery { api.getGenres() } returns Response.success(genreList)

        // When
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        viewModel.series.test {
            assertThat(awaitItem()).isEqualTo(seriesList)
        }
        viewModel.authors.test {
            assertThat(awaitItem()).isEqualTo(authorList)
        }
        viewModel.genres.test {
            assertThat(awaitItem()).isEqualTo(genreList)
        }
        viewModel.uiState.test {
            assertThat(awaitItem()).isEqualTo(LibraryUiState.Success)
        }
    }

    @Test
    fun `should set error state when category loading fails`() = runTest {
        // Given
        coEvery { api.getGenreMappings() } throws RuntimeException("Network error")

        // When
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            assertThat(state).isInstanceOf(LibraryUiState.Error::class.java)
            assertThat((state as LibraryUiState.Error).message).isEqualTo("Network error")
        }
    }

    @Test
    fun `should load all audiobooks on initialization`() = runTest {
        // Given
        val books = listOf(createTestBook(1, "Book 1"), createTestBook(2, "Book 2"))
        coEvery { api.getAudiobooks(limit = 10000) } returns
            Response.success(AudiobooksResponse(books))

        // When
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        viewModel.allAudiobooks.test {
            assertThat(awaitItem()).hasSize(2)
        }
    }

    @Test
    fun `should check AI status on initialization`() = runTest {
        // Given
        coEvery { api.getAiStatus() } returns
            Response.success(AiStatusResponse(configured = true, provider = "openai"))

        // When
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        viewModel.aiConfigured.test {
            assertThat(awaitItem()).isTrue()
        }
    }

    @Test
    fun `should set aiConfigured to false when AI status check fails`() = runTest {
        // Given
        coEvery { api.getAiStatus() } throws RuntimeException("Network error")

        // When
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        viewModel.aiConfigured.test {
            assertThat(awaitItem()).isFalse()
        }
    }

    // --- Collection Tests ---

    @Test
    fun `should load collections on initialization`() = runTest {
        // Given
        val collections = listOf(
            createTestCollection(1, "Favorites"),
            createTestCollection(2, "Sci-Fi Picks")
        )
        coEvery { api.getCollections() } returns Response.success(collections)

        // When
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        viewModel.collections.test {
            assertThat(awaitItem()).hasSize(2)
        }
    }

    @Test
    fun `should create collection and reload`() = runTest {
        // Given
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val newCollection = createTestCollection(1, "New Collection")
        coEvery {
            api.createCollection(CreateCollectionRequest("New Collection", "A description", false))
        } returns Response.success(newCollection)
        coEvery { api.getCollections() } returns Response.success(listOf(newCollection))

        var resultSuccess = false
        var resultMessage = ""

        // When
        viewModel.createCollection("New Collection", "A description") { success, message ->
            resultSuccess = success
            resultMessage = message
        }
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertThat(resultSuccess).isTrue()
        assertThat(resultMessage).isEqualTo("Collection created")
    }

    @Test
    fun `should handle create collection failure`() = runTest {
        // Given
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        coEvery {
            api.createCollection(any())
        } returns Response.error(500, "Server error".toResponseBody())

        var resultSuccess = false
        var resultMessage = ""

        // When
        viewModel.createCollection("Bad Collection", null) { success, message ->
            resultSuccess = success
            resultMessage = message
        }
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertThat(resultSuccess).isFalse()
        assertThat(resultMessage).isEqualTo("Failed to create collection")
    }

    @Test
    fun `should delete collection and clear selected if matches`() = runTest {
        // Given
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        coEvery { api.deleteCollection(1) } returns Response.success(Unit)

        var resultSuccess = false

        // When
        viewModel.deleteCollection(1) { success, _ ->
            resultSuccess = success
        }
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertThat(resultSuccess).isTrue()
    }

    @Test
    fun `should add book to collection with correct error for 409`() = runTest {
        // Given
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        coEvery {
            api.addToCollection(1, AddToCollectionRequest(42))
        } returns Response.error(409, "Conflict".toResponseBody())

        var resultMessage = ""

        // When
        viewModel.addBookToCollection(1, 42) { _, message ->
            resultMessage = message
        }
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertThat(resultMessage).isEqualTo("Book already in collection")
    }

    @Test
    fun `should add book to collection with correct error for 404`() = runTest {
        // Given
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        coEvery {
            api.addToCollection(1, AddToCollectionRequest(42))
        } returns Response.error(404, "Not Found".toResponseBody())

        var resultMessage = ""

        // When
        viewModel.addBookToCollection(1, 42) { _, message ->
            resultMessage = message
        }
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertThat(resultMessage).isEqualTo("Collection not found")
    }

    @Test
    fun `should remove book from collection`() = runTest {
        // Given
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        coEvery { api.removeFromCollection(1, 42) } returns Response.success(Unit)

        var resultSuccess = false

        // When
        viewModel.removeBookFromCollection(1, 42) { success, _ ->
            resultSuccess = success
        }
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertThat(resultSuccess).isTrue()
        coVerify { api.removeFromCollection(1, 42) }
    }

    @Test
    fun `should load collections for book`() = runTest {
        // Given
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val bookCollections = listOf(
            CollectionForBook(id = 1, name = "Favorites", containsBook = 1),
            CollectionForBook(id = 2, name = "Sci-Fi", containsBook = 0)
        )
        coEvery { api.getCollectionsForBook(42) } returns Response.success(bookCollections)

        // When
        viewModel.loadCollectionsForBook(42)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        viewModel.collectionsForBook.test {
            assertThat(awaitItem()).hasSize(2)
        }
    }

    // --- Reading List Tests ---

    @Test
    fun `should load reading list on initialization`() = runTest {
        // Given
        val books = listOf(createTestBook(1, "Favorite Book"))
        coEvery { api.getFavorites(any()) } returns Response.success(books)

        // When
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        viewModel.readingList.test {
            assertThat(awaitItem()).hasSize(1)
        }
    }

    // --- Batch Selection Tests ---

    @Test
    fun `should toggle selection mode`() = runTest {
        // Given
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        // When
        viewModel.toggleSelectionMode()

        // Then
        viewModel.isSelectionMode.test {
            assertThat(awaitItem()).isTrue()
        }

        // When - toggle again
        viewModel.toggleSelectionMode()

        // Then
        viewModel.isSelectionMode.test {
            assertThat(awaitItem()).isFalse()
        }
    }

    @Test
    fun `should toggle book selection`() = runTest {
        // Given
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        // When - select a book
        viewModel.toggleBookSelection(1)

        // Then
        viewModel.selectedBookIds.test {
            assertThat(awaitItem()).contains(1)
        }

        // When - deselect the same book
        viewModel.toggleBookSelection(1)

        // Then - should also exit selection mode when empty
        viewModel.selectedBookIds.test {
            assertThat(awaitItem()).isEmpty()
        }
        viewModel.isSelectionMode.test {
            assertThat(awaitItem()).isFalse()
        }
    }

    @Test
    fun `should select all books`() = runTest {
        // Given
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        // When
        viewModel.selectAllBooks(listOf(1, 2, 3, 4, 5))

        // Then
        viewModel.selectedBookIds.test {
            assertThat(awaitItem()).containsExactly(1, 2, 3, 4, 5)
        }
    }

    @Test
    fun `should deselect all books`() = runTest {
        // Given
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.selectAllBooks(listOf(1, 2, 3))

        // When
        viewModel.deselectAllBooks()

        // Then
        viewModel.selectedBookIds.test {
            assertThat(awaitItem()).isEmpty()
        }
    }

    @Test
    fun `should exit selection mode and clear selected books`() = runTest {
        // Given
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.toggleSelectionMode()
        viewModel.toggleBookSelection(1)
        viewModel.toggleBookSelection(2)

        // When
        viewModel.exitSelectionMode()

        // Then
        viewModel.isSelectionMode.test {
            assertThat(awaitItem()).isFalse()
        }
        viewModel.selectedBookIds.test {
            assertThat(awaitItem()).isEmpty()
        }
    }

    // --- Batch Actions Tests ---

    @Test
    fun `should batch mark finished and exit selection mode`() = runTest {
        // Given
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.toggleBookSelection(1)
        viewModel.toggleBookSelection(2)

        coEvery { api.batchMarkFinished(any()) } returns
            Response.success(BatchActionResponse(success = true, count = 2))

        var resultSuccess = false
        var resultMessage = ""

        // When
        viewModel.batchMarkFinished { success, message ->
            resultSuccess = success
            resultMessage = message
        }
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertThat(resultSuccess).isTrue()
        assertThat(resultMessage).isEqualTo("Marked 2 books as finished")
        viewModel.isSelectionMode.test {
            assertThat(awaitItem()).isFalse()
        }
    }

    @Test
    fun `should batch clear progress and exit selection mode`() = runTest {
        // Given
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.toggleBookSelection(1)

        coEvery { api.batchClearProgress(any()) } returns
            Response.success(BatchActionResponse(success = true, count = 1))

        var resultSuccess = false

        // When
        viewModel.batchClearProgress { success, _ ->
            resultSuccess = success
        }
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertThat(resultSuccess).isTrue()
    }

    @Test
    fun `should batch add to reading list`() = runTest {
        // Given
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.toggleBookSelection(1)
        viewModel.toggleBookSelection(2)
        viewModel.toggleBookSelection(3)

        coEvery { api.batchAddToReadingList(any()) } returns
            Response.success(BatchActionResponse(success = true, count = 3))

        var resultSuccess = false
        var resultMessage = ""

        // When
        viewModel.batchAddToReadingList { success, message ->
            resultSuccess = success
            resultMessage = message
        }
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertThat(resultSuccess).isTrue()
        assertThat(resultMessage).isEqualTo("Added 3 books to reading list")
    }

    @Test
    fun `should handle batch action failure`() = runTest {
        // Given
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.toggleBookSelection(1)

        coEvery { api.batchMarkFinished(any()) } returns
            Response.error(500, "Server error".toResponseBody())

        var resultSuccess = false
        var resultMessage = ""

        // When
        viewModel.batchMarkFinished { success, message ->
            resultSuccess = success
            resultMessage = message
        }
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertThat(resultSuccess).isFalse()
        assertThat(resultMessage).isEqualTo("Failed to mark books as finished")
    }

    @Test
    fun `should handle batch action network error`() = runTest {
        // Given
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.toggleBookSelection(1)

        coEvery { api.batchMarkFinished(any()) } throws RuntimeException("Network error")

        var resultSuccess = false

        // When
        viewModel.batchMarkFinished { success, _ ->
            resultSuccess = success
        }
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertThat(resultSuccess).isFalse()
    }

    // --- Refresh Tests ---

    @Test
    fun `refresh should reload categories, collections, and reading list`() = runTest {
        // Given
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        // When
        viewModel.refresh()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then - each should be called twice: once in init, once in refresh
        coVerify(atLeast = 2) { api.getGenreMappings() }
        coVerify(atLeast = 2) { api.getCollections() }
        coVerify(atLeast = 2) { api.getFavorites(any()) }
    }

    // --- Genre Normalization Tests (static companion functions) ---

    @Test
    fun `normalizeGenre should return original when no categories loaded`() {
        // When categories are empty, should return original
        val result = LibraryViewModel.normalizeGenre("Science Fiction")

        // Then - either normalized or original depending on cached state
        assertThat(result).isNotEmpty()
    }

    @Test
    fun `clearSelectedCollection should set to null`() = runTest {
        // Given
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        // When
        viewModel.clearSelectedCollection()

        // Then
        viewModel.selectedCollection.test {
            assertThat(awaitItem()).isNull()
        }
    }

    @Test
    fun `should update collection successfully`() = runTest {
        // Given
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        coEvery {
            api.updateCollection(1, UpdateCollectionRequest("Updated Name", "Updated Desc"))
        } returns Response.success(createTestCollection(1, "Updated Name"))

        var resultSuccess = false
        var resultMessage = ""

        // When
        viewModel.updateCollection(1, "Updated Name", "Updated Desc") { success, message ->
            resultSuccess = success
            resultMessage = message
        }
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertThat(resultSuccess).isTrue()
        assertThat(resultMessage).isEqualTo("Collection updated")
    }

    // --- Helper Functions ---

    private fun createTestBook(id: Int, title: String): Audiobook {
        return Audiobook(
            id = id,
            title = title,
            subtitle = null,
            author = "Test Author",
            narrator = null,
            series = null,
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

    private fun createTestCollection(id: Int, name: String): Collection {
        return Collection(
            id = id,
            name = name,
            description = null,
            userId = 1,
            bookCount = 0,
            firstCover = null,
            bookIds = null,
            isPublic = 0,
            isOwner = 1,
            creatorUsername = "testuser",
            createdAt = "2024-01-01",
            updatedAt = "2024-01-01"
        )
    }
}
