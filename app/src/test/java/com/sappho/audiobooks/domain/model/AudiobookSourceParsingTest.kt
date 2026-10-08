package com.sappho.audiobooks.domain.model

import com.google.common.truth.Truth.assertThat
import com.google.gson.reflect.TypeToken
import com.sappho.audiobooks.di.NetworkModule
import org.junit.Test

/**
 * Linked servers (server 0.16.0) add `source` and `available` to every book.
 * Older servers send neither, so both must parse as "local and playable".
 * Parsed with the app's own Gson, which bypasses Kotlin default values.
 */
class AudiobookSourceParsingTest {

    private val gson = NetworkModule.provideGson()

    private fun parse(extraFields: String): Audiobook = gson.fromJson(
        """{"id": 42, "title": "Dune", "author": "Frank Herbert", "narrator": null,
            "series": null, "series_position": null, "duration": 3600, "genre": null,
            "published_year": null, "isbn": null, "asin": null, "description": null,
            "cover_image": null, "progress": null $extraFields}""",
        Audiobook::class.java
    )

    @Test
    fun `book from an older server without the fields is local and playable`() {
        val book = parse("")

        assertThat(book.source).isNull()
        assertThat(book.available).isNull()
        assertThat(book.isRemote).isFalse()
        assertThat(book.isRemoteOffline).isFalse()
    }

    @Test
    fun `local book with source null is not remote`() {
        val book = parse(""", "source": null, "available": true""")

        assertThat(book.source).isNull()
        assertThat(book.available).isTrue()
        assertThat(book.isRemote).isFalse()
        assertThat(book.isRemoteOffline).isFalse()
    }

    @Test
    fun `remote book parses its source`() {
        val book = parse(""", "source": {"id": 4, "name": "Robert"}, "available": true""")

        assertThat(book.source).isEqualTo(BookSource(id = 4, name = "Robert"))
        assertThat(book.source?.displayName).isEqualTo("Robert")
        assertThat(book.isRemote).isTrue()
        assertThat(book.isRemoteOffline).isFalse()
    }

    @Test
    fun `remote book whose server is down is offline`() {
        val book = parse(""", "source": {"id": 4, "name": "Robert"}, "available": false""")

        assertThat(book.isRemoteOffline).isTrue()
    }

    @Test
    fun `local book with a missing file is not shown as a remote offline book`() {
        val book = parse(""", "source": null, "available": false""")

        assertThat(book.isRemote).isFalse()
        assertThat(book.isRemoteOffline).isFalse()
    }

    @Test
    fun `remote book without available is not treated as offline`() {
        val book = parse(""", "source": {"id": 4, "name": "Robert"}""")

        assertThat(book.isRemote).isTrue()
        assertThat(book.isRemoteOffline).isFalse()
    }

    @Test
    fun `source without a name falls back to a generic label`() {
        val book = parse(""", "source": {"id": 4}""")

        assertThat(book.source?.displayName).isEqualTo("Linked server")
    }

    @Test
    fun `remote book file path and integer id parse like a local book`() {
        val book = parse(""", "file_path": "sappho-remote://4/913", "source": {"id": 4, "name": "Robert"}""")

        assertThat(book.id).isEqualTo(42)
        assertThat(book.filePath).isEqualTo("sappho-remote://4/913")
    }

    @Test
    fun `book list response parses mixed local and remote books`() {
        val response = gson.fromJson(
            """{"audiobooks": [
                {"id": 1, "title": "Local", "source": null, "available": true},
                {"id": 2, "title": "Remote", "source": {"id": 4, "name": "Robert"}, "available": false}
            ]}""",
            AudiobooksResponse::class.java
        )

        assertThat(response.audiobooks.map { it.isRemote }).containsExactly(false, true).inOrder()
        assertThat(response.audiobooks[1].isRemoteOffline).isTrue()
    }

    @Test
    fun `linked sources list parses`() {
        val type = object : TypeToken<List<LinkedSource>>() {}.type
        val sources: List<LinkedSource> = gson.fromJson(
            """[{"id": 4, "name": "Robert", "available": true}, {"id": 7, "name": "", "available": false}]""",
            type
        )

        assertThat(sources).containsExactly(
            LinkedSource(id = 4, name = "Robert", available = true),
            LinkedSource(id = 7, name = "", available = false)
        ).inOrder()
        assertThat(sources[1].displayName).isEqualTo("Linked server")
    }

    @Test
    fun `serializing a book does not write the derived properties`() {
        val json = gson.toJson(parse(""", "source": {"id": 4, "name": "Robert"}, "available": false"""))

        assertThat(json).doesNotContain("isRemote")
        assertThat(json).contains("\"source\":{\"id\":4,\"name\":\"Robert\"}")
    }
}
