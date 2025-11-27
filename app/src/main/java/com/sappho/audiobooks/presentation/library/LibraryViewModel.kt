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
        // Genre consolidation matching Audible's categories
        val genreConsolidation = mapOf(
            // Science Fiction & Fantasy
            "sci-fi" to "Science Fiction",
            "scifi" to "Science Fiction",
            "science fiction" to "Science Fiction",
            "sf" to "Science Fiction",
            "space opera" to "Science Fiction",
            "cyberpunk" to "Science Fiction",
            "dystopian" to "Science Fiction",
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

            // Mystery & Thriller
            "mystery" to "Mystery",
            "mysteries" to "Mystery",
            "detective" to "Mystery",
            "thriller" to "Thriller",
            "thrillers" to "Thriller",
            "suspense" to "Thriller",
            "psychological thriller" to "Thriller",

            // Romance
            "romance" to "Romance",
            "romantic" to "Romance",
            "love" to "Romance",

            // Horror
            "horror" to "Horror",
            "scary" to "Horror",
            "supernatural" to "Horror",
            "dark" to "Horror",

            // Literature & Fiction
            "fiction" to "Fiction",
            "literary fiction" to "Literature",
            "literature" to "Literature",
            "literary" to "Literature",
            "literature & fiction" to "Literature",
            "classics" to "Classics",
            "classic" to "Classics",
            "literary classics" to "Classics",
            "contemporary" to "Fiction",
            "general fiction" to "Fiction",

            // Action & Adventure
            "action" to "Action",
            "adventure" to "Adventure",
            "action & adventure" to "Adventure",
            "action and adventure" to "Adventure",
            "military" to "Military",
            "war" to "Military",
            "espionage" to "Thriller",

            // Biographies & Memoirs
            "biography" to "Biographies",
            "biographies" to "Biographies",
            "memoir" to "Memoir",
            "memoirs" to "Memoir",
            "autobiography" to "Biographies",
            "biographies & memoirs" to "Biographies",

            // History
            "history" to "History",
            "historical" to "History",
            "historical fiction" to "Historical Fiction",
            "world history" to "History",

            // Self Development
            "self-help" to "Self Development",
            "self help" to "Self Development",
            "self-development" to "Self Development",
            "personal development" to "Self Development",
            "motivation" to "Self Development",
            "personal success" to "Self Development",
            "self-esteem" to "Self Development",
            "creativity" to "Self Development",
            "inspirational" to "Self Development",
            "mindfulness" to "Self Development",

            // Business
            "business" to "Business",
            "finance" to "Business",
            "economics" to "Business",
            "entrepreneurship" to "Business",
            "management" to "Business",
            "leadership" to "Business",
            "money" to "Business",
            "investing" to "Business",
            "career" to "Business",

            // Non-Fiction
            "non-fiction" to "Non-Fiction",
            "nonfiction" to "Non-Fiction",

            // Kids & Young Adult
            "young adult" to "Young Adult",
            "ya" to "Young Adult",
            "teen" to "Young Adult",
            "children" to "Children's",
            "children's" to "Children's",
            "kids" to "Children's",

            // Comedy
            "comedy" to "Comedy",
            "humor" to "Comedy",
            "humour" to "Comedy",
            "funny" to "Comedy",
            "satire" to "Comedy",

            // True Crime
            "true crime" to "True Crime",
            "crime" to "Crime",

            // Religion & Spirituality
            "religion" to "Religion",
            "spirituality" to "Spirituality",
            "spiritual" to "Spirituality",
            "christian" to "Religion",
            "philosophy" to "Philosophy",
            "new age" to "Spirituality",
            "meditation" to "Spirituality",

            // Health & Wellness
            "health" to "Health",
            "wellness" to "Health",
            "fitness" to "Health",
            "diet" to "Health",
            "medical" to "Health",
            "nutrition" to "Health",

            // Science & Technology
            "science" to "Science",
            "technology" to "Technology",
            "tech" to "Technology",
            "computers" to "Technology",
            "engineering" to "Technology",

            // Relationships
            "relationships" to "Relationships",
            "dating" to "Relationships",
            "marriage" to "Relationships",
            "family" to "Relationships",
            "parenting" to "Relationships",

            // Arts & Entertainment
            "arts" to "Arts",
            "entertainment" to "Entertainment",
            "music" to "Arts",
            "film" to "Arts",
            "performing arts" to "Arts",

            // Home & Lifestyle
            "cooking" to "Cooking",
            "food" to "Cooking",
            "home" to "Home",
            "lifestyle" to "Lifestyle",
            "travel" to "Travel",

            // Education
            "education" to "Education",
            "learning" to "Education",
            "reference" to "Education",
            "language" to "Education",

            // Politics & Social Sciences
            "politics" to "Politics",
            "political" to "Politics",
            "sociology" to "Social Science",
            "psychology" to "Psychology",
            "social science" to "Social Science",

            // Sports
            "sports" to "Sports",
            "outdoors" to "Sports",
            "nature" to "Science"
        )

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

            // If no match, use the first part capitalized
            return parts.firstOrNull()?.replaceFirstChar { it.uppercase() } ?: genre
        }

        fun getAllNormalizedGenres(genre: String): List<String> {
            val parts = genre.split(",", ";", "&", " and ")
                .map { it.trim().lowercase() }
                .filter { it.isNotEmpty() }

            return parts.mapNotNull { part ->
                genreConsolidation[part] ?: part.replaceFirstChar { it.uppercase() }
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
