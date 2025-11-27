package com.sappho.audiobooks.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = SapphoPrimary,
    secondary = SapphoSecondary,
    background = SapphoBackground,
    surface = SapphoSurface,
    error = SapphoError,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = SapphoText,
    onSurface = SapphoText,
    onError = Color.White,
    surfaceVariant = SapphoSurfaceBorder
)

@Composable
fun SapphoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // Always use dark theme (Sappho is dark-themed)
    val colorScheme = DarkColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
