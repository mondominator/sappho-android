package com.sappho.audiobooks.presentation.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// =============================================================================
// SAPPHO TYPOGRAPHY SYSTEM
// =============================================================================
// Based on Material3 Typography with customizations for the Sappho design system.
// Use these styles via MaterialTheme.typography.* instead of inline fontSize/fontWeight.

val Typography = Typography(
    // -------------------------------------------------------------------------
    // Display - Large hero text (profile avatars, player timestamps)
    // -------------------------------------------------------------------------
    displayLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 48.sp,
        lineHeight = 56.sp,
        letterSpacing = (-0.25).sp
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 40.sp,
        lineHeight = 48.sp,
        letterSpacing = 0.sp
    ),
    displaySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.sp
    ),

    // -------------------------------------------------------------------------
    // Headline - Section headers, page titles
    // -------------------------------------------------------------------------
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.sp
    ),

    // -------------------------------------------------------------------------
    // Title - Card titles, list item titles
    // -------------------------------------------------------------------------
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.15.sp
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),

    // -------------------------------------------------------------------------
    // Body - Main content text
    // -------------------------------------------------------------------------
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.4.sp
    ),

    // -------------------------------------------------------------------------
    // Label - Metadata, timestamps, buttons
    // -------------------------------------------------------------------------
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.5.sp
    )
)

// =============================================================================
// TYPOGRAPHY USAGE GUIDE
// =============================================================================
// Instead of inline fontSize/fontWeight, use MaterialTheme.typography:
//
// DISPLAY (hero text, large numbers):
//   displayLarge  (48sp) - Player timestamp, jumbo initials
//   displayMedium (40sp) - Profile avatar initials
//   displaySmall  (32sp) - Large hero numbers
//
// HEADLINE (page/section titles):
//   headlineLarge  (24sp) - Page titles, screen headers
//   headlineMedium (22sp) - Major section headers
//   headlineSmall  (20sp) - Section titles
//
// TITLE (card/item titles):
//   titleLarge  (18sp) - Dialog titles, card headers
//   titleMedium (16sp) - List item titles, button text
//   titleSmall  (15sp) - Compact list titles
//
// BODY (content text):
//   bodyLarge  (16sp) - Main body text, descriptions
//   bodyMedium (14sp) - Secondary body text
//   bodySmall  (13sp) - Tertiary text, compact descriptions
//
// LABEL (metadata, small text):
//   labelLarge  (14sp) - Button labels, emphasized metadata
//   labelMedium (12sp) - Timestamps, durations, metadata
//   labelSmall  (11sp) - Captions, hints, small badges
