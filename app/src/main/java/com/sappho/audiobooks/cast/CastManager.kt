package com.sappho.audiobooks.cast

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import androidx.mediarouter.media.MediaRouter
import com.sappho.audiobooks.cast.discovery.MdnsDiscovery
import com.sappho.audiobooks.cast.discovery.SsdpDiscovery
import com.sappho.audiobooks.cast.targets.AirPlayCastTarget
import com.sappho.audiobooks.cast.targets.ChromecastTarget
import com.sappho.audiobooks.cast.targets.KodiCastTarget
import com.sappho.audiobooks.data.remote.ProgressUpdateRequest
import com.sappho.audiobooks.data.remote.SapphoApi
import com.sappho.audiobooks.data.repository.AuthRepository
import com.sappho.audiobooks.domain.model.Audiobook
import com.sappho.audiobooks.download.DownloadManager
import com.sappho.audiobooks.presentation.theme.Timing
import com.sappho.audiobooks.service.PlayerState
import com.sappho.audiobooks.sync.ProgressPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orchestrates casting across all supported protocols (Chromecast, Kodi, AirPlay).
 * Manages device discovery, delegates playback to the active CastTarget, exposes
 * unified StateFlows for the UI layer, and reports listening progress to the
 * server while casting (the local player is idle, so nothing else does).
 */
