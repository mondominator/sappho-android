package com.sappho.audiobooks.presentation.detail

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sappho.audiobooks.data.remote.AudiobookRecapResponse
import com.sappho.audiobooks.data.remote.AudiobookUpdateRequest
import com.sappho.audiobooks.data.remote.AverageRating
import com.sappho.audiobooks.data.remote.ChapterUpdate
import com.sappho.audiobooks.data.remote.ChapterUpdateRequest
import com.sappho.audiobooks.data.remote.AddToCollectionRequest
import com.sappho.audiobooks.data.remote.Collection
import com.sappho.audiobooks.data.remote.CollectionForBook
import com.sappho.audiobooks.data.remote.CreateCollectionRequest
import com.sappho.audiobooks.data.remote.FetchChaptersRequest
import com.sappho.audiobooks.data.remote.MetadataSearchResult
import com.sappho.audiobooks.data.remote.RatingRequest
import com.sappho.audiobooks.data.remote.SapphoApi
import com.sappho.audiobooks.data.remote.UserRating
import com.sappho.audiobooks.data.repository.AuthRepository
import com.sappho.audiobooks.domain.model.Audiobook
import com.sappho.audiobooks.domain.model.DirectoryFile
import com.sappho.audiobooks.domain.model.Chapter
import com.sappho.audiobooks.domain.model.Progress
import com.sappho.audiobooks.service.DownloadService
import com.sappho.audiobooks.service.PlayerState
import com.sappho.audiobooks.download.DownloadManager
import com.sappho.audiobooks.util.NetworkMonitor
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AudiobookDetailViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: SapphoApi,
    private val authRepository: AuthRepository,
    val playerState: PlayerState,
    val downloadManager: DownloadManager,
    private val networkMonitor: NetworkMonitor
) : ViewModel() {

    private val _audiobook = MutableStateFlow<Audiobook?>(null)
    val audiobook: StateFlow<Audiobook?> = _audiobook

    private val _progress = MutableStateFlow<Progress?>(null)
    val progress: StateFlow<Progress?> = _progress

    private val _chapters = MutableStateFlow<List<Chapter>>(emptyList())
    val chapters: StateFlow<List<Chapter>> = _chapters

    private val _files = MutableStateFlow<List<DirectoryFile>>(emptyList())
    val files: StateFlow<List<DirectoryFile>> = _files

    private val _serverUrl = MutableStateFlow<String?>(null)
    val serverUrl: StateFlow<String?> = _serverUrl

    // Cover version for cache busting - increment after metadata updates
    private val _coverVersion = MutableStateFlow(0L)
    val coverVersion: StateFlow<Long> = _coverVersion

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _isOffline = MutableStateFlow(!networkMonitor.isOnline.value)
    val isOffline: StateFlow<Boolean> = _isOffline

    private val _isFavorite = MutableStateFlow(false)
    val isFavorite: StateFlow<Boolean> = _isFavorite

    private val _isTogglingFavorite = MutableStateFlow(false)
    val isTogglingFavorite: StateFlow<Boolean> = _isTogglingFavorite

    private val _userRating = MutableStateFlow<Int?>(null)
    val userRating: StateFlow<Int?> = _userRating

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    private val _averageRating = MutableStateFlow<AverageRating?>(null)
    val averageRating: StateFlow<AverageRating?> = _averageRating

    private val _isUpdatingRating = MutableStateFlow(false)
    val isUpdatingRating: StateFlow<Boolean> = _isUpdatingRating

    private val _isSavingMetadata = MutableStateFlow(false)
    val isSavingMetadata: StateFlow<Boolean> = _isSavingMetadata

    private val _metadataSaveResult = MutableStateFlow<String?>(null)
    val metadataSaveResult: StateFlow<String?> = _metadataSaveResult

    private val _isSearchingMetadata = MutableStateFlow(false)
    val isSearchingMetadata: StateFlow<Boolean> = _isSearchingMetadata

    private val _metadataSearchResults = MutableStateFlow<List<MetadataSearchResult>>(emptyList())
    val metadataSearchResults: StateFlow<List<MetadataSearchResult>> = _metadataSearchResults

    private val _metadataSearchError = MutableStateFlow<String?>(null)
    val metadataSearchError: StateFlow<String?> = _metadataSearchError

    private val _isEmbeddingMetadata = MutableStateFlow(false)
    val isEmbeddingMetadata: StateFlow<Boolean> = _isEmbeddingMetadata

    private val _embedMetadataResult = MutableStateFlow<String?>(null)
    val embedMetadataResult: StateFlow<String?> = _embedMetadataResult

    private val _isSavingChapters = MutableStateFlow(false)
    val isSavingChapters: StateFlow<Boolean> = _isSavingChapters

    private val _chapterSaveResult = MutableStateFlow<String?>(null)
    val chapterSaveResult: StateFlow<String?> = _chapterSaveResult

    private val _isFetchingChapters = MutableStateFlow(false)
    val isFetchingChapters: StateFlow<Boolean> = _isFetchingChapters

    private val _fetchChaptersResult = MutableStateFlow<String?>(null)
    val fetchChaptersResult: StateFlow<String?> = _fetchChaptersResult

    private val _collections = MutableStateFlow<List<Collection>>(emptyList())
    val collections: StateFlow<List<Collection>> = _collections

    private val _bookCollections = MutableStateFlow<Set<Int>>(emptySet())
    val bookCollections: StateFlow<Set<Int>> = _bookCollections

    private val _isLoadingCollections = MutableStateFlow(false)
    val isLoadingCollections: StateFlow<Boolean> = _isLoadingCollections

    // AI Recap (Catch Up)
    private val _isAiConfigured = MutableStateFlow(false)
    val isAiConfigured: StateFlow<Boolean> = _isAiConfigured

    private val _recap = MutableStateFlow<AudiobookRecapResponse?>(null)
    val recap: StateFlow<AudiobookRecapResponse?> = _recap

    private val _isLoadingRecap = MutableStateFlow(false)
    val isLoadingRecap: StateFlow<Boolean> = _isLoadingRecap

    private val _recapError = MutableStateFlow<String?>(null)
    val recapError: StateFlow<String?> = _recapError

    private val _previousBookCompleted = MutableStateFlow(false)
    val previousBookCompleted: StateFlow<Boolean> = _previousBookCompleted

    init {
        _serverUrl.value = authRepository.getServerUrlSync()
        observeNetwork()
    }

    private fun observeNetwork() {
        viewModelScope.launch {
            networkMonitor.isOnline.collectLatest { isOnline ->
                _isOffline.value = !isOnline
            }
        }
    }

    fun loadAudiobook(id: Int) {
        viewModelScope.launch {
            _isLoading.value = true

            // If offline, load from downloaded data
            if (!networkMonitor.isOnline.value) {
                val downloadedBook = downloadManager.getDownloadedBook(id)
                if (downloadedBook != null) {
                    _audiobook.value = downloadedBook.audiobook
                    _progress.value = downloadedBook.audiobook.progress
                    _chapters.value = downloadedBook.chapters
                }
                _isLoading.value = false
                return@launch
            }

            try {
                // Load audiobook
                val response = api.getAudiobook(id)
                if (response.isSuccessful) {
                    response.body()?.let { book ->
                        _audiobook.value = book
                        _isFavorite.value = book.isFavorite
                    } ?: run {
                        _errorMessage.value = "Invalid response from server"
                    }
                } else {
                    _errorMessage.value = when (response.code()) {
                        404 -> "Audiobook not found"
                        401 -> "Authentication required"
                        else -> "Failed to load audiobook (${response.code()})"
                    }
                }

                // Load user's rating
                try {
                    val ratingResponse = api.getUserRating(id)
                    if (ratingResponse.isSuccessful) {
                        _userRating.value = ratingResponse.body()?.rating
                    }
                } catch (e: Exception) {
                    // Rating is optional, don't show error to user
                    android.util.Log.e("AudiobookDetailViewModel", "Failed to load rating", e)
                }

                // Load average rating
                try {
                    val avgResponse = api.getAverageRating(id)
                    if (avgResponse.isSuccessful) {
                        _averageRating.value = avgResponse.body()
                    }
                } catch (e: Exception) {
                }

                // Load progress separately
                try {
                    val progressResponse = api.getProgress(id)
                    if (progressResponse.isSuccessful) {
                        _progress.value = progressResponse.body()
                    }
                } catch (e: Exception) {
                }

                // Load chapters
                try {
                    val chaptersResponse = api.getChapters(id)
                    if (chaptersResponse.isSuccessful) {
                        _chapters.value = chaptersResponse.body() ?: emptyList()
                    }
                } catch (e: Exception) {
                }

                // Load files
                try {
                    val filesResponse = api.getFiles(id)
                    if (filesResponse.isSuccessful) {
                        _files.value = filesResponse.body() ?: emptyList()
                    }
                } catch (e: Exception) {
                }
            } catch (e: Exception) {
                // Network error - try to load from downloaded data
                val downloadedBook = downloadManager.getDownloadedBook(id)
                if (downloadedBook != null) {
                    _audiobook.value = downloadedBook.audiobook
                    _progress.value = downloadedBook.audiobook.progress
                    _chapters.value = downloadedBook.chapters
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun markFinished() {
        viewModelScope.launch {
            _audiobook.value?.let { book ->
                try {
                    api.markFinished(book.id, com.sappho.audiobooks.data.remote.ProgressUpdateRequest(0, 1, "stopped"))
                    loadAudiobook(book.id) // Reload to get updated progress
                } catch (e: Exception) {
                }
            }
        }
    }

    fun clearProgress() {
        viewModelScope.launch {
            _audiobook.value?.let { book ->
                try {
                    api.clearProgress(book.id, com.sappho.audiobooks.data.remote.ProgressUpdateRequest(0, 0, "stopped"))
                    loadAudiobook(book.id) // Reload to get updated progress
                } catch (e: Exception) {
                }
            }
        }
    }

    fun deleteAudiobook(onDeleted: () -> Unit) {
        viewModelScope.launch {
            _audiobook.value?.let { book ->
                try {
                    val response = api.deleteAudiobook(book.id)
                    if (response.isSuccessful) {
                        onDeleted()
                    }
                } catch (e: Exception) {
                }
            }
        }
    }

    private val _isRefreshingMetadata = MutableStateFlow(false)
    val isRefreshingMetadata: StateFlow<Boolean> = _isRefreshingMetadata

    private val _refreshMetadataResult = MutableStateFlow<String?>(null)
    val refreshMetadataResult: StateFlow<String?> = _refreshMetadataResult

    fun refreshMetadata() {
        viewModelScope.launch {
            _audiobook.value?.let { book ->
                if (_isRefreshingMetadata.value) return@launch
                _isRefreshingMetadata.value = true
                _refreshMetadataResult.value = null
                try {
                    val response = api.refreshMetadata(book.id)
                    if (response.isSuccessful) {
                        response.body()?.audiobook?.let { updatedBook ->
                            _audiobook.value = updatedBook
                            // Increment cover version to bust image cache
                            _coverVersion.value = System.currentTimeMillis()
                        }
                        _refreshMetadataResult.value = response.body()?.message ?: "Metadata refreshed"
                    } else {
                        _refreshMetadataResult.value = "Failed to refresh metadata"
                    }
                } catch (e: Exception) {
                    _refreshMetadataResult.value = e.message ?: "Error refreshing metadata"
                } finally {
                    _isRefreshingMetadata.value = false
                }
            }
        }
    }

    fun clearRefreshMetadataResult() {
        _refreshMetadataResult.value = null
    }

    fun downloadAudiobook() {
        _audiobook.value?.let { book ->
            // Use foreground service for downloads to prevent cancellation on background
            DownloadService.startDownload(context, book)
        }
    }

    fun deleteDownload() {
        _audiobook.value?.let { book ->
            downloadManager.deleteDownload(book.id)
        }
    }

    fun clearDownloadError(audiobookId: Int) {
        // Remove the error state from download manager
        downloadManager.clearDownloadError(audiobookId)
    }

    fun toggleFavorite() {
        viewModelScope.launch {
            _audiobook.value?.let { book ->
                if (_isTogglingFavorite.value) return@launch
                _isTogglingFavorite.value = true
                try {
                    val response = api.toggleFavorite(book.id)
                    if (response.isSuccessful) {
                        response.body()?.let { result ->
                            _isFavorite.value = result.isFavorite
                        }
                    }
                } catch (e: Exception) {
                } finally {
                    _isTogglingFavorite.value = false
                }
            }
        }
    }

    fun setRating(rating: Int) {
        viewModelScope.launch {
            _audiobook.value?.let { book ->
                if (_isUpdatingRating.value) return@launch
                _isUpdatingRating.value = true
                try {
                    val response = api.setRating(book.id, RatingRequest(rating))
                    if (response.isSuccessful) {
                        _userRating.value = rating
                        // Refresh average rating
                        try {
                            val avgResponse = api.getAverageRating(book.id)
                            if (avgResponse.isSuccessful) {
                                _averageRating.value = avgResponse.body()
                            }
                        } catch (e: Exception) {
                        }
                    }
                } catch (e: Exception) {
                } finally {
                    _isUpdatingRating.value = false
                }
            }
        }
    }

    fun clearRating() {
        viewModelScope.launch {
            _audiobook.value?.let { book ->
                if (_isUpdatingRating.value) return@launch
                _isUpdatingRating.value = true
                try {
                    val response = api.deleteRating(book.id)
                    if (response.isSuccessful) {
                        _userRating.value = null
                        // Refresh average rating
                        try {
                            val avgResponse = api.getAverageRating(book.id)
                            if (avgResponse.isSuccessful) {
                                _averageRating.value = avgResponse.body()
                            }
                        } catch (e: Exception) {
                        }
                    }
                } catch (e: Exception) {
                } finally {
                    _isUpdatingRating.value = false
                }
            }
        }
    }

    fun updateMetadata(request: AudiobookUpdateRequest, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _audiobook.value?.let { book ->
                if (_isSavingMetadata.value) return@launch
                _isSavingMetadata.value = true
                _metadataSaveResult.value = null
                try {
                    val response = api.updateAudiobook(book.id, request)
                    if (response.isSuccessful) {
                        _metadataSaveResult.value = "Metadata saved successfully"
                        // Increment cover version to bust image cache
                        _coverVersion.value = System.currentTimeMillis()
                        // Reload audiobook to get updated data
                        loadAudiobook(book.id)
                        onSuccess()
                    } else {
                        _metadataSaveResult.value = "Failed to save metadata: ${response.code()}"
                    }
                } catch (e: Exception) {
                    _metadataSaveResult.value = "Error: ${e.message}"
                } finally {
                    _isSavingMetadata.value = false
                }
            }
        }
    }

    fun clearMetadataSaveResult() {
        _metadataSaveResult.value = null
    }

    fun searchMetadata(title: String?, author: String?, asin: String? = null) {
        viewModelScope.launch {
            _audiobook.value?.let { book ->
                if (_isSearchingMetadata.value) return@launch
                _isSearchingMetadata.value = true
                _metadataSearchError.value = null
                _metadataSearchResults.value = emptyList()
                try {
                    val response = api.searchMetadata(
                        id = book.id,
                        title = title?.takeIf { it.isNotBlank() },
                        author = author?.takeIf { it.isNotBlank() },
                        asin = asin?.takeIf { it.isNotBlank() }
                    )
                    if (response.isSuccessful) {
                        _metadataSearchResults.value = response.body()?.results ?: emptyList()
                        if (_metadataSearchResults.value.isEmpty()) {
                            _metadataSearchError.value = "No results found"
                        }
                    } else {
                        _metadataSearchError.value = "Search failed: ${response.code()}"
                    }
                } catch (e: Exception) {
                    _metadataSearchError.value = "Error: ${e.message}"
                } finally {
                    _isSearchingMetadata.value = false
                }
            }
        }
    }

    fun clearMetadataSearchResults() {
        _metadataSearchResults.value = emptyList()
        _metadataSearchError.value = null
    }

    fun embedMetadata() {
        viewModelScope.launch {
            _audiobook.value?.let { book ->
                if (_isEmbeddingMetadata.value) return@launch
                _isEmbeddingMetadata.value = true
                _embedMetadataResult.value = null
                try {
                    val response = api.embedMetadata(book.id)
                    if (response.isSuccessful) {
                        _embedMetadataResult.value = response.body()?.message ?: "Metadata embedded successfully"
                        // Increment cover version to bust image cache
                        _coverVersion.value = System.currentTimeMillis()
                        // Reload audiobook to refresh any updated data
                        loadAudiobook(book.id)
                    } else {
                        // Try to parse error message from server response
                        val errorBody = response.errorBody()?.string()
                        val errorMessage = try {
                            // Server may return JSON with error field
                            val jsonError = com.google.gson.JsonParser.parseString(errorBody).asJsonObject
                            jsonError.get("error")?.asString ?: jsonError.get("message")?.asString
                        } catch (e: Exception) {
                            null
                        }

                        _embedMetadataResult.value = when {
                            response.code() == 500 && errorMessage != null -> "Server error: $errorMessage"
                            response.code() == 500 -> "Server error: Embedding tools (tone/ffmpeg) may not be configured on server"
                            else -> "Failed to embed: ${response.code()} ${errorMessage ?: ""}"
                        }
                    }
                } catch (e: Exception) {
                    _embedMetadataResult.value = "Error: ${e.message}"
                } finally {
                    _isEmbeddingMetadata.value = false
                }
            }
        }
    }

    fun clearEmbedMetadataResult() {
        _embedMetadataResult.value = null
    }

    fun updateChapters(chapters: List<ChapterUpdate>, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _audiobook.value?.let { book ->
                if (_isSavingChapters.value) return@launch
                _isSavingChapters.value = true
                _chapterSaveResult.value = null
                try {
                    val response = api.updateChapters(book.id, ChapterUpdateRequest(chapters))
                    if (response.isSuccessful) {
                        _chapterSaveResult.value = response.body()?.message ?: "Chapters updated successfully"
                        // Reload chapters to get updated data
                        loadAudiobook(book.id)
                        onSuccess()
                    } else {
                        _chapterSaveResult.value = "Failed to update chapters: ${response.code()}"
                    }
                } catch (e: Exception) {
                    _chapterSaveResult.value = "Error: ${e.message}"
                } finally {
                    _isSavingChapters.value = false
                }
            }
        }
    }

    fun fetchChaptersFromAudnexus(asin: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _audiobook.value?.let { book ->
                if (_isFetchingChapters.value) return@launch
                _isFetchingChapters.value = true
                _fetchChaptersResult.value = null
                try {
                    val response = api.fetchChapters(book.id, FetchChaptersRequest(asin))
                    if (response.isSuccessful) {
                        _fetchChaptersResult.value = response.body()?.message ?: "Chapters fetched successfully"
                        // Reload chapters to get the new data
                        loadAudiobook(book.id)
                        onSuccess()
                    } else {
                        val errorBody = response.errorBody()?.string()
                        _fetchChaptersResult.value = when (response.code()) {
                            404 -> "No chapters found for this ASIN"
                            else -> "Failed to fetch chapters: ${response.code()}"
                        }
                    }
                } catch (e: Exception) {
                    _fetchChaptersResult.value = "Error: ${e.message}"
                } finally {
                    _isFetchingChapters.value = false
                }
            }
        }
    }

    fun clearChapterSaveResult() {
        _chapterSaveResult.value = null
    }

    fun clearFetchChaptersResult() {
        _fetchChaptersResult.value = null
    }

    fun loadCollectionsForBook(bookId: Int) {
        viewModelScope.launch {
            _isLoadingCollections.value = true
            try {
                // Load all collections and which ones contain this book in parallel
                val collectionsResponse = api.getCollections()
                val bookCollectionsResponse = api.getCollectionsForBook(bookId)

                if (collectionsResponse.isSuccessful) {
                    _collections.value = collectionsResponse.body() ?: emptyList()
                }
                if (bookCollectionsResponse.isSuccessful) {
                    _bookCollections.value = (bookCollectionsResponse.body() ?: emptyList())
                        .filter { it.containsBook == 1 }
                        .map { it.id }
                        .toSet()
                }
            } catch (e: Exception) {
            } finally {
                _isLoadingCollections.value = false
            }
        }
    }

    fun toggleBookInCollection(collectionId: Int, bookId: Int) {
        viewModelScope.launch {
            val isInCollection = _bookCollections.value.contains(collectionId)
            try {
                if (isInCollection) {
                    val response = api.removeFromCollection(collectionId, bookId)
                    if (response.isSuccessful) {
                        _bookCollections.value = _bookCollections.value - collectionId
                    }
                } else {
                    val response = api.addToCollection(collectionId, AddToCollectionRequest(bookId))
                    if (response.isSuccessful) {
                        _bookCollections.value = _bookCollections.value + collectionId
                    }
                }
            } catch (e: Exception) {
            }
        }
    }

    fun createCollectionAndAddBook(name: String, bookId: Int) {
        viewModelScope.launch {
            try {
                val createResponse = api.createCollection(CreateCollectionRequest(name, null))
                if (createResponse.isSuccessful) {
                    val newCollection = createResponse.body()
                    if (newCollection != null) {
                        // Add book to the new collection
                        val addResponse = api.addToCollection(newCollection.id, AddToCollectionRequest(bookId))
                        if (addResponse.isSuccessful) {
                            _collections.value = listOf(newCollection) + _collections.value
                            _bookCollections.value = _bookCollections.value + newCollection.id
                        }
                    }
                }
            } catch (e: Exception) {
            }
        }
    }

    fun checkAiStatus() {
        viewModelScope.launch {
            try {
                val response = api.getAiStatus()
                if (response.isSuccessful) {
                    _isAiConfigured.value = response.body()?.configured ?: false
                }
            } catch (e: Exception) {
                _isAiConfigured.value = false
            }
        }
    }

    fun loadRecap() {
        viewModelScope.launch {
            _audiobook.value?.let { book ->
                if (_isLoadingRecap.value) return@launch
                _isLoadingRecap.value = true
                _recapError.value = null
                try {
                    val response = api.getAudiobookRecap(book.id)
                    if (response.isSuccessful) {
                        _recap.value = response.body()
                    } else {
                        val errorBody = response.errorBody()?.string()
                        val errorMessage = try {
                            val jsonError = com.google.gson.JsonParser.parseString(errorBody).asJsonObject
                            jsonError.get("error")?.asString ?: jsonError.get("message")?.asString
                        } catch (e: Exception) {
                            null
                        }
                        _recapError.value = errorMessage ?: "Failed to load recap"
                    }
                } catch (e: Exception) {
                    _recapError.value = e.message ?: "Error loading recap"
                } finally {
                    _isLoadingRecap.value = false
                }
            }
        }
    }

    fun clearRecap() {
        viewModelScope.launch {
            _audiobook.value?.let { book ->
                try {
                    api.clearAudiobookRecap(book.id)
                    _recap.value = null
                } catch (e: Exception) {
                }
            }
        }
    }

    fun dismissRecap() {
        _recap.value = null
        _recapError.value = null
    }

    fun checkPreviousBookStatus(audiobookId: Int) {
        viewModelScope.launch {
            try {
                val response = api.getPreviousBookStatus(audiobookId)
                if (response.isSuccessful) {
                    _previousBookCompleted.value = response.body()?.previousBookCompleted ?: false
                }
            } catch (e: Exception) {
                _previousBookCompleted.value = false
            }
        }
    }
}
