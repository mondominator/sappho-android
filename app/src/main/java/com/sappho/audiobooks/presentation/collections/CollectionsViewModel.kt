package com.sappho.audiobooks.presentation.collections

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sappho.audiobooks.data.remote.Collection
import com.sappho.audiobooks.data.remote.CreateCollectionRequest
import com.sappho.audiobooks.data.remote.SapphoApi
import com.sappho.audiobooks.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CollectionsViewModel @Inject constructor(
    private val api: SapphoApi,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _collections = MutableStateFlow<List<Collection>>(emptyList())
    val collections: StateFlow<List<Collection>> = _collections

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _serverUrl = MutableStateFlow<String?>(null)
    val serverUrl: StateFlow<String?> = _serverUrl

    private val _isCreating = MutableStateFlow(false)
    val isCreating: StateFlow<Boolean> = _isCreating

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    init {
        _serverUrl.value = authRepository.getServerUrlSync()
        loadCollections()
    }

    fun loadCollections() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val response = api.getCollections()
                if (response.isSuccessful) {
                    _collections.value = response.body() ?: emptyList()
                } else {
                    _error.value = "Failed to load collections"
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _error.value = e.message ?: "Network error"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun createCollection(name: String, description: String?, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            _isCreating.value = true
            try {
                val response = api.createCollection(CreateCollectionRequest(name, description))
                if (response.isSuccessful) {
                    loadCollections()
                    onResult(true, "Collection created")
                } else {
                    onResult(false, "Failed to create collection")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                onResult(false, e.message ?: "Error creating collection")
            } finally {
                _isCreating.value = false
            }
        }
    }

    fun deleteCollection(collectionId: Int, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                val response = api.deleteCollection(collectionId)
                if (response.isSuccessful) {
                    loadCollections()
                    onResult(true, "Collection deleted")
                } else {
                    onResult(false, "Failed to delete collection")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                onResult(false, e.message ?: "Error deleting collection")
            }
        }
    }

    fun refresh() {
        loadCollections()
    }
}
