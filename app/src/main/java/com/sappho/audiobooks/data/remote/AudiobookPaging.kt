package com.sappho.audiobooks.data.remote

import com.sappho.audiobooks.domain.model.Audiobook

/** Every book matching the request, plus the server's count of them. */
data class AudiobookCatalog(val books: List<Audiobook>, val total: Int)

/** Page size for [fetchAllAudiobooks]; the server clamps `limit` to 2000. */
const val AUDIOBOOK_PAGE_SIZE = 1000

// Hard stop so a misbehaving server can't keep us paging forever (100k books).
private const val MAX_PAGES = 100

/**
 * Loads the whole library a page at a time. One request with a huge `limit`
 * comes back clamped to 2000 books, so a bigger library (e.g. with linked
 * servers) was silently cut short.
 *
 * Stops on an empty page, once `total` books have been read, or when a page
 * adds nothing new (a server that ignores `offset`). A server that doesn't
 * report `total` is read as a single page, as before. Books are de-duplicated
 * by id. Returns null when any page fails, so callers keep what they had.
 */
suspend fun fetchAllAudiobooks(
    api: SapphoApi,
    source: String? = null,
    pageSize: Int = AUDIOBOOK_PAGE_SIZE
): AudiobookCatalog? {
    val byId = LinkedHashMap<Int, Audiobook>()
    var offset = 0
    var total: Int? = null
    var pages = 0

    while (pages++ < MAX_PAGES) {
        val response = api.getAudiobooks(limit = pageSize, source = source, offset = offset)
        if (!response.isSuccessful) return null
        val body = response.body() ?: return null
        val page = body.audiobooks
        // Latest count wins in case the library changes while we page.
        total = body.total ?: total

        val before = byId.size
        page.forEach { byId[it.id] = it }
        offset += page.size

        val serverTotal = total
        val done = page.isEmpty() ||
            byId.size == before ||
            serverTotal == null ||
            offset >= serverTotal
        if (done) break
    }

    return AudiobookCatalog(byId.values.toList(), total ?: byId.size)
}
