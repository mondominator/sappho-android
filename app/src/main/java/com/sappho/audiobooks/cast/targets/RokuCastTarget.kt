package com.sappho.audiobooks.cast.targets

import android.util.Log
import com.sappho.audiobooks.cast.CastDevice
import com.sappho.audiobooks.cast.CastProtocol
import com.sappho.audiobooks.cast.CastTarget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder

/**
 * Roku casting via External Control Protocol (ECP).
 * Uses the "Media Assistant" channel (782875) for audio playback. PlayOnRoku (15985) was
 * locked down by Roku in OS 11.5+, so Media Assistant is the modern replacement.
 *
 * ECP reference: https://developer.roku.com/docs/developer-program/dev-tools/external-control-api.md
 * Media Assistant: https://github.com/MedievalApple/Media-Assistant
 */
class RokuCastTarget(
    private val httpClient: OkHttpClient
) : CastTarget {

    companion object {
        private const val TAG = "RokuCastTarget"
        private const val MEDIA_ASSISTANT_APP_ID = "782875"
        private const val POLL_INTERVAL_MS = 1000L
    }

    override val protocol = CastProtocol.ROKU

    private val _isConnected = MutableStateFlow(false)
    override val isConnected: StateFlow<Boolean> = _isConnected

    private val _isPlaying = MutableStateFlow(false)
    override val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _currentPosition = MutableStateFlow(0L)
    override val currentPosition: StateFlow<Long> = _currentPosition

    private val _connectedDeviceName = MutableStateFlow<String?>(null)
    override val connectedDeviceName: StateFlow<String?> = _connectedDeviceName

    private val _lastError = MutableStateFlow<String?>(null)
    override val lastError: StateFlow<String?> = _lastError

    private var connectedDevice: CastDevice? = null
    private var pollingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    override suspend fun connect(device: CastDevice) {
        connectedDevice = device
        _connectedDeviceName.value = device.name
        _isConnected.value = true
        startPolling()
        Log.d(TAG, "Connected to Roku: ${device.name} (${device.host}:${device.port})")
    }

    override suspend fun disconnect() {
        stopPolling()
        connectedDevice = null
        _isConnected.value = false
        _connectedDeviceName.value = null
        _isPlaying.value = false
        _currentPosition.value = 0L
        Log.d(TAG, "Disconnected from Roku")
    }

    override suspend fun loadMedia(
        streamUrl: String,
        title: String,
        author: String?,
        coverUrl: String?,
        positionSeconds: Long
    ) {
        _lastError.value = null
        withContext(Dispatchers.IO) {
            val device = connectedDevice ?: run {
                Log.e(TAG, "loadMedia: No connected device")
                return@withContext
            }
            val baseUrl = "http://${device.host}:${device.port}"

            Log.d(TAG, "loadMedia: streamUrl=$streamUrl, title=$title, position=$positionSeconds")

            try {
                // Use Media Assistant channel (782875) via ECP /launch deep link.
                // Media Assistant is the modern replacement for PlayOnRoku (locked down in OS 11.5+).
                // Format: /launch/782875?u=<url>&t=a&songName=<title>&artistName=<author>&albumArt=<cover>
                val encodedUrl = URLEncoder.encode(streamUrl, "UTF-8")
                val encodedTitle = URLEncoder.encode(title, "UTF-8")
                val encodedAuthor = URLEncoder.encode(author ?: "", "UTF-8")
                val encodedCover = if (coverUrl != null) URLEncoder.encode(coverUrl, "UTF-8") else ""

                val launchUrl = buildString {
                    append("$baseUrl/launch/$MEDIA_ASSISTANT_APP_ID")
                    append("?t=a")
                    append("&u=$encodedUrl")
                    append("&songName=$encodedTitle")
                    append("&artistName=$encodedAuthor")
                    if (encodedCover.isNotEmpty()) append("&albumArt=$encodedCover")
                }

                Log.d(TAG, "Media Assistant launch: $launchUrl")

                val request = Request.Builder()
                    .url(launchUrl)
                    .post("".toRequestBody("text/plain".toMediaType()))
                    .build()

                val response = httpClient.newCall(request).execute()
                val responseCode = response.code
                val responseBody = response.body?.string() ?: ""
                response.close()

                Log.d(TAG, "Media Assistant response: code=$responseCode, body=$responseBody")

                when {
                    responseCode in 200..204 -> {
                        // Wait for channel to load and start playback
                        delay(3000)

                        val mediaState = queryMediaPlayer(baseUrl)
                        Log.d(TAG, "Media state after launch: $mediaState")

                        if (mediaState?.contains("state=\"play\"") == true ||
                            mediaState?.contains("state=\"buffer\"") == true) {
                            _isPlaying.value = true
                            _currentPosition.value = positionSeconds
                            Log.d(TAG, "Roku playback started successfully")
                        } else {
                            // Channel launched but may still be loading — mark optimistically
                            Log.w(TAG, "Media Assistant launched, state: $mediaState")
                            _isPlaying.value = true
                            _currentPosition.value = positionSeconds
                        }
                    }
                    responseCode == 404 -> {
                        Log.e(TAG, "Media Assistant channel not installed (404)")
                        _lastError.value = "Roku casting requires the free \"Media Assistant\" " +
                                "channel. Install it from the Roku Channel Store, then try again."
                        _isPlaying.value = false
                    }
                    else -> {
                        Log.e(TAG, "Media Assistant launch failed: HTTP $responseCode")
                        _lastError.value = "Roku could not play the audio (HTTP $responseCode)."
                        _isPlaying.value = false
                    }
                }
            } catch (e: java.net.ConnectException) {
                Log.e(TAG, "Cannot reach Roku device", e)
                _lastError.value = "Cannot reach Roku device. Make sure it's powered on."
                _isPlaying.value = false
            } catch (e: Exception) {
                Log.e(TAG, "Error loading media on Roku", e)
                _lastError.value = "Failed to cast to Roku: ${e.message}"
                _isPlaying.value = false
            }
        }
    }

    private fun queryMediaPlayer(baseUrl: String): String? {
        return try {
            val request = Request.Builder()
                .url("$baseUrl/query/media-player")
                .get()
                .build()
            val response = httpClient.newCall(request).execute()
            val body = response.body?.string()
            response.close()
            body
        } catch (e: Exception) {
            Log.e(TAG, "Error querying media player state", e)
            null
        }
    }

    override suspend fun play() {
        sendKeypress("Play")
    }

    override suspend fun pause() {
        sendKeypress("Play") // Play/Pause toggle on Roku
    }

    override suspend fun seek(positionSeconds: Long) {
        // Roku ECP doesn't have direct seek — restart media at position
        // This is a limitation of the PlayOnRoku app
        Log.w(TAG, "Roku ECP does not support direct seek. Position: $positionSeconds")
    }

    override suspend fun stop() {
        val device = connectedDevice ?: return
        val baseUrl = "http://${device.host}:${device.port}"
        try {
            val request = Request.Builder()
                .url("$baseUrl/keypress/Home")
                .post("".toRequestBody("text/plain".toMediaType()))
                .build()
            httpClient.newCall(request).execute().close()
            _isPlaying.value = false
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping Roku playback", e)
        }
    }

    private fun sendKeypress(key: String) {
        val device = connectedDevice ?: return
        val baseUrl = "http://${device.host}:${device.port}"
        scope.launch {
            try {
                val request = Request.Builder()
                    .url("$baseUrl/keypress/$key")
                    .post("".toRequestBody("text/plain".toMediaType()))
                    .build()
                httpClient.newCall(request).execute().close()
            } catch (e: Exception) {
                Log.e(TAG, "Error sending keypress '$key'", e)
            }
        }
    }

    private fun startPolling() {
        pollingJob?.cancel()
        pollingJob = scope.launch {
            while (isActive) {
                pollMediaStatus()
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    private fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    private fun pollMediaStatus() {
        val device = connectedDevice ?: return
        val baseUrl = "http://${device.host}:${device.port}"
        try {
            val request = Request.Builder()
                .url("$baseUrl/query/media-player")
                .get()
                .build()
            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: ""
            response.close()

            // Parse XML response for state and position
            // Example: <player error="false" state="play"><position>12345 ms</position>...</player>
            val stateMatch = Regex("state=\"(\\w+)\"").find(body)
            val positionMatch = Regex("<position>(\\d+) ms</position>").find(body)

            val state = stateMatch?.groupValues?.get(1)
            val positionMs = positionMatch?.groupValues?.get(1)?.toLongOrNull() ?: 0L

            _isPlaying.value = state == "play"
            _currentPosition.value = positionMs / 1000
        } catch (e: Exception) {
            // Polling failure is expected when device goes offline
            Log.v(TAG, "Polling error: ${e.message}")
        }
    }
}
