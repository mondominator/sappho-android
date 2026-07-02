package com.sappho.audiobooks.service

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Tests for the skip-forward clamping logic (M3): when the player has not yet
 * reported a duration, skipping forward must not clamp against 0 (which would
 * seek back to the start of the book).
 */
class AudioPlaybackServiceSkipTest {

    @Test
    fun `should clamp target to duration when duration is known`() {
        // Given a 100s book and a target past the end
        val result = AudioPlaybackService.clampSkipForwardPosition(
            targetSeconds = 110,
            durationSeconds = 100
        )

        // Then the seek lands on the end of the book
        assertThat(result).isEqualTo(100)
    }

    @Test
    fun `should keep target when below duration`() {
        val result = AudioPlaybackService.clampSkipForwardPosition(
            targetSeconds = 40,
            durationSeconds = 100
        )

        assertThat(result).isEqualTo(40)
    }

    @Test
    fun `should not clamp when duration is unknown`() {
        // Given: duration not yet reported (0) — clamping would seek to 0
        val result = AudioPlaybackService.clampSkipForwardPosition(
            targetSeconds = 40,
            durationSeconds = 0
        )

        // Then the raw target is preserved
        assertThat(result).isEqualTo(40)
    }

    @Test
    fun `should not clamp when duration is negative`() {
        val result = AudioPlaybackService.clampSkipForwardPosition(
            targetSeconds = 25,
            durationSeconds = -1
        )

        assertThat(result).isEqualTo(25)
    }
}
