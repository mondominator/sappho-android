package com.sappho.audiobooks.presentation.player

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sappho.audiobooks.data.remote.SapphoApi
import com.sappho.audiobooks.data.repository.AuthRepository
import com.sappho.audiobooks.domain.model.Audiobook
import com.sappho.audiobooks.domain.model.Chapter
import com.sappho.audiobooks.download.DownloadManager
import com.sappho.audiobooks.service.AudioPlaybackService
import com.sappho.audiobooks.service.PlayerState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    application: Application,
    private val api: SapphoApi,
    private val authRepository: AuthRepository,
    private val sharedPlayerState: PlayerState,
    private val downloadManager: DownloadManager
) : AndroidViewModel(application) {

    private val _audiobook = MutableStateFlow<Audiobook?>(null)
    val audiobook: StateFlow<Audiobook?> = _audiobook

    private val _chapters = MutableStateFlow<List<Chapter>>(emptyList())
    val chapters: StateFlow<List<Chapter>> = _chapters

    private val _playerState = MutableStateFlow<PlayerState?>(null)
    val playerState: StateFlow<PlayerState?> = _playerState

    private val _serverUrl = MutableStateFlow<String?>(null)
    val serverUrl: StateFlow<String?> = _serverUrl

    init {
        _serverUrl.value = authRepository.getServerUrlSync()
        _playerState.value = sharedPlayerState
    }

    fun loadAndStartPlayback(audiobookId: Int, startPosition: Int) {
        android.util.Log.d("PlayerViewModel", "loadAndStartPlayback called with audiobookId: $audiobookId, startPosition: $startPosition")
        viewModelScope.launch {
            var book: Audiobook? = null
            var actualStartPosition = startPosition

            // Try to load from server first
            try {
                val response = api.getAudiobook(audiobookId)
                if (response.isSuccessful) {
                    book = response.body()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // Fall back to downloaded data if server failed
            if (book == null) {
                val downloadedBook = downloadManager.getDownloadedBook(audiobookId)
                book = downloadedBook?.audiobook
                // If we're using downloaded data and no position was passed, use the stored progress
                if (actualStartPosition == 0 && book?.progress != null) {
                    actualStartPosition = book.progress!!.position
                }
            }

            _audiobook.value = book

            book?.let {
                android.util.Log.d("PlayerViewModel", "About to start playback with actualStartPosition: $actualStartPosition (original was: $startPosition)")
                // Start the service
                val context = getApplication<Application>()
                val serviceIntent = Intent(context, AudioPlaybackService::class.java)
                context.startForegroundService(serviceIntent)

                // Wait a moment for service to be created
                kotlinx.coroutines.delay(500)

                // Load and play through the service
                AudioPlaybackService.instance?.loadAndPlay(it, actualStartPosition)
            }
        }
    }

    fun loadAudiobookDetails(audiobookId: Int) {
        viewModelScope.launch {
            var book: Audiobook? = null

            // Try to load from server first
            try {
                val response = api.getAudiobook(audiobookId)
                if (response.isSuccessful) {
                    book = response.body()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // Fall back to downloaded data if server failed
            if (book == null) {
                val downloadedBook = downloadManager.getDownloadedBook(audiobookId)
                book = downloadedBook?.audiobook
            }

            _audiobook.value = book
        }
    }

    fun loadChapters(audiobookId: Int) {
        viewModelScope.launch {
            try {
                val response = api.getChapters(audiobookId)
                if (response.isSuccessful) {
                    _chapters.value = response.body() ?: emptyList()
                }
            } catch (e: Exception) {
                // Offline - try to load chapters from downloaded data
                e.printStackTrace()
                val downloadedBook = downloadManager.getDownloadedBook(audiobookId)
                if (downloadedBook != null && downloadedBook.chapters.isNotEmpty()) {
                    _chapters.value = downloadedBook.chapters
                }
            }
        }
    }
}
