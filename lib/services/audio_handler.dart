import 'package:audio_service/audio_service.dart';
import 'package:just_audio/just_audio.dart';
import 'package:flutter/foundation.dart';
import 'dart:io';
import '../models/audiobook.dart';
import 'api_service.dart';

/// Audio handler for background playback and media notifications
/// Integrates just_audio with audio_service for system media controls
/// Supports Android Auto media browsing
class SapphoAudioHandler extends BaseAudioHandler with SeekHandler {
  final AudioPlayer _player = AudioPlayer();

  // API service for Android Auto browsing (set after init)
  ApiService? _apiService;

  // Root media IDs for Android Auto browsing
  static const String rootId = 'root';
  static const String continueListeningId = 'continue_listening';
  static const String recentlyAddedId = 'recently_added';
  static const String libraryId = 'library';

  SapphoAudioHandler() {
    _init();
  }

  /// Set the API service for Android Auto browsing
  void setApiService(ApiService apiService) {
    _apiService = apiService;
  }

  AudioPlayer get player => _player;

  void _init() {
    // Broadcast playback state changes
    _player.playbackEventStream.listen((event) {
      _broadcastState();
    });

    // Handle player state changes
    _player.playerStateStream.listen((state) {
      debugPrint(
        'SapphoAudioHandler: Player state - playing: ${state.playing}, processingState: ${state.processingState}',
      );
    });

    // Handle processing state for completion
    _player.processingStateStream.listen((state) {
      if (state == ProcessingState.completed) {
        stop();
      }
    });
  }

  /// Load and play audio with media notification
  Future<void> loadAndPlayAudio({
    required String url,
    required Map<String, String> headers,
    required int audiobookId,
    required String title,
    String? author,
    String? artUrl,
    int startPositionSeconds = 0,
    bool isLocalFile = false,
  }) async {
    // Set media item for notification
    final item = MediaItem(
      id: audiobookId.toString(),
      title: title,
      artist: author,
      artUri: artUrl != null ? Uri.parse(artUrl) : null,
      duration: _player.duration,
    );
    mediaItem.add(item);

    try {
      // Stop any current playback
      await _player.stop();

      // Set audio source
      AudioSource audioSource;
      if (isLocalFile) {
        final file = File(url);
        if (!await file.exists()) {
          throw Exception('Local audio file not found: $url');
        }
        audioSource = AudioSource.file(url);
        debugPrint('SapphoAudioHandler: Using local file source');
      } else {
        audioSource = AudioSource.uri(Uri.parse(url), headers: headers);
        debugPrint('SapphoAudioHandler: Using remote URL source');
      }

      final duration = await _player.setAudioSource(
        audioSource,
        initialPosition: Duration(seconds: startPositionSeconds),
      );

      // Update media item with actual duration
      if (duration != null) {
        mediaItem.add(item.copyWith(duration: duration));
      }

      debugPrint('SapphoAudioHandler: Audio source set, duration: $duration');
      await _player.play();
    } catch (e, stackTrace) {
      debugPrint('SapphoAudioHandler: Error loading audio: $e');
      debugPrint('SapphoAudioHandler: Stack trace: $stackTrace');
      rethrow;
    }
  }

  /// Update the current media item metadata (e.g., when artwork is loaded)
  void updateCurrentMediaItem({String? title, String? author, String? artUrl}) {
    final current = mediaItem.value;
    if (current != null) {
      mediaItem.add(
        current.copyWith(
          title: title ?? current.title,
          artist: author ?? current.artist,
          artUri: artUrl != null ? Uri.parse(artUrl) : current.artUri,
        ),
      );
    }
  }

  @override
  Future<void> play() async {
    await _player.play();
  }

  @override
  Future<void> pause() async {
    await _player.pause();
  }

  @override
  Future<void> stop() async {
    await _player.stop();
    await super.stop();
  }

