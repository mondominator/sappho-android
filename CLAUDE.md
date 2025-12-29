# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

### Building
```bash
# Debug build (Android)
flutter build apk --debug

# Debug build (iOS - simulator)
flutter build ios --debug --simulator

# Release build (Android)
flutter build apk --release

# Release build (iOS)
flutter build ios --release
```

### Running on Device/Emulator
```bash
# List available devices
flutter devices

# Run on connected device (auto-selects)
flutter run

# Run on specific device
flutter run -d <device-id>

# Install Android APK
/Users/mondo/Library/Android/sdk/platform-tools/adb install -r build/app/outputs/flutter-apk/app-debug.apk

# Install iOS on simulator
xcrun simctl install <device-id> build/ios/iphonesimulator/Runner.app

# Check Android logs
/Users/mondo/Library/Android/sdk/platform-tools/adb logcat -d | grep -i flutter
```

**IMPORTANT**: After completing code changes, deploy to BOTH devices for testing:
1. Android phone: `flutter run -d RFCX907G0WE` (or check `flutter devices` for current ID)
2. iOS simulator: `flutter run -d D6B6BBBD-19EF-4C0B-8D3A-FB4C982CB0E7` (iPhone 17 Pro)

You can run both in parallel by launching one with `flutter run -d <id1>` and then in another terminal running `flutter run -d <id2>` after the first build completes.

## Architecture Overview

### Project Structure
```
lib/
├── main.dart              # App entry point, provider setup
├── models/                # Data models (Audiobook, User, etc.)
├── providers/             # State management (ChangeNotifier providers)
├── screens/               # UI screens
│   ├── home/             # Home feed (Continue Listening, Recently Added)
│   ├── library/          # Library, series, author, genre views
│   ├── detail/           # Audiobook detail screen
│   ├── player/           # Audio player screen
│   ├── search/           # Search functionality
│   ├── profile/          # User profile and stats
│   ├── login/            # Authentication flow
│   └── main/             # Bottom navigation container
├── services/             # API and audio services
│   ├── api_service.dart  # HTTP API client
│   ├── auth_repository.dart  # Secure token storage
│   └── audio_handler.dart    # Background audio & Android Auto
└── theme/                # App theme and colors
    ├── app_theme.dart
    └── colors.dart
```

### State Management (Provider)
The app uses the Provider package for state management:
- `AuthProvider` - Authentication state
- `HomeProvider` - Home screen data
- `LibraryProvider` - Library browsing
- `PlayerProvider` - Audio playback state
- `DetailProvider` - Book detail data
- `SearchProvider` - Search functionality
- `ProfileProvider` - User profile and stats
- `DownloadProvider` - Offline downloads
- `ConnectivityProvider` - Network status

### Dynamic Server URL
Users enter their server URL at login. The URL and auth token are stored securely:
- `AuthRepository` uses `flutter_secure_storage` for secure persistence
- `ApiService` reads credentials from `AuthRepository` for all requests
- Cover images use authenticated URLs: `$serverUrl/api/audiobooks/$id/cover`

### JSON Serialization
The server uses **snake_case**, Dart models use **camelCase**. Models use `@JsonKey` annotations:
```dart
@JsonSerializable()
class Audiobook {
  final int id;
  @JsonKey(name: 'cover_image') final String? coverImage;
  @JsonKey(name: 'series_position') final double? seriesPosition;
  // ...
}
```

### Audio Playback
- `just_audio` for audio playback
- `audio_service` for background playback and media notifications
- `SapphoAudioHandler` integrates both for Android Auto support
- Playback progress syncs to server on pause

## API Endpoints

**Metadata endpoints**:
- `GET /api/audiobooks/meta/in-progress?limit=10`
- `GET /api/audiobooks/meta/recent?limit=10`
- `GET /api/audiobooks/meta/finished?limit=10`

**Cover images** (authenticated):
- `GET /api/audiobooks/{id}/cover`

**Authentication**:
- `POST /api/auth/login` - Returns `{ token, user }`

## UI Theme

The app matches the Sappho web app's dark theme:
- Background: `#0A0E1A` (`sapphoBackground`)
- Surface: `#1a1a1a` (`sapphoSurface`)
- Primary (blue): `#3B82F6` (`sapphoPrimary`)
- Text: `#E0E7F1` (light), `#9ca3af` (muted)

Colors are defined in `lib/theme/colors.dart`. Always use theme colors, never hardcode.

## PWA Reference

The Sappho PWA at `/Users/mondo/Documents/git/sappho/client` is the design reference:
- Theme colors match the PWA's Tailwind configuration
- UI aims to replicate the PWA experience
- Both consume the same server API

## Code Quality Principles

### General
- Write clean, readable code
- Follow existing patterns
- Avoid duplication - extract shared widgets
- Handle errors gracefully with user feedback
- Consider edge cases (null, empty, network failures)

### Flutter Specific
- Use theme colors from `colors.dart`, never hardcode
- Use `const` constructors where possible
- Prefer `StatelessWidget` unless state is needed
- Use `Consumer`/`Selector` for efficient rebuilds
- Keep widgets small and focused

### What to Avoid
- Hardcoded colors or magic numbers
- God widgets that do everything
- Ignoring compiler warnings
- Commented-out code

## PR Workflow

- Always work on a new branch and merge via PR
- Wait for CI pipelines before merging
- Wait for user testing before shipping
- Never bypass merge rules

## Remote Server Access (Unraid)

The production Sappho server runs on Unraid at `ssh root@192.168.86.151`.

**IMPORTANT: Observe only, do not modify.**
- You may SSH to **read logs** and **check container status**
- **DO NOT** stop, start, restart, or modify containers

Allowed:
```bash
ssh root@192.168.86.151 "docker logs sappho --tail 100"
ssh root@192.168.86.151 "docker ps | grep sappho"
```

## Release Checklist

Before creating a new release, update version in `pubspec.yaml`:
```yaml
version: 1.0.0+1  # version_name+version_code
```

## Testing Server Connectivity

```bash
# Check server is reachable
curl -I https://sapho.bitstorm.ca/api/audiobooks/meta/recent?limit=1

# Test authenticated endpoint
curl -H "Authorization: Bearer <token>" https://sapho.bitstorm.ca/api/audiobooks/5557/cover
```
