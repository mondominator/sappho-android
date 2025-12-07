package com.sappho.audiobooks.presentation.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sappho.audiobooks.data.remote.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AdminViewModel @Inject constructor(
    private val api: SapphoApi
) : ViewModel() {

    // Server Settings
    private val _serverSettings = MutableStateFlow<ServerSettingsResponse?>(null)
    val serverSettings: StateFlow<ServerSettingsResponse?> = _serverSettings

    // AI Settings
    private val _aiSettings = MutableStateFlow<AiSettings?>(null)
    val aiSettings: StateFlow<AiSettings?> = _aiSettings

    // Users
    private val _users = MutableStateFlow<List<UserInfo>>(emptyList())
    val users: StateFlow<List<UserInfo>> = _users

    // Backups
    private val _backups = MutableStateFlow<List<BackupInfo>>(emptyList())
    val backups: StateFlow<List<BackupInfo>> = _backups

    private val _backupRetention = MutableStateFlow<BackupRetention?>(null)
    val backupRetention: StateFlow<BackupRetention?> = _backupRetention

    // Maintenance
    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs

    private val _statistics = MutableStateFlow<LibraryStatistics?>(null)
    val statistics: StateFlow<LibraryStatistics?> = _statistics

    private val _duplicates = MutableStateFlow<List<DuplicateGroup>>(emptyList())
    val duplicates: StateFlow<List<DuplicateGroup>> = _duplicates

    private val _jobs = MutableStateFlow<List<JobInfo>>(emptyList())
    val jobs: StateFlow<List<JobInfo>> = _jobs

    // Loading states
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    fun clearMessage() {
        _message.value = null
    }

    // ============ Server Settings ============
    fun loadServerSettings() {
        // Don't reload if already have data (prevents flickering)
        if (_serverSettings.value != null) return

        viewModelScope.launch {
            _isLoading.value = true
            try {
                val response = api.getServerSettings()
                if (response.isSuccessful) {
                    _serverSettings.value = response.body()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _message.value = "Failed to load server settings"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun refreshServerSettings() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val response = api.getServerSettings()
                if (response.isSuccessful) {
                    _serverSettings.value = response.body()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _message.value = "Failed to load server settings"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun updateServerSettings(settings: ServerSettingsUpdate) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val response = api.updateServerSettings(settings)
                if (response.isSuccessful) {
                    _message.value = "Server settings updated"
                    refreshServerSettings()
                } else {
                    _message.value = "Failed to update settings"
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _message.value = "Error: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    // ============ AI Settings ============
    fun loadAiSettings() {
        // Don't reload if already have data (prevents flickering)
        if (_aiSettings.value != null) return

        viewModelScope.launch {
            _isLoading.value = true
            try {
                val response = api.getAiSettings()
                if (response.isSuccessful) {
                    _aiSettings.value = response.body()?.settings
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _message.value = "Failed to load AI settings"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun refreshAiSettings() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val response = api.getAiSettings()
                if (response.isSuccessful) {
                    _aiSettings.value = response.body()?.settings
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _message.value = "Failed to load AI settings"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun updateAiSettings(settings: AiSettingsUpdate) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val response = api.updateAiSettings(settings)
                if (response.isSuccessful) {
                    _message.value = "AI settings updated"
                    refreshAiSettings()
                } else {
                    _message.value = "Failed to update AI settings"
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _message.value = "Error: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun testAiConnection(settings: AiSettingsUpdate, onResult: (AiTestResponse) -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val response = api.testAiConnection(settings)
                if (response.isSuccessful) {
                    response.body()?.let { onResult(it) }
                } else {
                    _message.value = "AI test failed"
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _message.value = "Error: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    // ============ Users ============
    fun loadUsers() {
        // Don't reload if already have data (prevents flickering)
        if (_users.value.isNotEmpty()) return

        viewModelScope.launch {
            _isLoading.value = true
            try {
                val response = api.getUsers()
                if (response.isSuccessful) {
                    _users.value = response.body() ?: emptyList()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _message.value = "Failed to load users"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun refreshUsers() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val response = api.getUsers()
                if (response.isSuccessful) {
                    _users.value = response.body() ?: emptyList()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _message.value = "Failed to load users"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun createUser(request: CreateUserRequest, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val response = api.createUser(request)
                if (response.isSuccessful) {
                    _message.value = "User created"
                    refreshUsers()
                    onSuccess()
                } else {
                    _message.value = "Failed to create user"
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _message.value = "Error: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun updateUser(id: Int, request: UpdateUserRequest, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val response = api.updateUser(id, request)
                if (response.isSuccessful) {
                    _message.value = "User updated"
                    refreshUsers()
                    onSuccess()
                } else {
                    _message.value = "Failed to update user"
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _message.value = "Error: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun deleteUser(id: Int) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val response = api.deleteUser(id)
                if (response.isSuccessful) {
                    _message.value = "User deleted"
                    refreshUsers()
                } else {
                    _message.value = "Failed to delete user"
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _message.value = "Error: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    // ============ Backups ============
    fun loadBackups() {
        // Don't reload if already have data (prevents flickering)
        if (_backups.value.isNotEmpty()) return

        viewModelScope.launch {
            _isLoading.value = true
            try {
                val response = api.getBackups()
                if (response.isSuccessful) {
                    _backups.value = response.body()?.backups ?: emptyList()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _message.value = "Failed to load backups"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun refreshBackups() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val response = api.getBackups()
                if (response.isSuccessful) {
                    _backups.value = response.body()?.backups ?: emptyList()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _message.value = "Failed to load backups"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun createBackup() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val response = api.createBackup()
                if (response.isSuccessful) {
                    _message.value = "Backup created"
                    refreshBackups()
                } else {
                    _message.value = "Failed to create backup"
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _message.value = "Error: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun deleteBackup(filename: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val response = api.deleteBackup(filename)
                if (response.isSuccessful) {
                    _message.value = "Backup deleted"
                    refreshBackups()
                } else {
                    _message.value = "Failed to delete backup"
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _message.value = "Error: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun restoreBackup(filename: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val response = api.restoreBackup(filename)
                if (response.isSuccessful) {
                    _message.value = response.body()?.message ?: "Backup restored"
                } else {
                    _message.value = "Failed to restore backup"
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _message.value = "Error: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun updateBackupRetention(retention: BackupRetention) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val response = api.updateBackupRetention(retention)
                if (response.isSuccessful) {
                    _message.value = "Retention settings updated"
                    _backupRetention.value = response.body()
                } else {
                    _message.value = "Failed to update retention"
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _message.value = "Error: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    // ============ Maintenance ============
    fun loadLogs(lines: Int = 100, level: String? = null) {
        // Don't reload if already have data (prevents flickering)
        if (_logs.value.isNotEmpty()) return

        viewModelScope.launch {
            _isLoading.value = true
            try {
                val response = api.getLogs(lines, level)
                if (response.isSuccessful) {
                    _logs.value = response.body()?.logs ?: emptyList()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _message.value = "Failed to load logs"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun refreshLogs(lines: Int = 100, level: String? = null) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val response = api.getLogs(lines, level)
                if (response.isSuccessful) {
                    _logs.value = response.body()?.logs ?: emptyList()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _message.value = "Failed to load logs"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearLogs() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val response = api.clearLogs()
                if (response.isSuccessful) {
                    _message.value = "Logs cleared"
                    _logs.value = emptyList()
                } else {
                    _message.value = "Failed to clear logs"
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _message.value = "Error: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadStatistics() {
        // Don't reload if already have data (prevents flickering)
        if (_statistics.value != null) return

        viewModelScope.launch {
            _isLoading.value = true
            try {
                val response = api.getLibraryStatistics()
                if (response.isSuccessful) {
                    _statistics.value = response.body()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _message.value = "Failed to load statistics"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadDuplicates() {
        // Don't reload if already have data (prevents flickering)
        if (_duplicates.value.isNotEmpty()) return

        viewModelScope.launch {
            _isLoading.value = true
            try {
                val response = api.getDuplicates()
                if (response.isSuccessful) {
                    _duplicates.value = response.body()?.duplicates ?: emptyList()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _message.value = "Failed to load duplicates"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun refreshDuplicates() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val response = api.getDuplicates()
                if (response.isSuccessful) {
                    _duplicates.value = response.body()?.duplicates ?: emptyList()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _message.value = "Failed to load duplicates"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun mergeDuplicates(keepId: Int, deleteIds: List<Int>) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val response = api.mergeDuplicates(MergeDuplicatesRequest(keepId, deleteIds))
                if (response.isSuccessful) {
                    _message.value = response.body()?.message ?: "Duplicates merged"
                    refreshDuplicates()
                } else {
                    _message.value = "Failed to merge duplicates"
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _message.value = "Error: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadJobs() {
        // Don't reload if already have data (prevents flickering)
        if (_jobs.value.isNotEmpty()) return

        viewModelScope.launch {
            _isLoading.value = true
            try {
                val response = api.getJobs()
                if (response.isSuccessful) {
                    _jobs.value = response.body() ?: emptyList()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _message.value = "Failed to load jobs"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun scanLibrary() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val response = api.scanLibraryMaintenance()
                if (response.isSuccessful) {
                    val stats = response.body()?.stats
                    _message.value = "Scan complete: ${stats?.imported ?: 0} imported, ${stats?.skipped ?: 0} skipped"
                } else {
                    _message.value = "Failed to scan library"
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _message.value = "Error: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun forceRescan() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val response = api.forceRescan()
                if (response.isSuccessful) {
                    val stats = response.body()?.stats
                    _message.value = "Rescan complete: ${stats?.metadataRefreshed ?: 0} refreshed"
                } else {
                    _message.value = "Failed to rescan library"
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _message.value = "Error: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearLibrary(onConfirmed: () -> Unit) {
        // This requires confirmation from UI before calling
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val response = api.clearLibrary()
                if (response.isSuccessful) {
                    _message.value = response.body()?.message ?: "Library cleared"
                    onConfirmed()
                } else {
                    _message.value = "Failed to clear library"
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _message.value = "Error: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
}
