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

/**
 * AirPlay 1 casting via HTTP protocol on port 7000.
 * Marked as experimental — AirPlay 2 devices with encryption may not work.
 *
 * Protocol reference: https://nto.github.io/AirPlay.html
 */
class AirPlayCastTarget(
    private val httpClient: OkHttpClient
) : CastTarget {

    companion object {
        private const val TAG = "AirPlayCastTarget"
        private const val POLL_INTERVAL_MS = 1000L
    }

    override val protocol = CastProtocol.AIRPLAY

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
    private var mediaDuration: Long = 0L

    override suspend fun connect(device: CastDevice) {
        connectedDevice = device
        _connectedDeviceName.value = device.name
        _isConnected.value = true
        startPolling()
        Log.d(TAG, "Connected to AirPlay: ${device.name} (${device.host}:${device.port})")
    }

    override suspend fun disconnect() {
        val device = connectedDevice
        if (device != null) {
            try {
                val request = Request.Builder()
                    .url("http://${device.host}:${device.port}/stop")
                    .post("".toRequestBody("text/plain".toMediaType()))
                    .build()
                httpClient.newCall(request).execute().close()
            } catch (e: Exception) {
                Log.e(TAG, "Error sending stop on disconnect", e)
            }
        }
        stopPolling()
        connectedDevice = null
        _isConnected.value = false
        _connectedDeviceName.value = null
        _isPlaying.value = false
        _currentPosition.value = 0L
        mediaDuration = 0L
        Log.d(TAG, "Disconnected from AirPlay")
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
                // AirPlay 1 /play endpoint expects a text/parameters body
                // Start-Position is a ratio (0.0 to 1.0), but on first play we don't know duration
                val startPosition = if (mediaDuration > 0) {
                    positionSeconds.toFloat() / mediaDuration.toFloat()
                } else {
                    0f
                }

                val body = "Content-Location: $streamUrl\nStart-Position: $startPosition\n"

                Log.d(TAG, "Sending /play to $baseUrl with Start-Position=$startPosition")

                val request = Request.Builder()
                    .url("$baseUrl/play")
                    .post(body.toRequestBody("text/parameters".toMediaType()))
                    .build()

                val response = httpClient.newCall(request).execute()
                val responseBody = response.body?.string() ?: ""
                val responseCode = response.code
                Log.d(TAG, "AirPlay /play response: code=$responseCode, body=$responseBody")
                response.close()

                if (responseCode in 200..299) {
                    // Wait briefly and verify playback started
                    delay(2000)

                    val playbackInfo = queryPlaybackInfo(baseUrl)
                    if (playbackInfo != null) {
                        Log.d(TAG, "AirPlay playback info: $playbackInfo")
                        _isPlaying.value = true
                        _currentPosition.value = positionSeconds
                    } else {
                        Log.w(TAG, "AirPlay /play accepted but no playback info returned. " +
                                "Device may use AirPlay 2 (encrypted) which is not supported.")
                        // Still mark as attempting — polling may pick it up
                        _isPlaying.value = true
                        _currentPosition.value = positionSeconds
                    }
                } else {
                    Log.e(TAG, "AirPlay /play failed with HTTP $responseCode")
                    _lastError.value = "This device may require AirPlay 2 (encrypted) " +
                            "which is not yet supported."
                    _isPlaying.value = false
                }
            } catch (e: java.net.ConnectException) {
                Log.e(TAG, "Cannot reach AirPlay device at $baseUrl", e)
                _lastError.value = "Cannot reach AirPlay device. " +
                        "It may require AirPlay 2 (encrypted) which is not yet supported."
                _isPlaying.value = false
            } catch (e: Exception) {
                Log.e(TAG, "Error loading media on AirPlay", e)
                _lastError.value = "Failed to cast via AirPlay: ${e.message}"
                _isPlaying.value = false
            }
        }
    }

    private fun queryPlaybackInfo(baseUrl: String): String? {
        return try {
            val request = Request.Builder()
                .url("$baseUrl/scrub")
                .get()
                .build()
            val response = httpClient.newCall(request).execute()
            val body = response.body?.string()
            response.close()
            if (response.code == 200 && body?.contains("duration") == true) body else null
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun play() {
        val device = connectedDevice ?: return
        try {
            val request = Request.Builder()
                .url("http://${device.host}:${device.port}/rate?value=1.0")
                .post("".toRequestBody("text/plain".toMediaType()))
                .build()
            httpClient.newCall(request).execute().close()
        } catch (e: Exception) {
            Log.e(TAG, "Error sending play", e)
        }
    }

    override suspend fun pause() {
        val device = connectedDevice ?: return
        try {
            val request = Request.Builder()
                .url("http://${device.host}:${device.port}/rate?value=0.0")
                .post("".toRequestBody("text/plain".toMediaType()))
                .build()
            httpClient.newCall(request).execute().close()
        } catch (e: Exception) {
            Log.e(TAG, "Error sending pause", e)
        }
    }

    override suspend fun seek(positionSeconds: Long) {
        val device = connectedDevice ?: return
        try {
            val request = Request.Builder()
                .url("http://${device.host}:${device.port}/scrub?position=$positionSeconds")
                .post("".toRequestBody("text/plain".toMediaType()))
                .build()
            httpClient.newCall(request).execute().close()
            _currentPosition.value = positionSeconds
        } catch (e: Exception) {
            Log.e(TAG, "Error seeking on AirPlay", e)
        }
    }

    override suspend fun stop() {
        val device = connectedDevice ?: return
        try {
            val request = Request.Builder()
                .url("http://${device.host}:${device.port}/stop")
                .post("".toRequestBody("text/plain".toMediaType()))
                .build()
            httpClient.newCall(request).execute().close()
            _isPlaying.value = false
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping AirPlay", e)
        }
    }

    private fun startPolling() {
        pollingJob?.cancel()
        pollingJob = scope.launch {
            while (isActive) {
                pollPlaybackInfo()
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    private fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    private fun pollPlaybackInfo() {
        val device = connectedDevice ?: return
        try {
            val request = Request.Builder()
                .url("http://${device.host}:${device.port}/scrub")
                .get()
                .build()
            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: ""
            response.close()

            // Parse response: "duration: 12345.67\nposition: 89.01\n"
            val durationMatch = Regex("duration:\\s*([\\d.]+)").find(body)
            val positionMatch = Regex("position:\\s*([\\d.]+)").find(body)

            val duration = durationMatch?.groupValues?.get(1)?.toDoubleOrNull()
            val position = positionMatch?.groupValues?.get(1)?.toDoubleOrNull()

            if (duration != null) {
                mediaDuration = duration.toLong()
            }
            if (position != null) {
                _currentPosition.value = position.toLong()
                _isPlaying.value = true
            }
        } catch (e: Exception) {
            // Expected when no media is playing
            Log.v(TAG, "Polling error: ${e.message}")
        }
    }
}
