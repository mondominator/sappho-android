package com.sappho.audiobooks.presentation.library

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sappho.audiobooks.data.remote.SapphoApi
import com.sappho.audiobooks.domain.model.AuthorInfo
import com.sappho.audiobooks.domain.model.GenreInfo
import com.sappho.audiobooks.domain.model.SeriesInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val api: SapphoApi,
    private val authRepository: com.sappho.audiobooks.data.repository.AuthRepository
) : ViewModel() {

    companion object {
        // Genre mappings fetched from server, cached here
        private var genreMappings: Map<String, List<String>> = emptyMap()

        fun normalizeGenre(genre: String): String {
            if (genreMappings.isEmpty()) return genre

            val lowerGenre = genre.lowercase()

            // Try to find a matching category
            for ((category, keywords) in genreMappings) {
                for (keyword in keywords) {
                    if (lowerGenre == keyword || lowerGenre.contains(keyword)) {
                        return category
                    }
                }
            }

            return genre // Return original if no match
        }

        fun getAllNormalizedGenres(genre: String): List<String> {
            if (genreMappings.isEmpty()) return listOf(genre)

            val parts = genre.split(",", ";", "&", " and ")
                .map { it.trim().lowercase() }
                .filter { it.isNotEmpty() }

            val normalized = mutableSetOf<String>()
            for (part in parts) {
                for ((category, keywords) in genreMappings) {
                    for (keyword in keywords) {
                        if (part == keyword || part.contains(keyword)) {
                            normalized.add(category)
                            break
                        }
                    }
                }
            }

            return if (normalized.isEmpty()) listOf(genre) else normalized.toList()
        }
    }

    private val _uiState = MutableStateFlow<LibraryUiState>(LibraryUiState.Loading)
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    private val _series = MutableStateFlow<List<SeriesInfo>>(emptyList())
    val series: StateFlow<List<SeriesInfo>> = _series.asStateFlow()

    private val _authors = MutableStateFlow<List<AuthorInfo>>(emptyList())
    val authors: StateFlow<List<AuthorInfo>> = _authors.asStateFlow()

    private val _genres = MutableStateFlow<List<GenreInfo>>(emptyList())
    val genres: StateFlow<List<GenreInfo>> = _genres.asStateFlow()

    private val _allAudiobooks = MutableStateFlow<List<com.sappho.audiobooks.domain.model.Audiobook>>(emptyList())
    val allAudiobooks: StateFlow<List<com.sappho.audiobooks.domain.model.Audiobook>> = _allAudiobooks.asStateFlow()

    private val _serverUrl = MutableStateFlow<String?>(null)
    val serverUrl: StateFlow<String?> = _serverUrl.asStateFlow()

    init {
        _serverUrl.value = authRepository.getServerUrlSync()
        loadCategories()
    }

    fun loadCategories() {
        viewModelScope.launch {
            _uiState.value = LibraryUiState.Loading
            try {
                Log.d("LibraryViewModel", "Loading categories...")

                // Load genre mappings from server first
                val mappingsResponse = api.getGenreMappings()
                if (mappingsResponse.isSuccessful) {
                    genreMappings = mappingsResponse.body() ?: emptyMap()
                    Log.d("LibraryViewModel", "Loaded ${genreMappings.size} genre categories from server")
                }

                // Load genres from server (already normalized)
                val genresResponse = api.getGenres()
                if (genresResponse.isSuccessful) {
                    _genres.value = genresResponse.body() ?: emptyList()
                    Log.d("LibraryViewModel", "Loaded ${_genres.value.size} genres from server")
                }

                // Load all audiobooks for series/authors
                val audiobooksResponse = api.getAudiobooks(limit = 10000)
                Log.d("LibraryViewModel", "Response: ${audiobooksResponse.isSuccessful}")

                if (audiobooksResponse.isSuccessful) {
                    val audiobooks = audiobooksResponse.body()?.audiobooks ?: emptyList()
                    Log.d("LibraryViewModel", "Got ${audiobooks.size} audiobooks")

                    // Store all audiobooks
                    _allAudiobooks.value = audiobooks

                    // Extract and count series (filter out series with only 1 book)
                    val seriesMap = audiobooks
                        .filter { !it.series.isNullOrBlank() }
                        .groupBy { it.series!! }
                        .filter { (_, books) -> books.size > 1 }
                        .map { (series, books) ->
                            SeriesInfo(series = series, bookCount = books.size)
                        }
                        .sortedBy { it.series }
                    _series.value = seriesMap
                    Log.d("LibraryViewModel", "Found ${seriesMap.size} series")

                    // Extract and count authors
                    val authorsMap = audiobooks
                        .filter { !it.author.isNullOrBlank() }
                        .groupBy { it.author!! }
                        .map { (author, books) ->
                            AuthorInfo(author = author, bookCount = books.size)
                        }
                        .sortedBy { it.author }
                    _authors.value = authorsMap
                    Log.d("LibraryViewModel", "Found ${authorsMap.size} authors")
                }

                _uiState.value = LibraryUiState.Success
            } catch (e: Exception) {
                Log.e("LibraryViewModel", "Error loading categories", e)
                _uiState.value = LibraryUiState.Error(e.message ?: "Unknown error")
            }
        }
    }
}

sealed class LibraryUiState {
    object Loading : LibraryUiState()
    object Success : LibraryUiState()
    data class Error(val message: String) : LibraryUiState()
}
