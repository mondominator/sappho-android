package com.sappho.audiobooks.presentation.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sappho.audiobooks.data.remote.NotificationItem
import com.sappho.audiobooks.presentation.theme.*
import com.sappho.audiobooks.util.formatRelativeTime
import org.json.JSONObject

@Composable
fun NotificationPanel(
    notifications: List<NotificationItem>,
    onDismiss: () -> Unit,
    onMarkAllRead: () -> Unit,
    onNotificationClick: (NotificationItem) -> Unit
) {
    Box(
        modifier = Modifier
            .width(320.dp)
            .heightIn(max = 480.dp)
            .background(
                color = SapphoSurface,
                shape = RoundedCornerShape(8.dp)
            )
            .border(
                width = 1.dp,
                color = SapphoSurfaceBorder,
                shape = RoundedCornerShape(8.dp)
            )
    ) {
        Column {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Notifications",
                    color = SapphoTextLight,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                if (notifications.any { it.isRead == 0 }) {
                    Text(
                        text = "Mark all read",
                        color = SapphoInfo,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.clickable(onClick = onMarkAllRead)
                    )
                }
            }

            // Divider
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(SapphoSurfaceBorder)
            )

            if (notifications.isEmpty()) {
                // Empty state
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Notifications,
                            contentDescription = null,
                            tint = SapphoTextMuted,
                            modifier = Modifier.size(32.dp)
                        )
                        Text(
                            text = "No notifications yet",
                            color = SapphoTextMuted,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            } else {
                // Notification list
                LazyColumn(
                    modifier = Modifier.heightIn(max = 400.dp)
                ) {
                    items(notifications) { notification ->
                        NotificationRow(
                            notification = notification,
                            onClick = { onNotificationClick(notification) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationRow(
    notification: NotificationItem,
    onClick: () -> Unit
) {
    val isUnread = notification.isRead == 0
    val backgroundColor = if (isUnread) SapphoSurfaceLight else SapphoSurface
    val textColor = if (isUnread) SapphoTextLight else SapphoTextMuted

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(backgroundColor)
            .then(
                if (isUnread) {
                    Modifier.drawWithContent {
                        drawContent()
                        drawRect(
                            color = SapphoInfo,
                            size = Size(3.dp.toPx(), size.height)
                        )
                    }
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Type icon
        val icon = when {
            notification.type.contains("collection", ignoreCase = true) ->
                Icons.Outlined.Folder
            else -> Icons.AutoMirrored.Outlined.LibraryBooks
        }
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isUnread) SapphoInfo else SapphoIconDefault,
            modifier = Modifier
                .size(20.dp)
                .padding(top = 2.dp)
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = notification.message,
                color = textColor,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = formatRelativeTime(notification.createdAt),
                color = SapphoTextMuted,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

/**
 * Parse the metadata JSON string from a notification to extract navigation targets.
 * Returns a Pair of (audiobookId, collectionId) where -1 means not present.
 */
fun parseNotificationMetadata(metadata: String?): Pair<Int, Int> {
    if (metadata.isNullOrBlank()) return Pair(-1, -1)
    return try {
        val json = JSONObject(metadata)
        val audiobookId = json.optInt("audiobook_id", -1)
        val collectionId = json.optInt("collection_id", -1)
        Pair(audiobookId, collectionId)
    } catch (_: Exception) {
        Pair(-1, -1)
    }
}
