import 'dart:io';
import 'package:flutter/foundation.dart';
import 'package:path_provider/path_provider.dart';
import 'package:sqflite/sqflite.dart';
import 'package:path/path.dart' as path;
import 'package:dio/dio.dart';
import '../models/audiobook.dart';
import 'auth_repository.dart';

/// Download status enum
enum DownloadStatus { pending, downloading, completed, failed, paused }

/// Downloaded audiobook model
class DownloadedAudiobook {
  final int id;
  final int audiobookId;
  final String title;
  final String? author;
  final String? coverPath;
  final String? audioPath;
  final int totalBytes;
  final int downloadedBytes;
  final DownloadStatus status;
  final String? error;
  final DateTime createdAt;
  final DateTime? completedAt;

  DownloadedAudiobook({
    required this.id,
    required this.audiobookId,
    required this.title,
    this.author,
    this.coverPath,
    this.audioPath,
    required this.totalBytes,
    required this.downloadedBytes,
    required this.status,
    this.error,
    required this.createdAt,
    this.completedAt,
  });

  double get progress => totalBytes > 0 ? downloadedBytes / totalBytes : 0;
  bool get isCompleted => status == DownloadStatus.completed;
  bool get isDownloading => status == DownloadStatus.downloading;

  factory DownloadedAudiobook.fromMap(Map<String, dynamic> map) {
    return DownloadedAudiobook(
      id: map['id'],
      audiobookId: map['audiobook_id'],
      title: map['title'],
      author: map['author'],
      coverPath: map['cover_path'],
      audioPath: map['audio_path'],
      totalBytes: map['total_bytes'] ?? 0,
      downloadedBytes: map['downloaded_bytes'] ?? 0,
      status: DownloadStatus.values[map['status'] ?? 0],
      error: map['error'],
      createdAt: DateTime.parse(map['created_at']),
      completedAt: map['completed_at'] != null
          ? DateTime.parse(map['completed_at'])
          : null,
    );
  }

  Map<String, dynamic> toMap() {
    return {
      'id': id,
      'audiobook_id': audiobookId,
      'title': title,
      'author': author,
      'cover_path': coverPath,
      'audio_path': audioPath,
      'total_bytes': totalBytes,
      'downloaded_bytes': downloadedBytes,
      'status': status.index,
      'error': error,
      'created_at': createdAt.toIso8601String(),
      'completed_at': completedAt?.toIso8601String(),
    };
  }

  DownloadedAudiobook copyWith({
    int? id,
    int? audiobookId,
    String? title,
    String? author,
    String? coverPath,
    String? audioPath,
    int? totalBytes,
    int? downloadedBytes,
    DownloadStatus? status,
    String? error,
    DateTime? createdAt,
    DateTime? completedAt,
  }) {
    return DownloadedAudiobook(
      id: id ?? this.id,
      audiobookId: audiobookId ?? this.audiobookId,
      title: title ?? this.title,
      author: author ?? this.author,
      coverPath: coverPath ?? this.coverPath,
      audioPath: audioPath ?? this.audioPath,
      totalBytes: totalBytes ?? this.totalBytes,
      downloadedBytes: downloadedBytes ?? this.downloadedBytes,
      status: status ?? this.status,
      error: error ?? this.error,
      createdAt: createdAt ?? this.createdAt,
      completedAt: completedAt ?? this.completedAt,
    );
  }
}

/// Service for managing audiobook downloads
class DownloadService {
  static final DownloadService _instance = DownloadService._internal();
  factory DownloadService() => _instance;
  DownloadService._internal();

  Database? _database;
  final Dio _dio = Dio();
  final Map<int, CancelToken> _cancelTokens = {};

  // Callbacks for UI updates
  Function(int audiobookId, double progress)? onProgressUpdate;
  Function(int audiobookId, DownloadStatus status)? onStatusChange;
  Function()? onDownloadsChanged;

  Future<Database> get database async {
    if (_database != null) return _database!;
    _database = await _initDatabase();
    return _database!;
  }

