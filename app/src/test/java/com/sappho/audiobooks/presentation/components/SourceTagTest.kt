package com.sappho.audiobooks.presentation.components

import com.google.common.truth.Truth.assertThat
import com.sappho.audiobooks.domain.model.Audiobook
import com.sappho.audiobooks.domain.model.BookSource
import org.junit.Test

class SourceTagTest {

    private fun book(source: BookSource? = null, available: Boolean? = null) = Audiobook(
        id = 1, title = "Dune", author = null, narrator = null, series = null,
        seriesPosition = null, duration = null, genre = null, publishYear = null,
        isbn = null, asin = null, description = null, coverImage = null, progress = null,
        source = source, available = available
    )

    @Test
    fun `local books get no tag and no source line`() {
        assertThat(sourceTagLabel(book())).isNull()
        assertThat(sourceTagLabel(book(available = false))).isNull()
        assertThat(sourceLineText(book())).isNull()
    }

    @Test
    fun `remote book is tagged with its server name`() {
        val remote = book(BookSource(4, "Robert"), available = true)

        assertThat(sourceTagLabel(remote)).isEqualTo("Robert")
        assertThat(sourceLineText(remote)).isEqualTo("From Robert's library")
    }

    @Test
    fun `offline remote book says so`() {
        val offline = book(BookSource(4, "Robert"), available = false)

        assertThat(sourceTagLabel(offline)).isEqualTo("Robert · offline")
        assertThat(sourceLineText(offline)).startsWith("From Robert's library · offline")
    }
}
