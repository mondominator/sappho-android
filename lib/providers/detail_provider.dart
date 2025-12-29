import 'package:flutter/foundation.dart';
import '../models/audiobook.dart';
import '../services/api_service.dart'
    show
        ApiService,
        AverageRating,
        AudiobookRecap,
        AudiobookUpdateRequest,
        MetadataSearchResult;
import '../services/auth_repository.dart';

/// Detail provider matching Android's AudiobookDetailViewModel
class DetailProvider extends ChangeNotifier {
  final ApiService _api;
  final AuthRepository _authRepository;

  Audiobook? _audiobook;
  Progress? _progress;
  List<Chapter> _chapters = [];
  List<DirectoryFile> _files = [];
  bool _isLoading = false;
  bool _isFavorite = false;
  bool _isTogglingFavorite = false;
  bool _isRefreshingMetadata = false;
  String? _serverUrl;
  String? _authToken;
  String? _error;

  // Rating state
  int? _userRating;
  AverageRating? _averageRating;
  bool _isUpdatingRating = false;

  // AI Recap state
  bool _aiConfigured = false;
  AudiobookRecap? _recap;
  bool _isLoadingRecap = false;
  String? _recapError;

  // Metadata search state
  List<MetadataSearchResult> _metadataSearchResults = [];
  bool _isSearchingMetadata = false;
  String? _metadataSearchError;

  // Embed metadata state
  bool _isEmbeddingMetadata = false;
  String? _embedMetadataResult;

  // Save metadata state
  bool _isSavingMetadata = false;
  String? _metadataSaveResult;

  // Fetch chapters state
  bool _isFetchingChapters = false;
  String? _fetchChaptersResult;

  DetailProvider(this._api, this._authRepository) {
    _init();
  }

  Audiobook? get audiobook => _audiobook;
  Progress? get progress => _progress;
  List<Chapter> get chapters => _chapters;
  List<DirectoryFile> get files => _files;
  bool get isLoading => _isLoading;
  bool get isFavorite => _isFavorite;
  bool get isTogglingFavorite => _isTogglingFavorite;
  bool get isRefreshingMetadata => _isRefreshingMetadata;
  String? get serverUrl => _serverUrl;
  String? get authToken => _authToken;
  String? get error => _error;

  // Rating getters
  int? get userRating => _userRating;
  double? get averageRatingValue => _averageRating?.average;
  int get ratingCount => _averageRating?.count ?? 0;
  bool get isUpdatingRating => _isUpdatingRating;

  // AI Recap getters
  bool get aiConfigured => _aiConfigured;
  AudiobookRecap? get recap => _recap;
  bool get isLoadingRecap => _isLoadingRecap;
  String? get recapError => _recapError;

  // Metadata search getters
  List<MetadataSearchResult> get metadataSearchResults =>
      _metadataSearchResults;
  bool get isSearchingMetadata => _isSearchingMetadata;
  String? get metadataSearchError => _metadataSearchError;

  // Embed metadata getters
  bool get isEmbeddingMetadata => _isEmbeddingMetadata;
  String? get embedMetadataResult => _embedMetadataResult;

  // Save metadata getters
  bool get isSavingMetadata => _isSavingMetadata;
  String? get metadataSaveResult => _metadataSaveResult;

  // Fetch chapters getters
  bool get isFetchingChapters => _isFetchingChapters;
  String? get fetchChaptersResult => _fetchChaptersResult;

  /// Check if Catch Me Up should be shown
  /// Shown when AI is configured AND (user has progress OR previous book in series is completed)
  bool get showCatchMeUp {
    if (!_aiConfigured) return false;
    if (_audiobook == null) return false;

    // Has progress on this book
    final hasProgress = (_progress?.position ?? 0) > 0;
    if (hasProgress) return true;

    // Is part of a series with position > 1 (implies previous book exists)
    if (_audiobook!.series != null && (_audiobook!.seriesPosition ?? 0) > 1) {
      return true;
    }

    return false;
  }

  Future<void> _init() async {
    _serverUrl = await _authRepository.getServerUrl();
    _authToken = await _authRepository.getToken();
  }

