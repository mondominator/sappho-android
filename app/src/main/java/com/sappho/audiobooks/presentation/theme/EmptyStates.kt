package com.sappho.audiobooks.presentation.theme

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

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
