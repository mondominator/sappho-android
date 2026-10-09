package com.sappho.audiobooks.data.remote

import com.google.common.truth.Truth.assertThat
import com.sappho.audiobooks.domain.model.Audiobook
import com.sappho.audiobooks.domain.model.AudiobooksResponse
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Test
import retrofit2.Response

class AudiobookPagingTest {

    private val api: SapphoApi = mockk(relaxed = true)

    private fun book(id: Int) = Audiobook(
        id = id, title = "Book $id", author = null, narrator = null, series = null,
        seriesPosition = null, duration = null, genre = null, publishYear = null, isbn = null,
        asin = null, description = null, coverImage = null, progress = null
    )

    /**
     * Behaves like GET /api/audiobooks: clamps `limit` to [clamp], honours
     * `offset`, and reports `total` unless [sendTotal] is false (older servers).
     */
    private fun serveLibrary(size: Int, clamp: Int = 2000, sendTotal: Boolean = true) {
        val library = (1..size).map(::book)
        coEvery { api.getAudiobooks(any(), any(), any(), any(), any(), any()) } answers {
            val limit = minOf((arg<Int?>(3) ?: 50).coerceAtLeast(1), clamp)
            val offset = arg<Int?>(5) ?: 0
            Response.success(
                AudiobooksResponse(
                    audiobooks = library.drop(offset).take(limit),
                    total = if (sendTotal) library.size else null
                )
            )
        }
    }

    @Test
    fun `reads every page of a library bigger than the server clamp`() = runTest {
        serveLibrary(size = 2364)

        val catalog = fetchAllAudiobooks(api)!!

        assertThat(catalog.books).hasSize(2364)
        assertThat(catalog.books.map { it.id }).isEqualTo((1..2364).toList())
        assertThat(catalog.total).isEqualTo(2364)
        coVerify(exactly = 1) { api.getAudiobooks(limit = 1000, offset = 0) }
        coVerify(exactly = 1) { api.getAudiobooks(limit = 1000, offset = 1000) }
        coVerify(exactly = 1) { api.getAudiobooks(limit = 1000, offset = 2000) }
        coVerify(exactly = 3) { api.getAudiobooks(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `two pages and a total stop without an extra request`() = runTest {
        serveLibrary(size = 4)

        val catalog = fetchAllAudiobooks(api, pageSize = 2)!!

        assertThat(catalog.books.map { it.id }).containsExactly(1, 2, 3, 4).inOrder()
        assertThat(catalog.total).isEqualTo(4)
        coVerify(exactly = 2) { api.getAudiobooks(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `passes the source through on every page`() = runTest {
        serveLibrary(size = 3)

        fetchAllAudiobooks(api, source = "4", pageSize = 2)

        coVerify(exactly = 1) { api.getAudiobooks(limit = 2, source = "4", offset = 0) }
        coVerify(exactly = 1) { api.getAudiobooks(limit = 2, source = "4", offset = 2) }
    }

    @Test
    fun `books repeated across pages are kept once`() = runTest {
        // A book added mid-paging shifts the window: page 2 repeats book 2.
        coEvery { api.getAudiobooks(limit = 2, offset = 0) } returns
            Response.success(AudiobooksResponse(listOf(book(1), book(2)), total = 3))
        coEvery { api.getAudiobooks(limit = 2, offset = 2) } returns
            Response.success(AudiobooksResponse(listOf(book(2), book(3)), total = 3))

        val catalog = fetchAllAudiobooks(api, pageSize = 2)!!

        assertThat(catalog.books.map { it.id }).containsExactly(1, 2, 3).inOrder()
    }

    @Test
    fun `an empty page stops paging even when total says there is more`() = runTest {
        coEvery { api.getAudiobooks(limit = 2, offset = 0) } returns
            Response.success(AudiobooksResponse(listOf(book(1), book(2)), total = 10))
        coEvery { api.getAudiobooks(limit = 2, offset = 2) } returns
            Response.success(AudiobooksResponse(emptyList(), total = 10))

        val catalog = fetchAllAudiobooks(api, pageSize = 2)!!

        assertThat(catalog.books.map { it.id }).containsExactly(1, 2)
        coVerify(exactly = 2) { api.getAudiobooks(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `a server that ignores offset is read once, not forever`() = runTest {
        coEvery { api.getAudiobooks(any(), any(), any(), any(), any(), any()) } returns
            Response.success(AudiobooksResponse(listOf(book(1), book(2)), total = 10))

        val catalog = fetchAllAudiobooks(api, pageSize = 2)!!

        assertThat(catalog.books.map { it.id }).containsExactly(1, 2)
        coVerify(exactly = 2) { api.getAudiobooks(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `an older server without total is read as a single page`() = runTest {
        serveLibrary(size = 5, sendTotal = false)

        val catalog = fetchAllAudiobooks(api, pageSize = 3)!!

        assertThat(catalog.books.map { it.id }).containsExactly(1, 2, 3)
        assertThat(catalog.total).isEqualTo(3)
        coVerify(exactly = 1) { api.getAudiobooks(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `a failed page fails the whole load`() = runTest {
        coEvery { api.getAudiobooks(limit = 2, offset = 0) } returns
            Response.success(AudiobooksResponse(listOf(book(1), book(2)), total = 4))
        coEvery { api.getAudiobooks(limit = 2, offset = 2) } returns
            Response.error(500, "".toResponseBody())

        assertThat(fetchAllAudiobooks(api, pageSize = 2)).isNull()
    }
}
