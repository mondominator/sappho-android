package com.sappho.audiobooks.presentation.admin

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sappho.audiobooks.data.remote.*
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

    // Upload state
    private val _uploadState = MutableStateFlow(UploadState.IDLE)
    val uploadState: StateFlow<UploadState> = _uploadState

    private val _uploadProgress = MutableStateFlow(0f)
    val uploadProgress: StateFlow<Float> = _uploadProgress

    private val _uploadResult = MutableStateFlow<UploadResultData?>(null)
    val uploadResult: StateFlow<UploadResultData?> = _uploadResult

    fun clearMessage() {
        _message.value = null
    }

    fun clearUploadResult() {
        _uploadResult.value = null
        _uploadState.value = UploadState.IDLE
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

    fun loadBackupRetention() {
        if ("backupRetention" in loadedSections) return
        loadedSections.add("backupRetention")

        viewModelScope.launch {
            try {
                val response = api.getBackupRetention()
                if (response.isSuccessful) {
                    _backupRetention.value = response.body()
                }
            } catch (e: Exception) {
                android.util.Log.e("AdminViewModel", "Backup retention exception", e)
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

    fun refreshStatistics() {
        viewModelScope.launch {
            _loadingSection.value = "statistics"
            try {
                val response = api.getLibraryStatistics()
                if (response.isSuccessful) {
                    _statistics.value = response.body()
                }
            } catch (e: Exception) {
                android.util.Log.e("AdminViewModel", "Statistics refresh exception", e)
            } finally {
                _loadingSection.value = null
            }
        }
    }

    fun refreshLibraryTab() {
        viewModelScope.launch {
            _loadingSection.value = "library"
            try {
                // Refresh server settings
                val settingsResponse = api.getServerSettings()
                if (settingsResponse.isSuccessful) {
                    _serverSettings.value = settingsResponse.body()
                }

                // Refresh duplicates
                val duplicatesResponse = api.getDuplicates()
                if (duplicatesResponse.isSuccessful) {
                    _duplicates.value = duplicatesResponse.body()?.duplicateGroups ?: emptyList()
                }

                // Refresh jobs
                val jobsResponse = api.getJobs()
                if (jobsResponse.isSuccessful) {
                    val jobsMap = jobsResponse.body()?.jobs ?: emptyMap()
                    _jobs.value = jobsMap.map { (key, job) -> job.copy(id = key) }
                }
            } catch (e: Exception) {
                android.util.Log.e("AdminViewModel", "Library tab refresh exception", e)
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
                    _duplicates.value = response.body()?.duplicateGroups ?: emptyList()
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
                    _duplicates.value = response.body()?.duplicateGroups ?: emptyList()
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
                    // Convert map to list - the key becomes the job id
                    val jobsMap = response.body()?.jobs ?: emptyMap()
                    _jobs.value = jobsMap.map { (key, job) -> job.copy(id = key) }
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

    fun refreshJobs() {
        viewModelScope.launch {
            _loadingSection.value = "jobs"
            try {
                val response = api.getJobs()
                if (response.isSuccessful) {
                    val jobsMap = response.body()?.jobs ?: emptyMap()
                    _jobs.value = jobsMap.map { (key, job) -> job.copy(id = key) }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _message.value = "Failed to refresh jobs"
            } finally {
                _loadingSection.value = null
            }
        }
    }

    fun triggerJob(jobId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val response = api.triggerJob(jobId)
                if (response.isSuccessful) {
                    _message.value = response.body()?.message ?: "Job triggered successfully"
                    refreshJobs()
                } else {
                    _message.value = "Failed to trigger job"
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _message.value = "Error: ${e.message}"
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

    // ============ Upload ============
    fun uploadAudiobooks(
        context: Context,
        uris: List<Uri>,
        title: String?,
        author: String?,
        narrator: String?
    ) {
        if (uris.isEmpty()) return

        viewModelScope.launch {
            _uploadState.value = UploadState.UPLOADING
            _uploadProgress.value = 0f
            _uploadResult.value = null

            try {
                val totalFiles = uris.size
                var successCount = 0
                var failCount = 0

                uris.forEachIndexed { index, uri ->
                    try {
                        // Copy file from URI to temp file for uploading
                        val inputStream = context.contentResolver.openInputStream(uri)
                        val fileName = getFileNameFromUri(context, uri) ?: "audiobook_${System.currentTimeMillis()}.mp3"
                        val tempFile = File(context.cacheDir, fileName)

                        inputStream?.use { input ->
                            tempFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }

                        // Create multipart request
                        val requestFile = tempFile.asRequestBody("audio/*".toMediaTypeOrNull())
                        val filePart = MultipartBody.Part.createFormData("file", fileName, requestFile)

                        // Upload with optional metadata
                        val response = api.uploadAudiobook(
                            file = filePart,
                            title = title?.toRequestBody("text/plain".toMediaTypeOrNull()),
                            author = author?.toRequestBody("text/plain".toMediaTypeOrNull()),
                            narrator = narrator?.toRequestBody("text/plain".toMediaTypeOrNull())
                        )

                        // Clean up temp file
                        tempFile.delete()

                        if (response.isSuccessful) {
                            successCount++
                        } else {
                            failCount++
                            android.util.Log.e("AdminViewModel", "Upload failed: ${response.code()}")
                        }
                    } catch (e: Exception) {
                        failCount++
                        android.util.Log.e("AdminViewModel", "Upload error for file", e)
                    }

                    _uploadProgress.value = (index + 1).toFloat() / totalFiles
                }

                // Set result
                _uploadState.value = if (failCount == 0) UploadState.SUCCESS else UploadState.ERROR
                _uploadResult.value = UploadResultData(
                    success = failCount == 0,
                    message = if (failCount == 0) {
                        "Successfully uploaded $successCount file(s)"
                    } else {
                        "Uploaded $successCount file(s), $failCount failed"
                    }
                )

            } catch (e: Exception) {
                android.util.Log.e("AdminViewModel", "Upload error", e)
                _uploadState.value = UploadState.ERROR
                _uploadResult.value = UploadResultData(
                    success = false,
                    message = "Upload failed: ${e.message}"
                )
            }
        }
    }

    private fun getFileNameFromUri(context: Context, uri: Uri): String? {
        var fileName: String? = null
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) {
                    fileName = cursor.getString(nameIndex)
                }
            }
        }
        return fileName
    }
}

data class UploadResultData(
    val success: Boolean,
    val message: String?
)
