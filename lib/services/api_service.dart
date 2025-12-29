import 'dart:io';
import 'package:dio/dio.dart';
import 'package:flutter/foundation.dart';
import '../models/audiobook.dart';
import '../models/collection.dart';
import '../models/user.dart';
import 'auth_repository.dart';

/// API service matching Android's SapphoApi interface
/// Uses Dio with interceptor for dynamic base URL and auth token
class ApiService {
  late final Dio _dio;
  final AuthRepository _authRepository;

  // Cached server URL for sync access (Android Auto)
  String? _cachedServerUrl;

  ApiService(this._authRepository) {
    _dio = Dio();
    // Initialize cached URL
    _authRepository.getServerUrl().then((url) => _cachedServerUrl = url);

    // Add interceptor to inject base URL and auth token on every request
    _dio.interceptors.add(
      InterceptorsWrapper(
        onRequest: (options, handler) async {
          final serverUrl = await _authRepository.getServerUrl();
          final token = await _authRepository.getToken();

          if (serverUrl != null) {
            // Rebuild URL with dynamic base
            final path = options.path;
            options.path = '$serverUrl$path';
          }

          if (token != null) {
            options.headers['Authorization'] = 'Bearer $token';
          }

          return handler.next(options);
        },
        onError: (error, handler) async {
          // Handle 401/403 - trigger re-auth
          if (error.response?.statusCode == 401 ||
              error.response?.statusCode == 403) {
            // Could emit an auth error event here
          }
          return handler.next(error);
        },
      ),
    );
  }

  /// Get the current server URL for external use (e.g., Android Auto)
  String get serverUrl => _cachedServerUrl ?? '';

  /// Get the current auth token for external use (e.g., Android Auto)
  Future<String?> getToken() async {
    return _authRepository.getToken();
  }

  // Auth endpoints
  Future<LoginResponse> login(String username, String password) async {
    final response = await _dio.post(
      '/api/auth/login',
      data: {'username': username, 'password': password},
    );
    return LoginResponse.fromJson(response.data);
  }

  Future<User> getProfile() async {
    final response = await _dio.get('/api/profile');
    return User.fromJson(response.data);
  }

  Future<UserStats> getProfileStats() async {
    final response = await _dio.get('/api/profile/stats');
    debugPrint('ApiService.getProfileStats: response = ${response.data}');
    return UserStats.fromJson(response.data);
  }

  Future<User> updateAvatar(File imageFile) async {
    final fileName = imageFile.path.split('/').last;
    final formData = FormData.fromMap({
      'avatar': await MultipartFile.fromFile(
        imageFile.path,
        filename: fileName,
      ),
    });
    final response = await _dio.post('/api/profile/avatar', data: formData);
    return User.fromJson(response.data);
  }

  Future<void> deleteAvatar() async {
    await _dio.delete('/api/profile/avatar');
  }

  Future<User> updateProfile({String? displayName, String? email}) async {
    final response = await _dio.put(
      '/api/profile',
      data: {
        if (displayName != null) 'displayName': displayName,
        if (email != null) 'email': email,
      },
    );
    return User.fromJson(response.data);
  }

  Future<void> updatePassword({
    required String currentPassword,
    required String newPassword,
  }) async {
    await _dio.put(
      '/api/profile/password',
      data: {'currentPassword': currentPassword, 'newPassword': newPassword},
    );
  }

  Future<Map<String, dynamic>> getHealth() async {
    final response = await _dio.get('/api/health');
    return response.data as Map<String, dynamic>;
  }

  // Audiobook metadata endpoints
  Future<List<Audiobook>> getInProgress({int limit = 10}) async {
    final response = await _dio.get(
      '/api/audiobooks/meta/in-progress',
      queryParameters: {'limit': limit},
    );
    return (response.data as List)
        .map((json) => Audiobook.fromJson(json))
        .toList();
  }

