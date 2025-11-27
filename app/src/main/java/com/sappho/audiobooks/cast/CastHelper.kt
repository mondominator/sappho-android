package com.sappho.audiobooks.cast

import android.content.Context
import androidx.mediarouter.media.MediaRouter
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.MediaQueueItem
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
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

    private val _isPlaying = MutableStateFlow(false)
    val isPlayingFlow: StateFlow<Boolean> = _isPlaying

    private val remoteMediaClientCallback = object : RemoteMediaClient.Callback() {
        override fun onStatusUpdated() {
            _isPlaying.value = getCastSession()?.remoteMediaClient?.isPlaying ?: false
        }

        override fun onMetadataUpdated() {
            _isPlaying.value = getCastSession()?.remoteMediaClient?.isPlaying ?: false
        }
    }

    fun initialize(context: Context) {
        try {
            castContext = CastContext.getSharedInstance(context.applicationContext)
        } catch (e: Exception) {
            android.util.Log.e("CastHelper", "Error initializing Cast", e)
        }
    }

    fun getCastSession(): CastSession? {
        return castContext?.sessionManager?.currentCastSession
    }

    fun isCasting(): Boolean {
        return getCastSession()?.isConnected == true
    }

    fun getAvailableRoutes(context: Context): List<androidx.mediarouter.media.MediaRouter.RouteInfo> {
        val mediaRouter = MediaRouter.getInstance(context)
        val routes = mutableListOf<MediaRouter.RouteInfo>()

        castContext?.let {
            val selector = it.mergedSelector
            if (selector != null) {
                for (route in mediaRouter.routes) {
                    if (route.matchesSelector(selector) && !route.isDefaultOrBluetooth) {
                        routes.add(route)
                    }
                }
            }
        }

        return routes
    }

    fun selectRoute(context: Context, route: MediaRouter.RouteInfo) {
        try {
            route.select()
            android.util.Log.d("CastHelper", "Selected route: ${route.name}")

            // Register callback to listen for Cast state changes
            getCastSession()?.remoteMediaClient?.registerCallback(remoteMediaClientCallback)
        } catch (e: Exception) {
            android.util.Log.e("CastHelper", "Error selecting route", e)
            android.widget.Toast.makeText(context, "Failed to connect: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
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
        try {
            val castSession = getCastSession()
            if (castSession == null) {
                android.util.Log.e("CastHelper", "No cast session available")
                return
            }

            val remoteMediaClient = castSession.remoteMediaClient
            if (remoteMediaClient == null) {
                android.util.Log.e("CastHelper", "No remote media client available")
                return
            }

            // Register callback to listen for Cast state changes
            remoteMediaClient.registerCallback(remoteMediaClientCallback)

            android.util.Log.d("CastHelper", "Casting audiobook: ${audiobook.title} from $streamUrl")

            // Add access token to stream URL for Cast receiver authentication
            val token = authRepository.getTokenSync()
            val authenticatedStreamUrl = if (token != null) {
                "$streamUrl?token=$token"
            } else {
                streamUrl
            }

            android.util.Log.d("CastHelper", "Using authenticated URL with token")

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

            android.util.Log.d("CastHelper", "Loading media with position: $currentPosition seconds")

            // Use MediaLoadRequestData (newer API)
            val loadRequestData = MediaLoadRequestData.Builder()
                .setMediaInfo(mediaInfo)
                .setAutoplay(true)
                .setCurrentTime(currentPosition * 1000) // Position in milliseconds
                .build()

            android.util.Log.d("CastHelper", "Loading media from position $currentPosition seconds")

            val loadRequest = remoteMediaClient.load(loadRequestData)

            loadRequest.setResultCallback { result ->
                if (result.status.isSuccess) {
                    android.util.Log.d("CastHelper", "Media loaded successfully")
                    // Update the playing state immediately
                    _isPlaying.value = true
                } else {
                    android.util.Log.e("CastHelper", "Failed to load media: statusCode=${result.status.statusCode}, statusMessage=${result.status.statusMessage}")
                }
            }

            android.util.Log.d("CastHelper", "Load request sent")
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
            _isPlaying.value = true
            android.util.Log.d("CastHelper", "Sent play command to Cast receiver")
        } catch (e: Exception) {
            android.util.Log.e("CastHelper", "Error sending play command", e)
        }
    }

    fun pause() {
        try {
            getCastSession()?.remoteMediaClient?.pause()
            _isPlaying.value = false
            android.util.Log.d("CastHelper", "Sent pause command to Cast receiver")
        } catch (e: Exception) {
            android.util.Log.e("CastHelper", "Error sending pause command", e)
        }
    }

    fun seek(positionSeconds: Long) {
        try {
            getCastSession()?.remoteMediaClient?.seek(positionSeconds * 1000)
            android.util.Log.d("CastHelper", "Sent seek command to Cast receiver: $positionSeconds seconds")
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
