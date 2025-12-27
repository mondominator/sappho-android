import 'package:flutter/foundation.dart';
import '../models/user.dart';
import '../services/api_service.dart';
import '../services/auth_repository.dart';

/// Authentication state provider
/// Mirrors Android's AuthRepository + MainViewModel auth handling
class AuthProvider extends ChangeNotifier {
  final AuthRepository _authRepository;
  late final ApiService _apiService;

  User? _user;
  String? _serverUrl;
  bool _isLoading = false;
  bool _isAuthenticated = false;
  String? _error;

  AuthProvider(this._authRepository) {
    _apiService = ApiService(_authRepository);
    _checkAuthStatus();
  }

  User? get user => _user;
  String? get serverUrl => _serverUrl;
  bool get isLoading => _isLoading;
  bool get isAuthenticated => _isAuthenticated;
  String? get error => _error;
  ApiService get apiService => _apiService;

  Future<void> _checkAuthStatus() async {
    _isAuthenticated = await _authRepository.isAuthenticated();
    _serverUrl = await _authRepository.getServerUrl();

    if (_isAuthenticated) {
      // Load cached user data first
      final cachedUsername = await _authRepository.getCachedUsername();
      if (cachedUsername != null) {
        _user = User(
          id: 0,
          username: cachedUsername,
          displayName: await _authRepository.getCachedDisplayName(),
          isAdmin: 0,
          avatar: await _authRepository.getCachedAvatar(),
        );
        notifyListeners();
      }

      // Then try to fetch fresh user data
      try {
        _user = await _apiService.getProfile();
        await _authRepository.saveUserInfo(
          username: _user!.username,
          displayName: _user!.displayName,
          avatar: _user!.avatar,
        );
      } catch (e) {
        // Offline - keep using cached user
        debugPrint('Failed to fetch user profile: $e');
      }
    }

    notifyListeners();
  }

  Future<bool> login(String serverUrl, String username, String password) async {
    _isLoading = true;
    _error = null;
    notifyListeners();

    try {
      // Normalize server URL (remove trailing slash)
      final normalizedUrl = serverUrl.endsWith('/')
          ? serverUrl.substring(0, serverUrl.length - 1)
          : serverUrl;

      // Save server URL first so API service can use it
      await _authRepository.saveServerUrl(normalizedUrl);
      _serverUrl = normalizedUrl;

      // Attempt login
      final response = await _apiService.login(username, password);

      // Save token and user info
      await _authRepository.saveToken(response.token);
      await _authRepository.saveUserInfo(
        username: response.user.username,
        displayName: response.user.displayName,
        avatar: response.user.avatar,
      );

      _user = response.user;
      _isAuthenticated = true;
      _isLoading = false;
      notifyListeners();
      return true;
    } catch (e) {
      _error = 'Login failed: ${e.toString()}';
      _isLoading = false;
      notifyListeners();
      return false;
    }
  }

  Future<void> logout() async {
    await _authRepository.clearAll();
    _user = null;
    _serverUrl = null;
    _isAuthenticated = false;
    notifyListeners();
  }
}
