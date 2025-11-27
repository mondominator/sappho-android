package com.sappho.audiobooks.presentation.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sappho.audiobooks.data.remote.LoginRequest
import com.sappho.audiobooks.data.remote.SapphoApi
import com.sappho.audiobooks.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val api: SapphoApi,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Initial)
    val uiState: StateFlow<LoginUiState> = _uiState

    private val _serverUrl = MutableStateFlow(authRepository.getServerUrlSync() ?: "")
    val serverUrl: StateFlow<String> = _serverUrl

    fun updateServerUrl(url: String) {
        _serverUrl.value = url
    }

    fun login(username: String, password: String) {
        if (serverUrl.value.isBlank()) {
            _uiState.value = LoginUiState.Error("Please enter server URL")
            return
        }

        if (username.isBlank() || password.isBlank()) {
            _uiState.value = LoginUiState.Error("Please enter username and password")
            return
        }

        viewModelScope.launch {
            try {
                _uiState.value = LoginUiState.Loading

                // Save server URL
                authRepository.saveServerUrl(serverUrl.value)

                // Attempt login
                val response = api.login(LoginRequest(username, password))

                if (response.isSuccessful && response.body() != null) {
                    val authResponse = response.body()!!
                    authRepository.saveToken(authResponse.token)
                    _uiState.value = LoginUiState.Success
                } else {
                    _uiState.value = LoginUiState.Error(
                        response.errorBody()?.string() ?: "Login failed"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = LoginUiState.Error(
                    e.message ?: "Network error. Please check your server URL and connection."
                )
            }
        }
    }
}

sealed class LoginUiState {
    object Initial : LoginUiState()
    object Loading : LoginUiState()
    object Success : LoginUiState()
    data class Error(val message: String) : LoginUiState()
}
