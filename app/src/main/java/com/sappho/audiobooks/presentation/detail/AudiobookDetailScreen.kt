package com.sappho.audiobooks.presentation.detail

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.BookmarkAdded
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.StarHalf
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.ui.window.Dialog
import com.sappho.audiobooks.data.remote.AudiobookUpdateRequest
import com.sappho.audiobooks.data.remote.ChapterUpdate
import com.sappho.audiobooks.data.remote.MetadataSearchResult
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.sappho.audiobooks.presentation.theme.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import com.sappho.audiobooks.service.AudioPlaybackService
import com.sappho.audiobooks.service.DownloadService
import com.sappho.audiobooks.util.HapticPatterns

@Composable
fun AudiobookDetailScreen(
    audiobookId: Int,
    onBackClick: () -> Unit,
    onPlayClick: (Int, Int?) -> Unit = { _, _ -> },
    onAuthorClick: (String) -> Unit = {},
    onSeriesClick: (String) -> Unit = {},
    isAdmin: Boolean = false,
    viewModel: AudiobookDetailViewModel = hiltViewModel()
) {
    val audiobook by viewModel.audiobook.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val isProgressLoading by viewModel.isProgressLoading.collectAsState()
    val chapters by viewModel.chapters.collectAsState()
    val files by viewModel.files.collectAsState()
    val serverUrl by viewModel.serverUrl.collectAsState()
    val coverVersion by viewModel.coverVersion.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isOffline by viewModel.isOffline.collectAsState()
    val isFavorite by viewModel.isFavorite.collectAsState()
    val isTogglingFavorite by viewModel.isTogglingFavorite.collectAsState()
    val userRating by viewModel.userRating.collectAsState()
    val averageRating by viewModel.averageRating.collectAsState()
    val isUpdatingRating by viewModel.isUpdatingRating.collectAsState()
    val isSavingMetadata by viewModel.isSavingMetadata.collectAsState()
    val metadataSaveResult by viewModel.metadataSaveResult.collectAsState()
    val isSearchingMetadata by viewModel.isSearchingMetadata.collectAsState()
    val metadataSearchResults by viewModel.metadataSearchResults.collectAsState()
    val metadataSearchError by viewModel.metadataSearchError.collectAsState()
    val isEmbeddingMetadata by viewModel.isEmbeddingMetadata.collectAsState()
    val embedMetadataResult by viewModel.embedMetadataResult.collectAsState()
    val isSavingChapters by viewModel.isSavingChapters.collectAsState()
    val chapterSaveResult by viewModel.chapterSaveResult.collectAsState()
    val isFetchingChapters by viewModel.isFetchingChapters.collectAsState()
    val fetchChaptersResult by viewModel.fetchChaptersResult.collectAsState()
    var showDeleteDownloadDialog by remember { mutableStateOf(false) }
    var showChaptersDialog by remember { mutableStateOf(false) }
    var showEditMetadataDialog by remember { mutableStateOf(false) }
    var showCollectionsDialog by remember { mutableStateOf(false) }
    var showDeleteBookDialog by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    val isRefreshingMetadata by viewModel.isRefreshingMetadata.collectAsState()
    val refreshMetadataResult by viewModel.refreshMetadataResult.collectAsState()

    val collections by viewModel.collections.collectAsState()
    val bookCollections by viewModel.bookCollections.collectAsState()
    val isLoadingCollections by viewModel.isLoadingCollections.collectAsState()

    // AI Recap (Catch Up)
    val isAiConfigured by viewModel.isAiConfigured.collectAsState()
    val recap by viewModel.recap.collectAsState()
    val isLoadingRecap by viewModel.isLoadingRecap.collectAsState()
    val recapError by viewModel.recapError.collectAsState()
    val previousBookCompleted by viewModel.previousBookCompleted.collectAsState()
    var showRecapDialog by remember { mutableStateOf(false) }
    var isDescriptionExpanded by remember { mutableStateOf(false) }

    // Check if this audiobook is currently playing or loaded
    val currentAudiobook by viewModel.playerState.currentAudiobook.collectAsState()
    val isPlaying by viewModel.playerState.isPlaying.collectAsState()
    val currentPosition by viewModel.playerState.currentPosition.collectAsState()
    val isThisBookLoaded = currentAudiobook?.id == audiobookId
    val isThisBookPlaying = isThisBookLoaded && isPlaying

    // Download state
    val downloadStates by viewModel.downloadManager.downloadStates.collectAsState()
    val downloadState = downloadStates[audiobookId]
    val isDownloaded = viewModel.downloadManager.isDownloaded(audiobookId)
    val isDownloading = downloadState?.isDownloading == true
    val downloadProgress = downloadState?.progress ?: 0f
    val downloadError = downloadState?.error
    val hasDownloadError = !downloadError.isNullOrBlank() && !isDownloading
    val context = LocalContext.current

    LaunchedEffect(audiobookId) {
        viewModel.loadAudiobook(audiobookId)
        viewModel.checkAiStatus()
        viewModel.checkPreviousBookStatus(audiobookId)
    }

    // Handle system back button
    BackHandler { onBackClick() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SapphoBackground)
    ) {
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = SapphoInfo)
            }
        } else {
            audiobook?.let { book ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 80.dp)
                ) {
                    // Back Button and Edit Button (admin only)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val backHaptic = HapticPatterns.navigationAction()
                        OutlinedButton(
                            onClick = { 
                                backHaptic()
                                onBackClick() 
                            },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = SapphoIconDefault
                            ),
                            border = BorderStroke(1.dp, SapphoProgressTrack),
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

                        // Edit button (admin only)
                        if (isAdmin && !isOffline) {
                            val editHaptic = HapticPatterns.buttonPress()
                            OutlinedButton(
                                onClick = { 
                                    editHaptic()
                                    showEditMetadataDialog = true 
                                },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = SapphoInfo
                                ),
                                border = BorderStroke(1.dp, SapphoInfo.copy(alpha = 0.3f)),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit",
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Edit", fontSize = 14.sp)
                            }
                        }
                    }

                    // Offline Banner
                    if (isOffline && isDownloaded) {
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
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // Download Error Banner
                    if (hasDownloadError) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .background(
                                    SapphoError.copy(alpha = 0.2f),
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = null,
                                    tint = SapphoError,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column {
                                    Text(
                                        text = "Download failed",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = SapphoError
                                    )
                                    Text(
                                        text = downloadError ?: "Unknown error",
                                        fontSize = 12.sp,
                                        color = LegacyRedLight,
                                        maxLines = 2
                                    )
                                }
                            }
                            TextButton(
                                onClick = { viewModel.clearDownloadError(audiobookId) },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Dismiss",
                                    tint = SapphoError,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
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
                                    .background(SapphoProgressTrack)
                            ) {
                                if (book.coverImage != null && serverUrl != null) {
                                    // Let Coil try to load - use coverVersion for cache busting after metadata updates
                                    val coverUrl = if (coverVersion > 0) {
                                        "$serverUrl/api/audiobooks/${book.id}/cover?v=$coverVersion"
                                    } else {
                                        "$serverUrl/api/audiobooks/${book.id}/cover"
                                    }
                                    AsyncImage(
                                        model = coverUrl,
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
                                            color = SapphoInfo
                                        )
                                    }
                                }
                            }

                            // Reading list button overlay (top-right)
                            if (!isOffline) {
                                IconButton(
                                    onClick = { viewModel.toggleFavorite() },
                                    enabled = !isTogglingFavorite,
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(12.dp)
                                        .size(40.dp)
                                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                ) {
                                    if (isTogglingFavorite) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            color = SapphoInfo,
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Icon(
                                            imageVector = if (isFavorite) Icons.Filled.BookmarkAdded else Icons.Filled.BookmarkBorder,
                                            contentDescription = if (isFavorite) "Remove from reading list" else "Add to reading list",
                                            tint = if (isFavorite) SapphoInfo else Color.White,
                                            modifier = Modifier.size(22.dp)
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
                                                        listOf(SapphoSuccess, LegacyGreenLight)
                                                    )
                                                } else {
                                                    Brush.horizontalGradient(
                                                        listOf(SapphoInfo, LegacyBlueLight)
                                                    )
                                                }
                                            )
                                    )
                                }
                            }
                        }
                    }

                    // Rating Section (below cover, only when online)
                    if (!isOffline) {
                        Spacer(modifier = Modifier.height(16.dp))

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Your rating stars
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                for (starIndex in 1..5) {
                                    val isSelected = userRating != null && starIndex <= userRating!!

                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clickable(enabled = !isUpdatingRating) {
                                                if (userRating == starIndex) {
                                                    viewModel.clearRating()
                                                } else {
                                                    viewModel.setRating(starIndex)
                                                }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (isSelected) Icons.Filled.Star else Icons.Filled.StarBorder,
                                            contentDescription = "Rate $starIndex stars",
                                            tint = if (isSelected) SapphoStarFilled else LegacyGrayDark,
                                            modifier = Modifier.size(32.dp)
                                        )
                                    }
                                }

                                if (isUpdatingRating) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        color = SapphoStarFilled,
                                        strokeWidth = 2.dp
                                    )
                                }
                            }

                            // Rating info text
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (userRating != null) {
                                    Text(
                                        text = "Your rating",
                                        fontSize = 12.sp,
                                        color = SapphoIconDefault
                                    )
                                } else {
                                    Text(
                                        text = "Tap to rate",
                                        fontSize = 12.sp,
                                        color = SapphoTextMuted
                                    )
                                }

                                averageRating?.let { avg ->
                                    if (avg.count > 0) {
                                        Text(
                                            text = "  •  ",
                                            fontSize = 12.sp,
                                            color = LegacyGrayDark
                                        )
                                        Icon(
                                            imageVector = Icons.Filled.Star,
                                            contentDescription = null,
                                            tint = SapphoStarFilled,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Text(
                                            text = String.format(java.util.Locale.US, "%.1f", avg.average ?: 0f),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = LegacyWhite
                                        )
                                        Text(
                                            text = " (${avg.count})",
                                            fontSize = 12.sp,
                                            color = SapphoIconDefault
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Play/Pause Button with overflow menu
                    // Disable button until progress is confirmed (unless book is already loaded in service)
                    val canPlay = isThisBookLoaded || !isProgressLoading
                    val playButtonHaptic = HapticPatterns.playButtonPress()
                    val progressCheck = progress
                    val hasProgress = progressCheck != null && (progressCheck.position > 0 || progressCheck.completed == 1)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Play button
                        Button(
                            onClick = {
                                if (!canPlay) return@Button
                                playButtonHaptic()
                                val service = AudioPlaybackService.instance
                                if (isThisBookLoaded && service != null) {
                                    // Book is already loaded and service is alive, try to toggle play/pause
                                    val playerHandled = service.togglePlayPause()
                                    if (!playerHandled) {
                                        // Player was null (service exists but player released)
                                        // Restart playback from current position or saved progress
                                        val position = if (currentPosition > 0) currentPosition.toInt() else progress?.position
                                        onPlayClick(book.id, position)
                                    }
                                } else {
                                    // Start playing a new book (or restart if service was killed)
                                    onPlayClick(book.id, progress?.position)
                                }
                            },
                            enabled = canPlay,
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isThisBookPlaying) SapphoInfo.copy(alpha = 0.15f) else SapphoSuccess.copy(alpha = 0.15f),
                                contentColor = if (isThisBookPlaying) LegacyBluePale else LegacyGreenPale,
                                disabledContainerColor = SapphoSurface,
                                disabledContentColor = SapphoTextSecondary
                            ),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = if (canPlay) 0.1f else 0.05f))
                        ) {
                            if (isProgressLoading && !isThisBookLoaded) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = SapphoTextSecondary,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Loading...",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            } else {
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
                        }

                        // Overflow menu button (only show when online)
                        if (!isOffline) {
                            Box {
                                IconButton(
                                    onClick = { showOverflowMenu = true },
                                    modifier = Modifier
                                        .size(56.dp)
                                        .background(
                                            color = SapphoSurfaceLight.copy(alpha = 0.5f),
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        .border(
                                            width = 1.dp,
                                            color = Color.White.copy(alpha = 0.1f),
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = "More options",
                                        tint = SapphoIconDefault,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                DropdownMenu(
                                    expanded = showOverflowMenu,
                                    onDismissRequest = { showOverflowMenu = false },
                                    modifier = Modifier.background(SapphoSurface)
                                ) {
                                    // Collection
                                    DropdownMenuItem(
                                        text = { Text("Add to Collection", color = SapphoText) },
                                        onClick = {
                                            showOverflowMenu = false
                                            showCollectionsDialog = true
                                            viewModel.loadCollectionsForBook(book.id)
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Filled.LibraryBooks,
                                                contentDescription = null,
                                                tint = SapphoInfo
                                            )
                                        }
                                    )

                                    // Mark Finished
                                    DropdownMenuItem(
                                        text = { Text("Mark Finished", color = SapphoText) },
                                        onClick = {
                                            showOverflowMenu = false
                                            viewModel.markFinished()
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Filled.CheckCircle,
                                                contentDescription = null,
                                                tint = LegacyGreen
                                            )
                                        }
                                    )

                                    // Clear Progress (only if has progress)
                                    if (hasProgress) {
                                        DropdownMenuItem(
                                            text = { Text("Clear Progress", color = SapphoText) },
                                            onClick = {
                                                showOverflowMenu = false
                                                viewModel.clearProgress()
                                            },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Filled.Close,
                                                    contentDescription = null,
                                                    tint = SapphoWarning
                                                )
                                            }
                                        )
                                    }

                                    // Refresh Metadata
                                    DropdownMenuItem(
                                        text = { Text(if (isRefreshingMetadata) "Refreshing..." else "Refresh Metadata", color = SapphoText) },
                                        onClick = {
                                            if (!isRefreshingMetadata) {
                                                showOverflowMenu = false
                                                viewModel.refreshMetadata()
                                            }
                                        },
                                        enabled = !isRefreshingMetadata,
                                        leadingIcon = {
                                            if (isRefreshingMetadata) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(20.dp),
                                                    color = SapphoIconDefault,
                                                    strokeWidth = 2.dp
                                                )
                                            } else {
                                                Icon(
                                                    imageVector = Icons.Filled.Refresh,
                                                    contentDescription = null,
                                                    tint = SapphoIconDefault
                                                )
                                            }
                                        }
                                    )

                                    // Delete (admin only)
                                    if (isAdmin) {
                                        HorizontalDivider(color = SapphoProgressTrack)
                                        DropdownMenuItem(
                                            text = { Text("Delete Audiobook", color = SapphoError) },
                                            onClick = {
                                                showOverflowMenu = false
                                                showDeleteBookDialog = true
                                            },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Filled.Delete,
                                                    contentDescription = null,
                                                    tint = SapphoError
                                                )
                                            }
                                        )
                                    }
                                }
                            }
                        }
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
                                val chaptersHaptic = HapticPatterns.buttonPress()
                                Button(
                                    onClick = { 
                                        chaptersHaptic()
                                        showChaptersDialog = true 
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = SapphoInfo.copy(alpha = 0.15f),
                                        contentColor = LegacyBluePale
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
                                val downloadStartHaptic = HapticPatterns.downloadStart()
                                val downloadCancelHaptic = HapticPatterns.downloadCancel()
                                Button(
                                    onClick = {
                                        when {
                                            isDownloading -> { 
                                                downloadCancelHaptic()
                                                // Cancel download via service
                                                DownloadService.cancelDownload(context)
                                            }
                                            isDownloaded -> { 
                                                downloadCancelHaptic()
                                                if (!isOffline) showDeleteDownloadDialog = true 
                                            }
                                            hasDownloadError -> { 
                                                downloadStartHaptic()
                                                // Retry download
                                                viewModel.downloadAudiobook() 
                                            }
                                            else -> { 
                                                downloadStartHaptic()
                                                viewModel.downloadAudiobook() 
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = when {
                                            hasDownloadError -> SapphoError.copy(alpha = 0.15f)
                                            else -> SapphoInfo.copy(alpha = 0.15f)
                                        },
                                        contentColor = when {
                                            hasDownloadError -> LegacyRedLight
                                            else -> LegacyBluePale
                                        }
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                                ) {
                                    // Animated icon: progress ring -> checkmark
                                    Crossfade(
                                        targetState = when {
                                            isDownloading -> "downloading"
                                            isDownloaded -> "downloaded"
                                            hasDownloadError -> "error"
                                            else -> "idle"
                                        },
                                        animationSpec = tween(300),
                                        label = "download_icon"
                                    ) { state ->
                                        when (state) {
                                            "downloading" -> {
                                                CircularProgressIndicator(
                                                    progress = { downloadProgress },
                                                    modifier = Modifier.size(16.dp),
                                                    color = LegacyBluePale,
                                                    strokeWidth = 2.dp
                                                )
                                            }
                                            "downloaded" -> {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp),
                                                    tint = SapphoSuccess
                                                )
                                            }
                                            "error" -> {
                                                Icon(
                                                    imageVector = Icons.Default.Refresh,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                            else -> {
                                                Icon(
                                                    imageVector = Icons.Default.Download,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = when {
                                            isDownloading -> {
                                                val percent = (downloadProgress * 100).toInt()
                                                if (percent > 0) "$percent%" else "Starting..."
                                            }
                                            isDownloaded -> "Downloaded"
                                            hasDownloadError -> "Retry"
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
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = Color.White,
                                    modifier = Modifier.padding(bottom = 12.dp)
                                )

                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    color = SapphoSurfaceLight.copy(alpha = 0.5f),
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
                                                    color = if (prog.completed == 1) SapphoSuccess else Color.White
                                                )
                                                if (prog.completed != 1) {
                                                    Text(
                                                        text = "of ${totalHours}h ${totalMinutes}m total",
                                                        fontSize = 14.sp,
                                                        color = SapphoIconDefault
                                                    )
                                                }
                                            }
                                            if (prog.completed != 1) {
                                                Text(
                                                    text = "$percentage%",
                                                    style = MaterialTheme.typography.headlineSmall,
                                                    color = SapphoInfo
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
                                                    .background(SapphoProgressTrack)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth(percentage / 100f)
                                                        .fillMaxHeight()
                                                        .background(
                                                            Brush.horizontalGradient(
                                                                listOf(SapphoInfo, LegacyBlueLight)
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
                                                            color = SapphoIconDefault
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
                                                        color = SapphoIconDefault
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
                            // Header row with About title and Catch Up button
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "About",
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = Color.White
                                )

                                // Show Catch Up button when AI is configured and either:
                                // - user has actual progress on this book (position > 0), OR
                                // - book has no progress but the immediately previous book in series is finished
                                val hasActualProgress = progress != null && progress!!.position > 0
                                val noProgressButPreviousFinished = !hasActualProgress && previousBookCompleted
                                if (isAiConfigured && (hasActualProgress || noProgressButPreviousFinished)) {
                                    TextButton(
                                        onClick = {
                                            showRecapDialog = true
                                            viewModel.loadRecap()
                                        },
                                        colors = ButtonDefaults.textButtonColors(
                                            contentColor = LegacyPurpleLight
                                        )
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.AutoAwesome,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Catch Up",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }

                            // Description with inline "...more" / "less"
                            val isLongDescription = description.length > 200
                            if (isDescriptionExpanded || !isLongDescription) {
                                // Show full text
                                Text(
                                    text = description,
                                    fontSize = 16.sp,
                                    color = SapphoTextLight,
                                    lineHeight = 28.8.sp
                                )
                                if (isLongDescription) {
                                    Text(
                                        text = "less",
                                        fontSize = 16.sp,
                                        color = SapphoInfo,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier
                                            .padding(top = 4.dp)
                                            .clickable { isDescriptionExpanded = false }
                                    )
                                }
                            } else {
                                // Show truncated text at word boundary with "...more" inline
                                val truncatedText = remember(description) {
                                    val maxLength = 200
                                    if (description.length <= maxLength) {
                                        description
                                    } else {
                                        // Find last space before maxLength to avoid cutting words
                                        val lastSpace = description.lastIndexOf(' ', maxLength)
                                        if (lastSpace > 100) {
                                            description.substring(0, lastSpace)
                                        } else {
                                            // Fallback if no good break point
                                            description.substring(0, maxLength)
                                        }
                                    }
                                }
                                Text(
                                    buildAnnotatedString {
                                        append(truncatedText)
                                        append(" ")
                                        withStyle(SpanStyle(color = SapphoInfo, fontWeight = FontWeight.Medium)) {
                                            append("...more")
                                        }
                                    },
                                    fontSize = 16.sp,
                                    color = SapphoTextLight,
                                    lineHeight = 28.8.sp,
                                    modifier = Modifier.clickable { isDescriptionExpanded = true }
                                )
                            }
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

                        // Format - derived from first file's extension
                        files.firstOrNull()?.let { firstFile ->
                            val format = firstFile.extension.removePrefix(".").uppercase()
                            if (format.isNotEmpty()) {
                                MetadataItem("Format", format)
                            }
                        }
                    }

                    // Files Dropdown (only show if has files)
                    if (files.isNotEmpty()) {
                        var filesExpanded by remember { mutableStateOf(false) }

                        Spacer(modifier = Modifier.height(24.dp))

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
                                color = SapphoSurfaceLight.copy(alpha = 0.5f),
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
                                            tint = SapphoText,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Text(
                                            text = "${files.size} File${if (files.size != 1) "s" else ""}",
                                            color = SapphoText,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                    Icon(
                                        imageVector = if (filesExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                        contentDescription = null,
                                        tint = SapphoText,
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
                                            color = SapphoSurfaceLight.copy(alpha = 0.3f)
                                        ) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(12.dp)
                                            ) {
                                                Text(
                                                    text = file.name,
                                                    color = SapphoText,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
                                                Text(
                                                    text = formatFileSize(file.size),
                                                    color = SapphoIconDefault,
                                                    fontSize = 12.sp
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

        // Chapters Dialog
        if (showChaptersDialog) {
            ChaptersDialog(
                chapters = chapters,
                audiobook = audiobook,
                currentAudiobook = currentAudiobook,
                currentPosition = currentPosition,
                isAdmin = isAdmin,
                isSavingChapters = isSavingChapters,
                chapterSaveResult = chapterSaveResult,
                isFetchingChapters = isFetchingChapters,
                fetchChaptersResult = fetchChaptersResult,
                onChapterClick = { chapter ->
                    audiobook?.let { book ->
                        val service = AudioPlaybackService.instance
                        if (currentAudiobook?.id == book.id && service != null) {
                            service.seekToAndPlay(chapter.startTime.toLong())
                        } else {
                            onPlayClick(book.id, chapter.startTime.toInt())
                        }
                    }
                    showChaptersDialog = false
                },
                onSaveChapters = { chapterUpdates ->
                    viewModel.updateChapters(chapterUpdates) {}
                },
                onFetchChapters = { asin ->
                    viewModel.fetchChaptersFromAudnexus(asin) {}
                },
                onClearChapterSaveResult = { viewModel.clearChapterSaveResult() },
                onClearFetchChaptersResult = { viewModel.clearFetchChaptersResult() },
                onDismiss = { showChaptersDialog = false }
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
                        color = SapphoTextLight
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.deleteDownload()
                            showDeleteDownloadDialog = false
                        }
                    ) {
                        Text("Remove", color = SapphoError)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDownloadDialog = false }) {
                        Text("Cancel", color = SapphoInfo)
                    }
                },
                containerColor = SapphoSurfaceLight
            )
        }

        // AI Recap Dialog (Catch Up)
        if (showRecapDialog) {
            Dialog(
                onDismissRequest = {
                    showRecapDialog = false
                    viewModel.dismissRecap()
                }
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 200.dp, max = 500.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = SapphoSurfaceLight
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp)
                    ) {
                        // Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.AutoAwesome,
                                    contentDescription = null,
                                    tint = LegacyPurpleLight,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Catch Up",
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = Color.White
                                )
                            }
                            IconButton(
                                onClick = {
                                    showRecapDialog = false
                                    viewModel.dismissRecap()
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = SapphoIconDefault
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Content
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        ) {
                            when {
                                isLoadingRecap -> {
                                    Column(
                                        modifier = Modifier.fillMaxSize(),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        CircularProgressIndicator(
                                            color = LegacyPurpleLight,
                                            modifier = Modifier.size(40.dp)
                                        )
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Text(
                                            text = "Generating recap...",
                                            fontSize = 14.sp,
                                            color = SapphoIconDefault
                                        )
                                        Text(
                                            text = "This may take a moment",
                                            fontSize = 12.sp,
                                            color = SapphoTextMuted
                                        )
                                    }
                                }
                                recapError != null -> {
                                    Column(
                                        modifier = Modifier.fillMaxSize(),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            text = "Failed to load recap",
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = SapphoError
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = recapError ?: "Unknown error",
                                            fontSize = 14.sp,
                                            color = SapphoIconDefault,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                                recap != null -> {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .verticalScroll(rememberScrollState())
                                    ) {
                                        Text(
                                            text = recap!!.recap,
                                            fontSize = 15.sp,
                                            color = SapphoTextLight,
                                            lineHeight = 24.sp
                                        )
                                        if (recap!!.cached == true) {
                                            Spacer(modifier = Modifier.height(16.dp))
                                            Text(
                                                text = "Cached recap",
                                                fontSize = 12.sp,
                                                color = SapphoTextMuted,
                                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                            )
                                        }
                                    }
                                }
                                else -> {
                                    Text(
                                        text = "No recap available",
                                        fontSize = 14.sp,
                                        color = SapphoIconDefault,
                                        modifier = Modifier.align(Alignment.Center)
                                    )
                                }
                            }
                        }

                        // Regenerate button (only show when recap is loaded)
                        if (recap != null && !isLoadingRecap) {
                            Spacer(modifier = Modifier.height(16.dp))
                            TextButton(
                                onClick = {
                                    viewModel.clearRecap()
                                    viewModel.loadRecap()
                                },
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = SapphoInfo
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Regenerate",
                                    color = SapphoInfo
                                )
                            }
                        }
                    }
                }
            }
        }

        // Add to Collection Dialog
        if (showCollectionsDialog) {
            audiobook?.let { book ->
                AddToCollectionDialog(
                    collections = collections,
                    bookCollections = bookCollections,
                    isLoading = isLoadingCollections,
                    onDismiss = { showCollectionsDialog = false },
                    onToggleCollection = { collectionId ->
                        viewModel.toggleBookInCollection(collectionId, book.id)
                    },
                    onCreateCollection = { name ->
                        viewModel.createCollectionAndAddBook(name, book.id)
                    }
                )
            }
        }

        // Delete Book Confirmation Dialog
        if (showDeleteBookDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteBookDialog = false },
                title = { Text("Delete Audiobook", color = Color.White) },
                text = {
                    Text(
                        "Delete \"${audiobook?.title}\"? This action cannot be undone.",
                        color = SapphoTextLight
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDeleteBookDialog = false
                            viewModel.deleteAudiobook {
                                onBackClick()
                            }
                        }
                    ) {
                        Text("Delete", color = SapphoError)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteBookDialog = false }) {
                        Text("Cancel", color = SapphoInfo)
                    }
                },
                containerColor = SapphoSurfaceLight
            )
        }

        // Edit Metadata Dialog (admin only)
        if (showEditMetadataDialog) {
            audiobook?.let { book ->
                EditMetadataDialog(
                    audiobook = book,
                    isSaving = isSavingMetadata,
                    isSearching = isSearchingMetadata,
                    isEmbedding = isEmbeddingMetadata,
                    isFetchingChapters = isFetchingChapters,
                    searchResults = metadataSearchResults,
                    searchError = metadataSearchError,
                    embedResult = embedMetadataResult,
                    fetchChaptersResult = fetchChaptersResult,
                    onDismiss = {
                        showEditMetadataDialog = false
                        viewModel.clearMetadataSearchResults()
                        viewModel.clearEmbedMetadataResult()
                        viewModel.clearFetchChaptersResult()
                    },
                    onSave = { request ->
                        viewModel.updateMetadata(request) {
                            showEditMetadataDialog = false
                            viewModel.clearMetadataSearchResults()
                        }
                    },
                    onSaveAndEmbed = { request ->
                        viewModel.updateMetadata(request) {
                            viewModel.embedMetadata()
                        }
                    },
                    onSearch = { searchTitle, searchAuthor ->
                        viewModel.searchMetadata(searchTitle, searchAuthor)
                    },
                    onClearSearch = {
                        viewModel.clearMetadataSearchResults()
                    },
                    onFetchChapters = { asin ->
                        viewModel.fetchChaptersFromAudnexus(asin) {}
                    }
                )
            }
        }

        // Close dialog on successful embed
        LaunchedEffect(embedMetadataResult) {
            if (embedMetadataResult?.contains("success", ignoreCase = true) == true) {
                kotlinx.coroutines.delay(1500)
                showEditMetadataDialog = false
                viewModel.clearMetadataSearchResults()
                viewModel.clearEmbedMetadataResult()
            }
        }

        // Show snackbar for save result
        metadataSaveResult?.let { result ->
            LaunchedEffect(result) {
                kotlinx.coroutines.delay(3000)
                viewModel.clearMetadataSaveResult()
            }
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
            color = SapphoIconDefault,
            letterSpacing = 0.5.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = SapphoInfo // Blue color to indicate clickable
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
            color = SapphoIconDefault,
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
            .background(SapphoSurface, RoundedCornerShape(8.dp))
            .border(1.dp, SapphoProgressTrack, RoundedCornerShape(8.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = chapter.title ?: "Chapter $index",
            fontSize = 14.sp,
            color = SapphoTextLight,
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
                color = SapphoIconDefault
            )
        }
    }
}

@Composable
private fun FileItem(file: com.sappho.audiobooks.domain.model.AudiobookFile) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SapphoSurface, RoundedCornerShape(8.dp))
            .border(1.dp, SapphoProgressTrack, RoundedCornerShape(8.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = file.filename,
            fontSize = 14.sp,
            color = SapphoTextLight,
            modifier = Modifier.weight(1f)
        )

        // Format file size
        val sizeText = when {
            file.size >= 1_073_741_824 -> String.format(java.util.Locale.US, "%.2f GB", file.size / 1_073_741_824.0)
            file.size >= 1_048_576 -> String.format(java.util.Locale.US, "%.2f MB", file.size / 1_048_576.0)
            file.size >= 1_024 -> String.format(java.util.Locale.US, "%.2f KB", file.size / 1_024.0)
            else -> "$file.size B"
        }

        Text(
            text = sizeText,
            fontSize = 12.sp,
            color = SapphoIconDefault
        )
    }
}

private fun formatFileSize(size: Long): String {
    return when {
        size >= 1_073_741_824 -> String.format(java.util.Locale.US, "%.2f GB", size / 1_073_741_824.0)
        size >= 1_048_576 -> String.format(java.util.Locale.US, "%.2f MB", size / 1_048_576.0)
        size >= 1_024 -> String.format(java.util.Locale.US, "%.2f KB", size / 1_024.0)
        else -> "$size B"
    }
}

private fun formatTime(seconds: Long): String {
    val hours = seconds / 3600
    val mins = (seconds % 3600) / 60
    val secs = seconds % 60
    return if (hours > 0) {
        String.format(java.util.Locale.US, "%d:%02d:%02d", hours, mins, secs)
    } else {
        String.format(java.util.Locale.US, "%d:%02d", mins, secs)
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
    modifier: Modifier = Modifier,
    isLoading: Boolean = false
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
                    tint = if (isSelected) SapphoStarFilled else SapphoTextMuted,
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        if (isLoading) {
            Spacer(modifier = Modifier.width(8.dp))
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = SapphoStarFilled,
                strokeWidth = 2.dp
            )
        }
    }
}

@Composable
private fun CompactStarRating(
    rating: Int?,
    onRatingChanged: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 5 clickable stars (smaller)
        for (starIndex in 1..5) {
            val isSelected = rating != null && starIndex <= rating

            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clickable(enabled = !isLoading) {
                        if (rating == starIndex) {
                            onRatingChanged(null)
                        } else {
                            onRatingChanged(starIndex)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isSelected) Icons.Filled.Star else Icons.Filled.StarBorder,
                    contentDescription = "Star $starIndex",
                    tint = if (isSelected) SapphoStarFilled else SapphoTextMuted,
                    modifier = Modifier.size(26.dp)
                )
            }
        }

        if (isLoading) {
            Spacer(modifier = Modifier.width(4.dp))
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                color = SapphoStarFilled,
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
                tint = if (fillAmount > 0f) SapphoStarFilled else SapphoTextMuted,
                modifier = Modifier.size(16.dp)
            )
        }

        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = String.format(java.util.Locale.US, "%.1f", average),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = SapphoStarFilled
        )
    }
}

