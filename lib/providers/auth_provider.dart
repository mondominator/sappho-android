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
  String? _token;
  bool _isLoading = false;
  bool _isAuthenticated = false;
  bool _isCheckingAuth = true; // True while initial auth check is in progress
  String? _error;

  AuthProvider(this._authRepository) {
    _apiService = ApiService(_authRepository);
    _checkAuthStatus();
  }

  User? get user => _user;
  String? get serverUrl => _serverUrl;
  String? get token => _token;
  bool get isLoading => _isLoading;
  bool get isAuthenticated => _isAuthenticated;
  bool get isCheckingAuth => _isCheckingAuth;
  String? get error => _error;
  ApiService get apiService => _apiService;

  Future<void> _checkAuthStatus() async {
    _isAuthenticated = await _authRepository.isAuthenticated();
    _serverUrl = await _authRepository.getServerUrl();
    _token = await _authRepository.getToken();

    debugPrint(
      'AuthProvider: checkAuthStatus isAuthenticated=$_isAuthenticated, hasToken=${_token != null}',
    );

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
      }

      // Then try to fetch fresh user data (don't block on this)
      _apiService
          .getProfile()
          .then((user) async {
            _user = user;
            await _authRepository.saveUserInfo(
              username: _user!.username,
              displayName: _user!.displayName,
              avatar: _user!.avatar,
            );
            notifyListeners();
          })
          .catchError((e) {
            debugPrint('Failed to fetch user profile: $e');
          });
    }

    _isCheckingAuth = false;
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

      _token = response.token;
      _user = response.user;
      _isAuthenticated = true;
      _isLoading = false;
      notifyListeners();

      // Immediately fetch full profile to get avatar (login response may not include it)
      _apiService
          .getProfile()
          .then((user) async {
            _user = user;
            await _authRepository.saveUserInfo(
              username: user.username,
              displayName: user.displayName,
              avatar: user.avatar,
            );
            notifyListeners();
          })
          .catchError((e) {
            debugPrint('Failed to fetch user profile after login: $e');
          });

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
    _token = null;
    _isAuthenticated = false;
    notifyListeners();
  }

  /// Refresh user data from server
  Future<void> refreshUser() async {
    try {
      _user = await _apiService.getProfile();
      await _authRepository.saveUserInfo(
        username: _user!.username,
        displayName: _user!.displayName,
        avatar: _user!.avatar,
      );
      notifyListeners();
    } catch (e) {
      debugPrint('Failed to refresh user profile: $e');
    }
  }

  /// Update user directly (for immediate UI update after edit)
  void updateUser(User user) {
    _user = user;
    notifyListeners();
  }
}
