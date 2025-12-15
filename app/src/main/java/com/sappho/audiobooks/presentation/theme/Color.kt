package com.sappho.audiobooks.presentation.theme

import androidx.compose.ui.graphics.Color

// =============================================================================
// SAPPHO COLOR SYSTEM
// =============================================================================

// -----------------------------------------------------------------------------
// Core Brand Colors
// -----------------------------------------------------------------------------
val SapphoPrimary = Color(0xFF5BC0DE)  // Sky blue (matches logo)
val SapphoPrimaryDark = Color(0xFF3498DB)  // Darker sky blue for gradients
val SapphoSecondary = Color(0xFF26A69A)  // Teal accent (personal items)
val SapphoSecondaryDark = Color(0xFF00897B)  // Darker teal for gradients

// -----------------------------------------------------------------------------
// Background & Surface
// -----------------------------------------------------------------------------
val SapphoBackground = Color(0xFF0A0E1A)  // Dark blue-tinted background
val SapphoSurface = Color(0xFF1A1A1A)  // Card/dialog background
val SapphoSurfaceLight = Color(0xFF1E293B)  // Elevated surface
val SapphoSurfaceBorder = Color(0xFF3A3A3A)  // Card border (improved contrast)
val SapphoSurfaceDark = Color(0xFF1F2937)  // Darker surface variant

// -----------------------------------------------------------------------------
// Text Colors
// -----------------------------------------------------------------------------
val SapphoText = Color(0xFFE0E7F1)  // Primary text
val SapphoTextSecondary = Color(0xFFB0B7BF)  // Secondary text (5.5:1 contrast)
val SapphoTextMuted = Color(0xFF6B7280)  // Muted/disabled text
val SapphoTextLight = Color(0xFFD1D5DB)  // Light gray text

// -----------------------------------------------------------------------------
// Semantic Colors
// -----------------------------------------------------------------------------
val SapphoError = Color(0xFFEF4444)  // Error red
val SapphoWarning = Color(0xFFFB923C)  // Warning orange
val SapphoSuccess = Color(0xFF10B981)  // Success emerald
val SapphoInfo = Color(0xFF3B82F6)  // Info blue

// -----------------------------------------------------------------------------
// Icon Colors
// -----------------------------------------------------------------------------
val SapphoIconDefault = Color(0xFF9CA3AF)  // Default icon color
val SapphoIconActive = Color(0xFF3B82F6)  // Active/selected icon
val SapphoIconMuted = Color(0xFF6B7280)  // Muted icon

// -----------------------------------------------------------------------------
// Category Colors (Library)
// -----------------------------------------------------------------------------
object CategoryColors {
    // Content categories (sky blue)
    val contentLight = Color(0xFF5BC0DE)
    val contentDark = Color(0xFF3498DB)

    // Personal categories (teal)
    val personalLight = Color(0xFF26A69A)
    val personalDark = Color(0xFF00897B)

    // Neutral (gray)
    val neutralLight = Color(0xFF374151)
    val neutralDark = Color(0xFF1F2937)
}

// -----------------------------------------------------------------------------
// Progress & Status
// -----------------------------------------------------------------------------
val SapphoProgressTrack = Color(0xFF374151)  // Progress bar track
val SapphoProgressIndicator = Color(0xFF3B82F6)  // Progress bar fill
val SapphoStarFilled = Color(0xFFFBBF24)  // Star rating filled
val SapphoStarEmpty = Color(0xFF374151)  // Star rating empty

// -----------------------------------------------------------------------------
// Legacy mappings (for gradual migration)
// These match the old hardcoded values for compatibility
// -----------------------------------------------------------------------------
val LegacyBlue = Color(0xFF3B82F6)
val LegacyBlueDark = Color(0xFF1D4ED8)  // Shadow/darker blue
val LegacyBlueLight = Color(0xFF60A5FA)  // Lighter blue for highlights
val LegacyBluePale = Color(0xFF93C5FD)  // Very light blue
val LegacyGray = Color(0xFF9CA3AF)
val LegacyDarkGray = Color(0xFF374151)
val LegacySlate = Color(0xFF1E293B)
val LegacyGrayDark = Color(0xFF4B5563)  // Darker gray for muted icons
val LegacyGreenLight = Color(0xFF34D399)  // Light success green
val LegacyGreenPale = Color(0xFF6EE7B7)  // Very light green
val LegacyGreen = Color(0xFF22C55E)  // Alternative success green
val LegacyRedLight = Color(0xFFF87171)  // Light error red
val LegacyPurple = Color(0xFF8B5CF6)  // Purple/violet
val LegacyPurpleLight = Color(0xFFA78BFA)  // Light purple
val LegacyOrange = Color(0xFFF97316)  // Audible orange
val LegacyWhite = Color(0xFFE5E7EB)  // Off-white text
