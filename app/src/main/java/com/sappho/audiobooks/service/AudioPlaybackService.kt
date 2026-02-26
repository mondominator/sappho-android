package com.sappho.audiobooks.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
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
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@AndroidEntryPoint
class AudioPlaybackService : MediaLibraryService() {

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

    private var player: ExoPlayer? = null
    private var currentCoverBitmap: android.graphics.Bitmap? = null
    private var mediaLibrarySession: MediaLibrarySession? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var progressSyncJob: Job? = null
    private var positionUpdateJob: Job? = null
    private var sleepTimerJob: Job? = null
    private var pauseTimeoutJob: Job? = null

    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var noisyReceiver: BecomingNoisyReceiver? = null

    // Track playback session start time for progress sync delay
    private var playbackSessionStartTime: Long = 0L

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

        // Skip duration in seconds (hardcoded to 10 seconds)
        const val SKIP_SECONDS = 10L

        // Minimum time (in seconds) before first progress sync
        // This prevents recording progress for accidental plays or quick skips
        private const val INITIAL_PROGRESS_DELAY_SECONDS = 20

        // How long to keep the service alive after pausing before self-stopping
        private const val PAUSE_TIMEOUT_MS = 30 * 60 * 1000L  // 30 minutes

        // Custom command actions
        const val ACTION_SKIP_FORWARD = "com.sappho.audiobooks.SKIP_FORWARD"
        const val ACTION_SKIP_BACKWARD = "com.sappho.audiobooks.SKIP_BACKWARD"
        const val ACTION_PLAY_PAUSE = "com.sappho.audiobooks.PLAY_PAUSE"

