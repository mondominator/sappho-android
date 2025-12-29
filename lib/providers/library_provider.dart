import 'package:flutter/foundation.dart';
import '../models/audiobook.dart';
import '../services/api_service.dart';
import '../services/auth_repository.dart';

/// Series info model
class SeriesInfo {
  final String series;
  final int bookCount;

  SeriesInfo({required this.series, required this.bookCount});
}

/// Author info model
class AuthorInfo {
  final String author;
  final int bookCount;

  AuthorInfo({required this.author, required this.bookCount});
}

/// Genre info model
class GenreInfo {
  final String genre;
  final int count;
  final int totalDuration;
  final List<int> coverIds;

  GenreInfo({
    required this.genre,
    required this.count,
    this.totalDuration = 0,
    this.coverIds = const [],
  });

  factory GenreInfo.fromJson(Map<String, dynamic> json) {
    return GenreInfo(
      genre: json['genre'] ?? '',
      count: json['count'] ?? 0,
      totalDuration: json['total_duration'] ?? 0,
      coverIds:
          (json['cover_ids'] as List<dynamic>?)
              ?.map((e) => e as int)
              .toList() ??
          [],
    );
  }
}

/// Library provider matching Android's LibraryViewModel
class LibraryProvider extends ChangeNotifier {
  final ApiService _api;
  final AuthRepository _authRepository;

  List<Audiobook> _allAudiobooks = [];
  List<SeriesInfo> _series = [];
  List<AuthorInfo> _authors = [];
  List<GenreInfo> _genres = [];
  List<Audiobook> _readingList = [];
  int _collectionsCount = 0;
  bool _isLoading = false;
  String? _serverUrl;
  String? _authToken;
  String? _error;

  // Selection state for navigation from detail screen
  String? _selectedAuthor;
  String? _selectedSeries;
  String? _selectedGenre;
  int _selectedTabIndex = 0; // 0=All, 1=Series, 2=Authors, 3=Genres

  // View state to preserve navigation within library
  // 0=categories, 1=series, 2=seriesBooks, 3=authors, 4=authorBooks, 5=genres, 6=genreBooks, 7=allBooks
  int _currentViewIndex = 0;

  LibraryProvider(this._api, this._authRepository) {
    debugPrint('LibraryProvider: constructor called');
    _init();
  }

  List<Audiobook> get allAudiobooks => _allAudiobooks;
  List<SeriesInfo> get series => _series;
  List<AuthorInfo> get authors => _authors;
  List<GenreInfo> get genres => _genres;
  List<Audiobook> get readingList => _readingList;
  int get collectionsCount => _collectionsCount;
  bool get isLoading => _isLoading;
  String? get serverUrl => _serverUrl;
  String? get authToken => _authToken;
  String? get error => _error;
  String? get selectedAuthor => _selectedAuthor;
  String? get selectedSeries => _selectedSeries;
  String? get selectedGenre => _selectedGenre;
  int get selectedTabIndex => _selectedTabIndex;
  int get currentViewIndex => _currentViewIndex;

  /// Select an author and switch to Authors tab
  void selectAuthor(String author) {
    _selectedAuthor = author;
    _selectedSeries = null;
    _selectedGenre = null;
    _selectedTabIndex = 2; // Authors tab
    notifyListeners();
  }

  /// Select a series and switch to Series tab
  void selectSeries(String series) {
    _selectedSeries = series;
    _selectedAuthor = null;
    _selectedGenre = null;
    _selectedTabIndex = 1; // Series tab
    notifyListeners();
  }

  /// Select a genre and switch to Genres tab
  void selectGenre(String genre) {
    _selectedGenre = genre;
    _selectedAuthor = null;
    _selectedSeries = null;
    _selectedTabIndex = 3; // Genres tab
    notifyListeners();
  }

  /// Clear selection
  void clearSelection() {
    _selectedAuthor = null;
    _selectedSeries = null;
    _selectedGenre = null;
    notifyListeners();
  }

  /// Set tab index
  void setTabIndex(int index) {
    _selectedTabIndex = index;
    notifyListeners();
  }

  /// Set current view index (preserves navigation state)
  void setViewIndex(int index) {
    _currentViewIndex = index;
    // Don't notify - let the UI update when needed
  }

  /// Set view state with selected item
  void setViewState({
    required int viewIndex,
    String? series,
    String? author,
    String? genre,
    bool notify = false,
  }) {
    _currentViewIndex = viewIndex;
    _selectedSeries = series;
    _selectedAuthor = author;
    _selectedGenre = genre;
    // Only notify when explicitly requested (e.g., navigation reset)
    if (notify) {
      notifyListeners();
    }
  }

  Future<void> _init() async {
    try {
      debugPrint('LibraryProvider: _init starting');
      _serverUrl = await _authRepository.getServerUrl();
      _authToken = await _authRepository.getToken();
      debugPrint(
        'LibraryProvider: init with serverUrl=$_serverUrl, hasToken=${_authToken != null}',
      );
      await loadData();
    } catch (e, stackTrace) {
      debugPrint('LibraryProvider: _init error: $e');
      debugPrint('LibraryProvider: stack: $stackTrace');
    }
  }

