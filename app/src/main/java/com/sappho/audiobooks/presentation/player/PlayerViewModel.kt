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
import com.sappho.audiobooks.cast.CastHelper
import com.sappho.audiobooks.cast.CastManager
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
    private val downloadManager: DownloadManager,
    private val castHelper: CastHelper,
    private val castManager: CastManager
) : AndroidViewModel(application) {

    companion object {
        private const val STALENESS_THRESHOLD_MS = 30_000L
    }

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
            }

            // Fall back to downloaded data if server failed
            if (book == null) {
                val downloadedBook = downloadManager.getDownloadedBook(audiobookId)
                book = downloadedBook?.audiobook
            }

            // If no explicit position was passed, use the book's saved progress
            if (actualStartPosition == 0 && book?.progress != null) {
                actualStartPosition = book.progress!!.position
            }

            // Defense-in-depth: if we still have no position, fetch progress separately
            if (actualStartPosition == 0 && book?.progress == null) {
                try {
                    val progressResponse = api.getProgress(audiobookId)
                    if (progressResponse.isSuccessful) {
                        val progress = progressResponse.body()
                        if (progress != null && progress.position > 0) {
                            actualStartPosition = progress.position
                        }
                    }
                } catch (_: Exception) {
                }
            }

            _audiobook.value = book

            book?.let {

                // Check if we're currently casting
                if (castManager.isCasting()) {
                    val serverUrl = authRepository.getServerUrlSync()
                    if (serverUrl != null) {
                        // Load the new audiobook on the cast device
                        castManager.castAudiobook(
                            audiobook = it,
                            streamUrl = "$serverUrl/api/audiobooks/${it.id}/stream",
                            coverUrl = if (it.coverImage != null) com.sappho.audiobooks.util.buildCoverUrl(serverUrl, it.id) else null,
                            positionSeconds = actualStartPosition.toLong()
                        )
                        // Also update the shared player state with the new audiobook info
                        sharedPlayerState.updateAudiobook(it)
                    }
                } else {
                    // Not casting - use local playback
                    // Start the service
                    val context = getApplication<Application>()
                    val serviceIntent = Intent(context, AudioPlaybackService::class.java)
                    context.startForegroundService(serviceIntent)

                    // Wait for service to be ready with retry logic
                    var retries = 0
                    val maxRetries = 20 // 2 seconds max wait
                    while (AudioPlaybackService.instance == null && retries < maxRetries) {
                        kotlinx.coroutines.delay(100)
                        retries++
                    }

                    val service = AudioPlaybackService.instance
                    if (service != null) {
                        service.loadAndPlay(it, actualStartPosition)
                    } else {
                        android.util.Log.e("PlayerViewModel", "Failed to get AudioPlaybackService instance after ${retries * 100}ms")
                    }
                }
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
            }

            // Fall back to downloaded data if server failed
            if (book == null) {
                val downloadedBook = downloadManager.getDownloadedBook(audiobookId)
                book = downloadedBook?.audiobook
            }

            _audiobook.value = book
        }
    }

    fun checkForUpdatedProgress(audiobookId: Int) {
        viewModelScope.launch {
            try {
                val response = api.getProgress(audiobookId)
                if (!response.isSuccessful) return@launch
                val progress = response.body() ?: return@launch

                // Don't override display for completed books
                if (progress.completed == 1) return@launch

                val serverPosition = progress.position.toLong()
                val localPosition = sharedPlayerState.currentPosition.value
                val service = AudioPlaybackService.instance

                // If actively playing, don't interrupt
                if (service != null && service.isCurrentlyPlaying()) return@launch

                // Service is dead — update PlayerState directly so UI shows
                // correct position and play button restart uses the right value
                if (service == null) {
                    if (serverPosition > 0 && serverPosition != localPosition) {
                        sharedPlayerState.updatePosition(serverPosition)
                    }
                    return@launch
                }

                // Service alive but paused — seek if position differs by >2s
                if (kotlin.math.abs(serverPosition - localPosition) > 2) {
                    service.seekTo(serverPosition)
                }
            } catch (_: Exception) {
                // Network failure — keep showing current state
            }
        }
    }

    fun togglePlayPauseWithGuard(audiobookId: Int) {
        val service = AudioPlaybackService.instance

        // Pause should always be immediate
        if (service != null && service.isCurrentlyPlaying()) {
            service.togglePlayPause()
            return
        }

        // Check if state is stale
        val lastActive = sharedPlayerState.lastActiveTimestamp.value
        val isStale = lastActive == 0L ||
            (System.currentTimeMillis() - lastActive) > STALENESS_THRESHOLD_MS

        if (!isStale) {
            // Recently active — safe to toggle immediately
            val handled = service?.togglePlayPause() ?: false
            if (!handled) {
                loadAndStartPlayback(audiobookId, sharedPlayerState.currentPosition.value.toInt())
            }
            return
        }

        // Stale — fetch server progress first, then play
        viewModelScope.launch {
            var bestPosition = sharedPlayerState.currentPosition.value.toInt()

            try {
                val response = api.getProgress(audiobookId)
                if (response.isSuccessful) {
                    val progress = response.body()
                    if (progress != null && progress.completed != 1 && progress.position > 0) {
                        bestPosition = progress.position
                    }
                }
            } catch (_: Exception) {
                // Network failed — use local position
            }

            val svc = AudioPlaybackService.instance
            if (svc != null) {
                if (kotlin.math.abs(bestPosition.toLong() - sharedPlayerState.currentPosition.value) > 2) {
                    svc.seekTo(bestPosition.toLong())
                }
                svc.togglePlayPause()
            } else {
                loadAndStartPlayback(audiobookId, bestPosition)
            }
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
                val downloadedBook = downloadManager.getDownloadedBook(audiobookId)
                if (downloadedBook != null && downloadedBook.chapters.isNotEmpty()) {
                    _chapters.value = downloadedBook.chapters
                }
            }
        }
    }
}
