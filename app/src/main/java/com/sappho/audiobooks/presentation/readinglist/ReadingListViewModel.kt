package com.sappho.audiobooks.presentation.readinglist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

    init {
        _serverUrl.value = authRepository.getServerUrlSync()
        loadReadingList()
    }

    fun loadReadingList() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val response = api.getFavorites()
                if (response.isSuccessful) {
                    _books.value = response.body() ?: emptyList()
                }
            } catch (e: Exception) {
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun refresh() {
        loadReadingList()
    }
}
