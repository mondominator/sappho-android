package com.sappho.audiobooks.presentation.theme

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Shimmer effect for skeleton loading states
 */
@Composable
fun shimmerBrush(
    targetValue: Float = 1000f,
    showShimmer: Boolean = true
): Brush {
    if (!showShimmer) {
        return Brush.linearGradient(
            colors = listOf(
                Color.Transparent,
                Color.Transparent
            )
        )
    }

    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnimation = transition.animateFloat(
        initialValue = 0f,
        targetValue = targetValue,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 1200,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_translate"
    )

    val shimmerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    val backgroundColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)

    return Brush.linearGradient(
        colors = listOf(
            backgroundColor,
            shimmerColor,
            backgroundColor
        ),
        start = Offset.Zero,
        end = Offset(x = translateAnimation.value, y = translateAnimation.value)
    )
}

/**
 * Basic skeleton box for loading states
 */
@Composable
fun SkeletonBox(
    modifier: Modifier = Modifier,
    height: Dp = 16.dp,
    showShimmer: Boolean = true
) {
    Box(
        modifier = modifier
            .height(height)
            .background(
                brush = shimmerBrush(showShimmer = showShimmer),
                shape = RoundedCornerShape(4.dp)
            )
    )
}

/**
 * Skeleton circle for avatar placeholders
 */
@Composable
fun SkeletonCircle(
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    showShimmer: Boolean = true
) {
    Box(
        modifier = modifier
            .size(size)
            .background(
                brush = shimmerBrush(showShimmer = showShimmer),
                shape = CircleShape
            )
    )
}

/**
 * Skeleton audiobook card for home screen loading
 */
@Composable
fun SkeletonAudiobookCard(
    modifier: Modifier = Modifier,
    cardSize: Dp = 140.dp,
    showShimmer: Boolean = true
) {
    Column(
        modifier = modifier.width(cardSize + 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Cover image placeholder
        SkeletonBox(
            modifier = Modifier
                .width(cardSize)
                .height(cardSize),
            showShimmer = showShimmer
        )
        
        // Title placeholder (2 lines)
        SkeletonBox(
            modifier = Modifier
                .fillMaxWidth()
                .height(16.dp),
            showShimmer = showShimmer
        )
        SkeletonBox(
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .height(16.dp),
            showShimmer = showShimmer
        )
        
        // Author placeholder
        SkeletonBox(
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .height(12.dp),
            showShimmer = showShimmer
        )
    }
}

/**
 * Skeleton text with multiple lines
 */
@Composable
fun SkeletonText(
    modifier: Modifier = Modifier,
    lines: Int = 1,
    lineHeight: Dp = 16.dp,
    lastLineWidth: Float = 0.7f,
    showShimmer: Boolean = true
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        repeat(lines) { index ->
            val width = if (index == lines - 1) lastLineWidth else 1f
            SkeletonBox(
                modifier = Modifier.fillMaxWidth(width),
                height = lineHeight,
                showShimmer = showShimmer
            )
        }
    }
}

/**
 * Skeleton row with icon and text
 */
@Composable
fun SkeletonRow(
    modifier: Modifier = Modifier,
    iconSize: Dp = 24.dp,
    textLines: Int = 1,
    showShimmer: Boolean = true
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SkeletonCircle(
            size = iconSize,
            showShimmer = showShimmer
        )
        SkeletonText(
            modifier = Modifier.weight(1f),
            lines = textLines,
            showShimmer = showShimmer
        )
    }
}

/**
 * Skeleton home section with title and cards
 */
@Composable
fun SkeletonHomeSection(
    modifier: Modifier = Modifier,
    sectionTitle: String = "Loading...",
    cardCount: Int = 3,
    cardSize: Dp = 140.dp,
    showShimmer: Boolean = true
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Section title
        SkeletonBox(
            modifier = Modifier
                .width(120.dp)
                .height(20.dp),
            showShimmer = showShimmer
        )
        
        // Horizontal row of skeleton cards
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            repeat(cardCount) {
                SkeletonAudiobookCard(
                    cardSize = cardSize,
                    showShimmer = showShimmer
                )
            }
        }
    }
}

/**
 * Complete skeleton loading state for home screen
 */
@Composable
fun SkeletonHomeScreen(
    modifier: Modifier = Modifier,
    showShimmer: Boolean = true
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(32.dp)
    ) {
        // Continue Listening section
        SkeletonHomeSection(
            sectionTitle = "Continue Listening",
            cardCount = 3,
            showShimmer = showShimmer
        )
        
        // Recently Added section
        SkeletonHomeSection(
            sectionTitle = "Recently Added",
            cardCount = 3,
            showShimmer = showShimmer
        )
        
        // Listen Again section
        SkeletonHomeSection(
            sectionTitle = "Listen Again",
            cardCount = 3,
            showShimmer = showShimmer
        )
    }
}

/**
 * Skeleton loading for audiobook detail screen
 */
@Composable
fun SkeletonAudiobookDetail(
    modifier: Modifier = Modifier,
    showShimmer: Boolean = true
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Back button area
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            SkeletonBox(
                modifier = Modifier.size(width = 80.dp, height = 36.dp),
                showShimmer = showShimmer
            )
            SkeletonBox(
                modifier = Modifier.size(width = 60.dp, height = 36.dp),
                showShimmer = showShimmer
            )
        }
        
        // Cover image and info
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Cover
            SkeletonBox(
                modifier = Modifier.size(120.dp),
                showShimmer = showShimmer
            )
            
            // Book info
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SkeletonText(lines = 2, lineHeight = 18.dp, showShimmer = showShimmer)
                SkeletonText(lines = 1, lineHeight = 14.dp, lastLineWidth = 0.6f, showShimmer = showShimmer)
                SkeletonText(lines = 1, lineHeight = 12.dp, lastLineWidth = 0.4f, showShimmer = showShimmer)
            }
        }
        
        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SkeletonBox(
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
                showShimmer = showShimmer
            )
            SkeletonBox(
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
                showShimmer = showShimmer
            )
        }
        
        // Description area
        SkeletonText(
            lines = 4,
            lineHeight = 16.dp,
            showShimmer = showShimmer
        )
    }
}