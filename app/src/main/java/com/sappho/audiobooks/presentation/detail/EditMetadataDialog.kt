package com.sappho.audiobooks.presentation.detail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.sappho.audiobooks.data.remote.AudiobookUpdateRequest
import com.sappho.audiobooks.data.remote.MetadataSearchResult
import com.sappho.audiobooks.domain.model.Audiobook
import com.sappho.audiobooks.presentation.theme.*

/**
 * Dialog for editing audiobook metadata with metadata lookup support.
 */
@Composable
fun EditMetadataDialog(
    audiobook: Audiobook,
    isSaving: Boolean,
    isSearching: Boolean,
    isEmbedding: Boolean,
    isFetchingChapters: Boolean,
    searchResults: List<MetadataSearchResult>,
    searchError: String?,
    embedResult: String?,
    fetchChaptersResult: String?,
    onDismiss: () -> Unit,
    onSave: (AudiobookUpdateRequest) -> Unit,
    onSaveAndEmbed: (AudiobookUpdateRequest) -> Unit,
    onSearch: (String, String) -> Unit,
    onClearSearch: () -> Unit,
    onFetchChapters: (String) -> Unit
) {
    var title by remember { mutableStateOf(audiobook.title) }
    var subtitle by remember { mutableStateOf(audiobook.subtitle ?: "") }
    var author by remember { mutableStateOf(audiobook.author ?: "") }
    var narrator by remember { mutableStateOf(audiobook.narrator ?: "") }
    var series by remember { mutableStateOf(audiobook.series ?: "") }
    var seriesPosition by remember { mutableStateOf(audiobook.seriesPosition?.toString() ?: "") }
    var genre by remember { mutableStateOf(audiobook.genre ?: "") }
    var tags by remember { mutableStateOf(audiobook.tags ?: "") }
    var publishedYear by remember { mutableStateOf(audiobook.publishYear?.toString() ?: "") }
    var copyrightYear by remember { mutableStateOf(audiobook.copyrightYear?.toString() ?: "") }
    var publisher by remember { mutableStateOf(audiobook.publisher ?: "") }
    var description by remember { mutableStateOf(audiobook.description ?: "") }
    var isbn by remember { mutableStateOf(audiobook.isbn ?: "") }
    var asin by remember { mutableStateOf(audiobook.asin ?: "") }
    var language by remember { mutableStateOf(audiobook.language ?: "") }
    var bookRating by remember { mutableStateOf(audiobook.rating?.toString() ?: "") }
    var abridged by remember { mutableStateOf((audiobook.abridged ?: 0) == 1) }
    var coverUrl by remember { mutableStateOf("") }
    var showSearchResults by remember { mutableStateOf(false) }
    var selectedResultHasChapters by remember { mutableStateOf(false) }
    var selectedResultForPreview by remember { mutableStateOf<MetadataSearchResult?>(null) }

    // When search results come in, show them
    LaunchedEffect(searchResults) {
        if (searchResults.isNotEmpty()) {
            showSearchResults = true
        }
    }

    val isBusy = isSaving || isSearching || isEmbedding || isFetchingChapters

    Dialog(onDismissRequest = { if (!isBusy) onDismiss() }) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f),
            shape = RoundedCornerShape(16.dp),
            color = SapphoSurfaceLight
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Header with title and X button
                EditMetadataHeader(
                    isBusy = isBusy,
                    onDismiss = onDismiss
                )

                // Scrollable content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Status messages
                    StatusMessages(
                        embedResult = embedResult,
                        fetchChaptersResult = fetchChaptersResult,
                        isEmbedding = isEmbedding,
                        isFetchingChapters = isFetchingChapters
                    )

                    // Lookup Button
                    MetadataLookupButton(
                        isSearching = isSearching,
                        isBusy = isBusy,
                        title = title,
                        author = author,
                        onSearch = onSearch
                    )

                    // Search Results Section
                    if (showSearchResults && searchResults.isNotEmpty()) {
                        SearchResultsSection(
                            searchResults = searchResults,
                            onResultSelect = { result ->
                                selectedResultForPreview = result
                            },
                            onHide = {
                                showSearchResults = false
                                onClearSearch()
                            }
                        )
                    }

                    // Search Error
                    searchError?.let { error ->
                        Text(
                            text = error,
                            fontSize = 12.sp,
                            color = SapphoError
                        )
                    }

                    HorizontalDivider(color = SapphoProgressTrack)

                    // ===== BASIC INFO SECTION =====
                    SectionHeader("Basic Info")

                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Title") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = editTextFieldColors()
                    )

                    OutlinedTextField(
                        value = subtitle,
                        onValueChange = { subtitle = it },
                        label = { Text("Subtitle") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = editTextFieldColors()
                    )

                    OutlinedTextField(
                        value = author,
                        onValueChange = { author = it },
                        label = { Text("Author") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = editTextFieldColors()
                    )

                    OutlinedTextField(
                        value = narrator,
                        onValueChange = { narrator = it },
                        label = { Text("Narrator") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = editTextFieldColors()
                    )

                    // Series row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = series,
                            onValueChange = { series = it },
                            label = { Text("Series") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            colors = editTextFieldColors()
                        )
                        OutlinedTextField(
                            value = seriesPosition,
                            onValueChange = { seriesPosition = it },
                            label = { Text("#") },
                            modifier = Modifier.width(60.dp),
                            singleLine = true,
                            colors = editTextFieldColors()
                        )
                    }

                    HorizontalDivider(color = SapphoProgressTrack, modifier = Modifier.padding(vertical = 4.dp))

                    // ===== CLASSIFICATION SECTION =====
                    SectionHeader("Classification")

                    OutlinedTextField(
                        value = genre,
                        onValueChange = { genre = it },
                        label = { Text("Genre") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = editTextFieldColors()
                    )

                    OutlinedTextField(
                        value = tags,
                        onValueChange = { tags = it },
                        label = { Text("Tags (comma-separated)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = editTextFieldColors()
                    )

                    OutlinedTextField(
                        value = language,
                        onValueChange = { language = it },
                        label = { Text("Language") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = editTextFieldColors()
                    )

                    // Rating and Abridged row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = bookRating,
                            onValueChange = { bookRating = it },
                            label = { Text("Rating") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            colors = editTextFieldColors()
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Checkbox(
                                checked = abridged,
                                onCheckedChange = { abridged = it },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = SapphoInfo,
                                    uncheckedColor = SapphoIconDefault
                                )
                            )
                            Text(
                                text = "Abridged",
                                color = SapphoText,
                                fontSize = 14.sp
                            )
                        }
                    }

                    HorizontalDivider(color = SapphoProgressTrack, modifier = Modifier.padding(vertical = 4.dp))

                    // ===== PUBLISHING SECTION =====
                    SectionHeader("Publishing")

                    OutlinedTextField(
                        value = publisher,
                        onValueChange = { publisher = it },
                        label = { Text("Publisher") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = editTextFieldColors()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = publishedYear,
                            onValueChange = { publishedYear = it },
                            label = { Text("Published Year") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            colors = editTextFieldColors()
                        )
                        OutlinedTextField(
                            value = copyrightYear,
                            onValueChange = { copyrightYear = it },
                            label = { Text("Copyright Year") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            colors = editTextFieldColors()
                        )
                    }

                    HorizontalDivider(color = SapphoProgressTrack, modifier = Modifier.padding(vertical = 4.dp))

                    // ===== IDENTIFIERS SECTION =====
                    SectionHeader("Identifiers")

                    OutlinedTextField(
                        value = isbn,
                        onValueChange = { isbn = it },
                        label = { Text("ISBN") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = editTextFieldColors()
                    )

                    // ASIN with Fetch Chapters button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = asin,
                            onValueChange = {
                                asin = it
                                selectedResultHasChapters = it.matches(Regex("^[A-Z0-9]{10}$", RegexOption.IGNORE_CASE))
                            },
                            label = { Text("ASIN") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            colors = editTextFieldColors()
                        )
                        Button(
                            onClick = { onFetchChapters(asin) },
                            enabled = !isBusy && asin.isNotBlank() && asin.matches(Regex("^[A-Z0-9]{10}$", RegexOption.IGNORE_CASE)),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = LegacyPurple
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.List,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Chapters", fontSize = 12.sp)
                        }
                    }

                    HorizontalDivider(color = SapphoProgressTrack, modifier = Modifier.padding(vertical = 4.dp))

                    // ===== COVER SECTION =====
                    SectionHeader("Cover Image")

                    OutlinedTextField(
                        value = coverUrl,
                        onValueChange = { coverUrl = it },
                        label = { Text("Cover Image URL") },
                        placeholder = { Text("Enter URL to download cover", color = SapphoTextMuted) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = editTextFieldColors()
                    )

                    if (coverUrl.isNotBlank()) {
                        Text(
                            text = "Cover will be downloaded from URL when saved",
                            fontSize = 11.sp,
                            color = SapphoSuccess,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }

                    HorizontalDivider(color = SapphoProgressTrack, modifier = Modifier.padding(vertical = 4.dp))

                    // ===== DESCRIPTION SECTION =====
                    SectionHeader("Description")

                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Description") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 100.dp),
                        maxLines = 5,
                        colors = editTextFieldColors()
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Bottom buttons
                EditMetadataButtons(
                    isBusy = isBusy,
                    isSaving = isSaving,
                    isEmbedding = isEmbedding,
                    onSave = {
                        onSave(buildUpdateRequest(
                            title, subtitle, author, narrator, series, seriesPosition,
                            genre, tags, publishedYear, copyrightYear, publisher,
                            description, isbn, asin, language, bookRating, abridged, coverUrl
                        ))
                    },
                    onSaveAndEmbed = {
                        onSaveAndEmbed(buildUpdateRequest(
                            title, subtitle, author, narrator, series, seriesPosition,
                            genre, tags, publishedYear, copyrightYear, publisher,
                            description, isbn, asin, language, bookRating, abridged, coverUrl
                        ))
                    }
                )
            }
        }
    }

    // Metadata Preview Dialog - shows when a search result is selected
    selectedResultForPreview?.let { result ->
        MetadataPreviewDialog(
            result = result,
            currentTitle = title,
            currentSubtitle = subtitle,
            currentAuthor = author,
            currentNarrator = narrator,
            currentSeries = series,
            currentSeriesPosition = seriesPosition,
            currentGenre = genre,
            currentTags = tags,
            currentPublisher = publisher,
            currentPublishedYear = publishedYear,
            currentCopyrightYear = copyrightYear,
            currentIsbn = isbn,
            currentDescription = description,
            currentLanguage = language,
            currentRating = bookRating,
            currentAsin = asin,
            currentCoverUrl = coverUrl,
            onDismiss = { selectedResultForPreview = null },
            onApply = { selectedFields ->
                // Apply only selected fields
                if (selectedFields.contains("title")) result.title?.let { title = it }
                if (selectedFields.contains("subtitle")) result.subtitle?.let { subtitle = it }
                if (selectedFields.contains("author")) result.author?.let { author = it }
                if (selectedFields.contains("narrator")) result.narrator?.let { narrator = it }
                if (selectedFields.contains("series")) result.series?.let { series = it }
                if (selectedFields.contains("seriesPosition")) result.seriesPosition?.let { seriesPosition = it.toString() }
                if (selectedFields.contains("genre")) result.genre?.let { genre = it }
                if (selectedFields.contains("tags")) result.tags?.let { tags = it }
                if (selectedFields.contains("publisher")) result.publisher?.let { publisher = it }
                if (selectedFields.contains("publishedYear")) result.publishedYear?.let { publishedYear = it.toString() }
                if (selectedFields.contains("copyrightYear")) result.copyrightYear?.let { copyrightYear = it.toString() }
                if (selectedFields.contains("isbn")) result.isbn?.let { isbn = it }
                if (selectedFields.contains("description")) result.description?.let { description = it }
                if (selectedFields.contains("language")) result.language?.let { language = it }
                if (selectedFields.contains("rating")) result.rating?.let { bookRating = it.toString() }
                if (selectedFields.contains("asin")) result.asin?.let { asin = it }
                if (selectedFields.contains("coverUrl")) result.image?.let { coverUrl = it }

                selectedResultHasChapters = result.hasChapters == true && result.asin != null
                selectedResultForPreview = null
                showSearchResults = false
            }
        )
    }
}

@Composable
private fun EditMetadataHeader(
    isBusy: Boolean,
    onDismiss: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Edit Metadata",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp
        )
        IconButton(
            onClick = onDismiss,
            enabled = !isBusy,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close",
                tint = if (!isBusy) SapphoIconDefault else LegacyGrayDark
            )
        }
    }
}

@Composable
private fun StatusMessages(
    embedResult: String?,
    fetchChaptersResult: String?,
    isEmbedding: Boolean,
    isFetchingChapters: Boolean
) {
    // Embed result message
    embedResult?.let { result ->
        val isSuccess = result.contains("success", ignoreCase = true)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            color = if (isSuccess) SapphoSuccess.copy(alpha = 0.15f) else SapphoError.copy(alpha = 0.15f)
        ) {
            Text(
                text = result,
                fontSize = 12.sp,
                color = if (isSuccess) LegacyGreenLight else LegacyRedLight,
                modifier = Modifier.padding(12.dp)
            )
        }
    }

    // Fetch chapters result message
    fetchChaptersResult?.let { result ->
        val isSuccess = result.contains("success", ignoreCase = true)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            color = if (isSuccess) SapphoSuccess.copy(alpha = 0.15f) else SapphoError.copy(alpha = 0.15f)
        ) {
            Text(
                text = result,
                fontSize = 12.sp,
                color = if (isSuccess) LegacyGreenLight else LegacyRedLight,
                modifier = Modifier.padding(12.dp)
            )
        }
    }

    // Embedding progress indicator
    if (isEmbedding) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                color = SapphoSuccess,
                strokeWidth = 2.dp
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Embedding metadata into file...",
                fontSize = 12.sp,
                color = LegacyGreenLight
            )
        }
    }

    // Fetching chapters progress indicator
    if (isFetchingChapters) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                color = LegacyPurple,
                strokeWidth = 2.dp
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Fetching chapters from Audnexus...",
                fontSize = 12.sp,
                color = LegacyPurpleLight
            )
        }
    }
}

