package com.sappho.audiobooks.presentation.player

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.sappho.audiobooks.presentation.theme.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import dagger.hilt.android.AndroidEntryPoint
import com.sappho.audiobooks.service.AudioPlaybackService
import com.sappho.audiobooks.cast.CastHelper
import com.sappho.audiobooks.cast.CastManager
import com.sappho.audiobooks.cast.ui.CastDialog
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class PlayerActivity : ComponentActivity() {

    @Inject
    lateinit var playerState: com.sappho.audiobooks.service.PlayerState

    @Inject
    lateinit var castHelper: CastHelper

    @Inject
    lateinit var castManager: CastManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val audiobookId = intent.getIntExtra("AUDIOBOOK_ID", -1)
        val startPosition = intent.getIntExtra("START_POSITION", 0)
        val fromMinimized = intent.getBooleanExtra("FROM_MINIMIZED", false)

        if (audiobookId == -1) {
            finish()
            return
        }

        setContent {
            PlayerScreen(
                audiobookId = audiobookId,
                startPosition = startPosition,
                fromMinimized = fromMinimized,
                onMinimize = { finishWithNoAnimation() },
                castHelper = castHelper,
                castManager = castManager
            )
        }
    }


    private fun finishWithNoAnimation() {
        finish()
        // Disable the default activity transition animation
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    audiobookId: Int,
    startPosition: Int,
    fromMinimized: Boolean,
    onMinimize: () -> Unit,
    @Suppress("UNUSED_PARAMETER") castHelper: CastHelper, // Kept for API compatibility; CastManager wraps it
    castManager: CastManager,
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val audiobook by viewModel.audiobook.collectAsState()
    val chapters by viewModel.chapters.collectAsState()
    val playerState by viewModel.playerState.collectAsState()
    val serverUrl by viewModel.serverUrl.collectAsState()
    var showChapters by remember { mutableStateOf(false) }
    var showPlaybackSpeed by remember { mutableStateOf(false) }
    var showSleepTimer by remember { mutableStateOf(false) }
    val castCoroutineScope = rememberCoroutineScope()

    // Load audiobook details if not already loaded
    LaunchedEffect(audiobookId) {
        if (!fromMinimized) {
            viewModel.loadAndStartPlayback(audiobookId, startPosition)
        } else {
            viewModel.loadAudiobookDetails(audiobookId)
        }
        viewModel.loadChapters(audiobookId)
    }

    // Determine playing state based on whether we're casting or using local playback
    val localIsPlaying = playerState?.isPlaying?.collectAsState()?.value ?: false
    val castIsPlaying = castManager.isPlaying.collectAsState().value
    val isCastConnected = castManager.isConnected.collectAsState().value
    val castError by castManager.castError.collectAsState()
    val isPlaying = if (isCastConnected) {
        castIsPlaying
    } else {
        localIsPlaying
    }
    val localPosition = playerState?.currentPosition?.collectAsState()?.value ?: 0L
    val duration = playerState?.duration?.collectAsState()?.value ?: 0L
    val isLoading = playerState?.isLoading?.collectAsState()?.value ?: false
    val playbackSpeed = playerState?.playbackSpeed?.collectAsState()?.value ?: 1.0f
    val sleepTimerRemaining = playerState?.sleepTimerRemaining?.collectAsState()?.value

    // When casting, poll the Cast position periodically
    var castPosition by remember { mutableLongStateOf(0L) }
    LaunchedEffect(isCastConnected) {
        if (isCastConnected) {
            while (true) {
                castPosition = castManager.getCurrentPosition()
                kotlinx.coroutines.delay(Timing.POLL_INTERVAL_MS)
            }
        }
    }

    // Use Cast position when casting, local position otherwise
    val currentPosition = if (isCastConnected) castPosition else localPosition

    // Find current chapter based on position
    val currentChapter = remember(chapters, currentPosition) {
        chapters.findLast { chapter ->
            chapter.startTime <= currentPosition.toDouble()
        }
    }

    var dragOffset by remember { mutableFloatStateOf(0f) }
    var isMinimizing by remember { mutableStateOf(false) }

    // Animate the offset for smooth slide-down
    val animatedOffset by animateFloatAsState(
        targetValue = if (isMinimizing) 2000f else dragOffset,
        animationSpec = tween(durationMillis = if (isMinimizing) 300 else 0),
        finishedListener = { if (isMinimizing) onMinimize() },
        label = "slideDown"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SapphoBackground)
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        if (dragOffset > 150) {
                            isMinimizing = true
                        } else {
                            dragOffset = 0f
                        }
                    },
                    onVerticalDrag = { _, dragAmount ->
                        if (dragAmount > 0) {
                            dragOffset += dragAmount
                        } else if (dragOffset > 0) {
                            dragOffset = (dragOffset + dragAmount).coerceAtLeast(0f)
                        }
                    }
                )
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .offset(y = animatedOffset.dp)
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Top bar with minimize and cast
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onMinimize) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = SapphoAccessibility.ContentDescriptions.MINIMIZE,
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }

                // Cast button - shows unified cast dialog
                var showCastDialog by remember { mutableStateOf(false) }
                IconButton(onClick = { showCastDialog = true }) {
                    Icon(
                        imageVector = Icons.Default.Cast,
                        contentDescription = if (isCastConnected) SapphoAccessibility.ContentDescriptions.CAST_CONNECTED else SapphoAccessibility.ContentDescriptions.CAST,
                        tint = if (isCastConnected) SapphoInfo else SapphoIconDefault,
                        modifier = Modifier.size(24.dp)
                    )
                }

                if (showCastDialog) {
                    CastDialog(
                        castManager = castManager,
                        onDeviceSelected = { device ->
                            // Stop local playback
                            if (localIsPlaying) {
                                AudioPlaybackService.instance?.togglePlayPause()
                            }

                            castCoroutineScope.launch {
                                // For Chromecast, use the existing route selection flow
                                if (device.protocol == com.sappho.audiobooks.cast.CastProtocol.CHROMECAST) {
                                    val route = device.extras as? androidx.mediarouter.media.MediaRouter.RouteInfo
                                    if (route != null) {
                                        // Queue audiobook on CastHelper (handles auth + pending media)
                                        audiobook?.let { book ->
                                            serverUrl?.let { url ->
                                                castManager.getChromecastTarget().castAudiobook(
                                                    audiobook = book,
                                                    streamUrl = "$url/api/audiobooks/${book.id}/stream",
                                                    coverUrl = if (book.coverImage != null) com.sappho.audiobooks.util.buildCoverUrl(url, book.id) else null,
                                                    currentPosition = currentPosition
                                                )
                                            }
                                        }
                                        castManager.getChromecastTarget().selectRoute(context, route)
                                    }
                                } else {
                                    // For other protocols, connect then cast
                                    castManager.connectToDevice(device)
                                    audiobook?.let { book ->
                                        serverUrl?.let { url ->
                                            castManager.castAudiobook(
                                                audiobook = book,
                                                streamUrl = "$url/api/audiobooks/${book.id}/stream",
                                                coverUrl = if (book.coverImage != null) com.sappho.audiobooks.util.buildCoverUrl(url, book.id) else null,
                                                positionSeconds = currentPosition
                                            )
                                        }
                                    }
                                }
                            }
                            showCastDialog = false
                        },
                        onDisconnect = {
                            castCoroutineScope.launch {
                                castManager.disconnect()
                            }
                            showCastDialog = false
                        },
                        onDismiss = { showCastDialog = false }
                    )
                }

                // Cast error dialog
                castError?.let { error ->
                    AlertDialog(
                        onDismissRequest = { castManager.clearError() },
                        title = {
                            Text(
                                "Cast Failed",
                                color = Color.White,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                            )
                        },
                        text = {
                            Text(
                                error,
                                color = SapphoIconDefault
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = { castManager.clearError() }) {
                                Text("OK", color = SapphoInfo)
                            }
                        },
                        containerColor = SapphoSurfaceLight,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
                    )
                }
            }

            audiobook?.let { book ->
                val isLandscapeMode = isLandscape()
                // Smaller cover and scrollable content in landscape
                val coverFraction = if (isLandscapeMode) 0.35f else 0.75f
                val coverSpacing = if (isLandscapeMode) 16.dp else 32.dp

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (isLandscapeMode) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(if (isLandscapeMode) 8.dp else 16.dp))

                    // Cover art - smaller in landscape
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(coverFraction)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(SapphoSurfaceLight)
                    ) {
                        if (book.coverImage != null && serverUrl != null) {
                            AsyncImage(
                                model = com.sappho.audiobooks.util.buildCoverUrl(serverUrl!!, book.id, com.sappho.audiobooks.util.COVER_WIDTH_DETAIL),
                                contentDescription = book.title,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        } else {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = book.title.take(2).uppercase(),
                                    style = MaterialTheme.typography.displayLarge,
                                    color = SapphoInfo
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(coverSpacing))

                    // Title
                    Text(
                        text = book.title,
                        style = if (isLandscapeMode) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineMedium,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Author - clickable link
                    book.author?.let { author ->
                        Text(
                            text = author,
                            style = MaterialTheme.typography.titleSmall,
                            color = LegacyBlueLight,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.clickable {
                                // Navigate to library with author filter
                                val intent = Intent(context, com.sappho.audiobooks.presentation.MainActivity::class.java)
                                intent.putExtra("NAVIGATE_TO", "library")
                                intent.putExtra("AUTHOR", author)
                                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                                context.startActivity(intent)
                            }
                        )
                    }

                    // Series - clickable link
                    book.series?.let { series ->
                        Spacer(modifier = Modifier.height(2.dp))
                        val seriesText = if (book.seriesPosition != null) {
                            "$series #${formatSeriesPosition(book.seriesPosition)}"
                        } else {
                            series
                        }
                        Text(
                            text = seriesText,
                            style = MaterialTheme.typography.bodySmall,
                            color = LegacyBlueLight.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.clickable {
                                // Navigate to library with series filter
                                val intent = Intent(context, com.sappho.audiobooks.presentation.MainActivity::class.java)
                                intent.putExtra("NAVIGATE_TO", "library")
                                intent.putExtra("SERIES", series)
                                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                                context.startActivity(intent)
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    // Main playback controls with animations
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Previous Chapter
                        val prevChapterSource = remember { MutableInteractionSource() }
                        val isPrevChapterPressed by prevChapterSource.collectIsPressedAsState()
                        val prevChapterScale by animateFloatAsState(
                            targetValue = if (isPrevChapterPressed) 0.85f else 1f,
                            animationSpec = tween(100),
                            label = "prevChapter"
                        )

                        IconButton(
                            onClick = {
                                // Jump to previous chapter
                                val currentIdx = chapters.indexOfFirst { it == currentChapter }
                                if (currentIdx > 0) {
                                    AudioPlaybackService.instance?.seekTo(chapters[currentIdx - 1].startTime.toLong())
                                }
                            },
                            modifier = Modifier.size(48.dp),
                            interactionSource = prevChapterSource,
                            enabled = chapters.isNotEmpty()
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipPrevious,
                                contentDescription = SapphoAccessibility.ContentDescriptions.PREVIOUS_CHAPTER,
                                tint = if (chapters.isEmpty()) LegacyGrayDark else Color.White,
                                modifier = Modifier
                                    .size(32.dp)
                                    .scale(prevChapterScale)
                            )
                        }

                        // Skip Backward 10s
                        val skipBackSource = remember { MutableInteractionSource() }
                        val isSkipBackPressed by skipBackSource.collectIsPressedAsState()
                        val skipBackScale by animateFloatAsState(
                            targetValue = if (isSkipBackPressed) 0.85f else 1f,
                            animationSpec = tween(100),
                            label = "skipBack"
                        )

                        IconButton(
                            onClick = { AudioPlaybackService.instance?.skipBackward() },
                            modifier = Modifier.size(56.dp),
                            interactionSource = skipBackSource
                        ) {
                            Icon(
                                imageVector = Icons.Default.Replay10,
                                contentDescription = SapphoAccessibility.ContentDescriptions.SKIP_BACKWARD,
                                tint = Color.White,
                                modifier = Modifier
                                    .size(36.dp)
                                    .scale(skipBackScale)
                            )
                        }

                        // Play/Pause Button
                        val playSource = remember { MutableInteractionSource() }
                        val isPlayPressed by playSource.collectIsPressedAsState()
                        val playScale by animateFloatAsState(
                            targetValue = if (isPlayPressed) 0.9f else 1f,
                            animationSpec = tween(100),
                            label = "play"
                        )

                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .scale(playScale)
                                .clip(CircleShape)
                                .background(SapphoInfo)
                                .clickable(
                                    interactionSource = playSource,
                                    indication = null
                                ) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    // Check if we're currently casting
                                    if (isCastConnected) {
                                        // Control the cast device via CastManager
                                        castCoroutineScope.launch {
                                            if (isPlaying) {
                                                castManager.pause()
                                            } else {
                                                castManager.play()
                                            }
                                        }
                                    } else {
                                        // Control local playback
                                        val service = AudioPlaybackService.instance
                                        val playerHandled = service?.togglePlayPause() ?: false
                                        if (!playerHandled) {
                                            // Service was killed or player is null (e.g., after vehicle disconnect)
                                            // Restart playback from current position
                                            viewModel.loadAndStartPlayback(audiobookId, currentPosition.toInt())
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    modifier = Modifier.size(32.dp)
                                )
                            } else {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlaying) SapphoAccessibility.ContentDescriptions.PAUSE_BUTTON else SapphoAccessibility.ContentDescriptions.PLAY_BUTTON,
                                    tint = Color.White,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }

                        // Skip Forward 10s
                        val skipForwardSource = remember { MutableInteractionSource() }
                        val isSkipForwardPressed by skipForwardSource.collectIsPressedAsState()
                        val skipForwardScale by animateFloatAsState(
                            targetValue = if (isSkipForwardPressed) 0.85f else 1f,
                            animationSpec = tween(100),
                            label = "skipForward"
                        )

                        IconButton(
                            onClick = { AudioPlaybackService.instance?.skipForward() },
                            modifier = Modifier.size(56.dp),
                            interactionSource = skipForwardSource
                        ) {
                            Icon(
                                imageVector = Icons.Default.Forward10,
                                contentDescription = SapphoAccessibility.ContentDescriptions.SKIP_FORWARD,
                                tint = Color.White,
                                modifier = Modifier
                                    .size(36.dp)
                                    .scale(skipForwardScale)
                            )
                        }

                        // Next Chapter
                        val nextChapterSource = remember { MutableInteractionSource() }
                        val isNextChapterPressed by nextChapterSource.collectIsPressedAsState()
                        val nextChapterScale by animateFloatAsState(
                            targetValue = if (isNextChapterPressed) 0.85f else 1f,
                            animationSpec = tween(100),
                            label = "nextChapter"
                        )

                        IconButton(
                            onClick = {
                                // Jump to next chapter
                                val currentIdx = chapters.indexOfFirst { it == currentChapter }
                                if (currentIdx >= 0 && currentIdx < chapters.size - 1) {
                                    AudioPlaybackService.instance?.seekTo(chapters[currentIdx + 1].startTime.toLong())
                                }
                            },
                            modifier = Modifier.size(48.dp),
                            interactionSource = nextChapterSource,
                            enabled = chapters.isNotEmpty()
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = SapphoAccessibility.ContentDescriptions.NEXT_CHAPTER,
                                tint = if (chapters.isEmpty()) LegacyGrayDark else Color.White,
                                modifier = Modifier
                                    .size(32.dp)
                                    .scale(nextChapterScale)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Progress slider - MOVED BELOW BUTTONS
                    // Use local state for smooth dragging, only seek on release
                    var isDragging by remember { mutableStateOf(false) }
                    var dragPosition by remember { mutableFloatStateOf(0f) }
                    var wasPlayingBeforeDrag by remember { mutableStateOf(false) }

                    // The displayed position: use drag position while dragging, actual position otherwise
                    val displayedPosition = if (isDragging) dragPosition else currentPosition.toFloat()

                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Time popup while dragging
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isDragging) {
                                // Show popup with seek time while dragging
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(SapphoSurfaceLight)
                                        .padding(horizontal = 16.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = formatTime(dragPosition.toLong()),
                                        style = MaterialTheme.typography.titleLarge,
                                        color = SapphoInfo
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = formatTime(displayedPosition.toLong()),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isDragging) SapphoInfo else SapphoIconDefault
                            )
                            Text(
                                text = formatTime(duration),
                                style = MaterialTheme.typography.bodySmall,
                                color = SapphoIconDefault
                            )
                        }

                        Slider(
                            value = if (duration > 0) displayedPosition else 0f,
                            onValueChange = { newValue ->
                                if (!isDragging) {
                                    // Capture playing state when drag starts
                                    wasPlayingBeforeDrag = isPlaying
                                }
                                isDragging = true
                                dragPosition = newValue
                            },
                            onValueChangeFinished = {
                                // Seek to position, only resume playback if it was playing before
                                if (wasPlayingBeforeDrag) {
                                    AudioPlaybackService.instance?.seekToAndPlay(dragPosition.toLong())
                                } else {
                                    AudioPlaybackService.instance?.seekTo(dragPosition.toLong())
                                }
                                isDragging = false
                            },
                            valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
                            thumb = {
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .background(SapphoInfo)
                                )
                            },
                            track = { sliderState ->
                                val fraction = if (sliderState.valueRange.endInclusive > sliderState.valueRange.start) {
                                    (sliderState.value - sliderState.valueRange.start) /
                                    (sliderState.valueRange.endInclusive - sliderState.valueRange.start)
                                } else 0f
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(SapphoProgressTrack)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(fraction)
                                            .height(4.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(SapphoInfo)
                                    )
                                }
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Modern control buttons row - Chapters, Speed, Sleep Timer
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Chapters button
                        val chaptersInteractionSource = remember { MutableInteractionSource() }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clickable(
                                    interactionSource = chaptersInteractionSource,
                                    indication = null
                                ) { showChapters = !showChapters }
                                .padding(vertical = 12.dp, horizontal = 12.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.List,
                                    contentDescription = SapphoAccessibility.ContentDescriptions.CHAPTERS,
                                    modifier = Modifier.size(24.dp),
                                    tint = LegacyBlueLight
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = currentChapter?.title ?: "—",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // Speed button
                        val speedInteractionSource = remember { MutableInteractionSource() }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clickable(
                                    interactionSource = speedInteractionSource,
                                    indication = null
                                ) { showPlaybackSpeed = !showPlaybackSpeed }
                                .padding(vertical = 12.dp, horizontal = 12.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Speed,
                                    contentDescription = SapphoAccessibility.ContentDescriptions.PLAYBACK_SPEED,
                                    modifier = Modifier.size(24.dp),
                                    tint = LegacyPurpleLight
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "${playbackSpeed}x",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White
                                )
                            }
                        }

                        // Sleep Timer button
                        val hasSleepTimer = sleepTimerRemaining != null && sleepTimerRemaining > 0
                        val sleepInteractionSource = remember { MutableInteractionSource() }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clickable(
                                    interactionSource = sleepInteractionSource,
                                    indication = null
                                ) { showSleepTimer = !showSleepTimer }
                                .padding(vertical = 12.dp, horizontal = 12.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Bedtime,
                                    contentDescription = SapphoAccessibility.ContentDescriptions.SLEEP_TIMER,
                                    modifier = Modifier.size(24.dp),
                                    tint = if (hasSleepTimer) SapphoStarFilled else SapphoWarning
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = if (hasSleepTimer && sleepTimerRemaining != null) {
                                        val mins = sleepTimerRemaining / 60
                                        val secs = sleepTimerRemaining % 60
                                        "${mins}:${secs.toString().padStart(2, '0')}"
                                    } else "Off",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (hasSleepTimer) SapphoStarFilled else Color.White
                                )
                            }
                        }
                    }

                    // Playing animation centered in remaining vertical space
                    if (isPlaying) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            PlayingAnimation()
                        }
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }

        // Chapters Dialog
        if (showChapters && chapters.isNotEmpty()) {
            val listState = androidx.compose.foundation.lazy.rememberLazyListState()
            val currentChapterIndex = chapters.indexOfFirst { it == currentChapter }

            // Scroll to current chapter when dialog opens
            LaunchedEffect(showChapters) {
                if (currentChapterIndex >= 0) {
                    listState.scrollToItem(currentChapterIndex)
                }
            }

            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showChapters = false },
                title = { Text("Chapters", color = Color.White) },
                text = {
                    androidx.compose.foundation.lazy.LazyColumn(
                        state = listState,
                        modifier = Modifier.heightIn(max = 400.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(chapters.size) { index ->
                            val chapter = chapters[index]
                            val isCurrentChapter = chapter == currentChapter
                            androidx.compose.material3.TextButton(
                                onClick = {
                                    AudioPlaybackService.instance?.seekToAndPlay(chapter.startTime.toLong())
                                    showChapters = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (isCurrentChapter) {
                                        // Use marquee for current chapter
                                        MarqueeText(
                                            text = chapter.title ?: "Chapter ${index + 1}",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = SapphoInfo,
                                            modifier = Modifier.weight(1f).padding(end = 8.dp)
                                        )
                                    } else {
                                        // Use ellipsis for other chapters
                                        Text(
                                            text = chapter.title ?: "Chapter ${index + 1}",
                                            color = Color.White,
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f).padding(end = 8.dp)
                                        )
                                    }
                                    Text(
                                        text = formatTime(chapter.startTime.toLong()),
                                        color = SapphoIconDefault,
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showChapters = false }) {
                        Text("Close", color = SapphoInfo)
                    }
                },
                containerColor = SapphoSurfaceLight
            )
        }

        // Playback Speed Dialog
        if (showPlaybackSpeed) {
            val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showPlaybackSpeed = false },
                title = { Text("Playback Speed", color = Color.White) },
                text = {
                    Column {
                        speeds.forEach { speed ->
                            androidx.compose.material3.TextButton(
                                onClick = {
                                    AudioPlaybackService.instance?.setPlaybackSpeed(speed)
                                    showPlaybackSpeed = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "${speed}x",
                                    color = if (speed == playbackSpeed) SapphoInfo else Color.White,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showPlaybackSpeed = false }) {
                        Text("Close", color = SapphoInfo)
                    }
                },
                containerColor = SapphoSurfaceLight
            )
        }

        // Sleep Timer Dialog
        if (showSleepTimer) {
            val timerOptions = listOf(
                0 to "Off",
                5 to "5 minutes",
                10 to "10 minutes",
                15 to "15 minutes",
                30 to "30 minutes",
                45 to "45 minutes",
                60 to "1 hour",
                90 to "1.5 hours",
                120 to "2 hours"
            )
            val currentTimerActive = sleepTimerRemaining != null && sleepTimerRemaining > 0

            AlertDialog(
                onDismissRequest = { showSleepTimer = false },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bedtime,
                            contentDescription = null,
                            tint = SapphoStarFilled,
                            modifier = Modifier.size(24.dp)
                        )
                        Text("Sleep Timer", color = Color.White)
                    }
                },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (currentTimerActive) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp),
                                shape = RoundedCornerShape(12.dp),
                                color = SapphoWarning.copy(alpha = 0.15f)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            "Timer active",
                                            color = SapphoStarFilled,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        val mins = (sleepTimerRemaining ?: 0) / 60
                                        val secs = (sleepTimerRemaining ?: 0) % 60
                                        Text(
                                            "${mins}:${secs.toString().padStart(2, '0')} remaining",
                                            color = SapphoStarFilled.copy(alpha = 0.8f),
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                    TextButton(
                                        onClick = {
                                            AudioPlaybackService.instance?.cancelSleepTimer()
                                        }
                                    ) {
                                        Text("Cancel", color = SapphoError)
                                    }
                                }
                            }
                        }

                        timerOptions.forEach { (minutes, label) ->
                            TextButton(
                                onClick = {
                                    AudioPlaybackService.instance?.setSleepTimer(minutes)
                                    showSleepTimer = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = label,
                                        color = Color.White
                                    )
                                    if (minutes == 0 && !currentTimerActive) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = SapphoSuccess,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSleepTimer = false }) {
                        Text("Close", color = SapphoInfo)
                    }
                },
                containerColor = SapphoSurfaceLight
            )
        }

    }
}

@Composable
fun PlayingAnimation() {
    val infiniteTransition = rememberInfiniteTransition(label = "playing")

    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        repeat(3) { index ->
            val animatedHeight by infiniteTransition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = 400 + (index * 100),
                        easing = androidx.compose.animation.core.FastOutSlowInEasing
                    ),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "bar$index"
            )

            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height((12 * animatedHeight).dp)
                    .background(
                        color = SapphoInfo,
                        shape = RoundedCornerShape(2.dp)
                    )
            )
        }
    }
}

private fun formatTime(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    return if (hours > 0) {
        String.format(java.util.Locale.US, "%d:%02d:%02d", hours, minutes, secs)
    } else {
        String.format(java.util.Locale.US, "%d:%02d", minutes, secs)
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
