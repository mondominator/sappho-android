package com.sappho.audiobooks.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sappho.audiobooks.data.remote.SapphoApi
import com.sappho.audiobooks.data.remote.Collection
import com.sappho.audiobooks.data.remote.AddToCollectionRequest
import com.sappho.audiobooks.data.remote.CreateCollectionRequest
import com.sappho.audiobooks.data.remote.ProgressUpdateRequest
import com.sappho.audiobooks.data.repository.AuthRepository
import com.sappho.audiobooks.domain.model.Audiobook
import com.sappho.audiobooks.download.DownloadManager
import com.sappho.audiobooks.util.NetworkMonitor
import com.sappho.audiobooks.util.PerformanceMonitor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import com.sappho.audiobooks.sync.SyncStatusManager
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val api: SapphoApi,
    private val authRepository: AuthRepository,
    val downloadManager: DownloadManager,  // Make public for HomeScreen access
    private val networkMonitor: NetworkMonitor,
    private val syncStatusManager: SyncStatusManager,
    private val performanceMonitor: PerformanceMonitor
) : ViewModel() {

    private val _inProgress = MutableStateFlow<List<Audiobook>>(emptyList())
    val inProgress: StateFlow<List<Audiobook>> = _inProgress

    private val _recentlyAdded = MutableStateFlow<List<Audiobook>>(emptyList())
    val recentlyAdded: StateFlow<List<Audiobook>> = _recentlyAdded

    private val _upNext = MutableStateFlow<List<Audiobook>>(emptyList())
    val upNext: StateFlow<List<Audiobook>> = _upNext

    private val _finished = MutableStateFlow<List<Audiobook>>(emptyList())
    val finished: StateFlow<List<Audiobook>> = _finished

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _serverUrl = MutableStateFlow<String?>(null)
    val serverUrl: StateFlow<String?> = _serverUrl

    private val _isOffline = MutableStateFlow(false)
    val isOffline: StateFlow<Boolean> = _isOffline
    
    // Expose sync status to UI
    val syncStatus: StateFlow<com.sappho.audiobooks.sync.SyncStatus> = syncStatusManager.syncStatus

    // Collections state
    private val _collections = MutableStateFlow<List<Collection>>(emptyList())
    val collections: StateFlow<List<Collection>> = _collections

    private val _bookCollections = MutableStateFlow<Set<Int>>(emptySet())
    val bookCollections: StateFlow<Set<Int>> = _bookCollections

    private val _isLoadingCollections = MutableStateFlow(false)
    val isLoadingCollections: StateFlow<Boolean> = _isLoadingCollections

    init {
        _serverUrl.value = authRepository.getServerUrlSync()
        observeNetworkAndLoad()
    }

    private fun observeNetworkAndLoad() {
        viewModelScope.launch {
            // Observe network changes
            networkMonitor.isOnline.collectLatest { isOnline ->
                val wasOffline = _isOffline.value
                _isOffline.value = !isOnline

                if (isOnline) {
                    // Just came online - trigger sync and refresh data
                    if (wasOffline) {
                        syncStatusManager.triggerSync()
                    }
                    if (wasOffline || _inProgress.value.isEmpty()) {
                        loadData()
                    }
                }
            }
        }
    }

    private suspend fun syncPendingProgress() {
        val pendingList = downloadManager.getPendingProgressList()
        if (pendingList.isEmpty()) return


        for (pending in pendingList) {
            try {
                api.updateProgress(
                    pending.audiobookId,
                    ProgressUpdateRequest(
                        position = pending.position,
                        completed = 0,
                        state = "paused"
                    )
                )
                // Successfully synced - clear this pending progress
                downloadManager.clearPendingProgress(pending.audiobookId)
            } catch (e: Exception) {
                android.util.Log.e("HomeViewModel", "Failed to sync pending progress for book ${pending.audiobookId}", e)
                // Continue trying other entries
            }
        }
    }

    private fun loadData() {
        if (!networkMonitor.isOnline.value) {
            // Don't try to load if we know we're offline
            return
        }

        viewModelScope.launch {
            performanceMonitor.measureTime("Home Data Load") {
                _isLoading.value = true
                try {
                // Load all data in parallel for better performance
                val favoritesDeferred = async {
                    try {
                        api.getFavorites()
                    } catch (e: Exception) {
                        null
                    }
                }
                
                val inProgressDeferred = async { 
                    try {
                        api.getInProgress(10)
                    } catch (e: Exception) {
                        null
                    }
                }
                
                val recentlyAddedDeferred = async { 
                    try {
                        api.getRecentlyAdded(10) 
                    } catch (e: Exception) {
                        null
                    }
                }
                
                val upNextDeferred = async { 
                    try {
                        api.getUpNext(10)
                    } catch (e: Exception) {
                        null
                    }
                }
                
                val finishedDeferred = async {
                    try {
                        api.getFinished(10)
                    } catch (e: Exception) {
                        null
                    }
                }

                // Wait for favorites first to apply status to other lists
                val favoriteIds = mutableSetOf<Int>()
                val favoritesResponse = favoritesDeferred.await()
                if (favoritesResponse?.isSuccessful == true) {
                    favoriteIds.addAll(favoritesResponse.body()?.map { it.id } ?: emptyList())
                }

                // Helper function to apply favorite status to book lists
                fun List<Audiobook>.withFavoriteStatus(): List<Audiobook> {
                    return map { book ->
                        if (favoriteIds.contains(book.id)) book.copy(isFavorite = true) else book
                    }
                }

                // Process all responses in parallel
                val inProgressResponse = inProgressDeferred.await()
                if (inProgressResponse?.isSuccessful == true) {
                    _inProgress.value = (inProgressResponse.body() ?: emptyList()).withFavoriteStatus()
                }

                val recentlyAddedResponse = recentlyAddedDeferred.await()
                if (recentlyAddedResponse?.isSuccessful == true) {
                    _recentlyAdded.value = (recentlyAddedResponse.body() ?: emptyList()).withFavoriteStatus()
                }

                val upNextResponse = upNextDeferred.await()
                if (upNextResponse?.isSuccessful == true) {
                    val upNextBooks = (upNextResponse.body() ?: emptyList()).withFavoriteStatus()
                    // Prioritize next book in series of the currently playing book
                    _upNext.value = prioritizeUpNext(upNextBooks, _inProgress.value)
                }

                val finishedResponse = finishedDeferred.await()
                if (finishedResponse?.isSuccessful == true) {
                    _finished.value = (finishedResponse.body() ?: emptyList()).withFavoriteStatus()
                }
            } catch (e: Exception) {
                } finally {
                    _isLoading.value = false
                    performanceMonitor.logMemoryUsage("After Home Data Load")
                }
            }
        }
    }

    fun refresh() {
        loadData()
    }

    fun toggleFavorite(audiobookId: Int, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            try {
                val response = api.toggleFavorite(audiobookId)
                if (response.isSuccessful) {
                    val isFavorite = response.body()?.isFavorite ?: false
                    // Update the book's favorite status in all lists
                    updateFavoriteStatus(audiobookId, isFavorite)
                    onResult(isFavorite)
                }
            } catch (e: Exception) {
            }
        }
    }

    private fun updateFavoriteStatus(audiobookId: Int, isFavorite: Boolean) {
        _inProgress.value = _inProgress.value.map { book ->
            if (book.id == audiobookId) book.copy(isFavorite = isFavorite) else book
        }
        _recentlyAdded.value = _recentlyAdded.value.map { book ->
            if (book.id == audiobookId) book.copy(isFavorite = isFavorite) else book
        }
        _upNext.value = _upNext.value.map { book ->
            if (book.id == audiobookId) book.copy(isFavorite = isFavorite) else book
        }
        _finished.value = _finished.value.map { book ->
            if (book.id == audiobookId) book.copy(isFavorite = isFavorite) else book
        }
    }

    /**
     * Prioritizes the Up Next list by moving the next book in the currently playing
     * book's series to the front of the list.
     */
    private fun prioritizeUpNext(upNextBooks: List<Audiobook>, inProgressBooks: List<Audiobook>): List<Audiobook> {
        if (upNextBooks.isEmpty() || inProgressBooks.isEmpty()) {
            return upNextBooks
        }

        // Get the first in-progress book (most recently listened)
        val currentBook = inProgressBooks.firstOrNull() ?: return upNextBooks
        val currentSeries = currentBook.series ?: return upNextBooks
        val currentPosition = currentBook.seriesPosition ?: return upNextBooks

        // Find the next book in the same series (position > current position)
        val nextInSeries = upNextBooks.filter { book ->
            book.series == currentSeries &&
            (book.seriesPosition ?: 0f) > currentPosition
        }.minByOrNull { it.seriesPosition ?: Float.MAX_VALUE }

        if (nextInSeries == null) {
            return upNextBooks
        }

        // Move the next book in series to the front
        val reordered = mutableListOf(nextInSeries)
        reordered.addAll(upNextBooks.filter { it.id != nextInSeries.id })

        return reordered
    }

    // Collection functions
    fun loadCollectionsForBook(bookId: Int) {
        viewModelScope.launch {
            _isLoadingCollections.value = true
            try {
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
    
    fun triggerManualSync() {
        syncStatusManager.triggerSync()
    }
    
    fun clearSyncError() {
        syncStatusManager.clearErrorMessage()
    }

    fun clearAllDownloadErrors() {
        downloadManager.clearAllDownloadErrors()
    }
}