        var instance: AudioPlaybackService? = null
            private set
    }

    // Cache for audiobooks to avoid re-fetching
    private val audiobookCache = mutableMapOf<Int, Audiobook>()

    // Search state for Android Auto search
    private var lastSearchQuery: String = ""
    private var lastSearchResults: List<MediaItem> = emptyList()

    private inner class BecomingNoisyReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                // Pause playback when headphones are disconnected
                player?.pause()
            }
        }
    }

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
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        createNotificationChannel()

        // Use Media3's default notification provider for proper system media controls
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setChannelId(CHANNEL_ID)
                .setChannelName(R.string.app_name)
                .build()
        )

        initializePlayer()
        registerNoisyReceiver()
        registerNotificationActionReceiver()
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

    private fun registerNoisyReceiver() {
        try {
            noisyReceiver = BecomingNoisyReceiver()
            val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(noisyReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(noisyReceiver, filter)
            }
        } catch (e: Exception) {
            android.util.Log.e("AudioPlaybackService", "Failed to register noisy receiver", e)
            noisyReceiver = null
        }
    }

    private fun unregisterNoisyReceiver() {
        noisyReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (e: Exception) {
                // Receiver may not be registered
            }
        }
        noisyReceiver = null
    }

    private fun requestAudioFocus(): Boolean {
        val am = audioManager ?: return false

        // Abandon any previous audio focus request to avoid conflicting listeners
        audioFocusRequest?.let { am.abandonAudioFocusRequest(it) }

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

        val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(audioAttributes)
            .setAcceptsDelayedFocusGain(true)
            .setOnAudioFocusChangeListener { focusChange ->
                when (focusChange) {
                    AudioManager.AUDIOFOCUS_LOSS -> {
                        // Lost focus permanently - pause playback
                        player?.pause()
                    }
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                        // Lost focus temporarily - pause playback
                        player?.pause()
                    }
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                        // Lost focus temporarily but can duck - lower volume
                        player?.volume = 0.3f
                    }
                    AudioManager.AUDIOFOCUS_GAIN -> {
                        // Regained focus - restore volume and resume if needed
                        player?.volume = 1.0f
                    }
                }
            }
            .build()

        audioFocusRequest = focusRequest
        return am.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonAudioFocus() {
        audioManager?.let { am ->
            audioFocusRequest?.let { am.abandonAudioFocusRequest(it) }
        }
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

        player = ExoPlayer.Builder(this)
            .setSeekBackIncrementMs(skipBackMs)
            .setSeekForwardIncrementMs(skipForwardMs)
            .setLoadControl(loadControl)
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
                            markFinished()
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
                        syncProgress()
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

                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    android.util.Log.e("AudioPlaybackService", "Player error: ${error.message}", error)
                    playerState.updateLoadingState(false)
                    playerState.updatePlayingState(false)
                    // Show a toast to the user
                    android.widget.Toast.makeText(
                        this@AudioPlaybackService,
                        "Playback error: ${error.message ?: "Unknown error"}",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            })
        }

        // Set audio attributes on ExoPlayer for proper Bluetooth/audio routing
        // This tells the system what type of audio we're playing (speech media)
        val media3AudioAttributes = Media3AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
            .build()
        // handleAudioFocus=false because we manage audio focus manually via requestAudioFocus()
        player?.setAudioAttributes(media3AudioAttributes, false)

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
            val packageName = controller.packageName
            android.util.Log.d("AutoService", "Connection from: $packageName")
            
            // Check if this is an Android Auto connection
            val isAndroidAuto = packageName.contains("android.projection") || 
                               packageName.contains("com.google.android.projection.gearhead") ||
                               packageName == "com.google.android.gms"
            
            if (isAndroidAuto) {
                android.util.Log.i("AutoService", "Android Auto connection detected")
            }

            // Add custom commands for skip forward/backward
            val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon()
                .add(SessionCommand(ACTION_SKIP_FORWARD, Bundle.EMPTY))
                .add(SessionCommand(ACTION_SKIP_BACKWARD, Bundle.EMPTY))
                .build()

            // Enable player commands including SEEK_TO_PREVIOUS/NEXT for system media controls
            // The ForwardingPlayer intercepts these and performs 10-second skips
            val playerCommands = Player.Commands.Builder()
                .addAll(
                    Player.COMMAND_PLAY_PAUSE,
                    Player.COMMAND_SEEK_BACK,
                    Player.COMMAND_SEEK_FORWARD,
                    Player.COMMAND_SEEK_TO_PREVIOUS,
                    Player.COMMAND_SEEK_TO_NEXT,
                    Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
                    Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
                    Player.COMMAND_GET_TIMELINE,
                    Player.COMMAND_SET_MEDIA_ITEM,
                    Player.COMMAND_STOP,
                    Player.COMMAND_SET_SPEED_AND_PITCH,
                    Player.COMMAND_GET_AUDIO_ATTRIBUTES
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

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>
        ): ListenableFuture<MutableList<MediaItem>> {
            // This is called when Android Auto requests to play an item
            val future = SettableFuture.create<MutableList<MediaItem>>()

            if (mediaItems.isEmpty()) {
                future.set(mutableListOf())
                return future
            }

            val mediaItem = mediaItems.first()
            val mediaId = mediaItem.mediaId

            // Check for voice search query in request metadata
            // This handles "Hey Google, play X on Sappho" commands
            val searchQuery = mediaItem.requestMetadata.searchQuery
            if (!searchQuery.isNullOrBlank()) {
                android.util.Log.d("AutoService", "Voice search detected: $searchQuery")
                serviceScope.launch {
                    try {
                        val response = api.getAudiobooks(search = searchQuery, limit = 5)
                        if (response.isSuccessful) {
                            val audiobooks = response.body()?.audiobooks ?: emptyList()
                            if (audiobooks.isNotEmpty()) {
                                val audiobook = audiobooks.first()
                                android.util.Log.d("AutoService", "Voice search - playing: ${audiobook.title}")
                                val startPosition = audiobook.progress?.position ?: 0
                                val playableItem = buildPlayableMediaItem(audiobook, startPosition)
                                future.set(mutableListOf(playableItem))
                                loadAndPlay(audiobook, startPosition)
                            } else {
                                android.util.Log.w("AutoService", "Voice search - no results for: $searchQuery")
                                future.set(mutableListOf())
                            }
                        } else {
                            future.set(mutableListOf())
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("AutoService", "Voice search error", e)
                        future.set(mutableListOf())
                    }
                }
                return future
            }

            // Check if it's a chapter request
            if (mediaId.contains("_chapter_")) {
                val parts = mediaId.split("_chapter_")
                val audiobookId = parts[0].toIntOrNull()
                val chapterIndex = parts.getOrNull(1)?.toIntOrNull() ?: 0

                if (audiobookId != null) {
                    serviceScope.launch {
                        try {
                            val audiobook = audiobookCache[audiobookId] ?: run {
                                val response = api.getAudiobook(audiobookId)
                                response.body()?.also { audiobookCache[audiobookId] = it }
                            }

                            if (audiobook != null) {
                                val chapter = audiobook.chapters?.getOrNull(chapterIndex)
                                val startPosition = chapter?.startTime?.toInt() ?: 0

                                // Build the actual playable media item with URI
                                val playableItem = buildPlayableMediaItem(audiobook, startPosition)
                                future.set(mutableListOf(playableItem))

                                // Start playback
                                loadAndPlay(audiobook, startPosition)
                            } else {
                                future.set(mutableListOf())
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("AudioPlaybackService", "Error loading chapter", e)
                            future.set(mutableListOf())
                        }
                    }
                } else {
                    future.set(mutableListOf())
                }
            } else {
                // Regular audiobook playback
                val audiobookId = mediaId.toIntOrNull()

                if (audiobookId != null) {
                    serviceScope.launch {
                        try {
                            val audiobook = audiobookCache[audiobookId] ?: run {
                                val response = api.getAudiobook(audiobookId)
                                response.body()?.also { audiobookCache[audiobookId] = it }
                            }

                            if (audiobook != null) {
                                // Resume from saved position if available
                                val startPosition = audiobook.progress?.position ?: 0

                                // Build the actual playable media item with URI
                                val playableItem = buildPlayableMediaItem(audiobook, startPosition)
                                future.set(mutableListOf(playableItem))

                                // Start playback
                                loadAndPlay(audiobook, startPosition)
                            } else {
                                future.set(mutableListOf())
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("AudioPlaybackService", "Error loading audiobook for playback", e)
                            future.set(mutableListOf())
                        }
                    }
                } else {
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

            // Store the search query for later use in onGetSearchResult
            lastSearchQuery = query

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

        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            android.util.Log.d("AutoService", "onPlaybackResumption called")
            val future = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()

            serviceScope.launch {
                try {
                    // Try to resume the last played audiobook
                    val response = api.getInProgress(limit = 1)
                    if (response.isSuccessful) {
                        val audiobooks: List<Audiobook> = response.body() ?: emptyList()
                        if (audiobooks.isNotEmpty()) {
                            val audiobook: Audiobook = audiobooks.first()
                            val startPosition = audiobook.progress?.position ?: 0
                            val playableItem = buildPlayableMediaItem(audiobook, startPosition)

                            future.set(MediaSession.MediaItemsWithStartPosition(
                                listOf(playableItem),
                                0,
                                startPosition.toLong() * 1000 // Convert to milliseconds
                            ))

                            // Trigger actual playback
                            loadAndPlay(audiobook, startPosition)
                        } else {
                            future.setException(Exception("No audiobooks to resume"))
                        }
                    } else {
                        future.setException(Exception("Failed to load audiobooks"))
                    }
                } catch (e: Exception) {
                    android.util.Log.e("AutoService", "Error in playback resumption", e)
                    future.setException(e)
                }
            }

            return future
        }

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
        } catch (e: Exception) {
            android.util.Log.e("AutoService", "Exception during search", e)
            lastSearchResults = emptyList()
        }
    }

    /**
     * Search and auto-play the best matching audiobook.
     * Used by voice search ("Hey Google, play X on Sappho").
     */
    private fun searchAndPlayAudiobook(query: String) {
        if (query.isBlank()) return

        serviceScope.launch {
            try {
                android.util.Log.d("AutoService", "Voice search - searching for: $query")
                val response = api.getAudiobooks(search = query, limit = 5)
                if (response.isSuccessful) {
                    val audiobooks = response.body()?.audiobooks ?: emptyList()
                    if (audiobooks.isNotEmpty()) {
                        // Play the best match (first result)
                        val audiobook = audiobooks.first()
                        android.util.Log.d("AutoService", "Voice search - playing: ${audiobook.title}")
                        val startPosition = audiobook.progress?.position ?: 0
                        loadAndPlay(audiobook, startPosition)
                    } else {
                        android.util.Log.w("AutoService", "Voice search - no results for: $query")
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("AutoService", "Voice search error", e)
            }
        }
    }

    private fun buildPlayableMediaItem(audiobook: Audiobook, startPosition: Int = 0): MediaItem {
        val serverUrl = authRepository.getServerUrlSync() ?: ""
        val token = authRepository.getTokenSync() ?: ""

        // Check for downloaded file first
        val localFilePath = downloadManager.getLocalFilePath(audiobook.id)
        val mediaUri = if (localFilePath != null && File(localFilePath).exists()) {
            Uri.fromFile(File(localFilePath))
        } else {
            Uri.parse("$serverUrl/api/audiobooks/${audiobook.id}/stream?token=$token")
        }

        val coverArtUri = if (audiobook.coverImage != null && serverUrl.isNotEmpty() && token.isNotEmpty()) {
            Uri.parse("$serverUrl/api/audiobooks/${audiobook.id}/cover?token=$token")
        } else {
            null
        }

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

    private fun loadChapters(audiobookId: Int, params: LibraryParams?): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()

        serviceScope.launch {
            try {
                val audiobook = audiobookCache[audiobookId] ?: run {
                    val response = api.getAudiobook(audiobookId)
                    response.body()?.also { audiobookCache[audiobookId] = it }
                }

                if (audiobook != null && !audiobook.chapters.isNullOrEmpty()) {
                    val serverUrl = authRepository.getServerUrlSync() ?: ""
                    val token = authRepository.getTokenSync() ?: ""

                    val coverArtUri = if (audiobook.coverImage != null && serverUrl.isNotEmpty() && token.isNotEmpty()) {
                        Uri.parse("$serverUrl/api/audiobooks/${audiobook.id}/cover?token=$token")
                    } else {
                        null
                    }

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
            } catch (e: Exception) {
                future.set(LibraryResult.ofError(SessionError.ERROR_NOT_SUPPORTED))
            }
        }

        return future
    }

    private fun createPlayableMediaItem(audiobook: Audiobook): MediaItem {
        val serverUrl = authRepository.getServerUrlSync() ?: ""
        val token = authRepository.getTokenSync() ?: ""

        // Cache the audiobook for later use
        audiobookCache[audiobook.id] = audiobook

        // For Android Auto, we need to handle authentication differently
        // Some Android Auto systems may not properly handle tokens in URLs
        val coverArtUri = if (audiobook.coverImage != null && serverUrl.isNotEmpty()) {
            // Try to use a simpler URL structure that might work better with Auto
            if (token.isNotEmpty()) {
                Uri.parse("$serverUrl/api/audiobooks/${audiobook.id}/cover?token=$token")
            } else {
                // Fallback for unauthenticated or when token is unavailable
                Uri.parse("$serverUrl/api/audiobooks/${audiobook.id}/cover")
            }
        } else {
            null
        }

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

        // Ensure player is initialized - reinitialize if it was released
        if (player == null || mediaLibrarySession == null) {
            initializePlayer()
        }

        player?.let { exoPlayer ->
            android.util.Log.d("AudioPlaybackService", "loadAndPlay: book=${audiobook.title}, startPosition=$startPosition")

            // Stop any existing playback to ensure clean state
            exoPlayer.stop()

            // Always set position to what we intend to play
            playerState.updatePosition(startPosition.toLong())
            playerState.updateAudiobook(audiobook)
            playerState.updateLoadingState(true)

            // Request audio focus before playing
            if (!requestAudioFocus()) {
                android.util.Log.w("AudioPlaybackService", "Audio focus request failed")
                playerState.updateLoadingState(false)
                return
            }

            // Load cover bitmap for notification
            loadCoverBitmap(audiobook)

            // Get server URL and token
            val serverUrl = authRepository.getServerUrlSync()
            val token = authRepository.getTokenSync()

            // Check if we have a downloaded copy
            val localFilePath = downloadManager.getLocalFilePath(audiobook.id)
            val mediaUri: Uri

            if (localFilePath != null && File(localFilePath).exists()) {
                // Use local downloaded file
                mediaUri = Uri.fromFile(File(localFilePath))
                isPlayingLocalFile = true
                android.util.Log.d("AudioPlaybackService", "Using local file: $localFilePath")
            } else {
                // Stream from server
                if (serverUrl == null || token == null) {
                    android.util.Log.e("AudioPlaybackService", "No server URL or token available")
                    playerState.updateLoadingState(false)
                    return
                }
                mediaUri = Uri.parse("$serverUrl/api/audiobooks/${audiobook.id}/stream?token=$token")
                isPlayingLocalFile = false
                android.util.Log.d("AudioPlaybackService", "Streaming from: $mediaUri")
            }

            // Build cover art URI for notification
            val coverArtUri = if (audiobook.coverImage != null && !serverUrl.isNullOrEmpty() && !token.isNullOrEmpty()) {
                Uri.parse("$serverUrl/api/audiobooks/${audiobook.id}/cover?token=$token")
            } else {
                null
            }

            val mediaItem = MediaItem.Builder()
                .setUri(mediaUri)
                .setMediaId(audiobook.id.toString())
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(audiobook.title)
                        .setArtist(audiobook.author)
                        .setAlbumTitle(audiobook.series)
                        .setArtworkUri(coverArtUri)
                        .setMediaType(MediaMetadata.MEDIA_TYPE_AUDIO_BOOK)
                        .build()
                )
                .build()

            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()

            // Always seek to the target position, even if it's 0
            // This ensures consistent player state and mirrors the web player behavior
            // that always sets currentTime after the audio is ready
            // Apply rewind on resume if resuming from a saved position
            val rewindSeconds = if (startPosition > 0) userPreferences.rewindOnResumeSeconds.value else 0
            val adjustedPosition = (startPosition - rewindSeconds).coerceAtLeast(0)
            android.util.Log.d("AudioPlaybackService", "Seeking to position: ${adjustedPosition * 1000L}ms (rewind: ${rewindSeconds}s)")
            exoPlayer.seekTo(adjustedPosition * 1000L)

            android.util.Log.d("AudioPlaybackService", "Calling play()")
            exoPlayer.play()

            // Mark the start of this playback session for progress sync delay
            playbackSessionStartTime = System.currentTimeMillis()

            // Restore saved playback speed, or use default preference if not set
            val savedSpeed = authRepository.getPlaybackSpeed()
            val effectiveSpeed = if (savedSpeed == 1.0f) {
                // No custom speed saved, use default preference
                userPreferences.defaultPlaybackSpeed.value
            } else {
                savedSpeed
            }
            exoPlayer.setPlaybackSpeed(effectiveSpeed)
            playerState.updatePlaybackSpeed(effectiveSpeed)

            startProgressSync()

            // Start foreground with our custom MediaStyle notification
            startForeground(NOTIFICATION_ID, createNotification())

            // Try to sync any pending offline progress when we start playback
            syncPendingProgress()
        }
    }

    fun togglePlayPause(): Boolean {
        val exoPlayer = player
        if (exoPlayer == null) {
            // Player is null - signal caller to restart playback
            return false
        }

        if (exoPlayer.isPlaying) {
            exoPlayer.pause()
        } else {
            // Request audio focus before resuming playback
            if (!requestAudioFocus()) {
                return true // Still return true since player exists, just can't get focus
            }
            exoPlayer.play()
        }
        return true
    }

    fun isCurrentlyPlaying(): Boolean {
        return player?.isPlaying == true
    }

    fun seekTo(seconds: Long) {
        player?.seekTo(seconds * 1000)
        // Immediately update UI position so slider doesn't snap back
        playerState.updatePosition(seconds)
        playerState.updateLastActiveTimestamp()
    }

    fun seekToAndPlay(seconds: Long) {
        player?.let {
            it.seekTo(seconds * 1000)
            // Immediately update UI position so slider doesn't snap back
            playerState.updatePosition(seconds)
            playerState.updateLastActiveTimestamp()
            if (!it.isPlaying) {
                it.play()
            }
        }
    }

    fun skipForward() {
        player?.let {
            val skipSeconds = userPreferences.skipForwardSeconds.value.toLong()
            val newPosition = (it.currentPosition / 1000 + skipSeconds).coerceAtMost(playerState.duration.value)
            seekTo(newPosition)
        }
    }

    fun skipBackward() {
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
        playerState.updateSleepTimerRemaining(null)
    }

    private fun startPositionUpdates() {
        positionUpdateJob?.cancel()
        positionUpdateJob = serviceScope.launch {
            while (isActive) {
                player?.let {
                    playerState.updatePosition(it.currentPosition / 1000)
                    playerState.updateBufferedPosition(it.contentBufferedPosition / 1000)
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
        serviceScope.launch {
            playerState.currentAudiobook.value?.let { book ->
                val position = playerState.currentPosition.value.toInt()
                val totalDuration = playerState.duration.value.toInt()

                // Skip sync if near the end of the book
                if (totalDuration > 0 && (totalDuration - position) < 30) {
                    return@launch
                }

                // Skip sync if not enough time has passed since playback started
                // This prevents recording progress for accidental plays or quick skips
                val secondsSinceStart = (System.currentTimeMillis() - playbackSessionStartTime) / 1000
                if (secondsSinceStart < INITIAL_PROGRESS_DELAY_SECONDS) {
                    return@launch
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
                } catch (e: Exception) {
                    if (isPlayingLocalFile) {
                        // Playing downloaded file offline - save progress locally for later sync
                        downloadManager.saveOfflineProgress(book.id, position)
                    }
                    // When streaming, transient API errors are expected and don't need
                    // offline progress tracking — the next sync cycle will succeed
                }
            }
        }
    }

    private fun syncPendingProgress() {
        serviceScope.launch {
            val pendingList = downloadManager.getPendingProgressList()
            if (pendingList.isEmpty()) return@launch

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
                    downloadManager.clearPendingProgress(pending.audiobookId)
                } catch (_: Exception) {
                    // Continue trying remaining items
                }
            }
        }
    }

    private fun markFinished() {
        serviceScope.launch {
            try {
                playerState.currentAudiobook.value?.let { book ->
                    api.markFinished(book.id, ProgressUpdateRequest(0, 1, "stopped"))
                }
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
        val token = authRepository.getTokenSync() ?: return
        if (audiobook.coverImage == null) {
            currentCoverBitmap = null
            return
        }

        serviceScope.launch(Dispatchers.IO) {
            try {
                val coverUrl = "$serverUrl/api/audiobooks/${audiobook.id}/cover?token=$token"
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
            } catch (e: Exception) {
                android.util.Log.e("AudioPlaybackService", "Failed to load cover bitmap", e)
            }
        }
    }

    fun stopPlayback() {
        syncProgress()
        player?.stop()
        player?.release()
        player = null
        mediaLibrarySession?.release()
        mediaLibrarySession = null
        progressSyncJob?.cancel()
        positionUpdateJob?.cancel()
        sleepTimerJob?.cancel()
        pauseTimeoutJob?.cancel()
        playerState.deactivate()
        audiobookCache.clear() // Clear cache to avoid stale data after re-login
        abandonAudioFocus()
        unregisterNoisyReceiver()
        unregisterNotificationActionReceiver()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        // Allow any app to connect to the media session for Android Auto compatibility
        // The MediaLibrarySession.Callback methods handle authorization
        return mediaLibrarySession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val isPlaying = player?.isPlaying == true

        // Sync progress synchronously
        val book = playerState.currentAudiobook.value
        val position = playerState.currentPosition.value.toInt()
        val duration = playerState.duration.value.toInt()
        if (book != null && position > 0 && (duration == 0 || (duration - position) >= 30)) {
            runBlocking {
                try {
                    api.updateProgress(
                        book.id,
                        ProgressUpdateRequest(position = position, completed = 0, state = if (isPlaying) "playing" else "paused")
                    )
                } catch (_: Exception) {
                    if (isPlayingLocalFile) {
                        downloadManager.saveOfflineProgress(book.id, position)
                    }
                }
            }
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
        // Sync progress synchronously — the coroutine-based syncProgress() won't complete
        // because serviceScope is about to be cancelled
        val book = playerState.currentAudiobook.value
        val position = playerState.currentPosition.value.toInt()
        val duration = playerState.duration.value.toInt()
        if (book != null && position > 0 && (duration == 0 || (duration - position) >= 30)) {
            runBlocking {
                try {
                    api.updateProgress(
                        book.id,
                        ProgressUpdateRequest(position = position, completed = 0, state = "stopped")
                    )
                } catch (_: Exception) {
                    if (isPlayingLocalFile) {
                        downloadManager.saveOfflineProgress(book.id, position)
                    }
                }
            }
        }
        player?.release()
        mediaLibrarySession?.release()
        progressSyncJob?.cancel()
        positionUpdateJob?.cancel()
        pauseTimeoutJob?.cancel()
        abandonAudioFocus()
        unregisterNoisyReceiver()
        unregisterNotificationActionReceiver()
        playerState.deactivate()
        super.onDestroy()
    }
}
