package com.sappho.audiobooks.cast.targets

import android.content.Context
import androidx.mediarouter.media.MediaRouter
import com.sappho.audiobooks.cast.CastDevice
import com.sappho.audiobooks.cast.CastHelper
import com.sappho.audiobooks.cast.CastProtocol
import com.sappho.audiobooks.cast.CastTarget
import com.sappho.audiobooks.domain.model.Audiobook
import kotlinx.coroutines.flow.StateFlow

/**
 * Chromecast implementation that wraps the existing CastHelper.
 * This is a thin adapter - all actual Chromecast logic remains in CastHelper.
 */
class ChromecastTarget(
    private val castHelper: CastHelper
) : CastTarget {

    override val protocol = CastProtocol.CHROMECAST
    override val isConnected: StateFlow<Boolean> = castHelper.isConnected
    override val isPlaying: StateFlow<Boolean> = castHelper.isPlayingFlow
    override val currentPosition: StateFlow<Long> = castHelper.currentPositionFlow
    override val connectedDeviceName: StateFlow<String?> = castHelper.connectedDeviceName

    override suspend fun connect(device: CastDevice) {
        // Chromecast connection is handled by selecting a MediaRouter route
        // The route is stored in device.extras
        val route = device.extras as? MediaRouter.RouteInfo
        if (route != null) {
            castHelper.selectRoute(device.extras as? Context ?: return, route)
        }
    }

    override suspend fun disconnect() {
        castHelper.disconnectCast()
    }

    override suspend fun loadMedia(
        streamUrl: String,
        title: String,
        author: String?,
        coverUrl: String?,
        positionSeconds: Long
    ) {
        // CastHelper.castAudiobook handles auth token appending and pending media logic.
        // We create a minimal Audiobook for compatibility with the existing API.
        // Note: The actual castAudiobook call is made from CastManager which has access
        // to the full Audiobook object.
    }

    override suspend fun play() {
        castHelper.play()
    }

    override suspend fun pause() {
        castHelper.pause()
    }

    override suspend fun seek(positionSeconds: Long) {
        castHelper.seek(positionSeconds)
    }

    override suspend fun stop() {
        castHelper.stopCasting()
    }

    // -- Chromecast-specific helpers used by CastManager --

    fun startDiscovery(context: Context) {
        castHelper.startDiscovery(context)
    }

    fun stopDiscovery() {
        castHelper.stopDiscovery()
    }

    fun getAvailableRoutes(): StateFlow<List<MediaRouter.RouteInfo>> {
        return castHelper.availableRoutes
    }

    fun selectRoute(context: Context, route: MediaRouter.RouteInfo) {
        castHelper.selectRoute(context, route)
    }

    fun castAudiobook(
        audiobook: Audiobook,
        streamUrl: String,
        coverUrl: String?,
        currentPosition: Long
    ) {
        castHelper.castAudiobook(audiobook, streamUrl, coverUrl, currentPosition)
    }

    fun isCasting(): Boolean = castHelper.isCasting()

    fun getCurrentPositionMs(): Long = castHelper.getCurrentPosition()
}
