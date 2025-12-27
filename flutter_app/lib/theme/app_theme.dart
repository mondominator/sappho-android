import 'package:flutter/material.dart';
import 'colors.dart';
import 'typography.dart';

// Re-export colors and typography for convenience
export 'colors.dart';
export 'typography.dart';

/// Sappho app theme - matches Android app exactly
ThemeData sapphoTheme = ThemeData(
  useMaterial3: true,
  brightness: Brightness.dark,
  scaffoldBackgroundColor: sapphoBackground,

  // Color scheme
  colorScheme: const ColorScheme.dark(
    primary: sapphoInfo,
    secondary: legacyBlueLight,
    surface: sapphoSurface,
    error: sapphoError,
    onPrimary: Colors.white,
    onSecondary: Colors.white,
    onSurface: sapphoText,
    onError: Colors.white,
  ),

  // Typography
  textTheme: sapphoTextTheme.apply(
    bodyColor: sapphoText,
    displayColor: sapphoText,
  ),

  // AppBar
  appBarTheme: const AppBarTheme(
    backgroundColor: sapphoSurface,
    foregroundColor: sapphoText,
    elevation: 0,
    surfaceTintColor: Colors.transparent,
  ),

  // Cards
  cardTheme: CardThemeData(
    color: sapphoSurfaceLight,
    shape: RoundedRectangleBorder(
      borderRadius: BorderRadius.circular(12),
    ),
    elevation: 0,
  ),

  // Elevated buttons
  elevatedButtonTheme: ElevatedButtonThemeData(
    style: ElevatedButton.styleFrom(
      backgroundColor: sapphoInfo,
      foregroundColor: Colors.white,
      disabledBackgroundColor: sapphoProgressTrack,
      disabledForegroundColor: sapphoTextMuted,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(8),
      ),
      padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 12),
    ),
  ),

  // Text buttons
  textButtonTheme: TextButtonThemeData(
    style: TextButton.styleFrom(
      foregroundColor: sapphoInfo,
    ),
  ),

  // Outlined text fields
  inputDecorationTheme: InputDecorationTheme(
    filled: false,
    fillColor: Colors.transparent,
    contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 16),
    border: OutlineInputBorder(
      borderRadius: BorderRadius.circular(8),
      borderSide: const BorderSide(color: sapphoProgressTrack),
    ),
    enabledBorder: OutlineInputBorder(
      borderRadius: BorderRadius.circular(8),
      borderSide: const BorderSide(color: sapphoProgressTrack),
    ),
    focusedBorder: OutlineInputBorder(
      borderRadius: BorderRadius.circular(8),
      borderSide: const BorderSide(color: sapphoInfo),
    ),
    errorBorder: OutlineInputBorder(
      borderRadius: BorderRadius.circular(8),
      borderSide: const BorderSide(color: sapphoError),
    ),
    labelStyle: const TextStyle(color: sapphoTextLight),
    hintStyle: const TextStyle(color: sapphoTextMuted),
    prefixIconColor: sapphoIconDefault,
    suffixIconColor: sapphoIconDefault,
  ),

  // Bottom navigation
  bottomNavigationBarTheme: const BottomNavigationBarThemeData(
    backgroundColor: sapphoSurface,
    selectedItemColor: sapphoInfo,
    unselectedItemColor: sapphoTextMuted,
  ),

  // Navigation bar (Material 3)
  navigationBarTheme: NavigationBarThemeData(
    backgroundColor: sapphoSurface,
    indicatorColor: sapphoInfo.withValues(alpha: 0.2),
    iconTheme: WidgetStateProperty.resolveWith((states) {
      if (states.contains(WidgetState.selected)) {
        return const IconThemeData(color: sapphoInfo);
      }
      return const IconThemeData(color: sapphoIconDefault);
    }),
    labelTextStyle: WidgetStateProperty.resolveWith((states) {
      if (states.contains(WidgetState.selected)) {
        return const TextStyle(color: sapphoInfo, fontSize: 12);
      }
      return const TextStyle(color: sapphoIconDefault, fontSize: 12);
    }),
  ),

  // Slider
  sliderTheme: const SliderThemeData(
    activeTrackColor: sapphoInfo,
    inactiveTrackColor: sapphoProgressTrack,
    thumbColor: sapphoInfo,
    overlayColor: Color(0x293B82F6),
    trackHeight: 4,
  ),

  // Progress indicator
  progressIndicatorTheme: const ProgressIndicatorThemeData(
    color: sapphoInfo,
    linearTrackColor: sapphoProgressTrack,
  ),

  // Dialogs
  dialogTheme: DialogThemeData(
    backgroundColor: sapphoSurface,
    shape: RoundedRectangleBorder(
      borderRadius: BorderRadius.circular(16),
    ),
  ),

  // Dropdown menu
  dropdownMenuTheme: DropdownMenuThemeData(
    menuStyle: MenuStyle(
      backgroundColor: WidgetStateProperty.all(sapphoSurface),
      shape: WidgetStateProperty.all(
        RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(8),
          side: const BorderSide(color: sapphoSurfaceBorder),
        ),
      ),
    ),
  ),

  // Icon theme
  iconTheme: const IconThemeData(
    color: sapphoIconDefault,
  ),

  // Divider
  dividerTheme: const DividerThemeData(
    color: sapphoSurfaceBorder,
    thickness: 1,
  ),

  // Snackbar
  snackBarTheme: SnackBarThemeData(
    backgroundColor: sapphoSurfaceLight,
    contentTextStyle: const TextStyle(color: sapphoText),
    shape: RoundedRectangleBorder(
      borderRadius: BorderRadius.circular(8),
    ),
    behavior: SnackBarBehavior.floating,
  ),
);
