# Getting Started with Sappho Android

## What We've Built

This is the foundation of a native Android audiobook player that connects to your Sappho server. Here's what's already implemented:

### ✅ Complete Foundation
1. **Android Project Structure** - Modern Gradle setup with Kotlin DSL
2. **Dependency Injection** - Hilt configured and ready
3. **Network Layer** - Retrofit API client with JWT authentication
4. **Secure Storage** - EncryptedSharedPreferences for tokens
5. **UI Framework** - Jetpack Compose with Material 3
6. **Theme** - Blue-tinted dark theme matching Sappho web app
7. **Authentication** - Complete login flow with server URL config
8. **Home Screen** - Continue Listening and Recently Added sections

### 📦 Dependencies Included
- Jetpack Compose & Material 3
- Hilt (Dependency Injection)
- Retrofit (API calls)
- Room (Database - ready to use)
- Media3/ExoPlayer (Audio playback - ready to use)
- Coil (Image loading)
- WorkManager (Background tasks - ready to use)

## Opening in Android Studio

1. **Install Android Studio**: Download from https://developer.android.com/studio
2. **Open Project**: File → Open → Select the `sapphoapp` directory
3. **Sync Gradle**: Android Studio will automatically sync dependencies
4. **Wait for indexing**: Let Android Studio index the project

## Running the App

### On Emulator
1. Create an Android Virtual Device (AVD) in Android Studio
   - Tools → Device Manager → Create Device
   - Select a device (e.g., Pixel 6)
   - Select API 34 (Android 14) system image
2. Click the Run button (green play icon) or press Shift+F10
3. Select your emulator from the list

### On Physical Device
1. Enable Developer Options on your Android device:
   - Go to Settings → About Phone
   - Tap "Build Number" 7 times
2. Enable USB Debugging:
   - Settings → System → Developer Options
   - Turn on "USB Debugging"
3. Connect device via USB
4. Click Run and select your device

## First Run Configuration

When you launch the app for the first time:

1. **Server URL**: Enter your Sappho server address
   - Example: `http://192.168.1.100:3002`
   - Must be accessible from your device/emulator
   - Use `http://10.0.2.2:3002` if Sappho is on localhost (emulator only)

2. **Login**: Use your existing Sappho credentials
   - Username and password from your Sappho server
   - Token is stored securely in EncryptedSharedPreferences

3. **Browse**: View your audiobook library!

## Next Development Steps

To continue building the app, you'll want to implement:

### 1. Library Screen (Next Priority)
- Full audiobook grid
- Search functionality
- Filters and sorting
- Navigation to detail screen

### 2. Player Screen
- Playback controls
- Seek bar
- Chapter navigation
- Speed control

### 3. Background Playback Service
- MediaSessionService implementation
- Notification with controls
- Lock screen integration

### 4. Offline Downloads
- Download manager
- Local storage with Room
- Sync progress when online

## Project Files Overview

### Configuration
- `build.gradle.kts` - Project dependencies
- `app/build.gradle.kts` - App module dependencies
- `AndroidManifest.xml` - App permissions and components

### Source Code
- `SapphoApplication.kt` - Application entry point
- `MainActivity.kt` - Main activity with Compose setup
- `SapphoApp.kt` - Navigation and routing
- `presentation/login/` - Login screen and logic
- `presentation/home/` - Home screen and logic
- `presentation/theme/` - Material 3 theme
- `data/remote/SapphoApi.kt` - API endpoints
- `data/repository/AuthRepository.kt` - Authentication
- `domain/model/` - Data models
- `di/NetworkModule.kt` - Dependency injection config

## Troubleshooting

### Can't connect to server
- Ensure Sappho server is running
- Check firewall settings
- For emulator: use `10.0.2.2` instead of `localhost`
- For physical device: ensure device and server are on same network

### Gradle sync failed
- Check internet connection
- File → Invalidate Caches / Restart
- Delete `.gradle` folder and re-sync

### Build errors
- Ensure JDK 17 is configured
- Tools → SDK Manager → Install Android SDK 34
- Update Android Studio to latest version

## Resources

- [Jetpack Compose Documentation](https://developer.android.com/jetpack/compose)
- [Material 3 Design](https://m3.material.io/)
- [Media3 (ExoPlayer) Guide](https://developer.android.com/guide/topics/media/media3)
- [Hilt Documentation](https://developer.android.com/training/dependency-injection/hilt-android)

## Contributing

This is in active development! Priority areas:
1. Library screen implementation
2. Audio playback service
3. Player UI
4. Offline downloads
5. Android Auto support

Happy coding! 🎧
