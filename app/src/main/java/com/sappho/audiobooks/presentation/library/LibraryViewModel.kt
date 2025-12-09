package com.sappho.audiobooks.presentation.library

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sappho.audiobooks.data.remote.SapphoApi
import com.sappho.audiobooks.data.repository.UserPreferencesRepository
import com.sappho.audiobooks.domain.model.AuthorInfo
import com.sappho.audiobooks.domain.model.GenreCategoryData
import com.sappho.audiobooks.domain.model.GenreInfo
import com.sappho.audiobooks.domain.model.GenreMetadata
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
    private val authRepository: com.sappho.audiobooks.data.repository.AuthRepository,
    val userPreferences: UserPreferencesRepository
) : ViewModel() {

    companion object {
        // Genre mappings fetched from server, cached here (includes keywords, colors, icons)
        private var genreCategories: Map<String, GenreCategoryData> = emptyMap()
        private var defaultGenreMetadata: GenreMetadata = GenreMetadata(
            colors = listOf("#10b981", "#059669"),
            icon = "category"
        )

        fun normalizeGenre(genre: String): String {
            if (genreCategories.isEmpty()) return genre

            val lowerGenre = genre.lowercase()

            // Try to find a matching category
            for ((category, data) in genreCategories) {
                for (keyword in data.keywords) {
                    if (lowerGenre == keyword || lowerGenre.contains(keyword)) {
                        return category
                    }
                }
            }

            return genre // Return original if no match
        }

        fun getAllNormalizedGenres(genre: String): List<String> {
            if (genreCategories.isEmpty()) return listOf(genre)

            val parts = genre.split(",", ";", "&", " and ")
                .map { it.trim().lowercase() }
                .filter { it.isNotEmpty() }

            val normalized = mutableSetOf<String>()
            for (part in parts) {
                for ((category, data) in genreCategories) {
                    for (keyword in data.keywords) {
                        if (part == keyword || part.contains(keyword)) {
                            normalized.add(category)
                            break
                        }
                    }
                }
            }

            return if (normalized.isEmpty()) listOf(genre) else normalized.toList()
        }

        /**
         * Get colors for a genre category (returns hex color strings)
         */
        fun getGenreColors(genre: String): List<String> {
            return genreCategories[genre]?.colors ?: defaultGenreMetadata.colors
        }

        /**
         * Get icon name for a genre category
         */
        fun getGenreIcon(genre: String): String {
            return genreCategories[genre]?.icon ?: defaultGenreMetadata.icon
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

    private val _aiConfigured = MutableStateFlow(false)
    val aiConfigured: StateFlow<Boolean> = _aiConfigured.asStateFlow()

    init {
        _serverUrl.value = authRepository.getServerUrlSync()
        loadCategories()
        checkAiStatus()
    }

    private fun checkAiStatus() {
        viewModelScope.launch {
            try {
                val response = api.getAiStatus()
                if (response.isSuccessful) {
                    _aiConfigured.value = response.body()?.configured ?: false
                    Log.d("LibraryViewModel", "AI configured: ${_aiConfigured.value}")
                }
            } catch (e: Exception) {
                Log.e("LibraryViewModel", "Error checking AI status", e)
                _aiConfigured.value = false
            }
        }
    }

    suspend fun getSeriesRecap(seriesName: String): Result<com.sappho.audiobooks.data.remote.SeriesRecapResponse> {
        return try {
            // URLEncoder encodes spaces as '+', but we need '%20' for URL paths
            val encodedName = java.net.URLEncoder.encode(seriesName, "UTF-8").replace("+", "%20")
            val response = api.getSeriesRecap(encodedName)
            if (response.isSuccessful) {
                response.body()?.let { Result.success(it) }
                    ?: Result.failure(Exception("Empty response"))
            } else {
                val errorBody = response.errorBody()?.string()
                Result.failure(Exception(errorBody ?: "Failed to get recap"))
            }
        } catch (e: Exception) {
            Log.e("LibraryViewModel", "Error getting series recap", e)
            Result.failure(e)
        }
    }

    suspend fun clearSeriesRecap(seriesName: String): Result<Unit> {
        return try {
            // URLEncoder encodes spaces as '+', but we need '%20' for URL paths
            val encodedName = java.net.URLEncoder.encode(seriesName, "UTF-8").replace("+", "%20")
            val response = api.clearSeriesRecap(encodedName)
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to clear recap cache"))
            }
        } catch (e: Exception) {
            Log.e("LibraryViewModel", "Error clearing series recap", e)
            Result.failure(e)
        }
    }

    fun refresh() {
        loadCategories()
    }

    fun loadCategories() {
        viewModelScope.launch {
            _uiState.value = LibraryUiState.Loading
            try {
                Log.d("LibraryViewModel", "Loading categories...")

                // Load genre mappings from server first (includes keywords, colors, icons)
                val mappingsResponse = api.getGenreMappings()
                if (mappingsResponse.isSuccessful) {
                    val response = mappingsResponse.body()
                    if (response != null) {
                        genreCategories = response.genres
                        defaultGenreMetadata = response.defaults
                        Log.d("LibraryViewModel", "Loaded ${genreCategories.size} genre categories with colors/icons from server")
                    }
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
