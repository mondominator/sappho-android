package com.sappho.audiobooks.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.sappho.audiobooks.data.update.InAppUpdateManager
import com.sappho.audiobooks.data.update.UpdateState
import com.sappho.audiobooks.presentation.components.UpdateAvailableDialog
import com.sappho.audiobooks.presentation.components.UpdateDownloadingDialog
import com.sappho.audiobooks.presentation.components.UpdateReadyDialog
import com.sappho.audiobooks.presentation.components.UpdateFailedDialog
import com.sappho.audiobooks.presentation.theme.SapphoTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var inAppUpdateManager: InAppUpdateManager

    override fun onResume() {
        super.onResume()
        // Check for app updates every time app comes to foreground
        lifecycleScope.launch {
            inAppUpdateManager.checkForUpdate()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Get navigation extras from intent (e.g., from PlayerActivity)
        val navigateTo = intent.getStringExtra("NAVIGATE_TO")
        val author = intent.getStringExtra("AUTHOR")
        val series = intent.getStringExtra("SERIES")

        setContent {
            SapphoTheme {
                val updateState by inAppUpdateManager.updateState.collectAsState()

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SapphoApp(
                        initialAuthor = if (navigateTo == "library") author else null,
                        initialSeries = if (navigateTo == "library") series else null
                    )

                    // Show appropriate dialog based on update state
                    when (val state = updateState) {
                        is UpdateState.Available -> {
                            UpdateAvailableDialog(
                                onUpdateClick = {
                                    inAppUpdateManager.startUpdate(this@MainActivity)
                                },
                                onDismiss = {
                                    inAppUpdateManager.dismissUpdate()
                                }
                            )
                        }
                        is UpdateState.Downloading -> {
                            UpdateDownloadingDialog(progress = state.progress)
                        }
                        is UpdateState.ReadyToInstall -> {
                            UpdateReadyDialog(
                                onRestartClick = {
                                    inAppUpdateManager.completeUpdate()
                                },
                                onDismiss = {
                                    inAppUpdateManager.dismissUpdate()
                                }
                            )
                        }
                        is UpdateState.Failed -> {
                            UpdateFailedDialog(
                                message = state.message,
                                onRetryClick = {
                                    inAppUpdateManager.clearError()
                                    inAppUpdateManager.startUpdate(this@MainActivity)
                                },
                                onDismiss = {
                                    inAppUpdateManager.clearError()
                                }
                            )
                        }
                        else -> { /* No dialog for None or Installing states */ }
                    }
                }
            }
        }
    }
}
