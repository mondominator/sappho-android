package com.sappho.audiobooks.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sappho.audiobooks.data.remote.SapphoApi
import com.sappho.audiobooks.data.remote.PasswordUpdateRequest
import com.sappho.audiobooks.data.remote.ProfileUpdateRequest
import com.sappho.audiobooks.data.repository.AuthRepository
import com.sappho.audiobooks.data.repository.UserPreferencesRepository
import com.sappho.audiobooks.domain.model.User
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UserSettingsViewModel @Inject constructor(
    private val api: SapphoApi,
    private val authRepository: AuthRepository,
    val userPreferences: UserPreferencesRepository
) : ViewModel() {

    private val _user = MutableStateFlow<User?>(null)
    val user: StateFlow<User?> = _user

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    private val _serverVersion = MutableStateFlow<String?>(null)
    val serverVersion: StateFlow<String?> = _serverVersion

    init {
        loadProfile()
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
            } finally {
                _isLoading.value = false
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
            }
        }
    }

    fun updateProfile(displayName: String?, email: String?) {
        viewModelScope.launch {
            _isSaving.value = true
            try {
                val response = api.updateProfile(ProfileUpdateRequest(displayName, email))
                if (response.isSuccessful) {
                    _user.value = response.body()
                    _message.value = "Profile updated"
                } else {
                    _message.value = "Failed to update profile"
                }
            } catch (e: Exception) {
                _message.value = "Error: ${e.message}"
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun updatePassword(currentPassword: String, newPassword: String) {
        viewModelScope.launch {
            _isSaving.value = true
            try {
                val response = api.updatePassword(PasswordUpdateRequest(currentPassword, newPassword))
                if (response.isSuccessful) {
                    _message.value = "Password updated"
                } else {
                    _message.value = response.errorBody()?.string() ?: "Failed to update password"
                }
            } catch (e: Exception) {
                _message.value = "Error: ${e.message}"
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun clearMessage() {
        _message.value = null
    }
}
