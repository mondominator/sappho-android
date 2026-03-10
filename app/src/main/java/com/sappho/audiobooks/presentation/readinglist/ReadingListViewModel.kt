package com.sappho.audiobooks.presentation.readinglist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sappho.audiobooks.data.remote.ReorderFavoritesRequest
import com.sappho.audiobooks.data.remote.SapphoApi
import com.sappho.audiobooks.data.repository.AuthRepository
import com.sappho.audiobooks.domain.model.Audiobook
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ReadingListViewModel @Inject constructor(
    private val api: SapphoApi,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _books = MutableStateFlow<List<Audiobook>>(emptyList())
    val books: StateFlow<List<Audiobook>> = _books

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _serverUrl = MutableStateFlow<String?>(null)
    val serverUrl: StateFlow<String?> = _serverUrl

    private val _sortOption = MutableStateFlow("custom")
    val sortOption: StateFlow<String> = _sortOption

    init {
        _serverUrl.value = authRepository.getServerUrlSync()
        loadReadingList()
    }

    fun loadReadingList() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val response = api.getFavorites(sort = _sortOption.value)
                if (response.isSuccessful) {
                    _books.value = response.body() ?: emptyList()
                }
            } catch (e: Exception) {
                // Silently handle network errors
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun setSortOption(sort: String) {
        _sortOption.value = sort
        loadReadingList()
    }

    fun reorderBooks(fromIndex: Int, toIndex: Int) {
        if (fromIndex == toIndex) return

        val current = _books.value.toMutableList()
        val item = current.removeAt(fromIndex)
        current.add(toIndex, item)
        _books.value = current

        viewModelScope.launch {
            try {
                api.reorderFavorites(ReorderFavoritesRequest(current.map { it.id }))
            } catch (e: Exception) {
                // Silently handle sync failure; local state already updated optimistically
            }
        }
    }

    fun removeBook(audiobookId: Int) {
        val previous = _books.value
        _books.value = previous.filter { it.id != audiobookId }

        viewModelScope.launch {
            try {
                api.removeFavorite(audiobookId)
            } catch (e: Exception) {
                // Revert on failure by reloading from server
                loadReadingList()
            }
        }
    }

    fun refresh() {
        loadReadingList()
    }
}
