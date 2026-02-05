package com.sappho.audiobooks.presentation.detail

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.sappho.audiobooks.data.remote.Collection
import com.sappho.audiobooks.presentation.theme.*

/**
 * Dialog for adding an audiobook to one or more collections.
 * Supports:
 * - Viewing existing collections with membership status
 * - Creating new collections inline
 * - Toggling book membership in collections
 */
@Composable
internal fun AddToCollectionDialog(
    collections: List<Collection>,
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
                        CreateCollectionForm(
                            name = newCollectionName,
                            onNameChange = { newCollectionName = it },
                            onCancel = {
                                showCreateForm = false
                                newCollectionName = ""
                            },
                            onCreate = {
                                if (newCollectionName.isNotBlank()) {
                                    onCreateCollection(newCollectionName.trim())
                                    newCollectionName = ""
                                    showCreateForm = false
                                }
                            }
                        )
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
                            text = "No collections yet. Create one above!",
                            color = SapphoIconDefault,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                    } else {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            collections.forEach { collection ->
                                val isInCollection = bookCollections.contains(collection.id)
                                CollectionItem(
                                    collection = collection,
                                    isInCollection = isInCollection,
                                    onClick = { onToggleCollection(collection.id) }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Done button
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SapphoInfo
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Done")
                }
            }
        }
    }
}

@Composable
private fun CreateCollectionForm(
    name: String,
    onNameChange: (String) -> Unit,
    onCancel: () -> Unit,
    onCreate: () -> Unit
) {
    Column {
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
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
            TextButton(onClick = onCancel) {
                Text("Cancel", color = SapphoIconDefault)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onCreate,
                enabled = name.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = SapphoInfo
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Create & Add")
            }
        }
    }
}

@Composable
private fun CollectionItem(
    collection: Collection,
    isInCollection: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = if (isInCollection) SapphoInfo.copy(alpha = 0.15f) else SapphoProgressTrack
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = collection.name,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "${collection.bookCount ?: 0} books",
                    color = SapphoIconDefault,
                    fontSize = 12.sp
                )
            }
            if (isInCollection) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "In collection",
                    tint = SapphoInfo,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
