package com.sappho.audiobooks.presentation.home

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp
import com.sappho.audiobooks.presentation.theme.*

@Composable
fun DownloadStatusBanner(
    activeDownloads: List<com.sappho.audiobooks.download.DownloadState>,
    failedDownloads: List<com.sappho.audiobooks.download.DownloadState>,
    onClearFailedDownloads: () -> Unit
) {
    // Show active downloads with highest priority
    if (activeDownloads.isNotEmpty()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.M, vertical = Spacing.XS)
                .background(
                    SapphoInfo.copy(alpha = 0.2f),
                    RoundedCornerShape(8.dp)
                )
                .padding(Spacing.S),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Animated rotating download icon
            val rotation by rememberInfiniteTransition(label = "downloadRotation").animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(2000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ), label = "downloadRotation"
            )
            
            Icon(
                imageVector = Icons.Default.Download,
                contentDescription = SapphoAccessibility.ContentDescriptions.DOWNLOAD_PROGRESS,
                tint = SapphoInfo,
                modifier = Modifier
                    .size(IconSize.Medium)
                    .rotate(rotation)
            )
            Spacer(modifier = Modifier.width(Spacing.XS))
            
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = if (activeDownloads.size == 1) "Downloading audiobook" else "Downloading ${activeDownloads.size} audiobooks",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SapphoInfo
                )
                
                // Show progress of first active download
                activeDownloads.firstOrNull()?.let { download ->
                    val progressPercent = (download.progress * 100).toInt()
                    Text(
                        text = if (progressPercent > 0) "$progressPercent% complete" else "Starting download...",
                        style = MaterialTheme.typography.bodySmall,
                        color = LegacyBluePale
                    )
                }
            }
        }
    } 
    // Show failed downloads if no active downloads
    else if (failedDownloads.isNotEmpty()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.M, vertical = Spacing.XS)
                .background(
                    SapphoError.copy(alpha = 0.2f),
                    RoundedCornerShape(8.dp)
                )
                .padding(Spacing.S),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = "Download failed",
                    tint = SapphoError,
                    modifier = Modifier.size(IconSize.Medium)
                )
                Spacer(modifier = Modifier.width(Spacing.XS))
                
                Column {
                    Text(
                        text = if (failedDownloads.size == 1) "Download failed" else "${failedDownloads.size} downloads failed",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SapphoError
                    )
                    Text(
                        text = "Tap audiobook to retry",
                        style = MaterialTheme.typography.bodySmall,
                        color = LegacyRedLight
                    )
                }
            }
            
            TextButton(
                onClick = onClearFailedDownloads,
                colors = ButtonDefaults.textButtonColors(contentColor = SapphoError),
                contentPadding = PaddingValues(horizontal = Spacing.S, vertical = Spacing.XXS)
            ) {
                Text("Clear", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}