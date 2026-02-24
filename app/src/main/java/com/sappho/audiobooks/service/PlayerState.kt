package com.sappho.audiobooks.service

import com.sappho.audiobooks.domain.model.Audiobook
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlayerState @Inject constructor() {
    private val _currentAudiobook = MutableStateFlow<Audiobook?>(null)
    val currentAudiobook: StateFlow<Audiobook?> = _currentAudiobook

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed

    private val _sleepTimerRemaining = MutableStateFlow<Long?>(null)
    val sleepTimerRemaining: StateFlow<Long?> = _sleepTimerRemaining

    private val _bufferedPosition = MutableStateFlow(0L)
    val bufferedPosition: StateFlow<Long> = _bufferedPosition

    private val _lastActiveTimestamp = MutableStateFlow(0L)
    val lastActiveTimestamp: StateFlow<Long> = _lastActiveTimestamp

    fun updateLastActiveTimestamp() {
        _lastActiveTimestamp.value = System.currentTimeMillis()
    }

    fun updateAudiobook(audiobook: Audiobook?) {
        _currentAudiobook.value = audiobook
    }

    fun updatePlayingState(isPlaying: Boolean) {
        _isPlaying.value = isPlaying
    }

    fun updatePosition(position: Long) {
        _currentPosition.value = position
    }

    fun updateDuration(duration: Long) {
        _duration.value = duration
    }

    fun updateLoadingState(isLoading: Boolean) {
        _isLoading.value = isLoading
    }

    fun updatePlaybackSpeed(speed: Float) {
        _playbackSpeed.value = speed
    }

    fun updateSleepTimerRemaining(remaining: Long?) {
        _sleepTimerRemaining.value = remaining
    }

    fun updateBufferedPosition(position: Long) {
        _bufferedPosition.value = position
    }

    /**
     * Mark playback as inactive while preserving position, duration, and audiobook
     * for UI display. Called when the service times out or is destroyed — the player
     * screen may still be visible and needs to show the last known state.
     */
    fun deactivate() {
        _isPlaying.value = false
        _isLoading.value = false
        _sleepTimerRemaining.value = null
        _bufferedPosition.value = 0L
        _lastActiveTimestamp.value = 0L
    }

    fun clear() {
        _currentAudiobook.value = null
        _isPlaying.value = false
        _currentPosition.value = 0L
        _duration.value = 0L
        _isLoading.value = false
        _playbackSpeed.value = 1.0f
        _sleepTimerRemaining.value = null
        _bufferedPosition.value = 0L
        _lastActiveTimestamp.value = 0L
    }
}