@Composable
private fun MetadataLookupButton(
    isSearching: Boolean,
    isBusy: Boolean,
    title: String,
    author: String,
    onSearch: (String, String) -> Unit
) {
    Button(
        onClick = { onSearch(title, author) },
        modifier = Modifier.fillMaxWidth(),
        enabled = !isBusy && (title.isNotBlank() || author.isNotBlank()),
        colors = ButtonDefaults.buttonColors(
            containerColor = LegacyPurple.copy(alpha = 0.15f),
            contentColor = LegacyPurpleLight
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        if (isSearching) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                color = LegacyPurpleLight,
                strokeWidth = 2.dp
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Searching...")
        } else {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Lookup Metadata")
        }
    }
}

@Composable
private fun SearchResultsSection(
    searchResults: List<MetadataSearchResult>,
    onResultSelect: (MetadataSearchResult) -> Unit,
    onHide: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = SapphoProgressTrack.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, LegacyPurple.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${searchResults.size} Results",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = LegacyPurpleLight
                )
                TextButton(
                    onClick = onHide,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("Hide", color = SapphoIconDefault, fontSize = 12.sp)
                }
            }

            searchResults.forEach { result ->
                MetadataSearchResultItem(
                    result = result,
                    onSelect = { onResultSelect(result) }
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        color = SapphoIconDefault,
        modifier = Modifier.padding(top = 4.dp)
    )
}