  Future<List<Audiobook>> getRecentlyAdded({int limit = 10}) async {
    final response = await _dio.get(
      '/api/audiobooks/meta/recent',
      queryParameters: {'limit': limit},
    );
    return (response.data as List)
        .map((json) => Audiobook.fromJson(json))
        .toList();
  }

  Future<List<Audiobook>> getUpNext({int limit = 10}) async {
    final response = await _dio.get(
      '/api/audiobooks/meta/up-next',
      queryParameters: {'limit': limit},
    );
    return (response.data as List)
        .map((json) => Audiobook.fromJson(json))
        .toList();
  }

  Future<List<Audiobook>> getFinished({int limit = 10}) async {
    final response = await _dio.get(
      '/api/audiobooks/meta/finished',
      queryParameters: {'limit': limit},
    );
    return (response.data as List)
        .map((json) => Audiobook.fromJson(json))
        .toList();
  }

  Future<List<Audiobook>> getAllAudiobooks() async {
    final response = await _dio.get(
      '/api/audiobooks',
      queryParameters: {'limit': 10000},
    );
    // Response format: { audiobooks: [...] }
    final audiobooks = response.data['audiobooks'] as List;
    return audiobooks.map((json) => Audiobook.fromJson(json)).toList();
  }

  Future<List<Audiobook>> searchAudiobooks(
    String query, {
    int limit = 100,
  }) async {
    final response = await _dio.get(
      '/api/audiobooks',
      queryParameters: {'search': query, 'limit': limit},
    );
    // Response format: { audiobooks: [...] }
    final audiobooks = response.data['audiobooks'] as List;
    return audiobooks.map((json) => Audiobook.fromJson(json)).toList();
  }

  Future<Audiobook> getAudiobook(int id) async {
    final response = await _dio.get('/api/audiobooks/$id');
    return Audiobook.fromJson(response.data);
  }

  Future<List<Chapter>> getChapters(int audiobookId) async {
    final response = await _dio.get('/api/audiobooks/$audiobookId/chapters');
    return (response.data as List)
        .map((json) => Chapter.fromJson(json))
        .toList();
  }

  /// Fetch chapters from Audnexus using ASIN
  Future<FetchChaptersResponse> fetchChaptersFromAudnexus(
    int audiobookId,
    String asin,
  ) async {
    final response = await _dio.post(
      '/api/audiobooks/$audiobookId/fetch-chapters',
      data: {'asin': asin},
    );
    return FetchChaptersResponse.fromJson(response.data);
  }

  Future<List<DirectoryFile>> getFiles(int audiobookId) async {
    final response = await _dio.get(
      '/api/audiobooks/$audiobookId/directory-files',
    );
    return (response.data as List)
        .map((json) => DirectoryFile.fromJson(json))
        .toList();
  }

  // Favorites
  Future<List<Audiobook>> getFavorites() async {
    final response = await _dio.get('/api/audiobooks/favorites');
    return (response.data as List)
        .map((json) => Audiobook.fromJson(json))
        .toList();
  }

  Future<FavoriteResponse> toggleFavorite(int audiobookId) async {
    final response = await _dio.post(
      '/api/audiobooks/$audiobookId/favorite/toggle',
    );
    return FavoriteResponse.fromJson(response.data);
  }

  // Progress sync
  Future<Progress?> getProgress(int audiobookId) async {
    final response = await _dio.get('/api/audiobooks/$audiobookId/progress');
    if (response.data != null) {
      return Progress.fromJson(response.data);
    }
    return null;
  }

  Future<void> updateProgress(
    int audiobookId,
    int position, {
    int completed = 0,
    String state = 'paused',
  }) async {
    await _dio.post(
      '/api/audiobooks/$audiobookId/progress',
      data: {'position': position, 'completed': completed, 'state': state},
    );
  }

  Future<void> markFinished(int audiobookId) async {
    await updateProgress(audiobookId, 0, completed: 1, state: 'stopped');
  }

