package com.sappho.audiobooks.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sappho.audiobooks.data.remote.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val api: SapphoApi
) : ViewModel() {

    private val _librarySettings = MutableStateFlow<LibrarySettings?>(null)
    val librarySettings: StateFlow<LibrarySettings?> = _librarySettings

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    // AI Settings
    private val _aiSettings = MutableStateFlow<AiSettings?>(null)
    val aiSettings: StateFlow<AiSettings?> = _aiSettings

    // User Management
    private val _users = MutableStateFlow<List<UserInfo>>(emptyList())
    val users: StateFlow<List<UserInfo>> = _users

    // Scan result
    private val _scanResult = MutableStateFlow<ScanResult?>(null)
    val scanResult: StateFlow<ScanResult?> = _scanResult

    init {
        loadLibrarySettings()
        loadAiSettings()
        loadUsers()
    }

    private fun loadLibrarySettings() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val response = api.getLibrarySettings()
                if (response.isSuccessful) {
                    _librarySettings.value = response.body()
                }
            } catch (e: Exception) {
                _message.value = "Failed to load settings"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun updateLibrarySettings(libraryPath: String, uploadPath: String) {
        viewModelScope.launch {
            _isSaving.value = true
            _message.value = null
            try {
                val response = api.updateLibrarySettings(LibrarySettings(libraryPath, uploadPath))
                if (response.isSuccessful) {
                    _librarySettings.value = response.body()
                    _message.value = "Library settings updated"
                    // Trigger scan after updating settings
                    scanLibrary(refresh = false)
                } else {
                    _message.value = "Failed to update library settings"
                }
            } catch (e: Exception) {
                _message.value = "Error: ${e.message}"
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun scanLibrary(refresh: Boolean = false) {
        viewModelScope.launch {
            _isScanning.value = true
            _message.value = null
            _scanResult.value = null
            try {
                val response = api.scanLibrary(refresh)
                if (response.isSuccessful) {
                    val result = response.body()
                    _scanResult.value = result
                    val stats = result?.stats
                    if (stats?.scanning == true) {
                        _message.value = result.message ?: "Scan started in background"
                    } else {
                        _message.value = buildString {
                            append("Scan complete! ")
                            append("Imported: ${stats?.imported ?: 0}, ")
                            append("Skipped: ${stats?.skipped ?: 0}, ")
                            append("Errors: ${stats?.errors ?: 0}")
                            if (stats?.metadataRefreshed != null) {
                                append("\nMetadata refreshed: ${stats.metadataRefreshed}")
                            }
                        }
                    }
                } else {
                    _message.value = "Failed to scan library"
                }
            } catch (e: Exception) {
                _message.value = "Error: ${e.message}"
            } finally {
                _isScanning.value = false
            }
        }
    }

    fun forceRescanLibrary() {
        viewModelScope.launch {
            _isScanning.value = true
            _message.value = null
            _scanResult.value = null
            try {
                val response = api.forceRescanLibrary()
                if (response.isSuccessful) {
                    val result = response.body()
                    _scanResult.value = result
                    val stats = result?.stats
                    _message.value = buildString {
                        append("Force rescan complete! ")
                        append("Imported: ${stats?.imported ?: 0}, ")
                        append("Total files: ${stats?.totalFiles ?: 0}")
                    }
                } else {
                    _message.value = "Failed to force rescan"
                }
            } catch (e: Exception) {
                _message.value = "Error: ${e.message}"
            } finally {
                _isScanning.value = false
            }
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    fun clearScanResult() {
        _scanResult.value = null
    }

    // AI Settings functions
    fun loadAiSettings() {
        viewModelScope.launch {
            try {
                val response = api.getAiSettings()
                if (response.isSuccessful) {
                    _aiSettings.value = response.body()?.settings
                }
            } catch (e: Exception) {
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
                onResult(false, e.message ?: "Connection failed")
            }
        }
    }

    // User Management functions
    fun loadUsers() {
        viewModelScope.launch {
            try {
                val response = api.getUsers()
                if (response.isSuccessful) {
                    _users.value = response.body() ?: emptyList()
                }
            } catch (e: Exception) {
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
                onResult(false, e.message ?: "Error deleting user")
            }
        }
    }
}
