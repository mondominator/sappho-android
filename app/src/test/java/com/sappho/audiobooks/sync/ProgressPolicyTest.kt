package com.sappho.audiobooks.sync

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ProgressPolicyTest {

    @Test
    fun `end of part 1 of a multi-file book is not finished`() {
        // 10 h book, stream/download is part 1 (1 h): player ends at 3600 s
        assertThat(ProgressPolicy.shouldMarkFinished(endPositionSeconds = 3_600, bookDurationSeconds = 36_000)).isFalse()
    }

    @Test
    fun `ending within the threshold of the known duration is finished`() {
        assertThat(ProgressPolicy.shouldMarkFinished(35_990, 36_000)).isTrue()
        assertThat(ProgressPolicy.shouldMarkFinished(36_000 - ProgressPolicy.FINISHED_THRESHOLD_SECONDS.toLong(), 36_000)).isTrue()
    }

    @Test
    fun `just outside the threshold is not finished`() {
        assertThat(ProgressPolicy.shouldMarkFinished(36_000 - ProgressPolicy.FINISHED_THRESHOLD_SECONDS - 1L, 36_000)).isFalse()
    }

    @Test
    fun `unknown or zero duration never auto-finishes`() {
        assertThat(ProgressPolicy.shouldMarkFinished(3_600, null)).isFalse()
        assertThat(ProgressPolicy.shouldMarkFinished(3_600, 0)).isFalse()
    }

    @Test
    fun `position zero never finishes`() {
        assertThat(ProgressPolicy.shouldMarkFinished(0, 30)).isFalse()
    }

    @Test
    fun `near end uses a 30 second window and ignores unknown duration`() {
        assertThat(ProgressPolicy.isNearEnd(3_575, 3_600)).isTrue()
        assertThat(ProgressPolicy.isNearEnd(3_500, 3_600)).isFalse()
        assertThat(ProgressPolicy.isNearEnd(10, 0)).isFalse()
    }

    @Test
    fun `seek targets are clamped into the book`() {
        // A milliseconds value passed where seconds are expected must not run past the end
        assertThat(ProgressPolicy.clampSeekSeconds(3_600_000, 36_000)).isEqualTo(36_000)
        assertThat(ProgressPolicy.clampSeekSeconds(-5, 36_000)).isEqualTo(0)
        assertThat(ProgressPolicy.clampSeekSeconds(500, 0)).isEqualTo(500)
    }
}