@Composable
fun EditMetadataDialog(
    audiobook: com.sappho.audiobooks.domain.model.Audiobook,
    isSaving: Boolean,
    isSearching: Boolean,
    isEmbedding: Boolean,
    isFetchingChapters: Boolean,
    searchResults: List<MetadataSearchResult>,
    searchError: String?,
    embedResult: String?,
    fetchChaptersResult: String?,
    onDismiss: () -> Unit,
    onSave: (AudiobookUpdateRequest) -> Unit,
    onSaveAndEmbed: (AudiobookUpdateRequest) -> Unit,
    onSearch: (String, String) -> Unit,
    onClearSearch: () -> Unit,
    onFetchChapters: (String) -> Unit
) {
    var title by remember { mutableStateOf(audiobook.title) }
    var subtitle by remember { mutableStateOf(audiobook.subtitle ?: "") }
    var author by remember { mutableStateOf(audiobook.author ?: "") }
    var narrator by remember { mutableStateOf(audiobook.narrator ?: "") }
    var series by remember { mutableStateOf(audiobook.series ?: "") }
    var seriesPosition by remember { mutableStateOf(audiobook.seriesPosition?.toString() ?: "") }
    var genre by remember { mutableStateOf(audiobook.genre ?: "") }
    var tags by remember { mutableStateOf(audiobook.tags ?: "") }
    var publishedYear by remember { mutableStateOf(audiobook.publishYear?.toString() ?: "") }
    var copyrightYear by remember { mutableStateOf(audiobook.copyrightYear?.toString() ?: "") }
    var publisher by remember { mutableStateOf(audiobook.publisher ?: "") }
    var description by remember { mutableStateOf(audiobook.description ?: "") }
    var isbn by remember { mutableStateOf(audiobook.isbn ?: "") }
    var asin by remember { mutableStateOf(audiobook.asin ?: "") }
    var language by remember { mutableStateOf(audiobook.language ?: "") }
    var bookRating by remember { mutableStateOf(audiobook.rating?.toString() ?: "") }
    var abridged by remember { mutableStateOf((audiobook.abridged ?: 0) == 1) }
    var coverUrl by remember { mutableStateOf("") }
    var showSearchResults by remember { mutableStateOf(false) }
    var selectedResultHasChapters by remember { mutableStateOf(false) }
    var selectedResultForPreview by remember { mutableStateOf<MetadataSearchResult?>(null) }

    // When search results come in, show them
    LaunchedEffect(searchResults) {
        if (searchResults.isNotEmpty()) {
            showSearchResults = true
        }
    }

    val isBusy = isSaving || isSearching || isEmbedding || isFetchingChapters

    Dialog(onDismissRequest = { if (!isBusy) onDismiss() }) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f),
            shape = RoundedCornerShape(16.dp),
            color = SapphoSurfaceLight
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Header with title and X button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Edit Metadata",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    IconButton(
                        onClick = onDismiss,
                        enabled = !isBusy,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = if (!isBusy) SapphoIconDefault else LegacyGrayDark
                        )
                    }
                }

                // Scrollable content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                // Embed result message
                embedResult?.let { result ->
                    val isSuccess = result.contains("success", ignoreCase = true)
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSuccess) SapphoSuccess.copy(alpha = 0.15f) else SapphoError.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = result,
                            fontSize = 12.sp,
                            color = if (isSuccess) LegacyGreenLight else LegacyRedLight,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                // Fetch chapters result message
                fetchChaptersResult?.let { result ->
                    val isSuccess = result.contains("success", ignoreCase = true)
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSuccess) SapphoSuccess.copy(alpha = 0.15f) else SapphoError.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = result,
                            fontSize = 12.sp,
                            color = if (isSuccess) LegacyGreenLight else LegacyRedLight,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                // Embedding progress indicator
                if (isEmbedding) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = SapphoSuccess,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Embedding metadata into file...",
                            fontSize = 12.sp,
                            color = LegacyGreenLight
                        )
                    }
                }

                // Fetching chapters progress indicator
                if (isFetchingChapters) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = LegacyPurple,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Fetching chapters from Audnexus...",
                            fontSize = 12.sp,
                            color = LegacyPurpleLight
                        )
                    }
                }

                // Lookup Button
                Button(
                    onClick = {
                        onSearch(title, author)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isBusy && (title.isNotBlank() || author.isNotBlank()),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LegacyPurple.copy(alpha = 0.15f),
                        contentColor = LegacyPurpleLight
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    if (isSearching) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = LegacyPurpleLight,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Searching...")
                    } else {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Lookup Metadata")
                    }
                }

                // Search Results Section
                if (showSearchResults && searchResults.isNotEmpty()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = SapphoProgressTrack.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, LegacyPurple.copy(alpha = 0.3f))
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${searchResults.size} Results",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = LegacyPurpleLight
                                )
                                TextButton(
                                    onClick = {
                                        showSearchResults = false
                                        onClearSearch()
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text("Hide", color = SapphoIconDefault, fontSize = 12.sp)
                                }
                            }

                            searchResults.forEach { result ->
                                MetadataSearchResultItem(
                                    result = result,
                                    onSelect = {
                                        // Show preview dialog instead of directly applying
                                        selectedResultForPreview = result
                                    }
                                )
                            }
                        }
                    }
                }

                // Search Error
                searchError?.let { error ->
                    Text(
                        text = error,
                        fontSize = 12.sp,
                        color = SapphoError
                    )
                }

                Divider(color = SapphoProgressTrack)

                // ===== BASIC INFO SECTION =====
                Text(
                    text = "Basic Info",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = SapphoIconDefault,
                    modifier = Modifier.padding(top = 4.dp)
                )

                // Title
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = editTextFieldColors()
                )

                // Subtitle
                OutlinedTextField(
                    value = subtitle,
                    onValueChange = { subtitle = it },
                    label = { Text("Subtitle") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = editTextFieldColors()
                )

                // Author
                OutlinedTextField(
                    value = author,
                    onValueChange = { author = it },
                    label = { Text("Author") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = editTextFieldColors()
                )

                // Narrator
                OutlinedTextField(
                    value = narrator,
                    onValueChange = { narrator = it },
                    label = { Text("Narrator") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = editTextFieldColors()
                )

                // Series row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = series,
                        onValueChange = { series = it },
                        label = { Text("Series") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        colors = editTextFieldColors()
                    )
                    OutlinedTextField(
                        value = seriesPosition,
                        onValueChange = { seriesPosition = it },
                        label = { Text("#") },
                        modifier = Modifier.width(60.dp),
                        singleLine = true,
                        colors = editTextFieldColors()
                    )
                }

                Divider(color = SapphoProgressTrack, modifier = Modifier.padding(vertical = 4.dp))

                // ===== CLASSIFICATION SECTION =====
                Text(
                    text = "Classification",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = SapphoIconDefault
                )

                // Genre
                OutlinedTextField(
                    value = genre,
                    onValueChange = { genre = it },
                    label = { Text("Genre") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = editTextFieldColors()
                )

                // Tags
                OutlinedTextField(
                    value = tags,
                    onValueChange = { tags = it },
                    label = { Text("Tags (comma-separated)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = editTextFieldColors()
                )

                // Language
                OutlinedTextField(
                    value = language,
                    onValueChange = { language = it },
                    label = { Text("Language") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = editTextFieldColors()
                )

                // Rating and Abridged row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = bookRating,
                        onValueChange = { bookRating = it },
                        label = { Text("Rating") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        colors = editTextFieldColors()
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Checkbox(
                            checked = abridged,
                            onCheckedChange = { abridged = it },
                            colors = CheckboxDefaults.colors(
                                checkedColor = SapphoInfo,
                                uncheckedColor = SapphoIconDefault
                            )
                        )
                        Text(
                            text = "Abridged",
                            color = SapphoText,
                            fontSize = 14.sp
                        )
                    }
                }

                Divider(color = SapphoProgressTrack, modifier = Modifier.padding(vertical = 4.dp))

                // ===== PUBLISHING SECTION =====
                Text(
                    text = "Publishing",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = SapphoIconDefault
                )

                // Publisher
                OutlinedTextField(
                    value = publisher,
                    onValueChange = { publisher = it },
                    label = { Text("Publisher") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = editTextFieldColors()
                )

                // Published Year and Copyright Year row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = publishedYear,
                        onValueChange = { publishedYear = it },
                        label = { Text("Published Year") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        colors = editTextFieldColors()
                    )
                    OutlinedTextField(
                        value = copyrightYear,
                        onValueChange = { copyrightYear = it },
                        label = { Text("Copyright Year") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        colors = editTextFieldColors()
                    )
                }

                Divider(color = SapphoProgressTrack, modifier = Modifier.padding(vertical = 4.dp))

                // ===== IDENTIFIERS SECTION =====
                Text(
                    text = "Identifiers",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = SapphoIconDefault
                )

                // ISBN
                OutlinedTextField(
                    value = isbn,
                    onValueChange = { isbn = it },
                    label = { Text("ISBN") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = editTextFieldColors()
                )

                // ASIN with Fetch Chapters button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = asin,
                        onValueChange = {
                            asin = it
                            // Enable fetch chapters if valid ASIN format
                            selectedResultHasChapters = it.matches(Regex("^[A-Z0-9]{10}$", RegexOption.IGNORE_CASE))
                        },
                        label = { Text("ASIN") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        colors = editTextFieldColors()
                    )
                    Button(
                        onClick = { onFetchChapters(asin) },
                        enabled = !isBusy && asin.isNotBlank() && asin.matches(Regex("^[A-Z0-9]{10}$", RegexOption.IGNORE_CASE)),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = LegacyPurple
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.List,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Chapters", fontSize = 12.sp)
                    }
                }

                Divider(color = SapphoProgressTrack, modifier = Modifier.padding(vertical = 4.dp))

                // ===== COVER SECTION =====
                Text(
                    text = "Cover Image",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = SapphoIconDefault
                )

                // Cover URL
                OutlinedTextField(
                    value = coverUrl,
                    onValueChange = { coverUrl = it },
                    label = { Text("Cover Image URL") },
                    placeholder = { Text("Enter URL to download cover", color = SapphoTextMuted) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = editTextFieldColors()
                )

                if (coverUrl.isNotBlank()) {
                    Text(
                        text = "Cover will be downloaded from URL when saved",
                        fontSize = 11.sp,
                        color = SapphoSuccess,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }

                Divider(color = SapphoProgressTrack, modifier = Modifier.padding(vertical = 4.dp))

                // ===== DESCRIPTION SECTION =====
                Text(
                    text = "Description",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = SapphoIconDefault
                )

                // Description (multi-line)
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 100.dp),
                    maxLines = 5,
                    colors = editTextFieldColors()
                )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Bottom buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Save button
                    Button(
                        onClick = {
                            val request = AudiobookUpdateRequest(
                                title = title.ifBlank { null },
                                subtitle = subtitle.ifBlank { null },
                                author = author.ifBlank { null },
                                narrator = narrator.ifBlank { null },
                                series = series.ifBlank { null },
                                seriesPosition = seriesPosition.toFloatOrNull(),
                                genre = genre.ifBlank { null },
                                tags = tags.ifBlank { null },
                                publishedYear = publishedYear.toIntOrNull(),
                                copyrightYear = copyrightYear.toIntOrNull(),
                                publisher = publisher.ifBlank { null },
                                description = description.ifBlank { null },
                                isbn = isbn.ifBlank { null },
                                asin = asin.ifBlank { null },
                                language = language.ifBlank { null },
                                rating = bookRating.toFloatOrNull(),
                                abridged = if (abridged) true else null,
                                coverUrl = coverUrl.ifBlank { null }
                            )
                            onSave(request)
                        },
                        enabled = !isBusy,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SapphoInfo
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        if (isSaving && !isEmbedding) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text("Save")
                    }

                    // Save & Embed button
                    Button(
                        onClick = {
                            val request = AudiobookUpdateRequest(
                                title = title.ifBlank { null },
                                subtitle = subtitle.ifBlank { null },
                                author = author.ifBlank { null },
                                narrator = narrator.ifBlank { null },
                                series = series.ifBlank { null },
                                seriesPosition = seriesPosition.toFloatOrNull(),
                                genre = genre.ifBlank { null },
                                tags = tags.ifBlank { null },
                                publishedYear = publishedYear.toIntOrNull(),
                                copyrightYear = copyrightYear.toIntOrNull(),
                                publisher = publisher.ifBlank { null },
                                description = description.ifBlank { null },
                                isbn = isbn.ifBlank { null },
                                asin = asin.ifBlank { null },
                                language = language.ifBlank { null },
                                rating = bookRating.toFloatOrNull(),
                                abridged = if (abridged) true else null,
                                coverUrl = coverUrl.ifBlank { null }
                            )
                            onSaveAndEmbed(request)
                        },
                        enabled = !isBusy,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SapphoSuccess
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        if (isSaving && isEmbedding) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text("Save & Embed")
                    }
                }
            }
        }
    }

    // Metadata Preview Dialog - shows when a search result is selected
    selectedResultForPreview?.let { result ->
        MetadataPreviewDialog(
            result = result,
            currentTitle = title,
            currentSubtitle = subtitle,
            currentAuthor = author,
            currentNarrator = narrator,
            currentSeries = series,
            currentSeriesPosition = seriesPosition,
            currentGenre = genre,
            currentTags = tags,
            currentPublisher = publisher,
            currentPublishedYear = publishedYear,
            currentCopyrightYear = copyrightYear,
            currentIsbn = isbn,
            currentDescription = description,
            currentLanguage = language,
            currentRating = bookRating,
            currentAsin = asin,
            currentCoverUrl = coverUrl,
            onDismiss = { selectedResultForPreview = null },
            onApply = { selectedFields ->
                // Apply only selected fields
                if (selectedFields.contains("title")) result.title?.let { title = it }
                if (selectedFields.contains("subtitle")) result.subtitle?.let { subtitle = it }
                if (selectedFields.contains("author")) result.author?.let { author = it }
                if (selectedFields.contains("narrator")) result.narrator?.let { narrator = it }
                if (selectedFields.contains("series")) result.series?.let { series = it }
                if (selectedFields.contains("seriesPosition")) result.seriesPosition?.let { seriesPosition = it.toString() }
                if (selectedFields.contains("genre")) result.genre?.let { genre = it }
                if (selectedFields.contains("tags")) result.tags?.let { tags = it }
                if (selectedFields.contains("publisher")) result.publisher?.let { publisher = it }
                if (selectedFields.contains("publishedYear")) result.publishedYear?.let { publishedYear = it.toString() }
                if (selectedFields.contains("copyrightYear")) result.copyrightYear?.let { copyrightYear = it.toString() }
                if (selectedFields.contains("isbn")) result.isbn?.let { isbn = it }
                if (selectedFields.contains("description")) result.description?.let { description = it }
                if (selectedFields.contains("language")) result.language?.let { language = it }
                if (selectedFields.contains("rating")) result.rating?.let { bookRating = it.toString() }
                if (selectedFields.contains("asin")) result.asin?.let { asin = it }
                if (selectedFields.contains("coverUrl")) result.image?.let { coverUrl = it }

                // Update chapter availability
                selectedResultHasChapters = result.hasChapters == true && result.asin != null

                selectedResultForPreview = null
                showSearchResults = false
            }
        )
    }
}

@Composable
private fun MetadataPreviewDialog(
    result: MetadataSearchResult,
    currentTitle: String,
    currentSubtitle: String,
    currentAuthor: String,
    currentNarrator: String,
    currentSeries: String,
    currentSeriesPosition: String,
    currentGenre: String,
    currentTags: String,
    currentPublisher: String,
    currentPublishedYear: String,
    currentCopyrightYear: String,
    currentIsbn: String,
    currentDescription: String,
    currentLanguage: String,
    currentRating: String,
    currentAsin: String,
    currentCoverUrl: String,
    onDismiss: () -> Unit,
    onApply: (Set<String>) -> Unit
) {
    // Track which fields are selected
    val selectedFields = remember { mutableStateMapOf<String, Boolean>() }

    // Initialize with fields that have new values different from current
    LaunchedEffect(result) {
        if (result.title != null && result.title != currentTitle) selectedFields["title"] = true
        if (result.subtitle != null && result.subtitle != currentSubtitle) selectedFields["subtitle"] = true
        if (result.author != null && result.author != currentAuthor) selectedFields["author"] = true
        if (result.narrator != null && result.narrator != currentNarrator) selectedFields["narrator"] = true
        if (result.series != null && result.series != currentSeries) selectedFields["series"] = true
        if (result.seriesPosition != null && result.seriesPosition.toString() != currentSeriesPosition) selectedFields["seriesPosition"] = true
        if (result.genre != null && result.genre != currentGenre) selectedFields["genre"] = true
        if (result.tags != null && result.tags != currentTags) selectedFields["tags"] = true
        if (result.publisher != null && result.publisher != currentPublisher) selectedFields["publisher"] = true
        if (result.publishedYear != null && result.publishedYear.toString() != currentPublishedYear) selectedFields["publishedYear"] = true
        if (result.copyrightYear != null && result.copyrightYear.toString() != currentCopyrightYear) selectedFields["copyrightYear"] = true
        if (result.isbn != null && result.isbn != currentIsbn) selectedFields["isbn"] = true
        if (result.description != null && result.description != currentDescription) selectedFields["description"] = true
        if (result.language != null && result.language != currentLanguage) selectedFields["language"] = true
        if (result.rating != null && result.rating.toString() != currentRating) selectedFields["rating"] = true
        if (result.asin != null && result.asin != currentAsin) selectedFields["asin"] = true
        if (result.image != null && result.image != currentCoverUrl) selectedFields["coverUrl"] = true
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(16.dp),
            color = SapphoSurfaceLight
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Header with title and X button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Apply Metadata",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
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

                // Scrollable content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Select fields to apply:",
                        fontSize = 12.sp,
                        color = SapphoIconDefault
                    )

                // Field rows with checkboxes
                result.title?.let { newValue ->
                    if (newValue != currentTitle) {
                        FieldPreviewRow(
                            fieldName = "Title",
                            fieldKey = "title",
                            oldValue = currentTitle.ifBlank { "(empty)" },
                            newValue = newValue,
                            isSelected = selectedFields["title"] ?: false,
                            onSelectionChange = { selectedFields["title"] = it }
                        )
                    }
                }

                result.subtitle?.let { newValue ->
                    if (newValue != currentSubtitle) {
                        FieldPreviewRow(
                            fieldName = "Subtitle",
                            fieldKey = "subtitle",
                            oldValue = currentSubtitle.ifBlank { "(empty)" },
                            newValue = newValue,
                            isSelected = selectedFields["subtitle"] ?: false,
                            onSelectionChange = { selectedFields["subtitle"] = it }
                        )
                    }
                }

                result.author?.let { newValue ->
                    if (newValue != currentAuthor) {
                        FieldPreviewRow(
                            fieldName = "Author",
                            fieldKey = "author",
                            oldValue = currentAuthor.ifBlank { "(empty)" },
                            newValue = newValue,
                            isSelected = selectedFields["author"] ?: false,
                            onSelectionChange = { selectedFields["author"] = it }
                        )
                    }
                }

                result.narrator?.let { newValue ->
                    if (newValue != currentNarrator) {
                        FieldPreviewRow(
                            fieldName = "Narrator",
                            fieldKey = "narrator",
                            oldValue = currentNarrator.ifBlank { "(empty)" },
                            newValue = newValue,
                            isSelected = selectedFields["narrator"] ?: false,
                            onSelectionChange = { selectedFields["narrator"] = it }
                        )
                    }
                }

                result.series?.let { newValue ->
                    if (newValue != currentSeries) {
                        FieldPreviewRow(
                            fieldName = "Series",
                            fieldKey = "series",
                            oldValue = currentSeries.ifBlank { "(empty)" },
                            newValue = newValue,
                            isSelected = selectedFields["series"] ?: false,
                            onSelectionChange = { selectedFields["series"] = it }
                        )
                    }
                }

                result.seriesPosition?.let { newValue ->
                    if (newValue.toString() != currentSeriesPosition) {
                        FieldPreviewRow(
                            fieldName = "Series #",
                            fieldKey = "seriesPosition",
                            oldValue = currentSeriesPosition.ifBlank { "(empty)" },
                            newValue = newValue.toString(),
                            isSelected = selectedFields["seriesPosition"] ?: false,
                            onSelectionChange = { selectedFields["seriesPosition"] = it }
                        )
                    }
                }

                result.genre?.let { newValue ->
                    if (newValue != currentGenre) {
                        FieldPreviewRow(
                            fieldName = "Genre",
                            fieldKey = "genre",
                            oldValue = currentGenre.ifBlank { "(empty)" },
                            newValue = newValue,
                            isSelected = selectedFields["genre"] ?: false,
                            onSelectionChange = { selectedFields["genre"] = it }
                        )
                    }
                }

                result.tags?.let { newValue ->
                    if (newValue != currentTags) {
                        FieldPreviewRow(
                            fieldName = "Tags",
                            fieldKey = "tags",
                            oldValue = currentTags.ifBlank { "(empty)" },
                            newValue = newValue,
                            isSelected = selectedFields["tags"] ?: false,
                            onSelectionChange = { selectedFields["tags"] = it }
                        )
                    }
                }

                result.publisher?.let { newValue ->
                    if (newValue != currentPublisher) {
                        FieldPreviewRow(
                            fieldName = "Publisher",
                            fieldKey = "publisher",
                            oldValue = currentPublisher.ifBlank { "(empty)" },
                            newValue = newValue,
                            isSelected = selectedFields["publisher"] ?: false,
                            onSelectionChange = { selectedFields["publisher"] = it }
                        )
                    }
                }

                result.publishedYear?.let { newValue ->
                    if (newValue.toString() != currentPublishedYear) {
                        FieldPreviewRow(
                            fieldName = "Published Year",
                            fieldKey = "publishedYear",
                            oldValue = currentPublishedYear.ifBlank { "(empty)" },
                            newValue = newValue.toString(),
                            isSelected = selectedFields["publishedYear"] ?: false,
                            onSelectionChange = { selectedFields["publishedYear"] = it }
                        )
                    }
                }

                result.copyrightYear?.let { newValue ->
                    if (newValue.toString() != currentCopyrightYear) {
                        FieldPreviewRow(
                            fieldName = "Copyright Year",
                            fieldKey = "copyrightYear",
                            oldValue = currentCopyrightYear.ifBlank { "(empty)" },
                            newValue = newValue.toString(),
                            isSelected = selectedFields["copyrightYear"] ?: false,
                            onSelectionChange = { selectedFields["copyrightYear"] = it }
                        )
                    }
                }

                result.isbn?.let { newValue ->
                    if (newValue != currentIsbn) {
                        FieldPreviewRow(
                            fieldName = "ISBN",
                            fieldKey = "isbn",
                            oldValue = currentIsbn.ifBlank { "(empty)" },
                            newValue = newValue,
                            isSelected = selectedFields["isbn"] ?: false,
                            onSelectionChange = { selectedFields["isbn"] = it }
                        )
                    }
                }

                result.language?.let { newValue ->
                    if (newValue != currentLanguage) {
                        FieldPreviewRow(
                            fieldName = "Language",
                            fieldKey = "language",
                            oldValue = currentLanguage.ifBlank { "(empty)" },
                            newValue = newValue,
                            isSelected = selectedFields["language"] ?: false,
                            onSelectionChange = { selectedFields["language"] = it }
                        )
                    }
                }

                result.rating?.let { newValue ->
                    if (newValue.toString() != currentRating) {
                        FieldPreviewRow(
                            fieldName = "Rating",
                            fieldKey = "rating",
                            oldValue = currentRating.ifBlank { "(empty)" },
                            newValue = newValue.toString(),
                            isSelected = selectedFields["rating"] ?: false,
                            onSelectionChange = { selectedFields["rating"] = it }
                        )
                    }
                }

                result.asin?.let { newValue ->
                    if (newValue != currentAsin) {
                        FieldPreviewRow(
                            fieldName = "ASIN",
                            fieldKey = "asin",
                            oldValue = currentAsin.ifBlank { "(empty)" },
                            newValue = newValue,
                            isSelected = selectedFields["asin"] ?: false,
                            onSelectionChange = { selectedFields["asin"] = it }
                        )
                    }
                }

                result.image?.let { newValue ->
                    if (newValue != currentCoverUrl) {
                        FieldPreviewRow(
                            fieldName = "Cover URL",
                            fieldKey = "coverUrl",
                            oldValue = if (currentCoverUrl.isBlank()) "(empty)" else "(current)",
                            newValue = "(new cover)",
                            isSelected = selectedFields["coverUrl"] ?: false,
                            onSelectionChange = { selectedFields["coverUrl"] = it }
                        )
                    }
                }

                result.description?.let { newValue ->
                    if (newValue != currentDescription) {
                        FieldPreviewRow(
                            fieldName = "Description",
                            fieldKey = "description",
                            oldValue = if (currentDescription.isBlank()) "(empty)" else "(${currentDescription.take(30)}...)",
                            newValue = "(${newValue.take(30)}...)",
                            isSelected = selectedFields["description"] ?: false,
                            onSelectionChange = { selectedFields["description"] = it }
                        )
                    }
                }

                    if (selectedFields.isEmpty()) {
                        Text(
                            text = "No new values to apply - all fields match current values.",
                            fontSize = 12.sp,
                            color = SapphoTextMuted,
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Bottom button
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = SapphoSurfaceLight
                ) {
                    Button(
                        onClick = {
                            onApply(selectedFields.filter { it.value }.keys)
                        },
                        enabled = selectedFields.any { it.value },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SapphoInfo
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text("Apply Selected")
                    }
                }
            }
        }
    }
}

