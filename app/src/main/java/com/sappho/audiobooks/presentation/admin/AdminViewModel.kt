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

    // Track which sections have been loaded
    private val loadedSections = mutableSetOf<String>()

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

    // Loading states per section to avoid global spinner
    private val _loadingSection = MutableStateFlow<String?>(null)
    val loadingSection: StateFlow<String?> = _loadingSection

    // Keep for backwards compatibility
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    fun clearMessage() {
        _message.value = null
    }

    // ============ Server Settings ============
    fun loadServerSettings() {
        // Only load once per session (prevents flickering)
        if ("serverSettings" in loadedSections) return
        loadedSections.add("serverSettings")

        viewModelScope.launch {
            _loadingSection.value = "serverSettings"
            try {
                val response = api.getServerSettings()
                if (response.isSuccessful) {
                    _serverSettings.value = response.body()
                } else {
                    android.util.Log.e("AdminViewModel", "Server settings error: ${response.code()}")
                }
            } catch (e: Exception) {
                android.util.Log.e("AdminViewModel", "Server settings exception", e)
                _message.value = "Failed to load server settings"
            } finally {
                _loadingSection.value = null
            }
        }
    }

    fun refreshServerSettings() {
        viewModelScope.launch {
            _loadingSection.value = "serverSettings"
            try {
                val response = api.getServerSettings()
                if (response.isSuccessful) {
                    _serverSettings.value = response.body()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _message.value = "Failed to load server settings"
            } finally {
                _loadingSection.value = null
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
        if ("aiSettings" in loadedSections) return
        loadedSections.add("aiSettings")

        viewModelScope.launch {
            _loadingSection.value = "aiSettings"
            try {
                val response = api.getAiSettings()
                if (response.isSuccessful) {
                    _aiSettings.value = response.body()?.settings
                } else {
                    android.util.Log.e("AdminViewModel", "AI settings error: ${response.code()}")
                }
            } catch (e: Exception) {
                android.util.Log.e("AdminViewModel", "AI settings exception", e)
                _message.value = "Failed to load AI settings"
            } finally {
                _loadingSection.value = null
            }
        }
    }

    fun refreshAiSettings() {
        viewModelScope.launch {
            _loadingSection.value = "aiSettings"
            try {
                val response = api.getAiSettings()
                if (response.isSuccessful) {
                    _aiSettings.value = response.body()?.settings
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _message.value = "Failed to load AI settings"
            } finally {
                _loadingSection.value = null
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
        if ("users" in loadedSections) return
        loadedSections.add("users")

        viewModelScope.launch {
            _loadingSection.value = "users"
            try {
                val response = api.getUsers()
                if (response.isSuccessful) {
                    _users.value = response.body() ?: emptyList()
                } else {
                    android.util.Log.e("AdminViewModel", "Users error: ${response.code()}")
                }
            } catch (e: Exception) {
                android.util.Log.e("AdminViewModel", "Users exception", e)
                _message.value = "Failed to load users"
            } finally {
                _loadingSection.value = null
            }
        }
    }

    fun refreshUsers() {
        viewModelScope.launch {
            _loadingSection.value = "users"
            try {
                val response = api.getUsers()
                if (response.isSuccessful) {
                    _users.value = response.body() ?: emptyList()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _message.value = "Failed to load users"
            } finally {
                _loadingSection.value = null
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
        if ("backups" in loadedSections) return
        loadedSections.add("backups")

        viewModelScope.launch {
            _loadingSection.value = "backups"
            try {
                val response = api.getBackups()
                if (response.isSuccessful) {
                    _backups.value = response.body()?.backups ?: emptyList()
                } else {
                    android.util.Log.e("AdminViewModel", "Backups error: ${response.code()}")
                }
            } catch (e: Exception) {
                android.util.Log.e("AdminViewModel", "Backups exception", e)
                _message.value = "Failed to load backups"
            } finally {
                _loadingSection.value = null
            }
        }
    }

    fun refreshBackups() {
        viewModelScope.launch {
            _loadingSection.value = "backups"
            try {
                val response = api.getBackups()
                if (response.isSuccessful) {
                    _backups.value = response.body()?.backups ?: emptyList()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _message.value = "Failed to load backups"
            } finally {
                _loadingSection.value = null
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
        if ("logs" in loadedSections) return
        loadedSections.add("logs")

        viewModelScope.launch {
            _loadingSection.value = "logs"
            try {
                val response = api.getLogs(lines, level)
                if (response.isSuccessful) {
                    _logs.value = response.body()?.logs ?: emptyList()
                } else {
                    android.util.Log.e("AdminViewModel", "Logs error: ${response.code()}")
                }
            } catch (e: Exception) {
                android.util.Log.e("AdminViewModel", "Logs exception", e)
                _message.value = "Failed to load logs"
            } finally {
                _loadingSection.value = null
            }
        }
    }

    fun refreshLogs(lines: Int = 100, level: String? = null) {
        viewModelScope.launch {
            _loadingSection.value = "logs"
            try {
                val response = api.getLogs(lines, level)
                if (response.isSuccessful) {
                    _logs.value = response.body()?.logs ?: emptyList()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _message.value = "Failed to load logs"
            } finally {
                _loadingSection.value = null
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
        if ("statistics" in loadedSections) return
        loadedSections.add("statistics")

        viewModelScope.launch {
            _loadingSection.value = "statistics"
            try {
                val response = api.getLibraryStatistics()
                if (response.isSuccessful) {
                    _statistics.value = response.body()
                } else {
                    android.util.Log.e("AdminViewModel", "Statistics error: ${response.code()}")
                }
            } catch (e: Exception) {
                android.util.Log.e("AdminViewModel", "Statistics exception", e)
                _message.value = "Failed to load statistics"
            } finally {
                _loadingSection.value = null
            }
        }
    }

    fun loadDuplicates() {
        if ("duplicates" in loadedSections) return
        loadedSections.add("duplicates")

        viewModelScope.launch {
            _loadingSection.value = "duplicates"
            try {
                val response = api.getDuplicates()
                if (response.isSuccessful) {
                    _duplicates.value = response.body()?.duplicates ?: emptyList()
                } else {
                    android.util.Log.e("AdminViewModel", "Duplicates error: ${response.code()}")
                }
            } catch (e: Exception) {
                android.util.Log.e("AdminViewModel", "Duplicates exception", e)
                _message.value = "Failed to load duplicates"
            } finally {
                _loadingSection.value = null
            }
        }
    }

    fun refreshDuplicates() {
        viewModelScope.launch {
            _loadingSection.value = "duplicates"
            try {
                val response = api.getDuplicates()
                if (response.isSuccessful) {
                    _duplicates.value = response.body()?.duplicates ?: emptyList()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _message.value = "Failed to load duplicates"
            } finally {
                _loadingSection.value = null
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
        if ("jobs" in loadedSections) return
        loadedSections.add("jobs")

        viewModelScope.launch {
            _loadingSection.value = "jobs"
            try {
                val response = api.getJobs()
                if (response.isSuccessful) {
                    _jobs.value = response.body() ?: emptyList()
                } else {
                    android.util.Log.e("AdminViewModel", "Jobs error: ${response.code()}")
                }
            } catch (e: Exception) {
                android.util.Log.e("AdminViewModel", "Jobs exception", e)
                _message.value = "Failed to load jobs"
            } finally {
                _loadingSection.value = null
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
