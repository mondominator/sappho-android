package com.sappho.audiobooks.presentation.library

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sappho.audiobooks.data.remote.SapphoApi
import com.sappho.audiobooks.domain.model.AuthorInfo
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
        // Genre consolidation into 6 main categories
        val genreConsolidation = mapOf(
            // Literary Classics
            "classics" to "Literary Classics",
            "classic" to "Literary Classics",
            "literary classics" to "Literary Classics",
            "literature" to "Literary Classics",
            "literary" to "Literary Classics",
            "literary fiction" to "Literary Classics",
            "literature & fiction" to "Literary Classics",
            "fiction" to "Literary Classics",
            "general fiction" to "Literary Classics",
            "contemporary" to "Literary Classics",
            "historical fiction" to "Literary Classics",
            "romance" to "Literary Classics",
            "romantic" to "Literary Classics",
            "mystery" to "Literary Classics",
            "mysteries" to "Literary Classics",
            "detective" to "Literary Classics",
            "thriller" to "Literary Classics",
            "thrillers" to "Literary Classics",
            "suspense" to "Literary Classics",
            "psychological thriller" to "Literary Classics",
            "horror" to "Literary Classics",
            "scary" to "Literary Classics",
            "dark" to "Literary Classics",
            "action" to "Literary Classics",
            "adventure" to "Literary Classics",
            "action & adventure" to "Literary Classics",
            "action and adventure" to "Literary Classics",
            "military" to "Literary Classics",
            "war" to "Literary Classics",
            "espionage" to "Literary Classics",
            "crime" to "Literary Classics",
            "comedy" to "Literary Classics",
            "humor" to "Literary Classics",
            "humour" to "Literary Classics",
            "funny" to "Literary Classics",
            "satire" to "Literary Classics",
            "young adult" to "Literary Classics",
            "ya" to "Literary Classics",
            "teen" to "Literary Classics",
            "children" to "Literary Classics",
            "children's" to "Literary Classics",
            "kids" to "Literary Classics",
            "love" to "Literary Classics",

            // Science Fiction
            "sci-fi" to "Science Fiction",
            "scifi" to "Science Fiction",
            "science fiction" to "Science Fiction",
            "sf" to "Science Fiction",
            "space opera" to "Science Fiction",
            "cyberpunk" to "Science Fiction",
            "dystopian" to "Science Fiction",
            "post-apocalyptic" to "Science Fiction",
            "time travel" to "Science Fiction",
            "alien" to "Science Fiction",
            "aliens" to "Science Fiction",
            "robots" to "Science Fiction",
            "futuristic" to "Science Fiction",

            // Fantasy
            "fantasy" to "Fantasy",
            "epic fantasy" to "Fantasy",
            "urban fantasy" to "Fantasy",
            "epic" to "Fantasy",
            "dark fantasy" to "Fantasy",
            "high fantasy" to "Fantasy",
            "sword and sorcery" to "Fantasy",
            "dragons & mythical creatures" to "Fantasy",
            "dragons" to "Fantasy",
            "mythical creatures" to "Fantasy",
            "paranormal" to "Fantasy",
            "magic" to "Fantasy",
            "wizards" to "Fantasy",
            "supernatural" to "Fantasy",
            "vampires" to "Fantasy",
            "werewolves" to "Fantasy",
            "witches" to "Fantasy",
            "fairy tales" to "Fantasy",
            "mythology" to "Fantasy",
            "gods" to "Fantasy",

            // Non-Fiction
            "non-fiction" to "Non-Fiction",
            "nonfiction" to "Non-Fiction",
            "biography" to "Non-Fiction",
            "biographies" to "Non-Fiction",
            "memoir" to "Non-Fiction",
            "memoirs" to "Non-Fiction",
            "autobiography" to "Non-Fiction",
            "biographies & memoirs" to "Non-Fiction",
            "history" to "Non-Fiction",
            "historical" to "Non-Fiction",
            "world history" to "Non-Fiction",
            "self-help" to "Non-Fiction",
            "self help" to "Non-Fiction",
            "self-development" to "Non-Fiction",
            "personal development" to "Non-Fiction",
            "motivation" to "Non-Fiction",
            "personal success" to "Non-Fiction",
            "self-esteem" to "Non-Fiction",
            "creativity" to "Non-Fiction",
            "inspirational" to "Non-Fiction",
            "mindfulness" to "Non-Fiction",
            "business" to "Non-Fiction",
            "finance" to "Non-Fiction",
            "economics" to "Non-Fiction",
            "entrepreneurship" to "Non-Fiction",
            "management" to "Non-Fiction",
            "leadership" to "Non-Fiction",
            "money" to "Non-Fiction",
            "investing" to "Non-Fiction",
            "career" to "Non-Fiction",
            "health" to "Non-Fiction",
            "wellness" to "Non-Fiction",
            "fitness" to "Non-Fiction",
            "diet" to "Non-Fiction",
            "medical" to "Non-Fiction",
            "nutrition" to "Non-Fiction",
            "science" to "Non-Fiction",
            "technology" to "Non-Fiction",
            "tech" to "Non-Fiction",
            "computers" to "Non-Fiction",
            "engineering" to "Non-Fiction",
            "relationships" to "Non-Fiction",
            "dating" to "Non-Fiction",
            "marriage" to "Non-Fiction",
            "family" to "Non-Fiction",
            "parenting" to "Non-Fiction",
            "arts" to "Non-Fiction",
            "entertainment" to "Non-Fiction",
            "music" to "Non-Fiction",
            "film" to "Non-Fiction",
            "performing arts" to "Non-Fiction",
            "home" to "Non-Fiction",
            "lifestyle" to "Non-Fiction",
            "travel" to "Non-Fiction",
            "education" to "Non-Fiction",
            "learning" to "Non-Fiction",
            "reference" to "Non-Fiction",
            "language" to "Non-Fiction",
            "politics" to "Non-Fiction",
            "political" to "Non-Fiction",
            "sociology" to "Non-Fiction",
            "psychology" to "Non-Fiction",
            "social science" to "Non-Fiction",
            "sports" to "Non-Fiction",
            "outdoors" to "Non-Fiction",
            "nature" to "Non-Fiction",
            "true crime" to "Non-Fiction",
            "religion" to "Non-Fiction",
            "spirituality" to "Non-Fiction",
            "spiritual" to "Non-Fiction",
            "christian" to "Non-Fiction",
            "new age" to "Non-Fiction",
            "meditation" to "Non-Fiction",

            // Philosophy
            "philosophy" to "Philosophy",
            "philosophical" to "Philosophy",
            "ethics" to "Philosophy",
            "stoicism" to "Philosophy",
            "existentialism" to "Philosophy",

            // Cooking
            "cooking" to "Cooking",
            "food" to "Cooking",
            "recipes" to "Cooking",
            "culinary" to "Cooking",
            "baking" to "Cooking",
            "cuisine" to "Cooking"
        )

        // Default category for unmapped genres
        private const val DEFAULT_CATEGORY = "Literary Classics"

        fun normalizeGenre(genre: String): String {
            // First try exact match
            genreConsolidation[genre.lowercase()]?.let { return it }

            // Split by common delimiters and find first matching genre
            val parts = genre.split(",", ";", "&", " and ")
                .map { it.trim().lowercase() }
                .filter { it.isNotEmpty() }

            for (part in parts) {
                genreConsolidation[part]?.let { return it }
            }

            // If no match, default to Literary Classics
            return DEFAULT_CATEGORY
        }

        fun getAllNormalizedGenres(genre: String): List<String> {
            val parts = genre.split(",", ";", "&", " and ")
                .map { it.trim().lowercase() }
                .filter { it.isNotEmpty() }

            // Map each part to a category, defaulting to Literary Classics
            return parts.map { part ->
                genreConsolidation[part] ?: DEFAULT_CATEGORY
            }.distinct()
        }
    }

    private val _uiState = MutableStateFlow<LibraryUiState>(LibraryUiState.Loading)
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    private val _series = MutableStateFlow<List<SeriesInfo>>(emptyList())
    val series: StateFlow<List<SeriesInfo>> = _series.asStateFlow()

    private val _authors = MutableStateFlow<List<AuthorInfo>>(emptyList())
    val authors: StateFlow<List<AuthorInfo>> = _authors.asStateFlow()

    private val _genres = MutableStateFlow<List<String>>(emptyList())
    val genres: StateFlow<List<String>> = _genres.asStateFlow()

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

                // Load all audiobooks and extract series, authors, and genres
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

                    // Extract top genres by book count, consolidating similar tags
                    val genreCounts = mutableMapOf<String, Int>()
                    audiobooks.forEach { book ->
                        book.genre?.let { rawGenre ->
                            // Split compound genres and count each normalized genre
                            val normalizedGenres = getAllNormalizedGenres(rawGenre)
                            normalizedGenres.forEach { normalizedGenre ->
                                genreCounts[normalizedGenre] = (genreCounts[normalizedGenre] ?: 0) + 1
                            }
                        }
                    }

                    // Get top genres sorted by book count
                    val topGenres = genreCounts.entries
                        .sortedByDescending { it.value }
                        .map { it.key }

                    _genres.value = topGenres
                    Log.d("LibraryViewModel", "Found ${topGenres.size} consolidated genres")
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
