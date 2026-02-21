package com.sappho.audiobooks.sync

import android.content.Context
import android.util.Log
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.sappho.audiobooks.download.DownloadManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
    val errorMessage: String? = null,
    val errorTimestamp: Long? = null
)

@Singleton
class SyncStatusManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadManager: DownloadManager
) {
    private val TAG = "SyncStatusManager"
    private companion object {
        const val ERROR_EXPIRY_MS = 60_000L // Auto-clear errors after 60 seconds
    }

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

                val workRunId = workInfo?.id
                if (workRunId != null && workRunId != lastProcessedWorkRunId) {
                    if (lastProcessedWorkRunId == null) {
                        // First observation after app launch - record the ID but
                        // don't show errors from stale WorkManager state. Only
                        // acknowledge success to clear any leftover UI state.
                        lastProcessedWorkRunId = workRunId
                        if (workInfo.state == WorkInfo.State.SUCCEEDED) {
                            _lastSyncTime.value = System.currentTimeMillis()
                            updateSyncStatus(lastSyncSuccess = true)
                        }
                    } else {
                        // New work run during this session - process normally
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
                                // Expected when using ExistingWorkPolicy.REPLACE
                                lastProcessedWorkRunId = workRunId
                            }
                            else -> {}
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

        // Auto-clear stale errors:
        // 1. If no pending items remain, the error is no longer relevant
        // 2. If the error is older than 60 seconds, expire it
        val resolvedError = when {
            errorMessage != null -> errorMessage
            lastSyncSuccess == true -> null
            pendingCount == 0 -> null
            current.errorTimestamp != null &&
                System.currentTimeMillis() - current.errorTimestamp > ERROR_EXPIRY_MS -> null
            else -> current.errorMessage
        }

        _syncStatus.value = current.copy(
            pendingCount = pendingCount,
            issyncing = _isSyncing.value,
            lastSyncTime = _lastSyncTime.value ?: current.lastSyncTime,
            lastSyncSuccess = lastSyncSuccess ?: current.lastSyncSuccess,
            errorMessage = resolvedError,
            errorTimestamp = if (errorMessage != null) System.currentTimeMillis()
                           else if (resolvedError == null) null
                           else current.errorTimestamp
        )
    }
    
    fun triggerSync() {
        Log.d(TAG, "Manually triggering sync")
        // Clear any stale error from previous runs before starting new sync
        _syncStatus.value = _syncStatus.value.copy(errorMessage = null, errorTimestamp = null, lastSyncSuccess = true)
        ProgressSyncWorker.enqueue(context)
        updateSyncStatus()
    }
    
    fun getFormattedLastSyncTime(): String? {
        val lastSync = _syncStatus.value.lastSyncTime ?: return null
        val formatter = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
        return formatter.format(Date(lastSync))
    }
    
    fun clearErrorMessage() {
        _syncStatus.value = _syncStatus.value.copy(errorMessage = null, errorTimestamp = null)
    }
}