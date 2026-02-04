package com.sappho.audiobooks.data.update

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

sealed class UpdateState {
    object None : UpdateState()
    object Available : UpdateState()
    data class Downloading(val progress: Float) : UpdateState()
    object ReadyToInstall : UpdateState()
    object Installing : UpdateState()
    data class Failed(val message: String) : UpdateState()
}

@Singleton
class InAppUpdateManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val appUpdateManager: AppUpdateManager = AppUpdateManagerFactory.create(context)

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.None)
    val updateState: StateFlow<UpdateState> = _updateState

    private var appUpdateInfo: AppUpdateInfo? = null

    private val installStateListener = InstallStateUpdatedListener { state ->
        Log.d(TAG, "Install state changed: status=${state.installStatus()}, bytesDownloaded=${state.bytesDownloaded()}, totalBytes=${state.totalBytesToDownload()}")

        when (state.installStatus()) {
            InstallStatus.DOWNLOADING -> {
                val progress = if (state.totalBytesToDownload() > 0) {
                    state.bytesDownloaded().toFloat() / state.totalBytesToDownload().toFloat()
                } else 0f
                _updateState.value = UpdateState.Downloading(progress)
            }
            InstallStatus.DOWNLOADED -> {
                Log.d(TAG, "Update downloaded, ready to install")
                _updateState.value = UpdateState.ReadyToInstall
            }
            InstallStatus.INSTALLING -> {
                _updateState.value = UpdateState.Installing
            }
            InstallStatus.INSTALLED -> {
                Log.d(TAG, "Update installed")
                _updateState.value = UpdateState.None
                unregisterListener()
            }
            InstallStatus.FAILED -> {
                Log.e(TAG, "Update failed")
                _updateState.value = UpdateState.Failed("Update failed. Please try again.")
                unregisterListener()
            }
            InstallStatus.CANCELED -> {
                Log.d(TAG, "Update canceled")
                _updateState.value = UpdateState.None
                unregisterListener()
            }
            else -> {}
        }
    }

    init {
        appUpdateManager.registerListener(installStateListener)
    }

    private fun unregisterListener() {
        try {
            appUpdateManager.unregisterListener(installStateListener)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unregister listener", e)
        }
    }

    suspend fun checkForUpdate() {
        try {
            val info = appUpdateManager.appUpdateInfo.await()
            appUpdateInfo = info

            Log.d(TAG, "Update check: availability=${info.updateAvailability()}, installStatus=${info.installStatus()}")

            // Check if update already downloaded and waiting
            if (info.installStatus() == InstallStatus.DOWNLOADED) {
                Log.d(TAG, "Update already downloaded, ready to install")
                _updateState.value = UpdateState.ReadyToInstall
                return
            }

            val isUpdateAvailable = info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
            val isFlexibleAllowed = info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)
            val isImmediateAllowed = info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)

            Log.d(TAG, "Update check: available=$isUpdateAvailable, flexibleAllowed=$isFlexibleAllowed, immediateAllowed=$isImmediateAllowed")

            if (isUpdateAvailable && (isFlexibleAllowed || isImmediateAllowed)) {
                _updateState.value = UpdateState.Available
            } else {
                _updateState.value = UpdateState.None
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check for update", e)
            _updateState.value = UpdateState.None
        }
    }

    fun startUpdate(activity: Activity) {
        val info = appUpdateInfo ?: run {
            Log.w(TAG, "No update info available")
            return
        }

        try {
            // Re-register listener in case it was unregistered
            try {
                appUpdateManager.registerListener(installStateListener)
            } catch (e: Exception) {
                // Already registered
            }

            // Prefer FLEXIBLE for better UX (download in background)
            val updateType = when {
                info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE) -> AppUpdateType.FLEXIBLE
                info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE) -> AppUpdateType.IMMEDIATE
                else -> {
                    Log.e(TAG, "No update type allowed")
                    return
                }
            }

            Log.d(TAG, "Starting update with type=$updateType")
            _updateState.value = UpdateState.Downloading(0f)

            appUpdateManager.startUpdateFlowForResult(
                info,
                activity,
                AppUpdateOptions.defaultOptions(updateType),
                UPDATE_REQUEST_CODE
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start update flow", e)
            _updateState.value = UpdateState.Failed("Failed to start update: ${e.message}")
        }
    }

    fun completeUpdate() {
        Log.d(TAG, "Completing update (will restart app)")
        appUpdateManager.completeUpdate()
    }

    fun dismissUpdate() {
        _updateState.value = UpdateState.None
    }

    fun clearError() {
        if (_updateState.value is UpdateState.Failed) {
            _updateState.value = UpdateState.None
        }
    }

    companion object {
        private const val TAG = "InAppUpdateManager"
        const val UPDATE_REQUEST_CODE = 1001
    }
}
