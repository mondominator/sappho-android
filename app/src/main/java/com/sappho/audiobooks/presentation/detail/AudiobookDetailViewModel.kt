package com.sappho.audiobooks.presentation.detail

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sappho.audiobooks.data.remote.AverageRating
import com.sappho.audiobooks.data.remote.RatingRequest
import com.sappho.audiobooks.data.remote.SapphoApi
import com.sappho.audiobooks.data.remote.UserRating
import com.sappho.audiobooks.data.repository.AuthRepository
import com.sappho.audiobooks.domain.model.Audiobook
import com.sappho.audiobooks.domain.model.AudiobookFile
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

    private val _files = MutableStateFlow<List<AudiobookFile>>(emptyList())
    val files: StateFlow<List<AudiobookFile>> = _files

    private val _serverUrl = MutableStateFlow<String?>(null)
    val serverUrl: StateFlow<String?> = _serverUrl

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

    private val _averageRating = MutableStateFlow<AverageRating?>(null)
    val averageRating: StateFlow<AverageRating?> = _averageRating

    private val _isUpdatingRating = MutableStateFlow(false)
    val isUpdatingRating: StateFlow<Boolean> = _isUpdatingRating

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
                    val book = response.body()
                    _audiobook.value = book
                    _isFavorite.value = book?.isFavorite ?: false
                }

                // Load user's rating
                try {
                    val ratingResponse = api.getUserRating(id)
                    if (ratingResponse.isSuccessful) {
                        _userRating.value = ratingResponse.body()?.rating
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                // Load average rating
                try {
                    val avgResponse = api.getAverageRating(id)
                    if (avgResponse.isSuccessful) {
                        _averageRating.value = avgResponse.body()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                // Load progress separately
                try {
                    val progressResponse = api.getProgress(id)
                    if (progressResponse.isSuccessful) {
                        _progress.value = progressResponse.body()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                // Load chapters
                try {
                    val chaptersResponse = api.getChapters(id)
                    if (chaptersResponse.isSuccessful) {
                        _chapters.value = chaptersResponse.body() ?: emptyList()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                // Load files
                try {
                    val filesResponse = api.getFiles(id)
                    if (filesResponse.isSuccessful) {
                        _files.value = filesResponse.body() ?: emptyList()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            } catch (e: Exception) {
                // Network error - try to load from downloaded data
                e.printStackTrace()
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
                    e.printStackTrace()
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
                    e.printStackTrace()
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
                    e.printStackTrace()
                }
            }
        }
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
                    e.printStackTrace()
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
                            e.printStackTrace()
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
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
                            e.printStackTrace()
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    _isUpdatingRating.value = false
                }
            }
        }
    }
}
