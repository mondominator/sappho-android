package com.sappho.audiobooks.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sappho.audiobooks.data.remote.SapphoApi
import com.sappho.audiobooks.data.remote.Collection
import com.sappho.audiobooks.data.remote.AddToCollectionRequest
import com.sappho.audiobooks.data.remote.CreateCollectionRequest
import com.sappho.audiobooks.data.repository.AuthRepository
import com.sappho.audiobooks.domain.model.Audiobook
import com.sappho.audiobooks.download.DownloadManager
import com.sappho.audiobooks.util.NetworkMonitor
import com.sappho.audiobooks.util.PerformanceMonitor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import android.util.Log
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.CancellationException
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

    companion object {
        private const val TAG = "HomeViewModel"
        // Cap the home-feed load so an unreachable-but-online server can't hang it.
        private const val FEED_TIMEOUT_MS = 10_000L
    }

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

    private fun loadData() {
        if (!networkMonitor.isOnline.value) {
            // Device itself has no network — offline, surface downloads.
            _isOffline.value = true
            return
        }

        viewModelScope.launch {
            performanceMonitor.measureTime("Home Data Load") {
                _isLoading.value = true
                // The device can be online while the SERVER is unreachable (it's
                // down, or we're off its network). Those calls would otherwise
                // block on OkHttp's read timeout (minutes) and leave Home spinning
                // forever, so cap the whole load. On timeout or total failure we
                // fall back to the offline UI (downloaded books).
                val reachedServer = try {
                    withTimeoutOrNull(FEED_TIMEOUT_MS) { loadHomeFeed() } ?: false
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load home data", e)
                    false
                } finally {
                    _isLoading.value = false
                    performanceMonitor.logMemoryUsage("After Home Data Load")
                }
                _isOffline.value = !reachedServer
            }
        }
    }

    /**
     * Loads the home-feed sections in parallel. Returns true if the server was
     * reachable (at least one request produced an HTTP response), false if every
     * request failed with a network error.
     */
    private suspend fun loadHomeFeed(): Boolean = coroutineScope {
        val favoritesDeferred = async { safeCall { api.getFavorites() } }
        val inProgressDeferred = async { safeCall { api.getInProgress(10) } }
        val recentlyAddedDeferred = async { safeCall { api.getRecentlyAdded(10) } }
        val upNextDeferred = async { safeCall { api.getUpNext(10) } }
        val finishedDeferred = async { safeCall { api.getFinished(10) } }

        // Favorites first so we can flag favorite books in the other lists.
        val favoritesResponse = favoritesDeferred.await()
        val favoriteIds = mutableSetOf<Int>()
        if (favoritesResponse?.isSuccessful == true) {
            favoriteIds.addAll(favoritesResponse.body()?.map { it.id } ?: emptyList())
        }

        fun List<Audiobook>.withFavoriteStatus(): List<Audiobook> =
            map { book -> if (favoriteIds.contains(book.id)) book.copy(isFavorite = true) else book }

        // A non-null response means we actually reached the server (even an HTTP
        // error counts); all-null means the server was unreachable.
        var reached = favoritesResponse != null

        val inProgressResponse = inProgressDeferred.await()
        if (inProgressResponse != null) reached = true
        if (inProgressResponse?.isSuccessful == true) {
            _inProgress.value = (inProgressResponse.body() ?: emptyList()).withFavoriteStatus()
        }

        val recentlyAddedResponse = recentlyAddedDeferred.await()
        if (recentlyAddedResponse != null) reached = true
        if (recentlyAddedResponse?.isSuccessful == true) {
            _recentlyAdded.value = (recentlyAddedResponse.body() ?: emptyList()).withFavoriteStatus()
        }

        val upNextResponse = upNextDeferred.await()
        if (upNextResponse != null) reached = true
        if (upNextResponse?.isSuccessful == true) {
            val upNextBooks = (upNextResponse.body() ?: emptyList()).withFavoriteStatus()
            _upNext.value = prioritizeUpNext(upNextBooks, _inProgress.value)
        }

        val finishedResponse = finishedDeferred.await()
        if (finishedResponse != null) reached = true
        if (finishedResponse?.isSuccessful == true) {
            _finished.value = (finishedResponse.body() ?: emptyList()).withFavoriteStatus()
        }

        if (reached) {
            // Server has the real positions from successful syncs — stale pending
            // items can be dropped.
            downloadManager.clearAllPendingProgress()
            syncStatusManager.updateSyncStatus(lastSyncSuccess = true)
        }
        reached
    }

    /**
     * Runs an API call, swallowing network errors (returns null) while letting
     * coroutine cancellation propagate — so the feed timeout actually cancels
     * the in-flight request instead of being caught and ignored.
     */
    private suspend fun <T> safeCall(block: suspend () -> retrofit2.Response<T>): retrofit2.Response<T>? =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
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
                Log.e(TAG, "Failed to toggle favorite", e)
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
                Log.e(TAG, "Failed to load collections for book", e)
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
                Log.e(TAG, "Failed to toggle book in collection", e)
            }
        }
    }

    fun createCollectionAndAddBook(name: String, bookId: Int, isPublic: Boolean = false) {
        viewModelScope.launch {
            try {
                val createResponse = api.createCollection(CreateCollectionRequest(name, null, isPublic))
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
                Log.e(TAG, "Failed to create collection and add book", e)
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
