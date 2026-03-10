package com.sappho.audiobooks.presentation.readinglist

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.BookmarkAdded
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.sappho.audiobooks.domain.model.Audiobook
import com.sappho.audiobooks.presentation.theme.*
import com.sappho.audiobooks.util.COVER_WIDTH_THUMBNAIL
import com.sappho.audiobooks.util.buildCoverUrl
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun ReadingListScreen(
    onAudiobookClick: (Int) -> Unit = {},
    onBackClick: () -> Unit = {},
    viewModel: ReadingListViewModel = hiltViewModel()
) {
    val books by viewModel.books.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val serverUrl by viewModel.serverUrl.collectAsState()
    val sortOption by viewModel.sortOption.collectAsState()

    BackHandler { onBackClick() }

    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SapphoBackground)
    ) {
        // Header
        ReadingListHeader(
            bookCount = books.size,
            sortOption = sortOption,
            onBackClick = onBackClick,
            onSortSelected = { viewModel.setSortOption(it) }
        )

        when {
            isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = SapphoInfo)
                }
            }
            books.isEmpty() -> {
                ReadingListEmptyState()
            }
            else -> {
                ReadingListContent(
                    books = books,
                    serverUrl = serverUrl,
                    sortOption = sortOption,
                    onAudiobookClick = onAudiobookClick,
                    onReorder = { from, to -> viewModel.reorderBooks(from, to) },
                    onRemove = { id -> viewModel.removeBook(id) }
                )
            }
        }
    }
}

@Composable
private fun ReadingListHeader(
    bookCount: Int,
    sortOption: String,
    onBackClick: () -> Unit,
    onSortSelected: (String) -> Unit
) {
    var sortMenuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBackClick) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = SapphoText
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Reading List",
                style = MaterialTheme.typography.headlineLarge,
                color = SapphoText
            )
            Text(
                text = "$bookCount books to read",
                style = MaterialTheme.typography.bodySmall,
                color = SapphoIconDefault
            )
        }

        // Sort dropdown
        Box {
            TextButton(onClick = { sortMenuExpanded = true }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Sort,
                    contentDescription = "Sort",
                    tint = SapphoInfo,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = sortDisplayName(sortOption),
                    color = SapphoInfo,
                    style = MaterialTheme.typography.labelLarge
                )
            }

            DropdownMenu(
                expanded = sortMenuExpanded,
                onDismissRequest = { sortMenuExpanded = false }
            ) {
                SortOption.entries.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = option.displayName,
                                fontWeight = if (sortOption == option.value)
                                    FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        onClick = {
                            onSortSelected(option.value)
                            sortMenuExpanded = false
                        }
                    )
                }
            }
        }
    }
}

private enum class SortOption(val value: String, val displayName: String) {
    CUSTOM("custom", "Custom"),
    TITLE("title", "Title"),
    DATE("date", "Date Added")
}

private fun sortDisplayName(value: String): String =
    SortOption.entries.firstOrNull { it.value == value }?.displayName ?: "Custom"

