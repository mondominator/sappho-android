package com.sappho.audiobooks.presentation.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.BookmarkAdded
import androidx.compose.material.icons.filled.BookmarkRemove
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material3.*
import androidx.compose.ui.window.Dialog
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.foundation.Canvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.compose.AsyncImage
import com.sappho.audiobooks.domain.model.Audiobook
import com.sappho.audiobooks.presentation.theme.*

@Composable
fun HomeScreen(
    onAudiobookClick: (Int) -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val inProgress by viewModel.inProgress.collectAsState()
    val upNext by viewModel.upNext.collectAsState()
    val recentlyAdded by viewModel.recentlyAdded.collectAsState()
    val finished by viewModel.finished.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val serverUrl by viewModel.serverUrl.collectAsState()
    val isOffline by viewModel.isOffline.collectAsState()
    val downloadedBooks by viewModel.downloadManager.downloadedBooks.collectAsState()

    // Collections state
    val collections by viewModel.collections.collectAsState()
    val bookCollections by viewModel.bookCollections.collectAsState()
    val isLoadingCollections by viewModel.isLoadingCollections.collectAsState()

    // Dialog state
    var showCollectionDialog by remember { mutableStateOf(false) }
    var selectedBookId by remember { mutableIntStateOf(0) }

    // Refresh data every time the screen becomes visible (including navigation back)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    if (isLoading) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(SapphoBackground),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = SapphoInfo)
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(SapphoBackground),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Offline Banner
            if (isOffline) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .background(
                                SapphoWarning.copy(alpha = 0.2f),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudOff,
                            contentDescription = null,
                            tint = SapphoWarning,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Offline mode",
                            fontSize = 14.sp,
                            color = SapphoWarning
                        )
                    }
                }
            }

            // When offline - show Downloaded section prominently at top
            if (isOffline && downloadedBooks.isNotEmpty()) {
                item {
                    AudiobookSection(
                        title = "Downloaded Books",
                        books = downloadedBooks.map { it.audiobook },
                        serverUrl = serverUrl,
                        onAudiobookClick = onAudiobookClick,
                        onToggleFavorite = { id -> viewModel.toggleFavorite(id) },
                        cardSize = 180.dp,
                        titleSize = 18.sp
                    )
                }
            }

            // Only show server content when online
            if (!isOffline) {
                // Continue Listening Section - Larger cards to highlight importance
                if (inProgress.isNotEmpty()) {
                    item {
                        AudiobookSection(
                            title = "Continue Listening",
                            books = inProgress,
                            serverUrl = serverUrl,
                            onAudiobookClick = onAudiobookClick,
                            onToggleFavorite = { id -> viewModel.toggleFavorite(id) },
                            onAddToCollection = { id ->
                                selectedBookId = id
                                viewModel.loadCollectionsForBook(id)
                                showCollectionDialog = true
                            },
                            cardSize = 180.dp,
                            titleSize = 18.sp
                        )
                    }
                }

                // Up Next Section
                if (upNext.isNotEmpty()) {
                    item {
                        AudiobookSection(
                            title = "Up Next",
                            books = upNext,
                            serverUrl = serverUrl,
                            onAudiobookClick = onAudiobookClick,
                            onToggleFavorite = { id -> viewModel.toggleFavorite(id) },
                            onAddToCollection = { id ->
                                selectedBookId = id
                                viewModel.loadCollectionsForBook(id)
                                showCollectionDialog = true
                            }
                        )
                    }
                }

                // Recently Added Section
                if (recentlyAdded.isNotEmpty()) {
                    item {
                        AudiobookSection(
                            title = "Recently Added",
                            books = recentlyAdded,
                            serverUrl = serverUrl,
                            onAudiobookClick = onAudiobookClick,
                            onToggleFavorite = { id -> viewModel.toggleFavorite(id) },
                            onAddToCollection = { id ->
                                selectedBookId = id
                                viewModel.loadCollectionsForBook(id)
                                showCollectionDialog = true
                            }
                        )
                    }
                }

                // Listen Again Section
                if (finished.isNotEmpty()) {
                    item {
                        AudiobookSection(
                            title = "Listen Again",
                            books = finished,
                            serverUrl = serverUrl,
                            onAudiobookClick = onAudiobookClick,
                            onToggleFavorite = { id -> viewModel.toggleFavorite(id) },
                            onAddToCollection = { id ->
                                selectedBookId = id
                                viewModel.loadCollectionsForBook(id)
                                showCollectionDialog = true
                            }
                        )
                    }
                }

                // Downloaded Section - Show at bottom when online
                if (downloadedBooks.isNotEmpty()) {
                    item {
                        AudiobookSection(
                            title = "Downloaded",
                            books = downloadedBooks.map { it.audiobook },
                            serverUrl = serverUrl,
                            onAudiobookClick = onAudiobookClick,
                            onToggleFavorite = { id -> viewModel.toggleFavorite(id) },
                            onAddToCollection = { id ->
                                selectedBookId = id
                                viewModel.loadCollectionsForBook(id)
                                showCollectionDialog = true
                            }
                        )
                    }
                }
            }

            // Empty state
            val hasContent = downloadedBooks.isNotEmpty() || (!isOffline && (inProgress.isNotEmpty() || upNext.isNotEmpty() || recentlyAdded.isNotEmpty() || finished.isNotEmpty()))
            if (!hasContent) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isOffline) {
                                "No downloaded books available.\nConnect to the internet to browse your library."
                            } else {
                                "No audiobooks found.\nAdd some to your server to get started!"
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            color = SapphoIconDefault
                        )
                    }
                }
            }
        }
    }

    // Collection Dialog
    if (showCollectionDialog) {
        AddToCollectionDialog(
            collections = collections,
            bookCollections = bookCollections,
            isLoading = isLoadingCollections,
            onDismiss = { showCollectionDialog = false },
            onToggleCollection = { collectionId ->
                viewModel.toggleBookInCollection(collectionId, selectedBookId)
            },
            onCreateCollection = { name ->
                viewModel.createCollectionAndAddBook(name, selectedBookId)
            }
        )
    }
}

