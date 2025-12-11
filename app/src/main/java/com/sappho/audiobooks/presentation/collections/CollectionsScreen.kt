package com.sappho.audiobooks.presentation.collections

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.DisposableEffect
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
import com.sappho.audiobooks.data.remote.Collection
import kotlinx.coroutines.delay

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CollectionsScreen(
    onCollectionClick: (Int) -> Unit = {},
    onBackClick: () -> Unit = {},
    viewModel: CollectionsViewModel = hiltViewModel()
) {
    val collections by viewModel.collections.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val serverUrl by viewModel.serverUrl.collectAsState()
    val isCreating by viewModel.isCreating.collectAsState()

    var showCreateDialog by remember { mutableStateOf(false) }
    var isEditMode by remember { mutableStateOf(false) }
    var selectedCollections by remember { mutableStateOf(setOf<Int>()) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    // Refresh collections when screen becomes visible (e.g., returning from detail)
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

    // Handle back button - exit edit mode first, then go back
    BackHandler {
        if (isEditMode) {
            isEditMode = false
            selectedCollections = emptySet()
        } else {
            onBackClick()
        }
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
                            selectedCollections = emptySet()
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
                    Text(
                        text = if (isEditMode) "${selectedCollections.size} selected" else "Collections",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                // Action buttons
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (isEditMode) {
                        // Delete button (only show when items are selected)
                        if (selectedCollections.isNotEmpty()) {
                            IconButton(
                                onClick = { showDeleteDialog = true },
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(Color(0xFFef4444), CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete Selected",
                                    tint = Color.White
                                )
                            }
                        }
                    } else {
                        // Create button
                        IconButton(
                            onClick = { showCreateDialog = true },
                            modifier = Modifier
                                .size(40.dp)
                                .background(Color(0xFF3b82f6), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Create Collection",
                                tint = Color.White
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
                collections.isEmpty() -> {
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
                            text = "No collections yet",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Create a collection to organize your audiobooks",
                            fontSize = 14.sp,
                            color = Color(0xFF9ca3af)
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = { showCreateDialog = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF3b82f6)
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Create Collection")
                        }
                    }
                }
                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(collections) { collection ->
                            CollectionCard(
                                collection = collection,
                                serverUrl = serverUrl,
                                isEditMode = isEditMode,
                                isSelected = selectedCollections.contains(collection.id),
                                onClick = {
                                    if (isEditMode) {
                                        // Toggle selection
                                        selectedCollections = if (selectedCollections.contains(collection.id)) {
                                            selectedCollections - collection.id
                                        } else {
                                            selectedCollections + collection.id
                                        }
                                    } else {
                                        onCollectionClick(collection.id)
                                    }
                                },
                                onLongClick = {
                                    if (!isEditMode) {
                                        isEditMode = true
                                        selectedCollections = setOf(collection.id)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        // Create Collection Dialog
        if (showCreateDialog) {
            CreateCollectionDialog(
                isCreating = isCreating,
                onDismiss = { showCreateDialog = false },
                onCreate = { name, description ->
                    viewModel.createCollection(name, description) { success, _ ->
                        if (success) {
                            showCreateDialog = false
                        }
                    }
                }
            )
        }

        // Delete Confirmation Dialog
        if (showDeleteDialog && selectedCollections.isNotEmpty()) {
            val count = selectedCollections.size
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = { Text("Delete Collections") },
                text = {
                    Text(
                        if (count == 1) "Are you sure you want to delete this collection? This cannot be undone."
                        else "Are you sure you want to delete $count collections? This cannot be undone."
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            // Delete all selected collections
                            selectedCollections.forEach { collectionId ->
                                viewModel.deleteCollection(collectionId) { _, _ -> }
                            }
                            showDeleteDialog = false
                            selectedCollections = emptySet()
                            isEditMode = false
                        },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = Color(0xFFef4444)
                        )
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false }) {
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
private fun CollectionCard(
    collection: Collection,
    serverUrl: String?,
    isEditMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.85f)
            .then(
                if (isSelected) {
                    Modifier.border(2.dp, Color(0xFF3b82f6), RoundedCornerShape(12.dp))
                } else {
                    Modifier
                }
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1e293b)
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Cover area with rotating carousel
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF3b82f6).copy(alpha = 0.3f),
                                    Color(0xFF1e40af).copy(alpha = 0.3f)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    RotatingCover(
                        bookIds = collection.bookIds,
                        serverUrl = serverUrl,
                        collectionName = collection.name,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                    )

                    // Book count badge (top-right)
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .background(Color(0xFF3b82f6), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "${collection.bookCount ?: 0}",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    // Selection checkbox (top-left, only in edit mode)
                    if (isEditMode) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(8.dp)
                                .size(24.dp)
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
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                // Info section
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                ) {
                    // Title row with visibility badge
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = collection.name,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        // Public/Private badge
                        val isPublic = collection.isPublic == 1
                        Box(
                            modifier = Modifier
                                .background(
                                    if (isPublic) Color(0xFF22c55e).copy(alpha = 0.2f)
                                    else Color(0xFF6b7280).copy(alpha = 0.2f),
                                    RoundedCornerShape(4.dp)
                                )
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = if (isPublic) "Public" else "Private",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (isPublic) Color(0xFF22c55e) else Color(0xFF9ca3af)
                            )
                        }
                    }

                    collection.description?.let { desc ->
                        if (desc.isNotBlank()) {
                            Text(
                                text = desc,
                                fontSize = 12.sp,
                                color = Color(0xFF9ca3af),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }

                    // Creator info
                    Text(
                        text = if (collection.isOwner == 1) "Created by you"
                               else "Created by ${collection.creatorUsername ?: "Unknown"}",
                        fontSize = 10.sp,
                        color = Color(0xFF6b7280),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun RotatingCover(
    bookIds: List<Int>?,
    serverUrl: String?,
    collectionName: String,
    modifier: Modifier = Modifier
) {
    // If no books, show placeholder
    if (bookIds.isNullOrEmpty() || serverUrl == null) {
        Box(
            modifier = modifier
                .background(
                    Brush.linearGradient(
                        colors = listOf(Color(0xFF374151), Color(0xFF1f2937))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.LibraryBooks,
                contentDescription = null,
                tint = Color(0xFF3b82f6),
                modifier = Modifier.size(48.dp)
            )
        }
        return
    }

    // Rotate through covers every 4 seconds
    var currentIndex by remember { mutableStateOf(0) }

    // Only start rotation if there's more than 1 book
    if (bookIds.size > 1) {
        LaunchedEffect(bookIds) {
            while (true) {
                delay(4000L)
                currentIndex = (currentIndex + 1) % bookIds.size
            }
        }
    }

    Crossfade(
        targetState = currentIndex,
        animationSpec = tween(durationMillis = 500),
        modifier = modifier,
        label = "cover_crossfade"
    ) { index ->
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = "$serverUrl/api/audiobooks/${bookIds[index]}/cover",
                contentDescription = collectionName,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }
    }
}

@Composable
private fun CreateCollectionDialog(
    isCreating: Boolean,
    onDismiss: () -> Unit,
    onCreate: (String, String?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

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
                    text = "Create Collection",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(24.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
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
                    value = description,
                    onValueChange = { description = it },
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
                        onClick = { onCreate(name, description.ifBlank { null }) },
                        enabled = name.isNotBlank() && !isCreating,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF3b82f6)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        if (isCreating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Create")
                        }
                    }
                }
            }
        }
    }
}
