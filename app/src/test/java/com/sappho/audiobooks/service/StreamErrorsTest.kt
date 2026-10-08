package com.sappho.audiobooks.service

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import com.google.common.truth.Truth.assertThat
import java.io.IOException
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@RunWith(RobolectricTestRunner::class)
class StreamErrorsTest {

    private val dataSpec = DataSpec(Uri.parse("https://sappho.example.com/api/audiobooks/7/hls/master.m3u8"))

    private fun httpError(code: Int, body: String?) = HttpDataSource.InvalidResponseCodeException(
        code, null, null, emptyMap(), dataSpec, body?.toByteArray() ?: ByteArray(0)
    )

    private fun playbackError(cause: Throwable) =
        PlaybackException("load failed", cause, PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS)

    @Test
    fun `finds the HTTP status and error code inside a playback error`() {
        val error = playbackError(
            IOException("wrapped", httpError(404, """{"error":"gone","code":"FILE_VERSION_CHANGED","file_version":"v2"}"""))
        )

        assertThat(StreamErrors.httpFailureOf(error)).isEqualTo(HttpFailure(404, "FILE_VERSION_CHANGED"))
    }

    @Test
    fun `reads a linked-server code from a relayed error`() {
        val error = playbackError(
            httpError(503, """{"error":"Linked server unavailable","code":"REMOTE_UNAVAILABLE","source":{"id":4,"name":"Robert"}}""")
        )

        assertThat(StreamErrors.httpFailureOf(error)).isEqualTo(HttpFailure(503, "REMOTE_UNAVAILABLE"))
    }

    @Test
    fun `reads 415 HLS_UNSUPPORTED`() {
        val error = playbackError(httpError(415, """{"code":"HLS_UNSUPPORTED"}"""))

        assertThat(StreamErrors.httpFailureOf(error)).isEqualTo(HttpFailure(415, "HLS_UNSUPPORTED"))
    }

    @Test
    fun `non-JSON or empty bodies give a status without a code`() {
        assertThat(StreamErrors.httpFailureOf(playbackError(httpError(502, "<html>Bad gateway</html>"))))
            .isEqualTo(HttpFailure(502, null))
        assertThat(StreamErrors.httpFailureOf(playbackError(httpError(404, null))))
            .isEqualTo(HttpFailure(404, null))
        assertThat(StreamErrors.errorCodeOf("[1,2]".toByteArray())).isNull()
    }

    @Test
    fun `errors without an HTTP status give no failure`() {
        assertThat(StreamErrors.httpFailureOf(playbackError(IOException("connection reset")))).isNull()
        assertThat(StreamErrors.httpFailureOf(null)).isNull()
    }

    @Test
    fun `client errors are not retried, server errors and timeouts are`() {
        val policy = NoRetryOnClientErrorPolicy()

        assertThat(policy.getRetryDelayMsFor(loadError(httpError(415, null)))).isEqualTo(C.TIME_UNSET)
        assertThat(policy.getRetryDelayMsFor(loadError(httpError(404, null)))).isEqualTo(C.TIME_UNSET)
        assertThat(policy.getRetryDelayMsFor(loadError(httpError(503, null)))).isNotEqualTo(C.TIME_UNSET)
        assertThat(policy.getRetryDelayMsFor(loadError(httpError(429, null)))).isNotEqualTo(C.TIME_UNSET)
        assertThat(policy.getRetryDelayMsFor(loadError(IOException("timeout")))).isNotEqualTo(C.TIME_UNSET)
    }

    private fun loadError(exception: IOException) = LoadErrorHandlingPolicy.LoadErrorInfo(
        LoadEventInfo(LoadEventInfo.getNewId(), dataSpec, 0L),
        MediaLoadData(C.DATA_TYPE_MANIFEST),
        exception,
        /* errorCount= */ 1
    )
}
