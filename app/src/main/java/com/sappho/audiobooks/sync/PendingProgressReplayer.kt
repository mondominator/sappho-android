package com.sappho.audiobooks.sync

import android.util.Log
import com.sappho.audiobooks.data.remote.ProgressUpdateRequest
import com.sappho.audiobooks.data.remote.SapphoApi
import com.sappho.audiobooks.download.DownloadManager
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sends the signed-in account's offline progress queue to the server.
 *
 * Every replay carries `isReplay: true`, so the server's forward-only guard
 * (server/routes/audiobooks/progress.js) decides conflicts: a queued position
 * older than what another device has since written is ignored instead of
 * clobbering it. An entry leaves the queue only after the server answered 2xx
 * for it (applied or deliberately ignored), or rejected it permanently (4xx
 * other than auth/rate-limit). Network errors and 5xx keep it for a retry.
 */
@Singleton
class PendingProgressReplayer @Inject constructor(
    private val api: SapphoApi,
    private val downloadManager: DownloadManager
) {
    data class Result(val synced: Int, val dropped: Int, val retryable: Int) {
        val needsRetry: Boolean get() = retryable > 0
    }

    suspend fun replayAll(): Result {
        val pending = downloadManager.getPendingProgressList()
        var synced = 0
        var dropped = 0
        var retryable = 0
        for (entry in pending) {
            try {
                val response = api.updateProgress(
                    entry.audiobookId,
                    ProgressUpdateRequest(
                        position = entry.position,
                        completed = 0,
                        state = "paused",
                        isReplay = true
                    )
                )
                when {
                    response.isSuccessful -> {
                        downloadManager.clearPendingProgressIfUnchanged(entry)
                        synced++
                    }
                    isPermanentFailure(response.code()) -> {
                        Log.w(TAG, "Dropping queued progress for book ${entry.audiobookId}: HTTP ${response.code()}")
                        downloadManager.clearPendingProgressIfUnchanged(entry)
                        dropped++
                    }
                    else -> retryable++
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Replay failed for book ${entry.audiobookId}; will retry", e)
                retryable++
            }
        }
        return Result(synced, dropped, retryable)
    }

    companion object {
        private const val TAG = "ProgressReplayer"

        /** 4xx that retrying cannot fix (bad position, deleted book). */
        internal fun isPermanentFailure(code: Int): Boolean =
            code in 400..499 && code != 401 && code != 403 && code != 408 && code != 429
    }
}
