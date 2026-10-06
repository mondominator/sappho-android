package com.sappho.audiobooks.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.app.NotificationCompat
import android.net.Uri
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes as Media3AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaStyleNotificationHelper
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.sappho.audiobooks.R
import com.sappho.audiobooks.data.remote.ProgressUpdateRequest
import com.sappho.audiobooks.data.remote.SapphoApi
import com.sappho.audiobooks.data.repository.AuthRepository
import com.sappho.audiobooks.data.repository.UserPreferencesRepository
import com.sappho.audiobooks.domain.model.Audiobook
import com.sappho.audiobooks.download.DownloadManager
import com.sappho.audiobooks.presentation.theme.Timing
import com.sappho.audiobooks.sync.PendingProgressReplayer
import com.sappho.audiobooks.sync.ProgressPolicy
import com.sappho.audiobooks.sync.ProgressSyncWorker
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@AndroidEntryPoint
class AudioPlaybackService : MediaLibraryService(), LocalPlayback {

    @Inject
    lateinit var api: SapphoApi

    @Inject
    lateinit var authRepository: AuthRepository

    @Inject
    lateinit var playerState: PlayerState

    @Inject
    lateinit var downloadManager: DownloadManager

    @Inject
    lateinit var okHttpClient: okhttp3.OkHttpClient

    @Inject
    lateinit var userPreferences: UserPreferencesRepository

    @Inject
    lateinit var pendingProgressReplayer: PendingProgressReplayer

    @Inject
    lateinit var castManager: com.sappho.audiobooks.cast.CastManager

    private var player: ExoPlayer? = null
    // @Volatile: written from a background coroutine (cover fetch) and read on
    // the main thread when building notifications — without it the main thread
    // may never observe the loaded bitmap.
    @Volatile
    private var currentCoverBitmap: android.graphics.Bitmap? = null
    private var mediaLibrarySession: MediaLibrarySession? = null
    // SupervisorJob: one failing child (a sync, a cover fetch) must not cancel
    // every other loop for the rest of the service's life.
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var progressSyncJob: Job? = null
    private var positionUpdateJob: Job? = null
    private var sleepTimerJob: Job? = null
    private var pauseTimeoutJob: Job? = null

    // Track playback session start time for progress sync delay
    private var playbackSessionStartTime: Long = 0L

    // Track chapter index for end-of-chapter sleep timer
    private var previousChapterIndex: Int = -1
    private var sleepAtEndOfChapter: Boolean = false

    // Track whether current playback is from a local/downloaded file (true offline support)
    // vs streaming from server (transient API errors should not create pending sync items)
    private var isPlayingLocalFile: Boolean = false

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "audiobook_playback"
        private const val ROOT_ID = "__ROOT__"
        private const val RECENT_ID = "__RECENT__"
        private const val ALL_BOOKS_ID = "__ALL_BOOKS__"
        private const val CONTINUE_LISTENING_ID = "__CONTINUE_LISTENING__"
        private const val CHAPTERS_PREFIX = "__CHAPTERS__"

        // Minimum time (in seconds) before first progress sync
        // This prevents recording progress for accidental plays or quick skips
        private const val INITIAL_PROGRESS_DELAY_SECONDS = 20

        // How long to keep the service alive after pausing before self-stopping
        private const val PAUSE_TIMEOUT_MS = 30 * 60 * 1000L  // 30 minutes

        // Custom command actions
        const val ACTION_SKIP_FORWARD = "com.sappho.audiobooks.SKIP_FORWARD"
        const val ACTION_SKIP_BACKWARD = "com.sappho.audiobooks.SKIP_BACKWARD"
        const val ACTION_PLAY_PAUSE = "com.sappho.audiobooks.PLAY_PAUSE"

        /**
         * Intent action used when the app starts this service with
         * startForegroundService(). onStartCommand promotes the service to the
         * foreground immediately, before any work that could return early, so
         * the platform's 10 s startForeground() deadline is always met.
         */
        const val ACTION_PREPARE_PLAYBACK = "com.sappho.audiobooks.PREPARE_PLAYBACK"

        @Volatile
        var instance: AudioPlaybackService? = null
            private set

