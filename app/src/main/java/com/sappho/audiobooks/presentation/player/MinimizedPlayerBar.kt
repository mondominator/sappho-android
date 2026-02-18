package com.sappho.audiobooks.presentation.player

import androidx.compose.animation.animateColor
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import com.sappho.audiobooks.presentation.theme.*
import com.sappho.audiobooks.presentation.theme.Timing
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.sappho.audiobooks.service.AudioPlaybackService
import com.sappho.audiobooks.service.PlayerState
import com.sappho.audiobooks.cast.CastHelper
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MinimizedPlayerBar(
    playerState: PlayerState,
    serverUrl: String?,
    castHelper: CastHelper,
    onExpand: () -> Unit,
    onRestartPlayback: (audiobookId: Int, position: Int) -> Unit = { _, _ -> }
) {
    val audiobook by playerState.currentAudiobook.collectAsState()
    val localIsPlaying by playerState.isPlaying.collectAsState()
    val localPosition by playerState.currentPosition.collectAsState()
    val duration by playerState.duration.collectAsState()
    val bufferedPosition by playerState.bufferedPosition.collectAsState()

    // Check cast state - use reactive StateFlow
    val isCastConnected by castHelper.isConnected.collectAsState()
    val castIsPlaying by castHelper.isPlayingFlow.collectAsState()
    val isPlaying = if (isCastConnected) castIsPlaying else localIsPlaying

    // Poll Cast position when connected for smooth time updates
    var castPosition by remember { mutableLongStateOf(0L) }
    LaunchedEffect(isCastConnected) {
        if (isCastConnected) {
            while (true) {
                castPosition = castHelper.getCurrentPosition()
                delay(Timing.POLL_INTERVAL_MS)
            }
        }
    }
    val currentPosition = if (isCastConnected) castPosition else localPosition

    // Slider state for seeking
    var sliderPosition by remember { mutableFloatStateOf(0f) }
    var isUserSeeking by remember { mutableStateOf(false) }

    // Update slider position from playback when not seeking
    LaunchedEffect(currentPosition, duration, isUserSeeking) {
        if (!isUserSeeking && duration > 0) {
            sliderPosition = (currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
        }
    }

    // Calculate buffered progress
    val bufferedProgress = if (duration > 0) {
        (bufferedPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    audiobook?.let { book ->
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
            color = SapphoSurfaceLight,
            shadowElevation = 8.dp
        ) {
            Column(modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
            ) {
                // Custom progress slider at top
                var sliderWidth by remember { mutableFloatStateOf(0f) }
                val density = androidx.compose.ui.platform.LocalDensity.current

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(20.dp)
                        .padding(horizontal = 8.dp)
                        .pointerInput(duration) {
                            detectTapGestures { offset ->
                                if (sliderWidth > 0 && duration > 0) {
                                    val newProgress = (offset.x / sliderWidth).coerceIn(0f, 1f)
                                    sliderPosition = newProgress
                                    val seekPosition = (newProgress * duration).toLong()
                                    if (isCastConnected) {
                                        castHelper.seek(seekPosition)
                                    } else {
                                        AudioPlaybackService.instance?.seekTo(seekPosition)
                                    }
                                }
                            }
                        }
                        .pointerInput(duration) {
                            detectHorizontalDragGestures(
                                onDragStart = { isUserSeeking = true },
                                onDragEnd = {
                                    isUserSeeking = false
                                    val seekPosition = (sliderPosition * duration).toLong()
                                    if (isCastConnected) {
                                        castHelper.seek(seekPosition)
                                    } else {
                                        AudioPlaybackService.instance?.seekTo(seekPosition)
                                    }
                                },
                                onHorizontalDrag = { _, dragAmount ->
                                    if (sliderWidth > 0) {
                                        val delta = dragAmount / sliderWidth
                                        sliderPosition = (sliderPosition + delta).coerceIn(0f, 1f)
                                    }
                                }
                            )
                        }
                        .onSizeChanged { sliderWidth = it.width.toFloat() }
                ) {
                    // Track container - centered vertically
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .align(Alignment.Center)
                    ) {
                        // Background track
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(1.5.dp))
                                .background(SapphoProgressTrack)
                        )

                        // Buffered progress
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(bufferedProgress.coerceIn(0f, 1f))
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(1.5.dp))
                                .background(SapphoInfo.copy(alpha = 0.3f))
                        )

                        // Active progress
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(sliderPosition.coerceIn(0f, 1f))
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(1.5.dp))
                                .background(SapphoInfo)
                        )
                    }

                    // Thumb - centered vertically, positioned horizontally based on progress
                    val thumbOffset = with(density) {
                        val thumbRadiusPx = 6.dp.toPx()
                        ((sliderWidth * sliderPosition) - thumbRadiusPx).coerceAtLeast(0f).toDp()
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .offset(x = thumbOffset)
                            .size(12.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(SapphoInfo)
                    )
                }

                // Main content row
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clickable(onClick = onExpand)
                        .padding(start = 8.dp, end = 8.dp, top = 0.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Cover art
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(SapphoProgressTrack)
                    ) {
                        if (book.coverImage != null && serverUrl != null) {
                            AsyncImage(
                                model = com.sappho.audiobooks.util.buildCoverUrl(serverUrl, book.id, com.sappho.audiobooks.util.COVER_WIDTH_THUMBNAIL),
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
                                    text = book.title.take(1).uppercase(),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = SapphoInfo
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    // Title and metadata
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.Center
                    ) {
                        // Marquee scrolling title
                        MarqueeText(
                            text = book.title,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White,
                            lineHeight = 15.sp
                        )

                        book.author?.let { author ->
                            Text(
                                text = author,
                                style = MaterialTheme.typography.labelSmall,
                                color = SapphoIconDefault,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Time display with pulsing animation when playing
                        val timePulseTransition = rememberInfiniteTransition(label = "timePulse")
                        val timeColor by timePulseTransition.animateColor(
                            initialValue = LegacyBlueLight,
                            targetValue = LegacyBluePale,
                            animationSpec = infiniteRepeatable(
                                animation = tween(
                                    durationMillis = 2000,
                                    easing = FastOutSlowInEasing
                                ),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "timeColorPulse"
                        )

                        Text(
                            text = "${formatTime(currentPosition)} / ${formatTime(duration)}",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            color = if (isPlaying) timeColor else SapphoTextMuted
                        )
                    }

                    // Seek back button (10 seconds)
                    IconButton(
                        onClick = {
                            if (isCastConnected) {
                                val newPosition = (currentPosition - 10).coerceAtLeast(0)
                                castHelper.seek(newPosition)
                            } else {
                                AudioPlaybackService.instance?.skipBackward()
                            }
                        },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Replay10,
                            contentDescription = SapphoAccessibility.ContentDescriptions.SKIP_BACKWARD,
                            tint = SapphoIconDefault,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // Play/Pause button - Modern circular button with animation
                    val playInteractionSource = remember { MutableInteractionSource() }
                    val isPlayPressed by playInteractionSource.collectIsPressedAsState()
                    val playScale by animateFloatAsState(
                        targetValue = if (isPlayPressed) 0.9f else 1f,
                        animationSpec = tween(durationMillis = 100),
                        label = "playScale"
                    )

                    // Pulsing animation when playing - matches PWA
                    val playPulseTransition = rememberInfiniteTransition(label = "playPulse")
                    val playPulseScale by playPulseTransition.animateFloat(
                        initialValue = 1f,
                        targetValue = 1.08f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(
                                durationMillis = 3000,
                                easing = FastOutSlowInEasing
                            ),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "playPulseScale"
                    )

                    val playPulseAlpha by playPulseTransition.animateFloat(
                        initialValue = 0.7f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(
                                durationMillis = 3000,
                                easing = FastOutSlowInEasing
                            ),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "playPulseAlpha"
                    )

                    val buttonColor = if (isPlaying) {
                        SapphoSuccess
                    } else {
                        SapphoInfo
                    }

                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .scale(if (isPlaying) playScale * playPulseScale else playScale)
                            .graphicsLayer {
                                alpha = if (isPlaying) playPulseAlpha else 1f
                                shadowElevation = if (isPlaying) 20f else 4f
                            }
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(buttonColor)
                            .clickable(
                                interactionSource = playInteractionSource,
                                indication = null
                            ) {
                                if (isCastConnected) {
                                    if (isPlaying) {
                                        castHelper.pause()
                                    } else {
                                        castHelper.play()
                                    }
                                } else {
                                    val service = AudioPlaybackService.instance
                                    val playerHandled = service?.togglePlayPause() ?: false
                                    if (!playerHandled) {
                                        // Service is null or player is null (killed after vehicle disconnect, etc.)
                                        // Restart playback from current position or saved progress
                                        val position = if (currentPosition > 0) {
                                            currentPosition.toInt()
                                        } else {
                                            book.progress?.position ?: 0
                                        }
                                        onRestartPlayback(book.id, position)
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) SapphoAccessibility.ContentDescriptions.PAUSE_BUTTON else SapphoAccessibility.ContentDescriptions.PLAY_BUTTON,
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    // Seek forward button (10 seconds)
                    IconButton(
                        onClick = {
                            if (isCastConnected) {
                                val newPosition = currentPosition + 10
                                castHelper.seek(newPosition)
                            } else {
                                AudioPlaybackService.instance?.skipForward()
                            }
                        },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Forward10,
                            contentDescription = SapphoAccessibility.ContentDescriptions.SKIP_FORWARD,
                            tint = SapphoIconDefault,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
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

@Composable
fun MarqueeText(
    text: String,
    fontSize: androidx.compose.ui.unit.TextUnit,
    fontWeight: FontWeight,
    color: Color,
    modifier: Modifier = Modifier,
    lineHeight: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified
) {
    val scrollState = rememberScrollState()

    // Start scrolling after a short delay to let layout complete
    LaunchedEffect(text) {
        delay(Timing.DEBOUNCE_LAYOUT_MS)
        while (true) {
            if (scrollState.maxValue > 0) {
                // Scroll to end
                scrollState.animateScrollTo(
                    scrollState.maxValue,
                    animationSpec = tween(
                        durationMillis = (scrollState.maxValue * 20).coerceIn(2000, 10000),
                        easing = LinearEasing
                    )
                )
                delay(Timing.FEEDBACK_SHORT_MS) // Pause at end
                // Scroll back to start
                scrollState.animateScrollTo(
                    0,
                    animationSpec = tween(
                        durationMillis = (scrollState.maxValue * 20).coerceIn(2000, 10000),
                        easing = LinearEasing
                    )
                )
                delay(Timing.FEEDBACK_SHORT_MS) // Pause at start
            } else {
                delay(Timing.DEBOUNCE_LAYOUT_MS) // Check again later
            }
        }
    }

    Row(
        modifier = modifier.horizontalScroll(scrollState, enabled = false)
    ) {
        Text(
            text = text,
            fontSize = fontSize,
            fontWeight = fontWeight,
            color = color,
            maxLines = 1,
            softWrap = false,
            lineHeight = lineHeight
        )
    }
}
