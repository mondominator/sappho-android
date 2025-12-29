import 'dart:async';
import 'package:flutter/foundation.dart';
import '../models/audiobook.dart';
import '../services/api_service.dart';
import '../services/auth_repository.dart';

/// Search results model
class SearchResults {
  final List<Audiobook> books;
  final List<String> series;
  final List<String> authors;

  SearchResults({
    this.books = const [],
    this.series = const [],
    this.authors = const [],
  });

  bool get isEmpty => books.isEmpty && series.isEmpty && authors.isEmpty;
}

/// Search provider matching Android's SearchViewModel
class SearchProvider extends ChangeNotifier {
  final ApiService _api;
  final AuthRepository _authRepository;

  String _searchQuery = '';
  SearchResults _results = SearchResults();
  bool _isLoading = false;
  String? _serverUrl;
  String? _authToken;
  Timer? _debounceTimer;

  SearchProvider(this._api, this._authRepository) {
    _init();
  }

  String get searchQuery => _searchQuery;
  SearchResults get results => _results;
  bool get isLoading => _isLoading;
  String? get serverUrl => _serverUrl;
  String? get authToken => _authToken;

  Future<void> _init() async {
    _serverUrl = await _authRepository.getServerUrl();
    _authToken = await _authRepository.getToken();
  }

  void updateSearchQuery(String query) {
    _searchQuery = query;
    notifyListeners();

    // Cancel previous timer
    _debounceTimer?.cancel();

    if (query.isEmpty) {
      _results = SearchResults();
      _isLoading = false;
      notifyListeners();
      return;
    }

    // Debounce search by 200ms
    _debounceTimer = Timer(const Duration(milliseconds: 200), () {
      _performSearch(query);
    });
  }

  void clearSearch() {
    _searchQuery = '';
    _results = SearchResults();
    _isLoading = false;
    _debounceTimer?.cancel();
    notifyListeners();
  }

  Future<void> _performSearch(String query) async {
    _isLoading = true;
    notifyListeners();

    try {
      // Search books using the API
      final allBooks = await _api.searchAudiobooks(query, limit: 100);

      // Sort by relevance and take top 8
      final books = _sortBooksByRelevance(allBooks, query).take(8).toList();

      // Extract unique series and authors
      final queryLower = query.toLowerCase();

      final filteredSeries =
          allBooks
              .map((b) => b.series)
              .whereType<String>()
              .toSet()
              .where((s) => s.toLowerCase().contains(queryLower))
              .toList()
            ..sort((a, b) => _compareByRelevance(a, b, queryLower));

      final allAuthors = allBooks
          .map((b) => b.author)
          .whereType<String>()
          .toSet()
          .toList();

      final filteredAuthors = allAuthors
        ..sort((a, b) {
          final aLower = a.toLowerCase();
          final bLower = b.toLowerCase();
          final aContains = aLower.contains(queryLower);
          final bContains = bLower.contains(queryLower);
          if (aContains && !bContains) return -1;
          if (bContains && !aContains) return 1;
          return _compareByRelevance(a, b, queryLower);
        });

      _results = SearchResults(
        books: books,
        series: filteredSeries.take(5).toList(),
        authors: filteredAuthors.take(5).toList(),
      );
    } catch (e) {
      debugPrint('SearchProvider: Search error: $e');
      _results = SearchResults();
    } finally {
      _isLoading = false;
      notifyListeners();
    }
  }

  int _compareByRelevance(String a, String b, String query) {
    final aLower = a.toLowerCase();
    final bLower = b.toLowerCase();
    if (aLower == query) return -1;
    if (bLower == query) return 1;
    if (aLower.startsWith(query)) return -1;
    if (bLower.startsWith(query)) return 1;
    return a.compareTo(b);
  }

  List<Audiobook> _sortBooksByRelevance(List<Audiobook> books, String query) {
    final queryLower = query.toLowerCase();
    return books.toList()..sort((a, b) {
      final aTitle = a.title.toLowerCase();
      final bTitle = b.title.toLowerCase();
      final aAuthor = (a.author ?? '').toLowerCase();
      final bAuthor = (b.author ?? '').toLowerCase();
      final aSeries = (a.series ?? '').toLowerCase();
      final bSeries = (b.series ?? '').toLowerCase();

      // Exact title match
      if (aTitle == queryLower) return -1;
      if (bTitle == queryLower) return 1;

      // Title starts with query
      if (aTitle.startsWith(queryLower) && !bTitle.startsWith(queryLower))
        return -1;
      if (bTitle.startsWith(queryLower) && !aTitle.startsWith(queryLower))
        return 1;

      // Author starts with query
      if (aAuthor.startsWith(queryLower) && !bAuthor.startsWith(queryLower))
        return -1;
      if (bAuthor.startsWith(queryLower) && !aAuthor.startsWith(queryLower))
        return 1;

      // Series starts with query
      if (aSeries.startsWith(queryLower) && !bSeries.startsWith(queryLower))
        return -1;
      if (bSeries.startsWith(queryLower) && !aSeries.startsWith(queryLower))
        return 1;

      // Title contains query
      if (aTitle.contains(queryLower) && !bTitle.contains(queryLower))
        return -1;
      if (bTitle.contains(queryLower) && !aTitle.contains(queryLower)) return 1;

      return 0;
    });
  }

  @override
  void dispose() {
    _debounceTimer?.cancel();
    super.dispose();
  }
}
