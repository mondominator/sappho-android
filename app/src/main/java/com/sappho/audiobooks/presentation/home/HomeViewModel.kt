package com.sappho.audiobooks.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sappho.audiobooks.data.remote.SapphoApi
import com.sappho.audiobooks.data.repository.AuthRepository
import com.sappho.audiobooks.domain.model.Audiobook
import com.sappho.audiobooks.download.DownloadManager
import com.sappho.audiobooks.util.NetworkMonitor
import dagger.hilt.android.lifecycle.HiltViewModel
import com.sappho.audiobooks.data.remote.ProgressUpdateRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val api: SapphoApi,
    private val authRepository: AuthRepository,
    val downloadManager: DownloadManager,
    private val networkMonitor: NetworkMonitor
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

    private val _isOffline = MutableStateFlow(!networkMonitor.isOnline.value)
    val isOffline: StateFlow<Boolean> = _isOffline

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
                    // Just came online - sync pending progress first, then refresh data
                    if (wasOffline) {
                        syncPendingProgress()
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

        android.util.Log.d("HomeViewModel", "Syncing ${pendingList.size} pending progress entries")

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
                android.util.Log.d("HomeViewModel", "Synced pending progress for book ${pending.audiobookId}: position ${pending.position}")
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
            _isLoading.value = true
            try {
                // Load in progress audiobooks
                val inProgressResponse = api.getInProgress(10)
                if (inProgressResponse.isSuccessful) {
                    _inProgress.value = inProgressResponse.body() ?: emptyList()
                }

                // Load recently added audiobooks
                val recentlyAddedResponse = api.getRecentlyAdded(10)
                if (recentlyAddedResponse.isSuccessful) {
                    _recentlyAdded.value = recentlyAddedResponse.body() ?: emptyList()
                }

                // Load up next audiobooks
                val upNextResponse = api.getUpNext(10)
                if (upNextResponse.isSuccessful) {
                    val upNextBooks = upNextResponse.body() ?: emptyList()
                    // Prioritize next book in series of the currently playing book
                    _upNext.value = prioritizeUpNext(upNextBooks, _inProgress.value)
                }

                // Load finished audiobooks
                val finishedResponse = api.getFinished(10)
                if (finishedResponse.isSuccessful) {
                    _finished.value = finishedResponse.body() ?: emptyList()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun refresh() {
        loadData()
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
}
