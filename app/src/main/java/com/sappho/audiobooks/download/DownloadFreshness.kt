package com.sappho.audiobooks.download

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Detects a download that no longer matches the file the server streams —
 * e.g. a multi-file book that was downloaded as part 1 only and has since been
 * merged into one m4b, or a file re-encoded on the server.
 *
 * The book API exposes no file version yet, so this probes `/stream` with
 * `Range: bytes=0-0` and compares the ETag (server: `"<size>-<mtime>"`) and
 * total size from `Content-Range` against what was recorded at download time
 * and the local file's length.
 */
object DownloadFreshness {

    enum class Verdict { FRESH, STALE, UNKNOWN }

    data class RemoteFile(val totalBytes: Long?, val etag: String?)

    fun check(local: DownloadedBook, localFileLength: Long, remote: RemoteFile): Verdict {
        val remoteTotal = remote.totalBytes
        // Strongest signal: the server's ETag changed since we downloaded.
        if (!local.etag.isNullOrBlank() && !remote.etag.isNullOrBlank()) {
            if (local.etag != remote.etag) return Verdict.STALE
        }
        if (remoteTotal == null || remoteTotal <= 0) return Verdict.UNKNOWN
        // The bytes on disk must be exactly what the server serves now. This
        // also catches downloads made before ETags were recorded.
        if (localFileLength != remoteTotal) return Verdict.STALE
        return Verdict.FRESH
    }
}

@Singleton
class DownloadFreshnessChecker @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val downloadManager: DownloadManager
) {
    /**
     * Probe the server for [audiobookId]'s current file. On STALE the download
     * is marked so playback streams the current file instead.
     */
    suspend fun checkAndMark(serverUrl: String, audiobookId: Int): DownloadFreshness.Verdict {
        val local = downloadManager.getDownloadedBook(audiobookId) ?: return DownloadFreshness.Verdict.UNKNOWN
        val remote = probe(serverUrl, audiobookId) ?: return DownloadFreshness.Verdict.UNKNOWN
        val verdict = DownloadFreshness.check(local, File(local.filePath).length(), remote)
        if (verdict == DownloadFreshness.Verdict.STALE) {
            Log.i(TAG, "Download of book $audiobookId is stale (server file changed)")
            downloadManager.markStale(audiobookId)
        }
        return verdict
    }

    private suspend fun probe(serverUrl: String, audiobookId: Int): DownloadFreshness.RemoteFile? =
        withTimeoutOrNull(PROBE_TIMEOUT_MS) {
            withContext(Dispatchers.IO) {
                try {
                    val request = Request.Builder()
                        .url("$serverUrl/api/audiobooks/$audiobookId/stream")
                        .header("Range", "bytes=0-0")
                        .build()
                    okHttpClient.newCall(request).execute().use { response ->
                        when (response.code) {
                            206 -> DownloadFreshness.RemoteFile(
                                DownloadResume.totalFromContentRange(response.header("Content-Range")),
                                response.header("ETag")
                            )
                            200 -> DownloadFreshness.RemoteFile(
                                response.body?.contentLength()?.takeIf { it > 0 },
                                response.header("ETag")
                            )
                            else -> null
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Freshness probe failed for book $audiobookId", e)
                    null
                }
            }
        }

    companion object {
        private const val TAG = "DownloadFreshness"
        private const val PROBE_TIMEOUT_MS = 4_000L
    }
}
