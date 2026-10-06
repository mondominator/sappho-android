package com.sappho.audiobooks.download

import com.google.common.truth.Truth.assertThat
import com.sappho.audiobooks.download.DownloadResume.Outcome
import org.junit.Test

class DownloadResumeTest {

    @Test
    fun `fresh download sends no range`() {
        assertThat(DownloadResume.requestHeaders(0, "\"1-2\"")).isEmpty()
    }

    @Test
    fun `resume asks for the remaining bytes, guarded by the ETag`() {
        assertThat(DownloadResume.requestHeaders(1_000, "\"5000-123\""))
            .containsExactly("Range", "bytes=1000-", "If-Range", "\"5000-123\"")
    }

    @Test
    fun `206 for the requested offset appends`() {
        assertThat(DownloadResume.interpret(206, "bytes 1000-4999/5000", 4000, existingBytes = 1000))
            .isEqualTo(Outcome.Append(1000, 5000))
    }

    @Test
    fun `206 for a different offset is refused rather than spliced`() {
        assertThat(DownloadResume.interpret(206, "bytes 0-4999/5000", 5000, existingBytes = 1000))
            .isEqualTo(Outcome.Fail(206))
    }

    @Test
    fun `200 means the file changed or range was ignored, so restart`() {
        assertThat(DownloadResume.interpret(200, null, 6000, existingBytes = 1000))
            .isEqualTo(Outcome.Restart(6000))
    }

    @Test
    fun `416 with a complete part file is done`() {
        assertThat(DownloadResume.interpret(416, "bytes */5000", -1, existingBytes = 5000))
            .isEqualTo(Outcome.AlreadyComplete(5000))
    }

    @Test
    fun `416 with a mismatched part file restarts`() {
        assertThat(DownloadResume.interpret(416, "bytes */5000", -1, existingBytes = 7000))
            .isEqualTo(Outcome.Restart(null))
    }

    @Test
    fun `other errors fail`() {
        assertThat(DownloadResume.interpret(500, null, -1, 0)).isEqualTo(Outcome.Fail(500))
    }

    @Test
    fun `total parsed from content range`() {
        assertThat(DownloadResume.totalFromContentRange("bytes 0-0/123456")).isEqualTo(123456)
        assertThat(DownloadResume.totalFromContentRange("bytes */*")).isNull()
        assertThat(DownloadResume.totalFromContentRange(null)).isNull()
    }
}