  Future<Database> _initDatabase() async {
    final documentsDir = await getApplicationDocumentsDirectory();
    final dbPath = path.join(documentsDir.path, 'downloads.db');

    return openDatabase(
      dbPath,
      version: 1,
      onCreate: (db, version) async {
        await db.execute('''
          CREATE TABLE downloads (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            audiobook_id INTEGER UNIQUE,
            title TEXT NOT NULL,
            author TEXT,
            cover_path TEXT,
            audio_path TEXT,
            total_bytes INTEGER DEFAULT 0,
            downloaded_bytes INTEGER DEFAULT 0,
            status INTEGER DEFAULT 0,
            error TEXT,
            created_at TEXT NOT NULL,
            completed_at TEXT
          )
        ''');
      },
    );
  }

  Future<String> get _downloadsDir async {
    final appDir = await getApplicationDocumentsDirectory();
    final downloadsDir = Directory(path.join(appDir.path, 'downloads'));
    if (!await downloadsDir.exists()) {
      await downloadsDir.create(recursive: true);
    }
    return downloadsDir.path;
  }

  /// Get all downloads
  Future<List<DownloadedAudiobook>> getDownloads() async {
    final db = await database;
    final maps = await db.query('downloads', orderBy: 'created_at DESC');
    return maps.map((m) => DownloadedAudiobook.fromMap(m)).toList();
  }

  /// Get download by audiobook ID
  Future<DownloadedAudiobook?> getDownload(int audiobookId) async {
    final db = await database;
    final maps = await db.query(
      'downloads',
      where: 'audiobook_id = ?',
      whereArgs: [audiobookId],
    );
    if (maps.isEmpty) return null;
    return DownloadedAudiobook.fromMap(maps.first);
  }

  /// Check if audiobook is downloaded
  Future<bool> isDownloaded(int audiobookId) async {
    final download = await getDownload(audiobookId);
    return download?.isCompleted ?? false;
  }

  /// Get local audio path for a downloaded audiobook
  Future<String?> getLocalAudioPath(int audiobookId) async {
    final download = await getDownload(audiobookId);
    if (download?.isCompleted == true && download?.audioPath != null) {
      final file = File(download!.audioPath!);
      if (await file.exists()) {
        return download.audioPath;
      }
    }
    return null;
  }

  /// Start downloading an audiobook
  Future<void> startDownload(
    Audiobook audiobook,
    AuthRepository authRepository,
  ) async {
    final db = await database;
    final serverUrl = await authRepository.getServerUrl();
    final token = await authRepository.getToken();

    if (serverUrl == null || token == null) {
      throw Exception('Not authenticated');
    }

    // Check if already exists
    final existing = await getDownload(audiobook.id);
    if (existing != null) {
      if (existing.isCompleted) {
        debugPrint('DownloadService: Already downloaded');
        return;
      }
      if (existing.isDownloading) {
        debugPrint('DownloadService: Already downloading');
        return;
      }
    }

    final dir = await _downloadsDir;
    final audioPath = path.join(dir, 'audio_${audiobook.id}.m4b');
    final coverPath = path.join(dir, 'cover_${audiobook.id}.jpg');

    // Insert or update download record
    final now = DateTime.now();
    if (existing == null) {
      await db.insert('downloads', {
        'audiobook_id': audiobook.id,
        'title': audiobook.title,
        'author': audiobook.author,
        'audio_path': audioPath,
        'cover_path': coverPath,
        'status': DownloadStatus.pending.index,
        'created_at': now.toIso8601String(),
      });
    } else {
      await db.update(
        'downloads',
        {'status': DownloadStatus.pending.index, 'error': null},
        where: 'audiobook_id = ?',
        whereArgs: [audiobook.id],
      );
    }

    onDownloadsChanged?.call();

    // Download cover first (non-blocking)
    _downloadCover(audiobook.id, serverUrl, token, coverPath);

    // Download audio
    await _downloadAudio(audiobook.id, serverUrl, token, audioPath);
  }

  Future<void> _downloadCover(
    int audiobookId,
    String serverUrl,
    String token,
    String coverPath,
  ) async {
    try {
      final url = '$serverUrl/api/audiobooks/$audiobookId/cover';
      await _dio.download(
        url,
        coverPath,
        options: Options(headers: {'Authorization': 'Bearer $token'}),
      );
      debugPrint('DownloadService: Cover downloaded for $audiobookId');
    } catch (e) {
      debugPrint('DownloadService: Failed to download cover: $e');
    }
  }

