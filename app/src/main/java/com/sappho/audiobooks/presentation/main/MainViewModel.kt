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
import java.io.IOException
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

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
    }

    /**
     * Upload files as separate audiobooks (one book per file)
     */
    fun uploadAsSeparateBooks(
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
                    val fileName = getFileNameFromUri(context, uri) ?: "audiobook_${System.currentTimeMillis()}.mp3"
                    var tempFile: File? = null

                    try {
                        // Copy file from URI to temp file for uploading
                        val inputStream = context.contentResolver.openInputStream(uri)
                            ?: throw IOException("Cannot read file: $fileName")

                        tempFile = File(context.cacheDir, fileName)

                        inputStream.use { input ->
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

                        if (response.isSuccessful) {
                            successCount++
                        } else {
                            failCount++
                            android.util.Log.e("MainViewModel", "Upload failed: ${response.code()}")
                        }
                    } catch (e: CancellationException) {
                        throw e // Respect coroutine cancellation
                    } catch (e: Exception) {
                        failCount++
                        android.util.Log.e("MainViewModel", "Upload error for file: $fileName", e)
                    } finally {
                        // Always clean up temp file
                        tempFile?.let { file ->
                            try {
                                if (file.exists()) file.delete()
                            } catch (e: Exception) {
                            }
                        }
                    }

                    _uploadProgress.value = (index + 1).toFloat() / totalFiles
                }

                // Set result
                _uploadState.value = if (failCount == 0) UploadState.SUCCESS else UploadState.ERROR
                _uploadResult.value = UploadResultData(
                    success = failCount == 0,
                    message = if (failCount == 0) {
                        "Successfully uploaded $successCount audiobook(s)"
                    } else {
                        "Uploaded $successCount audiobook(s), $failCount failed"
                    }
                )

            } catch (e: CancellationException) {
                throw e // Respect coroutine cancellation
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

    /**
     * Upload multiple files as a single audiobook (chapters of one book)
     */
    fun uploadAsSingleBook(
        context: Context,
        uris: List<Uri>,
        title: String?,
        author: String?
    ) {
        if (uris.isEmpty()) return

        viewModelScope.launch {
            _uploadState.value = UploadState.UPLOADING
            _uploadProgress.value = 0f
            _uploadResult.value = null

            val tempFiles = mutableListOf<File>()

            try {
                val fileParts = mutableListOf<MultipartBody.Part>()

                // Prepare all files
                uris.forEachIndexed { index, uri ->
                    val fileName = getFileNameFromUri(context, uri) ?: "chapter_${index + 1}.mp3"
                    val inputStream = context.contentResolver.openInputStream(uri)
                        ?: throw IOException("Cannot read file: $fileName")

                    val tempFile = File(context.cacheDir, fileName)
                    tempFiles.add(tempFile)

                    inputStream.use { input ->
                        tempFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }

                    val requestFile = tempFile.asRequestBody("audio/*".toMediaTypeOrNull())
                    val filePart = MultipartBody.Part.createFormData("files", fileName, requestFile)
                    fileParts.add(filePart)

                    _uploadProgress.value = (index + 1).toFloat() / (uris.size * 2) // First half is prep
                }

                // Upload all files as single book
                val response = api.uploadMultiFile(
                    files = fileParts,
                    title = title?.toRequestBody("text/plain".toMediaTypeOrNull()),
                    author = author?.toRequestBody("text/plain".toMediaTypeOrNull())
                )

                _uploadProgress.value = 1f

                if (response.isSuccessful) {
                    _uploadState.value = UploadState.SUCCESS
                    _uploadResult.value = UploadResultData(
                        success = true,
                        message = "Successfully uploaded audiobook with ${uris.size} file(s)"
                    )
                } else {
                    _uploadState.value = UploadState.ERROR
                    _uploadResult.value = UploadResultData(
                        success = false,
                        message = "Upload failed: ${response.code()}"
                    )
                }

            } catch (e: CancellationException) {
                throw e // Respect coroutine cancellation
            } catch (e: IOException) {
                android.util.Log.e("MainViewModel", "File read error", e)
                _uploadState.value = UploadState.ERROR
                _uploadResult.value = UploadResultData(
                    success = false,
                    message = "Failed to read file: ${e.message}"
                )
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "Upload error", e)
                _uploadState.value = UploadState.ERROR
                _uploadResult.value = UploadResultData(
                    success = false,
                    message = "Upload failed: ${e.message}"
                )
            } finally {
                // Always clean up temp files
                tempFiles.forEach { file ->
                    try {
                        if (file.exists()) file.delete()
                    } catch (e: Exception) {
                    }
                }
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
