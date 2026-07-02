package com.sappho.audiobooks.presentation.components

import android.util.Log
import com.sappho.audiobooks.data.remote.AddToCollectionRequest
import com.sappho.audiobooks.data.remote.Collection
import com.sappho.audiobooks.data.remote.CreateCollectionRequest
import com.sappho.audiobooks.data.remote.SapphoApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Shared "add book to collection" state and actions used by the collection
 * picker sheet on both the Home and Audiobook Detail screens. ViewModels
 * instantiate this with their own [viewModelScope][androidx.lifecycle.viewModelScope]
 * and delegate to it, so the logic lives in exactly one place.
 */
class BookCollectionsController(
    private val api: SapphoApi,
    private val scope: CoroutineScope,
    private val tag: String
) {
    private val _collections = MutableStateFlow<List<Collection>>(emptyList())
    val collections: StateFlow<List<Collection>> = _collections

    private val _bookCollections = MutableStateFlow<Set<Int>>(emptySet())
    val bookCollections: StateFlow<Set<Int>> = _bookCollections

    private val _isLoadingCollections = MutableStateFlow(false)
    val isLoadingCollections: StateFlow<Boolean> = _isLoadingCollections

    fun loadCollectionsForBook(bookId: Int) {
        scope.launch {
            _isLoadingCollections.value = true
            try {
                val collectionsResponse = api.getCollections()
                val bookCollectionsResponse = api.getCollectionsForBook(bookId)

                if (collectionsResponse.isSuccessful) {
                    _collections.value = collectionsResponse.body() ?: emptyList()
                }
                if (bookCollectionsResponse.isSuccessful) {
                    _bookCollections.value = (bookCollectionsResponse.body() ?: emptyList())
                        .filter { it.containsBook == 1 }
                        .map { it.id }
                        .toSet()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(tag, "Failed to load collections for book", e)
            } finally {
                _isLoadingCollections.value = false
            }
        }
    }

    fun toggleBookInCollection(collectionId: Int, bookId: Int) {
        scope.launch {
            val isInCollection = _bookCollections.value.contains(collectionId)
            try {
                if (isInCollection) {
                    val response = api.removeFromCollection(collectionId, bookId)
                    if (response.isSuccessful) {
                        _bookCollections.value = _bookCollections.value - collectionId
                    }
                } else {
                    val response = api.addToCollection(collectionId, AddToCollectionRequest(bookId))
                    if (response.isSuccessful) {
                        _bookCollections.value = _bookCollections.value + collectionId
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(tag, "Failed to toggle book in collection", e)
            }
        }
    }

    fun createCollectionAndAddBook(name: String, bookId: Int, isPublic: Boolean = false) {
        scope.launch {
            try {
                val createResponse = api.createCollection(CreateCollectionRequest(name, null, isPublic))
                if (createResponse.isSuccessful) {
                    val newCollection = createResponse.body()
                    if (newCollection != null) {
                        val addResponse = api.addToCollection(newCollection.id, AddToCollectionRequest(bookId))
                        if (addResponse.isSuccessful) {
                            _collections.value = listOf(newCollection) + _collections.value
                            _bookCollections.value = _bookCollections.value + newCollection.id
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(tag, "Failed to create collection and add book", e)
            }
        }
    }
}
