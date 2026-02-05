package com.sappho.audiobooks.presentation.theme

import androidx.compose.ui.unit.dp

/**
 * Timing constants for animations, delays, and intervals.
 * Use semantic names for consistent behavior.
 */
object Timing {
    // Animation durations
    /** 100ms - Micro interactions, button press feedback */
    const val ANIMATION_FAST_MS = 100
    /** 200ms - Standard transitions, fade effects */
    const val ANIMATION_STANDARD_MS = 200
    /** 300ms - Slide transitions, expanding content */
    const val ANIMATION_MEDIUM_MS = 300
    /** 600ms - Emphasis animations, staggered reveals */
    const val ANIMATION_SLOW_MS = 600

    // Debounce delays
    /** 200ms - Search input debounce */
    const val DEBOUNCE_SEARCH_MS = 200L
    /** 500ms - Layout measurement delay */
    const val DEBOUNCE_LAYOUT_MS = 500L

    // Polling/sync intervals
    /** 1000ms - Position updates, progress polling */
    const val POLL_INTERVAL_MS = 1000L
    /** 10000ms - Background sync interval */
    const val SYNC_INTERVAL_MS = 10000L

    // UI feedback delays
    /** 1500ms - Toast/snackbar duration, marquee pause */
    const val FEEDBACK_SHORT_MS = 1500L
    /** 2500ms - Scanning animation, loading states */
    const val FEEDBACK_MEDIUM_MS = 2500L
    /** 3000ms - Success message display */
    const val FEEDBACK_LONG_MS = 3000L
    /** 5000ms - Extended operations feedback */
    const val FEEDBACK_EXTENDED_MS = 5000L
}

/**
 * Playback-related constants.
 */
object PlaybackDefaults {
    /** Default skip forward/backward duration in seconds */
    const val SKIP_DURATION_SECONDS = 10
    /** Skip duration in milliseconds */
    const val SKIP_DURATION_MS = SKIP_DURATION_SECONDS * 1000L
}

/**
 * Standardized spacing scale based on 4dp base unit.
 * Use semantic names for consistent visual rhythm.
 */
object Spacing {
    /** 4dp - Tight grouping, inline elements */
    val XXS = 4.dp

    /** 8dp - Related elements, small gaps */
    val XS = 8.dp

    /** 12dp - Group separation, list items */
    val S = 12.dp

    /** 16dp - Section padding, card padding */
    val M = 16.dp

    /** 24dp - Section separation, major gaps */
    val L = 24.dp

    /** 32dp - Major section breaks */
    val XL = 32.dp

    /** 48dp - Hero elements, major separations */
    val XXL = 48.dp
}

/**
 * Standardized icon sizes for visual consistency.
 * Use appropriate size based on context.
 */
object IconSize {
    /** 16dp - Inline text, badges, chips */
    val Small = 16.dp

    /** 20dp - Secondary actions, dense UI */
    val Medium = 20.dp

    /** 24dp - Standard actions, navigation icons */
    val Standard = 24.dp

    /** 32dp - Primary actions, feature highlights */
    val Large = 32.dp

    /** 48dp - Empty states, hero elements */
    val Hero = 48.dp

    /** 64dp - Large empty states, onboarding */
    val XLarge = 64.dp
}
