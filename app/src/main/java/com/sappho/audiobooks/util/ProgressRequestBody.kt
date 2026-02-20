package com.sappho.audiobooks.util

import okhttp3.MediaType
import okhttp3.RequestBody
import okio.BufferedSink
import okio.ForwardingSink
import okio.Sink
import okio.buffer
import java.io.File

/**
 * RequestBody wrapper that reports upload progress via a callback.
 * Used to provide real byte-level progress during file uploads.
 */
class ProgressRequestBody(
    private val file: File,
    private val contentType: MediaType?,
    private val onProgress: (bytesWritten: Long, totalBytes: Long) -> Unit
) : RequestBody() {

    override fun contentType(): MediaType? = contentType

    override fun contentLength(): Long = file.length()

    // Prevent HttpLoggingInterceptor from calling writeTo twice
    // (once for logging buffer, once for actual network send)
    override fun isOneShot(): Boolean = true

    override fun writeTo(sink: BufferedSink) {
        val totalBytes = file.length()
        val countingSink = CountingSink(sink, totalBytes, onProgress)
        val bufferedSink = countingSink.buffer()

        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                bufferedSink.write(buffer, 0, bytesRead)
            }
        }

        bufferedSink.flush()
    }

    private class CountingSink(
        delegate: Sink,
        private val totalBytes: Long,
        private val onProgress: (bytesWritten: Long, totalBytes: Long) -> Unit
    ) : ForwardingSink(delegate) {
        private var bytesWritten = 0L

        override fun write(source: okio.Buffer, byteCount: Long) {
            super.write(source, byteCount)
            bytesWritten += byteCount
            onProgress(bytesWritten, totalBytes)
        }
    }
}
