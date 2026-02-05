package com.sappho.audiobooks.presentation.detail

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sappho.audiobooks.data.remote.ChapterUpdate
import com.sappho.audiobooks.domain.model.Audiobook
import com.sappho.audiobooks.domain.model.Chapter
import com.sappho.audiobooks.presentation.theme.*

/**
 * Dialog for viewing and editing chapter information.
 * Supports:
 * - Viewing chapter list with current chapter highlighted
 * - Edit mode for admin users to rename chapters
 * - ASIN lookup to fetch chapters from Audnexus
 * - Auto-scroll to current playing chapter
 */
@Composable
internal fun ChaptersDialog(
    chapters: List<Chapter>,
    audiobook: Audiobook?,
    currentAudiobook: Audiobook?,
    currentPosition: Long,
    isAdmin: Boolean,
    isSavingChapters: Boolean,
    chapterSaveResult: String?,
    isFetchingChapters: Boolean,
    fetchChaptersResult: String?,
    onChapterClick: (Chapter) -> Unit,
    onSaveChapters: (List<ChapterUpdate>) -> Unit,
    onFetchChapters: (String) -> Unit,
    onClearChapterSaveResult: () -> Unit,
    onClearFetchChaptersResult: () -> Unit,
    onDismiss: () -> Unit
) {
    var isEditMode by remember { mutableStateOf(false) }
    var editedTitles by remember(chapters) {
        mutableStateOf(chapters.associate { it.id to (it.title ?: "Chapter ${chapters.indexOf(it) + 1}") })
    }
    var showAsinInput by remember { mutableStateOf(false) }
    var asinInput by remember(audiobook) { mutableStateOf(audiobook?.asin ?: "") }

    val isBusy = isSavingChapters || isFetchingChapters

    // Determine if this book is currently playing
    val isCurrentBook = audiobook != null && currentAudiobook?.id == audiobook.id

    // Find the current chapter index based on position
    val currentChapterIndex = remember(chapters, currentPosition, isCurrentBook) {
        if (!isCurrentBook || chapters.isEmpty()) -1
        else {
            chapters.indexOfLast { it.startTime <= currentPosition }
        }
    }

    // LazyListState for auto-scrolling
    val listState = rememberLazyListState()

    // Auto-scroll to current chapter when dialog opens
    LaunchedEffect(currentChapterIndex) {
        if (currentChapterIndex > 0) {
            // Scroll to center the current chapter
            listState.animateScrollToItem(
                index = maxOf(0, currentChapterIndex - 2),
                scrollOffset = 0
            )
        }
    }

    // Reset edit mode when chapters change (after save/fetch)
    LaunchedEffect(chapters) {
        if (!isBusy) {
            editedTitles = chapters.associate { it.id to (it.title ?: "Chapter ${chapters.indexOf(it) + 1}") }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isBusy) onDismiss() },
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Chapters", color = Color.White)
                if (isAdmin && !isEditMode) {
                    IconButton(
                        onClick = { isEditMode = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit chapters",
                            tint = SapphoInfo,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Result messages
                chapterSaveResult?.let { result ->
                    ResultMessage(
                        result = result,
                        onDismiss = onClearChapterSaveResult
                    )
                }

                fetchChaptersResult?.let { result ->
                    ResultMessage(
                        result = result,
                        onDismiss = onClearFetchChaptersResult
                    )
                }

                // Admin controls when in edit mode
                if (isAdmin && isEditMode) {
                    // ASIN lookup section
                    if (showAsinInput) {
                        AsinInputRow(
                            asinInput = asinInput,
                            onAsinChange = { asinInput = it },
                            isBusy = isBusy,
                            isFetching = isFetchingChapters,
                            onFetch = {
                                if (asinInput.isNotBlank()) {
                                    onFetchChapters(asinInput)
                                    showAsinInput = false
                                }
                            },
                            onCancel = { showAsinInput = false }
                        )
                    } else {
                        Button(
                            onClick = { showAsinInput = true },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isBusy,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = LegacyPurple.copy(alpha = 0.15f),
                                contentColor = LegacyPurpleLight
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Lookup Chapters from Audnexus")
                        }
                    }

                    HorizontalDivider(color = SapphoProgressTrack, modifier = Modifier.padding(vertical = 4.dp))
                }

                // Chapter list
                LazyColumn(
                    state = listState,
                    modifier = Modifier.heightIn(max = 350.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    itemsIndexed(chapters) { index, chapter ->
                        val isCurrentChapter = index == currentChapterIndex
                        if (isEditMode && isAdmin) {
                            // Edit mode - show text fields
                            ChapterEditRow(
                                index = index,
                                chapter = chapter,
                                editedTitle = editedTitles[chapter.id] ?: "",
                                isBusy = isBusy,
                                onTitleChange = { newTitle ->
                                    editedTitles = editedTitles.toMutableMap().apply {
                                        put(chapter.id, newTitle)
                                    }
                                }
                            )
                        } else {
                            // View mode - clickable chapter with highlighting for current chapter
                            ChapterViewRow(
                                index = index,
                                chapter = chapter,
                                isCurrentChapter = isCurrentChapter,
                                onClick = { onChapterClick(chapter) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (isEditMode && isAdmin) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = {
                            isEditMode = false
                            // Reset to original titles
                            editedTitles = chapters.associate { it.id to (it.title ?: "Chapter ${chapters.indexOf(it) + 1}") }
                        },
                        enabled = !isBusy
                    ) {
                        Text("Cancel", color = SapphoIconDefault)
                    }
                    Button(
                        onClick = {
                            val updates = editedTitles.map { (id, title) ->
                                ChapterUpdate(id, title)
                            }
                            onSaveChapters(updates)
                            isEditMode = false
                        },
                        enabled = !isBusy,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SapphoInfo
                        )
                    ) {
                        if (isSavingChapters) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text("Save")
                    }
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text("Close", color = SapphoInfo)
                }
            }
        },
        containerColor = SapphoSurfaceLight
    )
}

@Composable
private fun ResultMessage(
    result: String,
    onDismiss: () -> Unit
) {
    val isSuccess = result.contains("success", ignoreCase = true)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = if (isSuccess) SapphoSuccess.copy(alpha = 0.15f) else SapphoError.copy(alpha = 0.15f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = result,
                fontSize = 12.sp,
                color = if (isSuccess) LegacyGreenLight else LegacyRedLight,
                modifier = Modifier.weight(1f)
            )
            TextButton(
                onClick = onDismiss,
                contentPadding = PaddingValues(4.dp)
            ) {
                Text("Dismiss", fontSize = 10.sp, color = SapphoIconDefault)
            }
        }
    }
}

@Composable
private fun AsinInputRow(
    asinInput: String,
    onAsinChange: (String) -> Unit,
    isBusy: Boolean,
    isFetching: Boolean,
    onFetch: () -> Unit,
    onCancel: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = asinInput,
            onValueChange = onAsinChange,
            label = { Text("ASIN", fontSize = 12.sp) },
            modifier = Modifier.weight(1f),
            singleLine = true,
            enabled = !isBusy,
            colors = editTextFieldColors(),
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp)
        )
        Button(
            onClick = onFetch,
            enabled = !isBusy && asinInput.isNotBlank(),
            colors = ButtonDefaults.buttonColors(
                containerColor = LegacyPurple
            ),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
        ) {
            if (isFetching) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
            } else {
                Text("Fetch", fontSize = 12.sp)
            }
        }
        TextButton(
            onClick = onCancel,
            enabled = !isBusy
        ) {
            Text("Cancel", fontSize = 12.sp, color = SapphoIconDefault)
        }
    }
}

