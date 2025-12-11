package com.sappho.audiobooks.presentation.collections

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sappho.audiobooks.data.remote.CollectionDetail
import com.sappho.audiobooks.data.remote.SapphoApi
import com.sappho.audiobooks.data.remote.UpdateCollectionRequest
import com.sappho.audiobooks.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CollectionDetailViewModel @Inject constructor(
    private val api: SapphoApi,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _collection = MutableStateFlow<CollectionDetail?>(null)
    val collection: StateFlow<CollectionDetail?> = _collection

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _serverUrl = MutableStateFlow<String?>(null)
    val serverUrl: StateFlow<String?> = _serverUrl

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving

    init {
        _serverUrl.value = authRepository.getServerUrlSync()
    }

    fun loadCollection(collectionId: Int) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val response = api.getCollection(collectionId)
                if (response.isSuccessful) {
                    _collection.value = response.body()
                } else {
                    _error.value = "Failed to load collection"
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _error.value = e.message ?: "Network error"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun updateCollection(
        collectionId: Int,
        name: String,
        description: String?,
        isPublic: Boolean? = null,
        onResult: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            _isSaving.value = true
            try {
                val response = api.updateCollection(
                    collectionId,
                    UpdateCollectionRequest(name, description, isPublic)
                )
                if (response.isSuccessful) {
                    loadCollection(collectionId)
                    onResult(true, "Collection updated")
                } else {
                    onResult(false, "Failed to update collection")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                onResult(false, e.message ?: "Error updating collection")
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun removeBook(collectionId: Int, bookId: Int, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                val response = api.removeFromCollection(collectionId, bookId)
                if (response.isSuccessful) {
                    loadCollection(collectionId)
                    onResult(true, "Book removed")
                } else {
                    onResult(false, "Failed to remove book")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                onResult(false, e.message ?: "Error removing book")
            }
        }
    }

    fun refresh(collectionId: Int) {
        loadCollection(collectionId)
    }
}
