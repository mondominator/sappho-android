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

### Running on Device/Emulator
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

**IMPORTANT**: After completing code changes, always run `./gradlew installDebug` to install the app on the connected device for user testing.

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

## Enhanced UI System

The app features a comprehensive UI enhancement system with premium polish and accessibility features.

### Enhanced Theme System

**Advanced Dark Mode Colors** (`Color.kt`):
- Enhanced contrast ratios for better accessibility (WCAG compliant)
- `SapphoTextHigh` - High contrast white text for premium readability
- `SapphoBackgroundDeep` - OLED-friendly deeper background variant
- `SapphoSurfaceElevated` & `SapphoSurfaceDialog` - Sophisticated surface hierarchy
- Semantic color variants with dark alternatives for better visual hierarchy

**Material 3 Integration** (`Theme.kt`):
- Extended `SapphoColors` data class with all enhanced color variants
- Backward compatibility with legacy color mappings
- Dynamic theming with proper Material 3 color scheme integration

### Haptic Feedback System

**Comprehensive Haptic Patterns** (`HapticFeedback.kt`):
- `SapphoHaptics.hapticClickable()` - Enhanced clickable modifier with contextual feedback
- `SapphoHaptics.hapticToggleable()` - Toggleable interactions with appropriate feedback
- `HapticPatterns` - Predefined patterns for common actions:
  - `lightTap()` / `mediumTap()` - Basic interaction feedback
  - `playButtonPress()` - Media control emphasis
  - `downloadStart()` / `downloadCancel()` - Action-specific feedback
  - `cardTap()` - Card interaction feedback
  - `navigationAction()` - Navigation transitions
  - `successAction()` / `errorOccurred()` - State-based feedback

**Integration Points**:
- AudiobookDetailScreen: Play/pause, download actions, navigation buttons
- HomeScreen: Card taps, retry actions, sync operations
- All major interactive elements throughout the app

### Skeleton Loading Components

**Modular Skeleton System** (`SkeletonLoading.kt`):
- `shimmerBrush()` - High-performance shimmer animation engine
- `SkeletonBox` / `SkeletonCircle` - Basic building blocks
- `SkeletonText` - Multi-line text placeholders with configurable widths
- `SkeletonRow` - Icon + text combinations
- `SkeletonAudiobookCard` - Perfect audiobook card placeholder
- `SkeletonHomeSection` - Complete section with title + cards
- `SkeletonHomeScreen` - Full screen loading state
- `SkeletonAudiobookDetail` - Detail screen loading placeholder

**Performance Features**:
- Configurable shimmer effects with smooth infinite transitions
- Memory-efficient animations using `rememberInfiniteTransition`
- Accessibility-friendly loading states
- Responsive sizing based on content dimensions

### Advanced Animations

**Micro-Interactions** (`Animations.kt`):
- `bouncyClickable()` - Signature bouncy click animations with haptic feedback
- `enhancedClickable()` - Sophisticated click animations with scale transitions
- `progressiveReveal()` - Staggered list item animations
- `rememberShakeAnimation()` - Error state feedback with configurable intensity
- `rememberBreathingAnimation()` - Subtle active state indicators

**State Transition Animations**:
- `AnimatedContentSwap` - Smooth content transitions with slide effects
- `rememberButtonScaleAnimation()` - Dynamic button state animations
- `rememberElevationAnimation()` - Card hover/press elevation changes
- `rememberColorAnimation()` - Smooth color transitions for interactive states

**Content Animations**:
- `SapphoAnimatedVisibility` - Signature enter/exit animations
- `ContentStateAnimation` - Loading/error/success state management
- Performance-optimized animation specs with proper easing curves

### Enhanced Empty States

**Sophisticated Empty State System** (`EmptyStates.kt`):
- `EmptyState` - Base component with animation, icons, and action buttons
- Specialized states for different scenarios:
  - `EmptyLibrary` - Library onboarding
  - `EmptySearchResults` - Search feedback with query context
  - `NoInternetConnection` - Network error with retry action
  - `ServerError` - Server connectivity issues
  - `EmptyAudiobooks` - Animated illustration for empty content
  - `EmptyReadingList` - Reading list onboarding
  - `LoadingError` - Generic error handling with custom messages

