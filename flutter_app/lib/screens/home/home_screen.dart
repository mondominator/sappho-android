import 'package:flutter/material.dart';
import '../../theme/app_theme.dart';

/// Home screen - placeholder until full implementation
/// Will be replaced with exact match of Android HomeScreen.kt
class HomeScreen extends StatelessWidget {
  const HomeScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: sapphoBackground,
      appBar: AppBar(
        title: const Text('Home'),
        backgroundColor: sapphoSurface,
      ),
      body: const Center(
        child: Text(
          'Home screen - implementation in progress',
          style: TextStyle(color: sapphoTextSecondary),
        ),
      ),
    );
  }
}
