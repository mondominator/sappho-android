import 'package:flutter/foundation.dart';
import '../models/audiobook.dart';
import '../services/api_service.dart';
import '../services/auth_repository.dart';

/// Home provider matching Android's HomeViewModel
class HomeProvider extends ChangeNotifier {
  final ApiService _api;
  final AuthRepository _authRepository;

  List<Audiobook> _inProgress = [];
  List<Audiobook> _upNext = [];
  List<Audiobook> _recentlyAdded = [];
  List<Audiobook> _finished = [];
  bool _isLoading = false;
  String? _serverUrl;
  String? _authToken;
  String? _error;

  HomeProvider(this._api, this._authRepository) {
    debugPrint('HomeProvider: constructor called');
    _init();
  }

  List<Audiobook> get inProgress => _inProgress;
  List<Audiobook> get upNext => _upNext;
  List<Audiobook> get recentlyAdded => _recentlyAdded;
  List<Audiobook> get finished => _finished;
  bool get isLoading => _isLoading;
  String? get serverUrl => _serverUrl;
  String? get authToken => _authToken;
  String? get error => _error;

  Future<void> _init() async {
    try {
      debugPrint('HomeProvider: _init starting');
      _serverUrl = await _authRepository.getServerUrl();
      _authToken = await _authRepository.getToken();
      debugPrint(
        'HomeProvider: init with serverUrl=$_serverUrl, hasToken=${_authToken != null}',
      );
      await loadData();
    } catch (e, stackTrace) {
      debugPrint('HomeProvider: _init error: $e');
      debugPrint('HomeProvider: stack: $stackTrace');
    }
  }

  Future<void> loadData() async {
    _isLoading = true;
    _error = null;
    notifyListeners();

    debugPrint('HomeProvider: Starting to load data');
    debugPrint('HomeProvider: serverUrl = $_serverUrl');

    try {
      // First, load the favorites list to get IDs of favorited books
      // (The meta endpoints don't return correct is_favorite status)
      Set<int> favoriteIds = {};
      try {
        final favorites = await _api.getFavorites();
        favoriteIds = favorites.map((b) => b.id).toSet();
        debugPrint('HomeProvider: Loaded ${favorites.length} favorites');
      } catch (e) {
        debugPrint('HomeProvider: Failed to load favorites: $e');
        // Ignore favorites error - not critical
      }

      // Helper function to apply favorite status to book lists
      List<Audiobook> withFavoriteStatus(List<Audiobook> books) {
        return books.map((book) {
          if (favoriteIds.contains(book.id)) {
            return book.copyWith(isFavorite: true);
          }
          return book;
        }).toList();
      }

      // Load all sections in parallel
      debugPrint('HomeProvider: Loading sections...');
      final results = await Future.wait([
        _api.getInProgress(limit: 10),
        _api.getUpNext(limit: 10),
        _api.getRecentlyAdded(limit: 10),
        _api.getFinished(limit: 10),
      ]);

      _inProgress = withFavoriteStatus(results[0]);
      _upNext = _prioritizeUpNext(withFavoriteStatus(results[1]), _inProgress);
      _recentlyAdded = withFavoriteStatus(results[2]);
      _finished = withFavoriteStatus(results[3]);

      debugPrint(
        'HomeProvider: Loaded ${_inProgress.length} in-progress, ${_upNext.length} up-next, ${_recentlyAdded.length} recent, ${_finished.length} finished',
      );
    } catch (e, stackTrace) {
      _error = e.toString();
      debugPrint('HomeProvider: Error loading data: $e');
      debugPrint('HomeProvider: Stack trace: $stackTrace');
    } finally {
      _isLoading = false;
      notifyListeners();
    }
  }

  Future<void> refresh() async {
    await loadData();
  }

  Future<void> toggleFavorite(int audiobookId) async {
    try {
      final response = await _api.toggleFavorite(audiobookId);
      _updateFavoriteStatus(audiobookId, response.isFavorite);
      notifyListeners();
    } catch (e) {
      debugPrint('HomeProvider: Error toggling favorite: $e');
    }
  }

  Future<void> markFinished(int audiobookId) async {
    try {
      await _api.markFinished(audiobookId);
      // Refresh to update the lists
      await loadData();
    } catch (e) {
      debugPrint('HomeProvider: Error marking finished: $e');
    }
  }

  Future<void> clearProgress(int audiobookId) async {
    try {
      await _api.clearProgress(audiobookId);
      // Refresh to update the lists
      await loadData();
    } catch (e) {
      debugPrint('HomeProvider: Error clearing progress: $e');
    }
  }

  void _updateFavoriteStatus(int audiobookId, bool isFavorite) {
    _inProgress = _inProgress.map((book) {
      if (book.id == audiobookId) return book.copyWith(isFavorite: isFavorite);
      return book;
    }).toList();

    _upNext = _upNext.map((book) {
      if (book.id == audiobookId) return book.copyWith(isFavorite: isFavorite);
      return book;
    }).toList();

    _recentlyAdded = _recentlyAdded.map((book) {
      if (book.id == audiobookId) return book.copyWith(isFavorite: isFavorite);
      return book;
    }).toList();

    _finished = _finished.map((book) {
      if (book.id == audiobookId) return book.copyWith(isFavorite: isFavorite);
      return book;
    }).toList();
  }

  /// Prioritizes the Up Next list by moving the next book in the currently
  /// playing book's series to the front of the list
  List<Audiobook> _prioritizeUpNext(
    List<Audiobook> upNextBooks,
    List<Audiobook> inProgressBooks,
  ) {
    if (upNextBooks.isEmpty || inProgressBooks.isEmpty) {
      return upNextBooks;
    }

    // Get the first in-progress book (most recently listened)
    final currentBook = inProgressBooks.first;
    final currentSeries = currentBook.series;
    final currentPosition = currentBook.seriesPosition;

    if (currentSeries == null || currentPosition == null) {
      return upNextBooks;
    }

    // Find the next book in the same series (position > current position)
    final nextInSeries = upNextBooks
        .where(
          (book) =>
              book.series == currentSeries &&
              (book.seriesPosition ?? 0) > currentPosition,
        )
        .fold<Audiobook?>(null, (prev, curr) {
          if (prev == null) return curr;
          if ((curr.seriesPosition ?? double.infinity) <
              (prev.seriesPosition ?? double.infinity)) {
            return curr;
          }
          return prev;
        });

    if (nextInSeries == null) {
      return upNextBooks;
    }

    // Move the next book in series to the front
    final reordered = <Audiobook>[nextInSeries];
    reordered.addAll(upNextBooks.where((b) => b.id != nextInSeries.id));
    return reordered;
  }
}
