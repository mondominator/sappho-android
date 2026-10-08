package com.sappho.audiobooks.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sappho.audiobooks.domain.model.Audiobook
import com.sappho.audiobooks.presentation.theme.SapphoTextLight
import com.sappho.audiobooks.presentation.theme.SapphoTextMuted
import com.sappho.audiobooks.presentation.theme.SapphoWarning

/** Opacity of a remote book whose linked server is offline. */
private const val OFFLINE_ALPHA = 0.45f
private const val TAG_BACKGROUND_ALPHA = 0.6f
private const val COVER_TAG_MAX_WIDTH_FRACTION = 0.65f

/** The tag text: the linked server's name, plus "offline" when it can't be reached. */
fun sourceTagLabel(book: Audiobook): String? {
    val source = book.source ?: return null
    return if (book.isRemoteOffline) "${source.displayName} · offline" else source.displayName
}

/**
 * A small, quiet chip naming the linked server a remote book comes from.
 * Local books get nothing.
 */
@Composable
fun BookSourceTag(book: Audiobook, modifier: Modifier = Modifier) {
    val label = sourceTagLabel(book) ?: return
    Box(
        modifier = modifier
            .widthIn(max = 140.dp)
            .background(Color.Black.copy(alpha = TAG_BACKGROUND_ALPHA), RoundedCornerShape(4.dp))
            .padding(horizontal = 5.dp, vertical = 1.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = SapphoTextLight,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * [BookSourceTag] pinned to the bottom-left of a cover, clear of the
 * progress bar that cards draw along the bottom edge.
 */
@Composable
fun BoxScope.CoverSourceTag(book: Audiobook) {
    if (!book.isRemote) return
    // Capped to part of the cover so it never runs into a rating badge on the right.
    Box(
        modifier = Modifier
            .align(Alignment.BottomStart)
            .fillMaxWidth(COVER_TAG_MAX_WIDTH_FRACTION)
            .padding(start = 4.dp, bottom = 8.dp)
    ) {
        BookSourceTag(book = book)
    }
}

/** The detail-screen line for a remote book, e.g. "From Robert's library". */
fun sourceLineText(book: Audiobook): String? {
    val source = book.source ?: return null
    val line = "From ${source.displayName}'s library"
    return if (book.isRemoteOffline) "$line · offline, can't play right now" else line
}

/** "From Robert's library" under the cover of a remote book; nothing for local books. */
@Composable
fun BookSourceLine(book: Audiobook, modifier: Modifier = Modifier) {
    val text = sourceLineText(book) ?: return
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = if (book.isRemoteOffline) SapphoWarning else SapphoTextMuted,
        textAlign = TextAlign.Center,
        modifier = modifier
    )
}

/** Dims a remote book whose linked server is offline. */
fun Modifier.dimIfRemoteOffline(book: Audiobook): Modifier =
    if (book.isRemoteOffline) this.alpha(OFFLINE_ALPHA) else this
