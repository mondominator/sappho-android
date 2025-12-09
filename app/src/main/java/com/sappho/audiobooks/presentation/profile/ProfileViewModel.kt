package com.sappho.audiobooks.presentation.profile

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sappho.audiobooks.data.remote.*
import com.sappho.audiobooks.data.repository.AuthRepository
import com.sappho.audiobooks.data.repository.UserPreferencesRepository
import com.sappho.audiobooks.domain.model.User
import com.sappho.audiobooks.domain.model.UserStats
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val api: SapphoApi,
    private val authRepository: AuthRepository,
    val userPreferences: UserPreferencesRepository
) : ViewModel() {

    private val _user = MutableStateFlow<User?>(null)
    val user: StateFlow<User?> = _user

    private val _stats = MutableStateFlow<UserStats?>(null)
    val stats: StateFlow<UserStats?> = _stats

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving

    private val _saveMessage = MutableStateFlow<String?>(null)
    val saveMessage: StateFlow<String?> = _saveMessage

    private val _serverUrl = MutableStateFlow<String?>(null)
    val serverUrl: StateFlow<String?> = _serverUrl

    private val _avatarUri = MutableStateFlow<Uri?>(null)
    val avatarUri: StateFlow<Uri?> = _avatarUri

    private val _serverVersion = MutableStateFlow<String?>(null)
    val serverVersion: StateFlow<String?> = _serverVersion

    // Admin state
    private val _users = MutableStateFlow<List<UserInfo>>(emptyList())
    val users: StateFlow<List<UserInfo>> = _users

    private val _aiSettings = MutableStateFlow<AiSettings?>(null)
    val aiSettings: StateFlow<AiSettings?> = _aiSettings

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning

    private val _scanResult = MutableStateFlow<ScanResult?>(null)
    val scanResult: StateFlow<ScanResult?> = _scanResult

    init {
        _serverUrl.value = authRepository.getServerUrlSync()
        loadProfile()
        loadStats()
        loadServerVersion()
    }

    private fun loadProfile() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val response = api.getProfile()
                if (response.isSuccessful) {
                    _user.value = response.body()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun loadStats() {
        viewModelScope.launch {
            try {
                val response = api.getProfileStats()
                if (response.isSuccessful) {
                    _stats.value = response.body()
                }
            } catch (e: Exception) {
                e.printStackTrace()
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
                e.printStackTrace()
            }
        }
    }

    fun updateProfile(displayName: String?, email: String?) {
        viewModelScope.launch {
            _isSaving.value = true
            _saveMessage.value = null
            try {
                val response = api.updateProfile(ProfileUpdateRequest(displayName, email))
                if (response.isSuccessful) {
                    _user.value = response.body()
                    _saveMessage.value = "Profile updated successfully"
                } else {
                    _saveMessage.value = "Failed to update profile"
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _saveMessage.value = "Error: ${e.message}"
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun updateProfileWithAvatar(displayName: String?, email: String?, avatarFile: File?) {
        viewModelScope.launch {
            _isSaving.value = true
            _saveMessage.value = null
            try {
                val displayNameBody = displayName?.toRequestBody("text/plain".toMediaTypeOrNull())
                val emailBody = email?.toRequestBody("text/plain".toMediaTypeOrNull())

                val avatarPart = avatarFile?.let { file ->
                    val requestFile = file.asRequestBody("image/*".toMediaTypeOrNull())
                    MultipartBody.Part.createFormData("avatar", file.name, requestFile)
                }

                val response = api.updateProfileWithAvatar(displayNameBody, emailBody, avatarPart)
                if (response.isSuccessful) {
                    _user.value = response.body()
                    _avatarUri.value = null // Clear the local preview
                    _saveMessage.value = "Profile updated successfully"
                } else {
                    _saveMessage.value = "Failed to update profile"
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _saveMessage.value = "Error: ${e.message}"
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun setAvatarUri(uri: Uri?) {
        _avatarUri.value = uri
    }

    fun deleteAvatar() {
        viewModelScope.launch {
            _isSaving.value = true
            _saveMessage.value = null
            try {
                val response = api.deleteAvatar()
                if (response.isSuccessful) {
                    // Reload profile to get updated avatar state
                    loadProfile()
                    _avatarUri.value = null
                    _saveMessage.value = "Avatar removed"
                } else {
                    _saveMessage.value = "Failed to remove avatar"
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _saveMessage.value = "Error: ${e.message}"
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun updatePassword(currentPassword: String, newPassword: String) {
        viewModelScope.launch {
            _isSaving.value = true
            _saveMessage.value = null
            try {
                val response = api.updatePassword(PasswordUpdateRequest(currentPassword, newPassword))
                if (response.isSuccessful) {
                    _saveMessage.value = "Password updated successfully"
                } else {
                    val errorBody = response.errorBody()?.string()
                    _saveMessage.value = when (response.code()) {
                        401 -> "Current password is incorrect"
                        else -> errorBody ?: "Failed to update password"
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _saveMessage.value = "Error: ${e.message}"
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun clearMessage() {
        _saveMessage.value = null
    }

    fun refresh() {
        loadProfile()
        loadStats()
    }

    // Admin functions
    fun loadUsers() {
        viewModelScope.launch {
            try {
                val response = api.getUsers()
                if (response.isSuccessful) {
                    _users.value = response.body() ?: emptyList()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun loadAiSettings() {
        viewModelScope.launch {
            try {
                val response = api.getAiSettings()
                if (response.isSuccessful) {
                    _aiSettings.value = response.body()?.settings
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun updateAiSettings(settings: AiSettingsUpdate, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                val response = api.updateAiSettings(settings)
                if (response.isSuccessful) {
                    loadAiSettings()
                    onResult(true, "AI settings updated")
                } else {
                    onResult(false, "Failed to update AI settings")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                onResult(false, e.message ?: "Error updating settings")
            }
        }
    }

    fun testAiConnection(settings: AiSettingsUpdate, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                val response = api.testAiConnection(settings)
                if (response.isSuccessful) {
                    val body = response.body()
                    onResult(true, body?.message ?: "Connection successful")
                } else {
                    val errorBody = response.errorBody()?.string()
                    onResult(false, errorBody ?: "Connection failed")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                onResult(false, e.message ?: "Connection failed")
            }
        }
    }

    fun scanLibrary(forceRescan: Boolean = false) {
        viewModelScope.launch {
            _isScanning.value = true
            _scanResult.value = null
            try {
                val response = if (forceRescan) {
                    api.forceRescan()
                } else {
                    api.scanLibrary()
                }
                if (response.isSuccessful) {
                    _scanResult.value = response.body()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isScanning.value = false
            }
        }
    }

    fun createUser(username: String, password: String, email: String?, isAdmin: Boolean, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                val response = api.createUser(CreateUserRequest(username, password, email, isAdmin))
                if (response.isSuccessful) {
                    loadUsers()
                    onResult(true, "User created")
                } else {
                    val errorBody = response.errorBody()?.string()
                    onResult(false, errorBody ?: "Failed to create user")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                onResult(false, e.message ?: "Error creating user")
            }
        }
    }

    fun deleteUser(userId: Int, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                val response = api.deleteUser(userId)
                if (response.isSuccessful) {
                    loadUsers()
                    onResult(true, "User deleted")
                } else {
                    val errorBody = response.errorBody()?.string()
                    onResult(false, errorBody ?: "Failed to delete user")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                onResult(false, e.message ?: "Error deleting user")
            }
        }
    }

    fun clearScanResult() {
        _scanResult.value = null
    }
}
