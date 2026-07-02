package com.sappho.audiobooks.download

import android.content.Context
import android.util.Log
import com.sappho.audiobooks.domain.model.Audiobook
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class DownloadState(
    val audiobookId: Int,
    val progress: Float, // 0.0 to 1.0
    val isDownloading: Boolean,
    val isCompleted: Boolean,
    val error: String? = null
)

data class DownloadedBook(
    val audiobook: Audiobook,
    val filePath: String,
    val fileSize: Long,
    val downloadedAt: Long,
    val chapters: List<com.sappho.audiobooks.domain.model.Chapter> = emptyList()
)

data class PendingProgress(
    val audiobookId: Int,
    val position: Int,
    val timestamp: Long
)

@Singleton
class DownloadManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val TAG = "DownloadManager"

    private val _downloadStates = MutableStateFlow<Map<Int, DownloadState>>(emptyMap())
    val downloadStates: StateFlow<Map<Int, DownloadState>> = _downloadStates

    private val _downloadedBooks = MutableStateFlow<List<DownloadedBook>>(emptyList())
    val downloadedBooks: StateFlow<List<DownloadedBook>> = _downloadedBooks

    private val metadataFile: File
        get() = File(context.filesDir, "downloads_metadata.json")

    private val pendingProgressFile: File
        get() = File(context.filesDir, "pending_progress.json")

    private val _pendingProgress = MutableStateFlow<Map<Int, PendingProgress>>(emptyMap())
    val pendingProgress: StateFlow<Map<Int, PendingProgress>> = _pendingProgress

    // Serialize JSON metadata file writes: DownloadService and this class can
    // both trigger saves concurrently, and unsynchronized writes could leave
    // the file with stale (or interleaved) content.
    private val metadataFileLock = Any()
    private val pendingProgressFileLock = Any()

    init {
        loadDownloadedBooks()
        loadPendingProgress()
    }

    private fun loadDownloadedBooks() {
        try {
            if (metadataFile.exists()) {
                val json = metadataFile.readText()
                val books = parseDownloadedBooks(json)
                // Filter out books whose files no longer exist
                val validBooks = books.filter { File(it.filePath).exists() }
                _downloadedBooks.value = validBooks
                if (validBooks.size != books.size) {
                    saveDownloadedBooks()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading downloaded books", e)
        }
    }

    private fun parseDownloadedBooks(json: String): List<DownloadedBook> {
        // Simple JSON parsing - in production would use Gson/Moshi
        val books = mutableListOf<DownloadedBook>()
        try {
            val gson = com.google.gson.Gson()
            val type = object : com.google.gson.reflect.TypeToken<List<DownloadedBookJson>>() {}.type
            val jsonBooks: List<DownloadedBookJson> = gson.fromJson(json, type)
            jsonBooks.forEach { jsonBook ->
                books.add(DownloadedBook(
                    audiobook = jsonBook.audiobook,
                    filePath = jsonBook.filePath,
                    fileSize = jsonBook.fileSize,
                    downloadedAt = jsonBook.downloadedAt,
                    chapters = jsonBook.chapters
                ))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing downloaded books", e)
        }
        return books
    }

    private data class DownloadedBookJson(
        val audiobook: Audiobook,
        val filePath: String,
        val fileSize: Long,
        val downloadedAt: Long,
        val chapters: List<com.sappho.audiobooks.domain.model.Chapter> = emptyList()
    )

    private fun saveDownloadedBooks() {
        // The lock serializes file writes; the snapshot is read INSIDE it so a
        // later write always persists state at least as fresh as any earlier
        // one. A mutation landing mid-write is fine — every mutation calls
        // save() itself, so the file converges to the latest state.
        synchronized(metadataFileLock) {
            try {
                val gson = com.google.gson.Gson()
                val jsonBooks = _downloadedBooks.value.map { book ->
                    DownloadedBookJson(
                        audiobook = book.audiobook,
                        filePath = book.filePath,
                        fileSize = book.fileSize,
                        downloadedAt = book.downloadedAt,
                        chapters = book.chapters
                    )
                }
                metadataFile.writeText(gson.toJson(jsonBooks))
            } catch (e: Exception) {
                Log.e(TAG, "Error saving downloaded books", e)
            }
        }
    }

    fun isDownloaded(audiobookId: Int): Boolean {
        return _downloadedBooks.value.any { it.audiobook.id == audiobookId }
    }

    fun getDownloadedBook(audiobookId: Int): DownloadedBook? {
        return _downloadedBooks.value.find { it.audiobook.id == audiobookId }
    }

    fun getLocalFilePath(audiobookId: Int): String? {
        return getDownloadedBook(audiobookId)?.filePath
    }

    fun deleteDownload(audiobookId: Int): Boolean {
        val downloadedBook = getDownloadedBook(audiobookId) ?: return false

        return try {
            val file = File(downloadedBook.filePath)
            if (file.exists()) {
                file.delete()
            }

            _downloadedBooks.update { books -> books.filter { it.audiobook.id != audiobookId } }
            saveDownloadedBooks()

            // Clear download state
            _downloadStates.update { it - audiobookId }

            true
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting download", e)
            false
        }
    }

    private fun updateDownloadState(audiobookId: Int, state: DownloadState) {
        // update {} makes the read-modify-write atomic: concurrent downloads
        // (or DownloadService callbacks) would otherwise lose entries when two
        // threads snapshot the same map.
        _downloadStates.update { it + (audiobookId to state) }
    }

    // Called by DownloadService to update state
    fun updateDownloadStateExternal(audiobookId: Int, state: DownloadState) {
        updateDownloadState(audiobookId, state)
    }

    // Called by DownloadService to save a completed download
    fun saveDownloadedBook(
        audiobook: Audiobook,
        filePath: String,
        fileSize: Long,
        chapters: List<com.sappho.audiobooks.domain.model.Chapter>
    ) {
        val downloadedBook = DownloadedBook(
            audiobook = audiobook,
            filePath = filePath,
            fileSize = fileSize,
            downloadedAt = System.currentTimeMillis(),
            chapters = chapters
        )
        _downloadedBooks.update { it + downloadedBook }
        saveDownloadedBooks()
    }

    fun clearDownloadError(audiobookId: Int) {
        _downloadStates.update { states ->
            val currentState = states[audiobookId]
            if (currentState?.error != null) {
                // Clear the error but keep other state if still relevant
                states + (audiobookId to currentState.copy(error = null))
            } else {
                states
            }
        }
    }

    fun clearAllDownloadErrors() {
        _downloadStates.update { states ->
            if (states.values.any { !it.error.isNullOrBlank() }) {
                states.mapValues { (_, state) ->
                    if (!state.error.isNullOrBlank()) state.copy(error = null) else state
                }
            } else {
                states
            }
        }
    }

    // Pending progress management for offline sync
    private fun loadPendingProgress() {
        try {
            if (pendingProgressFile.exists()) {
                val json = pendingProgressFile.readText()
                val gson = com.google.gson.Gson()
                val type = object : com.google.gson.reflect.TypeToken<List<PendingProgress>>() {}.type
                val progressList: List<PendingProgress> = gson.fromJson(json, type) ?: emptyList()
                _pendingProgress.value = progressList.associateBy { it.audiobookId }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading pending progress", e)
        }
    }

    private fun savePendingProgress() {
        synchronized(pendingProgressFileLock) {
            try {
                val gson = com.google.gson.Gson()
                val progressList = _pendingProgress.value.values.toList()
                pendingProgressFile.writeText(gson.toJson(progressList))
            } catch (e: Exception) {
                Log.e(TAG, "Error saving pending progress", e)
            }
        }
    }

    fun saveOfflineProgress(audiobookId: Int, position: Int) {
        Log.d(TAG, "Saving offline progress for book $audiobookId: position $position")
        val pending = PendingProgress(
            audiobookId = audiobookId,
            position = position,
            timestamp = System.currentTimeMillis()
        )
        _pendingProgress.update { it + (audiobookId to pending) }
        savePendingProgress()

        // Also update the audiobook's progress in the downloaded book metadata
        updateDownloadedBookProgress(audiobookId, position)
        
        // Trigger background sync if we have network
        triggerSyncIfOnline()
    }

    fun getPendingProgressList(): List<PendingProgress> {
        return _pendingProgress.value.values.toList()
    }

    fun clearPendingProgress(audiobookId: Int) {
        Log.d(TAG, "Clearing pending progress for book $audiobookId")
        _pendingProgress.update { it - audiobookId }
        savePendingProgress()
    }

    fun clearAllPendingProgress() {
        if (_pendingProgress.value.isEmpty()) return
        Log.d(TAG, "Clearing all pending progress (${_pendingProgress.value.size} items)")
        _pendingProgress.value = emptyMap()
        savePendingProgress()
    }

    fun hasPendingProgress(): Boolean {
        return _pendingProgress.value.isNotEmpty()
    }
    
    fun getPendingProgressCount(): Int {
        return _pendingProgress.value.size
    }
    
    private fun triggerSyncIfOnline() {
        Log.d(TAG, "Triggering background sync - ${getPendingProgressCount()} items pending")
        try {
            com.sappho.audiobooks.sync.ProgressSyncWorker.enqueue(context)
        } catch (e: Exception) {
            Log.w(TAG, "Could not enqueue sync worker", e)
        }
    }

    private fun updateDownloadedBookProgress(audiobookId: Int, position: Int) {
        if (_downloadedBooks.value.none { it.audiobook.id == audiobookId }) return
        _downloadedBooks.update { books ->
            books.map { book ->
                if (book.audiobook.id == audiobookId) {
                    val updatedProgress = book.audiobook.progress?.copy(position = position)
                        ?: com.sappho.audiobooks.domain.model.Progress(
                            position = position,
                            completed = 0
                        )
                    book.copy(audiobook = book.audiobook.copy(progress = updatedProgress))
                } else {
                    book
                }
            }
        }
        saveDownloadedBooks()
    }
}
