package com.sappho.audiobooks.presentation.main

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sappho.audiobooks.data.remote.SapphoApi
import com.sappho.audiobooks.domain.model.User
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
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

    // Upload state
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
                android.util.Log.d("MainViewModel", "Loading user profile...")
                val response = api.getProfile()
                android.util.Log.d("MainViewModel", "Response: ${response.code()}, body: ${response.body()}")
                if (response.isSuccessful) {
                    val loadedUser = response.body()
                    _user.value = loadedUser
                    // Cache user info for offline use
                    loadedUser?.let {
                        authRepository.saveUserInfo(it.username, it.displayName, it.avatar)
                        // Download and cache avatar image if user has one
                        if (it.avatar != null) {
                            cacheAvatarImage()
                        }
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

    private fun cacheAvatarImage() {
        val serverUrl = _serverUrl.value ?: return
        val avatarHash = _user.value?.avatar?.hashCode() ?: return

        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val avatarUrl = "$serverUrl/api/profile/avatar?v=$avatarHash"
                    android.util.Log.d("MainViewModel", "Caching avatar from: $avatarUrl")

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
                            android.util.Log.d("MainViewModel", "Avatar cached to: ${avatarFile.absolutePath}")
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

    fun clearUploadResult() {
        _uploadResult.value = null
        _uploadState.value = UploadState.IDLE
    }

    fun uploadAudiobooks(
        context: Context,
        uris: List<Uri>,
        title: String?,
        author: String?,
        narrator: String?
    ) {
        if (uris.isEmpty()) return

        viewModelScope.launch {
            _uploadState.value = UploadState.UPLOADING
            _uploadProgress.value = 0f
            _uploadResult.value = null

            try {
                val totalFiles = uris.size
                var successCount = 0
                var failCount = 0

                uris.forEachIndexed { index, uri ->
                    try {
                        // Copy file from URI to temp file for uploading
                        val inputStream = context.contentResolver.openInputStream(uri)
                        val fileName = getFileNameFromUri(context, uri) ?: "audiobook_${System.currentTimeMillis()}.mp3"
                        val tempFile = File(context.cacheDir, fileName)

                        inputStream?.use { input ->
                            tempFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }

                        // Create multipart request
                        val requestFile = tempFile.asRequestBody("audio/*".toMediaTypeOrNull())
                        val filePart = MultipartBody.Part.createFormData("file", fileName, requestFile)

                        // Upload with optional metadata
                        val response = api.uploadAudiobook(
                            file = filePart,
                            title = title?.toRequestBody("text/plain".toMediaTypeOrNull()),
                            author = author?.toRequestBody("text/plain".toMediaTypeOrNull()),
                            narrator = narrator?.toRequestBody("text/plain".toMediaTypeOrNull())
                        )

                        // Clean up temp file
                        tempFile.delete()

                        if (response.isSuccessful) {
                            successCount++
                        } else {
                            failCount++
                            android.util.Log.e("MainViewModel", "Upload failed: ${response.code()}")
                        }
                    } catch (e: Exception) {
                        failCount++
                        android.util.Log.e("MainViewModel", "Upload error for file", e)
                    }

                    _uploadProgress.value = (index + 1).toFloat() / totalFiles
                }

                // Set result
                _uploadState.value = if (failCount == 0) UploadState.SUCCESS else UploadState.ERROR
                _uploadResult.value = UploadResultData(
                    success = failCount == 0,
                    message = if (failCount == 0) {
                        "Successfully uploaded $successCount file(s)"
                    } else {
                        "Uploaded $successCount file(s), $failCount failed"
                    }
                )

            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "Upload error", e)
                _uploadState.value = UploadState.ERROR
                _uploadResult.value = UploadResultData(
                    success = false,
                    message = "Upload failed: ${e.message}"
                )
            }
        }
    }

    private fun getFileNameFromUri(context: Context, uri: Uri): String? {
        var fileName: String? = null
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) {
                    fileName = cursor.getString(nameIndex)
                }
            }
        }
        return fileName
    }
}
