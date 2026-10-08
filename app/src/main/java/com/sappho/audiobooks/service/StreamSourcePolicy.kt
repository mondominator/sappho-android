package com.sappho.audiobooks.service

import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import com.sappho.audiobooks.util.RemoteErrors
import com.sappho.audiobooks.util.parseApiErrorCode

/** How a book is played. */
enum class StreamMode {
    /** A downloaded file on the device. */
    LOCAL,

    /** `GET /api/audiobooks/:id/stream`, the whole file over HTTP ranges. */
    PROGRESSIVE,

    /** `GET /api/audiobooks/:id/hls/master.m3u8`, ~10 s fMP4 segments. */
    HLS
}

/** The source chosen for one playback. [preferLow] asks the server for the data-saver variant first. */
data class StreamPlan(val mode: StreamMode, val preferLow: Boolean = false)

/** An HTTP error the server answered with, and the `code` field of its JSON body if any. */
data class HttpFailure(val statusCode: Int, val errorCode: String?)

/** What to do after a playback error. */
enum class StreamRecovery {
    /** Not a streaming problem we can fix: surface the error. */
    NONE,

    /** The file changed under the HLS URLs: request master.m3u8 again at the same position. */
    RELOAD_HLS,

    /** Play the same book from `/stream` at the same position. */
    FALL_BACK_TO_PROGRESSIVE
}

object StreamUrls {
    const val PREFER_LOW_QUERY = "prefer=low"

    fun progressive(serverUrl: String, audiobookId: Int): String =
        "$serverUrl/api/audiobooks/$audiobookId/stream"

    fun hlsMaster(serverUrl: String, audiobookId: Int, preferLow: Boolean): String {
        val base = "$serverUrl/api/audiobooks/$audiobookId/hls/master.m3u8"
        return if (preferLow) "$base?$PREFER_LOW_QUERY" else base
    }
}

/**
 * Chooses between a downloaded file, progressive `/stream` and HLS, and decides
 * how to recover when HLS fails. Both modes share one timeline, so a fallback
 * resumes at the same position.
 *
 * HLS is used on metered networks (it needs ~0.1 MB before the first audio,
 * where progressive needs the whole `moov` box, up to ~15 MB) and whenever data
 * saver is on. On unmetered networks progressive stays the default.
 *
 * Not thread safe: call from the main thread, as the player does.
 */
class StreamSourcePolicy(private val maxMasterReloads: Int = DEFAULT_MAX_MASTER_RELOADS) {

    /** Books the server said it cannot serve as HLS (415, e.g. MP3). Kept for the process lifetime. */
    private val hlsUnsupportedBooks = mutableSetOf<Int>()

    private var masterReloads = 0

    fun choose(
        audiobookId: Int,
        hasLocalFile: Boolean,
        isMeteredNetwork: Boolean,
        dataSaver: Boolean
    ): StreamPlan {
        // A new playback gets a fresh reload budget.
        masterReloads = 0
        return when {
            hasLocalFile -> StreamPlan(StreamMode.LOCAL)
            audiobookId in hlsUnsupportedBooks -> StreamPlan(StreamMode.PROGRESSIVE)
            dataSaver -> StreamPlan(StreamMode.HLS, preferLow = true)
            isMeteredNetwork -> StreamPlan(StreamMode.HLS)
            else -> StreamPlan(StreamMode.PROGRESSIVE)
        }
    }

    fun recoveryFor(audiobookId: Int, mode: StreamMode, failure: HttpFailure?): StreamRecovery {
        if (mode != StreamMode.HLS) return StreamRecovery.NONE
        return when {
            // The linked server is down or the book is gone: /stream is relayed
            // the same way and would fail too, so let the error reach the user.
            RemoteErrors.isRemoteError(failure?.errorCode) -> StreamRecovery.NONE
            failure?.statusCode == HTTP_UNSUPPORTED_MEDIA_TYPE -> {
                hlsUnsupportedBooks += audiobookId
                StreamRecovery.FALL_BACK_TO_PROGRESSIVE
            }
            failure?.statusCode == HTTP_NOT_FOUND &&
                failure.errorCode == FILE_VERSION_CHANGED &&
                masterReloads < maxMasterReloads -> {
                masterReloads++
                StreamRecovery.RELOAD_HLS
            }
            // Any other HLS failure, including a file that keeps changing:
            // progressive is the path every server version supports.
            else -> StreamRecovery.FALL_BACK_TO_PROGRESSIVE
        }
    }

    companion object {
        const val DEFAULT_MAX_MASTER_RELOADS = 3
        const val HTTP_NOT_FOUND = 404
        const val HTTP_UNSUPPORTED_MEDIA_TYPE = 415
        const val FILE_VERSION_CHANGED = "FILE_VERSION_CHANGED"
    }
}

@UnstableApi
object StreamErrors {

    /** The first HTTP status error in [error]'s cause chain, or null for non-HTTP failures. */
    fun httpFailureOf(error: Throwable?): HttpFailure? {
        var cause = error
        val seen = mutableSetOf<Throwable>()
        while (cause != null && seen.add(cause)) {
            if (cause is HttpDataSource.InvalidResponseCodeException) {
                return HttpFailure(cause.responseCode, errorCodeOf(cause.responseBody))
            }
            cause = cause.cause
        }
        return null
    }

    /** The `code` field of a JSON error body such as `{"code":"FILE_VERSION_CHANGED"}`. */
    fun errorCodeOf(body: ByteArray?): String? =
        if (body == null || body.isEmpty()) null else parseApiErrorCode(String(body, Charsets.UTF_8))
}

/**
 * Media3 retries HTTP errors by default. A 4xx from the server (415 for an MP3
 * book, 404 for a changed file) will not change on retry, and each retry delays
 * the fallback, so fail those at once. 408 and 429 stay retryable.
 */
@UnstableApi
class NoRetryOnClientErrorPolicy : DefaultLoadErrorHandlingPolicy() {
    override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
        val exception = loadErrorInfo.exception
        if (exception is HttpDataSource.InvalidResponseCodeException &&
            exception.responseCode in 400..499 &&
            exception.responseCode != HTTP_REQUEST_TIMEOUT &&
            exception.responseCode != HTTP_TOO_MANY_REQUESTS
        ) {
            return C.TIME_UNSET
        }
        return super.getRetryDelayMsFor(loadErrorInfo)
    }

    private companion object {
        const val HTTP_REQUEST_TIMEOUT = 408
        const val HTTP_TOO_MANY_REQUESTS = 429
    }
}
