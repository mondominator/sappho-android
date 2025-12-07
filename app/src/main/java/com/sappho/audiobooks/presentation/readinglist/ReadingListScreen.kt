package com.sappho.audiobooks.presentation.readinglist

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BookmarkAdded
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.sappho.audiobooks.domain.model.Audiobook

@Composable
fun ReadingListScreen(
    onAudiobookClick: (Int) -> Unit = {},
    onBackClick: () -> Unit = {},
    viewModel: ReadingListViewModel = hiltViewModel()
) {
    val books by viewModel.books.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val serverUrl by viewModel.serverUrl.collectAsState()

    // Handle system back button
    BackHandler { onBackClick() }

    // Refresh data when screen is loaded
    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0E1A))
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }
            Column {
                Text(
                    text = "Reading List",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "${books.size} books to read",
                    fontSize = 13.sp,
                    color = Color(0xFF9ca3af)
                )
            }
        }

        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color(0xFF3B82F6))
            }
        } else if (books.isEmpty()) {
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
                        contentDescription = null,
                        tint = Color(0xFF3b82f6),
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = "Your reading list is empty",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White
                    )
                    Text(
                        text = "Add books to your reading list from the book detail page",
                        fontSize = 14.sp,
                        color = Color(0xFF9ca3af)
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(books) { book ->
                    ReadingListBookItem(
                        book = book,
                        serverUrl = serverUrl,
                        onClick = { onAudiobookClick(book.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun ReadingListBookItem(
    book: Audiobook,
    serverUrl: String?,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier.clickable(onClick = onClick)
    ) {
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

            // Reading list indicator
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(20.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.BookmarkAdded,
                    contentDescription = "On reading list",
                    tint = Color(0xFF3b82f6),
                    modifier = Modifier.size(12.dp)
                )
            }

            // Progress bar if any
            val progress = book.progress
            if (progress != null && book.duration != null && book.duration > 0) {
                val progressPercent = if (progress.completed == 1) 1f
                else (progress.position.toFloat() / book.duration.toFloat()).coerceIn(0f, 1f)

                if (progressPercent > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .height(4.dp)
                            .background(Color.Black.copy(alpha = 0.7f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(progressPercent)
                                .background(
                                    if (progress.completed == 1) Color(0xFF10b981) else Color(0xFF3b82f6)
                                )
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = book.title,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White,
            maxLines = 2,
            lineHeight = 14.sp
        )

        book.author?.let {
            Text(
                text = it,
                fontSize = 10.sp,
                color = Color(0xFF9ca3af),
                maxLines = 1
            )
        }
    }
}
