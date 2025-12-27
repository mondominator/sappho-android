import 'package:flutter_secure_storage/flutter_secure_storage.dart';

/// Secure storage for auth token and server URL
/// Mirrors Android's AuthRepository using EncryptedSharedPreferences
class AuthRepository {
  static const _keyToken = 'auth_token';
  static const _keyServerUrl = 'server_url';
  static const _keyUsername = 'cached_username';
  static const _keyDisplayName = 'cached_display_name';
  static const _keyAvatar = 'cached_avatar';

  final FlutterSecureStorage _storage = const FlutterSecureStorage(
    aOptions: AndroidOptions(encryptedSharedPreferences: true),
    iOptions: IOSOptions(accessibility: KeychainAccessibility.first_unlock),
  );

  // Token management
  Future<void> saveToken(String token) async {
    await _storage.write(key: _keyToken, value: token);
  }

  Future<String?> getToken() async {
    return await _storage.read(key: _keyToken);
  }

  Future<void> clearToken() async {
    await _storage.delete(key: _keyToken);
  }

  // Server URL management
  Future<void> saveServerUrl(String url) async {
    await _storage.write(key: _keyServerUrl, value: url);
  }

  Future<String?> getServerUrl() async {
    return await _storage.read(key: _keyServerUrl);
  }

  // User info caching for offline display
  Future<void> saveUserInfo({
    required String username,
    String? displayName,
    String? avatar,
  }) async {
    await _storage.write(key: _keyUsername, value: username);
    if (displayName != null) {
      await _storage.write(key: _keyDisplayName, value: displayName);
    }
    if (avatar != null) {
      await _storage.write(key: _keyAvatar, value: avatar);
    }
  }

  Future<String?> getCachedUsername() async {
    return await _storage.read(key: _keyUsername);
  }

  Future<String?> getCachedDisplayName() async {
    return await _storage.read(key: _keyDisplayName);
  }

  Future<String?> getCachedAvatar() async {
    return await _storage.read(key: _keyAvatar);
  }

  // Clear all auth data (logout)
  Future<void> clearAll() async {
    await _storage.deleteAll();
  }

  // Check if user is authenticated
  Future<bool> isAuthenticated() async {
    final token = await getToken();
    final serverUrl = await getServerUrl();
    return token != null && serverUrl != null;
  }
}
