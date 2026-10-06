package com.sappho.audiobooks.download

import com.google.common.truth.Truth.assertThat
import com.sappho.audiobooks.domain.model.Audiobook
import com.sappho.audiobooks.download.DownloadFreshness.RemoteFile
import com.sappho.audiobooks.download.DownloadFreshness.Verdict
import io.mockk.mockk
import org.junit.Test

class DownloadFreshnessTest {

    private fun local(etag: String? = null, size: Long? = null) = DownloadedBook(
        audiobook = mockk<Audiobook>(relaxed = true),
        filePath = "/x",
        fileSize = 100,
        downloadedAt = 0,
        etag = etag,
        serverFileSize = size
    )

    @Test
    fun `part-1-only download of a book the server has since merged is stale`() {
        // Old download: 300 MB part 1, no ETag recorded (pre-0.9.88)
        val verdict = DownloadFreshness.check(local(), localFileLength = 300_000_000, remote = RemoteFile(900_000_000, "\"900000000-1\""))
        assertThat(verdict).isEqualTo(Verdict.STALE)
    }

    @Test
    fun `changed ETag is stale even if the size matches`() {
        val verdict = DownloadFreshness.check(local(etag = "\"5-1\""), 5, RemoteFile(5, "\"5-2\""))
        assertThat(verdict).isEqualTo(Verdict.STALE)
    }

    @Test
    fun `same ETag and size is fresh`() {
        val verdict = DownloadFreshness.check(local(etag = "\"5-1\""), 5, RemoteFile(5, "\"5-1\""))
        assertThat(verdict).isEqualTo(Verdict.FRESH)
    }

    @Test
    fun `truncated local file is stale`() {
        val verdict = DownloadFreshness.check(local(etag = "\"5000-1\""), 4000, RemoteFile(5000, "\"5000-1\""))
        assertThat(verdict).isEqualTo(Verdict.STALE)
    }

    @Test
    fun `unknown remote size and no etag to compare is unknown`() {
        val verdict = DownloadFreshness.check(local(), 5, RemoteFile(null, null))
        assertThat(verdict).isEqualTo(Verdict.UNKNOWN)
    }
}
