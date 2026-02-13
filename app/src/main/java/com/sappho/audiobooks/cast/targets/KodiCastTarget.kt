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
import org.json.JSONObject

/**
 * Kodi/Fire TV casting via JSON-RPC over HTTP.
 * Default Kodi web server port is 8080.
 *
 * JSON-RPC reference: https://kodi.wiki/view/JSON-RPC_API
 */
class KodiCastTarget(
    private val httpClient: OkHttpClient
) : CastTarget {

    companion object {
        private const val TAG = "KodiCastTarget"
        private const val POLL_INTERVAL_MS = 1000L
    }

    override val protocol = CastProtocol.KODI

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
    private var activePlayerId: Int? = null

    override suspend fun connect(device: CastDevice) {
        withContext(Dispatchers.IO) {
            // Verify connectivity by pinging Kodi's JSON-RPC
            val baseUrl = "http://${device.host}:${device.port}"
            try {
                val result = sendJsonRpc(baseUrl, "JSONRPC.Ping", JSONObject())
                if (result != null) {
                    connectedDevice = device
                    _connectedDeviceName.value = device.name
                    _isConnected.value = true
                    startPolling()
                    Log.d(TAG, "Connected to Kodi: ${device.name} (${device.host}:${device.port})")
                } else {
                    Log.e(TAG, "Failed to ping Kodi at $baseUrl")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error connecting to Kodi", e)
            }
        }
    }

    override suspend fun disconnect() {
        stopPolling()
        connectedDevice = null
        activePlayerId = null
        _isConnected.value = false
        _connectedDeviceName.value = null
        _isPlaying.value = false
        _currentPosition.value = 0L
        Log.d(TAG, "Disconnected from Kodi")
    }

    override suspend fun loadMedia(
        streamUrl: String,
        title: String,
        author: String?,
        coverUrl: String?,
        positionSeconds: Long
    ) {
        withContext(Dispatchers.IO) {
            _lastError.value = null
            val device = connectedDevice ?: run {
                Log.e(TAG, "loadMedia: No connected device")
                return@withContext
            }
            val baseUrl = "http://${device.host}:${device.port}"

            Log.d(TAG, "loadMedia: streamUrl=$streamUrl, title=$title, position=$positionSeconds")

            try {
                // Use Player.Open to start playback
                val params = JSONObject().apply {
                    put("item", JSONObject().apply {
                        put("file", streamUrl)
                    })
                }

                val result = sendJsonRpc(baseUrl, "Player.Open", params)
                Log.d(TAG, "Player.Open result: $result")

                // Check for JSON-RPC errors
                val error = result?.optJSONObject("error")
                if (error != null) {
                    Log.e(TAG, "Kodi Player.Open error: code=${error.optInt("code")}, " +
                            "message=${error.optString("message")}. " +
                            "Kodi may not be able to reach the stream URL.")
                    _lastError.value = "Kodi could not play the audio: ${error.optString("message")}"
                    _isPlaying.value = false
                    return@withContext
                }

                // Wait for player to initialize
                delay(2000)

                // Find the active player ID
                activePlayerId = getActivePlayerId(baseUrl)

                if (activePlayerId != null) {
                    Log.d(TAG, "Kodi player active with ID $activePlayerId")
                    // Seek to position if needed
                    if (positionSeconds > 0) {
                        seek(positionSeconds)
                    }
                    _isPlaying.value = true
                    _currentPosition.value = positionSeconds
                } else {
                    Log.e(TAG, "Kodi Player.Open succeeded but no active player found")
                    _lastError.value = "Kodi could not play the audio. " +
                            "The media URL may be unreachable from the Kodi device."
                    _isPlaying.value = false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading media on Kodi", e)
                _lastError.value = "Failed to cast to Kodi: ${e.message}"
                _isPlaying.value = false
            }
        }
    }

    override suspend fun play() {
        val device = connectedDevice ?: return
        val baseUrl = "http://${device.host}:${device.port}"
        val playerId = activePlayerId ?: getActivePlayerId(baseUrl) ?: return

        try {
            val params = JSONObject().apply {
                put("playerid", playerId)
            }
            sendJsonRpc(baseUrl, "Player.PlayPause", params)
        } catch (e: Exception) {
            Log.e(TAG, "Error toggling play/pause on Kodi", e)
        }
    }

    override suspend fun pause() {
        play() // PlayPause is a toggle
    }

    override suspend fun seek(positionSeconds: Long) {
        val device = connectedDevice ?: return
        val baseUrl = "http://${device.host}:${device.port}"
        val playerId = activePlayerId ?: getActivePlayerId(baseUrl) ?: return

        try {
            val hours = (positionSeconds / 3600).toInt()
            val minutes = ((positionSeconds % 3600) / 60).toInt()
            val seconds = (positionSeconds % 60).toInt()

            val params = JSONObject().apply {
                put("playerid", playerId)
                put("value", JSONObject().apply {
                    put("time", JSONObject().apply {
                        put("hours", hours)
                        put("minutes", minutes)
                        put("seconds", seconds)
                        put("milliseconds", 0)
                    })
                })
            }
            sendJsonRpc(baseUrl, "Player.Seek", params)
            _currentPosition.value = positionSeconds
        } catch (e: Exception) {
            Log.e(TAG, "Error seeking on Kodi", e)
        }
    }

    override suspend fun stop() {
        val device = connectedDevice ?: return
        val baseUrl = "http://${device.host}:${device.port}"
        val playerId = activePlayerId ?: getActivePlayerId(baseUrl) ?: return

        try {
            val params = JSONObject().apply {
                put("playerid", playerId)
            }
            sendJsonRpc(baseUrl, "Player.Stop", params)
            _isPlaying.value = false
            activePlayerId = null
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping Kodi playback", e)
        }
    }

    private fun sendJsonRpc(baseUrl: String, method: String, params: JSONObject): JSONObject? {
        val payload = JSONObject().apply {
            put("jsonrpc", "2.0")
            put("method", method)
            put("params", params)
            put("id", 1)
        }

        val request = Request.Builder()
            .url("$baseUrl/jsonrpc")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()

        return try {
            val response = httpClient.newCall(request).execute()
            val body = response.body?.string()
            response.close()
            body?.let { JSONObject(it) }
        } catch (e: Exception) {
            Log.e(TAG, "JSON-RPC error for $method", e)
            null
        }
    }

    private fun getActivePlayerId(baseUrl: String): Int? {
        val result = sendJsonRpc(baseUrl, "Player.GetActivePlayers", JSONObject())
        val players = result?.optJSONArray("result")
        if (players != null && players.length() > 0) {
            return players.getJSONObject(0).optInt("playerid", -1).takeIf { it >= 0 }
        }
        return null
    }

    private fun startPolling() {
        pollingJob?.cancel()
        pollingJob = scope.launch {
            while (isActive) {
                pollPlayerStatus()
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    private fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    private fun pollPlayerStatus() {
        val device = connectedDevice ?: return
        val baseUrl = "http://${device.host}:${device.port}"

        try {
            // Get active player
            val playerId = getActivePlayerId(baseUrl)
            if (playerId == null) {
                _isPlaying.value = false
                activePlayerId = null
                return
            }
            activePlayerId = playerId

            // Get player properties
            val params = JSONObject().apply {
                put("playerid", playerId)
                put("properties", org.json.JSONArray().apply {
                    put("time")
                    put("speed")
                })
            }
            val result = sendJsonRpc(baseUrl, "Player.GetProperties", params)
            val props = result?.optJSONObject("result")
            if (props != null) {
                val speed = props.optInt("speed", 0)
                _isPlaying.value = speed > 0

                val time = props.optJSONObject("time")
                if (time != null) {
                    val hours = time.optLong("hours", 0)
                    val minutes = time.optLong("minutes", 0)
                    val seconds = time.optLong("seconds", 0)
                    _currentPosition.value = hours * 3600 + minutes * 60 + seconds
                }
            }
        } catch (e: Exception) {
            Log.v(TAG, "Polling error: ${e.message}")
        }
    }
}