  Future<void> clearProgress(int audiobookId) async {
    await updateProgress(audiobookId, 0, completed: 0, state: 'stopped');
  }

  Future<void> refreshMetadata(int audiobookId) async {
    await _dio.post('/api/audiobooks/$audiobookId/refresh-metadata');
  }

  // Update audiobook metadata (Admin only)
  Future<Audiobook> updateAudiobook(
    int audiobookId,
    AudiobookUpdateRequest request,
  ) async {
    final response = await _dio.put(
      '/api/audiobooks/$audiobookId',
      data: request.toJson(),
    );
    return Audiobook.fromJson(response.data);
  }

  // Search metadata from external sources (Audnexus/Audible)
  Future<List<MetadataSearchResult>> searchMetadata(
    int audiobookId, {
    String? title,
    String? author,
    String? asin,
  }) async {
    final queryParams = <String, dynamic>{};
    if (title != null && title.isNotEmpty) queryParams['title'] = title;
    if (author != null && author.isNotEmpty) queryParams['author'] = author;
    if (asin != null && asin.isNotEmpty) queryParams['asin'] = asin;

    final response = await _dio.get(
      '/api/audiobooks/$audiobookId/search-audnexus',
      queryParameters: queryParams,
    );
    final results = response.data['results'] as List? ?? [];
    return results.map((json) => MetadataSearchResult.fromJson(json)).toList();
  }

  // Embed metadata into audio file tags
  Future<String> embedMetadata(int audiobookId) async {
    final response = await _dio.post(
      '/api/audiobooks/$audiobookId/embed-metadata',
    );
    return response.data['message'] ?? 'Metadata embedded successfully';
  }

  // Ratings
  Future<UserRating?> getUserRating(int audiobookId) async {
    final response = await _dio.get('/api/ratings/audiobook/$audiobookId');
    if (response.data != null && response.data['rating'] != null) {
      return UserRating.fromJson(response.data);
    }
    return null;
  }

  Future<AverageRating?> getAverageRating(int audiobookId) async {
    final response = await _dio.get(
      '/api/ratings/audiobook/$audiobookId/average',
    );
    if (response.data != null) {
      return AverageRating.fromJson(response.data);
    }
    return null;
  }

  Future<void> setRating(int audiobookId, int rating) async {
    await _dio.post(
      '/api/ratings/audiobook/$audiobookId',
      data: {'rating': rating},
    );
  }

  Future<void> deleteRating(int audiobookId) async {
    await _dio.delete('/api/ratings/audiobook/$audiobookId');
  }

  // Genres (normalized from server)
  Future<List<Map<String, dynamic>>> getGenres() async {
    final response = await _dio.get('/api/audiobooks/meta/genres');
    return (response.data as List)
        .map((e) => e as Map<String, dynamic>)
        .toList();
  }

  // Collections
  Future<List<Collection>> getCollections() async {
    final response = await _dio.get('/api/collections');
    return (response.data as List)
        .map((json) => Collection.fromJson(json))
        .toList();
  }

  Future<CollectionDetail> getCollection(int collectionId) async {
    final response = await _dio.get('/api/collections/$collectionId');
    debugPrint('ApiService.getCollection: response = ${response.data}');
    return CollectionDetail.fromJson(response.data);
  }

  Future<Collection> createCollection(
    String name, {
    String? description,
    bool? isPublic,
  }) async {
    final response = await _dio.post(
      '/api/collections',
      data: {
        'name': name,
        if (description != null) 'description': description,
        if (isPublic != null) 'is_public': isPublic,
      },
    );
    return Collection.fromJson(response.data);
  }

  Future<void> updateCollection(
    int collectionId,
    String name, {
    String? description,
    bool? isPublic,
  }) async {
    await _dio.put(
      '/api/collections/$collectionId',
      data: {
        'name': name,
        if (description != null) 'description': description,
        if (isPublic != null) 'is_public': isPublic,
      },
    );
  }