@Composable
fun AudiobookSection(
    title: String,
    books: List<Audiobook>,
    serverUrl: String?,
    onAudiobookClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onToggleFavorite: (Int) -> Unit = {},
    onAddToCollection: (Int) -> Unit = {},
    cardSize: androidx.compose.ui.unit.Dp = 140.dp,
    titleSize: androidx.compose.ui.unit.TextUnit = 16.sp
) {
    Column(modifier = modifier) {
        Text(
            text = title,
            fontSize = titleSize,
            fontWeight = FontWeight.Bold,
            color = SapphoText,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(books) { book ->
                AudiobookCard(
                    book = book,
                    serverUrl = serverUrl,
                    onClick = { onAudiobookClick(book.id) },
                    onToggleFavorite = { onToggleFavorite(book.id) },
                    onAddToCollection = { onAddToCollection(book.id) },
                    cardSize = cardSize
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AudiobookCard(
    book: Audiobook,
    serverUrl: String?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    onToggleFavorite: () -> Unit = {},
    onAddToCollection: () -> Unit = {},
    cardSize: androidx.compose.ui.unit.Dp = 140.dp
) {
    android.util.Log.d("AudiobookCard", "Book: ${book.title}, coverImage: ${book.coverImage}, serverUrl: $serverUrl")
    val textScale = cardSize.value / 140f
    val titleFontSize = (14 * textScale).sp
    val authorFontSize = (12 * textScale).sp
    val placeholderFontSize = (32 * textScale).sp

    var showMenu by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .width(cardSize)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { showMenu = true }
                )
        ) {
            // Cover Image
            Box(
                modifier = Modifier
                    .width(cardSize)
                    .height(cardSize)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (book.coverImage != null && serverUrl != null) {
                    val imageUrl = "$serverUrl/api/audiobooks/${book.id}/cover"
                    android.util.Log.d("AudiobookCard", "Loading cover from: $imageUrl")
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = book.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = book.title.take(2),
                            fontSize = placeholderFontSize,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // Progress Bar
                if (book.progress != null && book.duration != null) {
                    val progressPercent = (book.progress.position.toFloat() / book.duration) * 100
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .align(Alignment.BottomCenter)
                            .background(Color.Black.copy(alpha = 0.3f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progressPercent / 100f)
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                }

                // Reading list ribbon (top-right corner)
                if (book.isFavorite) {
                    ReadingListRibbon(
                        modifier = Modifier.align(Alignment.TopEnd),
                        size = 28f
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Title
            Text(
                text = book.title,
                fontSize = titleFontSize,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                color = SapphoText
            )

            // Author
            if (book.author != null) {
                Text(
                    text = book.author,
                    fontSize = authorFontSize,
                    maxLines = 1,
                    color = SapphoIconDefault
                )
            }
        }

        // Context menu dropdown
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
            modifier = Modifier.background(SapphoSurface)
        ) {
            DropdownMenuItem(
                text = {
                    Text(
                        text = if (book.isFavorite) "Remove from Reading List" else "Add to Reading List",
                        color = SapphoText
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = if (book.isFavorite) Icons.Filled.BookmarkRemove else Icons.Filled.BookmarkAdd,
                        contentDescription = null,
                        tint = SapphoInfo
                    )
                },
                onClick = {
                    showMenu = false
                    onToggleFavorite()
                }
            )
            DropdownMenuItem(
                text = {
                    Text(
                        text = "Add to Collection",
                        color = SapphoText
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.FolderSpecial,
                        contentDescription = null,
                        tint = SapphoInfo
                    )
                },
                onClick = {
                    showMenu = false
                    onAddToCollection()
                }
            )
        }
    }
}

/**
 * Reading list corner ribbon - blue folded ribbon on top-right corner
 */
@Composable
private fun ReadingListRibbon(
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

@Composable
private fun AddToCollectionDialog(
    collections: List<com.sappho.audiobooks.data.remote.Collection>,
    bookCollections: Set<Int>,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onToggleCollection: (Int) -> Unit,
    onCreateCollection: (String) -> Unit
) {
    var showCreateForm by remember { mutableStateOf(false) }
    var newCollectionName by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = SapphoSurface
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Add to Collection",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
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

                Spacer(modifier = Modifier.height(16.dp))

                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = SapphoInfo)
                    }
                } else {
                    // Create new collection form or button
                    if (showCreateForm) {
                        Column {
                            OutlinedTextField(
                                value = newCollectionName,
                                onValueChange = { newCollectionName = it },
                                placeholder = { Text("Collection name", color = SapphoTextMuted) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = SapphoInfo,
                                    unfocusedBorderColor = SapphoProgressTrack,
                                    cursorColor = SapphoInfo
                                ),
                                singleLine = true
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(onClick = {
                                    showCreateForm = false
                                    newCollectionName = ""
                                }) {
                                    Text("Cancel", color = SapphoIconDefault)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        if (newCollectionName.isNotBlank()) {
                                            onCreateCollection(newCollectionName.trim())
                                            newCollectionName = ""
                                            showCreateForm = false
                                        }
                                    },
                                    enabled = newCollectionName.isNotBlank(),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = SapphoInfo
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Create & Add")
                                }
                            }
                        }
                    } else {
                        Button(
                            onClick = { showCreateForm = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SapphoProgressTrack
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("New Collection")
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Collections list
                    if (collections.isEmpty()) {
                        Text(
                            text = "No collections yet",
                            color = SapphoIconDefault,
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 300.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            collections.forEach { collection ->
                                val isInCollection = bookCollections.contains(collection.id)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (isInCollection) SapphoInfo.copy(alpha = 0.2f)
                                            else SapphoProgressTrack
                                        )
                                        .clickable { onToggleCollection(collection.id) }
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = collection.name,
                                        color = Color.White,
                                        fontWeight = if (isInCollection) FontWeight.Bold else FontWeight.Normal
                                    )
                                    if (isInCollection) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "In collection",
                                            tint = SapphoInfo
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
