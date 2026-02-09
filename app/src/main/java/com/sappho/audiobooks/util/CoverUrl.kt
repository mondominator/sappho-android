package com.sappho.audiobooks.util

/**
 * Build a cover image URL with optional thumbnail width.
 *
 * @param serverUrl  Base server URL (e.g. "https://sappho.example.com")
 * @param bookId     Audiobook ID
 * @param width      Optional thumbnail width (120, 300, or 600). Null for full-res.
 * @param cacheBust  Optional cache-bust value (e.g. cover version or updated_at)
 */
fun buildCoverUrl(
    serverUrl: String,
    bookId: Int,
    width: Int? = null,
    cacheBust: String? = null
): String {
    val base = "$serverUrl/api/audiobooks/$bookId/cover"
    val params = mutableListOf<String>()
    if (width != null) params.add("width=$width")
    if (cacheBust != null) params.add("v=$cacheBust")
    return if (params.isEmpty()) base else "$base?${params.joinToString("&")}"
}

/** Thumbnail width for grid/list views (cards, rows). */
const val COVER_WIDTH_THUMBNAIL = 300

/** Larger cover for detail screens. */
const val COVER_WIDTH_DETAIL = 600
