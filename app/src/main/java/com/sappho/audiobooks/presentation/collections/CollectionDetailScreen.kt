package com.sappho.audiobooks.presentation.collections

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.sappho.audiobooks.domain.model.Audiobook

@OptIn(ExperimentalFoundationApi::class)
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
            .background(Color(0xFF0A0E1A))
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
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    IconButton(onClick = {
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
                            tint = Color(0xFFE0E7F1)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isEditMode) "${selectedBooks.size} selected" else (collection?.name ?: "Collection"),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (!isEditMode) {
                            collection?.description?.let { desc ->
                                if (desc.isNotBlank()) {
                                    Text(
                                        text = desc,
                                        fontSize = 13.sp,
                                        color = Color(0xFF9ca3af),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                // Action buttons
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (isEditMode) {
                        // Remove button (only show when items are selected)
                        if (selectedBooks.isNotEmpty()) {
                            IconButton(
                                onClick = { showRemoveDialog = true },
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(Color(0xFFef4444), CircleShape)
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
                            onClick = { showEditDialog = true },
                            modifier = Modifier
                                .size(40.dp)
                                .background(Color(0xFF374151), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit Collection",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            when {
                isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Color(0xFF3B82F6))
                    }
                }
                error != null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = error ?: "Error loading collection",
                                fontSize = 16.sp,
                                color = Color(0xFFef4444)
                            )
                            Button(
                                onClick = { viewModel.refresh(collectionId) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF3b82f6)
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
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.LibraryBooks,
                            contentDescription = null,
                            tint = Color(0xFF3b82f6),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No books in this collection",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Add books from the book detail page",
                            fontSize = 14.sp,
                            color = Color(0xFF9ca3af)
                        )
                    }
                }
                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(collection?.books ?: emptyList()) { book ->
                            CollectionBookItem(
                                book = book,
                                serverUrl = serverUrl,
                                isEditMode = isEditMode,
                                isSelected = selectedBooks.contains(book.id),
                                onClick = {
                                    if (isEditMode) {
                                        // Toggle selection
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
                            // Remove all selected books
                            selectedBooks.forEach { bookId ->
                                viewModel.removeBook(collectionId, bookId) { _, _ -> }
                            }
                            showRemoveDialog = false
                            selectedBooks = emptySet()
                            isEditMode = false
                        },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = Color(0xFFef4444)
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
                containerColor = Color(0xFF1a1a1a),
                titleContentColor = Color.White,
                textContentColor = Color(0xFFd1d5db)
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
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .then(
                if (isSelected) {
                    Modifier.border(2.dp, Color(0xFF3b82f6), RoundedCornerShape(8.dp))
                } else {
                    Modifier
                }
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        // Square book cover
        Box(
            modifier = Modifier
                .aspectRatio(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF374151))
        ) {
            if (book.coverImage != null && serverUrl != null) {
                AsyncImage(
                    model = "$serverUrl/api/audiobooks/${book.id}/cover",
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
                                colors = listOf(Color(0xFF374151), Color(0xFF1f2937))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = book.title.take(2).uppercase(),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            // Selection checkbox (top-left, only in edit mode)
            if (isEditMode) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp)
                        .size(22.dp)
                        .background(
                            if (isSelected) Color(0xFF3b82f6) else Color.Black.copy(alpha = 0.5f),
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

            // Progress bar (bottom)
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
                                    .fillMaxWidth(progressPercent)
                                    .background(
                                        if (progress.completed == 1) Color(0xFF22c55e)
                                        else Color(0xFF3b82f6)
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
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White,
                maxLines = 2,
                lineHeight = 14.sp,
                overflow = TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            book.author?.let {
                Text(
                    text = it,
                    fontSize = 10.sp,
                    color = Color(0xFF9ca3af),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
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
                        tint = Color(0xFFfbbf24),
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = String.format(java.util.Locale.US, "%.1f", displayRating),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFfbbf24)
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
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1a1a1a)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Text(
                    text = "Edit Collection",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(24.dp))

                OutlinedTextField(
                    value = editName,
                    onValueChange = { editName = it },
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF3b82f6),
                        unfocusedBorderColor = Color(0xFF374151),
                        focusedLabelColor = Color(0xFF3b82f6),
                        unfocusedLabelColor = Color(0xFF9ca3af)
                    ),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = editDescription,
                    onValueChange = { editDescription = it },
                    label = { Text("Description (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF3b82f6),
                        unfocusedBorderColor = Color(0xFF374151),
                        focusedLabelColor = Color(0xFF3b82f6),
                        unfocusedLabelColor = Color(0xFF9ca3af)
                    ),
                    maxLines = 3
                )

                // Visibility toggle - only show for owner
                if (isOwner) {
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF374151).copy(alpha = 0.5f))
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Public Collection",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White
                            )
                            Text(
                                text = if (editIsPublic) "Anyone can view and add books"
                                       else "Only you can see this collection",
                                fontSize = 12.sp,
                                color = Color(0xFF9ca3af)
                            )
                        }
                        Switch(
                            checked = editIsPublic,
                            onCheckedChange = { editIsPublic = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF22c55e),
                                uncheckedThumbColor = Color.White,
                                uncheckedTrackColor = Color(0xFF6b7280)
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = Color(0xFF9ca3af))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onSave(editName, editDescription.ifBlank { null }, editIsPublic) },
                        enabled = editName.isNotBlank() && !isSaving,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF3b82f6)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
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
