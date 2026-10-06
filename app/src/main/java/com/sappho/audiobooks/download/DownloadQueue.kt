package com.sappho.audiobooks.download

/**
 * Lifecycle of DownloadService's work: one active download at a time, the
 * rest waiting in order. Pure state, no Android, so the rules are testable.
 *
 * The bug this replaces: starting book B cancelled book A, and A's cleanup
 * then stopped the service, killing B too, with both left "downloading"
 * forever. Here a new request only queues, cancelling one book never touches
 * another, and the service may stop only when [isIdle].
 */
class DownloadQueue {

    enum class EnqueueResult { STARTS_NOW, QUEUED, ALREADY_PRESENT }
    enum class CancelResult { CANCEL_ACTIVE, REMOVED_FROM_QUEUE, NOT_FOUND }

    private val waiting = ArrayDeque<Int>()

    /** The download currently transferring, if any. */
    var active: Int? = null
        private set

    val isIdle: Boolean
        @Synchronized get() = active == null && waiting.isEmpty()

    @Synchronized
    fun enqueue(audiobookId: Int): EnqueueResult {
        if (audiobookId == active || audiobookId in waiting) return EnqueueResult.ALREADY_PRESENT
        waiting.addLast(audiobookId)
        return if (active == null && waiting.size == 1) EnqueueResult.STARTS_NOW else EnqueueResult.QUEUED
    }

    /** Promote the next waiting download to active. Null if one is already active or none wait. */
    @Synchronized
    fun takeNext(): Int? {
        if (active != null) return null
        val next = waiting.removeFirstOrNull() ?: return null
        active = next
        return next
    }

    /** The active download ended (success, failure or cancel). */
    @Synchronized
    fun finish(audiobookId: Int) {
        if (active == audiobookId) active = null
    }

    @Synchronized
    fun cancel(audiobookId: Int): CancelResult = when {
        active == audiobookId -> CancelResult.CANCEL_ACTIVE
        waiting.remove(audiobookId) -> CancelResult.REMOVED_FROM_QUEUE
        else -> CancelResult.NOT_FOUND
    }

    /** Drop every waiting download; returns their ids. The active one is left to the caller. */
    @Synchronized
    fun clearWaiting(): List<Int> {
        val ids = waiting.toList()
        waiting.clear()
        return ids
    }

    @Synchronized
    fun waitingIds(): List<Int> = waiting.toList()
}
