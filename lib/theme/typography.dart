import 'package:flutter/material.dart';

// =============================================================================
// SAPPHO TYPOGRAPHY SYSTEM
// Exact match of Android app's Type.kt
// =============================================================================

const TextTheme sapphoTextTheme = TextTheme(
  // -------------------------------------------------------------------------
  // Display - Large hero text (profile avatars, player timestamps)
  // -------------------------------------------------------------------------
  displayLarge: TextStyle(
    fontWeight: FontWeight.bold,
    fontSize: 48,
    height: 56 / 48,
    letterSpacing: -0.25,
  ),
  displayMedium: TextStyle(
    fontWeight: FontWeight.bold,
    fontSize: 40,
    height: 48 / 40,
    letterSpacing: 0,
  ),
  displaySmall: TextStyle(
    fontWeight: FontWeight.bold,
    fontSize: 32,
    height: 40 / 32,
    letterSpacing: 0,
  ),

  // -------------------------------------------------------------------------
  // Headline - Section headers, page titles
  // -------------------------------------------------------------------------
  headlineLarge: TextStyle(
    fontWeight: FontWeight.bold,
    fontSize: 24,
    height: 32 / 24,
    letterSpacing: 0,
  ),
  headlineMedium: TextStyle(
    fontWeight: FontWeight.bold,
    fontSize: 22,
    height: 28 / 22,
    letterSpacing: 0,
  ),
  headlineSmall: TextStyle(
    fontWeight: FontWeight.w600,
    fontSize: 20,
    height: 26 / 20,
    letterSpacing: 0,
  ),

  // -------------------------------------------------------------------------
  // Title - Card titles, list item titles
  // -------------------------------------------------------------------------
  titleLarge: TextStyle(
    fontWeight: FontWeight.w600,
    fontSize: 18,
    height: 24 / 18,
    letterSpacing: 0,
  ),
  titleMedium: TextStyle(
    fontWeight: FontWeight.w500,
    fontSize: 16,
    height: 22 / 16,
    letterSpacing: 0.15,
  ),
  titleSmall: TextStyle(
    fontWeight: FontWeight.w500,
    fontSize: 15,
    height: 20 / 15,
    letterSpacing: 0.1,
  ),

  // -------------------------------------------------------------------------
  // Body - Main content text
  // -------------------------------------------------------------------------
  bodyLarge: TextStyle(
    fontWeight: FontWeight.normal,
    fontSize: 16,
    height: 24 / 16,
    letterSpacing: 0.5,
  ),
  bodyMedium: TextStyle(
    fontWeight: FontWeight.normal,
    fontSize: 14,
    height: 20 / 14,
    letterSpacing: 0.25,
  ),
  bodySmall: TextStyle(
    fontWeight: FontWeight.normal,
    fontSize: 13,
    height: 18 / 13,
    letterSpacing: 0.4,
  ),

  // -------------------------------------------------------------------------
  // Label - Metadata, timestamps, buttons
  // -------------------------------------------------------------------------
  labelLarge: TextStyle(
    fontWeight: FontWeight.w500,
    fontSize: 14,
    height: 20 / 14,
    letterSpacing: 0.1,
  ),
  labelMedium: TextStyle(
    fontWeight: FontWeight.w500,
    fontSize: 12,
    height: 16 / 12,
    letterSpacing: 0.5,
  ),
  labelSmall: TextStyle(
    fontWeight: FontWeight.w500,
    fontSize: 11,
    height: 14 / 11,
    letterSpacing: 0.5,
  ),
);

// =============================================================================
// TYPOGRAPHY USAGE GUIDE
// =============================================================================
// Instead of inline fontSize/fontWeight, use Theme.of(context).textTheme:
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