  Future<void> loadData() async {
    _isLoading = true;
    _error = null;
    notifyListeners();

    debugPrint('LibraryProvider: Starting to load data');

    try {
      // Load all audiobooks
      final audiobooks = await _api.getAllAudiobooks();
      _allAudiobooks = audiobooks;
      debugPrint('LibraryProvider: Loaded ${audiobooks.length} audiobooks');

      // Extract series (filter out series with only 1 book)
      final seriesMap = <String, int>{};
      for (final book in audiobooks) {
        if (book.series != null && book.series!.isNotEmpty) {
          seriesMap[book.series!] = (seriesMap[book.series!] ?? 0) + 1;
        }
      }
      _series =
          seriesMap.entries
              .where((e) => e.value > 1)
              .map((e) => SeriesInfo(series: e.key, bookCount: e.value))
              .toList()
            ..sort((a, b) => a.series.compareTo(b.series));
      debugPrint('LibraryProvider: Found ${_series.length} series');

      // Extract authors
      final authorsMap = <String, int>{};
      for (final book in audiobooks) {
        if (book.author != null && book.author!.isNotEmpty) {
          authorsMap[book.author!] = (authorsMap[book.author!] ?? 0) + 1;
        }
      }
      _authors =
          authorsMap.entries
              .map((e) => AuthorInfo(author: e.key, bookCount: e.value))
              .toList()
            ..sort((a, b) => a.author.compareTo(b.author));
      debugPrint('LibraryProvider: Found ${_authors.length} authors');

      // Load genres from server (normalized)
      try {
        final genresList = await _api.getGenres();
        _genres = genresList.map((json) => GenreInfo.fromJson(json)).toList()
          ..sort((a, b) => a.genre.compareTo(b.genre));
        debugPrint(
          'LibraryProvider: Loaded ${_genres.length} genres from server',
        );
      } catch (e) {
        debugPrint('LibraryProvider: Failed to load genres from server: $e');
        // Fallback: Extract genres from audiobooks
        final genresMap = <String, int>{};
        for (final book in audiobooks) {
          if (book.genre != null && book.genre!.isNotEmpty) {
            genresMap[book.genre!] = (genresMap[book.genre!] ?? 0) + 1;
          }
        }
        _genres =
            genresMap.entries
                .map((e) => GenreInfo(genre: e.key, count: e.value))
                .toList()
              ..sort((a, b) => a.genre.compareTo(b.genre));
        debugPrint(
          'LibraryProvider: Fallback - extracted ${_genres.length} genres from books',
        );
      }

      // Load reading list (favorites)
      try {
        _readingList = await _api.getFavorites();
        debugPrint(
          'LibraryProvider: Loaded ${_readingList.length} reading list items',
        );
      } catch (e) {
        debugPrint('LibraryProvider: Failed to load reading list: $e');
      }

      // Load collections count
      try {
        final collections = await _api.getCollections();
        _collectionsCount = collections.length;
        debugPrint('LibraryProvider: Loaded $_collectionsCount collections');
      } catch (e) {
        debugPrint('LibraryProvider: Failed to load collections: $e');
      }
    } catch (e, stackTrace) {
      _error = e.toString();
      debugPrint('LibraryProvider: Error loading data: $e');
      debugPrint('LibraryProvider: Stack trace: $stackTrace');
    } finally {
      _isLoading = false;
      notifyListeners();
    }
  }

  Future<void> refresh() async {
    await loadData();
  }

  /// Get books for a specific series, sorted by series position
  List<Audiobook> getBooksForSeries(String seriesName) {
    return _allAudiobooks.where((book) => book.series == seriesName).toList()
      ..sort(
        (a, b) => (a.seriesPosition ?? 0).compareTo(b.seriesPosition ?? 0),
      );
  }

  /// Get books for a specific author, sorted by title
  List<Audiobook> getBooksForAuthor(String authorName) {
    return _allAudiobooks.where((book) => book.author == authorName).toList()
      ..sort((a, b) => a.title.compareTo(b.title));
  }

  /// Get books for a specific genre, sorted by title
  /// Uses normalized_genre field from server for accurate matching
  List<Audiobook> getBooksForGenre(String genreName) {
    final genreLower = genreName.toLowerCase();
    return _allAudiobooks.where((book) {
      // Only match on normalized_genre from server for accurate counts
      if (book.normalizedGenre == null) return false;
      final normalizedCategories = book.normalizedGenre!.toLowerCase().split(
        ', ',
      );
      return normalizedCategories.contains(genreLower);
    }).toList()..sort((a, b) => a.title.compareTo(b.title));
  }

  /// Toggle favorite status for a book
  Future<void> toggleFavorite(int audiobookId) async {
    try {
      final response = await _api.toggleFavorite(audiobookId);
      // Update the book in the all audiobooks list
      _allAudiobooks = _allAudiobooks.map((book) {
        if (book.id == audiobookId) {
          return book.copyWith(isFavorite: response.isFavorite);
        }
        return book;
      }).toList();

      // Refresh reading list
      _readingList = await _api.getFavorites();
      notifyListeners();
    } catch (e) {
      debugPrint('LibraryProvider: Error toggling favorite: $e');
    }
  }

  /// Refresh reading list from server
  Future<void> refreshReadingList() async {
    try {
      debugPrint('LibraryProvider: Refreshing reading list...');
      _readingList = await _api.getFavorites();
      debugPrint(
        'LibraryProvider: Loaded ${_readingList.length} reading list items',
      );
      notifyListeners();
    } catch (e) {
      debugPrint('LibraryProvider: Failed to refresh reading list: $e');
    }
  }
}
