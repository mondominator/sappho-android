# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

### Environment Setup
```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
```

### Building
```bash
# Debug build
./gradlew assembleDebug

# Release build
./gradlew assembleRelease

# Clean build
./gradlew clean assembleDebug
```

### Running on Emulator
```bash
# Install and run (requires device/emulator running)
./gradlew installDebug
/Users/mondo/Library/Android/sdk/platform-tools/adb shell am start -n com.sappho.audiobooks/.presentation.MainActivity

# Fresh install (clears data)
/Users/mondo/Library/Android/sdk/platform-tools/adb uninstall com.sappho.audiobooks
./gradlew installDebug

# Check logs
/Users/mondo/Library/Android/sdk/platform-tools/adb logcat -d | grep "Sappho\|okhttp"
```

## Architecture Overview

### Dynamic Server URL System
The app implements a **runtime configurable server URL** architecture that requires understanding across multiple layers:

1. **User Input**: Users enter server URL at login (e.g., `https://sapho.bitstorm.ca` or `http://192.168.1.100:3002`)

2. **Storage**: `AuthRepository` stores both the server URL and auth token in `EncryptedSharedPreferences`

3. **Network Layer** (`NetworkModule.kt`):
   - OkHttpClient has a **custom interceptor** that reads the server URL from `AuthRepository` on EVERY request
   - Dynamically rebuilds request URLs by replacing the base URL
   - Automatically adds `Authorization: Bearer <token>` headers
   - This interceptor is critical - Retrofit's static `baseUrl()` is NOT used for actual requests

4. **Image Loading** (`SapphoApplication.kt`):
   - Coil's `ImageLoader` is configured via `ImageLoaderFactory` to use the same `OkHttpClient`
   - This ensures cover images load with authentication and dynamic server URLs
   - Cover image URLs are constructed as `$serverUrl/api/audiobooks/${id}/cover`

**Critical**: When debugging network issues, check that:
- Server URL is properly stored in `AuthRepository`
- OkHttpClient interceptor is being invoked
- Both Retrofit API calls AND Coil image requests use the same authenticated OkHttpClient instance

### JSON Serialization Conventions

The Sappho server API uses **snake_case** field names in JSON responses, but Kotlin models use **camelCase**. Always use `@SerializedName` annotations:

```kotlin
data class Audiobook(
    val id: Int,
    @SerializedName("cover_image") val coverImage: String?,
    @SerializedName("series_position") val seriesPosition: Float?,
    @SerializedName("published_year") val publishYear: Int?,
    // ...
)
```

**Never** assume Gson will auto-convert between snake_case and camelCase. Missing `@SerializedName` annotations will result in null fields.

### MVVM + Clean Architecture Layers

```
presentation/        # Compose UI + ViewModels
    ├── home/       # Home feed (Continue Listening, Recently Added, Listen Again)
    ├── login/      # Authentication flow
    ├── main/       # Bottom nav container
    ├── library/    # Library grid (stub)
    ├── search/     # Search (stub)
    └── profile/    # User profile

domain/model/       # Pure Kotlin data classes with @SerializedName annotations

data/
    ├── remote/     # SapphoApi interface (Retrofit)
    └── repository/ # AuthRepository (EncryptedSharedPreferences)

di/                 # Hilt modules (NetworkModule provides OkHttpClient, Retrofit, API)
```

**State Management Pattern**:
- ViewModels expose `StateFlow<T>` for UI state
- Use `MutableStateFlow` privately, expose immutable `StateFlow`
- Collect state in Composables with `collectAsState()`

Example:
```kotlin
class HomeViewModel @Inject constructor(
    private val api: SapphoApi,
    private val authRepository: AuthRepository
) : ViewModel() {
    private val _books = MutableStateFlow<List<Audiobook>>(emptyList())
    val books: StateFlow<List<Audiobook>> = _books

    private val _serverUrl = MutableStateFlow<String?>(null)
    val serverUrl: StateFlow<String?> = _serverUrl

    init {
        _serverUrl.value = authRepository.getServerUrlSync()
        loadBooks()
    }
}
```

### API Endpoints

All endpoints defined in `SapphoApi.kt` use relative paths. The base URL is dynamically set via OkHttpClient interceptor.

**Metadata endpoints** (use `/meta/` prefix):
- `GET /api/audiobooks/meta/in-progress?limit=10`
- `GET /api/audiobooks/meta/recent?limit=10`
- `GET /api/audiobooks/meta/finished?limit=10`

**Cover images** (authenticated):
- `GET /api/audiobooks/{id}/cover`

**Authentication**:
- `POST /api/auth/login` - Returns `{ token: string, user: User }`
- Token stored in `AuthRepository` and auto-injected via OkHttpClient interceptor

## Dependency Injection (Hilt)

Key modules:
- `NetworkModule`: Provides `OkHttpClient`, `Retrofit`, `SapphoApi`
- ViewModels: Annotated with `@HiltViewModel`, injected via `hiltViewModel()` in Composables
- Application: `SapphoApplication` annotated with `@HiltAndroidApp`

`OkHttpClient` is a singleton shared across:
1. Retrofit (API calls)
2. Coil ImageLoader (cover images)

This ensures all network requests use the same authentication and dynamic URL logic.

## UI Theme

The app matches the Sappho web app's dark theme:
- Background: `#0A0E1A`
- Surface: `#1a1a1a`
- Primary (blue): `#3B82F6`
- Text: `#E0E7F1` (light), `#9ca3af` (muted)

Logo: `app/src/main/res/drawable/sappho_logo.png` (from web app)

## PWA Reference

The Sappho PWA (Progressive Web App) is located at `/Users/mondo/Documents/git/sappho/client` and serves as the design reference for the Android app:
- **Icon Design**: The app launcher icon (`ic_launcher_foreground.xml`) uses the same PWA icon from `/Users/mondo/Documents/git/sappho/client/public/icon-512.png`
- **Theme Colors**: All colors match the PWA's dark theme defined in the web app's Tailwind configuration
- **UI Components**: The Android app aims to replicate the PWA's user interface and interactions using Jetpack Compose
- **API Integration**: Both the PWA and Android app consume the same Sappho server API endpoints

## Common Gotchas

1. **Cover images not loading**: Check that:
   - `coverImage` field has `@SerializedName("cover_image")` annotation
   - `SapphoApplication` implements `ImageLoaderFactory` and returns ImageLoader with injected `OkHttpClient`
   - Server URL is set in `AuthRepository`

2. **API 404 errors**: Verify endpoint paths match server routes (e.g., `/meta/finished` not `/finished`)

3. **Null fields in API responses**: Missing `@SerializedName` annotations for snake_case JSON fields

4. **Build errors**: Ensure `JAVA_HOME` is set to Android Studio's JDK before running Gradle commands

5. **Emulator not found**: Start emulator first or check available devices:
   ```bash
   /Users/mondo/Library/Android/sdk/emulator/emulator -list-avds
   /Users/mondo/Library/Android/sdk/platform-tools/adb devices
   ```

## Testing Server Connectivity

```bash
# Check server is reachable
curl -I https://sapho.bitstorm.ca/api/audiobooks/meta/recent?limit=1

# Test authenticated endpoint (requires valid token)
curl -H "Authorization: Bearer <token>" https://sapho.bitstorm.ca/api/audiobooks/5557/cover
```
