package com.sappho.audiobooks.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.sappho.audiobooks.presentation.theme.SapphoTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Get navigation extras from intent (e.g., from PlayerActivity)
        val navigateTo = intent.getStringExtra("NAVIGATE_TO")
        val author = intent.getStringExtra("AUTHOR")
        val series = intent.getStringExtra("SERIES")

        setContent {
            SapphoTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SapphoApp(
                        initialAuthor = if (navigateTo == "library") author else null,
                        initialSeries = if (navigateTo == "library") series else null
                    )
                }
            }
        }
    }
}
