import 'dart:async';
import 'package:bonsoir/bonsoir.dart';
import 'package:cast_plus/cast.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/scheduler.dart';

/// Service for discovering and connecting to Chromecast devices
class CastService extends ChangeNotifier {
  static final CastService _instance = CastService._internal();
  factory CastService() => _instance;
  CastService._internal();

  /// Safely notify listeners, scheduling for next frame if in build phase
  void _safeNotify() {
    SchedulerBinding.instance.addPostFrameCallback((_) {
      notifyListeners();
    });
  }

  final List<CastDevice> _devices = [];
  List<CastDevice> get devices => List.unmodifiable(_devices);

  CastDevice? _connectedDevice;
  CastDevice? get connectedDevice => _connectedDevice;

  CastSession? _session;
  CastSession? get session => _session;

  bool _isSearching = false;
  bool get isSearching => _isSearching;

  bool _isConnecting = false;
  bool get isConnecting => _isConnecting;

  bool _isCasting = false;
  bool get isCasting => _isCasting;

  String? _error;
  String? get error => _error;

  // Current media state
  int _currentPosition = 0;
  int _duration = 0;
  bool _isPlaying = false;
  int _mediaSessionId = 1;

  int get currentPosition => _currentPosition;
  int get duration => _duration;
  bool get isPlaying => _isPlaying;

  StreamSubscription<CastSessionState>? _sessionStateSubscription;
  StreamSubscription<Map<String, dynamic>>? _messageSubscription;

  BonsoirDiscovery? _discovery;
  StreamSubscription? _discoverySubscription;

  /// Start searching for cast devices on the network
  Future<void> startDiscovery() async {
    if (_isSearching) return;

    _isSearching = true;
    _error = null;
    _devices.clear();
    _safeNotify(); // Use safe notify since this may be called from initState

    try {
      debugPrint('CastService: Starting discovery...');
      _discovery = BonsoirDiscovery(type: '_googlecast._tcp');
      await _discovery!.initialize();

      _discoverySubscription = _discovery!.eventStream!.listen((event) {
        debugPrint('CastService: Discovery event: ${event.runtimeType}');
        _handleDiscoveryEvent(event);
      });

      await _discovery!.start();
      debugPrint('CastService: Discovery started');

      // Stop after 10 seconds
      Future.delayed(const Duration(seconds: 10), () {
        if (_isSearching) {
          stopDiscovery();
        }
      });
    } catch (e, stack) {
      debugPrint('CastService: Discovery error: $e');
      debugPrint('CastService: Stack: $stack');
      _error = 'Failed to discover devices: $e';
      _isSearching = false;
      _safeNotify();
    }
  }

  void _handleDiscoveryEvent(BonsoirDiscoveryEvent event) {
    switch (event) {
      case BonsoirDiscoveryServiceFoundEvent():
        debugPrint('CastService: Service found: ${event.service.name}');
        event.service.resolve(_discovery!.serviceResolver);
        break;
      case BonsoirDiscoveryServiceResolvedEvent():
        debugPrint('CastService: Service resolved: ${event.service.name}');
        _addDeviceFromService(event.service);
        break;
      case BonsoirDiscoveryServiceUpdatedEvent():
        // Also handle updated events - these contain resolved services
        debugPrint('CastService: Service updated: ${event.service.name}');
        if (event.service.port > 0) {
          _addDeviceFromService(event.service);
        } else {
          // Try to resolve if not resolved yet
          event.service.resolve(_discovery!.serviceResolver);
        }
        break;
      case BonsoirDiscoveryServiceLostEvent():
        debugPrint('CastService: Service lost: ${event.service.name}');
        _devices.removeWhere((d) => d.serviceName == event.service.name);
        notifyListeners();
        break;
      default:
        debugPrint('CastService: Other event: ${event.runtimeType}');
        break;
    }
  }

  void _addDeviceFromService(BonsoirService service) {
    final port = service.port;
    final json = service.toJson();
    final host = json['service.ip'] ?? json['service.host'] ?? '';

    if (host.isEmpty || port == 0) {
      debugPrint('CastService: Invalid service, host=$host, port=$port');
      return;
    }

    String name = [
      service.attributes['md'],
      service.attributes['fn'],
    ].whereType<String>().join(' - ');
    if (name.isEmpty) {
      name = service.name;
    }

    // Check if already exists
    if (_devices.any((d) => d.serviceName == service.name)) {
      debugPrint('CastService: Device already exists: $name');
      return;
    }

    debugPrint('CastService: Adding device: $name at $host:$port');
    _devices.add(
      CastDevice(
        serviceName: service.name,
        name: name,
        port: port,
        host: host,
        extras: service.attributes,
      ),
    );
    debugPrint('CastService: Total devices: ${_devices.length}');
    notifyListeners(); // Use direct notify for device additions
  }

