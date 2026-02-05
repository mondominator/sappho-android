package com.sappho.audiobooks.presentation.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.sappho.audiobooks.data.remote.MetadataSearchResult
import com.sappho.audiobooks.presentation.theme.*

/**
 * Dialog for previewing and selecting which metadata fields to apply from a search result.
 */
@Composable
internal fun MetadataPreviewDialog(
    result: MetadataSearchResult,
    currentTitle: String,
    currentSubtitle: String,
    currentAuthor: String,
    currentNarrator: String,
    currentSeries: String,
    currentSeriesPosition: String,
    currentGenre: String,
    currentTags: String,
    currentPublisher: String,
    currentPublishedYear: String,
    currentCopyrightYear: String,
    currentIsbn: String,
    currentDescription: String,
    currentLanguage: String,
    currentRating: String,
    currentAsin: String,
    currentCoverUrl: String,
    onDismiss: () -> Unit,
    onApply: (Set<String>) -> Unit
) {
    // Track which fields are selected
    val selectedFields = remember { mutableStateMapOf<String, Boolean>() }

    // Initialize with fields that have new values different from current
    LaunchedEffect(result) {
        if (result.title != null && result.title != currentTitle) selectedFields["title"] = true
        if (result.subtitle != null && result.subtitle != currentSubtitle) selectedFields["subtitle"] = true
        if (result.author != null && result.author != currentAuthor) selectedFields["author"] = true
        if (result.narrator != null && result.narrator != currentNarrator) selectedFields["narrator"] = true
        if (result.series != null && result.series != currentSeries) selectedFields["series"] = true
        if (result.seriesPosition != null && result.seriesPosition.toString() != currentSeriesPosition) selectedFields["seriesPosition"] = true
        if (result.genre != null && result.genre != currentGenre) selectedFields["genre"] = true
        if (result.tags != null && result.tags != currentTags) selectedFields["tags"] = true
        if (result.publisher != null && result.publisher != currentPublisher) selectedFields["publisher"] = true
        if (result.publishedYear != null && result.publishedYear.toString() != currentPublishedYear) selectedFields["publishedYear"] = true
        if (result.copyrightYear != null && result.copyrightYear.toString() != currentCopyrightYear) selectedFields["copyrightYear"] = true
        if (result.isbn != null && result.isbn != currentIsbn) selectedFields["isbn"] = true
        if (result.description != null && result.description != currentDescription) selectedFields["description"] = true
        if (result.language != null && result.language != currentLanguage) selectedFields["language"] = true
        if (result.rating != null && result.rating.toString() != currentRating) selectedFields["rating"] = true
        if (result.asin != null && result.asin != currentAsin) selectedFields["asin"] = true
        if (result.image != null && result.image != currentCoverUrl) selectedFields["coverUrl"] = true
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(16.dp),
            color = SapphoSurfaceLight
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Apply Metadata",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = SapphoIconDefault
                        )
                    }
                }

                // Scrollable content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Select fields to apply:",
                        fontSize = 12.sp,
                        color = SapphoIconDefault
                    )

                    // Field rows with checkboxes
                    FieldPreviewRowIfChanged(result.title, currentTitle, "Title", "title", selectedFields)
                    FieldPreviewRowIfChanged(result.subtitle, currentSubtitle, "Subtitle", "subtitle", selectedFields)
                    FieldPreviewRowIfChanged(result.author, currentAuthor, "Author", "author", selectedFields)
                    FieldPreviewRowIfChanged(result.narrator, currentNarrator, "Narrator", "narrator", selectedFields)
                    FieldPreviewRowIfChanged(result.series, currentSeries, "Series", "series", selectedFields)
                    FieldPreviewRowIfChanged(result.seriesPosition?.toString(), currentSeriesPosition, "Series #", "seriesPosition", selectedFields)
                    FieldPreviewRowIfChanged(result.genre, currentGenre, "Genre", "genre", selectedFields)
                    FieldPreviewRowIfChanged(result.tags, currentTags, "Tags", "tags", selectedFields)
                    FieldPreviewRowIfChanged(result.publisher, currentPublisher, "Publisher", "publisher", selectedFields)
                    FieldPreviewRowIfChanged(result.publishedYear?.toString(), currentPublishedYear, "Published Year", "publishedYear", selectedFields)
                    FieldPreviewRowIfChanged(result.copyrightYear?.toString(), currentCopyrightYear, "Copyright Year", "copyrightYear", selectedFields)
                    FieldPreviewRowIfChanged(result.isbn, currentIsbn, "ISBN", "isbn", selectedFields)
                    FieldPreviewRowIfChanged(result.language, currentLanguage, "Language", "language", selectedFields)
                    FieldPreviewRowIfChanged(result.rating?.toString(), currentRating, "Rating", "rating", selectedFields)
                    FieldPreviewRowIfChanged(result.asin, currentAsin, "ASIN", "asin", selectedFields)

                    // Cover URL - special handling
                    result.image?.let { newValue ->
                        if (newValue != currentCoverUrl) {
                            FieldPreviewRow(
                                fieldName = "Cover URL",
                                fieldKey = "coverUrl",
                                oldValue = if (currentCoverUrl.isBlank()) "(empty)" else "(current)",
                                newValue = "(new cover)",
                                isSelected = selectedFields["coverUrl"] ?: false,
                                onSelectionChange = { selectedFields["coverUrl"] = it }
                            )
                        }
                    }

                    // Description - special handling for truncation
                    result.description?.let { newValue ->
                        if (newValue != currentDescription) {
                            FieldPreviewRow(
                                fieldName = "Description",
                                fieldKey = "description",
                                oldValue = if (currentDescription.isBlank()) "(empty)" else "(${currentDescription.take(30)}...)",
                                newValue = "(${newValue.take(30)}...)",
                                isSelected = selectedFields["description"] ?: false,
                                onSelectionChange = { selectedFields["description"] = it }
                            )
                        }
                    }

                    if (selectedFields.isEmpty()) {
                        Text(
                            text = "No new values to apply - all fields match current values.",
                            fontSize = 12.sp,
                            color = SapphoTextMuted,
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Bottom button
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = SapphoSurfaceLight
                ) {
                    Button(
                        onClick = {
                            onApply(selectedFields.filter { it.value }.keys)
                        },
                        enabled = selectedFields.any { it.value },
                        colors = ButtonDefaults.buttonColors(containerColor = SapphoInfo),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text("Apply Selected")
                    }
                }
            }
        }
    }
}