  Future<void> _downloadAudio(
    int audiobookId,
    String serverUrl,
    String token,
    String audioPath,
  ) async {
    final db = await database;
    final cancelToken = CancelToken();
    _cancelTokens[audiobookId] = cancelToken;

    try {
      // Update status to downloading
      await db.update(
        'downloads',
        {'status': DownloadStatus.downloading.index},
        where: 'audiobook_id = ?',
        whereArgs: [audiobookId],
      );
      onStatusChange?.call(audiobookId, DownloadStatus.downloading);
      onDownloadsChanged?.call();

      final url = '$serverUrl/api/audiobooks/$audiobookId/stream?token=$token';

      await _dio.download(
        url,
        audioPath,
        cancelToken: cancelToken,
        onReceiveProgress: (received, total) async {
          if (total > 0) {
            final progress = received / total;
            onProgressUpdate?.call(audiobookId, progress);

            // Update database periodically (every 5%)
            if ((received * 100 / total).floor() % 5 == 0) {
              await db.update(
                'downloads',
                {'downloaded_bytes': received, 'total_bytes': total},
                where: 'audiobook_id = ?',
                whereArgs: [audiobookId],
              );
            }
          }
        },
      );

      // Download completed
      await db.update(
        'downloads',
        {
          'status': DownloadStatus.completed.index,
          'completed_at': DateTime.now().toIso8601String(),
        },
        where: 'audiobook_id = ?',
        whereArgs: [audiobookId],
      );
      onStatusChange?.call(audiobookId, DownloadStatus.completed);
      onDownloadsChanged?.call();
      debugPrint('DownloadService: Audio downloaded for $audiobookId');
    } on DioException catch (e) {
      if (e.type == DioExceptionType.cancel) {
        debugPrint('DownloadService: Download cancelled for $audiobookId');
        await db.update(
          'downloads',
          {'status': DownloadStatus.paused.index},
          where: 'audiobook_id = ?',
          whereArgs: [audiobookId],
        );
        onStatusChange?.call(audiobookId, DownloadStatus.paused);
      } else {
        debugPrint('DownloadService: Download failed: $e');
        await db.update(
          'downloads',
          {'status': DownloadStatus.failed.index, 'error': e.message},
          where: 'audiobook_id = ?',
          whereArgs: [audiobookId],
        );
        onStatusChange?.call(audiobookId, DownloadStatus.failed);
      }
      onDownloadsChanged?.call();
    } finally {
      _cancelTokens.remove(audiobookId);
    }
  }

  /// Cancel a download
  Future<void> cancelDownload(int audiobookId) async {
    final cancelToken = _cancelTokens[audiobookId];
    if (cancelToken != null && !cancelToken.isCancelled) {
      cancelToken.cancel();
    }
  }

  /// Delete a download
  Future<void> deleteDownload(int audiobookId) async {
    // Cancel if downloading
    await cancelDownload(audiobookId);

    final download = await getDownload(audiobookId);
    if (download == null) return;

    // Delete files
    if (download.audioPath != null) {
      final audioFile = File(download.audioPath!);
      if (await audioFile.exists()) {
        await audioFile.delete();
      }
    }
    if (download.coverPath != null) {
      final coverFile = File(download.coverPath!);
      if (await coverFile.exists()) {
        await coverFile.delete();
      }
    }

    // Delete from database
    final db = await database;
    await db.delete(
      'downloads',
      where: 'audiobook_id = ?',
      whereArgs: [audiobookId],
    );

    onDownloadsChanged?.call();
    debugPrint('DownloadService: Deleted download for $audiobookId');
  }

  /// Get total storage used by downloads
  Future<int> getStorageUsed() async {
    final dir = await _downloadsDir;
    final directory = Directory(dir);
    if (!await directory.exists()) return 0;

    int totalSize = 0;
    await for (final entity in directory.list(recursive: true)) {
      if (entity is File) {
        totalSize += await entity.length();
      }
    }
    return totalSize;
  }

  /// Format bytes to human readable string
  static String formatBytes(int bytes) {
    if (bytes < 1024) return '$bytes B';
    if (bytes < 1024 * 1024) return '${(bytes / 1024).toStringAsFixed(1)} KB';
    if (bytes < 1024 * 1024 * 1024) {
      return '${(bytes / (1024 * 1024)).toStringAsFixed(1)} MB';
    }
    return '${(bytes / (1024 * 1024 * 1024)).toStringAsFixed(2)} GB';
  }
}