**Animated Illustrations**:
- `AnimatedAudiobookIllustration` - Rotating book icon with pulse effects
- `AnimatedMusicWaves` - Dynamic audio waveform animation
- Custom Canvas drawings with sophisticated animation timing
- Performance-optimized illustration rendering

### Accessibility Features

**WCAG Compliance**:
- Enhanced color contrast ratios (minimum 4.5:1, target 7:1 for text)
- Semantic descriptions for all interactive elements
- Proper focus management and keyboard navigation
- Screen reader optimizations with `accessibleCard()` modifier

**Interaction Accessibility**:
- Haptic feedback respects accessibility preferences
- Configurable animation preferences (reduce motion support)
- Proper touch target sizing (minimum 48dp)
- High contrast mode support with enhanced color variants

**Loading State Accessibility**:
- Skeleton loading states maintain semantic structure
- Progress announcements for screen readers
- Meaningful loading descriptions and timeouts

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

## Working with Issues

When reviewing or commenting on GitHub issues:
- **Always verify the actual code** before stating whether something is fixed or not fixed
- Do not rely on assumptions or previous comments - check the implementation directly
- Use Grep/Read tools to confirm the current state of the codebase before closing issues

### Security Issues

For security vulnerabilities, use **GitHub Security Advisories** instead of regular issues:
1. Go to the repository's "Security" tab
2. Click "Advisories" → "New draft security advisory"
3. This keeps vulnerability details private until a fix is released

## Code Quality Principles

**Focus on quality code, not just the easiest or fastest solution.**

### General Principles
- **Write clean, readable code**: Prioritize clarity over cleverness. Code is read more than written.
- **Follow existing patterns**: Look at how similar features are implemented before adding new ones.
- **Avoid duplication**: Extract common logic into shared functions or composables.
- **Single responsibility**: Each function/class should do one thing well.
- **Meaningful names**: Use descriptive names for variables, functions, and classes.
- **Handle errors gracefully**: Don't swallow exceptions silently. Provide useful feedback to users.
- **Think about edge cases**: Consider null states, empty lists, network failures, etc.

### Android/Compose Specific
- **Use the theme system**: Never hardcode colors with `Color(0xFFXXXXXX)`. Use `MaterialTheme.colorScheme`, `MaterialTheme.sapphoColors`, or import from `Color.kt`
- **Use Typography**: Don't use inline `fontSize` and `fontWeight`. Define text styles in `Type.kt` and use `MaterialTheme.typography`
- **Consistent spacing**: Use the spacing scale (4, 8, 12, 16, 24, 32dp) consistently
- **Single source of truth**: Define constants, colors, dimensions in one place and reference them
- **Reusable composables**: Create reusable composables instead of copy-pasting UI code
- **State hoisting**: Lift state up to the appropriate level for reusability and testability
- **Remember performance**: Use `remember`, `derivedStateOf`, and `LaunchedEffect` appropriately

### What to Avoid
- Don't take shortcuts that create technical debt
- Don't ignore compiler warnings
- Don't leave commented-out code
- Don't hardcode magic numbers or strings
- Don't create god classes/functions that do everything

Example - using theme colors:
```kotlin
// ❌ Bad - hardcoded color
Text(color = Color(0xFF9CA3AF))

// ✅ Good - theme reference
Text(color = MaterialTheme.colorScheme.onSurfaceVariant)
// or
Text(color = MaterialTheme.sapphoColors.textMuted)
// or import directly
Text(color = SapphoTextSecondary)
```

## Testing Requirements

**CRITICAL: All new code must include comprehensive unit tests.**

### Test Coverage Standards
- **Minimum 80% code coverage** for new features and bug fixes
- **100% coverage for critical business logic** (authentication, playback, data persistence)
- **Unit tests required** for all ViewModels, Repositories, and API classes
- **Integration tests** for complex workflows (login flow, playback, etc.)
- **UI tests** for critical user paths (login, playbook selection, player controls)

