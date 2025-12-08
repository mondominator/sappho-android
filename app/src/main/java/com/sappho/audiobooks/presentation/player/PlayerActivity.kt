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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.platform.LocalContext
import javax.inject.Inject

@AndroidEntryPoint
class PlayerActivity : ComponentActivity() {

    @Inject
    lateinit var playerState: com.sappho.audiobooks.service.PlayerState

    @Inject
    lateinit var castHelper: CastHelper

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
                castHelper = castHelper
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
    castHelper: CastHelper,
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val audiobook by viewModel.audiobook.collectAsState()
    val chapters by viewModel.chapters.collectAsState()
    val playerState by viewModel.playerState.collectAsState()
    val serverUrl by viewModel.serverUrl.collectAsState()
    var showChapters by remember { mutableStateOf(false) }
    var showPlaybackSpeed by remember { mutableStateOf(false) }
    var showSleepTimer by remember { mutableStateOf(false) }

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
    val castIsPlaying = castHelper.isPlayingFlow.collectAsState().value
    val isCastConnected = castHelper.isConnected.collectAsState().value
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
    var castPosition by remember { mutableStateOf(0L) }
    LaunchedEffect(isCastConnected) {
        if (isCastConnected) {
            while (true) {
                castPosition = castHelper.getCurrentPosition()
                kotlinx.coroutines.delay(1000) // Update every second
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

    var dragOffset by remember { mutableStateOf(0f) }
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
            .background(Color(0xFF0A0E1A))
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
                        contentDescription = "Minimize",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }

                // Cast button - shows our custom dialog
                var showCastDialog by remember { mutableStateOf(false) }
                IconButton(onClick = { showCastDialog = true }) {
                    Icon(
                        imageVector = Icons.Default.Cast,
                        contentDescription = "Cast",
                        tint = if (isCastConnected) Color(0xFF3b82f6) else Color(0xFF9ca3af),
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Cast Dialog
                if (showCastDialog) {
                    var isScanning by remember { mutableStateOf(true) }
                    val availableRoutes by castHelper.availableRoutes.collectAsState()

                    LaunchedEffect(Unit) {
                        castHelper.startDiscovery(context)
                        kotlinx.coroutines.delay(2000)
                        isScanning = false
                    }

                    AlertDialog(
                        onDismissRequest = { showCastDialog = false },
                        title = { Text("Cast to Device", color = Color.White) },
                        text = {
                            Column {
                                if (isCastConnected) {
                                    Text(
                                        "Currently casting",
                                        color = Color(0xFF10b981),
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    )
                                    TextButton(
                                        onClick = {
                                            castHelper.disconnectCast()
                                            showCastDialog = false
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Disconnect", color = Color(0xFFef4444))
                                    }
                                } else if (isScanning) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            color = Color(0xFF3b82f6),
                                            strokeWidth = 2.dp
                                        )
                                        Text("Scanning for Cast devices...", color = Color(0xFF9ca3af))
                                    }
                                } else if (availableRoutes.isEmpty()) {
                                    Text("No Cast devices found.", color = Color(0xFF9ca3af))
                                } else {
                                    Text("Select a device:", color = Color(0xFF9ca3af), modifier = Modifier.padding(bottom = 8.dp))
                                    availableRoutes.forEachIndexed { index, route ->
                                        TextButton(
                                            onClick = {
                                                android.util.Log.d("PlayerActivity", "User clicked index=$index, name=${route.name}, id=${route.id}")

                                                // Stop local playback
                                                if (localIsPlaying) {
                                                    AudioPlaybackService.instance?.togglePlayPause()
                                                }

                                                // Queue audiobook
                                                audiobook?.let { book ->
                                                    serverUrl?.let { url ->
                                                        castHelper.castAudiobook(
                                                            audiobook = book,
                                                            streamUrl = "$url/api/audiobooks/${book.id}/stream",
                                                            coverUrl = if (book.coverImage != null) "$url/api/audiobooks/${book.id}/cover" else null,
                                                            currentPosition = currentPosition
                                                        )
                                                    }
                                                }

                                                // Select route via CastHelper for better device selection
                                                android.util.Log.d("PlayerActivity", "Calling castHelper.selectRoute() for: ${route.name}")
                                                castHelper.selectRoute(context, route)
                                                showCastDialog = false
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(route.name, color = Color.White)
                                        }
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { showCastDialog = false }) {
                                Text("Cancel", color = Color(0xFF3b82f6))
                            }
                        },
                        containerColor = Color(0xFF1e293b)
                    )
                }
            }

            audiobook?.let { book ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(16.dp))

                    // Cover art
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.75f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF1e293b))
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
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = book.title.take(2).uppercase(),
                                    fontSize = 48.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF3b82f6)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    // Title
                    Text(
                        text = book.title,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
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
                            fontSize = 15.sp,
                            color = Color(0xFF60a5fa),
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
                            fontSize = 13.sp,
                            color = Color(0xFF60a5fa).copy(alpha = 0.7f),
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
                                contentDescription = "Previous chapter",
                                tint = if (chapters.isEmpty()) Color(0xFF4b5563) else Color.White,
                                modifier = Modifier
                                    .size(32.dp)
                                    .scale(prevChapterScale)
                            )
                        }

