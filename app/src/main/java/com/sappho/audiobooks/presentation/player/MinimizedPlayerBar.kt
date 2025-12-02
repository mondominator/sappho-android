package com.sappho.audiobooks.presentation.player

import androidx.compose.animation.animateColor
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
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
import javax.inject.Inject
import kotlinx.coroutines.delay

@Composable
fun MinimizedPlayerBar(
    playerState: PlayerState,
    serverUrl: String?,
    castHelper: CastHelper,
    onExpand: () -> Unit,
    onCastClick: () -> Unit
) {
    val audiobook by playerState.currentAudiobook.collectAsState()
    val localIsPlaying by playerState.isPlaying.collectAsState()
    val localPosition by playerState.currentPosition.collectAsState()
    val duration by playerState.duration.collectAsState()

    // Check cast state - use reactive StateFlow
    val isCastConnected by castHelper.isConnected.collectAsState()
    val castIsPlaying by castHelper.isPlayingFlow.collectAsState()
    val isPlaying = if (isCastConnected) castIsPlaying else localIsPlaying

    // Poll Cast position when connected for smooth time updates
    var castPosition by remember { mutableStateOf(0L) }
    LaunchedEffect(isCastConnected) {
        if (isCastConnected) {
            while (true) {
                castPosition = castHelper.getCurrentPosition()
                delay(1000)
            }
        }
    }
    val currentPosition = if (isCastConnected) castPosition else localPosition

    audiobook?.let { book ->
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .clickable(onClick = onExpand),
            color = Color(0xFF1e293b),
            shadowElevation = 8.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Cover art
                Box(
                    modifier = Modifier
                        .size(56.dp)
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
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = book.title.take(1).uppercase(),
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF3b82f6)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Title and metadata
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    // Marquee scrolling title
                    MarqueeText(
                        text = book.title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    book.author?.let { author ->
                        Text(
                            text = author,
                            fontSize = 12.sp,
                            color = Color(0xFF9ca3af),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    // Time display with pulsing animation when playing
                    val timePulseTransition = rememberInfiniteTransition(label = "timePulse")
                    val timeColor by timePulseTransition.animateColor(
                        initialValue = Color(0xFF60a5fa),
                        targetValue = Color(0xFF93c5fd),
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
                        fontSize = 11.sp,
                        color = if (isPlaying) timeColor else Color(0xFF6b7280)
                    )
                }

                Spacer(modifier = Modifier.width(24.dp))

                // Cast button (before play button)
                IconButton(
                    onClick = onCastClick,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Cast,
                        contentDescription = "Cast",
                        tint = if (isCastConnected) Color(0xFF3b82f6) else Color(0xFF9ca3af),
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

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
                    targetValue = 1.12f,
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
                    initialValue = 0.6f,
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
                    Color(0xFF10b981)
                } else {
                    Color(0xFF3b82f6)
                }

                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .scale(if (isPlaying) playScale * playPulseScale else playScale)
                        .graphicsLayer {
                            alpha = if (isPlaying) playPulseAlpha else 1f
                            shadowElevation = if (isPlaying) 40f else 8f
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
                                AudioPlaybackService.instance?.togglePlayPause()
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(modifier = Modifier.width(24.dp))
            }
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

@Composable
fun MarqueeText(
    text: String,
    fontSize: androidx.compose.ui.unit.TextUnit,
    fontWeight: FontWeight,
    color: Color,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    // Start scrolling after a short delay to let layout complete
    LaunchedEffect(text) {
        delay(500) // Wait for layout
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
                delay(1500) // Pause at end
                // Scroll back to start
                scrollState.animateScrollTo(
                    0,
                    animationSpec = tween(
                        durationMillis = (scrollState.maxValue * 20).coerceIn(2000, 10000),
                        easing = LinearEasing
                    )
                )
                delay(1500) // Pause at start
            } else {
                delay(500) // Check again later
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
            softWrap = false
        )
    }
}
