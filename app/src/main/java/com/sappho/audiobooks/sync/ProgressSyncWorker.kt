package com.sappho.audiobooks.sync

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.NetworkType
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

@HiltWorker
class ProgressSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val replayer: PendingProgressReplayer
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "ProgressSyncWorker"
        private const val WORK_NAME = "progress_sync"

        fun enqueue(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = OneTimeWorkRequestBuilder<ProgressSyncWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    30000L, // 30 seconds minimum backoff
                    TimeUnit.MILLISECONDS
                )
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    WORK_NAME,
                    // Run after any in-flight sync instead of cancelling it
                    // mid-request (REPLACE did); a failed chain is replaced.
                    ExistingWorkPolicy.APPEND_OR_REPLACE,
                    workRequest
                )
        }
    }

    override suspend fun doWork(): Result {
        return try {
            val outcome = replayer.replayAll()
            Log.d(TAG, "Progress sync: ${outcome.synced} synced, ${outcome.dropped} dropped, ${outcome.retryable} to retry")
            // Unreachable server / 5xx: retry with backoff instead of FAILED,
            // which would strand the queue until something new is enqueued.
            if (outcome.needsRetry) Result.retry() else Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Progress sync worker failed", e)
            Result.retry()
        }
    }
}