### Testing Guidelines
- Write tests **FIRST** when fixing bugs - create a failing test that reproduces the issue, then fix it
- Test both **happy path** and **error scenarios** (network failures, invalid data, etc.)
- Use **descriptive test names** that explain what is being tested: `fun should return error when network is unavailable()`
- **Mock external dependencies** (API, databases, file system) in unit tests
- **Test edge cases**: empty states, null values, boundary conditions
- **Verify error handling**: ensure proper error messages and state updates

### Test Structure
All tests must follow the **Arrange-Act-Assert** pattern:
```kotlin
@Test
fun `should update user profile when valid data provided`() = runTest {
    // Given (Arrange)
    val newProfile = UserProfile(name = "John Doe", email = "john@test.com")
    coEvery { api.updateProfile(any()) } returns Response.success(newProfile)
    
    // When (Act)
    viewModel.updateProfile(newProfile)
    testDispatcher.scheduler.advanceUntilIdle()
    
    // Then (Assert)
    assertThat(viewModel.profileState.value).isEqualTo(ProfileState.Success(newProfile))
    coVerify { api.updateProfile(newProfile) }
}
```

### Test Dependencies
Use these standardized testing libraries:
- **JUnit 4.13.2** - Core testing framework
- **MockK 1.13.12** - Mocking for Kotlin
- **Truth 1.4.2** - Fluent assertions (`assertThat().isEqualTo()`)
- **Turbine 1.1.0** - Testing Kotlin Flows
- **Robolectric 4.11.1** - Android framework testing
- **Hilt Testing** - Dependency injection for tests
- **Coroutines Test** - Testing coroutines and suspend functions

### Running Tests
```bash
# Run all tests
./gradlew test

# Run specific test class
./gradlew test --tests "*LoginViewModelTest*"

# Run tests with coverage
./gradlew test jacocoTestReport
```

### Test File Organization
```
app/src/test/java/
├── com/sappho/audiobooks/
│   ├── presentation/
│   │   ├── login/
│   │   │   └── LoginViewModelTest.kt
│   │   └── home/
│   │       └── HomeViewModelTest.kt
│   ├── data/
│   │   ├── repository/
│   │   │   └── AuthRepositoryTest.kt
│   │   └── remote/
│   │       └── SapphoApiTest.kt
│   └── TestSapphoApplication.kt
```

### Enforcement
- **CI/CD pipeline fails** if tests don't pass or coverage drops
- **PRs cannot be merged** without adequate test coverage
- **Code reviews must verify** that tests cover the changes made
- **No exceptions** - if you write code, you write tests

## PR Workflow

- Always work on a new branch and merge back in via PR
- Don't use automerge - wait for pipelines to give results before moving on or declaring success
- Wait for user testing before shipping/merging
- Never bypass merge rules

## Remote Server Access (Unraid)

The production Sappho server runs on an Unraid server accessible via `ssh root@192.168.86.151`.

**IMPORTANT: Observe only, do not modify.**
- You may SSH into unraid to **read logs** and **check container status**
- **DO NOT** stop, start, restart, or remove containers
- **DO NOT** run docker commands that modify state (stop, rm, run, etc.)
- **DO NOT** modify files on the server
- Container configuration is managed through Unraid's web UI, not CLI

Allowed commands:
```bash
# Check logs
ssh root@192.168.86.151 "docker logs sappho --tail 100"

# Check container status
ssh root@192.168.86.151 "docker ps | grep sappho"

# Check files (read-only)
ssh root@192.168.86.151 "docker exec sappho find /app/data/audiobooks -type d -empty"
```

Forbidden commands:
```bash
# DO NOT run these
docker stop/start/restart/rm/run
docker compose up/down
docker pull (user will pull via Unraid UI)
```

## Screenshots

- **Do NOT take screenshots** of the app using adb screencap or similar tools
- Let the user test the app manually on their device

## Release Checklist

Before creating a new release, update the version number in `app/build.gradle.kts`:
- `versionCode` - increment by 1
- `versionName` - update to match the release tag (e.g., "1.3.0")

The app version is displayed in two places which both use `BuildConfig.VERSION_NAME`:
- User dropdown menu (HomeScreen.kt)
- About section in Profile (ProfileScreen.kt)
