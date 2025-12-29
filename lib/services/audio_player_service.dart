import 'dart:async';
import 'dart:io';
import 'package:flutter/foundation.dart';
import 'package:just_audio/just_audio.dart';
import 'package:audio_session/audio_session.dart';

/// Audio player service using just_audio package with background support
/// Handles actual audio playback for audiobooks with media notifications
class AudioPlayerService {
  static final AudioPlayerService _instance = AudioPlayerService._internal();
  factory AudioPlayerService() => _instance;
  AudioPlayerService._internal();

  final AudioPlayer _player = AudioPlayer();

  // Stream subscriptions
  StreamSubscription<PlayerState>? _playerStateSubscription;
  StreamSubscription<Duration>? _positionSubscription;
  StreamSubscription<Duration?>? _durationSubscription;

  // Callbacks
  Function(bool isPlaying)? onPlayingChanged;
  Function(int positionSeconds)? onPositionChanged;
  Function(int durationSeconds)? onDurationChanged;
  Function()? onComplete;
  Function(String error)? onError;

  bool _isInitialized = false;

  AudioPlayer get player => _player;
  bool get isPlaying => _player.playing;
  Duration get position => _player.position;
  Duration? get duration => _player.duration;

  Future<void> init() async {
    if (_isInitialized) return;

    // Configure audio session for audiobook playback
    final session = await AudioSession.instance;
    await session.configure(
      const AudioSessionConfiguration(
        avAudioSessionCategory: AVAudioSessionCategory.playback,
        avAudioSessionCategoryOptions: AVAudioSessionCategoryOptions.duckOthers,
        avAudioSessionMode: AVAudioSessionMode.spokenAudio,
        avAudioSessionRouteSharingPolicy:
            AVAudioSessionRouteSharingPolicy.defaultPolicy,
        avAudioSessionSetActiveOptions: AVAudioSessionSetActiveOptions.none,
        androidAudioAttributes: AndroidAudioAttributes(
          contentType: AndroidAudioContentType.speech,
          usage: AndroidAudioUsage.media,
        ),
        androidAudioFocusGainType: AndroidAudioFocusGainType.gain,
        androidWillPauseWhenDucked: true,
      ),
    );
    debugPrint('AudioPlayerService: Audio session configured');

    _playerStateSubscription = _player.playerStateStream.listen((state) {
      debugPrint(
        'AudioPlayerService: Player state changed - playing: ${state.playing}, processing: ${state.processingState}',
      );
      onPlayingChanged?.call(state.playing);

      if (state.processingState == ProcessingState.completed) {
        onComplete?.call();
      }
    });

    _positionSubscription = _player.positionStream.listen((position) {
      onPositionChanged?.call(position.inSeconds);
    });

    _durationSubscription = _player.durationStream.listen((duration) {
      if (duration != null) {
        onDurationChanged?.call(duration.inSeconds);
      }
    });

    _isInitialized = true;
    debugPrint('AudioPlayerService: Initialized');
  }

  Future<void> loadAndPlay({
    required String url,
    required Map<String, String> headers,
    int startPositionSeconds = 0,
    bool isLocalFile = false,
  }) async {
    try {
      debugPrint(
        'AudioPlayerService: Loading $url (isLocalFile: $isLocalFile)',
      );
      debugPrint(
        'AudioPlayerService: Start position: $startPositionSeconds seconds',
      );

      // Stop any current playback
      await _player.stop();

      // Set audio source based on whether it's a local file or URL
      AudioSource audioSource;
      if (isLocalFile) {
        // Local file - use file URI
        final file = File(url);
        if (!await file.exists()) {
          throw Exception('Local audio file not found: $url');
        }
        audioSource = AudioSource.file(url);
        debugPrint('AudioPlayerService: Using local file source');
      } else {
        // Remote URL with authentication headers
        audioSource = AudioSource.uri(Uri.parse(url), headers: headers);
        debugPrint('AudioPlayerService: Using remote URL source');
      }

      final duration = await _player.setAudioSource(
        audioSource,
        initialPosition: Duration(seconds: startPositionSeconds),
      );
      debugPrint('AudioPlayerService: Audio source set, duration: $duration');

      // Start playback
      debugPrint('AudioPlayerService: Starting playback...');
      await _player.play();
      debugPrint(
        'AudioPlayerService: Play() called, isPlaying: ${_player.playing}',
      );
    } catch (e, stackTrace) {
      debugPrint('AudioPlayerService: Error loading audio: $e');
      debugPrint('AudioPlayerService: Stack trace: $stackTrace');
      onError?.call(e.toString());
    }
  }

  Future<void> play() async {
    await _player.play();
  }

  Future<void> pause() async {
    await _player.pause();
  }

  Future<void> togglePlayPause() async {
    if (_player.playing) {
      await pause();
    } else {
      await play();
    }
  }

  Future<void> seekTo(int positionSeconds) async {
    await _player.seek(Duration(seconds: positionSeconds));
  }

  Future<void> skipForward({int seconds = 10}) async {
    final newPosition = _player.position + Duration(seconds: seconds);
    final maxPosition = _player.duration ?? Duration.zero;
    if (newPosition < maxPosition) {
      await _player.seek(newPosition);
    } else {
      await _player.seek(maxPosition);
    }
  }

  Future<void> skipBackward({int seconds = 10}) async {
    final newPosition = _player.position - Duration(seconds: seconds);
    if (newPosition > Duration.zero) {
      await _player.seek(newPosition);
    } else {
      await _player.seek(Duration.zero);
    }
  }

  Future<void> setSpeed(double speed) async {
    await _player.setSpeed(speed);
  }

  Future<void> stop() async {
    await _player.stop();
  }

  void dispose() {
    _playerStateSubscription?.cancel();
    _positionSubscription?.cancel();
    _durationSubscription?.cancel();
    _player.dispose();
    _isInitialized = false;
  }
}
