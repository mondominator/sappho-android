# In-App Update Feature Design

## Overview

Add a flexible in-app update prompt that notifies internal testers when a new version is available on the Play Store, with a direct link to update.

## Decisions

- **Update behavior**: Flexible (dismissable, non-blocking)
- **Detection method**: Google Play In-App Updates API
- **Trigger point**: On app launch only
- **UI**: Compose AlertDialog with "Update Now" and "Later" buttons

## Dependencies

Add to `app/build.gradle.kts`:
```kotlin
implementation("com.google.android.play:app-update:2.1.0")
implementation("com.google.android.play:app-update-ktx:2.1.0")
```

## Components

### InAppUpdateManager

Location: `app/src/main/java/com/sappho/audiobooks/data/update/InAppUpdateManager.kt`

Responsibilities:
- Wrap the Play In-App Updates API
- Check for available updates on app launch
- Expose update availability as `StateFlow<Boolean>`
- Provide method to launch Play Store update flow

```kotlin
@Singleton
class InAppUpdateManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val appUpdateManager: AppUpdateManager = AppUpdateManagerFactory.create(context)

    private val _updateAvailable = MutableStateFlow(false)
    val updateAvailable: StateFlow<Boolean> = _updateAvailable

    private var appUpdateInfo: AppUpdateInfo? = null

    suspend fun checkForUpdate()
    fun startUpdate(activity: Activity)
}
```

### UpdateDialog

Location: `app/src/main/java/com/sappho/audiobooks/presentation/components/UpdateDialog.kt`

A Compose AlertDialog that:
- Shows title "Update Available"
- Shows message about new version
- Has "Update Now" button (primary action)
- Has "Later" button (dismisses dialog)
- Uses Sappho dark theme colors

## Flow

1. `MainActivity.onCreate()` triggers `InAppUpdateManager.checkForUpdate()`
2. API returns update info → `updateAvailable` set to `true`
3. MainActivity observes state → shows `UpdateDialog`
4. User taps "Update Now" → `startUpdate()` opens Play Store
5. User taps "Later" → dialog dismisses, app continues
6. Next launch → check runs again

## Error Handling

- Network failures: Silently fail, don't block app
- Play Store unavailable: Skip update check
- Exceptions: Log and continue normally

## File Changes

### New Files
- `app/src/main/java/com/sappho/audiobooks/data/update/InAppUpdateManager.kt`
- `app/src/main/java/com/sappho/audiobooks/presentation/components/UpdateDialog.kt`

### Modified Files
- `app/build.gradle.kts` - Add Play In-App Updates dependency
- `app/src/main/java/com/sappho/audiobooks/presentation/MainActivity.kt` - Inject manager, show dialog

## Testing Notes

The In-App Updates API requires:
- Signed release build
- Installed from Play Store (internal track works)
- A newer version available on the same track

During development, verify:
- Code compiles without errors
- Dialog UI renders correctly (can force-show for testing)
- Update manager initializes without crashes

## Implementation Steps

1. Add Play In-App Updates dependency to build.gradle.kts
2. Create InAppUpdateManager class with update check logic
3. Create UpdateDialog composable
4. Integrate into MainActivity
5. Build and test on device
