import 'dart:io';
import 'package:flutter/foundation.dart';
import '../models/user.dart';
import '../services/api_service.dart';
import '../services/auth_repository.dart';

/// Profile provider matching Android's ProfileViewModel
class ProfileProvider extends ChangeNotifier {
  final ApiService _api;
  final AuthRepository _authRepository;

  User? _user;
  UserStats? _stats;
  bool _isLoading = true; // Start as true since _init() loads data
  bool _isLoadingStats = false;
  bool _isUploadingAvatar = false;
  String? _serverUrl;
  String? _authToken;
  String? _error;
  String? _statsError;

  ProfileProvider(this._api, this._authRepository) {
    _init();
  }

  User? get user => _user;
  UserStats? get stats => _stats;
  bool get isLoading => _isLoading;
  bool get isLoadingStats => _isLoadingStats;
  bool get isUploadingAvatar => _isUploadingAvatar;
  String? get serverUrl => _serverUrl;
  String? get authToken => _authToken;
  String? get error => _error;
  String? get statsError => _statsError;

  Future<void> _init() async {
    try {
      _serverUrl = await _authRepository.getServerUrl();
      _authToken = await _authRepository.getToken();
      await loadProfile();
      await loadStats();
    } catch (e) {
      debugPrint('ProfileProvider._init error: $e');
      _isLoading = false;
      _error = e.toString();
      notifyListeners();
    }
  }

  Future<void> loadProfile() async {
    _isLoading = true;
    _error = null;
    notifyListeners();

    try {
      _user = await _api.getProfile();
      debugPrint('ProfileProvider: Loaded profile for ${_user?.username}');
    } catch (e) {
      _error = e.toString();
      debugPrint('ProfileProvider: Error loading profile: $e');
    } finally {
      _isLoading = false;
      notifyListeners();
    }
  }

  Future<void> loadStats() async {
    debugPrint('ProfileProvider: loadStats starting...');
    _isLoadingStats = true;
    _statsError = null;
    notifyListeners();

    try {
      _stats = await _api.getProfileStats();
      debugPrint(
        'ProfileProvider: Loaded stats - totalListenTime=${_stats?.totalListenTime}, booksCompleted=${_stats?.booksCompleted}',
      );
    } catch (e) {
      debugPrint('ProfileProvider: Error loading stats: $e');
      _statsError = e.toString();
    } finally {
      _isLoadingStats = false;
      notifyListeners();
    }
  }

  Future<void> refresh() async {
    await loadProfile();
    await loadStats();
  }

  Future<bool> uploadAvatar(File imageFile) async {
    _isUploadingAvatar = true;
    notifyListeners();

    try {
      _user = await _api.updateAvatar(imageFile);
      debugPrint('ProfileProvider: Avatar uploaded successfully');
      return true;
    } catch (e) {
      debugPrint('ProfileProvider: Error uploading avatar: $e');
      return false;
    } finally {
      _isUploadingAvatar = false;
      notifyListeners();
    }
  }

  Future<bool> deleteAvatar() async {
    _isUploadingAvatar = true;
    notifyListeners();

    try {
      await _api.deleteAvatar();
      // Reload profile to get updated user data
      await loadProfile();
      debugPrint('ProfileProvider: Avatar deleted successfully');
      return true;
    } catch (e) {
      debugPrint('ProfileProvider: Error deleting avatar: $e');
      return false;
    } finally {
      _isUploadingAvatar = false;
      notifyListeners();
    }
  }
}