  @override
  Future<void> seek(Duration position) async {
    await _player.seek(position);
  }

  @override
  Future<void> skipToNext() async {
    // Skip forward 10 seconds
    final newPosition = _player.position + const Duration(seconds: 10);
    final maxPosition = _player.duration ?? Duration.zero;
    if (newPosition < maxPosition) {
      await _player.seek(newPosition);
    } else {
      await _player.seek(maxPosition);
    }
  }

  @override
  Future<void> skipToPrevious() async {
    // Skip backward 10 seconds
    final newPosition = _player.position - const Duration(seconds: 10);
    if (newPosition > Duration.zero) {
      await _player.seek(newPosition);
    } else {
      await _player.seek(Duration.zero);
    }
  }

  @override
  Future<void> fastForward() async {
    await skipToNext();
  }

  @override
  Future<void> rewind() async {
    await skipToPrevious();
  }

  Future<void> setSpeed(double speed) async {
    await _player.setSpeed(speed);
  }

  void _broadcastState() {
    final playing = _player.playing;
    playbackState.add(
      playbackState.value.copyWith(
        controls: [
          MediaControl.rewind,
          if (playing) MediaControl.pause else MediaControl.play,
          MediaControl.fastForward,
        ],
        systemActions: const {
          MediaAction.seek,
          MediaAction.seekForward,
          MediaAction.seekBackward,
        },
        androidCompactActionIndices: const [0, 1, 2],
        processingState: const {
          ProcessingState.idle: AudioProcessingState.idle,
          ProcessingState.loading: AudioProcessingState.loading,
          ProcessingState.buffering: AudioProcessingState.buffering,
          ProcessingState.ready: AudioProcessingState.ready,
          ProcessingState.completed: AudioProcessingState.completed,
        }[_player.processingState]!,
        playing: playing,
        updatePosition: _player.position,
        bufferedPosition: _player.bufferedPosition,
        speed: _player.speed,
      ),
    );
  }

  @override
  Future<void> onTaskRemoved() async {
    await stop();
    await super.onTaskRemoved();
  }

  /// Android Auto media browsing support
  @override
  Future<List<MediaItem>> getChildren(
    String parentMediaId, [
    Map<String, dynamic>? options,
  ]) async {
    debugPrint(
      'SapphoAudioHandler: getChildren called with parentMediaId: $parentMediaId',
    );

    if (_apiService == null) {
      debugPrint(
        'SapphoAudioHandler: API service not set, returning empty list',
      );
      return [];
    }

    try {
      if (parentMediaId == rootId ||
          parentMediaId == AudioService.browsableRootId) {
        // Return root menu items
        return [
          const MediaItem(
            id: continueListeningId,
            title: 'Continue Listening',
            playable: false,
          ),
          const MediaItem(
            id: recentlyAddedId,
            title: 'Recently Added',
            playable: false,
          ),
          const MediaItem(id: libraryId, title: 'Library', playable: false),
        ];
      } else if (parentMediaId == continueListeningId) {
        // Get in-progress audiobooks
        final books = await _apiService!.getInProgress(limit: 20);
        return await _audiobooksToMediaItems(books);
      } else if (parentMediaId == recentlyAddedId) {
        // Get recently added audiobooks
        final books = await _apiService!.getRecentlyAdded(limit: 20);
        return await _audiobooksToMediaItems(books);
      } else if (parentMediaId == libraryId) {
        // Get all audiobooks
        final books = await _apiService!.getAllAudiobooks();
        return await _audiobooksToMediaItems(books.take(50).toList());
      }
    } catch (e) {
      debugPrint('SapphoAudioHandler: Error getting children: $e');
    }

    return [];
  }

