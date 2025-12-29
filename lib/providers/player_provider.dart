import 'dart:async';
import 'package:flutter/foundation.dart';
import 'package:just_audio/just_audio.dart';
import '../models/audiobook.dart';
import '../services/api_service.dart';
import '../services/auth_repository.dart';
import '../services/cast_service.dart';
import '../services/download_service.dart';
import '../main.dart' show audioHandler;

/// Player provider matching Android's PlayerState + PlayerViewModel
/// Manages audio playback state and controls using audio_service
class PlayerProvider extends ChangeNotifier {
  final ApiService _api;
  final AuthRepository _authRepository;
  final DownloadService _downloadService = DownloadService();
  final CastService _castService = CastService();

  Audiobook? _currentAudiobook;
  List<Chapter> _chapters = [];
  bool _isPlaying = false;
  int _currentPosition = 0; // in seconds
  int _duration = 0; // in seconds
  bool _isLoading = false;
  double _playbackSpeed = 1.0;
  int? _sleepTimerRemaining; // in seconds
  String? _serverUrl;
  String? _authToken;
  String? _error;

  Timer? _sleepTimer;
  Timer? _progressSyncTimer;

  PlayerProvider(this._api, this._authRepository) {
    _init();
    // Listen to cast service for state changes
    _castService.addListener(_onCastStateChanged);
  }

  void _onCastStateChanged() {
    debugPrint(
      'PlayerProvider._onCastStateChanged: isCasting=${_castService.isCasting}, castIsPlaying=${_castService.isPlaying}',
    );
    notifyListeners();
  }

  Audiobook? get currentAudiobook => _currentAudiobook;
  List<Chapter> get chapters => _chapters;
  bool get isPlaying =>
      _castService.isCasting ? _castService.isPlaying : _isPlaying;
  int get currentPosition => _castService.isCasting
      ? (_castService.currentPosition ~/ 1000)
      : _currentPosition; // Cast uses ms
  int get duration => _castService.isCasting && _castService.duration > 0
      ? (_castService.duration ~/ 1000)
      : _duration;
  bool get isLoading => _isLoading;
  double get playbackSpeed => _playbackSpeed;
  int? get sleepTimerRemaining => _sleepTimerRemaining;
  String? get serverUrl => _serverUrl;
  String? get authToken => _authToken;
  String? get error => _error;

  bool get hasAudiobook => _currentAudiobook != null;
  bool get isCasting => _castService.isCasting;

  // Get current chapter based on position
  Chapter? get currentChapter {
    if (_chapters.isEmpty) return null;
    for (int i = _chapters.length - 1; i >= 0; i--) {
      if (_currentPosition >= _chapters[i].startTime) {
        return _chapters[i];
      }
    }
    return _chapters.isNotEmpty ? _chapters.first : null;
  }

  StreamSubscription<bool>? _playingSubscription;
  StreamSubscription<Duration>? _positionSubscription;
  StreamSubscription<Duration?>? _durationSubscription;
  StreamSubscription<ProcessingState>? _processingSubscription;

  Future<void> _init() async {
    _serverUrl = await _authRepository.getServerUrl();
    _authToken = await _authRepository.getToken();

    // Listen to audio handler's player streams
    final player = audioHandler.player;

    _playingSubscription = player.playingStream.listen((playing) {
      _isPlaying = playing;
      notifyListeners();
    });

    _positionSubscription = player.positionStream.listen((position) {
      _currentPosition = position.inSeconds;
      notifyListeners();
    });

    _durationSubscription = player.durationStream.listen((duration) {
      if (duration != null) {
        _duration = duration.inSeconds;
        notifyListeners();
      }
    });

    _processingSubscription = player.processingStateStream.listen((state) {
      if (state == ProcessingState.completed) {
        _isPlaying = false;
        _markAsFinished();
        notifyListeners();
      }
    });
  }

