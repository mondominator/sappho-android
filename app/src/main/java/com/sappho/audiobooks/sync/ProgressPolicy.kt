package com.sappho.audiobooks.sync

/**
 * Pure rules for when playback counts as "finished" and when a position is
 * too close to the end to sync. Kept free of Android types so it can be unit
 * tested directly.
 */
object ProgressPolicy {

    /**
     * A book is finished only when playback stops within this many seconds of
     * the KNOWN total duration (the server's book duration).
     */
    const val FINISHED_THRESHOLD_SECONDS = 60

    /** Positions this close to the end are not synced as "in progress". */
    const val NEAR_END_SECONDS = 30

    /**
     * Decide whether reaching the end of the media should mark the book
     * finished.
     *
     * The player's own duration is not trusted: for a multi-file book the
     * stream (and therefore any download) is part 1 only, so the player hits
     * STATE_ENDED long before the book is over. We require the end position to
     * be within [FINISHED_THRESHOLD_SECONDS] of the book's server duration. If
     * that duration is unknown we never auto-finish; the user can still mark
     * the book finished by hand.
     *
     * @param endPositionSeconds where the player stopped
     * @param bookDurationSeconds the server's total duration for the book, or null
     */
    fun shouldMarkFinished(endPositionSeconds: Long, bookDurationSeconds: Int?): Boolean {
        val total = bookDurationSeconds ?: return false
        if (total <= 0 || endPositionSeconds <= 0) return false
        return endPositionSeconds >= total - FINISHED_THRESHOLD_SECONDS
    }

    /**
     * True when [positionSeconds] is within [NEAR_END_SECONDS] of [durationSeconds].
     * An unknown duration (<= 0) is never "near the end".
     */
    fun isNearEnd(positionSeconds: Long, durationSeconds: Long): Boolean =
        durationSeconds > 0 && (durationSeconds - positionSeconds) < NEAR_END_SECONDS

    /**
     * Clamp a seek target (seconds) into the playable range. Seeking past the
     * end makes ExoPlayer report STATE_ENDED; an unknown duration leaves the
     * target unclamped except for the lower bound.
     */
    fun clampSeekSeconds(targetSeconds: Long, durationSeconds: Long): Long {
        val lower = targetSeconds.coerceAtLeast(0)
        return if (durationSeconds > 0) lower.coerceAtMost(durationSeconds) else lower
    }
}
