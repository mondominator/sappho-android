# Sappho Android App

A native Android audiobook player app built with Kotlin and Jetpack Compose that connects to the Sappho audiobook server.

> **Disclaimer**: This app was vibe coded with [Claude Code](https://claude.com/claude-code). It may contain bugs, incomplete features, or unconventional implementations. Use at your own risk and feel free to contribute improvements!

## Features

### Core Functionality
- **Authentication** - Login with dynamic server URL configuration
- **Home Screen** - Continue Listening, Recently Added, and Listen Again sections
- **Library Browser** - Browse by Series, Authors, Genres, or All Books
- **Audiobook Details** - View book info, chapters, and series information
- **Audio Player** - Full-featured player with background playback support
- **Search** - Search across your audiobook library
- **Profile Management** - Update avatar, display name, email, and password
- **Catch Me Up** - AI-powered series recaps summarizing previous books in a series

### Playback Features
- Background audio playback with MediaSessionService
- Media notification controls
- Chapter navigation
- Playback speed control
- Sleep timer
- Progress sync with server

### Platform Integration
- **Chromecast Support** - Cast audiobooks to compatible devices
- **Android Auto** - Browse and play audiobooks while driving
- **Offline Downloads** - Download books for offline listening

### Administration
- **Library Scanning** - Trigger library scans and force rescans from the app
- **AI Configuration** - Configure OpenAI or Google Gemini API keys for series recaps
- **User Management** - Create and delete users directly from the mobile app

### UI/UX
- Dark theme matching Sappho web app
- Responsive layouts with FlowRow for overflow handling
- Cover art loading with authentication
- Pull-to-refresh on home screen

## Tech Stack

- **Language**: Kotlin
- **UI**: Jetpack Compose with Material 3
- **Architecture**: MVVM + Clean Architecture
- **DI**: Hilt
- **Networking**: Retrofit + OkHttp
- **Media**: Media3 (ExoPlayer)
- **Image Loading**: Coil
- **Security**: EncryptedSharedPreferences for token storage

## Project Structure

```
app/src/main/java/com/sappho/audiobooks/
├── data/
│   ├── remote/          # API service interfaces
│   └── repository/      # Data repositories
├── domain/
│   └── model/           # Domain models
├── presentation/
│   ├── login/           # Login screen
│   ├── home/            # Home screen
│   ├── library/         # Library browser
│   ├── detail/          # Audiobook details
│   ├── player/          # Audio player
│   ├── search/          # Search screen
│   ├── profile/         # Profile management
│   ├── settings/        # App settings
│   └── theme/           # Compose theme
├── service/             # Audio playback service
├── cast/                # Chromecast integration
├── download/            # Download manager
└── di/                  # Hilt modules
```

## Setup

### Prerequisites
- Android Studio Hedgehog or later
- JDK 17
- Android SDK API 34
- A running Sappho server instance

### Building
```bash
# Set Java home
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"

# Debug build
./gradlew assembleDebug

# Release build
./gradlew assembleRelease
```

### Installation
1. Build the APK or download from [Releases](../../releases)
2. Install on your Android device (API 26+)
3. Enter your Sappho server URL (e.g., `https://your-server.com` or `http://192.168.1.100:3002`)
4. Login with your Sappho credentials

## Security Notes

- Auth tokens are stored securely using Android's EncryptedSharedPreferences
- Cleartext traffic is enabled to support local HTTP servers during development
- For production use, configure your Sappho server with HTTPS
- Tokens are passed via URL query parameters for media streaming (required for ExoPlayer/Cast SDK compatibility)

## CI/CD

This project uses GitHub Actions for automated builds:
- Builds on every push to `main`
- Creates releases with APKs when version tags are pushed (`v*`)
- Manual workflow dispatch for on-demand builds

To create a release:
```bash
git tag v1.0.0
git push origin v1.0.0
```

## API Endpoints Used

The app uses these Sappho API endpoints:
- `POST /api/auth/login` - User authentication
- `GET /api/audiobooks/meta/in-progress` - Continue listening
- `GET /api/audiobooks/meta/recent` - Recently added books
- `GET /api/audiobooks/meta/finished` - Listen again
- `GET /api/audiobooks` - All audiobooks
- `GET /api/audiobooks/{id}` - Audiobook details
- `GET /api/audiobooks/{id}/cover` - Cover images
- `GET /api/audiobooks/{id}/stream` - Audio streaming
- `GET /api/series` - Series list
- `GET /api/authors` - Authors list
- `GET /api/genres` - Genres list
- `GET /api/profile` - User profile
- `PUT /api/profile` - Update profile
- `PUT /api/profile/password` - Change password
- `POST /api/progress/{id}` - Update playback progress
- `GET /api/series/{name}/recap` - Get AI-powered series recap
- `GET /api/settings/ai` - Get AI configuration
- `PUT /api/settings/ai` - Update AI settings
- `GET /api/users` - List users (admin)
- `POST /api/users` - Create user (admin)
- `DELETE /api/users/{id}` - Delete user (admin)
- `POST /api/library/scan` - Trigger library scan (admin)
- `POST /api/library/force-rescan` - Force rescan library (admin)

## License

MIT License - See LICENSE file for details.

---

**Built with Claude Code**