  Future<void> deleteCollection(int collectionId) async {
    await _dio.delete('/api/collections/$collectionId');
  }

  Future<void> addToCollection(int collectionId, int audiobookId) async {
    await _dio.post('/api/collections/$collectionId/books/$audiobookId');
  }

  Future<void> removeFromCollection(int collectionId, int audiobookId) async {
    await _dio.delete('/api/collections/$collectionId/books/$audiobookId');
  }

  // Cover image URL helper
  String getCoverUrl(int audiobookId) {
    // This will be called with the server URL prepended by the image loader
    return '/api/audiobooks/$audiobookId/cover';
  }

  // Stream URL helper (includes token for authentication)
  Future<String> getStreamUrl(int audiobookId) async {
    final serverUrl = await _authRepository.getServerUrl();
    final token = await _authRepository.getToken();
    return '$serverUrl/api/audiobooks/$audiobookId/stream?token=$token';
  }

  // Get server URL for cover images
  Future<String?> getServerUrl() async {
    return await _authRepository.getServerUrl();
  }

  // AI Features
  Future<AiStatus> getAiStatus() async {
    final response = await _dio.get('/api/settings/ai/status');
    return AiStatus.fromJson(response.data);
  }

  Future<AudiobookRecap> getAudiobookRecap(int audiobookId) async {
    final response = await _dio.get('/api/audiobooks/$audiobookId/recap');
    return AudiobookRecap.fromJson(response.data);
  }

  Future<void> clearAudiobookRecap(int audiobookId) async {
    await _dio.delete('/api/audiobooks/$audiobookId/recap');
  }

  Future<SeriesRecap> getSeriesRecap(String seriesName) async {
    final encodedName = Uri.encodeComponent(seriesName);
    final response = await _dio.get('/api/series/$encodedName/recap');
    return SeriesRecap.fromJson(response.data);
  }

  Future<void> clearSeriesRecap(String seriesName) async {
    final encodedName = Uri.encodeComponent(seriesName);
    await _dio.delete('/api/series/$encodedName/recap');
  }

  // Uploads
  Future<Audiobook> uploadAudiobook(
    File audioFile, {
    String? title,
    String? author,
    String? narrator,
    String? series,
    double? seriesPosition,
    String? description,
    String? genre,
    void Function(int, int)? onProgress,
  }) async {
    final fileName = audioFile.path.split('/').last;
    final formData = FormData.fromMap({
      'audiobook': await MultipartFile.fromFile(
        audioFile.path,
        filename: fileName,
      ),
      if (title != null) 'title': title,
      if (author != null) 'author': author,
      if (narrator != null) 'narrator': narrator,
      if (series != null) 'series': series,
      if (seriesPosition != null) 'seriesPosition': seriesPosition,
      if (description != null) 'description': description,
      if (genre != null) 'genre': genre,
    });

    final response = await _dio.post(
      '/api/upload',
      data: formData,
      onSendProgress: onProgress,
    );
    return Audiobook.fromJson(response.data['audiobook']);
  }

  // Admin - Library management
  Future<void> scanLibrary() async {
    await _dio.post('/api/maintenance/scan-library');
  }

  Future<void> refreshLibrary() async {
    await _dio.post('/api/maintenance/force-rescan');
  }

  // Admin - Statistics
  Future<Map<String, dynamic>> getServerStats() async {
    final response = await _dio.get('/api/maintenance/statistics');
    return response.data as Map<String, dynamic>;
  }

  // Admin - User Management
  Future<List<Map<String, dynamic>>> getUsers() async {
    final response = await _dio.get('/api/users');
    return (response.data as List).cast<Map<String, dynamic>>();
  }

