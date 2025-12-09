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
    SERIES_POSITION("Series Position")
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

    companion object {
        private const val PREFS_NAME = "user_preferences"
        private const val KEY_SKIP_FORWARD = "skip_forward_seconds"
        private const val KEY_SKIP_BACKWARD = "skip_backward_seconds"
        private const val KEY_LIBRARY_SORT = "library_sort_option"
        private const val KEY_LIBRARY_SORT_ASC = "library_sort_ascending"
        private const val KEY_LIBRARY_FILTER = "library_filter_option"

        const val DEFAULT_SKIP_FORWARD = 15
        const val DEFAULT_SKIP_BACKWARD = 15
        val DEFAULT_LIBRARY_SORT = LibrarySortOption.TITLE
        const val DEFAULT_SORT_ASCENDING = true
        val DEFAULT_LIBRARY_FILTER = LibraryFilterOption.ALL

        // Available options for skip intervals
        val SKIP_FORWARD_OPTIONS = listOf(10, 15, 30, 45, 60, 90)
        val SKIP_BACKWARD_OPTIONS = listOf(5, 10, 15, 30)
    }
}
