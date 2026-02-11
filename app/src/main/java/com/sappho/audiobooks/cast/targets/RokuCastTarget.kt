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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder

/**
 * Roku casting via External Control Protocol (ECP).
 * Uses PlayOnRoku app (app ID 15985) for audio playback on Roku OS 11.5+.
 *
 * ECP reference: https://developer.roku.com/docs/developer-program/dev-tools/external-control-api.md
 */
class RokuCastTarget(
    private val httpClient: OkHttpClient
) : CastTarget {

    companion object {
        private const val TAG = "RokuCastTarget"
        private const val PLAY_ON_ROKU_APP_ID = "15985"
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
        val device = connectedDevice ?: return
        val baseUrl = "http://${device.host}:${device.port}"

        try {
            val encodedUrl = URLEncoder.encode(streamUrl, "UTF-8")
            val encodedTitle = URLEncoder.encode(title, "UTF-8")
            // Launch PlayOnRoku with the stream URL
            val launchUrl = "$baseUrl/launch/$PLAY_ON_ROKU_APP_ID" +
                    "?url=$encodedUrl" +
                    "&mediaType=audio" +
                    "&t=$positionSeconds" +
                    "&songname=$encodedTitle"

            val request = Request.Builder()
                .url(launchUrl)
                .post("".toRequestBody("text/plain".toMediaType()))
                .build()

            val response = httpClient.newCall(request).execute()
            Log.d(TAG, "Launch PlayOnRoku: ${response.code}")
            response.close()

            _isPlaying.value = true
            _currentPosition.value = positionSeconds
        } catch (e: Exception) {
            Log.e(TAG, "Error loading media on Roku", e)
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