@Composable
private fun FieldPreviewRowIfChanged(
    newValue: String?,
    currentValue: String,
    fieldName: String,
    fieldKey: String,
    selectedFields: MutableMap<String, Boolean>
) {
    newValue?.let { value ->
        if (value != currentValue) {
            FieldPreviewRow(
                fieldName = fieldName,
                fieldKey = fieldKey,
                oldValue = currentValue.ifBlank { "(empty)" },
                newValue = value,
                isSelected = selectedFields[fieldKey] ?: false,
                onSelectionChange = { selectedFields[fieldKey] = it }
            )
        }
    }
}

@Composable
internal fun FieldPreviewRow(
    fieldName: String,
    fieldKey: String,
    oldValue: String,
    newValue: String,
    isSelected: Boolean,
    onSelectionChange: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(6.dp),
        color = if (isSelected) SapphoInfo.copy(alpha = 0.1f) else Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onSelectionChange(!isSelected) }
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = onSelectionChange,
                colors = CheckboxDefaults.colors(
                    checkedColor = SapphoInfo,
                    uncheckedColor = SapphoTextMuted
                ),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = fieldName,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = oldValue,
                        fontSize = 11.sp,
                        color = SapphoError,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Text(
                        text = " → ",
                        fontSize = 11.sp,
                        color = SapphoTextMuted
                    )
                    Text(
                        text = newValue,
                        fontSize = 11.sp,
                        color = SapphoSuccess,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
            }
        }
    }
}

/**
 * Displays a metadata search result item with cover, title, author, and source badge.
 */
@Composable
internal fun MetadataSearchResultItem(
    result: MetadataSearchResult,
    onSelect: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect),
        shape = RoundedCornerShape(6.dp),
        color = SapphoSurfaceLight
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Cover image preview
            if (!result.image.isNullOrBlank()) {
                AsyncImage(
                    model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                        .data(result.image)
                        .crossfade(true)
                        .build(),
                    contentDescription = "Cover",
                    modifier = Modifier
                        .size(50.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Surface(
                    modifier = Modifier.size(50.dp),
                    shape = RoundedCornerShape(4.dp),
                    color = SapphoProgressTrack
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = null,
                            tint = SapphoTextMuted,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            // Text content
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = result.title ?: "Unknown Title",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Chapter availability indicator
                        if (result.hasChapters == true) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = LegacyPurple.copy(alpha = 0.2f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.List,
                                        contentDescription = null,
                                        tint = LegacyPurpleLight,
                                        modifier = Modifier.size(10.dp)
                                    )
                                    Text(
                                        text = "Ch",
                                        fontSize = 10.sp,
                                        color = LegacyPurpleLight
                                    )
                                }
                            }
                        }
                        // Source badge
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = when (result.source.lowercase()) {
                                "audible" -> LegacyOrange.copy(alpha = 0.2f)
                                "google" -> SapphoInfo.copy(alpha = 0.2f)
                                else -> SapphoSuccess.copy(alpha = 0.2f)
                            }
                        ) {
                            Text(
                                text = result.source.replaceFirstChar { it.uppercase() },
                                fontSize = 10.sp,
                                color = when (result.source.lowercase()) {
                                    "audible" -> SapphoWarning
                                    "google" -> LegacyBlueLight
                                    else -> LegacyGreenLight
                                },
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                result.author?.let { author ->
                    Text(
                        text = "by $author",
                        fontSize = 12.sp,
                        color = SapphoIconDefault,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    result.narrator?.let { narrator ->
                        Text(
                            text = "Narrated: $narrator",
                            fontSize = 11.sp,
                            color = SapphoTextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                    result.series?.let { series ->
                        val seriesText = if (result.seriesPosition != null) {
                            "$series #${result.seriesPosition}"
                        } else {
                            series
                        }
                        Text(
                            text = seriesText,
                            fontSize = 11.sp,
                            color = LegacyPurple,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
