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
import com.sappho.audiobooks.data.remote.ProgressUpdateRequest
import com.sappho.audiobooks.data.remote.SapphoApi
import com.sappho.audiobooks.download.DownloadManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class ProgressSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val api: SapphoApi,
    private val downloadManager: DownloadManager
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
                    ExistingWorkPolicy.REPLACE,
                    workRequest
                )
        }
    }

    override suspend fun doWork(): Result {
        return try {
            Log.d(TAG, "Starting progress sync work")
            
            val pendingList = downloadManager.getPendingProgressList()
            if (pendingList.isEmpty()) {
                Log.d(TAG, "No pending progress to sync")
                return Result.success()
            }

            Log.d(TAG, "Syncing ${pendingList.size} pending progress updates")
            var successCount = 0
            var failureCount = 0

            for (pending in pendingList) {
                try {
                    val response = api.updateProgress(
                        pending.audiobookId,
                        ProgressUpdateRequest(
                            position = pending.position,
                            completed = 0,
                            state = "paused"
                        )
                    )

                    if (response.isSuccessful) {
                        downloadManager.clearPendingProgress(pending.audiobookId)
                        successCount++
                        Log.d(TAG, "Successfully synced progress for book ${pending.audiobookId}")
                    } else {
                        failureCount++
                        Log.w(TAG, "Failed to sync progress for book ${pending.audiobookId}: ${response.code()}")
                    }
                } catch (e: Exception) {
                    failureCount++
                    Log.e(TAG, "Exception syncing progress for book ${pending.audiobookId}", e)
                }
            }

            Log.d(TAG, "Progress sync complete: $successCount successes, $failureCount failures")

            if (failureCount == 0) {
                Result.success()
            } else if (successCount > 0) {
                // Partial success — retry for remaining failures
                Result.retry()
            } else {
                // All failed
                Result.failure()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Progress sync worker failed", e)
            Result.retry()
        }
    }
}