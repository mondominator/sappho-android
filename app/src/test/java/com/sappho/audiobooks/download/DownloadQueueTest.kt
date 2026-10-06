package com.sappho.audiobooks.download

import com.google.common.truth.Truth.assertThat
import com.sappho.audiobooks.download.DownloadQueue.CancelResult
import com.sappho.audiobooks.download.DownloadQueue.EnqueueResult
import org.junit.Test

class DownloadQueueTest {

    private val queue = DownloadQueue()

    @Test
    fun `a second download queues behind the first instead of replacing it`() {
        assertThat(queue.enqueue(1)).isEqualTo(EnqueueResult.STARTS_NOW)
        assertThat(queue.takeNext()).isEqualTo(1)

        assertThat(queue.enqueue(2)).isEqualTo(EnqueueResult.QUEUED)

        // Book 1 is still the active download; book 2 waits
        assertThat(queue.active).isEqualTo(1)
        assertThat(queue.takeNext()).isNull()
        assertThat(queue.isIdle).isFalse()
    }

    @Test
    fun `finishing the first starts the second and the service may stop only after both`() {
        queue.enqueue(1); queue.takeNext()
        queue.enqueue(2)

        queue.finish(1)
        assertThat(queue.isIdle).isFalse() // book 2 still waiting: must not stop the service

        assertThat(queue.takeNext()).isEqualTo(2)
        queue.finish(2)
        assertThat(queue.isIdle).isTrue()
    }

    @Test
    fun `requesting a book already active or queued is a no-op`() {
        queue.enqueue(1); queue.takeNext()
        queue.enqueue(2)
        assertThat(queue.enqueue(1)).isEqualTo(EnqueueResult.ALREADY_PRESENT)
        assertThat(queue.enqueue(2)).isEqualTo(EnqueueResult.ALREADY_PRESENT)
        assertThat(queue.waitingIds()).containsExactly(2)
    }

    @Test
    fun `cancelling a queued book leaves the active one running`() {
        queue.enqueue(1); queue.takeNext()
        queue.enqueue(2)

        assertThat(queue.cancel(2)).isEqualTo(CancelResult.REMOVED_FROM_QUEUE)
        assertThat(queue.active).isEqualTo(1)
        assertThat(queue.waitingIds()).isEmpty()
    }

    @Test
    fun `cancelling the active book does not drop the queued one`() {
        queue.enqueue(1); queue.takeNext()
        queue.enqueue(2)

        assertThat(queue.cancel(1)).isEqualTo(CancelResult.CANCEL_ACTIVE)
        queue.finish(1) // the cancelled transfer unwinds
        assertThat(queue.takeNext()).isEqualTo(2)
    }

    @Test
    fun `finish for a book that is not active does not clear the active one`() {
        queue.enqueue(1); queue.takeNext()
        queue.finish(99)
        assertThat(queue.active).isEqualTo(1)
    }

    @Test
    fun `cancel of unknown book reports not found`() {
        assertThat(queue.cancel(5)).isEqualTo(CancelResult.NOT_FOUND)
    }

    @Test
    fun `clearWaiting returns the dropped ids`() {
        queue.enqueue(1); queue.takeNext()
        queue.enqueue(2); queue.enqueue(3)
        assertThat(queue.clearWaiting()).containsExactly(2, 3).inOrder()
        assertThat(queue.active).isEqualTo(1)
    }
}