  Future<void> loadAudiobook(int id) async {
    _isLoading = true;
    _error = null;
    notifyListeners();

    try {
      debugPrint('DetailProvider: Loading audiobook $id');

      // Load audiobook first (required for other data)
      _audiobook = await _api.getAudiobook(id);
      _isFavorite = _audiobook?.isFavorite ?? false;
      debugPrint('DetailProvider: Loaded ${_audiobook?.title}');

      // Show the page immediately with basic info
      _isLoading = false;
      notifyListeners();

      // Load remaining data in parallel for faster loading
      await Future.wait([
        // Progress
        _api
            .getProgress(id)
            .then((progress) {
              _progress = progress;
              debugPrint(
                'DetailProvider: Progress position: ${_progress?.position}',
              );
            })
            .catchError((e) {
              _progress = _audiobook?.progress;
              debugPrint('DetailProvider: Using audiobook progress: $e');
            }),

        // Chapters
        _api
            .getChapters(id)
            .then((chapters) {
              _chapters = chapters;
              debugPrint('DetailProvider: Loaded ${_chapters.length} chapters');
            })
            .catchError((e) {
              debugPrint('DetailProvider: Failed to load chapters: $e');
            }),

        // User rating
        _api
            .getUserRating(id)
            .then((rating) {
              _userRating = rating?.rating;
              debugPrint('DetailProvider: User rating: $_userRating');
            })
            .catchError((e) {
              debugPrint('DetailProvider: Failed to load user rating: $e');
            }),

        // Average rating
        _api
            .getAverageRating(id)
            .then((avg) {
              _averageRating = avg;
              debugPrint(
                'DetailProvider: Average rating: ${_averageRating?.average}',
              );
            })
            .catchError((e) {
              debugPrint('DetailProvider: Failed to load average rating: $e');
            }),

        // Files
        _api
            .getFiles(id)
            .then((files) {
              _files = files;
              debugPrint('DetailProvider: Loaded ${_files.length} files');
            })
            .catchError((e) {
              debugPrint('DetailProvider: Failed to load files: $e');
            }),

        // AI status
        _api
            .getAiStatus()
            .then((status) {
              _aiConfigured = status.configured;
              debugPrint('DetailProvider: AI configured: $_aiConfigured');
            })
            .catchError((e) {
              _aiConfigured = false;
              debugPrint('DetailProvider: Failed to check AI status: $e');
            }),
      ]);

      // Notify after all parallel loads complete
      notifyListeners();
    } catch (e) {
      _error = e.toString();
      _isLoading = false;
      debugPrint('DetailProvider: Error loading audiobook: $e');
      notifyListeners();
    }
  }

  Future<void> toggleFavorite() async {
    if (_audiobook == null || _isTogglingFavorite) return;

    _isTogglingFavorite = true;
    notifyListeners();

    try {
      final response = await _api.toggleFavorite(_audiobook!.id);
      _isFavorite = response.isFavorite;
    } catch (e) {
      debugPrint('DetailProvider: Error toggling favorite: $e');
    } finally {
      _isTogglingFavorite = false;
      notifyListeners();
    }
  }

  Future<void> setRating(int rating) async {
    if (_audiobook == null || _isUpdatingRating) return;

    _isUpdatingRating = true;
    notifyListeners();

    try {
      await _api.setRating(_audiobook!.id, rating);
      _userRating = rating;

      // Refresh average rating
      try {
        _averageRating = await _api.getAverageRating(_audiobook!.id);
      } catch (e) {
        debugPrint('DetailProvider: Failed to refresh average rating: $e');
      }
    } catch (e) {
      debugPrint('DetailProvider: Error setting rating: $e');
    } finally {
      _isUpdatingRating = false;
      notifyListeners();
    }
  }

  Future<void> clearRating() async {
    if (_audiobook == null || _isUpdatingRating) return;

    _isUpdatingRating = true;
    notifyListeners();

    try {
      await _api.deleteRating(_audiobook!.id);
      _userRating = null;

      // Refresh average rating
      try {
        _averageRating = await _api.getAverageRating(_audiobook!.id);
      } catch (e) {
        debugPrint('DetailProvider: Failed to refresh average rating: $e');
      }
    } catch (e) {
      debugPrint('DetailProvider: Error clearing rating: $e');
    } finally {
      _isUpdatingRating = false;
      notifyListeners();
    }
  }

  Future<void> markFinished() async {
    if (_audiobook == null) return;

    try {
      await _api.markFinished(_audiobook!.id);
      // Reload to get updated progress
      await loadAudiobook(_audiobook!.id);
    } catch (e) {
      debugPrint('DetailProvider: Error marking finished: $e');
    }
  }