  /// Convert audiobooks to media items for Android Auto
  Future<List<MediaItem>> _audiobooksToMediaItems(List<Audiobook> books) async {
    // Get token for authenticated cover URLs (Android Auto can't use headers)
    final token = await _apiService?.getToken();
    final serverUrl = _apiService?.serverUrl ?? '';

    return books.map((book) {
      // Include token in URL so Android Auto can fetch covers without headers
      final artUrl = book.coverImage != null && token != null
          ? '$serverUrl/api/audiobooks/${book.id}/cover?token=$token'
          : null;

      return MediaItem(
        id: 'audiobook_${book.id}',
        title: book.title,
        artist: book.author,
        album: book.series,
        artUri: artUrl != null ? Uri.parse(artUrl) : null,
        duration: book.duration != null
            ? Duration(seconds: book.duration!)
            : null,
        playable: true,
        extras: {
          'audiobookId': book.id,
          'position': book.progress?.position ?? 0,
        },
      );
    }).toList();
  }

  /// Handle Android Auto search
  @override
  Future<List<MediaItem>> search(
    String query, [
    Map<String, dynamic>? extras,
  ]) async {
    debugPrint('SapphoAudioHandler: search called with query: $query');

    if (_apiService == null || query.isEmpty) {
      return [];
    }

    try {
      final books = await _apiService!.searchAudiobooks(query, limit: 20);
      return await _audiobooksToMediaItems(books);
    } catch (e) {
      debugPrint('SapphoAudioHandler: Error searching: $e');
      return [];
    }
  }

  /// Handle Android Auto voice search / play from search
  @override
  Future<void> playFromSearch(
    String query, [
    Map<String, dynamic>? extras,
  ]) async {
    debugPrint('SapphoAudioHandler: playFromSearch called with query: $query');

    if (_apiService == null || query.isEmpty) {
      return;
    }

    try {
      final books = await _apiService!.searchAudiobooks(query, limit: 1);
      if (books.isNotEmpty) {
        // Play the first matching result
        await playFromMediaId('audiobook_${books.first.id}');
      }
    } catch (e) {
      debugPrint('SapphoAudioHandler: Error playing from search: $e');
    }
  }

  /// Handle Android Auto item selection
  @override
  Future<void> playFromMediaId(
    String mediaId, [
    Map<String, dynamic>? extras,
  ]) async {
    debugPrint('SapphoAudioHandler: playFromMediaId called with: $mediaId');

    if (mediaId.startsWith('audiobook_')) {
      final audiobookId = int.tryParse(mediaId.replaceFirst('audiobook_', ''));
      if (audiobookId != null && _apiService != null) {
        try {
          // Get audiobook details
          final book = await _apiService!.getAudiobook(audiobookId);
          final serverUrl = _apiService!.serverUrl;
          final token = await _apiService!.getToken();

          // Get progress - fetch explicitly if not included with book
          int startPosition = book.progress?.position ?? 0;
          if (startPosition == 0) {
            try {
              final progress = await _apiService!.getProgress(audiobookId);
              if (progress != null && progress.position > 0) {
                startPosition = progress.position;
                debugPrint(
                  'SapphoAudioHandler: Fetched progress separately: $startPosition seconds',
                );
              }
            } catch (e) {
              debugPrint('SapphoAudioHandler: Could not fetch progress: $e');
            }
          }

          debugPrint(
            'SapphoAudioHandler: Starting playback at position: $startPosition seconds',
          );

          // Build stream URL
          final streamUrl = '$serverUrl/api/audiobooks/$audiobookId/stream';
          final artUrl = book.coverImage != null
              ? '$serverUrl/api/audiobooks/$audiobookId/cover?token=$token'
              : null;

          await loadAndPlayAudio(
            url: streamUrl,
            headers: {'Authorization': 'Bearer $token'},
            audiobookId: audiobookId,
            title: book.title,
            author: book.author,
            artUrl: artUrl,
            startPositionSeconds: startPosition,
          );
        } catch (e) {
          debugPrint('SapphoAudioHandler: Error playing from media id: $e');
        }
      }
    }
  }
}