  /// Load and start playback for an audiobook
  Future<void> loadAndPlay(int audiobookId, {int startPosition = 0}) async {
    _isLoading = true;
    _error = null;
    notifyListeners();

    try {
      debugPrint('PlayerProvider: Loading audiobook $audiobookId');
      _currentAudiobook = await _api.getAudiobook(audiobookId);
      _duration = _currentAudiobook?.duration ?? 0;
      debugPrint(
        'PlayerProvider: Audiobook loaded - ${_currentAudiobook?.title}',
      );
      _isLoading = false; // Content is ready to show
      notifyListeners(); // Notify early so UI can show content

      // Use saved progress if no start position provided
      int playPosition = startPosition;
      if (startPosition == 0 && _currentAudiobook?.progress != null) {
        playPosition = _currentAudiobook!.progress!.position;
      }
      _currentPosition = playPosition;

      // Load chapters
      try {
        _chapters = await _api.getChapters(audiobookId);
        debugPrint('PlayerProvider: Loaded ${_chapters.length} chapters');
      } catch (e) {
        debugPrint('PlayerProvider: Failed to load chapters: $e');
      }

      // Check if audiobook is downloaded locally
      final localPath = await _downloadService.getLocalAudioPath(audiobookId);

      String audioSource;
      bool isLocalFile = false;

      if (localPath != null) {
        // Play from local file
        audioSource = localPath;
        isLocalFile = true;
        debugPrint('PlayerProvider: Playing from local file: $localPath');
      } else {
        // Stream from server
        audioSource = await _api.getStreamUrl(audiobookId);
        debugPrint('PlayerProvider: Streaming from URL: $audioSource');
      }

      // Build cover URL for notification
      String? artUrl;
      if (_serverUrl != null && _currentAudiobook?.coverImage != null) {
        artUrl = '$_serverUrl/api/audiobooks/$audiobookId/cover';
      }

      await audioHandler.loadAndPlayAudio(
        url: audioSource,
        headers: {}, // Token is in URL query param for streaming
        audiobookId: audiobookId,
        title: _currentAudiobook?.title ?? 'Unknown',
        author: _currentAudiobook?.author,
        artUrl: artUrl,
        startPositionSeconds: playPosition,
        isLocalFile: isLocalFile,
      );

      // Start progress sync timer
      _startProgressSync();

      debugPrint('PlayerProvider: Started playback at position $playPosition');
    } catch (e) {
      _error = e.toString();
      debugPrint('PlayerProvider: Error loading audiobook: $e');
    } finally {
      _isLoading = false;
      notifyListeners();
    }
  }

  /// Load audiobook details without starting playback
  Future<void> loadAudiobookDetails(int audiobookId) async {
    try {
      _currentAudiobook = await _api.getAudiobook(audiobookId);
      _duration = _currentAudiobook?.duration ?? 0;

      try {
        _chapters = await _api.getChapters(audiobookId);
      } catch (e) {
        debugPrint('PlayerProvider: Failed to load chapters: $e');
      }

      notifyListeners();
    } catch (e) {
      debugPrint('PlayerProvider: Error loading audiobook details: $e');
    }
  }

  Future<void> togglePlayPause() async {
    debugPrint(
      'PlayerProvider.togglePlayPause: isCasting=${_castService.isCasting}',
    );
    if (_castService.isCasting) {
      debugPrint('PlayerProvider.togglePlayPause: Delegating to CastService');
      _castService.togglePlayPause();
      return;
    }
    if (_isPlaying) {
      await pause();
    } else {
      await play();
    }
  }

  Future<void> play() async {
    if (_castService.isCasting) {
      _castService.togglePlayPause();
      return;
    }
    await audioHandler.play();
  }

  Future<void> pause() async {
    if (_castService.isCasting) {
      _castService.togglePlayPause();
      return;
    }
    await audioHandler.pause();
    await _syncProgress();
  }

  Future<void> seekTo(int position) async {
    if (_castService.isCasting) {
      _castService.seekTo(position * 1000); // Convert to ms
      _currentPosition = position;
      notifyListeners();
      return;
    }
    _currentPosition = position.clamp(0, _duration);
    await audioHandler.seek(Duration(seconds: _currentPosition));
    notifyListeners();
  }