                        // Skip Backward 15s
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
                                contentDescription = "Skip backward 15s",
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
                                .background(Color(0xFF3b82f6))
                                .clickable(
                                    interactionSource = playSource,
                                    indication = null
                                ) {
                                    // Check if we're currently casting
                                    android.util.Log.d("PlayerActivity", "Play/Pause tapped: isCastConnected=$isCastConnected, isPlaying=$isPlaying, castIsPlaying=$castIsPlaying, localIsPlaying=$localIsPlaying")
                                    if (isCastConnected) {
                                        // Control the Cast receiver
                                        if (isPlaying) {
                                            android.util.Log.d("PlayerActivity", "Calling castHelper.pause()")
                                            castHelper.pause()
                                        } else {
                                            android.util.Log.d("PlayerActivity", "Calling castHelper.play()")
                                            castHelper.play()
                                        }
                                    } else {
                                        // Control local playback
                                        AudioPlaybackService.instance?.togglePlayPause()
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
                                    contentDescription = if (isPlaying) "Pause" else "Play",
                                    tint = Color.White,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }

                        // Skip Forward 15s
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
                                contentDescription = "Skip forward 15s",
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
                                contentDescription = "Next chapter",
                                tint = if (chapters.isEmpty()) Color(0xFF4b5563) else Color.White,
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
                    var dragPosition by remember { mutableStateOf(0f) }
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
                                        .background(Color(0xFF1e293b))
                                        .padding(horizontal = 16.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = formatTime(dragPosition.toLong()),
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF3b82f6)
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
                                fontSize = 13.sp,
                                color = if (isDragging) Color(0xFF3b82f6) else Color(0xFF9ca3af)
                            )
                            Text(
                                text = formatTime(duration),
                                fontSize = 13.sp,
                                color = Color(0xFF9ca3af)
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
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF3b82f6),
                                activeTrackColor = Color(0xFF3b82f6),
                                inactiveTrackColor = Color(0xFF374151)
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Modern control buttons row - Chapters, Speed, Sleep Timer
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Chapters button - modern pill style
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(Color(0xFF1e293b), Color(0xFF334155))
                                    )
                                )
                                .clickable { showChapters = !showChapters }
                                .padding(vertical = 12.dp, horizontal = 12.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF3b82f6).copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.List,
                                        contentDescription = "Chapters",
                                        modifier = Modifier.size(18.dp),
                                        tint = Color(0xFF60a5fa)
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = currentChapter?.title ?: "—",
                                    fontSize = 12.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // Speed button - modern pill style
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(Color(0xFF1e293b), Color(0xFF334155))
                                    )
                                )
                                .clickable { showPlaybackSpeed = !showPlaybackSpeed }
                                .padding(vertical = 12.dp, horizontal = 12.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF8b5cf6).copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Speed,
                                        contentDescription = "Speed",
                                        modifier = Modifier.size(18.dp),
                                        tint = Color(0xFFa78bfa)
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "${playbackSpeed}x",
                                    fontSize = 12.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        // Sleep Timer button - modern pill style
                        val hasSleepTimer = sleepTimerRemaining != null && sleepTimerRemaining > 0
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (hasSleepTimer) {
                                        Brush.horizontalGradient(
                                            listOf(Color(0xFFf59e0b).copy(alpha = 0.2f), Color(0xFFfbbf24).copy(alpha = 0.2f))
                                        )
                                    } else {
                                        Brush.horizontalGradient(
                                            listOf(Color(0xFF1e293b), Color(0xFF334155))
                                        )
                                    }
                                )
                                .clickable { showSleepTimer = !showSleepTimer }
                                .padding(vertical = 12.dp, horizontal = 12.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (hasSleepTimer) Color(0xFFf59e0b).copy(alpha = 0.3f)
                                            else Color(0xFFf59e0b).copy(alpha = 0.15f)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Bedtime,
                                        contentDescription = "Sleep Timer",
                                        modifier = Modifier.size(18.dp),
                                        tint = Color(0xFFfbbf24)
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = if (hasSleepTimer && sleepTimerRemaining != null) {
                                        val mins = sleepTimerRemaining / 60
                                        val secs = sleepTimerRemaining % 60
                                        "${mins}:${secs.toString().padStart(2, '0')}"
                                    } else "Off",
                                    fontSize = 12.sp,
                                    color = if (hasSleepTimer) Color(0xFFfbbf24) else Color.White,
                                    fontWeight = FontWeight.SemiBold
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
                                            color = Color(0xFF3b82f6),
                                            modifier = Modifier.weight(1f).padding(end = 8.dp)
                                        )
                                    } else {
                                        // Use ellipsis for other chapters
                                        Text(
                                            text = chapter.title ?: "Chapter ${index + 1}",
                                            color = Color.White,
                                            fontWeight = FontWeight.Normal,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f).padding(end = 8.dp)
                                        )
                                    }
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
                    TextButton(onClick = { showChapters = false }) {
                        Text("Close", color = Color(0xFF3b82f6))
                    }
                },
                containerColor = Color(0xFF1e293b)
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
                                    color = if (speed == playbackSpeed) Color(0xFF3b82f6) else Color.White,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showPlaybackSpeed = false }) {
                        Text("Close", color = Color(0xFF3b82f6))
                    }
                },
                containerColor = Color(0xFF1e293b)
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
                            tint = Color(0xFFfbbf24),
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
                                color = Color(0xFFf59e0b).copy(alpha = 0.15f)
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
                                            color = Color(0xFFfbbf24),
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        val mins = (sleepTimerRemaining ?: 0) / 60
                                        val secs = (sleepTimerRemaining ?: 0) % 60
                                        Text(
                                            "${mins}:${secs.toString().padStart(2, '0')} remaining",
                                            color = Color(0xFFfbbf24).copy(alpha = 0.8f),
                                            fontSize = 14.sp
                                        )
                                    }
                                    TextButton(
                                        onClick = {
                                            AudioPlaybackService.instance?.cancelSleepTimer()
                                        }
                                    ) {
                                        Text("Cancel", color = Color(0xFFef4444))
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
                                            tint = Color(0xFF10b981),
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
                        Text("Close", color = Color(0xFF3b82f6))
                    }
                },
                containerColor = Color(0xFF1e293b)
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
                        color = Color(0xFF3b82f6),
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
        String.format("%d:%02d:%02d", hours, minutes, secs)
    } else {
        String.format("%d:%02d", minutes, secs)
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
