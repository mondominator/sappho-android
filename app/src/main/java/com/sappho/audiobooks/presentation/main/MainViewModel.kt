package com.sappho.audiobooks.presentation.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sappho.audiobooks.data.remote.SapphoApi
import com.sappho.audiobooks.domain.model.User
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val api: SapphoApi,
    private val authRepository: com.sappho.audiobooks.data.repository.AuthRepository,
    val playerState: com.sappho.audiobooks.service.PlayerState,
    val castHelper: com.sappho.audiobooks.cast.CastHelper,
    val downloadManager: com.sappho.audiobooks.download.DownloadManager
) : ViewModel() {

    private val _user = MutableStateFlow<User?>(null)
    val user: StateFlow<User?> = _user

    private val _serverUrl = MutableStateFlow<String?>(null)
    val serverUrl: StateFlow<String?> = _serverUrl

    private val _serverVersion = MutableStateFlow<String?>(null)
    val serverVersion: StateFlow<String?> = _serverVersion

    init {
        _serverUrl.value = authRepository.getServerUrlSync()
        loadCachedUser() // Load cached user first for immediate display
        loadUser()
        loadServerVersion()
    }

    private fun loadCachedUser() {
        val cachedUsername = authRepository.getCachedUsername()
        if (cachedUsername != null) {
            // Create a minimal User object from cached data
            _user.value = User(
                id = 0,
                username = cachedUsername,
                email = null,
                displayName = authRepository.getCachedDisplayName(),
                isAdmin = 0,
                avatar = null
            )
        }
    }

    private fun loadUser() {
        viewModelScope.launch {
            try {
                android.util.Log.d("MainViewModel", "Loading user profile...")
                val response = api.getProfile()
                android.util.Log.d("MainViewModel", "Response: ${response.code()}, body: ${response.body()}")
                if (response.isSuccessful) {
                    val loadedUser = response.body()
                    _user.value = loadedUser
                    // Cache user info for offline use
                    loadedUser?.let {
                        authRepository.saveUserInfo(it.username, it.displayName)
                    }
                    android.util.Log.d("MainViewModel", "User set: ${_user.value}")
                } else {
                    android.util.Log.e("MainViewModel", "Failed to load user: ${response.code()} - ${response.message()}")
                }
            } catch (e: Exception) {
                // Offline - keep using cached user
                android.util.Log.e("MainViewModel", "Exception loading user (offline?)", e)
            }
        }
    }

    private fun loadServerVersion() {
        viewModelScope.launch {
            try {
                val response = api.getHealth()
                if (response.isSuccessful) {
                    _serverVersion.value = response.body()?.version
                }
            } catch (e: Exception) {
                // Ignore errors - version is optional
            }
        }
    }
}
