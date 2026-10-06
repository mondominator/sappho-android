package com.sappho.audiobooks.service

import com.sappho.audiobooks.cast.CastManager
import com.sappho.audiobooks.data.repository.UserPreferencesRepository
import com.sappho.audiobooks.domain.model.ListeningSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Transport controls of the on-device player (implemented by AudioPlaybackService). */
interface LocalPlayback {
    /** Returns false when there is no player to toggle (caller should restart playback). */
    fun togglePlayPause(): Boolean
    /** Seek to [seconds] into the book. */
    fun seekTo(seconds: Long)
    fun seekToAndPlay(seconds: Long)
    fun skipForward()
    fun skipBackward()
    /**
     * Stop and release for logout. With [syncFinalPosition] the position is
     * sent to the server first; otherwise (expired session, the server would
     * reject it) it is queued under the current account for a later replay.
     */
    suspend fun stopForLogout(syncFinalPosition: Boolean)
}

/**
 * The one place UI transport controls go through. While a cast session is
 * active every control targets the receiver; otherwise the local player.
 * Without this, full-player skip/seek and the mini bar drove the hidden local
 * player during Kodi/AirPlay casts, starting a second copy of the audio.
 */
@Singleton
class PlaybackController @Inject constructor(
    private val castManager: CastManager,
    private val userPreferences: UserPreferencesRepository
) {
    // Overridable in tests; production reads the running service.
    internal var localPlayback: () -> LocalPlayback? = { AudioPlaybackService.instance }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** True while connected to any cast receiver (Chromecast, Kodi, AirPlay). */
    val isCastConnected: StateFlow<Boolean> get() = castManager.isConnected
    val castIsPlaying: StateFlow<Boolean> get() = castManager.isPlaying
    val castPosition: StateFlow<Long> get() = castManager.currentPosition

    fun isCasting(): Boolean = castManager.isCasting()

    /**
     * Play/pause the active player. Returns false only when neither a cast
     * session nor a local player exists, so the caller restarts playback.
     */
    fun togglePlayPause(): Boolean {
        if (isCasting()) {
            val playing = castManager.isPlaying.value
            scope.launch { if (playing) castManager.pause() else castManager.play() }
            return true
        }
        return localPlayback()?.togglePlayPause() ?: false
    }

    /** Seek to [seconds] into the book (seconds, not milliseconds). */
    fun seekTo(seconds: Long) {
        if (isCasting()) {
            scope.launch { castManager.seek(seconds) }
        } else {
            localPlayback()?.seekTo(seconds)
        }
    }

    fun seekToAndPlay(seconds: Long) {
        if (isCasting()) {
            scope.launch {
                castManager.seek(seconds)
                if (!castManager.isPlaying.value) castManager.play()
            }
        } else {
            localPlayback()?.seekToAndPlay(seconds)
        }
    }

    fun skipForward() {
        if (isCasting()) {
            val skip = userPreferences.skipForwardSeconds.value.toLong()
            scope.launch { castManager.skipBy(skip) }
        } else {
            localPlayback()?.skipForward()
        }
    }

    fun skipBackward() {
        if (isCasting()) {
            val skip = userPreferences.skipBackwardSeconds.value.toLong()
            scope.launch { castManager.skipBy(-skip) }
        } else {
            localPlayback()?.skipBackward()
        }
    }

    /**
     * Jump to where a Listening History session started. The server stores
     * `start_position` in SECONDS, which is what [seekTo] takes.
     */
    fun seekToHistorySession(session: ListeningSession) {
        seekTo(session.startPosition.toLong())
    }

    /** Stop everything for logout: final local sync + stop, then end any cast. */
    suspend fun stopAllForLogout(syncFinalPosition: Boolean = true) {
        localPlayback()?.stopForLogout(syncFinalPosition)
        if (castManager.isCasting()) {
            try {
                castManager.stop()
            } finally {
                castManager.disconnect()
            }
        }
    }
}
