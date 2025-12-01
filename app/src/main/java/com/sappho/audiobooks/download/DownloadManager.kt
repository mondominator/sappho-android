package com.sappho.audiobooks.download

import android.content.Context
import android.util.Log
import com.sappho.audiobooks.data.repository.AuthRepository
import com.sappho.audiobooks.domain.model.Audiobook
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
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
    @ApplicationContext private val context: Context,
    private val authRepository: AuthRepository,
    private val api: com.sappho.audiobooks.data.remote.SapphoApi
) {
    private val TAG = "DownloadManager"

    // Create a dedicated OkHttpClient for downloads with long timeouts
    private val downloadClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.MINUTES)  // Long timeout for large files
        .writeTimeout(10, TimeUnit.MINUTES)
        .build()

    private val _downloadStates = MutableStateFlow<Map<Int, DownloadState>>(emptyMap())
    val downloadStates: StateFlow<Map<Int, DownloadState>> = _downloadStates

    private val _downloadedBooks = MutableStateFlow<List<DownloadedBook>>(emptyList())
    val downloadedBooks: StateFlow<List<DownloadedBook>> = _downloadedBooks

    private val downloadsDir: File
        get() = File(context.filesDir, "audiobooks").also { it.mkdirs() }

    private val metadataFile: File
        get() = File(context.filesDir, "downloads_metadata.json")

    private val pendingProgressFile: File
        get() = File(context.filesDir, "pending_progress.json")

    private val _pendingProgress = MutableStateFlow<Map<Int, PendingProgress>>(emptyMap())
    val pendingProgress: StateFlow<Map<Int, PendingProgress>> = _pendingProgress

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

    fun isDownloaded(audiobookId: Int): Boolean {
        return _downloadedBooks.value.any { it.audiobook.id == audiobookId }
    }

    fun getDownloadedBook(audiobookId: Int): DownloadedBook? {
        return _downloadedBooks.value.find { it.audiobook.id == audiobookId }
    }

    fun getLocalFilePath(audiobookId: Int): String? {
        return getDownloadedBook(audiobookId)?.filePath
    }

    suspend fun downloadAudiobook(audiobook: Audiobook): Boolean {
        val audiobookId = audiobook.id

        // Update state to downloading
        updateDownloadState(audiobookId, DownloadState(
            audiobookId = audiobookId,
            progress = 0f,
            isDownloading = true,
            isCompleted = false
        ))

        return withContext(Dispatchers.IO) {
            try {
                val serverUrl = authRepository.getServerUrlSync() ?: throw Exception("No server URL")
                val token = authRepository.getTokenSync() ?: throw Exception("No auth token")

                val downloadUrl = "$serverUrl/api/audiobooks/$audiobookId/stream"
                Log.d(TAG, "Downloading from: $downloadUrl")

                val request = Request.Builder()
                    .url(downloadUrl)
                    .addHeader("Authorization", "Bearer $token")
                    .build()

                Log.d(TAG, "Starting download request...")
                val response = downloadClient.newCall(request).execute()
                Log.d(TAG, "Response received: ${response.code}")

                if (!response.isSuccessful) {
                    throw Exception("Download failed: ${response.code}")
                }

                val body = response.body ?: throw Exception("Empty response body")
                val contentLength = body.contentLength()

                val file = File(downloadsDir, "audiobook_$audiobookId.m4b")
                val outputStream = FileOutputStream(file)
                val inputStream = body.byteStream()

                val buffer = ByteArray(8192)
                var bytesRead: Int
                var totalBytesRead = 0L

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    totalBytesRead += bytesRead

                    val progress = if (contentLength > 0) {
                        totalBytesRead.toFloat() / contentLength.toFloat()
                    } else {
                        0f
                    }

                    updateDownloadState(audiobookId, DownloadState(
                        audiobookId = audiobookId,
                        progress = progress,
                        isDownloading = true,
                        isCompleted = false
                    ))
                }

                outputStream.close()
                inputStream.close()

                // Fetch chapters for offline use
                val chapters = try {
                    val chaptersResponse = api.getChapters(audiobookId)
                    if (chaptersResponse.isSuccessful) {
                        chaptersResponse.body() ?: emptyList()
                    } else {
                        emptyList()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to fetch chapters for download", e)
                    emptyList()
                }

                // Add to downloaded books
                val downloadedBook = DownloadedBook(
                    audiobook = audiobook,
                    filePath = file.absolutePath,
                    fileSize = file.length(),
                    downloadedAt = System.currentTimeMillis(),
                    chapters = chapters
                )

                _downloadedBooks.value = _downloadedBooks.value + downloadedBook
                saveDownloadedBooks()

                // Update state to completed
                updateDownloadState(audiobookId, DownloadState(
                    audiobookId = audiobookId,
                    progress = 1f,
                    isDownloading = false,
                    isCompleted = true
                ))

                Log.d(TAG, "Download complete: ${file.absolutePath}")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Download failed", e)
                updateDownloadState(audiobookId, DownloadState(
                    audiobookId = audiobookId,
                    progress = 0f,
                    isDownloading = false,
                    isCompleted = false,
                    error = e.message
                ))
                false
            }
        }
    }

    fun deleteDownload(audiobookId: Int): Boolean {
        val downloadedBook = getDownloadedBook(audiobookId) ?: return false

        return try {
            val file = File(downloadedBook.filePath)
            if (file.exists()) {
                file.delete()
            }

            _downloadedBooks.value = _downloadedBooks.value.filter { it.audiobook.id != audiobookId }
            saveDownloadedBooks()

            // Clear download state
            val currentStates = _downloadStates.value.toMutableMap()
            currentStates.remove(audiobookId)
            _downloadStates.value = currentStates

            true
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting download", e)
            false
        }
    }

    private fun updateDownloadState(audiobookId: Int, state: DownloadState) {
        val currentStates = _downloadStates.value.toMutableMap()
        currentStates[audiobookId] = state
        _downloadStates.value = currentStates
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
        _downloadedBooks.value = _downloadedBooks.value + downloadedBook
        saveDownloadedBooks()
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
        try {
            val gson = com.google.gson.Gson()
            val progressList = _pendingProgress.value.values.toList()
            pendingProgressFile.writeText(gson.toJson(progressList))
        } catch (e: Exception) {
            Log.e(TAG, "Error saving pending progress", e)
        }
    }

    fun saveOfflineProgress(audiobookId: Int, position: Int) {
        Log.d(TAG, "Saving offline progress for book $audiobookId: position $position")
        val pending = PendingProgress(
            audiobookId = audiobookId,
            position = position,
            timestamp = System.currentTimeMillis()
        )
        val current = _pendingProgress.value.toMutableMap()
        current[audiobookId] = pending
        _pendingProgress.value = current
        savePendingProgress()

        // Also update the audiobook's progress in the downloaded book metadata
        updateDownloadedBookProgress(audiobookId, position)
    }

    fun getPendingProgressList(): List<PendingProgress> {
        return _pendingProgress.value.values.toList()
    }

    fun clearPendingProgress(audiobookId: Int) {
        Log.d(TAG, "Clearing pending progress for book $audiobookId")
        val current = _pendingProgress.value.toMutableMap()
        current.remove(audiobookId)
        _pendingProgress.value = current
        savePendingProgress()
    }

    fun hasPendingProgress(): Boolean {
        return _pendingProgress.value.isNotEmpty()
    }

    private fun updateDownloadedBookProgress(audiobookId: Int, position: Int) {
        val currentBooks = _downloadedBooks.value.toMutableList()
        val index = currentBooks.indexOfFirst { it.audiobook.id == audiobookId }
        if (index >= 0) {
            val book = currentBooks[index]
            val updatedProgress = book.audiobook.progress?.copy(position = position)
                ?: com.sappho.audiobooks.domain.model.Progress(
                    position = position,
                    completed = 0
                )
            val updatedAudiobook = book.audiobook.copy(progress = updatedProgress)
            currentBooks[index] = book.copy(audiobook = updatedAudiobook)
            _downloadedBooks.value = currentBooks
            saveDownloadedBooks()
        }
    }
}