        /**
         * Clamps a skip-forward target to the book duration. When the duration
         * is not yet known (<= 0, e.g. still buffering), the raw target is
         * returned unclamped — clamping against 0 would seek back to the start.
         */
        internal fun clampSkipForwardPosition(targetSeconds: Long, durationSeconds: Long): Long {
            return if (durationSeconds > 0) targetSeconds.coerceAtMost(durationSeconds) else targetSeconds
        }
    }

    // Cache for audiobooks to avoid re-fetching
    private val audiobookCache = mutableMapOf<Int, Audiobook>()

    // Search results for Android Auto search
    private var lastSearchResults: List<MediaItem> = emptyList()

    // Books resolved for a session controller (Android Auto, Assistant,
    // headset resumption) whose item Media3 is about to load. When the item
    // becomes current, adoptSessionPlayback() wires up state and syncing.
    private val pendingSessionBooks = mutableMapOf<String, Audiobook>()

    private inner class NotificationActionReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_SKIP_FORWARD -> skipForward()
                ACTION_SKIP_BACKWARD -> skipBackward()
                ACTION_PLAY_PAUSE -> togglePlayPause()
            }
        }
    }

    private var notificationActionReceiver: NotificationActionReceiver? = null

    /**
     * ForwardingPlayer that intercepts previous/next commands and converts them to seek back/forward.
     * This makes the system media controls (lock screen, notification) perform 15-second skips
     * instead of track navigation, which is more appropriate for audiobooks.
     */
    private inner class AudiobookForwardingPlayer(player: Player) : ForwardingPlayer(player) {
        override fun getAvailableCommands(): Player.Commands {
            // Expose SEEK_TO_PREVIOUS/NEXT so system shows previous/next buttons
            // We intercept these to perform 15-second skips instead of track navigation
            return super.getAvailableCommands().buildUpon()
                .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                .add(Player.COMMAND_SEEK_TO_NEXT)
                .build()
        }

        override fun isCommandAvailable(command: Int): Boolean {
            return when (command) {
                Player.COMMAND_SEEK_TO_PREVIOUS,
                Player.COMMAND_SEEK_TO_NEXT -> true
                else -> super.isCommandAvailable(command)
            }
        }

        // Play/pause from a headset, the lock screen or Android Auto while a
        // cast session is active controls the receiver; the phone must not
        // start a second copy of the audio.
        override fun play() {
            if (castManager.isCasting()) {
                serviceScope.launch { castManager.play() }
            } else {
                super.play()
            }
        }

        override fun pause() {
            if (castManager.isCasting()) {
                serviceScope.launch { castManager.pause() }
            } else {
                super.pause()
            }
        }

        override fun seekToPrevious() {
            // Instead of going to previous track, seek back 15 seconds
            seekBack()
        }

        override fun seekToNext() {
            // Instead of going to next track, seek forward 15 seconds
            seekForward()
        }

        override fun seekToPreviousMediaItem() {
            seekBack()
        }

        override fun seekToNextMediaItem() {
            seekForward()
        }
    }

    private var forwardingPlayer: AudiobookForwardingPlayer? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()

        // Use Media3's default notification provider for proper system media controls
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setChannelId(CHANNEL_ID)
                .setChannelName(R.string.app_name)
                .build()
        )

        initializePlayer()
        registerNotificationActionReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_PREPARE_PLAYBACK) {
            // Satisfy startForegroundService() right away. loadAndPlay() can
            // bail (no token, nothing to play); doing this first means it can
            // never leave the service past its foreground deadline.
            startForeground(NOTIFICATION_ID, createNotification())
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun registerNotificationActionReceiver() {
        notificationActionReceiver = NotificationActionReceiver()
        val filter = IntentFilter().apply {
            addAction(ACTION_SKIP_FORWARD)
            addAction(ACTION_SKIP_BACKWARD)
            addAction(ACTION_PLAY_PAUSE)
        }
        ContextCompat.registerReceiver(
            this,
            notificationActionReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    private fun unregisterNotificationActionReceiver() {
        notificationActionReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (e: Exception) {
                // Receiver may not be registered
            }
        }
        notificationActionReceiver = null
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Audiobook Playback",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Controls for audiobook playback"
            setShowBadge(false)
        }
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    private fun initializePlayer() {
        val skipBackMs = userPreferences.skipBackwardSeconds.value * 1000L
        val skipForwardMs = userPreferences.skipForwardSeconds.value * 1000L
        val bufferSizeMs = userPreferences.bufferSizeSeconds.value * 1000

        // Configure buffer based on user preferences
        val loadControl = androidx.media3.exoplayer.DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs */ 15000,  // Minimum buffer before playback starts
                /* maxBufferMs */ bufferSizeMs,  // Maximum buffer size from settings
                /* bufferForPlaybackMs */ 2500,  // Buffer required to start playback
                /* bufferForPlaybackAfterRebufferMs */ 5000  // Buffer after rebuffer
            )
            .build()

        // Speech content: Media3 requests audio focus on play (from ANY
        // controller: app, Bluetooth, lock screen, Android Auto), pauses on
        // transient loss and for "can duck" (speech shouldn't be talked over),
        // resumes on regain, and pauses when headphones disconnect.
        val media3AudioAttributes = Media3AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
            .build()

        player = ExoPlayer.Builder(this)
            .setSeekBackIncrementMs(skipBackMs)
            .setSeekForwardIncrementMs(skipForwardMs)
            .setLoadControl(loadControl)
            .setAudioAttributes(media3AudioAttributes, /* handleAudioFocus= */ true)
            .setHandleAudioBecomingNoisy(true)
            .setMediaSourceFactory(
                androidx.media3.exoplayer.source.DefaultMediaSourceFactory(
                    androidx.media3.datasource.DefaultDataSource.Factory(
                        this,
                        androidx.media3.datasource.okhttp.OkHttpDataSource.Factory(okHttpClient)
                    )
                )
            )
            .build().apply {
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    val stateName = when (playbackState) {
                        Player.STATE_IDLE -> "IDLE"
                        Player.STATE_BUFFERING -> "BUFFERING"
                        Player.STATE_READY -> "READY"
                        Player.STATE_ENDED -> "ENDED"
                        else -> "UNKNOWN($playbackState)"
                    }
                    android.util.Log.d("AudioPlaybackService", "onPlaybackStateChanged: $stateName, duration=${duration}ms, position=${currentPosition}ms")

                    when (playbackState) {
                        Player.STATE_READY -> {
                            playerState.updateDuration(duration / 1000)
                            playerState.updateLoadingState(false)
                        }
                        Player.STATE_BUFFERING -> {
                            playerState.updateLoadingState(true)
                        }
                        Player.STATE_ENDED -> {
                            playerState.updatePlayingState(false)
                            handlePlaybackEnded(currentPosition / 1000)
                        }
                        Player.STATE_IDLE -> {
                            playerState.updateLoadingState(false)
                        }
                        else -> {}
                    }
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    android.util.Log.d("AudioPlaybackService", "onIsPlayingChanged: $isPlaying")
                    playerState.updatePlayingState(isPlaying)
                    playerState.updateLastActiveTimestamp()
                    if (isPlaying) {
                        startPositionUpdates()
                        pauseTimeoutJob?.cancel()
                    } else {
                        stopPositionUpdates()
                        syncProgressImmediate() // Always sync on pause — no delay guard
                        // Re-assert foreground status so system doesn't kill us
                        startForeground(NOTIFICATION_ID, createNotification())
                        // Stop service after 30 minutes of inactivity
                        pauseTimeoutJob?.cancel()
                        pauseTimeoutJob = serviceScope.launch {
                            delay(PAUSE_TIMEOUT_MS)
                            stopPlayback()
                        }
                    }
                    updateNotification()
                }

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    val id = mediaItem?.mediaId ?: return
                    val book = pendingSessionBooks.remove(id) ?: return
                    adoptSessionPlayback(book)
                }

                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    android.util.Log.e("AudioPlaybackService", "Player error: ${error.message}", error)
                    playerState.updateLoadingState(false)
                    playerState.updatePlayingState(false)
                    // Surface the error through PlayerState so the UI can show a
                    // dismissible dialog — a Toast from a service is easy to miss
                    // and gives the user no way to acknowledge the failure.
                    playerState.updatePlaybackError(
                        "Playback error: ${error.message ?: "Unknown error"}"
                    )
                }
            })
        }

        // Create command buttons for notification using standard player commands
        // This helps the notification provider recognize them as seek buttons
        val skipBackSeconds = userPreferences.skipBackwardSeconds.value
        val skipForwardSeconds = userPreferences.skipForwardSeconds.value

        val seekBackButton = CommandButton.Builder()
            .setDisplayName("Rewind ${skipBackSeconds}s")
            .setIconResId(R.drawable.ic_replay_15)
            .setPlayerCommand(Player.COMMAND_SEEK_BACK)
            .build()

        val playPauseButton = CommandButton.Builder()
            .setDisplayName("Play/Pause")
            .setIconResId(R.drawable.ic_play)
            .setPlayerCommand(Player.COMMAND_PLAY_PAUSE)
            .build()

        val seekForwardButton = CommandButton.Builder()
            .setDisplayName("Forward ${skipForwardSeconds}s")
            .setIconResId(R.drawable.ic_forward_15)
            .setPlayerCommand(Player.COMMAND_SEEK_FORWARD)
            .build()

        // Wrap the player with ForwardingPlayer to intercept previous/next as seek back/forward
        forwardingPlayer = AudiobookForwardingPlayer(player!!)

        mediaLibrarySession = MediaLibrarySession.Builder(this, forwardingPlayer!!, MediaLibrarySessionCallback())
            .setCustomLayout(listOf(seekBackButton, playPauseButton, seekForwardButton))
            .build()
    }

    private inner class MediaLibrarySessionCallback : MediaLibrarySession.Callback {

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            android.util.Log.d("AutoService", "Connection from: ${controller.packageName}")

            // Add custom commands for skip forward/backward
            val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon()
                .add(SessionCommand(ACTION_SKIP_FORWARD, Bundle.EMPTY))
                .add(SessionCommand(ACTION_SKIP_BACKWARD, Bundle.EMPTY))
                .build()

            // Start from Media3's defaults (which include PREPARE, GET_METADATA and
            // CHANGE_MEDIA_ITEMS — needed for Android Auto / Assistant to load and
            // show a book) and make sure the skip commands are present. The
            // ForwardingPlayer turns previous/next into skips.
            val playerCommands = MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS.buildUpon()
                .addAll(
                    Player.COMMAND_SEEK_BACK,
                    Player.COMMAND_SEEK_FORWARD,
                    Player.COMMAND_SEEK_TO_PREVIOUS,
                    Player.COMMAND_SEEK_TO_NEXT
                )
                .build()

            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(sessionCommands)
                .setAvailablePlayerCommands(playerCommands)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            return when (customCommand.customAction) {
                ACTION_SKIP_FORWARD -> {
                    skipForward()
                    Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                ACTION_SKIP_BACKWARD -> {
                    skipBackward()
                    Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                else -> {
                    Futures.immediateFuture(SessionResult(SessionError.ERROR_NOT_SUPPORTED))
                }
            }
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> {
            android.util.Log.d("AutoService", "onGetLibraryRoot called by ${browser.packageName}")
            return Futures.immediateFuture(
                LibraryResult.ofItem(
                    MediaItem.Builder()
                        .setMediaId(ROOT_ID)
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setIsPlayable(false)
                                .setIsBrowsable(true)
                                .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                                .setTitle("Sappho Audiobooks")
                                .setSubtitle("Your Audiobook Library")
                                .build()
                        )
                        .build(),
                    params
                )
            )
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            android.util.Log.d("AutoService", "onGetChildren called for parentId: $parentId, by ${browser.packageName}")
            return when {
                parentId == ROOT_ID -> {
                    android.util.Log.d("AutoService", "Loading root menu items")
                    // Root menu items - optimized for Android Auto
                    val items = ImmutableList.of(
                        createBrowsableMediaItem(
                            CONTINUE_LISTENING_ID,
                            "Continue Listening",
                            MediaMetadata.MEDIA_TYPE_FOLDER_AUDIO_BOOKS,
                            "Resume your audiobooks"
                        ),
                        createBrowsableMediaItem(
                            RECENT_ID,
                            "Recently Added",
                            MediaMetadata.MEDIA_TYPE_FOLDER_AUDIO_BOOKS,
                            "Newest audiobooks"
                        ),
                        createBrowsableMediaItem(
                            ALL_BOOKS_ID,
                            "All Audiobooks", 
                            MediaMetadata.MEDIA_TYPE_FOLDER_AUDIO_BOOKS,
                            "Browse entire library"
                        )
                    )
                    android.util.Log.d("AutoService", "Returning ${items.size} root items")
                    Futures.immediateFuture(LibraryResult.ofItemList(items, params))
                }
                parentId == CONTINUE_LISTENING_ID -> {
                    android.util.Log.d("AutoService", "Loading in-progress audiobooks")
                    loadInProgressAudiobooks(params)
                }
                parentId == RECENT_ID -> {
                    android.util.Log.d("AutoService", "Loading recent audiobooks")
                    loadRecentAudiobooks(params)
                }
                parentId == ALL_BOOKS_ID -> {
                    android.util.Log.d("AutoService", "Loading all audiobooks")
                    loadAllAudiobooks(params)
                }
                parentId.startsWith(CHAPTERS_PREFIX) -> {
                    // Load chapters for a specific audiobook
                    val audiobookId = parentId.removePrefix("${CHAPTERS_PREFIX}_").toIntOrNull()
                    if (audiobookId != null) {
                        loadChapters(audiobookId, params)
                    } else {
                        Futures.immediateFuture(LibraryResult.ofItemList(ImmutableList.of(), params))
                    }
                }
                else -> {
                    // Check if it's an audiobook ID for chapter browsing
                    val audiobookId = parentId.toIntOrNull()
                    if (audiobookId != null) {
                        loadChapters(audiobookId, params)
                    } else {
                        Futures.immediateFuture(LibraryResult.ofItemList(ImmutableList.of(), params))
                    }
                }
            }
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String
        ): ListenableFuture<LibraryResult<MediaItem>> {
            // For browsable folders
            if (mediaId == ROOT_ID || mediaId == CONTINUE_LISTENING_ID ||
                mediaId == RECENT_ID || mediaId == ALL_BOOKS_ID ||
                mediaId.startsWith(CHAPTERS_PREFIX)) {
                return Futures.immediateFuture(LibraryResult.ofError(SessionError.ERROR_NOT_SUPPORTED))
            }

            // For individual audiobooks
            val audiobookId = mediaId.toIntOrNull() ?: return Futures.immediateFuture(
                LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
            )

            return loadAudiobookMediaItem(audiobookId)
        }

        /**
         * A controller (Android Auto, Assistant, Bluetooth) asked to play a
         * media id or search query. Resolve it to a playable item and hand it
         * back WITH its resume position, and let Media3 load it.
         *
         * We must not also call loadAndPlay() here: Media3 applies the returned
         * items after this future completes and would replace whatever
         * loadAndPlay prepared, resetting the position to 0:00.
         */
        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
            startIndex: Int,
            startPositionMs: Long
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val future = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
            val requested = mediaItems.firstOrNull()
            if (requested == null) {
                future.setException(IllegalArgumentException("No media item requested"))
                return future
            }
            serviceScope.launch {
                try {
                    val resolved = resolveRequestedItem(requested)
                    if (resolved == null) {
                        future.setException(IllegalStateException("Could not resolve ${requested.mediaId}"))
                    } else {
                        future.set(toItemsWithStartPosition(resolved))
                    }
                } catch (e: CancellationException) {
                    future.setException(e)
                    throw e
                } catch (e: Exception) {
                    android.util.Log.e("AutoService", "Error resolving media item", e)
                    future.setException(e)
                }
            }
            return future
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>
        ): ListenableFuture<MutableList<MediaItem>> {
            val future = SettableFuture.create<MutableList<MediaItem>>()
            val requested = mediaItems.firstOrNull()
            if (requested == null) {
                future.set(mutableListOf())
                return future
            }
            serviceScope.launch {
                try {
                    val resolved = resolveRequestedItem(requested)
                    future.set(
                        if (resolved == null) mutableListOf()
                        else mutableListOf(prepareSessionItem(resolved))
                    )
                } catch (e: CancellationException) {
                    // Resolve the future before rethrowing so the Media3
                    // controller waiting on it does not hang forever.
                    future.set(mutableListOf())
                    throw e
                } catch (e: Exception) {
                    android.util.Log.e("AutoService", "Error resolving media item", e)
                    future.set(mutableListOf())
                }
            }
            return future
        }

        override fun onSearch(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<Void>> {
            android.util.Log.d("AutoService", "onSearch called with query: $query")

            // Perform search asynchronously and notify when results are ready
            serviceScope.launch {
                performSearch(query)
                // Notify that search results are available
                session.notifySearchResultChanged(browser, query, lastSearchResults.size, params)
            }

            return Futures.immediateFuture(LibraryResult.ofVoid())
        }

        override fun onGetSearchResult(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            android.util.Log.d("AutoService", "onGetSearchResult called with query: $query, page: $page")

            // Return cached search results
            val startIndex = page * pageSize
            val endIndex = minOf(startIndex + pageSize, lastSearchResults.size)

            return if (startIndex < lastSearchResults.size) {
                val pageResults = lastSearchResults.subList(startIndex, endIndex)
                Futures.immediateFuture(LibraryResult.ofItemList(ImmutableList.copyOf(pageResults), params))
            } else {
                Futures.immediateFuture(LibraryResult.ofItemList(ImmutableList.of(), params))
            }
        }

        /**
         * Play pressed on a headset / the system resumption UI while the
         * service wasn't running. Return the last in-progress book with its
         * position; Media3 loads and plays it.
         */
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            android.util.Log.d("AutoService", "onPlaybackResumption called")
            val future = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()

            serviceScope.launch {
                try {
                    val response = api.getInProgress(limit = 1)
                    val audiobook = if (response.isSuccessful) response.body()?.firstOrNull() else null
                    if (audiobook != null) {
                        val resolved = ResolvedRequest(audiobook, audiobook.progress?.position ?: 0, applyRewind = true)
                        future.set(toItemsWithStartPosition(resolved))
                    } else {
                        future.setException(IllegalStateException("No audiobook to resume"))
                    }
                } catch (e: CancellationException) {
                    // Resolve the future before rethrowing so the controller
                    // waiting on it does not hang forever.
                    future.setException(e)
                    throw e
                } catch (e: Exception) {
                    android.util.Log.e("AutoService", "Error in playback resumption", e)
                    future.setException(e)
                }
            }

            return future
        }

    }

    /** A controller request resolved to a book and where to start it (seconds). */
    private data class ResolvedRequest(
        val audiobook: Audiobook,
        val startSeconds: Int,
        val applyRewind: Boolean
    )

    /**
     * Turn a controller's request (voice search query, `<id>_chapter_<n>`, or a
     * plain book id) into a book and start position. Null when nothing matches.
     */
    private suspend fun resolveRequestedItem(mediaItem: MediaItem): ResolvedRequest? {
        val searchQuery = mediaItem.requestMetadata.searchQuery
        if (!searchQuery.isNullOrBlank()) {
            android.util.Log.d("AutoService", "Voice search detected: $searchQuery")
            val response = api.getAudiobooks(search = searchQuery, limit = 5)
            val book = if (response.isSuccessful) response.body()?.audiobooks?.firstOrNull() else null
            return book?.let { ResolvedRequest(it, it.progress?.position ?: 0, applyRewind = true) }
        }

        val mediaId = mediaItem.mediaId
        if (mediaId.contains("_chapter_")) {
            val parts = mediaId.split("_chapter_")
            val audiobookId = parts[0].toIntOrNull() ?: return null
            val chapterIndex = parts.getOrNull(1)?.toIntOrNull() ?: 0
            val book = fetchAudiobook(audiobookId) ?: return null
            val chapterStart = book.chapters?.getOrNull(chapterIndex)?.startTime?.toInt() ?: 0
            return ResolvedRequest(book, chapterStart, applyRewind = false)
        }

        val audiobookId = mediaId.toIntOrNull() ?: return null
        val book = fetchAudiobook(audiobookId) ?: return null
        return ResolvedRequest(book, book.progress?.position ?: 0, applyRewind = true)
    }

    private suspend fun fetchAudiobook(audiobookId: Int): Audiobook? {
        audiobookCache[audiobookId]?.let { return it }
        val fromServer = try {
            api.getAudiobook(audiobookId).body()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.w("AutoService", "getAudiobook failed; trying downloaded copy", e)
            null
        }
        val book = fromServer ?: downloadManager.getDownloadedBook(audiobookId)?.audiobook
        book?.let { audiobookCache[audiobookId] = it }
        return book
    }

    /** Build the playable item and remember the book so we adopt it once loaded. */
    private fun prepareSessionItem(resolved: ResolvedRequest): MediaItem {
        val item = buildPlayableMediaItem(resolved.audiobook)
        pendingSessionBooks[item.mediaId] = resolved.audiobook
        return item
    }

    private fun toItemsWithStartPosition(resolved: ResolvedRequest): MediaSession.MediaItemsWithStartPosition {
        val item = prepareSessionItem(resolved)
        val rewind = if (resolved.applyRewind && resolved.startSeconds > 0) {
            userPreferences.rewindOnResumeSeconds.value
        } else 0
        val startSeconds = (resolved.startSeconds - rewind).coerceAtLeast(0)
        playerState.updatePosition(startSeconds.toLong())
        return MediaSession.MediaItemsWithStartPosition(listOf(item), 0, startSeconds * 1000L)
    }

    /**
     * Media3 loaded a book requested by a session controller. Do the setup
     * loadAndPlay() does for in-app playback: shared state, speed, cover,
     * progress sync and the pending-queue replay.
     */
    private fun adoptSessionPlayback(audiobook: Audiobook) {
        android.util.Log.d("AudioPlaybackService", "Adopting session playback of ${audiobook.id}")
        playerState.updateAudiobook(audiobook)
        audiobook.duration?.takeIf { it > 0 }?.let { playerState.updateDuration(it.toLong()) }
        isPlayingLocalFile = downloadManager.getLocalFilePath(audiobook.id)?.let { File(it).exists() } == true
        loadCoverBitmap(audiobook)
        applySavedPlaybackSpeed()
        playbackSessionStartTime = System.currentTimeMillis()
        startProgressSync()
        syncPendingProgress()
    }

    /**
     * Perform search and cache results for Android Auto.
     * Results are stored in lastSearchResults for retrieval via onGetSearchResult.
     */
    private suspend fun performSearch(query: String) {
        if (query.isBlank()) {
            lastSearchResults = emptyList()
            return
        }

        // Check authentication
        val serverUrl = authRepository.getServerUrlSync()
        val token = authRepository.getTokenSync()
        if (serverUrl.isNullOrEmpty() || token.isNullOrEmpty()) {
            android.util.Log.w("AutoService", "Missing authentication for search")
            lastSearchResults = emptyList()
            return
        }

        try {
            android.util.Log.d("AutoService", "Searching for: $query")
            val response = api.getAudiobooks(search = query, limit = 20)
            if (response.isSuccessful) {
                val audiobooks = response.body()?.audiobooks ?: emptyList()
                android.util.Log.d("AutoService", "Search found ${audiobooks.size} results for '$query'")

                lastSearchResults = audiobooks.map { book ->
                    createPlayableMediaItem(book)
                }
            } else {
                android.util.Log.e("AutoService", "Search failed: ${response.code()}")
                lastSearchResults = emptyList()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.e("AutoService", "Exception during search", e)
            lastSearchResults = emptyList()
        }
    }

    /**
     * Item ExoPlayer can play: the downloaded file when present, otherwise the
     * stream. The stream URL carries no token; ExoPlayer fetches it through the
     * app's OkHttpClient, which adds the Authorization header and refreshes an
     * expired access token.
     */
    private fun buildPlayableMediaItem(audiobook: Audiobook): MediaItem {
        val serverUrl = authRepository.getServerUrlSync() ?: ""

        // Check for downloaded file first
        val localFilePath = downloadManager.getLocalFilePath(audiobook.id)
        val mediaUri = if (localFilePath != null && File(localFilePath).exists()) {
            Uri.fromFile(File(localFilePath))
        } else {
            Uri.parse("$serverUrl/api/audiobooks/${audiobook.id}/stream")
        }

        val coverArtUri = coverArtUriFor(audiobook)

        return MediaItem.Builder()
            .setMediaId(audiobook.id.toString())
            .setUri(mediaUri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setIsPlayable(true)
                    .setIsBrowsable(false)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_AUDIO_BOOK)
                    .setTitle(audiobook.title)
                    .setArtist(audiobook.author)
                    .setArtworkUri(coverArtUri)
                    .setAlbumTitle(audiobook.series)
                    .build()
            )
            .build()
    }

    /**
     * Artwork for notification, lock screen and Android Auto. A content:// URI
     * served by CoverArtProvider, so the access token never leaves the app in
     * an artwork URL handed to other processes.
     */
    private fun coverArtUriFor(audiobook: Audiobook): Uri? =
        if (audiobook.coverImage != null) CoverArtProvider.uriFor(this, audiobook.id) else null

    private fun loadChapters(audiobookId: Int, params: LibraryParams?): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()

        serviceScope.launch {
            try {
                val audiobook = audiobookCache[audiobookId] ?: run {
                    val response = api.getAudiobook(audiobookId)
                    response.body()?.also { audiobookCache[audiobookId] = it }
                }

                if (audiobook != null && !audiobook.chapters.isNullOrEmpty()) {
                    val coverArtUri = coverArtUriFor(audiobook)

                    val chapterItems = audiobook.chapters.mapIndexed { index: Int, chapter: com.sappho.audiobooks.domain.model.Chapter ->
                        val endTime = chapter.endTime ?: chapter.startTime
                        val duration = (endTime - chapter.startTime).toInt()
                        val durationMin = duration / 60

                        MediaItem.Builder()
                            .setMediaId("${audiobookId}_chapter_$index")
                            .setMediaMetadata(
                                MediaMetadata.Builder()
                                    .setIsPlayable(true)
                                    .setIsBrowsable(false)
                                    .setMediaType(MediaMetadata.MEDIA_TYPE_AUDIO_BOOK_CHAPTER)
                                    .setTitle(chapter.title ?: "Chapter ${index + 1}")
                                    .setArtist("${durationMin}m")
                                    .setArtworkUri(coverArtUri)
                                    .setTrackNumber(index + 1)
                                    .build()
                            )
                            .build()
                    }
                    future.set(LibraryResult.ofItemList(ImmutableList.copyOf<MediaItem>(chapterItems), params))
                } else {
                    future.set(LibraryResult.ofItemList(ImmutableList.of(), params))
                }
            } catch (e: CancellationException) {
                future.set(LibraryResult.ofItemList(ImmutableList.of(), params))
                throw e
            } catch (e: Exception) {
                android.util.Log.e("AudioPlaybackService", "Error loading chapters", e)
                future.set(LibraryResult.ofItemList(ImmutableList.of(), params))
            }
        }

        return future
    }

    private fun createBrowsableMediaItem(
        mediaId: String,
        title: String,
        mediaType: Int,
        subtitle: String? = null
    ): MediaItem {
        return MediaItem.Builder()
            .setMediaId(mediaId)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setIsPlayable(false)
                    .setIsBrowsable(true)
                    .setMediaType(mediaType)
                    .setTitle(title)
                    .apply { subtitle?.let { setSubtitle(it) } }
                    .build()
            )
            .build()
    }

    private fun loadInProgressAudiobooks(params: LibraryParams?): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()

        // Check if we have authentication
        val serverUrl = authRepository.getServerUrlSync()
        val token = authRepository.getTokenSync()
        if (serverUrl.isNullOrEmpty() || token.isNullOrEmpty()) {
            android.util.Log.w("AutoService", "Missing authentication for in-progress books")
            future.set(LibraryResult.ofItemList(ImmutableList.of(), params))
            return future
        }

        serviceScope.launch {
            try {
                android.util.Log.d("AutoService", "Fetching in-progress audiobooks")
                // Use the dedicated /meta/in-progress endpoint for proper server-side filtering and sorting
                val response = api.getInProgress(limit = 25)
                if (response.isSuccessful) {
                    val inProgressBooks = response.body() ?: emptyList()
                    android.util.Log.d("AutoService", "Found ${inProgressBooks.size} in-progress books")

                    val mediaItems = inProgressBooks.map { book ->
                        createPlayableMediaItem(book)
                    }
                    future.set(LibraryResult.ofItemList(ImmutableList.copyOf(mediaItems), params))
                } else {
                    android.util.Log.e("AutoService", "Failed to load in-progress books: ${response.code()}")
                    future.set(LibraryResult.ofItemList(ImmutableList.of(), params))
                }
            } catch (e: CancellationException) {
                future.set(LibraryResult.ofItemList(ImmutableList.of(), params))
                throw e
            } catch (e: Exception) {
                android.util.Log.e("AutoService", "Exception loading in-progress books", e)
                future.set(LibraryResult.ofItemList(ImmutableList.of(), params))
            }
        }

        return future
    }

    private fun loadRecentAudiobooks(params: LibraryParams?): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()

        // Check if we have authentication
        val serverUrl = authRepository.getServerUrlSync()
        val token = authRepository.getTokenSync()
        if (serverUrl.isNullOrEmpty() || token.isNullOrEmpty()) {
            android.util.Log.w("AutoService", "Missing authentication for recent books")
            future.set(LibraryResult.ofItemList(ImmutableList.of(), params))
            return future
        }

        serviceScope.launch {
            try {
                android.util.Log.d("AutoService", "Fetching recent audiobooks")
                // Use the dedicated /meta/recent endpoint for proper server-side sorting
                val response = api.getRecentlyAdded(limit = 20)
                if (response.isSuccessful) {
                    val recentBooks = response.body() ?: emptyList()
                    android.util.Log.d("AutoService", "Found ${recentBooks.size} recent books")

                    val mediaItems = recentBooks.map { book ->
                        createPlayableMediaItem(book)
                    }
                    future.set(LibraryResult.ofItemList(ImmutableList.copyOf(mediaItems), params))
                } else {
                    android.util.Log.e("AutoService", "Failed to load recent books: ${response.code()}")
                    future.set(LibraryResult.ofItemList(ImmutableList.of(), params))
                }
            } catch (e: CancellationException) {
                future.set(LibraryResult.ofItemList(ImmutableList.of(), params))
                throw e
            } catch (e: Exception) {
                android.util.Log.e("AutoService", "Exception loading recent books", e)
                future.set(LibraryResult.ofItemList(ImmutableList.of(), params))
            }
        }

        return future
    }

    private fun loadAllAudiobooks(params: LibraryParams?): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()

        // Check if we have authentication
        val serverUrl = authRepository.getServerUrlSync()
        val token = authRepository.getTokenSync()
        if (serverUrl.isNullOrEmpty() || token.isNullOrEmpty()) {
            android.util.Log.w("AutoService", "Missing authentication for all books")
            future.set(LibraryResult.ofItemList(ImmutableList.of(), params))
            return future
        }

        serviceScope.launch {
            try {
                android.util.Log.d("AutoService", "Fetching all audiobooks")
                val response = api.getAudiobooks(limit = 100) // Reduced limit for better Android Auto performance
                if (response.isSuccessful) {
                    val audiobooks = response.body()?.audiobooks ?: emptyList()
                    android.util.Log.d("AutoService", "Found ${audiobooks.size} total books")
                    
                    val mediaItems = audiobooks.map { book ->
                        createPlayableMediaItem(book)
                    }
                    future.set(LibraryResult.ofItemList(ImmutableList.copyOf(mediaItems), params))
                } else {
                    android.util.Log.e("AutoService", "Failed to load all books: ${response.code()}")
                    future.set(LibraryResult.ofItemList(ImmutableList.of(), params))
                }
            } catch (e: CancellationException) {
                future.set(LibraryResult.ofItemList(ImmutableList.of(), params))
                throw e
            } catch (e: Exception) {
                android.util.Log.e("AutoService", "Exception loading all books", e)
                future.set(LibraryResult.ofItemList(ImmutableList.of(), params))
            }
        }

        return future
    }

    private fun loadAudiobookMediaItem(audiobookId: Int): ListenableFuture<LibraryResult<MediaItem>> {
        val future = SettableFuture.create<LibraryResult<MediaItem>>()

        serviceScope.launch {
            try {
                val response = api.getAudiobook(audiobookId)
                if (response.isSuccessful) {
                    val audiobook = response.body()
                    if (audiobook != null) {
                        val mediaItem = createPlayableMediaItem(audiobook)
                        future.set(LibraryResult.ofItem(mediaItem, null))
                    } else {
                        future.set(LibraryResult.ofError(SessionError.ERROR_NOT_SUPPORTED))
                    }
                } else {
                    future.set(LibraryResult.ofError(SessionError.ERROR_NOT_SUPPORTED))
                }
            } catch (e: CancellationException) {
                future.set(LibraryResult.ofError(SessionError.ERROR_NOT_SUPPORTED))
                throw e
            } catch (e: Exception) {
                future.set(LibraryResult.ofError(SessionError.ERROR_NOT_SUPPORTED))
            }
        }

        return future
    }

    private fun createPlayableMediaItem(audiobook: Audiobook): MediaItem {
        // Cache the audiobook for later use
        audiobookCache[audiobook.id] = audiobook

        val coverArtUri = coverArtUriFor(audiobook)

        // Build subtitle with progress info if available - optimized for Android Auto
        val subtitle = buildString {
            append(audiobook.author ?: "Unknown Author")
            audiobook.progress?.let { progress ->
                if (progress.position > 0 && progress.completed != 1) {
                    val positionMin = progress.position / 60
                    val positionHr = positionMin / 60
                    val remainingMin = positionMin % 60
                    when {
                        positionHr > 0 -> append(" • ${positionHr}h ${remainingMin}m")
                        positionMin > 0 -> append(" • ${positionMin}m")
                    }
                }
            }
        }
        
        // Build duration info for description
        val durationText = audiobook.duration?.let { duration ->
            val hours = duration / 3600
            val minutes = (duration % 3600) / 60
            when {
                hours > 0 -> "${hours}h ${minutes}m"
                minutes > 0 -> "${minutes}m"
                else -> null
            }
        }

        // Make audiobook browsable if it has chapters
        val hasChapters = !audiobook.chapters.isNullOrEmpty()

        return MediaItem.Builder()
            .setMediaId(audiobook.id.toString())
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setIsPlayable(true)
                    .setIsBrowsable(hasChapters)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_AUDIO_BOOK)
                    .setTitle(audiobook.title)
                    .setArtist(audiobook.author ?: "Unknown Author")
                    .setSubtitle(subtitle)
                    .setDescription(durationText)
                    .setArtworkUri(coverArtUri)
                    .setAlbumTitle(audiobook.series)
                    .setGenre(audiobook.genre)
                    .build()
            )
            .build()
    }

    fun loadAndPlay(audiobook: Audiobook, startPosition: Int) {
        // Promote to foreground FIRST. Every path below may return early, and a
        // service started with startForegroundService() that never calls
        // startForeground() is killed with ForegroundServiceDidNotStartInTime.
        startForeground(NOTIFICATION_ID, createNotification())

        // Ensure player is initialized - reinitialize if it was released
        if (player == null || mediaLibrarySession == null) {
            initializePlayer()
        }

        val exoPlayer = player ?: run {
            stopForeground(STOP_FOREGROUND_REMOVE)
            return
        }
        android.util.Log.d("AudioPlaybackService", "loadAndPlay: book=${audiobook.title}, startPosition=$startPosition")

        // Stop any existing playback to ensure clean state
        exoPlayer.stop()
        pendingSessionBooks.clear()

        // Always set position to what we intend to play
        playerState.updatePosition(startPosition.toLong())
        playerState.updateAudiobook(audiobook)
        playerState.updateLoadingState(true)

        val serverUrl = authRepository.getServerUrlSync()
        val token = authRepository.getTokenSync()
        val localFilePath = downloadManager.getLocalFilePath(audiobook.id)
        val hasLocalFile = localFilePath != null && File(localFilePath).exists()
        if (!hasLocalFile && (serverUrl == null || token == null)) {
            android.util.Log.e("AudioPlaybackService", "No server URL or token available")
            playerState.updateLoadingState(false)
            stopForeground(STOP_FOREGROUND_REMOVE)
            return
        }

        // Load cover bitmap for notification
        loadCoverBitmap(audiobook)

        val mediaItem = buildPlayableMediaItem(audiobook)
        isPlayingLocalFile = hasLocalFile
        // Not logging the URI itself: file paths and server URLs stay out of logcat
        android.util.Log.d("AudioPlaybackService", if (hasLocalFile) "Using local file" else "Streaming audiobook ${audiobook.id} from server")

        // Apply rewind on resume if resuming from a saved position
        val rewindSeconds = if (startPosition > 0) userPreferences.rewindOnResumeSeconds.value else 0
        val adjustedPosition = (startPosition - rewindSeconds).coerceAtLeast(0)

        // setMediaItem with a start position: no window where the item is
        // prepared at 0:00 before a separate seek lands.
        exoPlayer.setMediaItem(mediaItem, adjustedPosition * 1000L)
        exoPlayer.prepare()
        // Media3 requests audio focus on play(); if it is refused (e.g. during
        // a call) playback simply waits instead of crashing or bailing out.
        exoPlayer.play()

        // Mark the start of this playback session for progress sync delay
        playbackSessionStartTime = System.currentTimeMillis()

        applySavedPlaybackSpeed()
        startProgressSync()

        // Refresh the foreground notification now that metadata is set
        updateNotification()

        // Try to sync any pending offline progress when we start playback
        syncPendingProgress()
    }

    /** Restore saved playback speed, or use the default preference if none was saved. */
    private fun applySavedPlaybackSpeed() {
        val savedSpeed = authRepository.getPlaybackSpeed()
        val effectiveSpeed = if (savedSpeed == 1.0f) {
            userPreferences.defaultPlaybackSpeed.value
        } else {
            savedSpeed
        }
        player?.setPlaybackSpeed(effectiveSpeed)
        playerState.updatePlaybackSpeed(effectiveSpeed)
    }

    override fun togglePlayPause(): Boolean {
        val exoPlayer = player ?: return false // signal caller to restart playback
        if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
        return true
    }

    fun isCurrentlyPlaying(): Boolean {
        return player?.isPlaying == true
    }

    /** Seek to [seconds] (NOT milliseconds) into the book. */
    override fun seekTo(seconds: Long) {
        val target = ProgressPolicy.clampSeekSeconds(seconds, playerState.duration.value)
        player?.seekTo(target * 1000)
        // Immediately update UI position so slider doesn't snap back
        playerState.updatePosition(target)
        playerState.updateLastActiveTimestamp()
    }

    override fun seekToAndPlay(seconds: Long) {
        player?.let {
            val target = ProgressPolicy.clampSeekSeconds(seconds, playerState.duration.value)
            it.seekTo(target * 1000)
            // Immediately update UI position so slider doesn't snap back
            playerState.updatePosition(target)
            playerState.updateLastActiveTimestamp()
            if (!it.isPlaying) {
                it.play()
            }
        }
    }

    override fun skipForward() {
        player?.let {
            val skipSeconds = userPreferences.skipForwardSeconds.value.toLong()
            val newPosition = clampSkipForwardPosition(
                targetSeconds = it.currentPosition / 1000 + skipSeconds,
                durationSeconds = playerState.duration.value
            )
            seekTo(newPosition)
        }
    }

    override fun skipBackward() {
        player?.let {
            val skipSeconds = userPreferences.skipBackwardSeconds.value.toLong()
            val newPosition = (it.currentPosition / 1000 - skipSeconds).coerceAtLeast(0)
            seekTo(newPosition)
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        player?.setPlaybackSpeed(speed)
        playerState.updatePlaybackSpeed(speed)
        authRepository.savePlaybackSpeed(speed)
    }

    fun setSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        if (minutes <= 0) {
            playerState.updateSleepTimerRemaining(null)
            return
        }

        val totalSeconds = minutes * 60L
        playerState.updateSleepTimerRemaining(totalSeconds)

        sleepTimerJob = serviceScope.launch {
            var remaining = totalSeconds
            while (remaining > 0 && isActive) {
                delay(1000)
                remaining--
                playerState.updateSleepTimerRemaining(remaining)
            }
            if (isActive) {
                // Timer finished - pause playback
                player?.pause()
                playerState.updateSleepTimerRemaining(null)
            }
        }
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        sleepAtEndOfChapter = false
        playerState.updateSleepTimerRemaining(null)
        playerState.updateSleepAtEndOfChapter(false)
    }

    fun setSleepTimerEndOfChapter() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        playerState.updateSleepTimerRemaining(null)
        sleepAtEndOfChapter = true
        playerState.updateSleepAtEndOfChapter(true)

        // Initialize the previous chapter index based on current position
        val currentPositionSec = player?.currentPosition?.div(1000) ?: 0L
        val chapters = playerState.currentAudiobook.value?.chapters
        previousChapterIndex = if (!chapters.isNullOrEmpty()) {
            chapters.indexOfLast { it.startTime <= currentPositionSec.toDouble() }
        } else {
            -1
        }
    }

    private fun startPositionUpdates() {
        positionUpdateJob?.cancel()
        positionUpdateJob = serviceScope.launch {
            while (isActive) {
                player?.let {
                    val positionSec = it.currentPosition / 1000
                    playerState.updatePosition(positionSec)
                    playerState.updateBufferedPosition(it.contentBufferedPosition / 1000)

                    // Check for chapter change when end-of-chapter sleep timer is active
                    if (sleepAtEndOfChapter) {
                        val chapters = playerState.currentAudiobook.value?.chapters
                        if (!chapters.isNullOrEmpty()) {
                            val currentChapterIdx = chapters.indexOfLast { ch ->
                                ch.startTime <= positionSec.toDouble()
                            }
                            if (previousChapterIndex >= 0 && currentChapterIdx != previousChapterIndex) {
                                // Chapter changed - pause playback
                                it.pause()
                                sleepAtEndOfChapter = false
                                playerState.updateSleepAtEndOfChapter(false)
                                playerState.updateSleepTimerRemaining(null)
                            }
                            previousChapterIndex = currentChapterIdx
                        }
                    }
                }
                delay(500)
            }
        }
    }

    private fun stopPositionUpdates() {
        positionUpdateJob?.cancel()
    }

    private fun startProgressSync() {
        progressSyncJob?.cancel()
        progressSyncJob = serviceScope.launch {
            while (isActive) {
                delay(Timing.SYNC_INTERVAL_MS)
                syncProgress()
            }
        }
    }

    private fun syncProgress() {
        syncProgressInternal(respectDelayGuard = true)
    }

    /** Sync immediately, bypassing the initial delay guard. Used on pause and stop events. */
    private fun syncProgressImmediate() {
        syncProgressInternal(respectDelayGuard = false)
    }

    private fun syncProgressInternal(respectDelayGuard: Boolean) {
        serviceScope.launch {
            playerState.currentAudiobook.value?.let { book ->
                val position = playerState.currentPosition.value.toInt()
                val totalDuration = playerState.duration.value.toInt()

                // Skip sync if near the end of the book
                if (ProgressPolicy.isNearEnd(position.toLong(), totalDuration.toLong())) {
                    return@launch
                }

                // Skip sync if not enough time has passed since playback started
                // This prevents recording progress for accidental plays or quick skips
                // Bypassed for explicit pause/stop events
                if (respectDelayGuard) {
                    val secondsSinceStart = (System.currentTimeMillis() - playbackSessionStartTime) / 1000
                    if (secondsSinceStart < INITIAL_PROGRESS_DELAY_SECONDS) {
                        return@launch
                    }
                }

                // Save locally on pause/stop as safety net in case API fails or process is killed
                if (!respectDelayGuard) {
                    downloadManager.saveOfflineProgress(book.id, position)
                }

                try {
                    api.updateProgress(
                        book.id,
                        ProgressUpdateRequest(
                            position = position,
                            completed = 0,
                            state = if (playerState.isPlaying.value) "playing" else "paused"
                        )
                    )
                    // Successfully synced - clear any pending progress for this book
                    downloadManager.clearPendingProgress(book.id)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (!respectDelayGuard || isPlayingLocalFile) {
                        // Pause/stop event or offline — save locally for later sync
                        downloadManager.saveOfflineProgress(book.id, position)
                    }
                    android.util.Log.w("AudioPlaybackService", "Progress sync failed", e)
                }
            }
        }
    }

    private fun syncPendingProgress() {
        serviceScope.launch {
            try {
                pendingProgressReplayer.replayAll()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.w("AudioPlaybackService", "Pending progress replay failed", e)
            }
        }
    }

    /**
     * The player reached the end of its media. That is only the end of the
     * BOOK when we're near the server's total duration — a multi-file book's
     * stream/download is part 1 only, and ending part 1 must not mark the
     * whole book finished (which also resets the position to 0 server-side).
     */
    private fun handlePlaybackEnded(endPositionSeconds: Long) {
        val book = playerState.currentAudiobook.value ?: return
        if (ProgressPolicy.shouldMarkFinished(endPositionSeconds, book.duration)) {
            markFinished(book)
        } else {
            android.util.Log.w(
                "AudioPlaybackService",
                "Media ended at ${endPositionSeconds}s but book ${book.id} is ${book.duration}s; not marking finished"
            )
            syncProgressImmediate()
        }
    }

    private fun markFinished(book: Audiobook) {
        serviceScope.launch {
            try {
                api.markFinished(book.id, ProgressUpdateRequest(0, 1, "stopped"))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("AudioPlaybackService", "Failed to mark audiobook as finished", e)
            }
        }
    }

    private fun createNotification(): Notification {
        val audiobook = playerState.currentAudiobook.value
        val session = mediaLibrarySession ?: return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Sappho Audiobooks")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .build()

        // Create a content intent to open the player when notification is tapped
        val contentIntent = Intent(this, com.sappho.audiobooks.presentation.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            0,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Create action intents
        val skipBackwardIntent = PendingIntent.getBroadcast(
            this, 1,
            Intent(ACTION_SKIP_BACKWARD).setPackage(packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val playPauseIntent = PendingIntent.getBroadcast(
            this, 2,
            Intent(ACTION_PLAY_PAUSE).setPackage(packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val skipForwardIntent = PendingIntent.getBroadcast(
            this, 3,
            Intent(ACTION_SKIP_FORWARD).setPackage(packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val isPlaying = player?.isPlaying == true
        val playPauseIcon = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        val playPauseText = if (isPlaying) "Pause" else "Play"

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(audiobook?.title ?: "Sappho Audiobooks")
            .setContentText(audiobook?.author ?: "")
            .setSubText(audiobook?.series)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(contentPendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setShowWhen(false)
            .addAction(R.drawable.ic_replay_15, "Rewind", skipBackwardIntent)
            .addAction(playPauseIcon, playPauseText, playPauseIntent)
            .addAction(R.drawable.ic_forward_15, "Forward", skipForwardIntent)
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(session.sessionCompatToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )

        // Add cover art if available
        currentCoverBitmap?.let { bitmap ->
            builder.setLargeIcon(bitmap)
        }

        return builder.build()
    }

    private fun updateNotification() {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, createNotification())
    }

    private fun loadCoverBitmap(audiobook: Audiobook) {
        val serverUrl = authRepository.getServerUrlSync() ?: return
        if (audiobook.coverImage == null) {
            currentCoverBitmap = null
            return
        }

        serviceScope.launch(Dispatchers.IO) {
            try {
                // The shared OkHttpClient adds the Authorization header; no
                // token in the URL.
                val coverUrl = "$serverUrl/api/audiobooks/${audiobook.id}/cover"
                val request = okhttp3.Request.Builder()
                    .url(coverUrl)
                    .build()

                okHttpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        response.body?.byteStream()?.use { inputStream ->
                            currentCoverBitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
                            // Update notification with the new bitmap
                            kotlinx.coroutines.withContext(Dispatchers.Main) {
                                updateNotification()
                            }
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("AudioPlaybackService", "Failed to load cover bitmap", e)
            }
        }
    }

    /**
     * Logout: push the final position while the session token is still valid,
     * then stop. Unlike [stopPlayback] this waits for the sync (bounded), so
     * the position isn't left in an offline queue that logout then clears.
     */
    override suspend fun stopForLogout(syncFinalPosition: Boolean) {
        val book = playerState.currentAudiobook.value
        val position = (player?.currentPosition ?: 0L) / 1000
        val worthSaving = book != null && position > 0 && !ProgressPolicy.isNearEnd(position, playerState.duration.value)
        if (worthSaving && !syncFinalPosition) {
            downloadManager.saveOfflineProgress(book!!.id, position.toInt())
        } else if (worthSaving) {
            kotlinx.coroutines.withTimeoutOrNull(5_000) {
                try {
                    api.updateProgress(book!!.id, ProgressUpdateRequest(position.toInt(), 0, "stopped"))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    android.util.Log.w("AudioPlaybackService", "Final sync at logout failed", e)
                }
            }
        }
        playerState.updateAudiobook(null) // nothing left for stopPlayback to re-sync
        stopPlayback()
    }

    fun stopPlayback() {
        syncProgressImmediate() // Bypass delay guard — this is an explicit stop
        player?.stop()
        player?.release()
        player = null
        mediaLibrarySession?.release()
        mediaLibrarySession = null
        progressSyncJob?.cancel()
        positionUpdateJob?.cancel()
        sleepTimerJob?.cancel()
        pauseTimeoutJob?.cancel()
        sleepAtEndOfChapter = false
        playerState.deactivate()
        audiobookCache.clear() // Clear cache to avoid stale data after re-login
        pendingSessionBooks.clear()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onUpdateNotification(session: MediaSession, startInForegroundRequired: Boolean) {
        if (startInForegroundRequired) {
            // Media3 says we need foreground — do the default
            super.onUpdateNotification(session, startInForegroundRequired)
        } else if (pauseTimeoutJob?.isActive == true) {
            // Media3 wants to demote us out of foreground, but our pause timeout
            // hasn't expired yet — re-assert foreground with our own notification
            // so the user can resume from the notification shade.
            startForeground(NOTIFICATION_ID, createNotification())
        } else {
            // Timeout expired or no active playback — let Media3 handle it
            super.onUpdateNotification(session, startInForegroundRequired)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        // Allow any app to connect to the media session for Android Auto compatibility
        // The MediaLibrarySession.Callback methods handle authorization
        return mediaLibrarySession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val isPlaying = player?.isPlaying == true

        // Persist progress without blocking: save locally, then let WorkManager
        // push it. runBlocking { api.updateProgress(...) } here ran on the main
        // thread and could ANR the process while OkHttp waited out its timeouts.
        val book = playerState.currentAudiobook.value
        val position = playerState.currentPosition.value.toInt()
        val duration = playerState.duration.value.toInt()
        if (book != null && position > 0 && (duration == 0 || (duration - position) >= 30)) {
            downloadManager.saveOfflineProgress(book.id, position)
            ProgressSyncWorker.enqueue(this)
        }

        if (isPlaying) {
            // Playing — keep the service alive (super will keep foreground)
            super.onTaskRemoved(rootIntent)
        } else if (pauseTimeoutJob?.isActive == true) {
            // Paused but timeout hasn't expired — keep service alive for resume.
            // Do NOT call super.onTaskRemoved() because MediaLibraryService's
            // default implementation stops the service when the player is paused.
            // Safety fallback: if the main timeout job gets cancelled unexpectedly,
            // ensure the service doesn't stay alive forever.
            serviceScope.launch {
                delay(PAUSE_TIMEOUT_MS)
                if (player?.isPlaying != true) {
                    android.util.Log.w("AudioPlaybackService", "Fallback timeout — stopping stale service")
                    stopPlayback()
                }
            }
        } else {
            // Paused and timeout already expired (shouldn't normally happen) — clean up
            super.onTaskRemoved(rootIntent)
        }
    }

    override fun onDestroy() {
        instance = null
        // Persist progress without blocking the main thread (see onTaskRemoved):
        // save locally, then hand off to WorkManager — it survives service death.
        val book = playerState.currentAudiobook.value
        val position = playerState.currentPosition.value.toInt()
        val duration = playerState.duration.value.toInt()
        if (book != null && position > 0 && (duration == 0 || (duration - position) >= 30)) {
            downloadManager.saveOfflineProgress(book.id, position)
            ProgressSyncWorker.enqueue(this)
        }
        player?.release()
        mediaLibrarySession?.release()
        // Cancel the whole scope — individual job cancels missed sleepTimerJob
        // and the onTaskRemoved fallback-timeout coroutine, which leaked.
        serviceScope.cancel()
        unregisterNotificationActionReceiver()
        playerState.deactivate()
        super.onDestroy()
    }
}