  Future<void> clearProgress() async {
    if (_audiobook == null) return;

    try {
      await _api.clearProgress(_audiobook!.id);
      // Reload to get updated progress
      await loadAudiobook(_audiobook!.id);
    } catch (e) {
      debugPrint('DetailProvider: Error clearing progress: $e');
    }
  }

  Future<void> refreshMetadata() async {
    if (_audiobook == null || _isRefreshingMetadata) return;

    _isRefreshingMetadata = true;
    notifyListeners();

    try {
      await _api.refreshMetadata(_audiobook!.id);
      // Reload to get updated metadata
      await loadAudiobook(_audiobook!.id);
    } catch (e) {
      debugPrint('DetailProvider: Error refreshing metadata: $e');
    } finally {
      _isRefreshingMetadata = false;
      notifyListeners();
    }
  }

  Future<void> updateAudiobook(AudiobookUpdateRequest request) async {
    if (_audiobook == null) return;

    try {
      debugPrint('DetailProvider: Updating audiobook metadata');
      _audiobook = await _api.updateAudiobook(_audiobook!.id, request);
      notifyListeners();
      debugPrint('DetailProvider: Audiobook updated successfully');
    } catch (e) {
      debugPrint('DetailProvider: Error updating audiobook: $e');
      rethrow;
    }
  }

  /// Search for metadata from external sources (Audnexus/Audible)
  Future<void> searchMetadata({
    String? title,
    String? author,
    String? asin,
  }) async {
    if (_audiobook == null || _isSearchingMetadata) return;

    _isSearchingMetadata = true;
    _metadataSearchError = null;
    _metadataSearchResults = [];
    notifyListeners();

    try {
      _metadataSearchResults = await _api.searchMetadata(
        _audiobook!.id,
        title: title,
        author: author,
        asin: asin,
      );
      if (_metadataSearchResults.isEmpty) {
        _metadataSearchError = 'No results found';
      }
      debugPrint(
        'DetailProvider: Found ${_metadataSearchResults.length} metadata results',
      );
    } catch (e) {
      _metadataSearchError = 'Error: ${e.toString()}';
      debugPrint('DetailProvider: Error searching metadata: $e');
    } finally {
      _isSearchingMetadata = false;
      notifyListeners();
    }
  }

  /// Clear metadata search results
  void clearMetadataSearch() {
    _metadataSearchResults = [];
    _metadataSearchError = null;
    notifyListeners();
  }

  /// Save metadata and optionally embed into audio file
  Future<void> saveMetadata(
    AudiobookUpdateRequest request, {
    bool embed = false,
  }) async {
    if (_audiobook == null || _isSavingMetadata) return;

    final audiobookId = _audiobook!.id;
    _isSavingMetadata = true;
    _metadataSaveResult = null;
    notifyListeners();

    try {
      debugPrint(
        'DetailProvider: Saving metadata for audiobook $audiobookId, embed=$embed',
      );
      _audiobook = await _api.updateAudiobook(audiobookId, request);
      _metadataSaveResult = 'Metadata saved successfully';
      debugPrint('DetailProvider: Metadata saved successfully');

      if (embed) {
        debugPrint('DetailProvider: Calling embedMetadata...');
        // Call embed directly instead of through embedMetadata() to avoid guard clause issues
        try {
          final message = await _api.embedMetadata(audiobookId);
          _embedMetadataResult = message;
          debugPrint('DetailProvider: Metadata embedded: $message');
        } catch (e) {
          final errorStr = e.toString();
          if (errorStr.contains('500')) {
            _embedMetadataResult =
                'Server error: Embedding tools (tone/ffmpeg) may not be configured on server';
          } else {
            _embedMetadataResult = 'Error: $errorStr';
          }
          debugPrint('DetailProvider: Error embedding metadata: $e');
        }
      }

      // Reload audiobook to get updated data
      await loadAudiobook(audiobookId);
    } catch (e) {
      _metadataSaveResult = 'Error: ${e.toString()}';
      debugPrint('DetailProvider: Error saving metadata: $e');
    } finally {
      _isSavingMetadata = false;
      notifyListeners();
    }
  }