  Future<Map<String, dynamic>> createUser({
    required String username,
    required String password,
    String? email,
    String? displayName,
    bool isAdmin = false,
  }) async {
    final response = await _dio.post(
      '/api/users',
      data: {
        'username': username,
        'password': password,
        if (email != null) 'email': email,
        if (displayName != null) 'displayName': displayName,
        'isAdmin': isAdmin ? 1 : 0,
      },
    );
    return response.data as Map<String, dynamic>;
  }

  Future<void> updateUser(
    int userId, {
    String? displayName,
    String? email,
    bool? isAdmin,
    String? password,
  }) async {
    await _dio.put(
      '/api/users/$userId',
      data: {
        if (displayName != null) 'displayName': displayName,
        if (email != null) 'email': email,
        if (isAdmin != null) 'isAdmin': isAdmin ? 1 : 0,
        if (password != null) 'password': password,
      },
    );
  }

  Future<void> deleteUser(int userId) async {
    await _dio.delete('/api/users/$userId');
  }

  Future<void> disableUser(int userId) async {
    await _dio.post('/api/users/$userId/disable');
  }

  Future<void> enableUser(int userId) async {
    await _dio.post('/api/users/$userId/enable');
  }

  // Admin - Backup
  Future<List<Map<String, dynamic>>> getBackups() async {
    final response = await _dio.get('/api/backup');
    return (response.data['backups'] as List? ?? [])
        .cast<Map<String, dynamic>>();
  }

  Future<Map<String, dynamic>> createBackup() async {
    final response = await _dio.post('/api/backup');
    return response.data as Map<String, dynamic>;
  }

  Future<String> getBackupDownloadUrl(String filename) async {
    final serverUrl = await _authRepository.getServerUrl();
    final token = await _authRepository.getToken();
    return '$serverUrl/api/backup/download/$filename?token=$token';
  }

  Future<void> restoreBackup(File backupFile) async {
    final fileName = backupFile.path.split('/').last;
    final formData = FormData.fromMap({
      'backup': await MultipartFile.fromFile(
        backupFile.path,
        filename: fileName,
      ),
    });
    await _dio.post('/api/backup/upload', data: formData);
  }

  // Admin - User Management (additional)
  Future<void> unlockUser(int userId) async {
    await _dio.post('/api/users/$userId/unlock');
  }

  // Admin - Server Settings
  Future<Map<String, dynamic>> getServerSettings() async {
    final response = await _dio.get('/api/settings/all');
    return response.data as Map<String, dynamic>;
  }

  Future<void> updateServerSettings(Map<String, dynamic> settings) async {
    await _dio.put('/api/settings/all', data: settings);
  }

  // Admin - AI Settings
  Future<Map<String, dynamic>> getAISettings() async {
    final response = await _dio.get('/api/settings/ai');
    return response.data as Map<String, dynamic>;
  }

  Future<void> updateAISettings(Map<String, dynamic> settings) async {
    await _dio.put('/api/settings/ai', data: settings);
  }

  Future<Map<String, dynamic>> testAIConnection(
    Map<String, dynamic> settings,
  ) async {
    final response = await _dio.post('/api/settings/ai/test', data: settings);
    return response.data as Map<String, dynamic>;
  }

  // Admin - Email Settings
  Future<Map<String, dynamic>> getEmailSettings() async {
    final response = await _dio.get('/api/email/settings');
    return response.data as Map<String, dynamic>;
  }

  Future<void> updateEmailSettings(Map<String, dynamic> settings) async {
    await _dio.put('/api/email/settings', data: settings);
  }

  Future<Map<String, dynamic>> testEmailConnection(
    Map<String, dynamic> settings,
  ) async {
    final response = await _dio.post(
      '/api/email/test-connection',
      data: settings,
    );
    return response.data as Map<String, dynamic>;
  }

  Future<void> sendTestEmail(String to) async {
    await _dio.post('/api/email/send-test', data: {'to': to});
  }

