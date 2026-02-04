package com.sappho.audiobooks.data.update

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InAppUpdateManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val appUpdateManager: AppUpdateManager = AppUpdateManagerFactory.create(context)

    private val _updateAvailable = MutableStateFlow(false)
    val updateAvailable: StateFlow<Boolean> = _updateAvailable

    private var appUpdateInfo: AppUpdateInfo? = null
    private var updateType: Int = AppUpdateType.IMMEDIATE

    suspend fun checkForUpdate() {
        try {
            val info = appUpdateManager.appUpdateInfo.await()
            appUpdateInfo = info

            val isUpdateAvailable = info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
            val isImmediateAllowed = info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)
            val isFlexibleAllowed = info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)

            // Prefer IMMEDIATE, fall back to FLEXIBLE
            updateType = when {
                isImmediateAllowed -> AppUpdateType.IMMEDIATE
                isFlexibleAllowed -> AppUpdateType.FLEXIBLE
                else -> AppUpdateType.IMMEDIATE
            }

            _updateAvailable.value = isUpdateAvailable && (isImmediateAllowed || isFlexibleAllowed)

            Log.d(TAG, "Update check: available=$isUpdateAvailable, immediateAllowed=$isImmediateAllowed, flexibleAllowed=$isFlexibleAllowed, usingType=$updateType")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check for update", e)
            _updateAvailable.value = false
        }
    }

    fun startUpdate(activity: Activity) {
        val info = appUpdateInfo ?: run {
            Log.w(TAG, "No update info available")
            return
        }

        try {
            Log.d(TAG, "Starting update with type=$updateType")
            appUpdateManager.startUpdateFlowForResult(
                info,
                activity,
                AppUpdateOptions.defaultOptions(updateType),
                UPDATE_REQUEST_CODE
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start update flow", e)
        }
    }

    fun dismissUpdate() {
        _updateAvailable.value = false
    }

    companion object {
        private const val TAG = "InAppUpdateManager"
        const val UPDATE_REQUEST_CODE = 1001
    }
}
