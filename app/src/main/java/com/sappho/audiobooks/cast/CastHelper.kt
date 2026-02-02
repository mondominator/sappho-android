package com.sappho.audiobooks.cast

import android.app.Activity
import android.content.Context
import android.view.Menu
import android.view.MenuItem
import androidx.mediarouter.media.MediaRouter
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.MediaQueueItem
import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.google.android.gms.common.images.WebImage
import com.sappho.audiobooks.data.repository.AuthRepository
import com.sappho.audiobooks.domain.model.Audiobook
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CastHelper @Inject constructor(
    private val authRepository: AuthRepository
) {

    private var castContext: CastContext? = null
    private var mediaRouter: MediaRouter? = null
    private var mediaRouterCallback: MediaRouter.Callback? = null

    private val _isPlaying = MutableStateFlow(false)
    val isPlayingFlow: StateFlow<Boolean> = _isPlaying

    private val _availableRoutes = MutableStateFlow<List<MediaRouter.RouteInfo>>(emptyList())
    val availableRoutes: StateFlow<List<MediaRouter.RouteInfo>> = _availableRoutes

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected

    private val _connectedDeviceName = MutableStateFlow<String?>(null)
    val connectedDeviceName: StateFlow<String?> = _connectedDeviceName

    private val _currentPosition = MutableStateFlow(0L)
    val currentPositionFlow: StateFlow<Long> = _currentPosition

    // Pending media to load after session is established
    private var pendingAudiobook: Audiobook? = null
    private var pendingStreamUrl: String? = null
    private var pendingCoverUrl: String? = null
    private var pendingPosition: Long = 0

    private val remoteMediaClientCallback = object : RemoteMediaClient.Callback() {
        override fun onStatusUpdated() {
            val remoteMediaClient = getCastSession()?.remoteMediaClient
            val isPlaying = remoteMediaClient?.isPlaying ?: false
            val position = remoteMediaClient?.approximateStreamPosition?.div(1000) ?: 0L
            _isPlaying.value = isPlaying
            _currentPosition.value = position
        }

        override fun onMetadataUpdated() {
            val remoteMediaClient = getCastSession()?.remoteMediaClient
            val isPlaying = remoteMediaClient?.isPlaying ?: false
            val position = remoteMediaClient?.approximateStreamPosition?.div(1000) ?: 0L
            _isPlaying.value = isPlaying
            _currentPosition.value = position
        }
    }

    private val sessionManagerListener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarting(session: CastSession) {
            val deviceName = session.castDevice?.friendlyName ?: "unknown"
            val deviceId = session.castDevice?.deviceId ?: "unknown"
            android.util.Log.d("CastHelper", "onSessionStarting: device='$deviceName' deviceId='$deviceId'")
        }

        override fun onSessionStarted(session: CastSession, sessionId: String) {
            val deviceName = session.castDevice?.friendlyName ?: "unknown"
            val deviceId = session.castDevice?.deviceId ?: "unknown"
            android.util.Log.d("CastHelper", ">>> onSessionStarted: CONNECTED TO device='$deviceName' deviceId='$deviceId' sessionId='$sessionId'")
            _isConnected.value = true
            _connectedDeviceName.value = deviceName
            session.remoteMediaClient?.registerCallback(remoteMediaClientCallback)

            // Load pending media if any
            loadPendingMedia()
        }

        override fun onSessionStartFailed(session: CastSession, error: Int) {
            val deviceName = session.castDevice?.friendlyName ?: "unknown"
            android.util.Log.e("CastHelper", "onSessionStartFailed: device='$deviceName' error=$error")
            _isConnected.value = false
            clearPendingMedia()
        }

        override fun onSessionEnding(session: CastSession) {
            val deviceName = session.castDevice?.friendlyName ?: "unknown"
            android.util.Log.d("CastHelper", "onSessionEnding: device='$deviceName'")
        }

        override fun onSessionEnded(session: CastSession, error: Int) {
            val deviceName = session.castDevice?.friendlyName ?: "unknown"
            android.util.Log.d("CastHelper", "onSessionEnded: device='$deviceName' error=$error")
            _isConnected.value = false
            _connectedDeviceName.value = null
            _isPlaying.value = false
        }

        override fun onSessionResuming(session: CastSession, sessionId: String) {
            val deviceName = session.castDevice?.friendlyName ?: "unknown"
            android.util.Log.d("CastHelper", "onSessionResuming: device='$deviceName' sessionId='$sessionId'")
        }

        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
            val deviceName = session.castDevice?.friendlyName ?: "unknown"
            android.util.Log.d("CastHelper", ">>> onSessionResumed: RECONNECTED TO device='$deviceName' wasSuspended=$wasSuspended")
            _isConnected.value = true
            _connectedDeviceName.value = deviceName
            session.remoteMediaClient?.registerCallback(remoteMediaClientCallback)
        }

        override fun onSessionResumeFailed(session: CastSession, error: Int) {
            val deviceName = session.castDevice?.friendlyName ?: "unknown"
            android.util.Log.e("CastHelper", "onSessionResumeFailed: device='$deviceName' error=$error")
            _isConnected.value = false
        }

        override fun onSessionSuspended(session: CastSession, reason: Int) {
            val deviceName = session.castDevice?.friendlyName ?: "unknown"
            android.util.Log.d("CastHelper", "onSessionSuspended: device='$deviceName' reason=$reason")
        }
    }

    fun initialize(context: Context) {
        try {
            castContext = CastContext.getSharedInstance(context.applicationContext)
            mediaRouter = MediaRouter.getInstance(context.applicationContext)

            // Register session manager listener
            castContext?.sessionManager?.addSessionManagerListener(
                sessionManagerListener,
                CastSession::class.java
            )

            // Don't auto-connect to existing Cast sessions - start fresh
            // If there's an existing session, end it so user can choose to connect again
            val existingSession = castContext?.sessionManager?.currentCastSession
            if (existingSession != null) {
                val deviceName = existingSession.castDevice?.friendlyName ?: "unknown"
                val isConnected = existingSession.isConnected
                // Stop any playing media first
                try {
                    existingSession.remoteMediaClient?.stop()
                } catch (e: Exception) {
                }
                // End the session with stopCasting=true to also stop on the receiver
                castContext?.sessionManager?.endCurrentSession(true)
            }
            _isConnected.value = false
            _connectedDeviceName.value = null
            _isPlaying.value = false
            _currentPosition.value = 0L

        } catch (e: Exception) {
            android.util.Log.e("CastHelper", "Error initializing Cast", e)
        }
    }

    private fun loadPendingMedia(retryCount: Int = 0) {
        val audiobook = pendingAudiobook
        val streamUrl = pendingStreamUrl
        val coverUrl = pendingCoverUrl
        val position = pendingPosition

        android.util.Log.d("CastHelper", "loadPendingMedia: retryCount=$retryCount, audiobook=${audiobook?.title}, streamUrl=$streamUrl")

        if (audiobook != null && streamUrl != null) {
            // Check if remote media client is ready
            val session = getCastSession()
            val remoteMediaClient = session?.remoteMediaClient

            android.util.Log.d("CastHelper", "loadPendingMedia: session=${session != null}, isConnected=${session?.isConnected}, remoteMediaClient=${remoteMediaClient != null}")

            if (remoteMediaClient != null && session.isConnected) {
                android.util.Log.d("CastHelper", "loadPendingMedia: Calling loadMedia()")
                loadMedia(audiobook, streamUrl, coverUrl, position)
                clearPendingMedia()
            } else if (retryCount < 5) {
                // Retry with increasing delay
                val delay = 1000L * (retryCount + 1)
                android.util.Log.d("CastHelper", "loadPendingMedia: Remote media client not ready, retrying in ${delay}ms")
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    loadPendingMedia(retryCount + 1)
                }, delay)
            } else {
                android.util.Log.e("CastHelper", "Failed to load media after $retryCount retries - remote media client not available")
                clearPendingMedia()
            }
        } else {
            android.util.Log.w("CastHelper", "loadPendingMedia: No pending media to load (audiobook=${audiobook != null}, streamUrl=${streamUrl != null})")
        }
    }

    private fun clearPendingMedia() {
        pendingAudiobook = null
        pendingStreamUrl = null
        pendingCoverUrl = null
        pendingPosition = 0
    }

    fun getCastSession(): CastSession? {
        return castContext?.sessionManager?.currentCastSession
    }

    fun isCasting(): Boolean {
        return getCastSession()?.isConnected == true
    }

    /**
     * Start scanning for Cast devices. Call this when opening the Cast dialog.
     */
    fun startDiscovery(context: Context) {
        try {
            val router = mediaRouter ?: MediaRouter.getInstance(context)
            val selector = castContext?.mergedSelector

            if (selector == null) {
                return
            }

            // Remove any existing callback first
            mediaRouterCallback?.let { router.removeCallback(it) }

            // Create new callback for route discovery
            mediaRouterCallback = object : MediaRouter.Callback() {
                override fun onRouteAdded(router: MediaRouter, route: MediaRouter.RouteInfo) {
                    updateAvailableRoutes(context)
                }

                override fun onRouteRemoved(router: MediaRouter, route: MediaRouter.RouteInfo) {
                    updateAvailableRoutes(context)
                }

                override fun onRouteChanged(router: MediaRouter, route: MediaRouter.RouteInfo) {
                    updateAvailableRoutes(context)
                }
            }

            // Start active scanning with CALLBACK_FLAG_REQUEST_DISCOVERY
            router.addCallback(
                selector,
                mediaRouterCallback!!,
                MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY or MediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN
            )


            // Update routes immediately
            updateAvailableRoutes(context)

        } catch (e: Exception) {
            android.util.Log.e("CastHelper", "Error starting discovery", e)
        }
    }

    /**
     * Stop scanning for Cast devices. Call this when closing the Cast dialog.
     */
    fun stopDiscovery() {
        try {
            mediaRouterCallback?.let {
                mediaRouter?.removeCallback(it)
                mediaRouterCallback = null
            }
        } catch (e: Exception) {
            android.util.Log.e("CastHelper", "Error stopping discovery", e)
        }
    }

    @Suppress("RestrictedApi") // No public API alternative for isDefaultOrBluetooth
    private fun updateAvailableRoutes(context: Context) {
        val router = mediaRouter ?: MediaRouter.getInstance(context)
        val routes = mutableListOf<MediaRouter.RouteInfo>()

        castContext?.let { ctx ->
            val selector = ctx.mergedSelector
            if (selector != null) {
                for (route in router.routes) {
                    if (route.matchesSelector(selector) && !route.isDefaultOrBluetooth) {
                        routes.add(route)
                    }
                }
            }
        }

        _availableRoutes.value = routes
    }

    fun getAvailableRoutes(context: Context): List<MediaRouter.RouteInfo> {
        updateAvailableRoutes(context)
        return _availableRoutes.value
    }

    fun selectRoute(context: Context, route: MediaRouter.RouteInfo) {
        try {
            android.util.Log.d("CastHelper", "selectRoute: === SELECTING CAST DEVICE ===")
            android.util.Log.d("CastHelper", "selectRoute: Target route name='${route.name}', id='${route.id}'")

            // Get CastDevice from the route
            val castDevice = com.google.android.gms.cast.CastDevice.getFromBundle(route.extras)
            android.util.Log.d("CastHelper", "selectRoute: CastDevice from route: ${castDevice?.friendlyName ?: "NULL"} (deviceId: ${castDevice?.deviceId ?: "NULL"})")

            // End any existing session first
            val existingSession = getCastSession()
            if (existingSession != null) {
                val currentDevice = existingSession.castDevice?.friendlyName
                val currentDeviceId = existingSession.castDevice?.deviceId
                android.util.Log.d("CastHelper", "selectRoute: Ending existing session on '$currentDevice' (deviceId: $currentDeviceId)")

                // Stop any media first
                try {
                    existingSession.remoteMediaClient?.stop()
                } catch (e: Exception) {
                    android.util.Log.w("CastHelper", "selectRoute: Error stopping media: ${e.message}")
                }

                castContext?.sessionManager?.endCurrentSession(true)

                // Use non-blocking delay then select route
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    performRouteSelection(context, route)
                }, 500)
            } else {
                // No existing session, select immediately
                performRouteSelection(context, route)
            }

        } catch (e: Exception) {
            android.util.Log.e("CastHelper", "Error selecting route", e)
            android.widget.Toast.makeText(context, "Failed to connect: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun performRouteSelection(context: Context, route: MediaRouter.RouteInfo) {
        try {
            // Log all available routes for debugging
            val router = mediaRouter ?: MediaRouter.getInstance(context)
            android.util.Log.d("CastHelper", "performRouteSelection: Available routes:")
            router.routes.forEach { r ->
                val rDevice = com.google.android.gms.cast.CastDevice.getFromBundle(r.extras)
                android.util.Log.d("CastHelper", "  - '${r.name}' id='${r.id}' deviceId='${rDevice?.deviceId ?: "N/A"}' selected=${r.isSelected}")
            }

            // Select the route - this should trigger Cast SDK to connect to this specific device
            android.util.Log.d("CastHelper", "performRouteSelection: Calling route.select() for '${route.name}'")
            route.select()

            android.util.Log.d("CastHelper", "performRouteSelection: route.select() called, waiting for session callback...")
        } catch (e: Exception) {
            android.util.Log.e("CastHelper", "Error in performRouteSelection", e)
            android.widget.Toast.makeText(context, "Failed to connect: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Select a route by its ID - more reliable than passing route object
     */
    fun selectRouteById(context: Context, routeId: String) {
        try {
            val router = mediaRouter ?: MediaRouter.getInstance(context)

            // Log all available routes for debugging
            router.routes.forEach { r ->
            }

            val route = router.routes.find { it.id == routeId }

            if (route != null) {
                route.select()
            } else {
                android.util.Log.e("CastHelper", "selectRouteById: Route not found for id $routeId")
                android.widget.Toast.makeText(context, "Device not found, please try again", android.widget.Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            android.util.Log.e("CastHelper", "Error selecting route by id", e)
            android.widget.Toast.makeText(context, "Failed to connect: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Get the MediaRouteSelector from CastContext for use with MediaRouteButton
     */
    fun getMediaRouteSelector(): androidx.mediarouter.media.MediaRouteSelector? {
        return castContext?.mergedSelector
    }

    /**
     * Setup a MediaRouteButton with the Cast SDK's selector
     */
    fun setupMediaRouteButton(button: androidx.mediarouter.app.MediaRouteButton, context: Context) {
        try {
            val selector = castContext?.mergedSelector
            if (selector != null) {
                button.routeSelector = selector
            } else {
            }
        } catch (e: Exception) {
            android.util.Log.e("CastHelper", "Error setting up MediaRouteButton", e)
        }
    }

    fun disconnectCast() {
        try {
            castContext?.sessionManager?.endCurrentSession(true)
        } catch (e: Exception) {
            android.util.Log.e("CastHelper", "Error disconnecting", e)
        }
    }

    fun castAudiobook(
        audiobook: Audiobook,
        streamUrl: String,
        coverUrl: String?,
        currentPosition: Long = 0
    ) {
        android.util.Log.d("CastHelper", "castAudiobook: Setting pending media - title='${audiobook.title}', streamUrl=$streamUrl, position=$currentPosition")

        // Store as pending media - will be loaded when session is ready
        pendingAudiobook = audiobook
        pendingStreamUrl = streamUrl
        pendingCoverUrl = coverUrl
        pendingPosition = currentPosition

        // If already connected, load immediately
        val castSession = getCastSession()
        val isConnected = castSession?.isConnected == true
        val hasRemoteClient = castSession?.remoteMediaClient != null
        android.util.Log.d("CastHelper", "castAudiobook: castSession=${castSession != null}, isConnected=$isConnected, hasRemoteClient=$hasRemoteClient")

        if (isConnected && hasRemoteClient) {
            android.util.Log.d("CastHelper", "castAudiobook: Already connected, loading immediately")
            loadPendingMedia()
        } else {
            android.util.Log.d("CastHelper", "castAudiobook: Not connected yet, media will load when session starts")
        }
    }

    private fun loadMedia(
        audiobook: Audiobook,
        streamUrl: String,
        coverUrl: String?,
        currentPosition: Long
    ) {
        android.util.Log.d("CastHelper", "loadMedia: === LOADING MEDIA TO CAST ===")
        android.util.Log.d("CastHelper", "loadMedia: title='${audiobook.title}', streamUrl=$streamUrl, position=$currentPosition")

        try {
            val castSession = getCastSession()
            if (castSession == null) {
                android.util.Log.e("CastHelper", "loadMedia: No cast session available")
                return
            }

            val deviceName = castSession.castDevice?.friendlyName ?: "unknown"
            android.util.Log.d("CastHelper", "loadMedia: Cast session device='$deviceName', isConnected=${castSession.isConnected}")

            val remoteMediaClient = castSession.remoteMediaClient
            if (remoteMediaClient == null) {
                android.util.Log.e("CastHelper", "loadMedia: No remote media client available")
                return
            }

            // Register callback to listen for Cast state changes
            remoteMediaClient.registerCallback(remoteMediaClientCallback)

            // Add access token to stream URL for Cast receiver authentication
            val token = authRepository.getTokenSync()
            val authenticatedStreamUrl = if (token != null) {
                "$streamUrl?token=$token"
            } else {
                streamUrl
            }

            android.util.Log.d("CastHelper", "loadMedia: Using authenticated URL (token present: ${token != null})")


            val metadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MUSIC_TRACK).apply {
                putString(MediaMetadata.KEY_TITLE, audiobook.title)
                audiobook.author?.let { putString(MediaMetadata.KEY_ARTIST, it) }
                coverUrl?.let {
                    addImage(WebImage(android.net.Uri.parse(it)))
                }
            }

            val mediaInfo = MediaInfo.Builder(authenticatedStreamUrl)
                .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
                .setContentType("audio/mp4")
                .setMetadata(metadata)
                .build()


            // Use MediaLoadRequestData (newer API)
            val loadRequestData = MediaLoadRequestData.Builder()
                .setMediaInfo(mediaInfo)
                .setAutoplay(true)
                .setCurrentTime(currentPosition * 1000) // Position in milliseconds
                .build()


            val loadRequest = remoteMediaClient.load(loadRequestData)

            loadRequest.setResultCallback { result ->
                if (result.status.isSuccess) {
                    // Update the playing state immediately
                    _isPlaying.value = true
                } else {
                    android.util.Log.e("CastHelper", "Failed to load media: statusCode=${result.status.statusCode}, statusMessage=${result.status.statusMessage}")
                }
            }

        } catch (e: Exception) {
            android.util.Log.e("CastHelper", "Error casting audiobook", e)
        }
    }

    fun stopCasting() {
        getCastSession()?.remoteMediaClient?.stop()
    }

    fun play() {
        try {
            getCastSession()?.remoteMediaClient?.play()
            // Don't set state here - let the callback handle it based on actual Cast receiver state
        } catch (e: Exception) {
            android.util.Log.e("CastHelper", "Error sending play command", e)
        }
    }

    fun pause() {
        try {
            getCastSession()?.remoteMediaClient?.pause()
            // Don't set state here - let the callback handle it based on actual Cast receiver state
        } catch (e: Exception) {
            android.util.Log.e("CastHelper", "Error sending pause command", e)
        }
    }

    fun seek(positionSeconds: Long) {
        try {
            getCastSession()?.remoteMediaClient?.seek(positionSeconds * 1000)
        } catch (e: Exception) {
            android.util.Log.e("CastHelper", "Error sending seek command", e)
        }
    }

    fun getCurrentPosition(): Long {
        return getCastSession()?.remoteMediaClient?.approximateStreamPosition?.div(1000) ?: 0
    }

    fun isPlaying(): Boolean {
        return getCastSession()?.remoteMediaClient?.isPlaying == true
    }
}
