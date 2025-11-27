package com.sappho.audiobooks.presentation.profile

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sappho.audiobooks.data.remote.PasswordUpdateRequest
import com.sappho.audiobooks.data.remote.ProfileUpdateRequest
import com.sappho.audiobooks.data.remote.SapphoApi
import com.sappho.audiobooks.data.repository.AuthRepository
import com.sappho.audiobooks.domain.model.User
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
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _user = MutableStateFlow<User?>(null)
    val user: StateFlow<User?> = _user

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

    init {
        _serverUrl.value = authRepository.getServerUrlSync()
        loadProfile()
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
    }
}
