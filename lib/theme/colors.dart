import 'package:flutter/material.dart';

// =============================================================================
// SAPPHO COLOR SYSTEM
// Exact match of Android app's Color.kt
// =============================================================================

// -----------------------------------------------------------------------------
// Core Brand Colors
// -----------------------------------------------------------------------------
const Color sapphoPrimary = Color(0xFF5BC0DE); // Sky blue (matches logo)
const Color sapphoPrimaryDark = Color(
  0xFF3498DB,
); // Darker sky blue for gradients
const Color sapphoSecondary = Color(0xFF26A69A); // Teal accent (personal items)
const Color sapphoSecondaryDark = Color(
  0xFF00897B,
); // Darker teal for gradients

// -----------------------------------------------------------------------------
// Background & Surface
// -----------------------------------------------------------------------------
const Color sapphoBackground = Color(0xFF0A0E1A); // Dark blue-tinted background
const Color sapphoSurface = Color(0xFF1A1A1A); // Card/dialog background
const Color sapphoSurfaceLight = Color(0xFF1E293B); // Elevated surface
const Color sapphoSurfaceBorder = Color(
  0xFF3A3A3A,
); // Card border (improved contrast)
const Color sapphoSurfaceDark = Color(0xFF1F2937); // Darker surface variant

// -----------------------------------------------------------------------------
// Text Colors
// -----------------------------------------------------------------------------
const Color sapphoText = Color(0xFFE0E7F1); // Primary text
const Color sapphoTextSecondary = Color(
  0xFFB0B7BF,
); // Secondary text (5.5:1 contrast)
const Color sapphoTextMuted = Color(
  0xFF9CA3AF,
); // Muted/disabled text (4.5:1 contrast)
const Color sapphoTextLight = Color(0xFFD1D5DB); // Light gray text

// -----------------------------------------------------------------------------
// Semantic Colors
// -----------------------------------------------------------------------------
const Color sapphoError = Color(0xFFEF4444); // Error red
const Color sapphoErrorLight = Color(0xFFFCA5A5); // Light error red
const Color sapphoWarning = Color(0xFFFB923C); // Warning orange
const Color sapphoSuccess = Color(0xFF10B981); // Success emerald
const Color sapphoInfo = Color(0xFF3B82F6); // Info blue
const Color sapphoInfoLight = Color(0xFFA5B4FC); // Light info (indigo-ish)

// Feature accent (used for AI features, recaps, etc.)
const Color sapphoAccent = Color(0xFF6366F1); // Indigo accent
const Color sapphoAccentLight = Color(0xFFA5B4FC); // Light indigo
const Color sapphoFeatureAccent =
    sapphoAccent; // Alias for AI/Catch Me Up features

// -----------------------------------------------------------------------------
// Icon Colors
// -----------------------------------------------------------------------------
const Color sapphoIconDefault = Color(0xFF9CA3AF); // Default icon color
const Color sapphoIconActive = Color(0xFF3B82F6); // Active/selected icon
const Color sapphoIconMuted = Color(0xFF6B7280); // Muted icon

// -----------------------------------------------------------------------------
// Progress & Status
// -----------------------------------------------------------------------------
const Color sapphoProgressTrack = Color(0xFF374151); // Progress bar track
const Color sapphoProgressIndicator = Color(0xFF3B82F6); // Progress bar fill
const Color sapphoStarFilled = Color(0xFFFBBF24); // Star rating filled
const Color sapphoStarEmpty = Color(0xFF374151); // Star rating empty

// -----------------------------------------------------------------------------
// Legacy mappings (for gradual migration)
// -----------------------------------------------------------------------------
const Color legacyBlue = Color(0xFF3B82F6);
const Color legacyBlueDark = Color(0xFF1D4ED8);
const Color legacyBlueLight = Color(0xFF60A5FA);
const Color legacyBluePale = Color(0xFF93C5FD);
const Color legacyGray = Color(0xFF9CA3AF);
const Color legacyDarkGray = Color(0xFF374151);
const Color legacySlate = Color(0xFF1E293B);
const Color legacyGrayDark = Color(0xFF4B5563);
const Color legacyGreenLight = Color(0xFF34D399);
const Color legacyGreenPale = Color(0xFF6EE7B7);
const Color legacyGreen = Color(0xFF22C55E);
const Color legacyRedLight = Color(0xFFF87171);
const Color legacyPurple = Color(0xFF8B5CF6);
const Color legacyPurpleLight = Color(0xFFA78BFA);
const Color legacyOrange = Color(0xFFF97316);
const Color legacyWhite = Color(0xFFE5E7EB);

// -----------------------------------------------------------------------------
// Library Gradient Palette
// -----------------------------------------------------------------------------
class LibraryGradients {
  static const List<Color> blue = [Color(0xFF3B82F6), Color(0xFF2563EB)];
  static const List<Color> indigo = [Color(0xFF6366F1), Color(0xFF4338CA)];
  static const List<Color> purple = [Color(0xFF8B5CF6), Color(0xFF6D28D9)];
  static const List<Color> pink = [Color(0xFFEC4899), Color(0xFFDB2777)];
  static const List<Color> rose = [Color(0xFFF43F5E), Color(0xFFE11D48)];
  static const List<Color> orange = [Color(0xFFF97316), Color(0xFFEA580C)];
  static const List<Color> amber = [Color(0xFFF59E0B), Color(0xFFD97706)];
  static const List<Color> emerald = [Color(0xFF10B981), Color(0xFF059669)];
  static const List<Color> teal = [Color(0xFF14B8A6), Color(0xFF0D9488)];
  static const List<Color> cyan = [Color(0xFF06B6D4), Color(0xFF0891B2)];
  static const List<Color> slate = [Color(0xFF64748B), Color(0xFF475569)];
  static const List<Color> stone = [Color(0xFF78716C), Color(0xFF57534E)];

  static const List<List<Color>> all = [
    blue,
    indigo,
    purple,
    pink,
    rose,
    orange,
    amber,
    emerald,
    teal,
    cyan,
    slate,
    stone,
  ];

  static const List<List<Color>> avatars = [
    blue,
    indigo,
    purple,
    pink,
    orange,
    emerald,
    teal,
    cyan,
  ];

  /// Get a gradient based on a string hash (for consistent color per item)
  static List<Color> forString(String text, {List<List<Color>> palette = all}) {
    final index = text.hashCode.abs() % palette.length;
    return palette[index];
  }

  /// Get an avatar gradient based on name
  static List<Color> forAvatar(String name) =>
      forString(name, palette: avatars);
}

// -----------------------------------------------------------------------------
// Category Colors (Library)
// -----------------------------------------------------------------------------
class CategoryColors {
  // Content categories (blue)
  static const Color contentLight = Color(0xFF3B82F6);
  static const Color contentDark = Color(0xFF2563EB);

  // Personal categories (teal)
  static const Color personalLight = Color(0xFF26A69A);
  static const Color personalDark = Color(0xFF00897B);

  // Neutral (gray)
  static const Color neutralLight = Color(0xFF374151);
  static const Color neutralDark = Color(0xFF1F2937);
}

// Convenience gradient lists for library cards
const List<Color> categoryContentColors = [
  CategoryColors.contentLight,
  CategoryColors.contentDark,
];
const List<Color> categoryPersonalColors = [
  CategoryColors.personalLight,
  CategoryColors.personalDark,
];
const List<Color> categoryNeutralColors = [
  CategoryColors.neutralLight,
  CategoryColors.neutralDark,
];
