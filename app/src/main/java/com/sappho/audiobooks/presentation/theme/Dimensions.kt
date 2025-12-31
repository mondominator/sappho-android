package com.sappho.audiobooks.presentation.theme

import androidx.compose.ui.unit.dp

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