  /// Stop searching for cast devices
  void stopDiscovery() {
    debugPrint('CastService: Stopping discovery');
    _discoverySubscription?.cancel();
    _discoverySubscription = null;
    _discovery?.stop();
    _discovery = null;
    _isSearching = false;
    notifyListeners();
  }

  /// Connect to a cast device
  Future<bool> connectToDevice(CastDevice device) async {
    if (_isConnecting) {
      debugPrint('CastService: Already connecting, ignoring');
      return false;
    }

    debugPrint(
      'CastService: Connecting to ${device.name} at ${device.host}:${device.port}',
    );
    _isConnecting = true;
    _error = null;
    notifyListeners();

    try {
      debugPrint('CastService: Starting session...');
      _session = await CastSessionManager().startSession(device);
      debugPrint('CastService: Session started, setting up listeners...');

      _sessionStateSubscription?.cancel();
      _sessionStateSubscription = _session!.stateStream.listen((state) {
        debugPrint('CastService: Session state changed to $state');
        if (state == CastSessionState.connected) {
          _connectedDevice = device;
          _isConnecting = false;
          notifyListeners();
        } else if (state == CastSessionState.closed) {
          _handleDisconnect();
        }
      });

      _messageSubscription?.cancel();
      _messageSubscription = _session!.messageStream.listen((message) {
        debugPrint('CastService: Received message: ${message['type']}');
        _handleCastMessage(message);
      });

      // Launch the default media receiver
      debugPrint('CastService: Launching media receiver app...');
      _session!.sendMessage(CastSession.kNamespaceReceiver, {
        'type': 'LAUNCH',
        'appId': 'CC1AD845', // Default Media Receiver app ID
      });

      return true;
    } catch (e, stack) {
      debugPrint('CastService: Connection error: $e');
      debugPrint('CastService: Stack trace: $stack');
      _error = 'Failed to connect: $e';
      _isConnecting = false;
      notifyListeners();
      return false;
    }
  }

  /// Disconnect from the current cast device
  Future<void> disconnect() async {
    debugPrint('CastService.disconnect: Disconnecting from cast device');
    try {
      if (_session != null) {
        // Stop any playing media
        _session!.sendMessage(CastSession.kNamespaceMedia, {
          'type': 'STOP',
          'requestId': _requestId++,
          'mediaSessionId': _mediaSessionId,
        });
      }
    } catch (e) {
      debugPrint('CastService.disconnect: Error stopping media - $e');
    }

    _handleDisconnect();
  }

  void _handleDisconnect() {
    _sessionStateSubscription?.cancel();
    _sessionStateSubscription = null;
    _messageSubscription?.cancel();
    _messageSubscription = null;
    _session = null;
    _connectedDevice = null;
    _isCasting = false;
    _currentPosition = 0;
    _duration = 0;
    _isPlaying = false;
    notifyListeners();
  }

  int _requestId = 1;

  /// Cast audio to the connected device
  Future<bool> castAudio({
    required String url,
    required String title,
    String? author,
    String? coverUrl,
    int startPosition = 0,
  }) async {
    if (_session == null || _connectedDevice == null) {
      debugPrint('CastService.castAudio: No session or device');
      _error = 'Not connected to a cast device';
      notifyListeners();
      return false;
    }

    try {
      debugPrint('CastService.castAudio: Sending LOAD message');
      debugPrint('CastService.castAudio: URL = $url');
      debugPrint('CastService.castAudio: title = $title');
      debugPrint('CastService.castAudio: author = $author');
      debugPrint('CastService.castAudio: coverUrl = $coverUrl');
      debugPrint(
        'CastService.castAudio: startPosition = $startPosition ms (${startPosition / 1000.0} seconds)',
      );

      // Build metadata object explicitly
      final metadata = <String, dynamic>{
        'metadataType': 0, // GenericMediaMetadata
        'title': title,
      };
      if (author != null && author.isNotEmpty) {
        metadata['subtitle'] = author;
      }
      if (coverUrl != null && coverUrl.isNotEmpty) {
        metadata['images'] = [
          {'url': coverUrl},
        ];
      }

      final mediaInfo = {
        'type': 'LOAD',
        'requestId': _requestId++,
        'media': {
          'contentId': url,
          'contentType': 'audio/mp4', // Most audiobooks are AAC/M4B
          'streamType': 'BUFFERED',
          'metadata': metadata,
        },
        'currentTime': startPosition / 1000.0, // Convert ms to seconds
        'autoplay': true,
      };

      debugPrint('CastService.castAudio: mediaInfo = $mediaInfo');
      _session!.sendMessage(CastSession.kNamespaceMedia, mediaInfo);

      _currentPosition = startPosition;
      _isCasting = true;
      _isPlaying = true;
      debugPrint(
        'CastService.castAudio: Setting isCasting=true, isPlaying=true',
      );
      debugPrint('CastService.castAudio: Listener count = ${hasListeners}');
      notifyListeners(); // Use direct notify here since we're not in build phase

      debugPrint('CastService.castAudio: LOAD message sent successfully');
      return true;
    } catch (e) {
      debugPrint('CastService.castAudio: Error - $e');
      _error = 'Failed to cast: $e';
      notifyListeners();
      return false;
    }
  }

