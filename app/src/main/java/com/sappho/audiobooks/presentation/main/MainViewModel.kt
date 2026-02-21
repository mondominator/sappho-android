package com.sappho.audiobooks.presentation.main

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sappho.audiobooks.data.remote.SapphoApi
import com.sappho.audiobooks.domain.model.User
import com.sappho.audiobooks.service.UploadService
import com.sappho.audiobooks.service.UploadServiceState
import com.sappho.audiobooks.service.UploadStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import javax.inject.Inject

enum class UploadState {
    IDLE,
    UPLOADING,
    SUCCESS,
    ERROR
}

data class UploadResultData(
    val success: Boolean,
    val message: String?
)

@HiltViewModel
class MainViewModel @Inject constructor(
    private val api: SapphoApi,
    private val authRepository: com.sappho.audiobooks.data.repository.AuthRepository,
    private val okHttpClient: OkHttpClient,
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

    // Cached avatar file for offline display
    private val _cachedAvatarFile = MutableStateFlow<File?>(null)
    val cachedAvatarFile: StateFlow<File?> = _cachedAvatarFile

    // Upload state - derived from UploadService's shared state
    val serviceUploadState: StateFlow<UploadServiceState> = UploadService.uploadState

    // Legacy upload state accessors for UI compatibility
    private val _uploadState = MutableStateFlow(UploadState.IDLE)
    val uploadState: StateFlow<UploadState> = _uploadState

    private val _uploadProgress = MutableStateFlow(0f)
    val uploadProgress: StateFlow<Float> = _uploadProgress

    private val _uploadResult = MutableStateFlow<UploadResultData?>(null)
    val uploadResult: StateFlow<UploadResultData?> = _uploadResult

    init {
        _serverUrl.value = authRepository.getServerUrlSync()
        loadCachedUser() // Load cached user first for immediate display
        loadCachedAvatarFile() // Check for locally cached avatar image
        loadUser()
        loadServerVersion()
        observeUploadService()
    }

    private fun observeUploadService() {
        viewModelScope.launch {
            UploadService.uploadState.collect { state ->
                _uploadState.value = when (state.status) {
                    UploadStatus.IDLE -> UploadState.IDLE
                    UploadStatus.UPLOADING -> UploadState.UPLOADING
                    UploadStatus.SUCCESS -> UploadState.SUCCESS
                    UploadStatus.ERROR -> UploadState.ERROR
                }
                _uploadProgress.value = state.progress
                if (state.status == UploadStatus.SUCCESS || state.status == UploadStatus.ERROR) {
                    _uploadResult.value = UploadResultData(
                        success = state.status == UploadStatus.SUCCESS,
                        message = state.message
                    )
                }
            }
        }
    }

    private fun loadCachedAvatarFile() {
        if (authRepository.hasCachedAvatarImage()) {
            _cachedAvatarFile.value = authRepository.getCachedAvatarFile()
        }
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
                avatar = authRepository.getCachedAvatar()
            )
        }
    }

    private fun loadUser() {
        viewModelScope.launch {
            try {
                val response = api.getProfile()
                if (response.isSuccessful) {
                    val loadedUser = response.body()
                    _user.value = loadedUser
                    // Cache user info for offline use
                    loadedUser?.let {
                        if (it.username != null) {
                            authRepository.saveUserInfo(it.username, it.displayName, it.avatar)
                        }
                        // Download and cache avatar image if user has one
                        if (it.avatar != null) {
                            cacheAvatarImage()
                        }
                    }
                } else {
                }
            } catch (e: Exception) {
                // Offline - keep using cached user

            }
        }
    }

    private fun cacheAvatarImage() {
        val serverUrl = _serverUrl.value ?: return
        val avatarHash = _user.value?.avatar?.hashCode() ?: return

        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val avatarUrl = "$serverUrl/api/profile/avatar?v=$avatarHash"

                    val request = Request.Builder()
                        .url(avatarUrl)
                        .build()

                    okHttpClient.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            val avatarFile = authRepository.getCachedAvatarFile()
                            response.body?.byteStream()?.use { input ->
                                avatarFile.outputStream().use { output ->
                                    input.copyTo(output)
                                }
                            }
                            _cachedAvatarFile.value = avatarFile
                        } else {
                            android.util.Log.e("MainViewModel", "Failed to cache avatar: ${response.code}")
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "Error caching avatar", e)
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

    /**
     * Refresh user profile and avatar cache.
     * Called when avatar is changed in ProfileScreen.
     */
    fun refreshProfile() {

        // Clear existing cached avatar file
        val existingFile = _cachedAvatarFile.value
        if (existingFile != null && existingFile.exists()) {
            existingFile.delete()
        }
        _cachedAvatarFile.value = null

        // Force reload user data after a delay to ensure server has processed the upload
        viewModelScope.launch {
            // Small delay to ensure server has processed the new avatar
            kotlinx.coroutines.delay(1500)
            loadUser()
        }
    }

    fun clearUploadResult() {
        _uploadResult.value = null
        _uploadState.value = UploadState.IDLE
        UploadService.resetState()
    }

    /**
     * Upload files as separate audiobooks (one book per file).
     * Delegates to UploadService foreground service to survive screen off.
     */
    fun uploadAsSeparateBooks(
        context: Context,
        uris: List<Uri>,
        title: String?,
        author: String?,
        narrator: String?
    ) {
        if (uris.isEmpty()) return
        UploadService.startUploadSeparate(context, uris, title, author, narrator)
    }

    /**
     * Upload multiple files as a single audiobook (chapters of one book).
     * Delegates to UploadService foreground service to survive screen off.
     */
    fun uploadAsSingleBook(
        context: Context,
        uris: List<Uri>,
        title: String?,
        author: String?
    ) {
        if (uris.isEmpty()) return
        UploadService.startUploadSingle(context, uris, title, author)
    }
}
