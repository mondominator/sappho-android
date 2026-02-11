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
        val device = connectedDevice ?: return
        val baseUrl = "http://${device.host}:${device.port}"

        try {
            // AirPlay 1 /play endpoint expects a text/parameters body
            val startPosition = if (mediaDuration > 0) {
                positionSeconds.toFloat() / mediaDuration.toFloat()
            } else {
                0f
            }

            val body = "Content-Location: $streamUrl\nStart-Position: $startPosition\n"

            val request = Request.Builder()
                .url("$baseUrl/play")
                .post(body.toRequestBody("text/parameters".toMediaType()))
                .build()

            val response = httpClient.newCall(request).execute()
            Log.d(TAG, "AirPlay /play: ${response.code}")
            response.close()

            _isPlaying.value = true
            _currentPosition.value = positionSeconds
        } catch (e: Exception) {
            Log.e(TAG, "Error loading media on AirPlay", e)
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