  /// Play/pause the cast media
  void togglePlayPause() {
    if (_session == null) {
      debugPrint('CastService.togglePlayPause: No session!');
      return;
    }

    debugPrint(
      'CastService.togglePlayPause: isPlaying=$_isPlaying, sending ${_isPlaying ? 'PAUSE' : 'PLAY'}',
    );
    _session!.sendMessage(CastSession.kNamespaceMedia, {
      'type': _isPlaying ? 'PAUSE' : 'PLAY',
      'requestId': _requestId++,
      'mediaSessionId': _mediaSessionId,
    });

    _isPlaying = !_isPlaying;
    debugPrint('CastService.togglePlayPause: isPlaying now $_isPlaying');
    notifyListeners();
  }

  /// Seek to a position (in milliseconds)
  void seekTo(int positionMs) {
    if (_session == null) return;

    debugPrint('CastService.seekTo: $positionMs ms');
    _session!.sendMessage(CastSession.kNamespaceMedia, {
      'type': 'SEEK',
      'requestId': _requestId++,
      'mediaSessionId': _mediaSessionId,
      'currentTime': positionMs / 1000.0,
    });

    _currentPosition = positionMs;
    notifyListeners();
  }

  /// Skip forward by seconds
  void skipForward(int seconds) {
    seekTo(_currentPosition + (seconds * 1000));
  }

  /// Skip backward by seconds
  void skipBackward(int seconds) {
    seekTo((_currentPosition - (seconds * 1000)).clamp(0, _duration));
  }

  /// Stop casting and return to local playback
  void stopCasting() {
    if (_session != null) {
      try {
        debugPrint('CastService.stopCasting: Sending STOP message');
        _session!.sendMessage(CastSession.kNamespaceMedia, {
          'type': 'STOP',
          'requestId': _requestId++,
          'mediaSessionId': _mediaSessionId,
        });
      } catch (e) {
        debugPrint('CastService.stopCasting: Error - $e');
      }
    }

    debugPrint(
      'CastService.stopCasting: Setting isCasting=false, isPlaying=false',
    );
    _isCasting = false;
    _isPlaying = false;
    notifyListeners();
  }

  void _handleCastMessage(Map<String, dynamic> message) {
    final type = message['type'];
    debugPrint('CastService._handleCastMessage: type=$type');

    if (type == 'MEDIA_STATUS') {
      final status = message['status'];
      if (status is List && status.isNotEmpty) {
        final mediaStatus = status[0] as Map<String, dynamic>;
        final playerState = mediaStatus['playerState'];

        // Capture the mediaSessionId from the response
        if (mediaStatus['mediaSessionId'] != null) {
          _mediaSessionId = mediaStatus['mediaSessionId'] as int;
          debugPrint('CastService: Got mediaSessionId=$_mediaSessionId');
        }

        debugPrint('CastService: playerState=$playerState');
        _isPlaying = playerState == 'PLAYING';

        if (mediaStatus['currentTime'] != null) {
          _currentPosition = ((mediaStatus['currentTime'] as num) * 1000)
              .toInt();
          debugPrint('CastService: currentPosition=$_currentPosition ms');
        }

        if (mediaStatus['media'] != null) {
          final media = mediaStatus['media'] as Map<String, dynamic>;
          if (media['duration'] != null) {
            _duration = ((media['duration'] as num) * 1000).toInt();
            debugPrint('CastService: duration=$_duration ms');
          }
        }

        notifyListeners();
      }
    } else if (type == 'LOAD_FAILED' || type == 'INVALID_REQUEST') {
      debugPrint('CastService: Error message received - $message');
      _error = 'Cast error: $type';
      notifyListeners();
    }
  }

  @override
  void dispose() {
    stopDiscovery();
    _sessionStateSubscription?.cancel();
    _messageSubscription?.cancel();
    super.dispose();
  }
}
