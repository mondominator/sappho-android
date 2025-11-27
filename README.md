# Sappho Android App

A native Android audiobook player app built with Kotlin and Jetpack Compose that connects to the Sappho audiobook server.

## Features

### Implemented (Phase 1)
- ✅ Modern Android project structure with Gradle Kotlin DSL
- ✅ Jetpack Compose UI with Material 3
- ✅ Hilt dependency injection
- ✅ Retrofit API client for Sappho server
- ✅ Secure token storage with EncryptedSharedPreferences
- ✅ Login screen with server URL configuration
- ✅ Home screen with "Continue Listening" and "Recently Added" sections
- ✅ MVVM architecture with Clean Architecture principles
- ✅ Sappho's blue-tinted dark theme

### In Progress
- 🚧 Library screen with grid layout
- 🚧 MediaSessionService for background playback
- 🚧 Player screen with playback controls

### Planned
- ⏳ Chapter navigation
- ⏳ Offline downloads
- ⏳ Android Auto integration
- ⏳ Sleep timer
- ⏳ Playback queue
- ⏳ Home screen widgets

## Tech Stack

- **Language**: Kotlin
- **UI**: Jetpack Compose with Material 3
- **Architecture**: MVVM + Clean Architecture
- **DI**: Hilt
- **Networking**: Retrofit + OkHttp
- **Media**: Media3 (ExoPlayer) - ready to implement
- **Database**: Room (for offline storage) - ready to implement
- **Image Loading**: Coil

## Project Structure

```
app/src/main/java/com/sappho/audiobooks/
├── data/
│   ├── remote/          # API service interfaces
│   ├── local/           # Room database (TODO)
│   └── repository/      # Data repositories
├── domain/
│   └── model/           # Domain models
├── presentation/
│   ├── login/           # Login screen & ViewModel
│   ├── home/            # Home screen & ViewModel
│   ├── library/         # Library screen (TODO)
│   ├── player/          # Player screen (TODO)
│   └── theme/           # Compose theme
├── di/                  # Hilt modules
└── service/             # Background services (TODO)
```

## Setup

### Prerequisites
- Android Studio Hedgehog or later
- JDK 17
- Android SDK API 34
- A running Sappho server instance

### Building
1. Open the project in Android Studio
2. Sync Gradle dependencies
3. Run on emulator or physical device (API 26+)

### Configuration
On first launch:
1. Enter your Sappho server URL (e.g., `http://192.168.1.100:3002`)
2. Login with your Sappho credentials
3. Start browsing your audiobook library

## Development Status

This is currently in **early development**. The foundation is complete with:
- Complete project setup and configuration
- Authentication flow with secure token storage
- API integration with Sappho server
- Basic UI screens (Login, Home)
- Theme matching Sappho web app

### Next Steps
1. Implement Library screen with search and filtering
2. Build MediaSessionService for background playback
3. Create full-featured player screen
4. Add chapter navigation
5. Implement offline downloads

## API Endpoints Used

The app currently uses these Sappho API endpoints:
- `POST /api/auth/login` - User authentication
- `GET /api/audiobooks/in-progress` - Continue listening
- `GET /api/audiobooks/recently-added` - Recently added books
- `GET /api/audiobooks/{id}/cover` - Book cover images

Additional endpoints are defined in `SapphoApi.kt` for future implementation.

## Building for Release

```bash
./gradlew assembleRelease
```

The APK will be in `app/build/outputs/apk/release/`

## License

MIT License - See [LICENSE](../sappho/LICENSE) file for details.

---

**Built with** ❤️ **using Claude Code**
