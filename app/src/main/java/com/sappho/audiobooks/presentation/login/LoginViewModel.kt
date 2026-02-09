package com.sappho.audiobooks.presentation.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sappho.audiobooks.data.remote.LoginRequest
import com.sappho.audiobooks.data.remote.MfaVerifyRequest
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

    private val _serverUrl = MutableStateFlow(authRepository.getServerUrlSync() ?: "https://")
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
                        if (authResponse.mfaRequired && authResponse.mfaToken != null) {
                            // MFA required - show code entry
                            _uiState.value = LoginUiState.MfaRequired(authResponse.mfaToken)
                        } else if (authResponse.token != null) {
                            authRepository.saveToken(authResponse.token)
                            _uiState.value = LoginUiState.Success
                        } else {
                            _uiState.value = LoginUiState.Error("Invalid response from server")
                        }
                    } ?: run {
                        _uiState.value = LoginUiState.Error("Invalid response from server")
                    }
                } else {
                    val errorBody = response.errorBody()?.string() ?: ""
                    val errorMessage = when (response.code()) {
                        401 -> "Invalid username or password"
                        403 -> {
                            if (errorBody.contains("locked", ignoreCase = true)) {
                                "Account is locked. Please try again later or request an unlock email."
                            } else {
                                "Access denied"
                            }
                        }
                        404 -> "Server endpoint not found. Please check your server URL"
                        500 -> "Server error. Please try again later"
                        else -> "Login failed (${response.code()})"
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

    fun verifyMfa(mfaToken: String, code: String) {
        if (code.isBlank()) {
            _uiState.value = LoginUiState.MfaError(mfaToken, "Please enter the verification code")
            return
        }

        viewModelScope.launch {
            try {
                _uiState.value = LoginUiState.Loading

                val response = api.verifyMfa(MfaVerifyRequest(mfaToken, code))

                if (response.isSuccessful) {
                    response.body()?.let { authResponse ->
                        if (authResponse.token != null) {
                            authRepository.saveToken(authResponse.token)
                            _uiState.value = LoginUiState.Success
                        } else {
                            _uiState.value = LoginUiState.MfaError(mfaToken, "Invalid response from server")
                        }
                    } ?: run {
                        _uiState.value = LoginUiState.MfaError(mfaToken, "Invalid response from server")
                    }
                } else {
                    val errorMessage = when (response.code()) {
                        400 -> "Invalid verification code"
                        403 -> "MFA session expired. Please login again."
                        else -> "Verification failed (${response.code()})"
                    }
                    if (response.code() == 403) {
                        // Session expired, go back to login
                        _uiState.value = LoginUiState.Error(errorMessage)
                    } else {
                        _uiState.value = LoginUiState.MfaError(mfaToken, errorMessage)
                    }
                }
            } catch (e: Exception) {
                _uiState.value = LoginUiState.MfaError(mfaToken, "Network error: ${e.message}")
            }
        }
    }

    fun cancelMfa() {
        _uiState.value = LoginUiState.Initial
    }
}

sealed class LoginUiState {
    object Initial : LoginUiState()
    object Loading : LoginUiState()
    object Success : LoginUiState()
    data class Error(val message: String) : LoginUiState()
    data class MfaRequired(val mfaToken: String) : LoginUiState()
    data class MfaError(val mfaToken: String, val message: String) : LoginUiState()
}
