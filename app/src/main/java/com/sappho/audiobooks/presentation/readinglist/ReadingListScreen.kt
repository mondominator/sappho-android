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
import com.sappho.audiobooks.presentation.theme.*
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.foundation.Canvas
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
            .background(SapphoBackground)
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
                    color = SapphoIconDefault
                )
            }
        }

        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = SapphoInfo)
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
                        tint = SapphoInfo,
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
                        color = SapphoIconDefault
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
                .background(SapphoProgressTrack)
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
                                colors = listOf(SapphoProgressTrack, SapphoSurfaceDark)
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

            // Reading list ribbon (top-right corner)
            ReadingListRibbon(
                modifier = Modifier.align(Alignment.TopEnd),
                size = 28f
            )

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
                                    if (progress.completed == 1) SapphoSuccess else SapphoInfo
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
                color = SapphoIconDefault,
                maxLines = 1
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