@Composable
private fun EditMetadataButtons(
    isBusy: Boolean,
    isSaving: Boolean,
    isEmbedding: Boolean,
    onSave: () -> Unit,
    onSaveAndEmbed: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(
            onClick = onSave,
            enabled = !isBusy,
            colors = ButtonDefaults.buttonColors(containerColor = SapphoInfo),
            modifier = Modifier.weight(1f)
        ) {
            if (isSaving && !isEmbedding) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text("Save")
        }

        Button(
            onClick = onSaveAndEmbed,
            enabled = !isBusy,
            colors = ButtonDefaults.buttonColors(containerColor = SapphoSuccess),
            modifier = Modifier.weight(1f)
        ) {
            if (isSaving && isEmbedding) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text("Save & Embed")
        }
    }
}

private fun buildUpdateRequest(
    title: String,
    subtitle: String,
    author: String,
    narrator: String,
    series: String,
    seriesPosition: String,
    genre: String,
    tags: String,
    publishedYear: String,
    copyrightYear: String,
    publisher: String,
    description: String,
    isbn: String,
    asin: String,
    language: String,
    bookRating: String,
    abridged: Boolean,
    coverUrl: String
): AudiobookUpdateRequest {
    return AudiobookUpdateRequest(
        title = title.ifBlank { null },
        subtitle = subtitle.ifBlank { null },
        author = author.ifBlank { null },
        narrator = narrator.ifBlank { null },
        series = series.ifBlank { null },
        seriesPosition = seriesPosition.toFloatOrNull(),
        genre = genre.ifBlank { null },
        tags = tags.ifBlank { null },
        publishedYear = publishedYear.toIntOrNull(),
        copyrightYear = copyrightYear.toIntOrNull(),
        publisher = publisher.ifBlank { null },
        description = description.ifBlank { null },
        isbn = isbn.ifBlank { null },
        asin = asin.ifBlank { null },
        language = language.ifBlank { null },
        rating = bookRating.toFloatOrNull(),
        abridged = if (abridged) true else null,
        coverUrl = coverUrl.ifBlank { null }
    )
}

@Composable
internal fun editTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    focusedBorderColor = SapphoInfo,
    unfocusedBorderColor = SapphoProgressTrack,
    focusedLabelColor = SapphoInfo,
    unfocusedLabelColor = SapphoIconDefault,
    cursorColor = SapphoInfo
)
