package com.sappho.audiobooks.presentation.player

import androidx.compose.animation.animateColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.sappho.audiobooks.presentation.theme.*
import com.sappho.audiobooks.presentation.theme.Timing
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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

    // Derive current chapter title from chapters list and position
    val currentChapterTitle = remember(audiobook?.chapters, currentPosition) {
        audiobook?.chapters?.let { chapters ->
            if (chapters.isEmpty()) return@let null
            val posSec = currentPosition.toDouble()
            chapters.firstOrNull { chapter ->
                val end = chapter.endTime ?: (chapter.startTime + (chapter.duration ?: 0.0))
                posSec >= chapter.startTime && posSec < end
            }?.title ?: chapters.lastOrNull { it.startTime <= posSec }?.title
        }
    }

    // Animated gradient offset for progress bar shimmer
    val gradientTransition = rememberInfiniteTransition(label = "gradientShimmer")
    val gradientOffset by gradientTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "gradientOffset"
    )

    // Cover art pulse animation when playing
    val coverPulseTransition = rememberInfiniteTransition(label = "coverPulse")
    val coverScale by coverPulseTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "coverScale"
    )


    audiobook?.let { book ->
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
            color = SapphoSurfaceElevated,
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

                        // Active progress - animated gradient when playing, solid when paused
                        val progressBrush = if (isPlaying) {
                            val offset = gradientOffset * 2f
                            Brush.linearGradient(
                                colors = listOf(
                                    SapphoInfo,
                                    SapphoSuccess,
                                    SapphoInfo,
                                    SapphoSuccess
                                ),
                                start = Offset(sliderWidth * (offset - 1f), 0f),
                                end = Offset(sliderWidth * offset, 0f)
                            )
                        } else {
                            Brush.linearGradient(
                                colors = listOf(SapphoInfo, SapphoInfo)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth(sliderPosition.coerceIn(0f, 1f))
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(1.5.dp))
                                .background(progressBrush)
                        )
                    }

                    // Thumb - centered vertically, positioned horizontally based on progress
                    val thumbOffset = with(density) {
                        val thumbRadiusPx = 6.dp.toPx()
                        ((sliderWidth * sliderPosition) - thumbRadiusPx).coerceAtLeast(0f).toDp()
                    }

                    // Glowing thumb when playing
                    if (isPlaying) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .offset(x = thumbOffset - 2.dp)
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(SapphoInfo.copy(alpha = 0.3f))
                        )
                    }

                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .offset(x = thumbOffset)
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(
                                if (isPlaying) {
                                    Brush.linearGradient(
                                        colors = listOf(SapphoInfo, SapphoSuccess)
                                    )
                                } else {
                                    Brush.linearGradient(
                                        colors = listOf(SapphoInfo, SapphoInfo)
                                    )
                                }
                            )
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
                    // Cover art with subtle pulse when playing
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .scale(if (isPlaying) coverScale else 1f)
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

                        // Chapter name display
                        currentChapterTitle?.let { chapterTitle ->
                            Text(
                                text = chapterTitle,
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                color = SapphoTextSecondary,
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

                    // Waveform visualizer bars - only visible when playing
                    if (isPlaying) {
                        WaveformVisualizer(
                            modifier = Modifier
                                .padding(end = 4.dp)
                                .height(22.dp)
                                .width(25.dp)
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

                    // Play button glow color when playing
                    val playGlowAlpha by playPulseTransition.animateFloat(
                        initialValue = 0.2f,
                        targetValue = 0.5f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(
                                durationMillis = 3000,
                                easing = FastOutSlowInEasing
                            ),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "playGlowAlpha"
                    )

                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .scale(if (isPlaying) playScale * playPulseScale else playScale)
                            .then(
                                if (isPlaying) {
                                    Modifier.drawBehind {
                                        // Green glow shadow behind play button
                                        drawCircle(
                                            color = SapphoSuccess.copy(alpha = playGlowAlpha),
                                            radius = size.maxDimension / 1.4f
                                        )
                                    }
                                } else {
                                    Modifier
                                }
                            )
                            .graphicsLayer {
                                alpha = if (isPlaying) playPulseAlpha else 1f
                                shadowElevation = if (isPlaying) 20f else 4f
                            }
                            .clip(CircleShape)
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

/**
 * Animated waveform visualizer with 3 bars.
 * Bars use a color-shifting gradient from green to blue with smooth cubic-bezier easing.
 */
@Composable
private fun WaveformVisualizer(
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "waveform")

    // 3 bars with varied speeds for organic feel (matches PWA)
    val barAnimations = listOf(
        transition.animateFloat(
            initialValue = 0.3f, targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ), label = "bar0"
        ),
        transition.animateFloat(
            initialValue = 0.5f, targetValue = 0.25f,
            animationSpec = infiniteRepeatable(
                animation = tween(900, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ), label = "bar1"
        ),
        transition.animateFloat(
            initialValue = 0.4f, targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1100, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ), label = "bar2"
        )
    )

    // Color shift animations — each bar shifts between green-first and blue-first gradients
    val colorShifts = listOf(
        transition.animateFloat(
            initialValue = 0f, targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(2400, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ), label = "color0"
        ),
        transition.animateFloat(
            initialValue = 1f, targetValue = 0f,
            animationSpec = infiniteRepeatable(
                animation = tween(2000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ), label = "color1"
        ),
        transition.animateFloat(
            initialValue = 0f, targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(2800, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ), label = "color2"
        )
    )

    Canvas(modifier = modifier) {
        val barWidthPx = 3.dp.toPx()
        val spacingPx = 2.5.dp.toPx()
        val totalWidth = 3 * barWidthPx + 2 * spacingPx
        val startX = (size.width - totalWidth) / 2f
        val maxBarHeight = size.height

        for (i in 0 until 3) {
            val fraction = barAnimations[i].value
            val barHeight = maxBarHeight * fraction
            val x = startX + i * (barWidthPx + spacingPx)
            val y = (maxBarHeight - barHeight) / 2f

            // Interpolate gradient direction based on color shift
            val shift = colorShifts[i].value
            val topColor = lerp(SapphoSuccess, SapphoInfo, shift)
            val bottomColor = lerp(SapphoInfo, SapphoSuccess, shift)

            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(topColor, bottomColor)
                ),
                topLeft = Offset(x, y),
                size = Size(barWidthPx, barHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidthPx)
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
