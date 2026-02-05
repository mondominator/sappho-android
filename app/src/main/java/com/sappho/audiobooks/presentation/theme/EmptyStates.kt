package com.sappho.audiobooks.presentation.theme

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * Base empty state component with optional illustration
 */
@Composable
fun EmptyState(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    illustration: (@Composable () -> Unit)? = null,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null,
    isAnimated: Boolean = true
) {
    val alpha by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(600, delayMillis = 200),
        label = "empty_state_alpha"
    )
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .alpha(if (isAnimated) alpha else 1f)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Illustration or Icon
        if (illustration != null) {
            illustration()
        } else if (icon != null) {
            val iconAlpha by animateFloatAsState(
                targetValue = 0.6f,
                animationSpec = tween(800, delayMillis = 400),
                label = "icon_alpha"
            )
            
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier
                    .size(80.dp)
                    .alpha(if (isAnimated) iconAlpha else 0.6f),
                tint = MaterialTheme.colorScheme.outline
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Title
        val titleAlpha by animateFloatAsState(
            targetValue = 1f,
            animationSpec = tween(600, delayMillis = 600),
            label = "title_alpha"
        )
        
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.alpha(if (isAnimated) titleAlpha else 1f)
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Subtitle
        val subtitleAlpha by animateFloatAsState(
            targetValue = 1f,
            animationSpec = tween(600, delayMillis = 800),
            label = "subtitle_alpha"
        )
        
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.alpha(if (isAnimated) subtitleAlpha else 1f)
        )
        
        // Action Button
        if (actionText != null && onActionClick != null) {
            Spacer(modifier = Modifier.height(24.dp))
            
            val buttonAlpha by animateFloatAsState(
                targetValue = 1f,
                animationSpec = tween(600, delayMillis = 1000),
                label = "button_alpha"
            )
            
            Button(
                onClick = onActionClick,
                modifier = Modifier.alpha(if (isAnimated) buttonAlpha else 1f)
            ) {
                Text(actionText)
            }
        }
    }
}

/**
 * Empty library state
 */
@Composable
fun EmptyLibrary(
    modifier: Modifier = Modifier,
    onRefresh: () -> Unit = {}
) {
    EmptyState(
        title = "Your library is empty",
        subtitle = "Add some audiobooks to get started with your listening journey.",
        icon = Icons.Default.LibraryBooks,
        actionText = "Refresh",
        onActionClick = onRefresh,
        modifier = modifier
    )
}

/**
 * Empty search results state
 */
@Composable
fun EmptySearchResults(
    query: String,
    modifier: Modifier = Modifier,
    onClearSearch: () -> Unit = {}
) {
    EmptyState(
        title = "No results found",
        subtitle = "Try searching with different keywords or browse the library.",
        icon = Icons.Default.Search,
        actionText = "Clear search",
        onActionClick = onClearSearch,
        modifier = modifier
    )
}

/**
 * No internet connection state
 */
@Composable
fun NoInternetConnection(
    modifier: Modifier = Modifier,
    onRetry: () -> Unit = {}
) {
    EmptyState(
        title = "No internet connection",
        subtitle = "Check your connection and try again.",
        icon = Icons.Default.CloudOff,
        actionText = "Retry",
        onActionClick = onRetry,
        modifier = modifier
    )
}

/**
 * Server error state
 */
@Composable
fun ServerError(
    modifier: Modifier = Modifier,
    onRetry: () -> Unit = {}
) {
    EmptyState(
        title = "Something went wrong",
        subtitle = "We're having trouble connecting to our servers. Please try again.",
        icon = Icons.Default.Error,
        actionText = "Try again",
        onActionClick = onRetry,
        modifier = modifier
    )
}

/**
 * Animated audiobook illustration
 */
@Composable
fun AnimatedAudiobookIllustration(
    modifier: Modifier = Modifier,
    size: Float = 120f
) {
    val infiniteTransition = rememberInfiniteTransition(label = "audiobook_anim")
    
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(20000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )
    
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    
    Canvas(
        modifier = modifier.size(size.dp)
    ) {
        val center = Offset(size.dp.toPx() / 2, size.dp.toPx() / 2)
        val radius = size.dp.toPx() / 3
        
        // Background circle
        drawCircle(
            color = SapphoInfo,
            radius = radius * pulse,
            center = center,
            alpha = 0.1f
        )
        
        // Rotating book icon representation
        drawArc(
            color = SapphoInfo,
            startAngle = rotation,
            sweepAngle = 60f,
            useCenter = false,
            topLeft = Offset(
                center.x - radius * 0.8f,
                center.y - radius * 0.8f
            ),
            size = Size(
                radius * 1.6f,
                radius * 1.6f
            ),
            style = Stroke(width = 4.dp.toPx())
        )
        
        // Central dot
        drawCircle(
            color = SapphoInfo,
            radius = 6.dp.toPx(),
            center = center
        )
    }
}

/**
 * Animated music waves illustration
 */
@Composable
fun AnimatedMusicWaves(
    modifier: Modifier = Modifier,
    size: Float = 120f
) {
    val infiniteTransition = rememberInfiniteTransition(label = "music_waves")
    
    val wave1 by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "wave1"
    )
    
    val wave2 by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, delayMillis = 200, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "wave2"
    )
    
    val wave3 by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, delayMillis = 400, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "wave3"
    )
    
    Canvas(
        modifier = modifier.size(size.dp)
    ) {
        val center = Offset(size.dp.toPx() / 2, size.dp.toPx() / 2)
        val baseHeight = size.dp.toPx() * 0.1f
        val spacing = size.dp.toPx() * 0.15f
        
        // Wave bars
        val waves = listOf(wave1, wave2, wave3, wave2, wave1)
        waves.forEachIndexed { index, amplitude ->
            val x = center.x + (index.toFloat() - 2f) * spacing
            val height = baseHeight + (size.dp.toPx() * 0.3f * amplitude)
            
            drawRect(
                color = SapphoInfo,
                topLeft = Offset(x - 4.dp.toPx(), center.y - height / 2),
                size = Size(8.dp.toPx(), height),
                alpha = 0.7f
            )
        }
    }
}

/**
 * Empty state with animated audiobook illustration
 */
@Composable
fun EmptyAudiobooks(
    modifier: Modifier = Modifier,
    onRefresh: () -> Unit = {}
) {
    EmptyState(
        title = "No audiobooks found",
        subtitle = "Start building your library by adding some great audiobooks to listen to.",
        illustration = { AnimatedAudiobookIllustration() },
        actionText = "Refresh Library",
        onActionClick = onRefresh,
        modifier = modifier
    )
}

/**
 * Empty reading list state
 */
@Composable
fun EmptyReadingList(
    modifier: Modifier = Modifier
) {
    EmptyState(
        title = "Your reading list is empty",
        subtitle = "Add audiobooks to your reading list to keep track of what you want to listen to next.",
        icon = Icons.Default.BookmarkBorder,
        modifier = modifier
    )
}

/**
 * Loading error state
 */
@Composable
fun LoadingError(
    message: String = "Something went wrong",
    modifier: Modifier = Modifier,
    onRetry: () -> Unit = {}
) {
    EmptyState(
        title = "Oops!",
        subtitle = message,
        icon = Icons.Default.ErrorOutline,
        actionText = "Try Again",
        onActionClick = onRetry,
        modifier = modifier
    )
}