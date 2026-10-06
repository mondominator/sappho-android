package com.sappho.audiobooks.download

/**
 * HTTP Range handling for resuming a partial download. The server's /stream
 * route answers `Range: bytes=N-` with 206 and `Content-Range: bytes N-M/TOTAL`
 * and sends an ETag derived from file size + mtime.
 */
object DownloadResume {

    sealed class Outcome {
        /** Append the body to the existing part file starting at [offset]. */
        data class Append(val offset: Long, val totalBytes: Long?) : Outcome()
        /** Discard the part file and write the body from byte 0. */
        data class Restart(val totalBytes: Long?) : Outcome()
        /** The part file already holds the whole file. */
        data class AlreadyComplete(val totalBytes: Long) : Outcome()
        data class Fail(val httpCode: Int) : Outcome()
    }

    /** Request headers for resuming from [existingBytes]; empty for a fresh start. */
    fun requestHeaders(existingBytes: Long, etag: String?): Map<String, String> {
        if (existingBytes <= 0) return emptyMap()
        val headers = mutableMapOf("Range" to "bytes=$existingBytes-")
        // If-Range: the server only honours the range when the file is
        // unchanged; otherwise it sends the whole (new) file with 200.
        if (!etag.isNullOrBlank()) headers["If-Range"] = etag
        return headers
    }

    /** Total size from `Content-Range: bytes a-b/TOTAL` (null when unknown or `*`). */
    fun totalFromContentRange(contentRange: String?): Long? =
        contentRange?.substringAfterLast('/', "")?.trim()?.toLongOrNull()

    /** Start offset from `Content-Range: bytes a-b/TOTAL`. */
    private fun startFromContentRange(contentRange: String?): Long? =
        contentRange?.removePrefix("bytes")?.trim()?.substringBefore('-')?.trim()?.toLongOrNull()

    fun interpret(
        httpCode: Int,
        contentRange: String?,
        contentLength: Long,
        existingBytes: Long
    ): Outcome = when (httpCode) {
        206 -> {
            val start = startFromContentRange(contentRange)
            val total = totalFromContentRange(contentRange)
            if (start != null && start == existingBytes) Outcome.Append(existingBytes, total)
            // A range we didn't ask for: don't risk a corrupt splice.
            else Outcome.Fail(httpCode)
        }
        200 -> Outcome.Restart(contentLength.takeIf { it > 0 })
        416 -> {
            // Range starts at/after EOF. If the part file is exactly the file, it's done.
            val total = totalFromContentRange(contentRange)
            if (total != null && existingBytes == total) Outcome.AlreadyComplete(total)
            else Outcome.Restart(null)
        }
        else -> Outcome.Fail(httpCode)
    }
}
