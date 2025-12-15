package com.sappho.audiobooks.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Extended color scheme for Sappho-specific colors
data class SapphoColors(
    val textMuted: Color = SapphoTextMuted,
    val textLight: Color = SapphoTextLight,
    val warning: Color = SapphoWarning,
    val success: Color = SapphoSuccess,
    val info: Color = SapphoInfo,
    val surfaceLight: Color = SapphoSurfaceLight,
    val surfaceDark: Color = SapphoSurfaceDark,
    val surfaceBorder: Color = SapphoSurfaceBorder,
    val iconDefault: Color = SapphoIconDefault,
    val iconActive: Color = SapphoIconActive,
    val iconMuted: Color = SapphoIconMuted,
    val progressTrack: Color = SapphoProgressTrack,
    val progressIndicator: Color = SapphoProgressIndicator,
    val starFilled: Color = SapphoStarFilled,
    val starEmpty: Color = SapphoStarEmpty
)

val LocalSapphoColors = staticCompositionLocalOf { SapphoColors() }

private val DarkColorScheme = darkColorScheme(
    primary = SapphoPrimary,
    secondary = SapphoSecondary,
    tertiary = SapphoInfo,
    background = SapphoBackground,
    surface = SapphoSurface,
    error = SapphoError,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = SapphoText,
    onSurface = SapphoText,
    onError = Color.White,
    surfaceVariant = SapphoSurfaceLight,
    onSurfaceVariant = SapphoTextSecondary,
    outline = SapphoSurfaceBorder,
    outlineVariant = SapphoSurfaceDark
)

@Composable
fun SapphoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // Always use dark theme (Sappho is dark-themed)
    val colorScheme = DarkColorScheme
    val sapphoColors = SapphoColors()

    CompositionLocalProvider(LocalSapphoColors provides sapphoColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}

// Extension property to access Sappho colors from MaterialTheme
val MaterialTheme.sapphoColors: SapphoColors
    @Composable
    get() = LocalSapphoColors.current
