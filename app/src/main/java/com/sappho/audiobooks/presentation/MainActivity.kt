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
import com.sappho.audiobooks.presentation.components.UpdateDialog
import com.sappho.audiobooks.presentation.theme.SapphoTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var inAppUpdateManager: InAppUpdateManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check for app updates
        lifecycleScope.launch {
            inAppUpdateManager.checkForUpdate()
        }

        // Get navigation extras from intent (e.g., from PlayerActivity)
        val navigateTo = intent.getStringExtra("NAVIGATE_TO")
        val author = intent.getStringExtra("AUTHOR")
        val series = intent.getStringExtra("SERIES")

        setContent {
            SapphoTheme {
                val updateAvailable by inAppUpdateManager.updateAvailable.collectAsState()

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SapphoApp(
                        initialAuthor = if (navigateTo == "library") author else null,
                        initialSeries = if (navigateTo == "library") series else null
                    )

                    if (updateAvailable) {
                        UpdateDialog(
                            onUpdateClick = {
                                inAppUpdateManager.startUpdate(this@MainActivity)
                            },
                            onDismiss = {
                                inAppUpdateManager.dismissUpdate()
                            }
                        )
                    }
                }
            }
        }
    }
}
