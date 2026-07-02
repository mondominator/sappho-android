package com.sappho.audiobooks.presentation.components

import com.google.common.truth.Truth.assertThat
import com.sappho.audiobooks.data.remote.AddToCollectionRequest
import com.sappho.audiobooks.data.remote.Collection
import com.sappho.audiobooks.data.remote.CollectionForBook
import com.sappho.audiobooks.data.remote.SapphoApi
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Test
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class BookCollectionsControllerTest {

    private val api: SapphoApi = mockk()

    private fun collection(id: Int, name: String = "Collection $id") = Collection(
        id = id,
        name = name,
        description = null,
        userId = 1,
        bookCount = 0,
        firstCover = null,
        bookIds = null,
        isPublic = 0,
        isOwner = 1,
        creatorUsername = null,
        createdAt = null,
        updatedAt = null
    )

    private fun errorResponse(): Response<Unit> = Response.error(
        500,
        "{\"error\":\"boom\"}".toResponseBody("application/json".toMediaType())
    )

    @Test
    fun `should load collections and membership for book`() = runTest {
        // Given
        val controller = BookCollectionsController(api, this, "Test")
        coEvery { api.getCollections() } returns Response.success(listOf(collection(1), collection(2)))
        coEvery { api.getCollectionsForBook(42) } returns Response.success(
            listOf(
                CollectionForBook(id = 1, name = "Collection 1", containsBook = 1),
                CollectionForBook(id = 2, name = "Collection 2", containsBook = 0)
            )
        )

        // When
        controller.loadCollectionsForBook(42)
        advanceUntilIdle()

        // Then
        assertThat(controller.collections.value.map { it.id }).containsExactly(1, 2)
        assertThat(controller.bookCollections.value).containsExactly(1)
        assertThat(controller.isLoadingCollections.value).isFalse()
    }

    @Test
    fun `should clear loading flag when load fails`() = runTest {
        // Given
        val controller = BookCollectionsController(api, this, "Test")
        coEvery { api.getCollections() } throws RuntimeException("network down")

        // When
        controller.loadCollectionsForBook(42)
        advanceUntilIdle()

        // Then
        assertThat(controller.isLoadingCollections.value).isFalse()
        assertThat(controller.collections.value).isEmpty()
    }

    @Test
    fun `should add book to collection when not a member`() = runTest {
        // Given
        val controller = BookCollectionsController(api, this, "Test")
        coEvery { api.addToCollection(5, AddToCollectionRequest(42)) } returns
            Response.success(Unit)

        // When
        controller.toggleBookInCollection(5, 42)
        advanceUntilIdle()

        // Then
        assertThat(controller.bookCollections.value).containsExactly(5)
        coVerify(exactly = 1) { api.addToCollection(5, AddToCollectionRequest(42)) }
    }

    @Test
    fun `should remove book from collection when already a member`() = runTest {
        // Given - make book a member of collection 5 first
        val controller = BookCollectionsController(api, this, "Test")
        coEvery { api.addToCollection(5, AddToCollectionRequest(42)) } returns Response.success(Unit)
        controller.toggleBookInCollection(5, 42)
        advanceUntilIdle()
        coEvery { api.removeFromCollection(5, 42) } returns Response.success(Unit)

        // When
        controller.toggleBookInCollection(5, 42)
        advanceUntilIdle()

        // Then
        assertThat(controller.bookCollections.value).isEmpty()
        coVerify(exactly = 1) { api.removeFromCollection(5, 42) }
    }

    @Test
    fun `should not update membership when toggle request fails`() = runTest {
        // Given
        val controller = BookCollectionsController(api, this, "Test")
        coEvery { api.addToCollection(5, AddToCollectionRequest(42)) } returns errorResponse()

        // When
        controller.toggleBookInCollection(5, 42)
        advanceUntilIdle()

        // Then
        assertThat(controller.bookCollections.value).isEmpty()
    }

    @Test
    fun `should create collection and add book to it`() = runTest {
        // Given
        val controller = BookCollectionsController(api, this, "Test")
        val created = collection(9, "New Collection")
        coEvery { api.createCollection(any()) } returns Response.success(created)
        coEvery { api.addToCollection(9, AddToCollectionRequest(42)) } returns Response.success(Unit)

        // When
        controller.createCollectionAndAddBook("New Collection", 42)
        advanceUntilIdle()

        // Then
        assertThat(controller.collections.value.first().id).isEqualTo(9)
        assertThat(controller.bookCollections.value).containsExactly(9)
    }

    @Test
    fun `should leave state unchanged when create collection fails`() = runTest {
        // Given
        val controller = BookCollectionsController(api, this, "Test")
        coEvery { api.createCollection(any()) } throws RuntimeException("boom")

        // When
        controller.createCollectionAndAddBook("New Collection", 42)
        advanceUntilIdle()

        // Then
        assertThat(controller.collections.value).isEmpty()
        assertThat(controller.bookCollections.value).isEmpty()
    }
}
