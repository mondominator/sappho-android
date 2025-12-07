package com.sappho.audiobooks.presentation.detail

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.StarHalf
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.sappho.audiobooks.service.AudioPlaybackService

@Composable
fun AudiobookDetailScreen(
    audiobookId: Int,
    onBackClick: () -> Unit,
    onPlayClick: (Int, Int?) -> Unit = { _, _ -> },
    onAuthorClick: (String) -> Unit = {},
    onSeriesClick: (String) -> Unit = {},
    viewModel: AudiobookDetailViewModel = hiltViewModel()
) {
    val audiobook by viewModel.audiobook.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val chapters by viewModel.chapters.collectAsState()
    val files by viewModel.files.collectAsState()
    val serverUrl by viewModel.serverUrl.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isOffline by viewModel.isOffline.collectAsState()
    val isFavorite by viewModel.isFavorite.collectAsState()
    val isTogglingFavorite by viewModel.isTogglingFavorite.collectAsState()
    val userRating by viewModel.userRating.collectAsState()
    val averageRating by viewModel.averageRating.collectAsState()
    val isUpdatingRating by viewModel.isUpdatingRating.collectAsState()
    var showDeleteDownloadDialog by remember { mutableStateOf(false) }
    var showChaptersDialog by remember { mutableStateOf(false) }

    // Check if this audiobook is currently playing or loaded
    val currentAudiobook by viewModel.playerState.currentAudiobook.collectAsState()
    val isPlaying by viewModel.playerState.isPlaying.collectAsState()
    val isThisBookLoaded = currentAudiobook?.id == audiobookId
    val isThisBookPlaying = isThisBookLoaded && isPlaying

    // Download state
    val downloadStates by viewModel.downloadManager.downloadStates.collectAsState()
    val downloadState = downloadStates[audiobookId]
    val isDownloaded = viewModel.downloadManager.isDownloaded(audiobookId)
    val isDownloading = downloadState?.isDownloading == true
    val downloadProgress = downloadState?.progress ?: 0f

    LaunchedEffect(audiobookId) {
        viewModel.loadAudiobook(audiobookId)
    }

    // Handle system back button
    BackHandler { onBackClick() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0E1A))
    ) {
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color(0xFF3B82F6))
            }
        } else {
            audiobook?.let { book ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 80.dp)
                ) {
                    // Back Button
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        OutlinedButton(
                            onClick = onBackClick,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color(0xFF9ca3af)
                            ),
                            border = BorderStroke(1.dp, Color(0xFF374151)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Back",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Back", fontSize = 14.sp)
                        }
                    }

                    // Offline Banner
                    if (isOffline && isDownloaded) {
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
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // Cover Image with Progress
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier.size(320.dp)
                        ) {
                            // Cover with shadow
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .shadow(
                                        elevation = 60.dp,
                                        shape = RoundedCornerShape(20.dp),
                                        spotColor = Color.Black.copy(alpha = 0.6f)
                                    )
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(Color(0xFF374151))
                            ) {
                                if (book.coverImage != null && serverUrl != null) {
                                    // Let Coil try to load - it may have the image cached
                                    AsyncImage(
                                        model = "$serverUrl/api/audiobooks/${book.id}/cover",
                                        contentDescription = book.title,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    // Show placeholder when no cover available
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = book.title.take(2),
                                            fontSize = 72.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF3B82F6)
                                        )
                                    }
                                }
                            }

                            // Progress bar overlay at bottom
                            val progressData = progress
                            val hasProgress = progressData != null &&
                                              book.duration != null &&
                                              book.duration > 0 &&
                                              (progressData.position > 0 || progressData.completed == 1)

                            if (hasProgress && progressData != null) {
                                val duration = book.duration!!
                                val isCompleted = progressData.completed == 1
                                val progressPercent = if (isCompleted) {
                                    1f
                                } else {
                                    (progressData.position.toFloat() / duration.toFloat()).coerceIn(0.01f, 1f)
                                }

                                // Background bar (dark)
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .align(Alignment.BottomCenter)
                                        .clip(RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp))
                                        .background(Color.Black.copy(alpha = 0.5f))
                                ) {
                                    // Progress fill bar (colored gradient)
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(progressPercent)
                                            .fillMaxHeight()
                                            .background(
                                                if (isCompleted) {
                                                    Brush.horizontalGradient(
                                                        listOf(Color(0xFF10b981), Color(0xFF34d399))
                                                    )
                                                } else {
                                                    Brush.horizontalGradient(
                                                        listOf(Color(0xFF3b82f6), Color(0xFF60a5fa))
                                                    )
                                                }
                                            )
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    // Play/Pause Button (mobile style)
                    Button(
                        onClick = {
                            if (isThisBookLoaded) {
                                // Book is already loaded, just toggle play/pause
                                AudioPlaybackService.instance?.togglePlayPause()
                            } else {
                                // Start playing a new book
                                onPlayClick(book.id, progress?.position)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isThisBookPlaying) Color(0xFF3b82f6).copy(alpha = 0.15f) else Color(0xFF10b981).copy(alpha = 0.15f),
                            contentColor = if (isThisBookPlaying) Color(0xFF93c5fd) else Color(0xFF6ee7b7)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                    ) {
                        Icon(
                            imageVector = if (isThisBookPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isThisBookPlaying) "Pause" else if (progress?.position ?: 0 > 0) "Continue" else "Play",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Chapters and Download row
                    val hasChapters = book.isMultiFile == 1 && chapters.isNotEmpty()
                    val showDownloadButton = isDownloading || isDownloaded || !isOffline

                    if (hasChapters || showDownloadButton) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Chapters button
                            if (hasChapters) {
                                Button(
                                    onClick = { showChaptersDialog = true },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF3b82f6).copy(alpha = 0.15f),
                                        contentColor = Color(0xFF93c5fd)
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                                ) {
                                    Text(
                                        text = "${chapters.size} Chapters",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            // Download button
                            if (showDownloadButton) {
                                Button(
                                    onClick = {
                                        when {
                                            isDownloading -> { /* Cancel not implemented */ }
                                            isDownloaded -> { if (!isOffline) showDeleteDownloadDialog = true }
                                            else -> { viewModel.downloadAudiobook() }
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = when {
                                            isDownloading -> Color(0xFF3b82f6).copy(alpha = 0.15f)
                                            isDownloaded -> Color(0xFF10b981).copy(alpha = 0.15f)
                                            else -> Color(0xFF3b82f6).copy(alpha = 0.15f)
                                        },
                                        contentColor = when {
                                            isDownloading -> Color(0xFF93c5fd)
                                            isDownloaded -> Color(0xFF6ee7b7)
                                            else -> Color(0xFF93c5fd)
                                        }
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                                ) {
                                    if (isDownloading) {
                                        CircularProgressIndicator(
                                            progress = downloadProgress,
                                            modifier = Modifier.size(16.dp),
                                            color = Color(0xFF93c5fd),
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                    }
                                    Text(
                                        text = when {
                                            isDownloading -> "${(downloadProgress * 100).toInt()}%"
                                            isDownloaded -> "Downloaded"
                                            else -> "Download"
                                        },
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // Files Dropdown (only show if has files)
                    if (files.isNotEmpty()) {
                        var filesExpanded by remember { mutableStateOf(false) }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp)
                        ) {
                            // Files toggle button
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { filesExpanded = !filesExpanded },
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFF1e293b).copy(alpha = 0.5f),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Description,
                                            contentDescription = null,
                                            tint = Color(0xFFE0E7F1),
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Text(
                                            text = "${files.size} File${if (files.size != 1) "s" else ""}",
                                            color = Color(0xFFE0E7F1),
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                    Icon(
                                        imageVector = if (filesExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                        contentDescription = null,
                                        tint = Color(0xFFE0E7F1),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            // Files list (expanded)
                            if (filesExpanded) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    files.forEach { file ->
                                        Surface(
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(8.dp),
                                            color = Color(0xFF1e293b).copy(alpha = 0.3f)
                                        ) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(12.dp)
                                            ) {
                                                Text(
                                                    text = file.filename,
                                                    color = Color(0xFFE0E7F1),
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
                                                Text(
                                                    text = formatFileSize(file.size),
                                                    color = Color(0xFF9ca3af),
                                                    fontSize = 12.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // Action Buttons - First Row (only show when online)
                    if (!isOffline) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Mark Finished Button
                            OutlinedButton(
                                onClick = { viewModel.markFinished() },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = Color(0xFF3b82f6)
                                ),
                                border = BorderStroke(1.dp, Color(0xFF3b82f6).copy(alpha = 0.3f)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = "Mark Finished",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            // Clear Progress Button (only show if has progress)
                            val progressCheck = progress
                            if (progressCheck != null && (progressCheck.position > 0 || progressCheck.completed == 1)) {
                                OutlinedButton(
                                    onClick = { viewModel.clearProgress() },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = Color(0xFFfb923c)
                                    ),
                                    border = BorderStroke(1.dp, Color(0xFFfb923c).copy(alpha = 0.3f)),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(
                                        text = "Clear Progress",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // Progress Section (above About)
                    progress?.let { prog ->
                        if (prog.position > 0 || prog.completed == 1) {
                            Spacer(modifier = Modifier.height(24.dp))

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp)
                            ) {
                                Text(
                                    text = "Progress",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.padding(bottom = 12.dp)
                                )

                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFF1e293b).copy(alpha = 0.5f),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        // Time progress
                                        val progressHours = prog.position / 3600
                                        val progressMinutes = (prog.position % 3600) / 60
                                        val totalHours = (book.duration ?: 0) / 3600
                                        val totalMinutes = ((book.duration ?: 0) % 3600) / 60
                                        val percentage = book.duration?.let {
                                            if (it > 0) (prog.position.toFloat() / it * 100).toInt() else 0
                                        } ?: 0

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(
                                                    text = if (prog.completed == 1) "Completed" else "${progressHours}h ${progressMinutes}m listened",
                                                    fontSize = 16.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = if (prog.completed == 1) Color(0xFF10b981) else Color.White
                                                )
                                                if (prog.completed != 1) {
                                                    Text(
                                                        text = "of ${totalHours}h ${totalMinutes}m total",
                                                        fontSize = 14.sp,
                                                        color = Color(0xFF9ca3af)
                                                    )
                                                }
                                            }
                                            if (prog.completed != 1) {
                                                Text(
                                                    text = "$percentage%",
                                                    fontSize = 20.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF3b82f6)
                                                )
                                            }
                                        }

                                        // Progress bar
                                        if (prog.completed != 1 && book.duration != null && book.duration > 0) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(6.dp)
                                                    .clip(RoundedCornerShape(3.dp))
                                                    .background(Color(0xFF374151))
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth(percentage / 100f)
                                                        .fillMaxHeight()
                                                        .background(
                                                            Brush.horizontalGradient(
                                                                listOf(Color(0xFF3b82f6), Color(0xFF60a5fa))
                                                            )
                                                        )
                                                )
                                            }
                                        }

                                        // Current chapter
                                        if (chapters.isNotEmpty() && prog.completed != 1) {
                                            val currentChapter = chapters.findLast { chapter ->
                                                chapter.startTime <= prog.position.toDouble()
                                            }
                                            val currentChapterIndex = chapters.indexOfFirst { it == currentChapter }

                                            currentChapter?.let { chapter ->
                                                Divider(
                                                    color = Color.White.copy(alpha = 0.1f),
                                                    thickness = 1.dp
                                                )
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column(
                                                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                                                    ) {
                                                        Text(
                                                            text = "Current Chapter",
                                                            fontSize = 12.sp,
                                                            color = Color(0xFF9ca3af)
                                                        )
                                                        Text(
                                                            text = chapter.title ?: "Chapter ${currentChapterIndex + 1}",
                                                            fontSize = 14.sp,
                                                            fontWeight = FontWeight.Medium,
                                                            color = Color.White,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                    Text(
                                                        text = "${currentChapterIndex + 1} of ${chapters.size}",
                                                        fontSize = 14.sp,
                                                        color = Color(0xFF9ca3af)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Description (About section)
                    book.description?.let { description ->
                        Spacer(modifier = Modifier.height(32.dp))

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp)
                        ) {
                            Text(
                                text = "About",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )

                            Text(
                                text = description,
                                fontSize = 16.sp,
                                color = Color(0xFFd1d5db),
                                lineHeight = 28.8.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    // Metadata
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        book.author?.let { author ->
                            ClickableMetadataItem("Author", author) {
                                onAuthorClick(author)
                            }
                        }

                        book.narrator?.let { narrator ->
                            MetadataItem("Narrator", narrator)
                        }

                        book.series?.let { series ->
                            val seriesText = if (book.seriesPosition != null) {
                                "$series (Book ${formatSeriesPosition(book.seriesPosition)})"
                            } else {
                                series
                            }
                            ClickableMetadataItem("Series", seriesText) {
                                onSeriesClick(series)
                            }
                        }

                        book.genre?.let { genre ->
                            MetadataItem("Genre", genre)
                        }

                        book.publishYear?.let { year ->
                            MetadataItem("Published", year.toString())
                        }

                        book.duration?.let { duration ->
                            val hours = duration / 3600
                            val minutes = (duration % 3600) / 60
                            val durationText = if (hours > 0) {
                                "${hours}h ${minutes}m"
                            } else {
                                "${minutes}m"
                            }
                            MetadataItem("Duration", durationText)
                        }
                    }

                    // Rating Section (only show when online)
                    if (!isOffline) {
                        Spacer(modifier = Modifier.height(32.dp))

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp)
                        ) {
                            Text(
                                text = "Your Rating",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )

                            StarRatingBar(
                                rating = userRating,
                                onRatingChanged = { rating ->
                                    if (rating == null) {
                                        viewModel.clearRating()
                                    } else {
                                        viewModel.setRating(rating)
                                    }
                                },
                                isLoading = isUpdatingRating
                            )

                            // Show average rating if available
                            averageRating?.let { avg ->
                                if (avg.count > 0) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        AverageRatingDisplay(average = avg.average ?: 0f)
                                        Text(
                                            text = "(${avg.count} ${if (avg.count == 1) "rating" else "ratings"})",
                                            fontSize = 14.sp,
                                            color = Color(0xFF9ca3af)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Chapters Dialog
        if (showChaptersDialog) {
            AlertDialog(
                onDismissRequest = { showChaptersDialog = false },
                title = { Text("Chapters", color = Color.White) },
                text = {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 400.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        itemsIndexed(chapters) { index, chapter ->
                            TextButton(
                                onClick = {
                                    audiobook?.let { book ->
                                        if (currentAudiobook?.id == book.id) {
                                            AudioPlaybackService.instance?.seekToAndPlay(chapter.startTime.toLong())
                                        } else {
                                            onPlayClick(book.id, chapter.startTime.toInt())
                                        }
                                    }
                                    showChaptersDialog = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = chapter.title ?: "Chapter ${index + 1}",
                                        color = Color.White,
                                        modifier = Modifier.weight(1f).padding(end = 8.dp),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = formatTime(chapter.startTime.toLong()),
                                        color = Color(0xFF9ca3af),
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showChaptersDialog = false }) {
                        Text("Close", color = Color(0xFF3b82f6))
                    }
                },
                containerColor = Color(0xFF1e293b)
            )
        }

        // Delete Download Confirmation Dialog
        if (showDeleteDownloadDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteDownloadDialog = false },
                title = { Text("Remove Download", color = Color.White) },
                text = {
                    Text(
                        "Remove this book from downloads? This will only delete the local file - your listening progress on the server will not be affected.",
                        color = Color(0xFFd1d5db)
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.deleteDownload()
                            showDeleteDownloadDialog = false
                        }
                    ) {
                        Text("Remove", color = Color(0xFFef4444))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDownloadDialog = false }) {
                        Text("Cancel", color = Color(0xFF3b82f6))
                    }
                },
                containerColor = Color(0xFF1e293b)
            )
        }
    }
}

@Composable
private fun ClickableMetadataItem(label: String, value: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Text(
            text = label.uppercase(),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF9ca3af),
            letterSpacing = 0.5.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF3b82f6) // Blue color to indicate clickable
        )
    }
}

@Composable
private fun MetadataItem(label: String, value: String) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = label.uppercase(),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF9ca3af),
            letterSpacing = 0.5.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White
        )
    }
}

@Composable
private fun ChapterItem(chapter: com.sappho.audiobooks.domain.model.Chapter, index: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1a1a1a), RoundedCornerShape(8.dp))
            .border(1.dp, Color(0xFF374151), RoundedCornerShape(8.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = chapter.title ?: "Chapter $index",
            fontSize = 14.sp,
            color = Color(0xFFd1d5db),
            modifier = Modifier.weight(1f).padding(end = 8.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        // Use duration field if available, otherwise calculate from endTime
        val duration = chapter.duration ?: chapter.endTime?.let { (it - chapter.startTime).toDouble() }
        duration?.let {
            val totalSeconds = it.toInt()
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val durationText = if (hours > 0) {
                "${hours}h ${minutes}m"
            } else {
                "${minutes}m"
            }
            Text(
                text = durationText,
                fontSize = 12.sp,
                color = Color(0xFF9ca3af)
            )
        }
    }
}

@Composable
private fun FileItem(file: com.sappho.audiobooks.domain.model.AudiobookFile) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1a1a1a), RoundedCornerShape(8.dp))
            .border(1.dp, Color(0xFF374151), RoundedCornerShape(8.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = file.filename,
            fontSize = 14.sp,
            color = Color(0xFFd1d5db),
            modifier = Modifier.weight(1f)
        )

        // Format file size
        val sizeText = when {
            file.size >= 1_073_741_824 -> String.format("%.2f GB", file.size / 1_073_741_824.0)
            file.size >= 1_048_576 -> String.format("%.2f MB", file.size / 1_048_576.0)
            file.size >= 1_024 -> String.format("%.2f KB", file.size / 1_024.0)
            else -> "$file.size B"
        }

        Text(
            text = sizeText,
            fontSize = 12.sp,
            color = Color(0xFF9ca3af)
        )
    }
}

private fun formatFileSize(size: Long): String {
    return when {
        size >= 1_073_741_824 -> String.format("%.2f GB", size / 1_073_741_824.0)
        size >= 1_048_576 -> String.format("%.2f MB", size / 1_048_576.0)
        size >= 1_024 -> String.format("%.2f KB", size / 1_024.0)
        else -> "$size B"
    }
}

private fun formatTime(seconds: Long): String {
    val hours = seconds / 3600
    val mins = (seconds % 3600) / 60
    val secs = seconds % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, mins, secs)
    } else {
        String.format("%d:%02d", mins, secs)
    }
}

private fun formatSeriesPosition(position: Float?): String {
    if (position == null) return "?"
    // Check if it's a whole number
    return if (position == position.toLong().toFloat()) {
        position.toLong().toString()
    } else {
        position.toString()
    }
}

@Composable
private fun StarRatingBar(
    rating: Int?,
    onRatingChanged: (Int?) -> Unit,
    isLoading: Boolean = false,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 5 clickable stars
        for (starIndex in 1..5) {
            val isSelected = rating != null && starIndex <= rating

            IconButton(
                onClick = {
                    if (!isLoading) {
                        // If clicking the same star that's already selected, clear the rating
                        if (rating == starIndex) {
                            onRatingChanged(null)
                        } else {
                            onRatingChanged(starIndex)
                        }
                    }
                },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = if (isSelected) Icons.Filled.Star else Icons.Filled.StarBorder,
                    contentDescription = "Star $starIndex",
                    tint = if (isSelected) Color(0xFFfbbf24) else Color(0xFF6b7280),
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        if (isLoading) {
            Spacer(modifier = Modifier.width(8.dp))
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = Color(0xFFfbbf24),
                strokeWidth = 2.dp
            )
        }
    }
}

@Composable
private fun AverageRatingDisplay(
    average: Float,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Show 5 stars representing the average
        for (starIndex in 1..5) {
            val fillAmount = (average - starIndex + 1).coerceIn(0f, 1f)

            Icon(
                imageVector = when {
                    fillAmount >= 1f -> Icons.Filled.Star
                    fillAmount >= 0.5f -> Icons.Filled.StarHalf
                    else -> Icons.Filled.StarBorder
                },
                contentDescription = null,
                tint = if (fillAmount > 0f) Color(0xFFfbbf24) else Color(0xFF6b7280),
                modifier = Modifier.size(16.dp)
            )
        }

        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = String.format("%.1f", average),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFFfbbf24)
        )
    }
}