@Composable
private fun FieldPreviewRow(
    fieldName: String,
    fieldKey: String,
    oldValue: String,
    newValue: String,
    isSelected: Boolean,
    onSelectionChange: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(6.dp),
        color = if (isSelected) SapphoInfo.copy(alpha = 0.1f) else Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onSelectionChange(!isSelected) }
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = onSelectionChange,
                colors = CheckboxDefaults.colors(
                    checkedColor = SapphoInfo,
                    uncheckedColor = SapphoTextMuted
                ),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = fieldName,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = oldValue,
                        fontSize = 11.sp,
                        color = SapphoError,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Text(
                        text = " → ",
                        fontSize = 11.sp,
                        color = SapphoTextMuted
                    )
                    Text(
                        text = newValue,
                        fontSize = 11.sp,
                        color = SapphoSuccess,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
            }
        }
    }
}

@Composable
private fun MetadataSearchResultItem(
    result: MetadataSearchResult,
    onSelect: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect),
        shape = RoundedCornerShape(6.dp),
        color = SapphoSurfaceLight
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Cover image preview
            if (!result.image.isNullOrBlank()) {
                AsyncImage(
                    model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                        .data(result.image)
                        .crossfade(true)
                        .build(),
                    contentDescription = "Cover",
                    modifier = Modifier
                        .size(50.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                // Placeholder when no image
                Surface(
                    modifier = Modifier.size(50.dp),
                    shape = RoundedCornerShape(4.dp),
                    color = SapphoProgressTrack
                ) {
                    Box(
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = null,
                            tint = SapphoTextMuted,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            // Text content
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = result.title ?: "Unknown Title",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Chapter availability indicator
                        if (result.hasChapters == true) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = LegacyPurple.copy(alpha = 0.2f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.List,
                                        contentDescription = null,
                                        tint = LegacyPurpleLight,
                                        modifier = Modifier.size(10.dp)
                                    )
                                    Text(
                                        text = "Ch",
                                        fontSize = 10.sp,
                                        color = LegacyPurpleLight
                                    )
                                }
                            }
                        }
                        // Source badge
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = when (result.source.lowercase()) {
                                "audible" -> LegacyOrange.copy(alpha = 0.2f)
                                "google" -> SapphoInfo.copy(alpha = 0.2f)
                                else -> SapphoSuccess.copy(alpha = 0.2f)
                            }
                        ) {
                            Text(
                                text = result.source.replaceFirstChar { it.uppercase() },
                                fontSize = 10.sp,
                                color = when (result.source.lowercase()) {
                                    "audible" -> SapphoWarning
                                    "google" -> LegacyBlueLight
                                    else -> LegacyGreenLight
                                },
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                result.author?.let { author ->
                    Text(
                        text = "by $author",
                        fontSize = 12.sp,
                        color = SapphoIconDefault,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    result.narrator?.let { narrator ->
                        Text(
                            text = "Narrated: $narrator",
                            fontSize = 11.sp,
                            color = SapphoTextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                    result.series?.let { series ->
                        val seriesText = if (result.seriesPosition != null) {
                            "$series #${result.seriesPosition}"
                        } else {
                            series
                        }
                        Text(
                            text = seriesText,
                            fontSize = 11.sp,
                            color = LegacyPurple,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun editTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White,
    unfocusedTextColor = SapphoTextLight,
    focusedBorderColor = SapphoInfo,
    unfocusedBorderColor = SapphoProgressTrack,
    focusedLabelColor = SapphoInfo,
    unfocusedLabelColor = SapphoIconDefault,
    cursorColor = SapphoInfo
)

@Composable
private fun ChaptersDialog(
    chapters: List<com.sappho.audiobooks.domain.model.Chapter>,
    audiobook: com.sappho.audiobooks.domain.model.Audiobook?,
    currentAudiobook: com.sappho.audiobooks.domain.model.Audiobook?,
    currentPosition: Long,
    isAdmin: Boolean,
    isSavingChapters: Boolean,
    chapterSaveResult: String?,
    isFetchingChapters: Boolean,
    fetchChaptersResult: String?,
    onChapterClick: (com.sappho.audiobooks.domain.model.Chapter) -> Unit,
    onSaveChapters: (List<ChapterUpdate>) -> Unit,
    onFetchChapters: (String) -> Unit,
    onClearChapterSaveResult: () -> Unit,
    onClearFetchChaptersResult: () -> Unit,
    onDismiss: () -> Unit
) {
    var isEditMode by remember { mutableStateOf(false) }
    var editedTitles by remember(chapters) {
        mutableStateOf(chapters.associate { it.id to (it.title ?: "Chapter ${chapters.indexOf(it) + 1}") })
    }
    var showAsinInput by remember { mutableStateOf(false) }
    var asinInput by remember(audiobook) { mutableStateOf(audiobook?.asin ?: "") }

    val isBusy = isSavingChapters || isFetchingChapters

    // Determine if this book is currently playing
    val isCurrentBook = audiobook != null && currentAudiobook?.id == audiobook.id

    // Find the current chapter index based on position
    val currentChapterIndex = remember(chapters, currentPosition, isCurrentBook) {
        if (!isCurrentBook || chapters.isEmpty()) -1
        else {
            chapters.indexOfLast { it.startTime <= currentPosition }
        }
    }

    // LazyListState for auto-scrolling
    val listState = rememberLazyListState()

    // Auto-scroll to current chapter when dialog opens
    LaunchedEffect(currentChapterIndex) {
        if (currentChapterIndex > 0) {
            // Scroll to center the current chapter
            listState.animateScrollToItem(
                index = maxOf(0, currentChapterIndex - 2),
                scrollOffset = 0
            )
        }
    }

    // Reset edit mode when chapters change (after save/fetch)
    LaunchedEffect(chapters) {
        if (!isBusy) {
            editedTitles = chapters.associate { it.id to (it.title ?: "Chapter ${chapters.indexOf(it) + 1}") }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isBusy) onDismiss() },
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Chapters", color = Color.White)
                if (isAdmin && !isEditMode) {
                    IconButton(
                        onClick = { isEditMode = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit chapters",
                            tint = SapphoInfo,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Result messages
                chapterSaveResult?.let { result ->
                    val isSuccess = result.contains("success", ignoreCase = true)
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSuccess) SapphoSuccess.copy(alpha = 0.15f) else SapphoError.copy(alpha = 0.15f)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = result,
                                fontSize = 12.sp,
                                color = if (isSuccess) LegacyGreenLight else LegacyRedLight,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                onClick = onClearChapterSaveResult,
                                contentPadding = PaddingValues(4.dp)
                            ) {
                                Text("Dismiss", fontSize = 10.sp, color = SapphoIconDefault)
                            }
                        }
                    }
                }

                fetchChaptersResult?.let { result ->
                    val isSuccess = result.contains("success", ignoreCase = true)
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSuccess) SapphoSuccess.copy(alpha = 0.15f) else SapphoError.copy(alpha = 0.15f)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = result,
                                fontSize = 12.sp,
                                color = if (isSuccess) LegacyGreenLight else LegacyRedLight,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                onClick = onClearFetchChaptersResult,
                                contentPadding = PaddingValues(4.dp)
                            ) {
                                Text("Dismiss", fontSize = 10.sp, color = SapphoIconDefault)
                            }
                        }
                    }
                }

                // Admin controls when in edit mode
                if (isAdmin && isEditMode) {
                    // ASIN lookup section
                    if (showAsinInput) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = asinInput,
                                onValueChange = { asinInput = it },
                                label = { Text("ASIN", fontSize = 12.sp) },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                enabled = !isBusy,
                                colors = editTextFieldColors(),
                                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp)
                            )
                            Button(
                                onClick = {
                                    if (asinInput.isNotBlank()) {
                                        onFetchChapters(asinInput)
                                        showAsinInput = false
                                    }
                                },
                                enabled = !isBusy && asinInput.isNotBlank(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = LegacyPurple
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                if (isFetchingChapters) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        color = Color.White,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Text("Fetch", fontSize = 12.sp)
                                }
                            }
                            TextButton(
                                onClick = { showAsinInput = false },
                                enabled = !isBusy
                            ) {
                                Text("Cancel", fontSize = 12.sp, color = SapphoIconDefault)
                            }
                        }
                    } else {
                        Button(
                            onClick = { showAsinInput = true },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isBusy,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = LegacyPurple.copy(alpha = 0.15f),
                                contentColor = LegacyPurpleLight
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Lookup Chapters from Audnexus")
                        }
                    }

                    Divider(color = SapphoProgressTrack, modifier = Modifier.padding(vertical = 4.dp))
                }

                // Chapter list
                LazyColumn(
                    state = listState,
                    modifier = Modifier.heightIn(max = 350.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    itemsIndexed(chapters) { index, chapter ->
                        val isCurrentChapter = index == currentChapterIndex
                        if (isEditMode && isAdmin) {
                            // Edit mode - show text fields
                            OutlinedTextField(
                                value = editedTitles[chapter.id] ?: "",
                                onValueChange = { newTitle ->
                                    editedTitles = editedTitles.toMutableMap().apply {
                                        put(chapter.id, newTitle)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                enabled = !isBusy,
                                leadingIcon = {
                                    Text(
                                        text = "${index + 1}.",
                                        color = SapphoIconDefault,
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(start = 8.dp)
                                    )
                                },
                                trailingIcon = {
                                    Text(
                                        text = formatTime(chapter.startTime.toLong()),
                                        color = SapphoIconDefault,
                                        fontSize = 10.sp,
                                        modifier = Modifier.padding(end = 8.dp)
                                    )
                                },
                                colors = editTextFieldColors(),
                                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp)
                            )
                        } else {
                            // View mode - clickable chapter with highlighting for current chapter
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                color = if (isCurrentChapter) SapphoInfo.copy(alpha = 0.15f) else Color.Transparent
                            ) {
                                TextButton(
                                    onClick = { onChapterClick(chapter) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            modifier = Modifier.weight(1f),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (isCurrentChapter) {
                                                Icon(
                                                    imageVector = Icons.Default.PlayArrow,
                                                    contentDescription = "Currently playing",
                                                    tint = SapphoInfo,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                            Text(
                                                text = chapter.title ?: "Chapter ${index + 1}",
                                                color = if (isCurrentChapter) SapphoInfo else Color.White,
                                                fontWeight = if (isCurrentChapter) FontWeight.SemiBold else FontWeight.Normal,
                                                modifier = Modifier.padding(end = 8.dp),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Text(
                                            text = formatTime(chapter.startTime.toLong()),
                                            color = if (isCurrentChapter) SapphoInfo else SapphoIconDefault,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (isEditMode && isAdmin) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = {
                            isEditMode = false
                            // Reset to original titles
                            editedTitles = chapters.associate { it.id to (it.title ?: "Chapter ${chapters.indexOf(it) + 1}") }
                        },
                        enabled = !isBusy
                    ) {
                        Text("Cancel", color = SapphoIconDefault)
                    }
                    Button(
                        onClick = {
                            val updates = editedTitles.map { (id, title) ->
                                ChapterUpdate(id, title)
                            }
                            onSaveChapters(updates)
                            isEditMode = false
                        },
                        enabled = !isBusy,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SapphoInfo
                        )
                    ) {
                        if (isSavingChapters) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text("Save")
                    }
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text("Close", color = SapphoInfo)
                }
            }
        },
        containerColor = SapphoSurfaceLight
    )
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
                                Surface(
                                    onClick = { onToggleCollection(collection.id) },
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