@Composable
private fun ChapterEditRow(
    index: Int,
    chapter: Chapter,
    editedTitle: String,
    isBusy: Boolean,
    onTitleChange: (String) -> Unit
) {
    OutlinedTextField(
        value = editedTitle,
        onValueChange = onTitleChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        enabled = !isBusy,
        leadingIcon = {
            Text(
                text = "${index + 1}.",
                color = SapphoIconDefault,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 8.dp)
            )
        },
        trailingIcon = {
            Text(
                text = formatTime(chapter.startTime.toLong()),
                color = SapphoIconDefault,
                fontSize = 10.sp,
                modifier = Modifier.padding(end = 8.dp)
            )
        },
        colors = editTextFieldColors(),
        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp)
    )
}

@Composable
private fun ChapterViewRow(
    index: Int,
    chapter: Chapter,
    isCurrentChapter: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = if (isCurrentChapter) SapphoInfo.copy(alpha = 0.15f) else Color.Transparent
    ) {
        TextButton(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isCurrentChapter) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Currently playing",
                            tint = SapphoInfo,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Text(
                        text = chapter.title ?: "Chapter ${index + 1}",
                        color = if (isCurrentChapter) SapphoInfo else Color.White,
                        fontWeight = if (isCurrentChapter) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.padding(end = 8.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = formatTime(chapter.startTime.toLong()),
                    color = if (isCurrentChapter) SapphoInfo else SapphoIconDefault,
                    fontSize = 12.sp
                )
            }
        }
    }
}

/**
 * Formats time in seconds to a human-readable string (H:MM:SS or M:SS).
 */
private fun formatTime(seconds: Long): String {
    val hours = seconds / 3600
    val mins = (seconds % 3600) / 60
    val secs = seconds % 60
    return if (hours > 0) {
        String.format(java.util.Locale.US, "%d:%02d:%02d", hours, mins, secs)
    } else {
        String.format(java.util.Locale.US, "%d:%02d", mins, secs)
    }
}