  /// Embed metadata into audio file tags
  Future<void> embedMetadata() async {
    if (_audiobook == null || _isEmbeddingMetadata) return;

    _isEmbeddingMetadata = true;
    _embedMetadataResult = null;
    notifyListeners();

    try {
      final message = await _api.embedMetadata(_audiobook!.id);
      _embedMetadataResult = message;
      debugPrint('DetailProvider: Metadata embedded: $message');
      // Reload audiobook to refresh any updated data
      await loadAudiobook(_audiobook!.id);
    } catch (e) {
      final errorStr = e.toString();
      if (errorStr.contains('500')) {
        _embedMetadataResult =
            'Server error: Embedding tools (tone/ffmpeg) may not be configured on server';
      } else {
        _embedMetadataResult = 'Error: $errorStr';
      }
      debugPrint('DetailProvider: Error embedding metadata: $e');
    } finally {
      _isEmbeddingMetadata = false;
      notifyListeners();
    }
  }

  /// Clear save/embed result messages
  void clearMetadataMessages() {
    _metadataSaveResult = null;
    _embedMetadataResult = null;
    _fetchChaptersResult = null;
    notifyListeners();
  }

  /// Fetch chapters from Audnexus using ASIN
  Future<void> fetchChaptersFromAudnexus(String asin) async {
    if (_audiobook == null || _isFetchingChapters) return;
    if (asin.isEmpty) {
      _fetchChaptersResult = 'ASIN is required';
      notifyListeners();
      return;
    }

    _isFetchingChapters = true;
    _fetchChaptersResult = null;
    notifyListeners();

    try {
      final response = await _api.fetchChaptersFromAudnexus(
        _audiobook!.id,
        asin,
      );
      _fetchChaptersResult =
          response.message ?? 'Chapters fetched successfully';
      debugPrint('DetailProvider: Chapters fetched: ${response.message}');
      // Reload audiobook to get updated chapters
      await loadAudiobook(_audiobook!.id);
    } catch (e) {
      final errorStr = e.toString();
      if (errorStr.contains('404')) {
        _fetchChaptersResult = 'No chapters found for this ASIN';
      } else if (errorStr.contains('500')) {
        _fetchChaptersResult = 'Server error: Could not fetch chapters';
      } else {
        _fetchChaptersResult = 'Error: $errorStr';
      }
      debugPrint('DetailProvider: Error fetching chapters: $e');
    } finally {
      _isFetchingChapters = false;
      notifyListeners();
    }
  }

  /// Clear fetch chapters result
  void clearFetchChaptersResult() {
    _fetchChaptersResult = null;
    notifyListeners();
  }

  /// Load AI recap for current audiobook
  Future<void> loadRecap() async {
    if (_audiobook == null || _isLoadingRecap) return;

    _isLoadingRecap = true;
    _recapError = null;
    _recap = null;
    notifyListeners();

    try {
      _recap = await _api.getAudiobookRecap(_audiobook!.id);
      debugPrint('DetailProvider: Loaded recap (cached: ${_recap?.cached})');
    } catch (e) {
      _recapError = e.toString();
      debugPrint('DetailProvider: Error loading recap: $e');
    } finally {
      _isLoadingRecap = false;
      notifyListeners();
    }
  }

  /// Clear cached recap and regenerate
  Future<void> regenerateRecap() async {
    if (_audiobook == null) return;

    try {
      await _api.clearAudiobookRecap(_audiobook!.id);
    } catch (e) {
      debugPrint('DetailProvider: Error clearing recap: $e');
    }

    await loadRecap();
  }

  /// Dismiss recap dialog
  void dismissRecap() {
    _recap = null;
    _recapError = null;
    _isLoadingRecap = false;
    notifyListeners();
  }

  void clear() {
    _audiobook = null;
    _progress = null;
    _chapters = [];
    _files = [];
    _isLoading = false;
    _isFavorite = false;
    _isRefreshingMetadata = false;
    _userRating = null;
    _averageRating = null;
    _isUpdatingRating = false;
    _aiConfigured = false;
    _recap = null;
    _isLoadingRecap = false;
    _recapError = null;
    _metadataSearchResults = [];
    _isSearchingMetadata = false;
    _metadataSearchError = null;
    _isEmbeddingMetadata = false;
    _embedMetadataResult = null;
    _isSavingMetadata = false;
    _metadataSaveResult = null;
    _isFetchingChapters = false;
    _fetchChaptersResult = null;
    _error = null;
    notifyListeners();
  }
}
