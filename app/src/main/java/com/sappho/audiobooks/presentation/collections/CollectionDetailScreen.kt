package com.sappho.audiobooks.presentation.collections

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.sappho.audiobooks.domain.model.Audiobook
import com.sappho.audiobooks.presentation.theme.*
import com.sappho.audiobooks.util.HapticPatterns

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CollectionDetailScreen(
    collectionId: Int,
    onBookClick: (Int) -> Unit = {},
    onBackClick: () -> Unit = {},
    viewModel: CollectionDetailViewModel = hiltViewModel()
) {
    val collection by viewModel.collection.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val serverUrl by viewModel.serverUrl.collectAsState()
    val error by viewModel.error.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()

    var showEditDialog by remember { mutableStateOf(false) }
    var isEditMode by remember { mutableStateOf(false) }
    var selectedBooks by remember { mutableStateOf(setOf<Int>()) }
    var showRemoveDialog by remember { mutableStateOf(false) }

    // Haptic patterns
    val cardTapHaptic = HapticPatterns.cardTap()
    val buttonPressHaptic = HapticPatterns.buttonPress()
    val mediumTapHaptic = HapticPatterns.mediumTap()

    // Progressive reveal tracking
    var contentRevealed by remember { mutableStateOf(false) }
    LaunchedEffect(collection?.books) {
        if (!collection?.books.isNullOrEmpty()) {
            contentRevealed = true
        }
    }

    // Handle back button - exit edit mode first, then go back
    BackHandler {
        if (isEditMode) {
            isEditMode = false
            selectedBooks = emptySet()
        } else {
            onBackClick()
        }
    }

    LaunchedEffect(collectionId) {
        viewModel.loadCollection(collectionId)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SapphoBackground)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.M),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.S),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    IconButton(onClick = {
                        buttonPressHaptic()
                        if (isEditMode) {
                            isEditMode = false
                            selectedBooks = emptySet()
                        } else {
                            onBackClick()
                        }
                    }) {
                        Icon(
                            imageVector = if (isEditMode) Icons.Default.Close else Icons.Default.ArrowBack,
                            contentDescription = if (isEditMode) "Cancel" else "Back",
                            tint = SapphoText
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isEditMode) "${selectedBooks.size} selected" else (collection?.name ?: "Collection"),
                            style = MaterialTheme.typography.headlineLarge,
                            color = SapphoText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (!isEditMode) {
                            collection?.description?.let { desc ->
                                if (desc.isNotBlank()) {
                                    Text(
                                        text = desc,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = SapphoIconDefault,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                // Action buttons
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                    if (isEditMode) {
                        // Remove button with animated visibility
                        SapphoAnimatedVisibility(visible = selectedBooks.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    buttonPressHaptic()
                                    showRemoveDialog = true
                                },
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(SapphoError, CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Remove Selected",
                                    tint = Color.White
                                )
                            }
                        }
                    } else {
                        // Edit button
                        IconButton(
                            onClick = {
                                buttonPressHaptic()
                                showEditDialog = true
                            },
                            modifier = Modifier
                                .size(40.dp)
                                .background(SapphoProgressTrack, CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit Collection",
                                tint = Color.White,
                                modifier = Modifier.size(IconSize.Medium)
                            )
                        }
                    }
                }
            }

            when {
                isLoading && collection?.books.isNullOrEmpty() -> {
                    SkeletonLibraryGrid()
                }
                error != null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(Spacing.XS)
                        ) {
                            Text(
                                text = error ?: "Error loading collection",
                                style = MaterialTheme.typography.bodyLarge,
                                color = SapphoError
                            )
                            Button(
                                onClick = {
                                    buttonPressHaptic()
                                    viewModel.refresh(collectionId)
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = SapphoInfo
                                )
                            ) {
                                Text("Retry")
                            }
                        }
                    }
                }
                collection?.books.isNullOrEmpty() -> {
                    // Empty state
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(Spacing.XL),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.LibraryBooks,
                            contentDescription = null,
                            tint = SapphoInfo,
                            modifier = Modifier.size(IconSize.XLarge)
                        )
                        Spacer(modifier = Modifier.height(Spacing.M))
                        Text(
                            text = "No books in this collection",
                            style = MaterialTheme.typography.titleLarge,
                            color = SapphoText
                        )
                        Spacer(modifier = Modifier.height(Spacing.XS))
                        Text(
                            text = "Add books from the book detail page",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SapphoIconDefault
                        )
                    }
                }
                else -> {
                    PullToRefreshBox(
                        isRefreshing = isLoading,
                        onRefresh = { viewModel.refresh(collectionId) },
                        modifier = Modifier.fillMaxSize()
                    ) {
                        LazyVerticalGrid(
                            columns = AdaptiveGrid.audiobookGrid,
                            contentPadding = PaddingValues(AdaptiveSpacing.screenPadding),
                            horizontalArrangement = Arrangement.spacedBy(AdaptiveSpacing.gridSpacing),
                            verticalArrangement = Arrangement.spacedBy(AdaptiveSpacing.gridSpacing),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            itemsIndexed(collection?.books ?: emptyList()) { index, book ->
                                CollectionBookItem(
                                    book = book,
                                    serverUrl = serverUrl,
                                    isEditMode = isEditMode,
                                    isSelected = selectedBooks.contains(book.id),
                                    revealed = contentRevealed,
                                    index = index,
                                    onClick = {
                                        cardTapHaptic()
                                        if (isEditMode) {
                                            selectedBooks = if (selectedBooks.contains(book.id)) {
                                                selectedBooks - book.id
                                            } else {
                                                selectedBooks + book.id
                                            }
                                        } else {
                                            onBookClick(book.id)
                                        }
                                    },
                                    onLongClick = {
                                        mediumTapHaptic()
                                        if (!isEditMode) {
                                            isEditMode = true
                                            selectedBooks = setOf(book.id)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Edit Collection Dialog
        if (showEditDialog && collection != null) {
            EditCollectionDialog(
                name = collection?.name ?: "",
                description = collection?.description ?: "",
                isPublic = collection?.isPublic == 1,
                isOwner = collection?.isOwner == 1,
                isSaving = isSaving,
                onDismiss = { showEditDialog = false },
                onSave = { name, description, isPublic ->
                    viewModel.updateCollection(collectionId, name, description, isPublic) { success, _ ->
                        if (success) {
                            showEditDialog = false
                        }
                    }
                }
            )
        }

        // Remove Books Confirmation Dialog
        if (showRemoveDialog && selectedBooks.isNotEmpty()) {
            val count = selectedBooks.size
            AlertDialog(
                onDismissRequest = { showRemoveDialog = false },
                title = { Text("Remove Books") },
                text = {
                    Text(
                        if (count == 1) "Are you sure you want to remove this book from the collection?"
                        else "Are you sure you want to remove $count books from this collection?"
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            selectedBooks.forEach { bookId ->
                                viewModel.removeBook(collectionId, bookId) { _, _ -> }
                            }
                            showRemoveDialog = false
                            selectedBooks = emptySet()
                            isEditMode = false
                        },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = SapphoError
                        )
                    ) {
                        Text("Remove")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showRemoveDialog = false }) {
                        Text("Cancel")
                    }
                },
                containerColor = SapphoSurface,
                titleContentColor = SapphoText,
                textContentColor = SapphoTextLight
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CollectionBookItem(
    book: Audiobook,
    serverUrl: String?,
    isEditMode: Boolean,
    isSelected: Boolean,
    revealed: Boolean,
    index: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    // Bouncy scale animation
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "book_scale"
    )

    Column(
        modifier = Modifier
            .progressiveReveal(index = index, visible = revealed)
            .scale(scale)
            .then(
                if (isSelected) {
                    Modifier.border(2.dp, SapphoInfo, RoundedCornerShape(8.dp))
                } else {
                    Modifier
                }
            )
            .combinedClickable(
                interactionSource = interactionSource,
                indication = ripple(),
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        // Square book cover
        Box(
            modifier = Modifier
                .aspectRatio(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(SapphoProgressTrack)
        ) {
            if (book.coverImage != null && serverUrl != null) {
                AsyncImage(
                    model = com.sappho.audiobooks.util.buildCoverUrl(serverUrl, book.id, com.sappho.audiobooks.util.COVER_WIDTH_THUMBNAIL),
                    contentDescription = book.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
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
                        style = MaterialTheme.typography.headlineLarge,
                        color = SapphoText
                    )
                }
            }

            // Selection checkbox (top-left, only in edit mode)
            SapphoAnimatedVisibility(
                visible = isEditMode,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(Spacing.XXS)
            ) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .background(
                            if (isSelected) SapphoInfo else Color.Black.copy(alpha = 0.5f),
                            CircleShape
                        )
                        .then(
                            if (!isSelected) {
                                Modifier.border(2.dp, Color.White.copy(alpha = 0.5f), CircleShape)
                            } else {
                                Modifier
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            // Animated progress bar (bottom)
            book.progress?.let { progress ->
                if (progress.position > 0 || progress.completed == 1) {
                    val progressPercent = if (progress.completed == 1) {
                        1f
                    } else if (book.duration != null && book.duration > 0) {
                        (progress.position.toFloat() / book.duration).coerceIn(0f, 1f)
                    } else {
                        0f
                    }

                    if (progressPercent > 0) {
                        val animatedProgress by animateFloatAsState(
                            targetValue = progressPercent,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessLow
                            ),
                            label = "progress"
                        )

                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .height(3.dp)
                                .background(Color.Black.copy(alpha = 0.5f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(animatedProgress)
                                    .background(
                                        if (progress.completed == 1) LegacyGreen
                                        else SapphoInfo
                                    )
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Centered text section
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = book.title,
                style = MaterialTheme.typography.labelMedium,
                color = SapphoText,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )

            book.author?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = SapphoIconDefault,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }

            // Rating below author - prefer user rating, fall back to average rating
            val displayRating = book.userRating ?: book.averageRating
            if (displayRating != null && displayRating > 0) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = SapphoStarFilled,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = String.format(java.util.Locale.US, "%.1f", displayRating),
                        style = MaterialTheme.typography.labelSmall,
                        color = SapphoStarFilled
                    )
                }
            }
        }
    }
}

@Composable
private fun EditCollectionDialog(
    name: String,
    description: String,
    isPublic: Boolean,
    isOwner: Boolean,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String?, Boolean) -> Unit
) {
    var editName by remember { mutableStateOf(name) }
    var editDescription by remember { mutableStateOf(description) }
    var editIsPublic by remember { mutableStateOf(isPublic) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.M),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = SapphoSurfaceLight
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.L)
            ) {
                Text(
                    text = "Edit Collection",
                    style = MaterialTheme.typography.headlineSmall,
                    color = SapphoText
                )

                Spacer(modifier = Modifier.height(Spacing.L))

                OutlinedTextField(
                    value = editName,
                    onValueChange = { editName = it },
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = SapphoText,
                        unfocusedTextColor = SapphoText,
                        focusedBorderColor = SapphoInfo,
                        unfocusedBorderColor = SapphoProgressTrack,
                        focusedLabelColor = SapphoInfo,
                        unfocusedLabelColor = SapphoIconDefault
                    ),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(Spacing.M))

                OutlinedTextField(
                    value = editDescription,
                    onValueChange = { editDescription = it },
                    label = { Text("Description (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = SapphoText,
                        unfocusedTextColor = SapphoText,
                        focusedBorderColor = SapphoInfo,
                        unfocusedBorderColor = SapphoProgressTrack,
                        focusedLabelColor = SapphoInfo,
                        unfocusedLabelColor = SapphoIconDefault
                    ),
                    maxLines = 3
                )

                // Visibility toggle - only show for owner
                if (isOwner) {
                    Spacer(modifier = Modifier.height(Spacing.M))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(SapphoProgressTrack.copy(alpha = 0.5f))
                            .padding(Spacing.S),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Public Collection",
                                style = MaterialTheme.typography.bodyMedium,
                                color = SapphoText
                            )
                            Text(
                                text = if (editIsPublic) "Anyone can view and add books"
                                       else "Only you can see this collection",
                                style = MaterialTheme.typography.labelMedium,
                                color = SapphoIconDefault
                            )
                        }
                        Switch(
                            checked = editIsPublic,
                            onCheckedChange = { editIsPublic = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = LegacyGreen,
                                uncheckedThumbColor = Color.White,
                                uncheckedTrackColor = SapphoTextMuted
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.L))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = SapphoIconDefault)
                    }
                    Spacer(modifier = Modifier.width(Spacing.XS))
                    Button(
                        onClick = { onSave(editName, editDescription.ifBlank { null }, editIsPublic) },
                        enabled = editName.isNotBlank() && !isSaving,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SapphoInfo
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(IconSize.Small),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Save")
                        }
                    }
                }
            }
        }
    }
}