  // Admin - API Keys
  Future<List<Map<String, dynamic>>> getApiKeys() async {
    final response = await _dio.get('/api/api-keys');
    return (response.data as List).cast<Map<String, dynamic>>();
  }

  Future<Map<String, dynamic>> createApiKey({
    required String name,
    String? permissions,
    int? expiresInDays,
  }) async {
    final response = await _dio.post(
      '/api/api-keys',
      data: {
        'name': name,
        if (permissions != null) 'permissions': permissions,
        if (expiresInDays != null) 'expires_in_days': expiresInDays,
      },
    );
    return response.data as Map<String, dynamic>;
  }

  Future<void> updateApiKey(int keyId, {String? name, bool? isActive}) async {
    await _dio.put(
      '/api/api-keys/$keyId',
      data: {
        if (name != null) 'name': name,
        if (isActive != null) 'is_active': isActive,
      },
    );
  }

  Future<void> deleteApiKey(int keyId) async {
    await _dio.delete('/api/api-keys/$keyId');
  }

  // Admin - Jobs
  Future<Map<String, dynamic>> getJobsStatus() async {
    final response = await _dio.get('/api/maintenance/jobs');
    return response.data as Map<String, dynamic>;
  }

  // Admin - Logs
  Future<Map<String, dynamic>> getServerLogs({int limit = 200}) async {
    final response = await _dio.get(
      '/api/maintenance/logs',
      queryParameters: {'limit': limit},
    );
    return response.data as Map<String, dynamic>;
  }

  Future<void> clearServerLogs() async {
    await _dio.delete('/api/maintenance/logs');
  }

  // Admin - Duplicates
  Future<Map<String, dynamic>> getDuplicates() async {
    final response = await _dio.get('/api/maintenance/duplicates');
    return response.data as Map<String, dynamic>;
  }

  Future<Map<String, dynamic>> mergeDuplicates({
    required int keepId,
    required List<int> deleteIds,
    bool deleteFiles = false,
  }) async {
    final response = await _dio.post(
      '/api/maintenance/duplicates/merge',
      data: {
        'keepId': keepId,
        'deleteIds': deleteIds,
        'deleteFiles': deleteFiles,
      },
    );
    return response.data as Map<String, dynamic>;
  }

  // Admin - Orphan Directories
  Future<Map<String, dynamic>> getOrphanDirectories() async {
    final response = await _dio.get('/api/maintenance/orphan-directories');
    return response.data as Map<String, dynamic>;
  }

  Future<Map<String, dynamic>> deleteOrphanDirectories(
    List<String> paths,
  ) async {
    final response = await _dio.delete(
      '/api/maintenance/orphan-directories',
      data: {'paths': paths},
    );
    return response.data as Map<String, dynamic>;
  }

  // Admin - Library Organization
  Future<Map<String, dynamic>> getOrganizationPreview() async {
    final response = await _dio.get('/api/maintenance/organize/preview');
    return response.data as Map<String, dynamic>;
  }

  Future<Map<String, dynamic>> organizeLibrary() async {
    final response = await _dio.post('/api/maintenance/organize');
    return response.data as Map<String, dynamic>;
  }
}

class LoginResponse {
  final String token;
  final User user;

  LoginResponse({required this.token, required this.user});

  factory LoginResponse.fromJson(Map<String, dynamic> json) {
    return LoginResponse(
      token: json['token'],
      user: User.fromJson(json['user']),
    );
  }
}

class FavoriteResponse {
  final bool success;
  final bool isFavorite;

  FavoriteResponse({required this.success, required this.isFavorite});

  factory FavoriteResponse.fromJson(Map<String, dynamic> json) {
    return FavoriteResponse(
      success: json['success'] ?? true,
      isFavorite: json['is_favorite'] == true || json['is_favorite'] == 1,
    );
  }
}

class UserRating {
  final int? rating;

  UserRating({this.rating});

