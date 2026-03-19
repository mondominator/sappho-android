package com.sappho.audiobooks.presentation.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*

/**
 * Accessibility utilities for the Sappho app
 */
object SapphoAccessibility {

    /**
     * Common content descriptions for reuse
     */
    object ContentDescriptions {
        // Playback controls
        const val PLAY_BUTTON = "Play audiobook"
        const val PAUSE_BUTTON = "Pause audiobook"
        const val SKIP_FORWARD = "Skip forward 10 seconds"
        const val SKIP_BACKWARD = "Skip backward 10 seconds"
        const val NEXT_CHAPTER = "Next chapter"
        const val PREVIOUS_CHAPTER = "Previous chapter"
        const val PLAYBACK_SPEED = "Playback speed"
        const val SLEEP_TIMER = "Sleep timer"
        const val CHAPTERS = "Chapters"
        const val CAST = "Cast to device"
        const val CAST_CONNECTED = "Cast connected"

        // Reading list
        const val FAVORITE_ADD = "Add to reading list"
        const val FAVORITE_REMOVE = "Remove from reading list"

        // Downloads
        const val DOWNLOAD_START = "Download audiobook"
        const val DOWNLOAD_CANCEL = "Cancel download"
        const val DOWNLOAD_RETRY = "Retry download"
        const val DOWNLOAD_COMPLETE = "Download complete"
        const val DOWNLOAD_PROGRESS = "Download in progress"

        // Navigation
        const val BACK_BUTTON = "Go back"
        const val MENU_BUTTON = "Open menu"
        const val MINIMIZE = "Minimize player"
        const val EXPAND = "Expand"
        const val COLLAPSE = "Collapse"
        const val CHEVRON_RIGHT = "View details"

        // Search and filter
        const val SEARCH_BUTTON = "Search audiobooks"
        const val SEARCH_CLEAR = "Clear search"
        const val FILTER = "Filter"
        const val SORT = "Sort"

        // Settings and admin
        const val SETTINGS_BUTTON = "Open settings"
        const val EDIT = "Edit"
        const val DELETE = "Delete"
        const val REFRESH = "Refresh"
        const val UPLOAD = "Upload"
        const val SCAN = "Scan library"

        // General actions
        const val CLOSE_BUTTON = "Close"
        const val CONFIRM = "Confirm"
        const val CANCEL = "Cancel"
        const val RETRY = "Retry"
        const val CLEAR = "Clear"

        // Status indicators
        const val LOADING = "Loading content"
        const val ERROR = "Error occurred"
        const val OFFLINE = "Offline mode"
        const val SUCCESS = "Success"
        const val WARNING = "Warning"

        // Sync status
        const val SYNC_ERROR = "Sync error"
        const val SYNC_IN_PROGRESS = "Syncing in progress"
        const val SYNC_PENDING = "Sync pending"
        const val SYNC_COMPLETE = "Sync complete"
        const val SYNC_TRIGGER = "Trigger sync"
        const val SYNC_DISMISS = "Dismiss sync error"

        // Rating
        const val RATE_BOOK = "Rate this book"
        const val STAR_FILLED = "Star filled"
        const val STAR_EMPTY = "Star empty"
        const val AVERAGE_RATING = "Average rating"
        const val YOUR_RATING = "Your rating"

        // Content
        const val BOOK_COVER = "Book cover"
        const val AUTHOR = "Author"
        const val SERIES = "Series"
        const val SHOW_MORE = "Show more"
        const val SHOW_LESS = "Show less"

        // Library categories
        const val SERIES_CATEGORY = "Browse series"
        const val AUTHORS_CATEGORY = "Browse authors"
        const val GENRES_CATEGORY = "Browse genres"
        const val COLLECTIONS_CATEGORY = "Browse collections"
        const val READING_LIST_CATEGORY = "Reading list"
        const val ALL_BOOKS = "All books"

        // Decorative icons should use contentDescription = null directly
        // This tells screen readers to skip them
    }
}

/**
 * Accessible card with proper semantic information
 */
fun Modifier.accessibleCard(
    title: String,
    subtitle: String? = null,
    progress: Float? = null,
    isFavorite: Boolean = false,
    isCompleted: Boolean = false,
    onClick: (() -> Unit)? = null
) = this.semantics(mergeDescendants = true) {
    // Set content description
    val description = buildString {
        append(title)
        if (subtitle != null) {
            append(", by $subtitle")
        }
        if (isCompleted) {
            append(", completed")
        } else if (progress != null && progress > 0) {
            val percentage = (progress * 100).toInt()
            append(", $percentage percent complete")
        }
        if (isFavorite) {
            append(", in reading list")
        }
    }
    contentDescription = description

    // Set role
    role = Role.Button

    // Add click action if provided
    onClick?.let { clickAction ->
        this.onClick(label = "Open audiobook") {
            clickAction()
            true
        }
    }
}

/**
 * Section header with proper heading semantics
 */
@Composable
fun AccessibleSectionHeader(
    text: String,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.headlineSmall
) {
    Text(
        text = text,
        style = style,
        modifier = modifier.semantics {
            heading()
            contentDescription = "$text section"
        }
    )
}
