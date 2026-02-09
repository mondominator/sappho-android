package com.sappho.audiobooks.presentation.library

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sappho.audiobooks.data.remote.*
import com.sappho.audiobooks.data.repository.UserPreferencesRepository
import com.sappho.audiobooks.domain.model.AuthorInfo
import com.sappho.audiobooks.domain.model.GenreCategoryData
import com.sappho.audiobooks.domain.model.GenreInfo
import com.sappho.audiobooks.domain.model.GenreMetadata
import com.sappho.audiobooks.domain.model.SeriesInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val api: SapphoApi,
    private val authRepository: com.sappho.audiobooks.data.repository.AuthRepository,
    val userPreferences: UserPreferencesRepository
) : ViewModel() {

    companion object {
        // Genre mappings fetched from server, cached here (includes keywords, colors, icons)
        private var genreCategories: Map<String, GenreCategoryData> = emptyMap()
        private var defaultGenreMetadata: GenreMetadata = GenreMetadata(
            colors = listOf("#10b981", "#059669"),
            icon = "category"
        )

        fun normalizeGenre(genre: String): String {
            if (genreCategories.isEmpty()) return genre

            val lowerGenre = genre.lowercase()

            // Try to find a matching category
            for ((category, data) in genreCategories) {
                for (keyword in data.keywords) {
                    if (lowerGenre == keyword || lowerGenre.contains(keyword)) {
                        return category
                    }
                }
            }

            return genre // Return original if no match
        }

        fun getAllNormalizedGenres(genre: String): List<String> {
            if (genreCategories.isEmpty()) return listOf(genre)

            val parts = genre.split(",", ";", "&", " and ")
                .map { it.trim().lowercase() }
                .filter { it.isNotEmpty() }

            val normalized = mutableSetOf<String>()
            for (part in parts) {
                for ((category, data) in genreCategories) {
                    for (keyword in data.keywords) {
                        if (part == keyword || part.contains(keyword)) {
                            normalized.add(category)
                            break
                        }
                    }
                }
            }

            return if (normalized.isEmpty()) listOf(genre) else normalized.toList()
        }

        /**
         * Get colors for a genre category (returns hex color strings)
         */
        fun getGenreColors(genre: String): List<String> {
            return genreCategories[genre]?.colors ?: defaultGenreMetadata.colors
        }

        /**
         * Get icon name for a genre category
         */
        fun getGenreIcon(genre: String): String {
            return genreCategories[genre]?.icon ?: defaultGenreMetadata.icon
        }
    }

    private val _uiState = MutableStateFlow<LibraryUiState>(LibraryUiState.Loading)
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    private val _series = MutableStateFlow<List<SeriesInfo>>(emptyList())
    val series: StateFlow<List<SeriesInfo>> = _series.asStateFlow()

    private val _authors = MutableStateFlow<List<AuthorInfo>>(emptyList())
    val authors: StateFlow<List<AuthorInfo>> = _authors.asStateFlow()

    private val _genres = MutableStateFlow<List<GenreInfo>>(emptyList())
    val genres: StateFlow<List<GenreInfo>> = _genres.asStateFlow()

    private val _allAudiobooks = MutableStateFlow<List<com.sappho.audiobooks.domain.model.Audiobook>>(emptyList())
    val allAudiobooks: StateFlow<List<com.sappho.audiobooks.domain.model.Audiobook>> = _allAudiobooks.asStateFlow()

    private val _serverUrl = MutableStateFlow<String?>(null)
    val serverUrl: StateFlow<String?> = _serverUrl.asStateFlow()

    private val _aiConfigured = MutableStateFlow(false)
    val aiConfigured: StateFlow<Boolean> = _aiConfigured.asStateFlow()

    // Collections state
    private val _collections = MutableStateFlow<List<com.sappho.audiobooks.data.remote.Collection>>(emptyList())
    val collections: StateFlow<List<com.sappho.audiobooks.data.remote.Collection>> = _collections.asStateFlow()

    private val _selectedCollection = MutableStateFlow<CollectionDetail?>(null)
    val selectedCollection: StateFlow<CollectionDetail?> = _selectedCollection.asStateFlow()

    private val _collectionsForBook = MutableStateFlow<List<CollectionForBook>>(emptyList())
    val collectionsForBook: StateFlow<List<CollectionForBook>> = _collectionsForBook.asStateFlow()

    // Reading list state
    private val _readingList = MutableStateFlow<List<com.sappho.audiobooks.domain.model.Audiobook>>(emptyList())
    val readingList: StateFlow<List<com.sappho.audiobooks.domain.model.Audiobook>> = _readingList.asStateFlow()

    // Batch selection state
    private val _selectedBookIds = MutableStateFlow<Set<Int>>(emptySet())
    val selectedBookIds: StateFlow<Set<Int>> = _selectedBookIds.asStateFlow()

    private val _isSelectionMode = MutableStateFlow(false)
    val isSelectionMode: StateFlow<Boolean> = _isSelectionMode.asStateFlow()

    init {
        _serverUrl.value = authRepository.getServerUrlSync()
        loadCategories()
        loadCollections()
        loadReadingList()
        checkAiStatus()
    }

    private fun checkAiStatus() {
        viewModelScope.launch {
            try {
                val response = api.getAiStatus()
                if (response.isSuccessful) {
                    _aiConfigured.value = response.body()?.configured ?: false
                    Log.d("LibraryViewModel", "AI configured: ${_aiConfigured.value}")
                }
            } catch (e: Exception) {
                Log.e("LibraryViewModel", "Error checking AI status", e)
                _aiConfigured.value = false
            }
        }
    }

    suspend fun getSeriesRecap(seriesName: String): Result<com.sappho.audiobooks.data.remote.SeriesRecapResponse> {
        return try {
            // URLEncoder encodes spaces as '+', but we need '%20' for URL paths
            val encodedName = java.net.URLEncoder.encode(seriesName, "UTF-8").replace("+", "%20")
            val response = api.getSeriesRecap(encodedName)
            if (response.isSuccessful) {
                response.body()?.let { Result.success(it) }
                    ?: Result.failure(Exception("Empty response"))
            } else {
                val errorBody = response.errorBody()?.string()
                Result.failure(Exception(errorBody ?: "Failed to get recap"))
            }
        } catch (e: Exception) {
            Log.e("LibraryViewModel", "Error getting series recap", e)
            Result.failure(e)
        }
    }

    suspend fun clearSeriesRecap(seriesName: String): Result<Unit> {
        return try {
            // URLEncoder encodes spaces as '+', but we need '%20' for URL paths
            val encodedName = java.net.URLEncoder.encode(seriesName, "UTF-8").replace("+", "%20")
            val response = api.clearSeriesRecap(encodedName)
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to clear recap cache"))
            }
        } catch (e: Exception) {
            Log.e("LibraryViewModel", "Error clearing series recap", e)
            Result.failure(e)
        }
    }

    fun refresh() {
        loadCategories()
        loadCollections()
        loadReadingList()
    }

    // Reading list methods
    fun loadReadingList() {
        viewModelScope.launch {
            try {
                val response = api.getFavorites()
                if (response.isSuccessful) {
                    _readingList.value = response.body() ?: emptyList()
                    Log.d("LibraryViewModel", "Loaded ${_readingList.value.size} reading list items")
                }
            } catch (e: Exception) {
                Log.e("LibraryViewModel", "Error loading reading list", e)
            }
        }
    }

    // Collection methods
    fun loadCollections() {
        viewModelScope.launch {
            try {
                val response = api.getCollections()
                if (response.isSuccessful) {
                    _collections.value = response.body() ?: emptyList()
                    Log.d("LibraryViewModel", "Loaded ${_collections.value.size} collections")
                }
            } catch (e: Exception) {
                Log.e("LibraryViewModel", "Error loading collections", e)
            }
        }
    }

    fun loadCollectionDetail(collectionId: Int) {
        viewModelScope.launch {
            try {
                val response = api.getCollection(collectionId)
                if (response.isSuccessful) {
                    _selectedCollection.value = response.body()
                }
            } catch (e: Exception) {
                Log.e("LibraryViewModel", "Error loading collection detail", e)
            }
        }
    }

    fun clearSelectedCollection() {
        _selectedCollection.value = null
    }

    fun createCollection(name: String, description: String?, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                val response = api.createCollection(CreateCollectionRequest(name, description))
                if (response.isSuccessful) {
                    loadCollections()
                    onResult(true, "Collection created")
                } else {
                    onResult(false, "Failed to create collection")
                }
            } catch (e: Exception) {
                Log.e("LibraryViewModel", "Error creating collection", e)
                onResult(false, e.message ?: "Error creating collection")
            }
        }
    }

    fun updateCollection(collectionId: Int, name: String, description: String?, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                val response = api.updateCollection(collectionId, UpdateCollectionRequest(name, description))
                if (response.isSuccessful) {
                    loadCollections()
                    _selectedCollection.value?.let { if (it.id == collectionId) loadCollectionDetail(collectionId) }
                    onResult(true, "Collection updated")
                } else {
                    onResult(false, "Failed to update collection")
                }
            } catch (e: Exception) {
                Log.e("LibraryViewModel", "Error updating collection", e)
                onResult(false, e.message ?: "Error updating collection")
            }
        }
    }

    fun deleteCollection(collectionId: Int, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                val response = api.deleteCollection(collectionId)
                if (response.isSuccessful) {
                    loadCollections()
                    if (_selectedCollection.value?.id == collectionId) {
                        _selectedCollection.value = null
                    }
                    onResult(true, "Collection deleted")
                } else {
                    onResult(false, "Failed to delete collection")
                }
            } catch (e: Exception) {
                Log.e("LibraryViewModel", "Error deleting collection", e)
                onResult(false, e.message ?: "Error deleting collection")
            }
        }
    }

    fun addBookToCollection(collectionId: Int, bookId: Int, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                Log.d("LibraryViewModel", "Adding book $bookId to collection $collectionId")
                val response = api.addToCollection(collectionId, AddToCollectionRequest(bookId))
                if (response.isSuccessful) {
                    loadCollections()
                    _selectedCollection.value?.let { if (it.id == collectionId) loadCollectionDetail(collectionId) }
                    onResult(true, "Added to collection")
                } else {
                    val errorBody = response.errorBody()?.string()
                    Log.e("LibraryViewModel", "Failed to add to collection: ${response.code()} - $errorBody")
                    val errorMessage = when (response.code()) {
                        409 -> "Book already in collection"
                        404 -> "Collection not found"
                        else -> "Failed to add to collection (${response.code()})"
                    }
                    onResult(false, errorMessage)
                }
            } catch (e: Exception) {
                Log.e("LibraryViewModel", "Error adding to collection", e)
                onResult(false, e.message ?: "Error adding to collection")
            }
        }
    }

    fun removeBookFromCollection(collectionId: Int, bookId: Int, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                val response = api.removeFromCollection(collectionId, bookId)
                if (response.isSuccessful) {
                    loadCollections()
                    _selectedCollection.value?.let { if (it.id == collectionId) loadCollectionDetail(collectionId) }
                    onResult(true, "Removed from collection")
                } else {
                    onResult(false, "Failed to remove from collection")
                }
            } catch (e: Exception) {
                Log.e("LibraryViewModel", "Error removing from collection", e)
                onResult(false, e.message ?: "Error removing from collection")
            }
        }
    }

    fun loadCollectionsForBook(bookId: Int) {
        viewModelScope.launch {
            try {
                val response = api.getCollectionsForBook(bookId)
                if (response.isSuccessful) {
                    _collectionsForBook.value = response.body() ?: emptyList()
                }
            } catch (e: Exception) {
                Log.e("LibraryViewModel", "Error loading collections for book", e)
            }
        }
    }

    // Batch selection methods
    fun toggleSelectionMode() {
        _isSelectionMode.value = !_isSelectionMode.value
        if (!_isSelectionMode.value) {
            _selectedBookIds.value = emptySet()
        }
    }

    fun exitSelectionMode() {
        _isSelectionMode.value = false
        _selectedBookIds.value = emptySet()
    }

    fun toggleBookSelection(bookId: Int) {
        val current = _selectedBookIds.value.toMutableSet()
        if (current.contains(bookId)) {
            current.remove(bookId)
        } else {
            current.add(bookId)
        }
        _selectedBookIds.value = current
        if (current.isEmpty()) {
            _isSelectionMode.value = false
        }
    }

    fun selectAllBooks(bookIds: List<Int>) {
        _selectedBookIds.value = bookIds.toSet()
    }

    fun deselectAllBooks() {
        _selectedBookIds.value = emptySet()
    }

    // Batch actions using proper batch endpoints
    fun batchMarkFinished(onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                val ids = _selectedBookIds.value.toList()
                val response = api.batchMarkFinished(BatchActionRequest(ids))
                if (response.isSuccessful) {
                    val count = response.body()?.count ?: ids.size
                    loadCategories()
                    exitSelectionMode()
                    onResult(true, "Marked $count books as finished")
                } else {
                    onResult(false, "Failed to mark books as finished")
                }
            } catch (e: Exception) {
                Log.e("LibraryViewModel", "Error in batch mark finished", e)
                onResult(false, e.message ?: "Error marking books finished")
            }
        }
    }

    fun batchClearProgress(onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                val ids = _selectedBookIds.value.toList()
                val response = api.batchClearProgress(BatchActionRequest(ids))
                if (response.isSuccessful) {
                    val count = response.body()?.count ?: ids.size
                    loadCategories()
                    exitSelectionMode()
                    onResult(true, "Cleared progress for $count books")
                } else {
                    onResult(false, "Failed to clear progress")
                }
            } catch (e: Exception) {
                Log.e("LibraryViewModel", "Error in batch clear progress", e)
                onResult(false, e.message ?: "Error clearing progress")
            }
        }
    }

    fun batchAddToReadingList(onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                val ids = _selectedBookIds.value.toList()
                val response = api.batchAddToReadingList(BatchActionRequest(ids))
                if (response.isSuccessful) {
                    val count = response.body()?.count ?: ids.size
                    loadCategories()
                    exitSelectionMode()
                    onResult(true, "Added $count books to reading list")
                } else {
                    onResult(false, "Failed to add to reading list")
                }
            } catch (e: Exception) {
                Log.e("LibraryViewModel", "Error in batch add to reading list", e)
                onResult(false, e.message ?: "Error adding to reading list")
            }
        }
    }

    fun batchAddToCollection(collectionId: Int, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                val ids = _selectedBookIds.value.toList()
                // Use single endpoint for single book selection (more reliable)
                if (ids.size == 1) {
                    val response = api.addToCollection(collectionId, AddToCollectionRequest(ids.first()))
                    if (response.isSuccessful) {
                        loadCollections()
                        exitSelectionMode()
                        onResult(true, "Added to collection")
                    } else {
                        val errorBody = response.errorBody()?.string()
                        Log.e("LibraryViewModel", "Failed to add to collection: ${response.code()} - $errorBody")
                        val errorMessage = when (response.code()) {
                            409 -> "Book already in collection"
                            404 -> "Collection not found"
                            else -> "Failed to add to collection (${response.code()})"
                        }
                        onResult(false, errorMessage)
                    }
                } else {
                    // Use batch endpoint for multiple books
                    val response = api.batchAddToCollection(BatchAddToCollectionRequest(ids, collectionId))
                    if (response.isSuccessful) {
                        val count = response.body()?.count ?: ids.size
                        loadCollections()
                        exitSelectionMode()
                        onResult(true, "Added $count books to collection")
                    } else {
                        val errorBody = response.errorBody()?.string()
                        Log.e("LibraryViewModel", "Failed to batch add to collection: ${response.code()} - $errorBody")
                        onResult(false, "Failed to add to collection")
                    }
                }
            } catch (e: Exception) {
                Log.e("LibraryViewModel", "Error in batch add to collection", e)
                onResult(false, e.message ?: "Error adding to collection")
            }
        }
    }

    fun loadCategories() {
        viewModelScope.launch {
            _uiState.value = LibraryUiState.Loading
            try {
                Log.d("LibraryViewModel", "Loading categories...")

                // Load genre mappings from server first (includes keywords, colors, icons)
                val mappingsResponse = api.getGenreMappings()
                if (mappingsResponse.isSuccessful) {
                    val response = mappingsResponse.body()
                    if (response != null) {
                        genreCategories = response.genres
                        defaultGenreMetadata = response.defaults
                        Log.d("LibraryViewModel", "Loaded ${genreCategories.size} genre categories with colors/icons from server")
                    }
                }

                // Load genres from server (already normalized)
                val genresResponse = api.getGenres()
                if (genresResponse.isSuccessful) {
                    _genres.value = genresResponse.body() ?: emptyList()
                    Log.d("LibraryViewModel", "Loaded ${_genres.value.size} genres from server")
                }

                // Load series from server (pre-aggregated)
                val seriesResponse = api.getSeries()
                if (seriesResponse.isSuccessful) {
                    _series.value = seriesResponse.body() ?: emptyList()
                    Log.d("LibraryViewModel", "Loaded ${_series.value.size} series from server")
                }

                // Load authors from server (pre-aggregated)
                val authorsResponse = api.getAuthors()
                if (authorsResponse.isSuccessful) {
                    _authors.value = authorsResponse.body() ?: emptyList()
                    Log.d("LibraryViewModel", "Loaded ${_authors.value.size} authors from server")
                }

                // Load all audiobooks for All Books view
                try {
                    val audiobooksResponse = api.getAudiobooks(limit = 10000)
                    if (audiobooksResponse.isSuccessful) {
                        val audiobooks = audiobooksResponse.body()?.audiobooks ?: emptyList()
                        _allAudiobooks.value = audiobooks
                        Log.d("LibraryViewModel", "Loaded ${audiobooks.size} audiobooks")
                    } else {
                        Log.e("LibraryViewModel", "Audiobooks request failed: ${audiobooksResponse.code()} - ${audiobooksResponse.errorBody()?.string()}")
                    }
                } catch (e: Exception) {
                    Log.e("LibraryViewModel", "Error loading audiobooks", e)
                }

                _uiState.value = LibraryUiState.Success
            } catch (e: Exception) {
                Log.e("LibraryViewModel", "Error loading categories", e)
                _uiState.value = LibraryUiState.Error(e.message ?: "Unknown error")
            }
        }
    }
}

sealed class LibraryUiState {
    object Loading : LibraryUiState()
    object Success : LibraryUiState()
    data class Error(val message: String) : LibraryUiState()
}