  factory UserRating.fromJson(Map<String, dynamic> json) {
    return UserRating(rating: json['rating']);
  }
}

class AverageRating {
  final double? average;
  final int count;

  AverageRating({this.average, required this.count});

  factory AverageRating.fromJson(Map<String, dynamic> json) {
    return AverageRating(
      average: json['average']?.toDouble(),
      count: json['count'] ?? 0,
    );
  }
}

class AiStatus {
  final bool configured;
  final String? provider;

  AiStatus({required this.configured, this.provider});

  factory AiStatus.fromJson(Map<String, dynamic> json) {
    return AiStatus(
      configured: json['configured'] ?? false,
      provider: json['provider'],
    );
  }
}

class AudiobookRecap {
  final String recap;
  final bool cached;
  final String? previousBookTitle;
  final List<RecapBookInfo>? booksIncluded;

  AudiobookRecap({
    required this.recap,
    required this.cached,
    this.previousBookTitle,
    this.booksIncluded,
  });

  factory AudiobookRecap.fromJson(Map<String, dynamic> json) {
    return AudiobookRecap(
      recap: json['recap'] ?? '',
      cached: json['cached'] ?? false,
      previousBookTitle: json['previousBookTitle'],
      booksIncluded: (json['booksIncluded'] as List?)
          ?.map((e) => RecapBookInfo.fromJson(e))
          .toList(),
    );
  }
}

class SeriesRecap {
  final String recap;
  final bool cached;
  final List<RecapBookInfo> booksIncluded;

  SeriesRecap({
    required this.recap,
    required this.cached,
    required this.booksIncluded,
  });

  factory SeriesRecap.fromJson(Map<String, dynamic> json) {
    return SeriesRecap(
      recap: json['recap'] ?? '',
      cached: json['cached'] ?? false,
      booksIncluded:
          (json['booksIncluded'] as List?)
              ?.map((e) => RecapBookInfo.fromJson(e))
              .toList() ??
          [],
    );
  }
}

class RecapBookInfo {
  final String title;
  final double? seriesPosition;

  RecapBookInfo({required this.title, this.seriesPosition});

  factory RecapBookInfo.fromJson(Map<String, dynamic> json) {
    return RecapBookInfo(
      title: json['title'] ?? '',
      seriesPosition:
          json['series_position']?.toDouble() ??
          json['seriesPosition']?.toDouble(),
    );
  }
}

class AudiobookUpdateRequest {
  final String? title;
  final String? subtitle;
  final String? author;
  final String? narrator;
  final String? description;
  final String? genre;
  final String? tags;
  final String? series;
  final double? seriesPosition;
  final int? publishedYear;
  final int? copyrightYear;
  final String? publisher;
  final String? isbn;
  final String? asin;
  final String? language;
  final double? rating;
  final bool? abridged;
  final String? coverUrl;

  AudiobookUpdateRequest({
    this.title,
    this.subtitle,
    this.author,
    this.narrator,
    this.description,
    this.genre,
    this.tags,
    this.series,
    this.seriesPosition,
    this.publishedYear,
    this.copyrightYear,
    this.publisher,
    this.isbn,
    this.asin,
    this.language,
    this.rating,
    this.abridged,
    this.coverUrl,
  });

  Map<String, dynamic> toJson() {
    final map = <String, dynamic>{};
    if (title != null) map['title'] = title;
    if (subtitle != null) map['subtitle'] = subtitle;
    if (author != null) map['author'] = author;
    if (narrator != null) map['narrator'] = narrator;
    if (description != null) map['description'] = description;
    if (genre != null) map['genre'] = genre;
    if (tags != null) map['tags'] = tags;
    if (series != null) map['series'] = series;
    if (seriesPosition != null) map['series_position'] = seriesPosition;
    if (publishedYear != null) map['published_year'] = publishedYear;
    if (copyrightYear != null) map['copyright_year'] = copyrightYear;
    if (publisher != null) map['publisher'] = publisher;
    if (isbn != null) map['isbn'] = isbn;
    if (asin != null) map['asin'] = asin;
    if (language != null) map['language'] = language;
    if (rating != null) map['rating'] = rating;
    if (abridged != null) map['abridged'] = abridged;
    if (coverUrl != null) map['cover_url'] = coverUrl;
    return map;
  }
}

