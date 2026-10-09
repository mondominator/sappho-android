package com.sappho.audiobooks.presentation.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sappho.audiobooks.data.remote.CreateUserRequest
import com.sappho.audiobooks.data.remote.SapphoApi
import com.sappho.audiobooks.data.remote.ScanResult
import com.sappho.audiobooks.data.remote.ScanStats
import com.sappho.audiobooks.data.remote.UserInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import retrofit2.Response
import javax.inject.Inject

/**
 * Admin screen state. Mirrors the iOS AdminView: library scan / force rescan,
 * and user management (list, add, delete).
 */
@HiltViewModel
class AdminViewModel @Inject constructor(
    private val api: SapphoApi
) : ViewModel() {

    private val _users = MutableStateFlow<List<UserInfo>>(emptyList())
    val users: StateFlow<List<UserInfo>> = _users

    private val _isLoadingUsers = MutableStateFlow(true)
    val isLoadingUsers: StateFlow<Boolean> = _isLoadingUsers

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning

    /** Result of the last scan/rescan, shown inline under the Library buttons. */
    private val _scanMessage = MutableStateFlow<String?>(null)
    val scanMessage: StateFlow<String?> = _scanMessage

    /** One-shot error/confirmation text for the snackbar. */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    init {
        loadUsers()
    }

    fun clearMessage() {
        _message.value = null
    }

    // ============ Library ============

    fun scanLibrary() = runScan(
        call = { api.scanLibraryMaintenance() },
        success = { stats -> "Scan complete: ${stats?.imported ?: 0} imported, ${stats?.skipped ?: 0} skipped" },
        failure = "Scan failed"
    )

    fun forceRescan() = runScan(
        call = { api.forceRescan() },
        success = { stats -> "Rescan complete: ${stats?.metadataRefreshed ?: 0} refreshed" },
        failure = "Rescan failed"
    )

    private fun runScan(
        call: suspend () -> Response<ScanResult>,
        success: (ScanStats?) -> String,
        failure: String
    ) {
        if (_isScanning.value) return
        viewModelScope.launch {
            _isScanning.value = true
            _scanMessage.value = null
            try {
                val response = call()
                _scanMessage.value = if (response.isSuccessful) {
                    success(response.body()?.stats)
                } else {
                    "$failure (HTTP ${response.code()})"
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _scanMessage.value = "$failure: ${e.message}"
            } finally {
                _isScanning.value = false
            }
        }
    }

    // ============ Users ============

    fun loadUsers() {
        viewModelScope.launch {
            _isLoadingUsers.value = true
            try {
                val response = api.getUsers()
                if (response.isSuccessful) {
                    _users.value = response.body() ?: emptyList()
                } else {
                    _message.value = "Failed to load users"
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _message.value = "Failed to load users"
            } finally {
                _isLoadingUsers.value = false
            }
        }
    }

    /**
     * Creates a user. [onResult] gets null on success, or an error message so the
     * dialog can stay open and show it inline (same as iOS CreateUserSheet).
     */
    fun createUser(username: String, password: String, isAdmin: Boolean, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            try {
                val response = api.createUser(CreateUserRequest(username, password, isAdmin))
                if (response.isSuccessful) {
                    loadUsers()
                    onResult(null)
                } else {
                    onResult("Failed to create user (HTTP ${response.code()})")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onResult(e.message ?: "Failed to create user")
            }
        }
    }

    fun deleteUser(id: Int) {
        viewModelScope.launch {
            try {
                val response = api.deleteUser(id)
                if (response.isSuccessful) {
                    loadUsers()
                } else {
                    _message.value = "Failed to delete user"
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _message.value = "Error: ${e.message}"
            }
        }
    }
}
