package com.sappho.audiobooks.presentation.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sappho.audiobooks.data.remote.SapphoApi
import com.sappho.audiobooks.data.repository.AuthRepository
import com.sappho.audiobooks.domain.model.Audiobook
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SearchResults(
    val books: List<Audiobook> = emptyList(),
    val series: List<String> = emptyList(),
    val authors: List<String> = emptyList()
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val api: SapphoApi,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _results = MutableStateFlow(SearchResults())
    val results: StateFlow<SearchResults> = _results

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _serverUrl = MutableStateFlow<String?>(null)
    val serverUrl: StateFlow<String?> = _serverUrl

    private var searchJob: Job? = null

    init {
        _serverUrl.value = authRepository.getServerUrlSync()
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query

        // Cancel previous search
        searchJob?.cancel()

        if (query.isBlank()) {
            _results.value = SearchResults()
            _isLoading.value = false
            return
        }

        // Debounce search
        searchJob = viewModelScope.launch {
            delay(200) // 200ms debounce
            performSearch(query)
        }
    }

    fun clearSearch() {
        _searchQuery.value = ""
        _results.value = SearchResults()
        _isLoading.value = false
        searchJob?.cancel()
    }

    private suspend fun performSearch(query: String) {
        _isLoading.value = true

        try {
            // Search books
            val booksResponse = api.getAudiobooks(search = query, limit = 100)
            val allBooks = if (booksResponse.isSuccessful) {
                booksResponse.body()?.audiobooks ?: emptyList()
            } else {
                emptyList()
            }

            // Sort by relevance and take top 8
            val books = sortBooksByRelevance(allBooks, query).take(8)

            // Extract unique series and authors from results
            val queryLower = query.lowercase()

            val filteredSeries = allBooks
                .mapNotNull { it.series }
                .distinct()
                .filter { it.lowercase().contains(queryLower) }
                .sortedWith { a, b ->
                    val aLower = a.lowercase()
                    val bLower = b.lowercase()
                    when {
                        aLower == queryLower -> -1
                        bLower == queryLower -> 1
                        aLower.startsWith(queryLower) -> -1
                        bLower.startsWith(queryLower) -> 1
                        else -> a.compareTo(b)
                    }
                }
                .take(5)

            // Show authors from matching books, prioritize those matching the query
            val allAuthors = allBooks.mapNotNull { it.author }.distinct()
            val filteredAuthors = allAuthors
                .sortedWith { a, b ->
                    val aLower = a.lowercase()
                    val bLower = b.lowercase()
                    val aContains = aLower.contains(queryLower)
                    val bContains = bLower.contains(queryLower)
                    when {
                        // Authors matching query come first
                        aContains && !bContains -> -1
                        bContains && !aContains -> 1
                        // Then sort by relevance
                        aLower == queryLower -> -1
                        bLower == queryLower -> 1
                        aLower.startsWith(queryLower) -> -1
                        bLower.startsWith(queryLower) -> 1
                        else -> a.compareTo(b)
                    }
                }
                .take(5)

            _results.value = SearchResults(
                books = books,
                series = filteredSeries,
                authors = filteredAuthors
            )
        } catch (e: Exception) {
            android.util.Log.e("SearchViewModel", "Search error", e)
            _results.value = SearchResults()
        } finally {
            _isLoading.value = false
        }
    }

    private fun sortBooksByRelevance(books: List<Audiobook>, query: String): List<Audiobook> {
        val queryLower = query.lowercase()
        return books.sortedWith { a, b ->
            val aTitle = (a.title).lowercase()
            val bTitle = (b.title).lowercase()
            val aAuthor = (a.author ?: "").lowercase()
            val bAuthor = (b.author ?: "").lowercase()
            val aSeries = (a.series ?: "").lowercase()
            val bSeries = (b.series ?: "").lowercase()

            when {
                // Exact title match
                aTitle == queryLower -> -1
                bTitle == queryLower -> 1
                // Title starts with query
                aTitle.startsWith(queryLower) && !bTitle.startsWith(queryLower) -> -1
                bTitle.startsWith(queryLower) && !aTitle.startsWith(queryLower) -> 1
                // Author starts with query
                aAuthor.startsWith(queryLower) && !bAuthor.startsWith(queryLower) -> -1
                bAuthor.startsWith(queryLower) && !aAuthor.startsWith(queryLower) -> 1
                // Series starts with query
                aSeries.startsWith(queryLower) && !bSeries.startsWith(queryLower) -> -1
                bSeries.startsWith(queryLower) && !aSeries.startsWith(queryLower) -> 1
                // Title contains query
                aTitle.contains(queryLower) && !bTitle.contains(queryLower) -> -1
                bTitle.contains(queryLower) && !aTitle.contains(queryLower) -> 1
                else -> 0
            }
        }
    }
}
