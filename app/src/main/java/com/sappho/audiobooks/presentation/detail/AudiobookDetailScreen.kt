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
    var showRatingPicker by remember { mutableStateOf(false) }

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
                // Adaptive layout for tablets
                val screenSize = rememberScreenSize()
                val horizontalPadding = when (screenSize) {
                    ScreenSize.COMPACT -> 0.dp
                    ScreenSize.MEDIUM -> 48.dp
                    ScreenSize.EXPANDED -> 120.dp
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 80.dp)
                        .padding(horizontal = horizontalPadding)
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
                                contentDescription = SapphoAccessibility.ContentDescriptions.OFFLINE,
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
                                    contentDescription = "Download failed",
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
                        // Close picker on back press
                        BackHandler(enabled = showRatingPicker) {
                            showRatingPicker = false
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Main row: Average rating + Rate button
                            Row(
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Show average rating
                                averageRating?.let { avg ->
                                    if (avg.count > 0) {
                                        Icon(
                                            imageVector = Icons.Filled.Star,
                                            contentDescription = null,
                                            tint = SapphoStarFilled,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = String.format(java.util.Locale.US, "%.1f", avg.average ?: 0f),
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = SapphoText
                                        )
                                        Text(
                                            text = " (${avg.count})",
                                            fontSize = 13.sp,
                                            color = SapphoTextMuted
                                        )
                                        Spacer(modifier = Modifier.width(16.dp))
                                    }
                                }

                                // Rate button - filled star if rated, outline with "Rate" if not
                                Box(
                                    modifier = Modifier
                                        .border(
                                            width = 1.dp,
                                            color = if (userRating != null) SapphoStarFilled.copy(alpha = 0.5f) else SapphoTextMuted.copy(alpha = 0.3f),
                                            shape = RoundedCornerShape(16.dp)
                                        )
                                        .clip(RoundedCornerShape(16.dp))
                                        .clickable { showRatingPicker = !showRatingPicker }
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isUpdatingRating) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(14.dp),
                                            color = SapphoStarFilled,
                                            strokeWidth = 1.5.dp
                                        )
                                    } else if (userRating != null) {
                                        // Rated - just show filled star
                                        Icon(
                                            imageVector = Icons.Filled.Star,
                                            contentDescription = "Rated",
                                            tint = SapphoStarFilled,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    } else {
                                        // Not rated - show outline star + "Rate" text
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Filled.StarBorder,
                                                contentDescription = null,
                                                tint = SapphoTextMuted,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "Rate",
                                                fontSize = 13.sp,
                                                color = SapphoTextMuted
                                            )
                                        }
                                    }
                                }
                            }

                            // Expandable star picker with animation
                            SapphoAnimatedVisibility(
                                visible = showRatingPicker,
                                enter = SapphoAnimations.normalFadeIn + SapphoAnimations.scaleInAnimation,
                                exit = SapphoAnimations.fastFadeOut + SapphoAnimations.scaleOutAnimation
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        for (starIndex in 1..5) {
                                            val isSelected = userRating != null && starIndex <= userRating!!

                                            Icon(
                                                imageVector = if (isSelected) Icons.Filled.Star else Icons.Filled.StarBorder,
                                                contentDescription = "Rate $starIndex stars",
                                                tint = if (isSelected) SapphoStarFilled else SapphoTextMuted.copy(alpha = 0.4f),
                                                modifier = Modifier
                                                    .size(32.dp)
                                                    .bouncyClickable(enabled = !isUpdatingRating) {
                                                        if (userRating == starIndex) {
                                                            viewModel.clearRating()
                                                        } else {
                                                            viewModel.setRating(starIndex)
                                                            showRatingPicker = false
                                                        }
                                                    }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Play/Pause Button with download and overflow menu
                    // Disable button until progress is confirmed (unless book is already loaded in service)
                    val canPlay = isThisBookLoaded || !isProgressLoading
                    val playButtonHaptic = HapticPatterns.playButtonPress()
                    val progressCheck = progress
                    val hasProgress = progressCheck != null && (progressCheck.position > 0 || progressCheck.completed == 1)
                    val hasChapters = book.isMultiFile == 1 && chapters.isNotEmpty()
                    val showDownloadButton = isDownloading || isDownloaded || !isOffline

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

                        // Download button (icon only)
                        if (showDownloadButton) {
                            val downloadStartHaptic = HapticPatterns.downloadStart()
                            val downloadCancelHaptic = HapticPatterns.downloadCancel()
                            IconButton(
                                onClick = {
                                    when {
                                        isDownloading -> {
                                            downloadCancelHaptic()
                                            DownloadService.cancelDownload(context)
                                        }
                                        isDownloaded -> {
                                            downloadCancelHaptic()
                                            if (!isOffline) showDeleteDownloadDialog = true
                                        }
                                        hasDownloadError -> {
                                            downloadStartHaptic()
                                            viewModel.downloadAudiobook()
                                        }
                                        else -> {
                                            downloadStartHaptic()
                                            viewModel.downloadAudiobook()
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(
                                        color = when {
                                            hasDownloadError -> SapphoError.copy(alpha = 0.15f)
                                            else -> SapphoSurfaceLight.copy(alpha = 0.5f)
                                        },
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = Color.White.copy(alpha = 0.1f),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                            ) {
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
                                                modifier = Modifier.size(20.dp),
                                                color = SapphoInfo,
                                                strokeWidth = 2.dp
                                            )
                                        }
                                        "downloaded" -> {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Downloaded",
                                                modifier = Modifier.size(22.dp),
                                                tint = SapphoSuccess
                                            )
                                        }
                                        "error" -> {
                                            Icon(
                                                imageVector = Icons.Default.Refresh,
                                                contentDescription = "Retry download",
                                                modifier = Modifier.size(22.dp),
                                                tint = SapphoError
                                            )
                                        }
                                        else -> {
                                            Icon(
                                                imageVector = Icons.Default.Download,
                                                contentDescription = "Download",
                                                modifier = Modifier.size(22.dp),
                                                tint = SapphoIconDefault
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Overflow menu button (only show when online)
                        if (!isOffline) {
                            Box {
                                IconButton(
                                    onClick = { showOverflowMenu = true },
                                    modifier = Modifier
                                        .size(48.dp)
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
                                        modifier = Modifier.size(22.dp)
                                    )
                                }

                                DropdownMenu(
                                    expanded = showOverflowMenu,
                                    onDismissRequest = { showOverflowMenu = false },
                                    modifier = Modifier.background(SapphoSurface)
                                ) {
                                    // Chapters (only for multi-file books)
                                    if (hasChapters) {
                                        DropdownMenuItem(
                                            text = { Text("${chapters.size} Chapters", color = SapphoText) },
                                            onClick = {
                                                showOverflowMenu = false
                                                showChaptersDialog = true
                                            },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Filled.List,
                                                    contentDescription = null,
                                                    tint = SapphoInfo
                                                )
                                            }
                                        )
                                    }

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

                            // Description with animated expand/collapse
                            val isLongDescription = description.length > 200

                            Box {
                                Text(
                                    text = description,
                                    fontSize = 16.sp,
                                    color = SapphoTextLight,
                                    lineHeight = 28.8.sp,
                                    maxLines = if (isDescriptionExpanded || !isLongDescription) Int.MAX_VALUE else 4,
                                    overflow = TextOverflow.Clip
                                )

                                // Gradient fade overlay when collapsed
                                if (!isDescriptionExpanded && isLongDescription) {
                                    Box(
                                        modifier = Modifier
                                            .matchParentSize()
                                            .background(
                                                Brush.verticalGradient(
                                                    colors = listOf(
                                                        Color.Transparent,
                                                        Color.Transparent,
                                                        SapphoBackground.copy(alpha = 0.8f),
                                                        SapphoBackground
                                                    ),
                                                    startY = 0f,
                                                    endY = Float.POSITIVE_INFINITY
                                                )
                                            )
                                    )
                                }
                            }

                            // Show more/less button
                            if (isLongDescription) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { isDescriptionExpanded = !isDescriptionExpanded }
                                        .padding(vertical = 4.dp, horizontal = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (isDescriptionExpanded) "Show less" else "Show more",
                                        fontSize = 14.sp,
                                        color = SapphoInfo,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = if (isDescriptionExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                        contentDescription = null,
                                        tint = SapphoInfo,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
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

