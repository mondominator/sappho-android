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
 * Skeleton loading for search results
 */
@Composable
fun SkeletonSearchResults(
    modifier: Modifier = Modifier,
    showShimmer: Boolean = true
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Section header skeleton
        SkeletonBox(
            modifier = Modifier.width(80.dp),
            height = 14.dp,
            showShimmer = showShimmer
        )

        // Search result items
        repeat(5) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Cover thumbnail
                SkeletonBox(
                    modifier = Modifier.size(56.dp),
                    showShimmer = showShimmer
                )

                // Text content
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SkeletonBox(
                        modifier = Modifier.fillMaxWidth(0.8f),
                        height = 16.dp,
                        showShimmer = showShimmer
                    )
                    SkeletonBox(
                        modifier = Modifier.fillMaxWidth(0.5f),
                        height = 12.dp,
                        showShimmer = showShimmer
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Another section
        SkeletonBox(
            modifier = Modifier.width(60.dp),
            height = 14.dp,
            showShimmer = showShimmer
        )

        repeat(3) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SkeletonCircle(size = 40.dp, showShimmer = showShimmer)
                SkeletonBox(
                    modifier = Modifier.weight(1f),
                    height = 16.dp,
                    showShimmer = showShimmer
                )
            }
        }
    }
}

/**
 * Skeleton loading for library grid
 */
@Composable
fun SkeletonLibraryGrid(
    modifier: Modifier = Modifier,
    columns: Int = 3,
    rows: Int = 4,
    showShimmer: Boolean = true
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        repeat(rows) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                repeat(columns) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SkeletonBox(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f),
                            showShimmer = showShimmer
                        )
                        SkeletonBox(
                            modifier = Modifier.fillMaxWidth(0.9f),
                            height = 14.dp,
                            showShimmer = showShimmer
                        )
                        SkeletonBox(
                            modifier = Modifier.fillMaxWidth(0.6f),
                            height = 12.dp,
                            showShimmer = showShimmer
                        )
                    }
                }
            }
        }
    }
}