@Composable
private fun ReadingListEmptyState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.BookmarkAdded,
                contentDescription = "Empty reading list",
                tint = SapphoInfo,
                modifier = Modifier.size(48.dp)
            )
            Text(
                text = "Your reading list is empty",
                style = MaterialTheme.typography.titleLarge,
                color = SapphoText
            )
            Text(
                text = "Add books to your reading list from the book detail page",
                style = MaterialTheme.typography.bodyMedium,
                color = SapphoIconDefault
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReadingListContent(
    books: List<Audiobook>,
    serverUrl: String?,
    sortOption: String,
    onAudiobookClick: (Int) -> Unit,
    onReorder: (Int, Int) -> Unit,
    onRemove: (Int) -> Unit
) {
    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        onReorder(from.index, to.index)
    }
    val canDrag = sortOption == "custom"

    LazyColumn(
        state = lazyListState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        itemsIndexed(books, key = { _, book -> book.id }) { index, book ->
            ReorderableItem(reorderableState, key = book.id) { isDragging ->
                val dragHandleModifier = if (canDrag) {
                    Modifier.draggableHandle()
                } else {
                    Modifier
                }

                val dismissState = rememberSwipeToDismissBoxState(
                    confirmValueChange = { value ->
                        if (value == SwipeToDismissBoxValue.EndToStart) {
                            onRemove(book.id)
                            true
                        } else {
                            false
                        }
                    }
                )

                SwipeToDismissBox(
                    state = dismissState,
                    backgroundContent = {
                        SwipeDeleteBackground(dismissState)
                    },
                    enableDismissFromStartToEnd = false
                ) {
                    ReadingListRow(
                        number = index + 1,
                        book = book,
                        serverUrl = serverUrl,
                        isDragging = isDragging,
                        canDrag = canDrag,
                        dragHandleModifier = dragHandleModifier,
                        onClick = { onAudiobookClick(book.id) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeDeleteBackground(dismissState: SwipeToDismissBoxState) {
    val color by animateColorAsState(
        targetValue = when (dismissState.targetValue) {
            SwipeToDismissBoxValue.EndToStart -> SapphoError
            else -> Color.Transparent
        },
        label = "swipe-bg"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(8.dp))
            .background(color)
            .padding(end = 20.dp),
        contentAlignment = Alignment.CenterEnd
    ) {
        if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = "Delete",
                tint = Color.White
            )
        }
    }
}

@Composable
private fun ReadingListRow(
    number: Int,
    book: Audiobook,
    serverUrl: String?,
    isDragging: Boolean,
    canDrag: Boolean,
    dragHandleModifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val backgroundColor = if (isDragging) SapphoSurfaceElevated else SapphoSurface

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Number
        Text(
            text = "$number",
            style = MaterialTheme.typography.titleMedium,
            color = SapphoInfo,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(28.dp)
        )

        // Cover thumbnail
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(SapphoProgressTrack)
        ) {
            if (book.coverImage != null && serverUrl != null) {
                AsyncImage(
                    model = buildCoverUrl(serverUrl, book.id, COVER_WIDTH_THUMBNAIL),
                    contentDescription = book.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(SapphoProgressTrack, SapphoSurfaceDark)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = book.title.take(2).uppercase(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = SapphoText,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Progress bar
            val progress = book.progress
            if (progress != null && book.duration != null && book.duration > 0) {
                val progressPercent = if (progress.completed == 1) 1f
                else (progress.position.toFloat() / book.duration.toFloat()).coerceIn(0f, 1f)

                if (progressPercent > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .height(3.dp)
                            .background(Color.Black.copy(alpha = 0.7f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(progressPercent)
                                .background(
                                    if (progress.completed == 1) SapphoSuccess else SapphoInfo
                                )
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Title + author/duration
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = book.title,
                style = MaterialTheme.typography.bodyLarge,
                color = SapphoText,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            val subtitle = buildSubtitle(book)
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = SapphoIconDefault,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Drag handle (only for custom sort)
        if (canDrag) {
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Filled.DragHandle,
                contentDescription = "Reorder",
                tint = SapphoIconDefault,
                modifier = dragHandleModifier.size(24.dp)
            )
        }
    }
}

private fun buildSubtitle(book: Audiobook): String {
    val parts = mutableListOf<String>()
    book.author?.let { parts.add(it) }
    book.duration?.let { if (it > 0) parts.add(formatDuration(it)) }
    return parts.joinToString(" · ")
}

private fun formatDuration(totalSeconds: Int): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}

/**
 * Reading list corner ribbon - blue folded ribbon on top-right corner.
 * Used by LibraryScreen and HomeScreen for reading list indicators.
 */
@Composable
fun ReadingListRibbon(
    modifier: Modifier = Modifier,
    size: Float = 32f
) {
    Canvas(
        modifier = modifier.size(size.dp)
    ) {
        val ribbonColor = SapphoInfo
        val shadowColor = LegacyBlueDark

        // Main triangle (folded corner)
        val trianglePath = Path().apply {
            moveTo(0f, 0f)
            lineTo(this@Canvas.size.width, 0f)
            lineTo(this@Canvas.size.width, this@Canvas.size.height)
            close()
        }
        drawPath(trianglePath, ribbonColor, style = Fill)

        // Shadow fold line (darker edge to create 3D folded effect)
        val foldPath = Path().apply {
            moveTo(0f, 0f)
            lineTo(this@Canvas.size.width * 0.3f, this@Canvas.size.height * 0.3f)
            lineTo(this@Canvas.size.width * 0.4f, this@Canvas.size.height * 0.25f)
            lineTo(this@Canvas.size.width * 0.1f, 0f)
            close()
        }
        drawPath(foldPath, shadowColor.copy(alpha = 0.4f), style = Fill)
    }
}
