package com.sappho.audiobooks.presentation.theme

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Accessibility utilities for the Sappho app
 */
object SapphoAccessibility {
    
    // Minimum touch target sizes per Material Design guidelines
    val MinTouchTarget = 48.dp
    val MinIconTarget = 44.dp
    
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
    
    /**
     * Progress announcements for screen readers
     */
    fun getProgressAnnouncement(current: Int, total: Int): String {
        val percentage = ((current.toFloat() / total.toFloat()) * 100).toInt()
        return "Progress $percentage percent, $current of $total"
    }
    
    fun getTimeProgressAnnouncement(currentSeconds: Int, totalSeconds: Int): String {
        val currentMinutes = currentSeconds / 60
        val totalMinutes = totalSeconds / 60
        val percentage = ((currentSeconds.toFloat() / totalSeconds.toFloat()) * 100).toInt()
        return "Progress $percentage percent, $currentMinutes minutes of $totalMinutes minutes"
    }
}

/**
 * Accessible icon button with proper touch targets
 */
@Composable
fun AccessibleIconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: Dp = SapphoAccessibility.MinIconTarget,
    iconSize: Dp = 24.dp,
    tint: androidx.compose.ui.graphics.Color = LocalContentColor.current
) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(size)
            .semantics {
                this.contentDescription = contentDescription
                if (!enabled) {
                    disabled()
                }
            },
        enabled = enabled
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null, // Already set on the button
            modifier = Modifier.size(iconSize),
            tint = tint
        )
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
 * Progress bar with accessibility support
 */
@Composable
fun AccessibleProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    currentTime: String? = null,
    totalTime: String? = null,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary
) {
    LinearProgressIndicator(
        progress = progress,
        modifier = modifier.semantics {
            val percentage = (progress * 100).toInt()
            contentDescription = if (currentTime != null && totalTime != null) {
                "Progress $percentage percent, $currentTime of $totalTime"
            } else {
                "Progress $percentage percent"
            }
            
            // Set progress info for screen readers
            progressBarRangeInfo = ProgressBarRangeInfo(
                current = progress,
                range = 0f..1f
            )
        },
        color = color
    )
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

/**
 * Status announcement for dynamic content changes
 */
@Composable
fun AccessibleStatusAnnouncement(
    message: String,
    isError: Boolean = false
) {
    // Create an invisible text element that announces status changes
    Text(
        text = message,
        modifier = Modifier
            .size(0.dp)
            .semantics {
                contentDescription = message
                if (isError) {
                    error(message)
                } else {
                    liveRegion = LiveRegionMode.Polite
                }
            }
    )
}

/**
 * Accessible button with minimum touch target
 */
@Composable
fun AccessibleButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentDescription: String? = null,
    content: @Composable () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .let { mod ->
                if (contentDescription != null) {
                    mod.semantics { this.contentDescription = contentDescription }
                } else mod
            },
        enabled = enabled,
        content = { content() }
    )
}