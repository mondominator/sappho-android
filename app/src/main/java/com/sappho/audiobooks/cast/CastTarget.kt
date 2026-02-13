package com.sappho.audiobooks.cast

import kotlinx.coroutines.flow.StateFlow

/**
 * Protocol-agnostic interface for casting audio to external devices.
 * Each casting protocol (Chromecast, Roku, Kodi, AirPlay) implements this interface.
 */
interface CastTarget {
    val protocol: CastProtocol
    val isConnected: StateFlow<Boolean>
    val isPlaying: StateFlow<Boolean>
    val currentPosition: StateFlow<Long>  // seconds
    val connectedDeviceName: StateFlow<String?>
    val lastError: StateFlow<String?>

    suspend fun connect(device: CastDevice)
    suspend fun disconnect()
    suspend fun loadMedia(
        streamUrl: String,
        title: String,
        author: String?,
        coverUrl: String?,
        positionSeconds: Long
    )
    suspend fun play()
    suspend fun pause()
    suspend fun seek(positionSeconds: Long)
    suspend fun stop()
}

enum class CastProtocol {
    CHROMECAST,
    ROKU,
    KODI,
    AIRPLAY
}

enum class CastDeviceType {
    UNKNOWN,
    TV,
    SPEAKER
}

data class CastDevice(
    val id: String,
    val name: String,
    val protocol: CastProtocol,
    val host: String,
    val port: Int,
    val deviceType: CastDeviceType = CastDeviceType.UNKNOWN,
    val extras: Any? = null  // Protocol-specific data (e.g., MediaRouter.RouteInfo for Chromecast)
)
