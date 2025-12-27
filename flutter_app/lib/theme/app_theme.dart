import 'package:flutter/material.dart';

// Sappho color palette - matching Android app
class SapphoColors {
  // Backgrounds
  static const Color background = Color(0xFF0A0E1A);
  static const Color surface = Color(0xFF1a1a1a);
  static const Color surfaceLight = Color(0xFF262626);
  static const Color surfaceBorder = Color(0xFF333333);

  // Primary
  static const Color primary = Color(0xFF3B82F6);
  static const Color primaryDark = Color(0xFF2563EB);

  // Text
  static const Color textPrimary = Color(0xFFE0E7F1);
  static const Color textSecondary = Color(0xFF9ca3af);
  static const Color textMuted = Color(0xFF6b7280);

  // Status colors
  static const Color success = Color(0xFF22c55e);
  static const Color warning = Color(0xFFf59e0b);
  static const Color error = Color(0xFFef4444);
  static const Color info = Color(0xFF3B82F6);

  // Progress
  static const Color progressTrack = Color(0xFF374151);
  static const Color starFilled = Color(0xFFfbbf24);

  // Legacy colors for compatibility
  static const Color blueLight = Color(0xFF60a5fa);
  static const Color purpleLight = Color(0xFFa78bfa);
}

ThemeData sapphoTheme = ThemeData(
  useMaterial3: true,
  brightness: Brightness.dark,
  scaffoldBackgroundColor: SapphoColors.background,
  colorScheme: const ColorScheme.dark(
    primary: SapphoColors.primary,
    secondary: SapphoColors.blueLight,
    surface: SapphoColors.surface,
    error: SapphoColors.error,
    onPrimary: Colors.white,
    onSecondary: Colors.white,
    onSurface: SapphoColors.textPrimary,
    onError: Colors.white,
  ),
  appBarTheme: const AppBarTheme(
    backgroundColor: SapphoColors.surface,
    foregroundColor: SapphoColors.textPrimary,
    elevation: 0,
  ),
  cardTheme: CardThemeData(
    color: SapphoColors.surfaceLight,
    shape: RoundedRectangleBorder(
      borderRadius: BorderRadius.circular(12),
    ),
  ),
  elevatedButtonTheme: ElevatedButtonThemeData(
    style: ElevatedButton.styleFrom(
      backgroundColor: SapphoColors.primary,
      foregroundColor: Colors.white,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(8),
      ),
    ),
  ),
  inputDecorationTheme: InputDecorationTheme(
    filled: true,
    fillColor: SapphoColors.surfaceLight,
    border: OutlineInputBorder(
      borderRadius: BorderRadius.circular(8),
      borderSide: const BorderSide(color: SapphoColors.surfaceBorder),
    ),
    enabledBorder: OutlineInputBorder(
      borderRadius: BorderRadius.circular(8),
      borderSide: const BorderSide(color: SapphoColors.surfaceBorder),
    ),
    focusedBorder: OutlineInputBorder(
      borderRadius: BorderRadius.circular(8),
      borderSide: const BorderSide(color: SapphoColors.primary),
    ),
    labelStyle: const TextStyle(color: SapphoColors.textSecondary),
    hintStyle: const TextStyle(color: SapphoColors.textMuted),
  ),
  textTheme: const TextTheme(
    displayLarge: TextStyle(color: SapphoColors.textPrimary, fontWeight: FontWeight.bold),
    displayMedium: TextStyle(color: SapphoColors.textPrimary, fontWeight: FontWeight.bold),
    displaySmall: TextStyle(color: SapphoColors.textPrimary, fontWeight: FontWeight.bold),
    headlineLarge: TextStyle(color: SapphoColors.textPrimary, fontWeight: FontWeight.w600),
    headlineMedium: TextStyle(color: SapphoColors.textPrimary, fontWeight: FontWeight.w600),
    headlineSmall: TextStyle(color: SapphoColors.textPrimary, fontWeight: FontWeight.w600),
    titleLarge: TextStyle(color: SapphoColors.textPrimary, fontWeight: FontWeight.w500),
    titleMedium: TextStyle(color: SapphoColors.textPrimary, fontWeight: FontWeight.w500),
    titleSmall: TextStyle(color: SapphoColors.textSecondary),
    bodyLarge: TextStyle(color: SapphoColors.textPrimary),
    bodyMedium: TextStyle(color: SapphoColors.textPrimary),
    bodySmall: TextStyle(color: SapphoColors.textSecondary),
    labelLarge: TextStyle(color: SapphoColors.textPrimary),
    labelMedium: TextStyle(color: SapphoColors.textSecondary),
    labelSmall: TextStyle(color: SapphoColors.textMuted),
  ),
  bottomNavigationBarTheme: const BottomNavigationBarThemeData(
    backgroundColor: SapphoColors.surface,
    selectedItemColor: SapphoColors.primary,
    unselectedItemColor: SapphoColors.textMuted,
  ),
  sliderTheme: const SliderThemeData(
    activeTrackColor: SapphoColors.primary,
    inactiveTrackColor: SapphoColors.progressTrack,
    thumbColor: SapphoColors.primary,
    overlayColor: Color(0x293B82F6),
  ),
);
