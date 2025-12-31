package com.sappho.audiobooks.presentation.home

import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.LibraryBooks
import androidx.compose.ui.text.style.TextAlign
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
import androidx.compose.ui.graphics.Brush
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
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.sappho.audiobooks.domain.model.Audiobook
import com.sappho.audiobooks.presentation.theme.*
import com.sappho.audiobooks.presentation.theme.IconSize
import com.sappho.audiobooks.presentation.theme.Spacing

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
        SkeletonHomeScreen()
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(SapphoBackground)
        ) {
            // Sticky Offline Banner
            if (isOffline) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.M, vertical = Spacing.XS)
                        .background(
                            SapphoWarning.copy(alpha = 0.2f),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(Spacing.S),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudOff,
                        contentDescription = null,
                        tint = SapphoWarning,
                        modifier = Modifier.size(IconSize.Medium)
                    )
                    Spacer(modifier = Modifier.width(Spacing.XS))
                    Text(
                        text = "You're offline",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SapphoWarning,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = { viewModel.refresh() },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = SapphoWarning
                        ),
                        contentPadding = PaddingValues(horizontal = Spacing.S, vertical = Spacing.XXS)
                    ) {
                        Text(
                            text = "Retry",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize(),
                contentPadding = PaddingValues(vertical = Spacing.M),
                verticalArrangement = Arrangement.spacedBy(Spacing.L)
            ) {
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
                            titleSize = 20.sp
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

                // Listen Again Section - De-emphasized with smaller cards, no checkmarks (redundant)
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
                            },
                            cardSize = 120.dp,
                            titleSize = 14.sp,
                            showCompletedCheckmark = false
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
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Spacing.XXL),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(Spacing.M)
                    ) {
                        Icon(
                            imageVector = if (isOffline) Icons.Outlined.CloudOff else Icons.Outlined.LibraryBooks,
                            contentDescription = null,
                            modifier = Modifier.size(IconSize.XLarge),
                            tint = SapphoIconDefault
                        )
                        Text(
                            text = if (isOffline) "You're Offline" else "No Audiobooks Yet",
                            style = MaterialTheme.typography.headlineSmall,
                            color = SapphoText
                        )
                        Text(
                            text = if (isOffline) {
                                "Download books while online to listen offline."
                            } else {
                                "Add audiobooks to your server to get started!"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = SapphoIconDefault,
                            textAlign = TextAlign.Center
                        )
                    }
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
    titleSize: androidx.compose.ui.unit.TextUnit = 16.sp,
    showCompletedCheckmark: Boolean = true
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
                    cardSize = cardSize,
                    showCompletedCheckmark = showCompletedCheckmark
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
    cardSize: androidx.compose.ui.unit.Dp = 140.dp,
    showCompletedCheckmark: Boolean = true
) {
    val textScale = cardSize.value / 140f
    val titleFontSize = (14 * textScale).sp
    val authorFontSize = (12 * textScale).sp
    val placeholderFontSize = (32 * textScale).sp

    var showMenu by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .width(cardSize)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        showMenu = true
                    }
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
                val context = LocalContext.current
                val imageUrl = if (book.coverImage != null && serverUrl != null) {
                    "$serverUrl/api/audiobooks/${book.id}/cover"
                } else null

                // Placeholder/fallback content (initials)
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = book.title.take(2).uppercase(),
                        fontSize = placeholderFontSize,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // Cover image with crossfade (overlays placeholder when loaded)
                if (imageUrl != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(imageUrl)
                            .crossfade(300)
                            .build(),
                        contentDescription = book.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }

                // Progress Bar
                if (book.progress != null && book.duration != null) {
                    val progressPercent = (book.progress.position.toFloat() / book.duration) * 100
                    val isCompleted = book.progress.completed == 1
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .align(Alignment.BottomCenter)
                            .background(Color.Black.copy(alpha = 0.5f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(if (isCompleted) 1f else progressPercent / 100f)
                                .fillMaxHeight()
                                .background(
                                    Brush.horizontalGradient(
                                        if (isCompleted)
                                            listOf(SapphoSuccess, LegacyGreenLight)
                                        else
                                            listOf(SapphoInfo, LegacyBlueLight)
                                    )
                                )
                        )
                    }
                }

                // Completed checkmark overlay
                if (showCompletedCheckmark && book.progress?.completed == 1) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                            .size(24.dp)
                            .background(SapphoSuccess, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Completed",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
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
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
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
                containerColor = SapphoSurfaceLight
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
                        style = MaterialTheme.typography.headlineSmall,
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

@Composable
fun SkeletonHomeScreen() {
    val shimmerColors = listOf(
        SapphoSurface.copy(alpha = 0.6f),
        SapphoSurface.copy(alpha = 0.2f),
        SapphoSurface.copy(alpha = 0.6f)
    )

    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnimation = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer"
    )

    val shimmerBrush = Brush.linearGradient(
        colors = shimmerColors,
        start = androidx.compose.ui.geometry.Offset(translateAnimation.value - 200f, 0f),
        end = androidx.compose.ui.geometry.Offset(translateAnimation.value, 0f)
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(SapphoBackground),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Skeleton for Continue Listening (large cards)
        item {
            Column {
                // Section title skeleton
                Box(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .width(160.dp)
                        .height(20.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(shimmerBrush)
                )

                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(3) {
                        SkeletonCard(size = 180.dp, brush = shimmerBrush)
                    }
                }
            }
        }

        // Skeleton for Up Next section
        item {
            Column {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .width(80.dp)
                        .height(18.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(shimmerBrush)
                )

                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(4) {
                        SkeletonCard(size = 140.dp, brush = shimmerBrush)
                    }
                }
            }
        }

        // Skeleton for Recently Added section
        item {
            Column {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .width(120.dp)
                        .height(18.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(shimmerBrush)
                )

                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(4) {
                        SkeletonCard(size = 140.dp, brush = shimmerBrush)
                    }
                }
            }
        }
    }
}

@Composable
private fun SkeletonCard(
    size: Dp,
    brush: Brush
) {
    Column(modifier = Modifier.width(size)) {
        // Cover placeholder
        Box(
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(12.dp))
                .background(brush)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Title placeholder
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(brush)
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Author placeholder
        Box(
            modifier = Modifier
                .width((size.value * 0.7f).dp)
                .height(12.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(brush)
        )
    }
}
