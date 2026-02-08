package com.sappho.audiobooks.sync

import android.content.Context
import android.util.Log
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.sappho.audiobooks.download.DownloadManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class SyncStatus(
    val pendingCount: Int = 0,
    val issyncing: Boolean = false,
    val lastSyncTime: Long? = null,
    val lastSyncSuccess: Boolean = true,
    val errorMessage: String? = null
)

@Singleton
class SyncStatusManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadManager: DownloadManager
) {
    private val TAG = "SyncStatusManager"

    private val _syncStatus = MutableStateFlow(SyncStatus())
    val syncStatus: StateFlow<SyncStatus> = _syncStatus

    private val _lastSyncTime = MutableStateFlow<Long?>(null)
    private val _isSyncing = MutableStateFlow(false)

    // Track the last processed work run to avoid re-handling the same state
    private var lastProcessedWorkRunId: UUID? = null

    private val workManager = WorkManager.getInstance(context)
    
    init {
        observeWorkStatus()
        observePendingProgress()
    }
    
    private fun observeWorkStatus() {
        // Observe the progress sync work status
        workManager.getWorkInfosForUniqueWorkLiveData("progress_sync")
            .observeForever { workInfos ->
                val workInfo = workInfos?.firstOrNull()
                val isRunning = workInfo?.state == WorkInfo.State.RUNNING

                if (isRunning != _isSyncing.value) {
                    _isSyncing.value = isRunning
                    updateSyncStatus()
                }

                // Only process completed states once per work run to avoid
                // re-setting errors that the user has dismissed
                val workRunId = workInfo?.id
                if (workRunId != null && workRunId != lastProcessedWorkRunId) {
                    when (workInfo.state) {
                        WorkInfo.State.SUCCEEDED -> {
                            lastProcessedWorkRunId = workRunId
                            _lastSyncTime.value = System.currentTimeMillis()
                            updateSyncStatus(lastSyncSuccess = true)
                        }
                        WorkInfo.State.FAILED -> {
                            lastProcessedWorkRunId = workRunId
                            updateSyncStatus(
                                lastSyncSuccess = false,
                                errorMessage = "Sync failed. Will retry when network is available."
                            )
                        }
                        WorkInfo.State.CANCELLED -> {
                            lastProcessedWorkRunId = workRunId
                            updateSyncStatus(
                                lastSyncSuccess = false,
                                errorMessage = "Sync was cancelled"
                            )
                        }
                        else -> {
                            // Running, enqueued, or blocked - no action needed yet
                        }
                    }
                }
            }
    }
    
    private fun observePendingProgress() {
        // This would ideally be a Flow, but for now we'll update manually
        // when sync operations occur
    }
    
    fun updateSyncStatus(
        lastSyncSuccess: Boolean? = null,
        errorMessage: String? = null
    ) {
        val pendingCount = downloadManager.getPendingProgressList().size
        val current = _syncStatus.value
        
        _syncStatus.value = current.copy(
            pendingCount = pendingCount,
            issyncing = _isSyncing.value,
            lastSyncTime = _lastSyncTime.value ?: current.lastSyncTime,
            lastSyncSuccess = lastSyncSuccess ?: current.lastSyncSuccess,
            errorMessage = errorMessage ?: if (lastSyncSuccess == true) null else current.errorMessage
        )
    }
    
    fun triggerSync() {
        Log.d(TAG, "Manually triggering sync")
        ProgressSyncWorker.enqueue(context)
        updateSyncStatus()
    }
    
    fun getFormattedLastSyncTime(): String? {
        val lastSync = _syncStatus.value.lastSyncTime ?: return null
        val formatter = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
        return formatter.format(Date(lastSync))
    }
    
    fun clearErrorMessage() {
        _syncStatus.value = _syncStatus.value.copy(errorMessage = null)
    }
}