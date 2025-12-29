import 'package:flutter/foundation.dart';
import '../models/audiobook.dart';
import '../services/auth_repository.dart';
import '../services/download_service.dart';

/// Provider for managing audiobook downloads
class DownloadProvider extends ChangeNotifier {
  final AuthRepository _authRepository;
  final DownloadService _downloadService = DownloadService();

  List<DownloadedAudiobook> _downloads = [];
  final Map<int, double> _downloadProgress = {};
  int _storageUsed = 0;
  bool _isLoading = false;
  bool _isInitialized = false;
  String? _error;

  DownloadProvider(this._authRepository) {
    _init();
  }

  List<DownloadedAudiobook> get downloads => _downloads;
  Map<int, double> get downloadProgress => _downloadProgress;
  int get storageUsed => _storageUsed;
  String get formattedStorageUsed => DownloadService.formatBytes(_storageUsed);
  bool get isLoading => _isLoading;
  bool get isInitialized => _isInitialized;
  String? get error => _error;

  /// Get completed downloads only
  List<DownloadedAudiobook> get completedDownloads =>
      _downloads.where((d) => d.isCompleted).toList();

  /// Get active downloads (downloading or pending)
  List<DownloadedAudiobook> get activeDownloads => _downloads
      .where(
        (d) =>
            d.status == DownloadStatus.downloading ||
            d.status == DownloadStatus.pending,
      )
      .toList();

  Future<void> _init() async {
    try {
      // Set up callbacks
      _downloadService.onProgressUpdate = (audiobookId, progress) {
        _downloadProgress[audiobookId] = progress;
        notifyListeners();
      };

      _downloadService.onStatusChange = (audiobookId, status) {
        _refreshDownloads();
      };

      _downloadService.onDownloadsChanged = () {
        _refreshDownloads();
      };

      // Load initial downloads
      await _refreshDownloads();
      _isInitialized = true;
      _error = null;
    } catch (e) {
      debugPrint('DownloadProvider: Error initializing: $e');
      _error = e.toString();
      _isInitialized =
          true; // Mark as initialized even on error so UI can show error state
    }
    notifyListeners();
  }

  Future<void> _refreshDownloads() async {
    try {
      debugPrint('DownloadProvider: _refreshDownloads starting');
      _downloads = await _downloadService.getDownloads();
      debugPrint('DownloadProvider: Got ${_downloads.length} downloads');
      _storageUsed = await _downloadService.getStorageUsed();
      debugPrint('DownloadProvider: Storage used: $_storageUsed');
      _error = null;
      notifyListeners();
    } catch (e, stack) {
      debugPrint('DownloadProvider: Error refreshing downloads: $e');
      debugPrint('DownloadProvider: Stack: $stack');
      _error = e.toString();
      notifyListeners();
    }
  }

  /// Check if an audiobook is downloaded
  Future<bool> isDownloaded(int audiobookId) async {
    return await _downloadService.isDownloaded(audiobookId);
  }

  /// Check download status synchronously from cache
  DownloadStatus? getDownloadStatus(int audiobookId) {
    final download = _downloads.cast<DownloadedAudiobook?>().firstWhere(
      (d) => d?.audiobookId == audiobookId,
      orElse: () => null,
    );
    return download?.status;
  }

  /// Get download progress (0.0 - 1.0) for an audiobook
  double getProgress(int audiobookId) {
    return _downloadProgress[audiobookId] ?? 0.0;
  }

  /// Get local audio path for a downloaded audiobook
  Future<String?> getLocalAudioPath(int audiobookId) async {
    return await _downloadService.getLocalAudioPath(audiobookId);
  }

  /// Start downloading an audiobook
  Future<void> startDownload(Audiobook audiobook) async {
    try {
      _isLoading = true;
      notifyListeners();

      await _downloadService.startDownload(audiobook, _authRepository);
    } catch (e) {
      debugPrint('DownloadProvider: Error starting download: $e');
      rethrow;
    } finally {
      _isLoading = false;
      notifyListeners();
    }
  }

  /// Cancel an active download
  Future<void> cancelDownload(int audiobookId) async {
    await _downloadService.cancelDownload(audiobookId);
    _downloadProgress.remove(audiobookId);
    await _refreshDownloads();
  }

  /// Delete a download (removes files and database entry)
  Future<void> deleteDownload(int audiobookId) async {
    await _downloadService.deleteDownload(audiobookId);
    _downloadProgress.remove(audiobookId);
    await _refreshDownloads();
  }

  /// Retry a failed download
  Future<void> retryDownload(Audiobook audiobook) async {
    // Delete existing record and files
    await deleteDownload(audiobook.id);
    // Start fresh download
    await startDownload(audiobook);
  }

  /// Refresh the downloads list
  Future<void> refresh() async {
    await _refreshDownloads();
  }
}