@Singleton
class CastManager @Inject constructor(
    private val authRepository: AuthRepository,
    private val castHelper: CastHelper,
    private val api: SapphoApi,
    private val playerState: PlayerState,
    private val downloadManager: DownloadManager
) {
    companion object {
        private const val TAG = "CastManager"
        private const val KODI_SEARCH_TARGET = "urn:schemas-upnp-org:device:MediaRenderer:1"
        private const val AIRPLAY_SERVICE_TYPE = "_airplay._tcp."
    }

    // Local network HTTP client (no auth interceptor)
    private val localHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    // Protocol targets
    private val chromecastTarget = ChromecastTarget(castHelper)
    private val kodiTarget = KodiCastTarget(localHttpClient)
    private val airPlayTarget = AirPlayCastTarget(localHttpClient)

    // Discovery utilities
    private val ssdpDiscovery = SsdpDiscovery()

    // Active target
    private var activeTarget: CastTarget? = null

    // Discovered devices from all protocols
    private val _discoveredDevices = MutableStateFlow<List<CastDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<CastDevice>> = _discoveredDevices

    // Unified state (delegates to active target or defaults)
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition

    private val _connectedDeviceName = MutableStateFlow<String?>(null)
    val connectedDeviceName: StateFlow<String?> = _connectedDeviceName

    private val _activeProtocol = MutableStateFlow<CastProtocol?>(null)
    val activeProtocol: StateFlow<CastProtocol?> = _activeProtocol

    private val _castError = MutableStateFlow<String?>(null)
    val castError: StateFlow<String?> = _castError

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var discoveryJob: Job? = null
    private var stateObserverJob: Job? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    private var progressJob: Job? = null
    private var lastReportedPosition: Long = -1
    // Last position seen while connected, handed back to the local player on disconnect.
    private var lastCastPosition: Long = 0

    init {
        observeCastProgress()

        // Observe Chromecast connection changes from CastHelper (it connects externally via route selection)
        scope.launch {
            castHelper.isConnected.collect { connected ->
                if (connected && activeTarget == null) {
                    // Chromecast connected externally (e.g., through route selection)
                    activeTarget = chromecastTarget
                    _activeProtocol.value = CastProtocol.CHROMECAST
                    observeActiveTargetState()
                } else if (!connected && activeTarget == chromecastTarget) {
                    onCastSessionEnded()
                    activeTarget = null
                    _activeProtocol.value = null
                    _isConnected.value = false
                    _isPlaying.value = false
                    _connectedDeviceName.value = null
                }
            }
        }
    }

    fun initialize(context: Context) {
        castHelper.initialize(context)
    }

    /**
     * Start discovering devices across all supported protocols.
     */
    fun startDiscovery(context: Context) {
        stopDiscovery()

        // Acquire multicast lock for SSDP/mDNS
        acquireMulticastLock(context)

        // Start Chromecast discovery
        chromecastTarget.startDiscovery(context)

        discoveryJob = scope.launch {
            // Collect Chromecast routes as devices
            launch {
                chromecastTarget.getAvailableRoutes().collect { routes ->
                    val chromecastDevices = routes.map { route ->
                        val type = when (route.deviceType) {
                            MediaRouter.RouteInfo.DEVICE_TYPE_TV -> CastDeviceType.TV
                            MediaRouter.RouteInfo.DEVICE_TYPE_SPEAKER -> CastDeviceType.SPEAKER
                            else -> CastDeviceType.UNKNOWN
                        }
                        CastDevice(
                            id = "chromecast_${route.id}",
                            name = route.name,
                            protocol = CastProtocol.CHROMECAST,
                            host = "",
                            port = 0,
                            deviceType = type,
                            extras = route
                        )
                    }
                    updateDeviceList(CastProtocol.CHROMECAST, chromecastDevices)
                }
            }

            // Discover Kodi devices via SSDP
            // Note: The UPnP MediaRenderer search target is generic — many non-Kodi devices
            // respond to it (smart TVs, DLNA speakers, etc.). We verify each candidate by
            // pinging Kodi's JSON-RPC endpoint before adding it to the list.
            launch(Dispatchers.IO) {
                try {
                    ssdpDiscovery.discover(KODI_SEARCH_TARGET, timeoutMs = 5000).collect { ssdpDevice ->
                        val verified = verifyKodiDevice(ssdpDevice.host, 8080)
                        if (verified) {
                            val kodiDevice = CastDevice(
                                id = "kodi_${ssdpDevice.host}",
                                name = ssdpDevice.friendlyName ?: "Kodi (${ssdpDevice.host})",
                                protocol = CastProtocol.KODI,
                                host = ssdpDevice.host,
                                port = 8080
                            )
                            addDiscoveredDevice(kodiDevice)
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Kodi discovery error", e)
                }
            }

            // Discover AirPlay devices via mDNS
            launch(Dispatchers.IO) {
                try {
                    val mdnsDiscovery = MdnsDiscovery(context)
                    mdnsDiscovery.discover(AIRPLAY_SERVICE_TYPE).collect { mdnsDevice ->
                        val airPlayDevice = CastDevice(
                            id = "airplay_${mdnsDevice.host}",
                            name = mdnsDevice.name,
                            protocol = CastProtocol.AIRPLAY,
                            host = mdnsDevice.host,
                            port = mdnsDevice.port
                        )
                        addDiscoveredDevice(airPlayDevice)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "AirPlay discovery error", e)
                }
            }
        }
    }

    /**
     * Stop all device discovery.
     */
    fun stopDiscovery() {
        discoveryJob?.cancel()
        discoveryJob = null
        chromecastTarget.stopDiscovery()
        releaseMulticastLock()
    }

    /**
     * Connect to a discovered device.
     */
    suspend fun connectToDevice(device: CastDevice) {
        // Disconnect from current target if different protocol
        if (activeTarget != null && _activeProtocol.value != device.protocol) {
            disconnect()
        }

        val target = when (device.protocol) {
            CastProtocol.CHROMECAST -> {
                // For Chromecast, use route selection (connection happens async via Cast SDK)
                val route = device.extras as? MediaRouter.RouteInfo
                if (route != null) {
                    // The actual connection will be handled by CastHelper's session listener
                    // which triggers the init block's observer above
                    return
                }
                return
            }
            CastProtocol.KODI -> kodiTarget
            CastProtocol.AIRPLAY -> airPlayTarget
        }

        target.connect(device)
        activeTarget = target
        _activeProtocol.value = device.protocol
        observeActiveTargetState()
    }

    /**
     * Disconnect from the current casting device.
     */
    suspend fun disconnect() {
        onCastSessionEnded()
        activeTarget?.disconnect()
        activeTarget = null
        _activeProtocol.value = null
        _isConnected.value = false
        _isPlaying.value = false
        _currentPosition.value = 0L
        _connectedDeviceName.value = null
        _castError.value = null
        stateObserverJob?.cancel()
    }

    /**
     * Cast an audiobook to the active device.
     */
    suspend fun castAudiobook(
        audiobook: Audiobook,
        streamUrl: String,
        coverUrl: String?,
        positionSeconds: Long
    ) {
        val target = activeTarget ?: run {
            Log.e(TAG, "castAudiobook: No active target")
            return
        }
        val protocol = _activeProtocol.value ?: run {
            Log.e(TAG, "castAudiobook: No active protocol")
            return
        }

        Log.d(TAG, "castAudiobook: protocol=$protocol, title=${audiobook.title}, " +
                "streamUrl=$streamUrl, position=$positionSeconds")

        // For Chromecast, use the existing CastHelper flow which handles auth tokens
        if (protocol == CastProtocol.CHROMECAST) {
            chromecastTarget.castAudiobook(audiobook, streamUrl, coverUrl, positionSeconds)
            return
        }

        // SECURITY NOTE: Auth token must be passed as a URL query parameter because all
        // cast protocols (AirPlay, Kodi JSON-RPC) work by instructing a remote
        // device to fetch a URL. The remote device's HTTP client cannot be configured with
        // custom headers from the sender app. This is an inherent limitation of these
        // protocols, not a design choice. The risk is mitigated by:
        //   1. Cast targets are local network devices (not internet-facing)
        //   2. The Sappho server should treat token query params as equivalent to headers
        //   3. Stream URLs are ephemeral and not persisted by cast receivers
        val token = authRepository.getTokenSync()
        val authenticatedUrl = if (token != null) "$streamUrl?token=$token" else streamUrl
        val authenticatedCoverUrl = if (coverUrl != null && token != null) {
            "$coverUrl?token=$token"
        } else {
            coverUrl
        }

        Log.d(TAG, "castAudiobook: Sending to $protocol target, " +
                "hasToken=${token != null}")

        target.loadMedia(
            streamUrl = authenticatedUrl,
            title = audiobook.title,
            author = audiobook.author,
            coverUrl = authenticatedCoverUrl,
            positionSeconds = positionSeconds
        )
    }

    suspend fun play() {
        activeTarget?.play()
    }

    suspend fun pause() {
        activeTarget?.pause()
    }

    suspend fun seek(positionSeconds: Long) {
        activeTarget?.seek(positionSeconds)
    }

    suspend fun stop() {
        activeTarget?.stop()
    }

    /** Skip relative to the receiver's current position, clamped to the book. */
    suspend fun skipBy(deltaSeconds: Long) {
        val duration = playerState.currentAudiobook.value?.duration?.toLong() ?: 0L
        val target = ProgressPolicy.clampSeekSeconds(_currentPosition.value + deltaSeconds, duration)
        seek(target)
    }

    fun isCasting(): Boolean {
        return activeTarget != null && _isConnected.value
    }

    fun getCurrentPosition(): Long {
        return _currentPosition.value
    }

    fun clearError() {
        _castError.value = null
    }

    /**
     * Get the Chromecast target for direct route selection in the UI.
     * This is needed because Chromecast connection is initiated by selecting a MediaRouter route,
     * not through the generic connect() path.
     */
    fun getChromecastTarget(): ChromecastTarget = chromecastTarget

    // -- Progress reporting while casting --

    /**
     * While a receiver plays, the phone's player is idle, so the service's
     * sync loop records nothing. Report the receiver's position on the same
     * cadence, plus once on every pause.
     */
    private fun observeCastProgress() {
        scope.launch {
            _isPlaying.collect { playing ->
                progressJob?.cancel()
                if (playing) {
                    progressJob = scope.launch {
                        while (isActive) {
                            delay(Timing.SYNC_INTERVAL_MS)
                            reportCastProgress("playing")
                        }
                    }
                } else if (_isConnected.value) {
                    reportCastProgress("paused")
                }
            }
        }
        scope.launch {
            _currentPosition.collect { pos ->
                if (_isConnected.value && pos > 0) lastCastPosition = pos
            }
        }
    }

    internal suspend fun reportCastProgress(state: String) {
        val book = playerState.currentAudiobook.value ?: return
        val position = _currentPosition.value.takeIf { it > 0 } ?: lastCastPosition
        val duration = book.duration?.toLong() ?: 0L
        if (position <= 0 || ProgressPolicy.isNearEnd(position, duration)) return
        if (state == "playing" && position == lastReportedPosition) return
        lastReportedPosition = position
        playerState.updatePosition(position)
        try {
            val response = api.updateProgress(
                book.id,
                ProgressUpdateRequest(position = position.toInt(), completed = 0, state = state)
            )
            if (response.isSuccessful) {
                downloadManager.clearPendingProgress(book.id)
            } else {
                downloadManager.saveOfflineProgress(book.id, position.toInt())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Cast progress sync failed; queued for retry", e)
            downloadManager.saveOfflineProgress(book.id, position.toInt())
        }
    }

    /**
     * The cast session is ending: record the final position and hand it to the
     * local player so resuming on the phone continues where the TV stopped.
     */
    private fun onCastSessionEnded() {
        progressJob?.cancel()
        val position = lastCastPosition
        if (position > 0 && playerState.currentAudiobook.value != null) {
            playerState.updatePosition(position)
            scope.launch { reportCastProgress("paused") }
            com.sappho.audiobooks.service.AudioPlaybackService.instance?.seekTo(position)
        }
        lastCastPosition = 0
        lastReportedPosition = -1
    }

    // -- Private helpers --

    @Synchronized
    private fun updateDeviceList(protocol: CastProtocol, devices: List<CastDevice>) {
        val current = _discoveredDevices.value.toMutableList()
        current.removeAll { it.protocol == protocol }
        current.addAll(devices)
        _discoveredDevices.value = current
    }

    @Synchronized
    private fun addDiscoveredDevice(device: CastDevice) {
        val current = _discoveredDevices.value.toMutableList()
        // Replace if same ID, add if new
        val index = current.indexOfFirst { it.id == device.id }
        if (index >= 0) {
            current[index] = device
        } else {
            current.add(device)
        }
        _discoveredDevices.value = current
    }

    private fun observeActiveTargetState() {
        stateObserverJob?.cancel()
        val target = activeTarget ?: return

        stateObserverJob = scope.launch {
            launch { target.isConnected.collect { _isConnected.value = it } }
            launch { target.isPlaying.collect { _isPlaying.value = it } }
            launch { target.currentPosition.collect { _currentPosition.value = it } }
            launch { target.connectedDeviceName.collect { _connectedDeviceName.value = it } }
            launch { target.lastError.collect { _castError.value = it } }
        }
    }

    /**
     * Verify that a discovered UPnP MediaRenderer is actually running Kodi
     * by pinging its JSON-RPC endpoint. Many non-Kodi devices (Samsung TVs,
     * DLNA speakers, etc.) respond to the generic MediaRenderer SSDP search.
     */
    private fun verifyKodiDevice(host: String, port: Int): Boolean {
        return try {
            val payload = org.json.JSONObject().apply {
                put("jsonrpc", "2.0")
                put("method", "JSONRPC.Ping")
                put("params", org.json.JSONObject())
                put("id", 1)
            }
            val request = okhttp3.Request.Builder()
                .url("http://$host:$port/jsonrpc")
                .post(
                    okhttp3.RequestBody.create(
                        "application/json".toMediaType(),
                        payload.toString()
                    )
                )
                .build()
            val response = localHttpClient.newCall(request).execute()
            val body = response.body?.string() ?: ""
            response.close()
            // Kodi responds with {"id":1,"jsonrpc":"2.0","result":"pong"}
            val isKodi = body.contains("pong")
            Log.d(TAG, "Kodi verification for $host:$port = $isKodi")
            isKodi
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.v(TAG, "Not a Kodi device at $host:$port: ${e.message}")
            false
        }
    }

    private fun acquireMulticastLock(context: Context) {
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            multicastLock = wifiManager.createMulticastLock("sappho_cast_discovery").apply {
                setReferenceCounted(true)
                acquire()
            }
            Log.d(TAG, "Multicast lock acquired")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire multicast lock", e)
        }
    }

    private fun releaseMulticastLock() {
        try {
            multicastLock?.let {
                if (it.isHeld) {
                    it.release()
                    Log.d(TAG, "Multicast lock released")
                }
            }
            multicastLock = null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing multicast lock", e)
        }
    }
}
