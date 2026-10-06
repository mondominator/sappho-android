package com.sappho.audiobooks.data.repository

import android.content.Context
import android.util.Log
import com.sappho.audiobooks.data.remote.LogoutRequest
import com.sappho.audiobooks.data.remote.SapphoApi
import com.sappho.audiobooks.download.DownloadManager
import com.sappho.audiobooks.service.CoverArtProvider
import com.sappho.audiobooks.service.PlaybackController
import com.sappho.audiobooks.service.PlayerState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Everything that has to happen when a session ends, in one place. Logout used
 * to only clear the stored token: the book kept playing on the old stream,
 * the server-side refresh token stayed valid, and the offline queue was then
 * replayed into whichever account signed in next.
 *
 * Downloads are kept (they are large and tied to an account, see
 * DownloadManager) and only shown again when that account signs back in.
 */
@Singleton
class SessionTeardown @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: SapphoApi,
    private val authRepository: AuthRepository,
    private val downloadManager: DownloadManager,
    private val playerState: PlayerState,
    private val playbackController: PlaybackController
) {

    /** User-initiated logout. */
    suspend fun logout() {
        val account = authRepository.getAccountKeySync()

        // 1. Stop playback (local and cast) while the token still works, so the
        //    final position reaches the server.
        try {
            playbackController.stopAllForLogout()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Stopping playback at logout failed", e)
        }

        // 2. Revoke server-side: the access token and the refresh-token family.
        //    Best effort — logging out offline must still work locally.
        val refreshToken = authRepository.getRefreshTokenSync()
        try {
            withTimeoutOrNull(LOGOUT_TIMEOUT_MS) { api.logout(LogoutRequest(refreshToken)) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Server logout failed (continuing local logout)", e)
        }

        // 3. Clear everything tied to this session.
        downloadManager.clearPendingProgressForAccount(account)
        clearLocalSession()
    }

    /**
     * The server rejected our credentials (401 after a failed refresh). The
     * token is already useless, so there is nothing to revoke or sync. The
     * offline queue is KEPT: it is tagged with this account and replays if the
     * same user signs back in, and it can never reach a different account.
     */
    suspend fun onSessionExpired() {
        try {
            playbackController.stopAllForLogout(syncFinalPosition = false)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Stopping playback after session expiry failed", e)
        }
        clearLocalSession()
    }

    private fun clearLocalSession() {
        playerState.deactivate()
        playerState.updateAudiobook(null)
        playerState.updatePosition(0)
        CoverArtProvider.clearCache(context)
        authRepository.clearToken()
        authRepository.clearUserInfo()
        // Re-scope visible downloads / queue: nothing is visible while logged out.
        downloadManager.onAccountChanged()
    }

    companion object {
        private const val TAG = "SessionTeardown"
        private const val LOGOUT_TIMEOUT_MS = 5_000L
    }
}
