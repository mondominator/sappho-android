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

// Enhanced dark mode variants
val SapphoBackgroundDeep = Color(0xFF08111D)  // Even deeper background for OLED
val SapphoSurfaceElevated = Color(0xFF252525)  // Higher elevation surface
val SapphoSurfaceDialog = Color(0xFF2D2D2D)  // Dialog/modal background

// -----------------------------------------------------------------------------
// Text Colors (Enhanced Contrast)
// -----------------------------------------------------------------------------
val SapphoText = Color(0xFFE8F0FF)  // Primary text (improved contrast)
val SapphoTextSecondary = Color(0xFFB8C2D1)  // Secondary text (enhanced 5.5:1 contrast)
val SapphoTextMuted = Color(0xFF9CA3AF)  // Muted/disabled text (4.5:1 contrast)
val SapphoTextLight = Color(0xFFD1D5DB)  // Light gray text
val SapphoTextHigh = Color(0xFFF8FAFC)  // High contrast white text
val SapphoTextDim = Color(0xFF8B92A6)  // Dimmed text for subtle elements

// -----------------------------------------------------------------------------
// Semantic Colors (Enhanced)
// -----------------------------------------------------------------------------
val SapphoError = Color(0xFFF87171)  // Error red (softer on dark)
val SapphoErrorLight = Color(0xFFFCA5A5)  // Light error red (for text on error backgrounds)
val SapphoErrorDark = Color(0xFFDC2626)  // Darker error red for borders
val SapphoWarning = Color(0xFFFBBF24)  // Warning amber (better contrast)
val SapphoWarningDark = Color(0xFFD97706)  // Darker warning for emphasis
val SapphoSuccess = Color(0xFF34D399)  // Success emerald (brighter on dark)
val SapphoSuccessDark = Color(0xFF059669)  // Darker success for borders
val SapphoInfo = Color(0xFF60A5FA)  // Info blue (brighter on dark)
val SapphoInfoLight = Color(0xFFA5B4FC)  // Light info (indigo-ish, for loading states)
val SapphoInfoDark = Color(0xFF2563EB)  // Darker info blue

// Feature accent (used for AI features, recaps, etc.)
val SapphoAccent = Color(0xFF6366F1)  // Indigo accent
val SapphoAccentLight = Color(0xFFA5B4FC)  // Light indigo

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
    // Content categories (blue)
    val contentLight = Color(0xFF3B82F6)  // blue-500
    val contentDark = Color(0xFF2563EB)   // blue-600

    // Personal categories (teal)
    val personalLight = Color(0xFF26A69A)
    val personalDark = Color(0xFF00897B)

    // Neutral (gray)
    val neutralLight = Color(0xFF374151)
    val neutralDark = Color(0xFF1F2937)
}

// -----------------------------------------------------------------------------
// Library Gradient Palette
// Standardized gradients for genres, authors, collections, etc.
// Each gradient is a pair: [lighter, darker] for gradient effects
// -----------------------------------------------------------------------------
object LibraryGradients {
    // Blue - Primary brand color
    val blue = listOf(Color(0xFF3B82F6), Color(0xFF2563EB))

    // Indigo - Mystery, Thriller
    val indigo = listOf(Color(0xFF6366F1), Color(0xFF4338CA))

    // Purple - Fantasy, Paranormal
    val purple = listOf(Color(0xFF8B5CF6), Color(0xFF6D28D9))

    // Pink - Romance
    val pink = listOf(Color(0xFFEC4899), Color(0xFFDB2777))

    // Rose - Drama, Emotional
    val rose = listOf(Color(0xFFF43F5E), Color(0xFFE11D48))

    // Orange - Adventure, Action
    val orange = listOf(Color(0xFFF97316), Color(0xFFEA580C))

    // Amber - Historical, Western
    val amber = listOf(Color(0xFFF59E0B), Color(0xFFD97706))

    // Emerald - Nature, Environment
    val emerald = listOf(Color(0xFF10B981), Color(0xFF059669))

    // Teal - Self-Help, Psychology
    val teal = listOf(Color(0xFF14B8A6), Color(0xFF0D9488))

    // Cyan - Science Fiction, Technology
    val cyan = listOf(Color(0xFF06B6D4), Color(0xFF0891B2))

    // Slate - Non-Fiction, Biography
    val slate = listOf(Color(0xFF64748B), Color(0xFF475569))

    // Stone - Classic, Literary
    val stone = listOf(Color(0xFF78716C), Color(0xFF57534E))

    // All gradients for random/hash-based selection
    // Excludes browns (stone) and magentas (pink, rose) for better readability
    val all = listOf(
        blue, indigo, purple,
        orange, amber, emerald, teal, cyan,
        slate
    )

    // Subset for avatars (more vibrant colors, no grays/browns/magentas)
    val avatars = listOf(
        blue, indigo, purple,
        orange, emerald, teal, cyan
    )

    /**
     * Get a gradient based on a string hash (for consistent color per item)
     */
    fun forString(text: String, palette: List<List<Color>> = all): List<Color> {
        val index = kotlin.math.abs(text.hashCode()) % palette.size
        return palette[index]
    }

    /**
     * Get an avatar gradient based on name
     */
    fun forAvatar(name: String): List<Color> = forString(name, avatars)
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
