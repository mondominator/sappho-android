import 'package:dio/dio.dart';
import '../models/audiobook.dart';
import '../models/user.dart';
import 'auth_repository.dart';

/// API service matching Android's SapphoApi interface
/// Uses Dio with interceptor for dynamic base URL and auth token
class ApiService {
  late final Dio _dio;
  final AuthRepository _authRepository;

  ApiService(this._authRepository) {
    _dio = Dio();

    // Add interceptor to inject base URL and auth token on every request
    _dio.interceptors.add(InterceptorsWrapper(
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
        if (error.response?.statusCode == 401 || error.response?.statusCode == 403) {
          // Could emit an auth error event here
        }
        return handler.next(error);
      },
    ));
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

  // Audiobook metadata endpoints
  Future<List<Audiobook>> getInProgress({int limit = 10}) async {
    final response = await _dio.get('/api/audiobooks/meta/in-progress', queryParameters: {'limit': limit});
    return (response.data as List).map((json) => Audiobook.fromJson(json)).toList();
  }

  Future<List<Audiobook>> getRecent({int limit = 10}) async {
    final response = await _dio.get('/api/audiobooks/meta/recent', queryParameters: {'limit': limit});
    return (response.data as List).map((json) => Audiobook.fromJson(json)).toList();
  }

  Future<List<Audiobook>> getFinished({int limit = 10}) async {
    final response = await _dio.get('/api/audiobooks/meta/finished', queryParameters: {'limit': limit});
    return (response.data as List).map((json) => Audiobook.fromJson(json)).toList();
  }

  Future<List<Audiobook>> getAllAudiobooks() async {
    final response = await _dio.get('/api/audiobooks');
    return (response.data as List).map((json) => Audiobook.fromJson(json)).toList();
  }

  Future<Audiobook> getAudiobook(int id) async {
    final response = await _dio.get('/api/audiobooks/$id');
    return Audiobook.fromJson(response.data);
  }

  // Progress sync
  Future<void> updateProgress(int audiobookId, int position, {String? status}) async {
    await _dio.patch(
      '/api/audiobooks/$audiobookId/progress',
      data: {
        'current_position': position,
        if (status != null) 'status': status,
      },
    );
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