/// Metadata search result from external sources (Audnexus/Audible)
class MetadataSearchResult {
  final String source;
  final String? asin;
  final String? title;
  final String? subtitle;
  final String? author;
  final String? narrator;
  final String? series;
  final double? seriesPosition;
  final String? publisher;
  final int? publishedYear;
  final int? copyrightYear;
  final String? isbn;
  final String? description;
  final String? genre;
  final String? tags;
  final double? rating;
  final String? image;
  final String? language;
  final int? abridged;
  final bool? hasChapters;

  MetadataSearchResult({
    required this.source,
    this.asin,
    this.title,
    this.subtitle,
    this.author,
    this.narrator,
    this.series,
    this.seriesPosition,
    this.publisher,
    this.publishedYear,
    this.copyrightYear,
    this.isbn,
    this.description,
    this.genre,
    this.tags,
    this.rating,
    this.image,
    this.language,
    this.abridged,
    this.hasChapters,
  });

  factory MetadataSearchResult.fromJson(Map<String, dynamic> json) {
    // Helper to parse numeric values that might be strings
    double? parseDouble(dynamic value) {
      if (value == null) return null;
      if (value is num) return value.toDouble();
      if (value is String) return double.tryParse(value);
      return null;
    }

    int? parseInt(dynamic value) {
      if (value == null) return null;
      if (value is int) return value;
      if (value is num) return value.toInt();
      if (value is String) return int.tryParse(value);
      return null;
    }

    return MetadataSearchResult(
      source: json['source']?.toString() ?? 'Unknown',
      asin: json['asin']?.toString(),
      title: json['title']?.toString(),
      subtitle: json['subtitle']?.toString(),
      author: json['author']?.toString(),
      narrator: json['narrator']?.toString(),
      series: json['series']?.toString(),
      seriesPosition: parseDouble(json['series_position']),
      publisher: json['publisher']?.toString(),
      publishedYear: parseInt(json['published_year']),
      copyrightYear: parseInt(json['copyright_year']),
      isbn: json['isbn']?.toString(),
      description: json['description']?.toString(),
      genre: json['genre']?.toString(),
      tags: json['tags']?.toString(),
      rating: parseDouble(json['rating']),
      image: json['image']?.toString(),
      language: json['language']?.toString(),
      abridged: parseInt(json['abridged']),
      hasChapters: json['hasChapters'] == true || json['hasChapters'] == 1,
    );
  }

  /// Convert to AudiobookUpdateRequest for applying this metadata
  AudiobookUpdateRequest toUpdateRequest() {
    return AudiobookUpdateRequest(
      title: title,
      subtitle: subtitle,
      author: author,
      narrator: narrator,
      description: description,
      genre: genre,
      tags: tags,
      series: series,
      seriesPosition: seriesPosition,
      publishedYear: publishedYear,
      copyrightYear: copyrightYear,
      publisher: publisher,
      isbn: isbn,
      asin: asin,
      language: language,
      rating: rating,
      abridged: abridged == 1,
      coverUrl: image,
    );
  }
}

/// Response from fetch chapters API
class FetchChaptersResponse {
  final String? message;
  final int? chaptersUpdated;

  FetchChaptersResponse({this.message, this.chaptersUpdated});

  factory FetchChaptersResponse.fromJson(Map<String, dynamic> json) {
    return FetchChaptersResponse(
      message: json['message']?.toString(),
      chaptersUpdated: json['chapters_updated'] is int
          ? json['chapters_updated']
          : null,
    );
  }
}
