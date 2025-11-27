package com.sappho.audiobooks.presentation.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
                .background(Color(0xFF0A0E1A)),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = Color(0xFF3B82F6))
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0A0E1A)),
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
                                Color(0xFFfb923c).copy(alpha = 0.2f),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudOff,
                            contentDescription = null,
                            tint = Color(0xFFfb923c),
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Offline mode",
                            fontSize = 14.sp,
                            color = Color(0xFFfb923c)
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
                            onAudiobookClick = onAudiobookClick
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
                            onAudiobookClick = onAudiobookClick
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
                            onAudiobookClick = onAudiobookClick
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
                            onAudiobookClick = onAudiobookClick
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
                            color = Color(0xFF9ca3af)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AudiobookSection(
    title: String,
    books: List<Audiobook>,
    serverUrl: String?,
    onAudiobookClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    cardSize: androidx.compose.ui.unit.Dp = 140.dp,
    titleSize: androidx.compose.ui.unit.TextUnit = 16.sp
) {
    Column(modifier = modifier) {
        Text(
            text = title,
            fontSize = titleSize,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFE0E7F1),
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
                    cardSize = cardSize
                )
            }
        }
    }
}

@Composable
fun AudiobookCard(
    book: Audiobook,
    serverUrl: String?,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    cardSize: androidx.compose.ui.unit.Dp = 140.dp
) {
    android.util.Log.d("AudiobookCard", "Book: ${book.title}, coverImage: ${book.coverImage}, serverUrl: $serverUrl")
    val textScale = cardSize.value / 140f
    val titleFontSize = (14 * textScale).sp
    val authorFontSize = (12 * textScale).sp
    val placeholderFontSize = (32 * textScale).sp

    Column(
        modifier = modifier
            .width(cardSize)
            .clickable(onClick = onClick)
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
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Title
        Text(
            text = book.title,
            fontSize = titleFontSize,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            color = Color(0xFFE0E7F1)
        )

        // Author
        if (book.author != null) {
            Text(
                text = book.author,
                fontSize = authorFontSize,
                maxLines = 1,
                color = Color(0xFF9ca3af)
            )
        }
    }
}
