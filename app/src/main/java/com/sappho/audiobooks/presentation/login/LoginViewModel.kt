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

                if (response.isSuccessful) {
                    response.body()?.let { authResponse ->
                        authRepository.saveToken(authResponse.token)
                        _uiState.value = LoginUiState.Success
                    } ?: run {
                        _uiState.value = LoginUiState.Error("Invalid response from server")
                    }
                } else {
                    val errorMessage = when (response.code()) {
                        401 -> "Invalid username or password"
                        404 -> "Server endpoint not found. Please check your server URL"
                        500 -> "Server error. Please try again later"
                        else -> response.errorBody()?.string() ?: "Login failed (${response.code()})"
                    }
                    _uiState.value = LoginUiState.Error(errorMessage)
                }
            } catch (e: Exception) {
                val errorMessage = when (e) {
                    is java.net.UnknownHostException -> "Cannot reach server. Please check your server URL and network connection"
                    is java.net.ConnectException -> "Connection failed. Is the server running?"
                    is java.net.SocketTimeoutException -> "Connection timed out. Please check your server URL"
                    is javax.net.ssl.SSLHandshakeException -> "SSL certificate error. If using self-signed certificates, enable 'Allow cleartext traffic'"
                    is java.io.IOException -> "Network error: ${e.message}"
                    else -> "Unexpected error: ${e.message ?: "Please try again"}"
                }
                _uiState.value = LoginUiState.Error(errorMessage)
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