  Future<void> seekToAndPlay(int position) async {
    if (_castService.isCasting) {
      _castService.seekTo(position * 1000); // Convert to ms
      _currentPosition = position;
      notifyListeners();
      return;
    }
    _currentPosition = position.clamp(0, _duration);
    await audioHandler.seek(Duration(seconds: _currentPosition));
    if (!_isPlaying) {
      await audioHandler.play();
    }
    notifyListeners();
  }

  Future<void> skipForward() async {
    if (_castService.isCasting) {
      _castService.skipForward(10);
      return;
    }
    await audioHandler.fastForward();
  }

  Future<void> skipBackward() async {
    if (_castService.isCasting) {
      _castService.skipBackward(10);
      return;
    }
    await audioHandler.rewind();
  }

  Future<void> jumpToChapter(Chapter chapter) async {
    await seekToAndPlay(chapter.startTime.toInt());
  }

  Future<void> previousChapter() async {
    if (_chapters.isEmpty) return;
    final currentIdx = _chapters.indexWhere((c) => c == currentChapter);
    if (currentIdx > 0) {
      await jumpToChapter(_chapters[currentIdx - 1]);
    }
  }

  Future<void> nextChapter() async {
    if (_chapters.isEmpty) return;
    final currentIdx = _chapters.indexWhere((c) => c == currentChapter);
    if (currentIdx >= 0 && currentIdx < _chapters.length - 1) {
      await jumpToChapter(_chapters[currentIdx + 1]);
    }
  }

  Future<void> setPlaybackSpeed(double speed) async {
    _playbackSpeed = speed;
    await audioHandler.setSpeed(speed);
    notifyListeners();
  }

  void setSleepTimer(int minutes) {
    _sleepTimer?.cancel();
    if (minutes == 0) {
      _sleepTimerRemaining = null;
    } else {
      _sleepTimerRemaining = minutes * 60;
      _sleepTimer = Timer.periodic(const Duration(seconds: 1), (timer) {
        if (_sleepTimerRemaining != null && _sleepTimerRemaining! > 0) {
          _sleepTimerRemaining = _sleepTimerRemaining! - 1;
          if (_sleepTimerRemaining == 0) {
            pause();
            timer.cancel();
          }
          notifyListeners();
        }
      });
    }
    notifyListeners();
  }

  void cancelSleepTimer() {
    _sleepTimer?.cancel();
    _sleepTimerRemaining = null;
    notifyListeners();
  }

  void _startProgressSync() {
    _progressSyncTimer?.cancel();
    // Sync progress every 30 seconds
    _progressSyncTimer = Timer.periodic(const Duration(seconds: 30), (timer) {
      if (_isPlaying) {
        _syncProgress();
      }
    });
  }

  Future<void> _syncProgress() async {
    if (_currentAudiobook == null) return;
    try {
      await _api.updateProgress(
        _currentAudiobook!.id,
        _currentPosition,
        state: _isPlaying ? 'playing' : 'paused',
      );
      debugPrint('PlayerProvider: Synced progress at $_currentPosition');
    } catch (e) {
      debugPrint('PlayerProvider: Failed to sync progress: $e');
    }
  }

  Future<void> _markAsFinished() async {
    if (_currentAudiobook == null) return;
    try {
      await _api.markFinished(_currentAudiobook!.id);
      debugPrint('PlayerProvider: Marked as finished');
    } catch (e) {
      debugPrint('PlayerProvider: Failed to mark as finished: $e');
    }
  }

  Future<void> stop() async {
    await audioHandler.stop();
    await _syncProgress();
  }

  void clear() {
    audioHandler.stop();
    _sleepTimer?.cancel();
    _progressSyncTimer?.cancel();
    _currentAudiobook = null;
    _chapters = [];
    _isPlaying = false;
    _currentPosition = 0;
    _duration = 0;
    _isLoading = false;
    _playbackSpeed = 1.0;
    _sleepTimerRemaining = null;
    _error = null;
    notifyListeners();
  }

  @override
  void dispose() {
    _progressSyncTimer?.cancel();
    _sleepTimer?.cancel();
    _playingSubscription?.cancel();
    _positionSubscription?.cancel();
    _durationSubscription?.cancel();
    _processingSubscription?.cancel();
    _castService.removeListener(_onCastStateChanged);
    super.dispose();
  }
}
