package com.sappho.audiobooks.data.repository

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class LibrarySortOption(val displayName: String) {
    TITLE("Title"),
    AUTHOR("Author"),
    RECENTLY_ADDED("Recently Added"),
    RECENTLY_LISTENED("Recently Listened"),
    DURATION("Duration"),
    PROGRESS("Progress"),
    SERIES_POSITION("Series Position"),
    RATING("Rating")
}

enum class LibraryFilterOption(val displayName: String) {
    ALL("All Books"),
    HIDE_FINISHED("Hide Finished"),
    IN_PROGRESS("In Progress"),
    NOT_STARTED("Not Started"),
    FINISHED("Finished")
}

@Singleton
class UserPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // Skip intervals
    private val _skipForwardSeconds = MutableStateFlow(getSkipForwardSecondsSync())
    val skipForwardSeconds: StateFlow<Int> = _skipForwardSeconds.asStateFlow()

    private val _skipBackwardSeconds = MutableStateFlow(getSkipBackwardSecondsSync())
    val skipBackwardSeconds: StateFlow<Int> = _skipBackwardSeconds.asStateFlow()

    // Library sort
    private val _librarySortOption = MutableStateFlow(getLibrarySortOptionSync())
    val librarySortOption: StateFlow<LibrarySortOption> = _librarySortOption.asStateFlow()

    private val _librarySortAscending = MutableStateFlow(getLibrarySortAscendingSync())
    val librarySortAscending: StateFlow<Boolean> = _librarySortAscending.asStateFlow()

    // Library filter
    private val _libraryFilterOption = MutableStateFlow(getLibraryFilterOptionSync())
    val libraryFilterOption: StateFlow<LibraryFilterOption> = _libraryFilterOption.asStateFlow()

    // Playback settings
    private val _defaultPlaybackSpeed = MutableStateFlow(getDefaultPlaybackSpeedSync())
    val defaultPlaybackSpeed: StateFlow<Float> = _defaultPlaybackSpeed.asStateFlow()

    private val _rewindOnResumeSeconds = MutableStateFlow(getRewindOnResumeSecondsSync())
    val rewindOnResumeSeconds: StateFlow<Int> = _rewindOnResumeSeconds.asStateFlow()

    private val _defaultSleepTimerMinutes = MutableStateFlow(getDefaultSleepTimerMinutesSync())
    val defaultSleepTimerMinutes: StateFlow<Int> = _defaultSleepTimerMinutes.asStateFlow()

    // Buffer size setting
    private val _bufferSizeSeconds = MutableStateFlow(getBufferSizeSecondsSync())
    val bufferSizeSeconds: StateFlow<Int> = _bufferSizeSeconds.asStateFlow()

    // Show chapter progress setting
    private val _showChapterProgress = MutableStateFlow(getShowChapterProgressSync())
    val showChapterProgress: StateFlow<Boolean> = _showChapterProgress.asStateFlow()

    // Skip forward options: 10s, 15s, 30s, 45s, 60s, 90s
    fun setSkipForwardSeconds(seconds: Int) {
        prefs.edit().putInt(KEY_SKIP_FORWARD, seconds).apply()
        _skipForwardSeconds.value = seconds
    }

    fun getSkipForwardSecondsSync(): Int {
        return prefs.getInt(KEY_SKIP_FORWARD, DEFAULT_SKIP_FORWARD)
    }

    // Skip backward options: 5s, 10s, 15s, 30s
    fun setSkipBackwardSeconds(seconds: Int) {
        prefs.edit().putInt(KEY_SKIP_BACKWARD, seconds).apply()
        _skipBackwardSeconds.value = seconds
    }

    fun getSkipBackwardSecondsSync(): Int {
        return prefs.getInt(KEY_SKIP_BACKWARD, DEFAULT_SKIP_BACKWARD)
    }

    // Library sort option
    fun setLibrarySortOption(option: LibrarySortOption) {
        prefs.edit().putString(KEY_LIBRARY_SORT, option.name).apply()
        _librarySortOption.value = option
    }

    fun getLibrarySortOptionSync(): LibrarySortOption {
        val name = prefs.getString(KEY_LIBRARY_SORT, DEFAULT_LIBRARY_SORT.name)
        return try {
            LibrarySortOption.valueOf(name ?: DEFAULT_LIBRARY_SORT.name)
        } catch (e: Exception) {
            DEFAULT_LIBRARY_SORT
        }
    }

    // Library sort direction
    fun setLibrarySortAscending(ascending: Boolean) {
        prefs.edit().putBoolean(KEY_LIBRARY_SORT_ASC, ascending).apply()
        _librarySortAscending.value = ascending
    }

    fun getLibrarySortAscendingSync(): Boolean {
        return prefs.getBoolean(KEY_LIBRARY_SORT_ASC, DEFAULT_SORT_ASCENDING)
    }

    // Library filter option
    fun setLibraryFilterOption(option: LibraryFilterOption) {
        prefs.edit().putString(KEY_LIBRARY_FILTER, option.name).apply()
        _libraryFilterOption.value = option
    }

    fun getLibraryFilterOptionSync(): LibraryFilterOption {
        val name = prefs.getString(KEY_LIBRARY_FILTER, DEFAULT_LIBRARY_FILTER.name)
        return try {
            LibraryFilterOption.valueOf(name ?: DEFAULT_LIBRARY_FILTER.name)
        } catch (e: Exception) {
            DEFAULT_LIBRARY_FILTER
        }
    }

    // Default playback speed (0.5x to 3.0x)
    fun setDefaultPlaybackSpeed(speed: Float) {
        prefs.edit().putFloat(KEY_DEFAULT_PLAYBACK_SPEED, speed).apply()
        _defaultPlaybackSpeed.value = speed
    }

    fun getDefaultPlaybackSpeedSync(): Float {
        return prefs.getFloat(KEY_DEFAULT_PLAYBACK_SPEED, DEFAULT_PLAYBACK_SPEED)
    }

    // Rewind on resume (0, 5, 10, 15, 30 seconds)
    fun setRewindOnResumeSeconds(seconds: Int) {
        prefs.edit().putInt(KEY_REWIND_ON_RESUME, seconds).apply()
        _rewindOnResumeSeconds.value = seconds
    }

    fun getRewindOnResumeSecondsSync(): Int {
        return prefs.getInt(KEY_REWIND_ON_RESUME, DEFAULT_REWIND_ON_RESUME)
    }

    // Default sleep timer (0 = disabled, or minutes: 5, 10, 15, 30, 45, 60)
    fun setDefaultSleepTimerMinutes(minutes: Int) {
        prefs.edit().putInt(KEY_DEFAULT_SLEEP_TIMER, minutes).apply()
        _defaultSleepTimerMinutes.value = minutes
    }

    fun getDefaultSleepTimerMinutesSync(): Int {
        return prefs.getInt(KEY_DEFAULT_SLEEP_TIMER, DEFAULT_SLEEP_TIMER)
    }

    // Buffer size (seconds)
    fun setBufferSizeSeconds(seconds: Int) {
        prefs.edit().putInt(KEY_BUFFER_SIZE, seconds).apply()
        _bufferSizeSeconds.value = seconds
    }

    fun getBufferSizeSecondsSync(): Int {
        return prefs.getInt(KEY_BUFFER_SIZE, DEFAULT_BUFFER_SIZE)
    }

    // Show chapter progress
    fun setShowChapterProgress(show: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_CHAPTER_PROGRESS, show).apply()
        _showChapterProgress.value = show
    }

    fun getShowChapterProgressSync(): Boolean {
        return prefs.getBoolean(KEY_SHOW_CHAPTER_PROGRESS, DEFAULT_SHOW_CHAPTER_PROGRESS)
    }

    companion object {
        private const val PREFS_NAME = "user_preferences"
        private const val KEY_SKIP_FORWARD = "skip_forward_seconds"
        private const val KEY_SKIP_BACKWARD = "skip_backward_seconds"
        private const val KEY_LIBRARY_SORT = "library_sort_option"
        private const val KEY_LIBRARY_SORT_ASC = "library_sort_ascending"
        private const val KEY_LIBRARY_FILTER = "library_filter_option"
        private const val KEY_DEFAULT_PLAYBACK_SPEED = "default_playback_speed"
        private const val KEY_REWIND_ON_RESUME = "rewind_on_resume_seconds"
        private const val KEY_DEFAULT_SLEEP_TIMER = "default_sleep_timer_minutes"
        private const val KEY_BUFFER_SIZE = "buffer_size_seconds"
        private const val KEY_SHOW_CHAPTER_PROGRESS = "show_chapter_progress"

        const val DEFAULT_SKIP_FORWARD = 15
        const val DEFAULT_SKIP_BACKWARD = 15
        val DEFAULT_LIBRARY_SORT = LibrarySortOption.TITLE
        const val DEFAULT_SORT_ASCENDING = true
        val DEFAULT_LIBRARY_FILTER = LibraryFilterOption.ALL
        const val DEFAULT_PLAYBACK_SPEED = 1.0f
        const val DEFAULT_REWIND_ON_RESUME = 0
        const val DEFAULT_SLEEP_TIMER = 0
        const val DEFAULT_BUFFER_SIZE = 60  // 1 minute default
        const val DEFAULT_SHOW_CHAPTER_PROGRESS = false

        // Available options for skip intervals
        val SKIP_FORWARD_OPTIONS = listOf(10, 15, 30, 45, 60, 90)
        val SKIP_BACKWARD_OPTIONS = listOf(5, 10, 15, 30)

        // Available playback speed options
        val PLAYBACK_SPEED_OPTIONS = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 2.5f, 3.0f)

        // Rewind on resume options (seconds)
        val REWIND_ON_RESUME_OPTIONS = listOf(0, 5, 10, 15, 30)

        // Sleep timer default options (minutes, 0 = disabled)
        val SLEEP_TIMER_OPTIONS = listOf(0, 5, 10, 15, 30, 45, 60)

        // Buffer size options (seconds)
        val BUFFER_SIZE_OPTIONS = listOf(30, 60, 120, 300, 600, 1800, 3600, 7200, 10800)

        // Helper function to format buffer size for display
        fun formatBufferSize(seconds: Int): String {
            return when {
                seconds < 60 -> "${seconds}s"
                seconds < 3600 -> "${seconds / 60} min"
                else -> "${seconds / 3600} hr"
            }
        }
    }
}
