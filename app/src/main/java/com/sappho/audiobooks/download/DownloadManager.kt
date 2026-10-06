package com.sappho.audiobooks.download

import android.content.Context
import android.util.Log
import com.sappho.audiobooks.data.repository.AccountKey
import com.sappho.audiobooks.data.repository.AuthRepository
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
    val error: String? = null,
    /** Waiting behind another download in the queue. */
    val isQueued: Boolean = false
)

data class DownloadedBook(
    val audiobook: Audiobook,
    val filePath: String,
    val fileSize: Long,
    val downloadedAt: Long,
    val chapters: List<com.sappho.audiobooks.domain.model.Chapter> = emptyList(),
    /** Account ("server#userId") that downloaded this file. Null = legacy entry. */
    val accountKey: String? = null,
    /** ETag the server sent for /stream when this file was downloaded. */
    val etag: String? = null,
    /** Total size in bytes the server reported for /stream at download time. */
    val serverFileSize: Long? = null
)

data class PendingProgress(
    val audiobookId: Int,
    val position: Int,
    val timestamp: Long,
    /** Account the position was recorded under. Null = legacy entry (pre-0.9.88). */
    val accountKey: String? = null
)

@Singleton
class DownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authRepository: AuthRepository
) {
    private val TAG = "DownloadManager"

    private val _downloadStates = MutableStateFlow<Map<Int, DownloadState>>(emptyMap())
    val downloadStates: StateFlow<Map<Int, DownloadState>> = _downloadStates

    // Every download on disk, across all accounts.
    private val allDownloads = MutableStateFlow<List<DownloadedBook>>(emptyList())

    // Downloads belonging to the signed-in account (what the UI sees).
    private val _downloadedBooks = MutableStateFlow<List<DownloadedBook>>(emptyList())
    val downloadedBooks: StateFlow<List<DownloadedBook>> = _downloadedBooks

    // Book ids whose local copy no longer matches the server file (see
    // DownloadFreshness). Playback streams instead of using a stale copy.
    private val _staleDownloads = MutableStateFlow<Set<Int>>(emptySet())
    val staleDownloads: StateFlow<Set<Int>> = _staleDownloads

    private val metadataFile: File
        get() = File(context.filesDir, "downloads_metadata.json")

    private val pendingProgressFile: File
        get() = File(context.filesDir, "pending_progress.json")

    // Every queued position, across accounts.
    private val allPending = MutableStateFlow<List<PendingProgress>>(emptyList())

    // Pending progress for the signed-in account, keyed by book id.
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
        onAccountChanged()
        authRepository.addAccountChangeListener { onAccountChanged() }
    }

    private fun currentAccount(): String? = try {
        authRepository.getAccountKeySync()
    } catch (e: Exception) {
        Log.w(TAG, "Could not read account key", e)
        null
    }

    /**
     * Re-scope visible downloads and pending progress to the signed-in account.
     * Call after login and logout. Legacy entries (written before entries were
     * tagged) are adopted by the first account that signs in, which on a
     * single-user install is the user who created them.
     */
    fun onAccountChanged() {
        val account = currentAccount()
        if (account != null) {
            var adoptedDownloads = false
            allDownloads.update { books ->
                books.map { if (it.accountKey == null) { adoptedDownloads = true; it.copy(accountKey = account) } else it }
            }
            if (adoptedDownloads) saveDownloadedBooks()

            var adoptedPending = false
            allPending.update { list ->
                list.map { if (it.accountKey == null) { adoptedPending = true; it.copy(accountKey = account) } else it }
            }
            if (adoptedPending) savePendingProgress()
        }
        publishVisible()
    }

    private fun publishVisible() {
        val account = currentAccount()
        _downloadedBooks.value = if (account == null) emptyList()
        else allDownloads.value.filter { it.accountKey == account }
        _pendingProgress.value = if (account == null) emptyMap()
        else allPending.value.filter { it.accountKey == account }.associateBy { it.audiobookId }
    }

    private fun loadDownloadedBooks() {
        try {
            if (metadataFile.exists()) {
                val json = metadataFile.readText()
                val books = parseDownloadedBooks(json)
                // Filter out books whose files no longer exist
                val validBooks = books.filter { File(it.filePath).exists() }
                allDownloads.value = validBooks
                if (validBooks.size != books.size) {
                    saveDownloadedBooks()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading downloaded books", e)
        }
    }

    private fun parseDownloadedBooks(json: String): List<DownloadedBook> {
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
                    // Gson bypasses Kotlin defaults, so a missing list arrives as null
                    chapters = jsonBook.chapters ?: emptyList(),
                    accountKey = jsonBook.accountKey,
                    etag = jsonBook.etag,
                    serverFileSize = jsonBook.serverFileSize
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
        val chapters: List<com.sappho.audiobooks.domain.model.Chapter>? = emptyList(),
        val accountKey: String? = null,
        val etag: String? = null,
        val serverFileSize: Long? = null
    )

    private fun saveDownloadedBooks() {
        // The lock serializes file writes; the snapshot is read INSIDE it so a
        // later write always persists state at least as fresh as any earlier
        // one. A mutation landing mid-write is fine — every mutation calls
        // save() itself, so the file converges to the latest state.
        synchronized(metadataFileLock) {
            try {
                val gson = com.google.gson.Gson()
                val jsonBooks = allDownloads.value.map { book ->
                    DownloadedBookJson(
                        audiobook = book.audiobook,
                        filePath = book.filePath,
                        fileSize = book.fileSize,
                        downloadedAt = book.downloadedAt,
                        chapters = book.chapters,
                        accountKey = book.accountKey,
                        etag = book.etag,
                        serverFileSize = book.serverFileSize
                    )
                }
                metadataFile.writeText(gson.toJson(jsonBooks))
            } catch (e: Exception) {
                Log.e(TAG, "Error saving downloaded books", e)
            }
        }
    }

    /**
     * Directory for the signed-in account's downloads. Book ids are only unique
     * per server, so each account gets its own folder.
     */
    fun downloadsDirForCurrentAccount(): File? {
        val account = currentAccount() ?: return null
        return File(File(context.filesDir, DOWNLOADS_ROOT), AccountKey.directoryName(account))
    }

    fun isDownloaded(audiobookId: Int): Boolean {
        return _downloadedBooks.value.any { it.audiobook.id == audiobookId }
    }

    fun getDownloadedBook(audiobookId: Int): DownloadedBook? {
        return _downloadedBooks.value.find { it.audiobook.id == audiobookId }
    }

    /**
     * Local file to play for this book, or null to stream. A download known to
     * be stale (the server file changed) is skipped so the user hears the
     * current file while a fresh copy downloads.
     */
    fun getLocalFilePath(audiobookId: Int): String? {
        if (audiobookId in _staleDownloads.value) return null
        return getDownloadedBook(audiobookId)?.filePath
    }

    fun markStale(audiobookId: Int) {
        _staleDownloads.update { it + audiobookId }
    }

    fun deleteDownload(audiobookId: Int): Boolean {
        val downloadedBook = getDownloadedBook(audiobookId) ?: return false

        return try {
            val file = File(downloadedBook.filePath)
            if (file.exists()) {
                file.delete()
            }

            allDownloads.update { books ->
                books.filterNot { it.audiobook.id == audiobookId && it.accountKey == downloadedBook.accountKey }
            }
            saveDownloadedBooks()
            publishVisible()
            _staleDownloads.update { it - audiobookId }

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

    /** Drop any in-memory state for this book (used when a queued download is cancelled). */
    fun clearDownloadState(audiobookId: Int) {
        _downloadStates.update { it - audiobookId }
    }

    // Called by DownloadService to save a completed download. Replaces any
    // existing entry for the same book and account (re-download).
    fun saveDownloadedBook(
        audiobook: Audiobook,
        filePath: String,
        fileSize: Long,
        chapters: List<com.sappho.audiobooks.domain.model.Chapter>,
        etag: String? = null,
        serverFileSize: Long? = null
    ) {
        val account = currentAccount()
        val downloadedBook = DownloadedBook(
            audiobook = audiobook,
            filePath = filePath,
            fileSize = fileSize,
            downloadedAt = System.currentTimeMillis(),
            chapters = chapters,
            accountKey = account,
            etag = etag,
            serverFileSize = serverFileSize
        )
        val replaced = mutableListOf<DownloadedBook>()
        allDownloads.update { books ->
            val (same, others) = books.partition { it.audiobook.id == audiobook.id && it.accountKey == account }
            replaced.clear()
            replaced.addAll(same)
            others + downloadedBook
        }
        // A re-download may land at a new path (e.g. a legacy flat file); remove
        // the superseded file so it doesn't linger untracked.
        replaced.filter { it.filePath != filePath }.forEach { old ->
            try { File(old.filePath).delete() } catch (e: Exception) { Log.w(TAG, "Could not remove old file", e) }
        }
        saveDownloadedBooks()
        _staleDownloads.update { it - audiobook.id }
        publishVisible()
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

    /**
     * Delete files in the downloads tree that no metadata entry points at:
     * leftovers from a crash mid-download, or a transfer whose metadata save
     * failed. Partial `.part` files of an active download are kept (they are
     * the resume point).
     */
    fun sweepOrphanedFiles(activePartFiles: Set<String> = emptySet()) {
        val root = File(context.filesDir, DOWNLOADS_ROOT)
        if (!root.exists()) return
        val tracked = allDownloads.value.map { File(it.filePath).absolutePath }.toSet()
        root.walkTopDown().filter { it.isFile }.forEach { file ->
            val path = file.absolutePath
            val keep = path in tracked || path in activePartFiles
            if (!keep) {
                Log.i(TAG, "Removing untracked download file ${file.name}")
                file.delete()
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
                allPending.value = progressList
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading pending progress", e)
        }
    }

    private fun savePendingProgress() {
        synchronized(pendingProgressFileLock) {
            try {
                val gson = com.google.gson.Gson()
                pendingProgressFile.writeText(gson.toJson(allPending.value))
            } catch (e: Exception) {
                Log.e(TAG, "Error saving pending progress", e)
            }
        }
    }

    /**
     * Queue a position for later sync, tagged with the signed-in account. With
     * no account (logged out) there is nobody to sync it to, so it is dropped.
     */
    fun saveOfflineProgress(audiobookId: Int, position: Int) {
        val account = currentAccount()
        if (account == null) {
            Log.w(TAG, "Not queueing progress for book $audiobookId: no signed-in account")
            return
        }
        Log.d(TAG, "Saving offline progress for book $audiobookId: position $position")
        val pending = PendingProgress(
            audiobookId = audiobookId,
            position = position,
            timestamp = System.currentTimeMillis(),
            accountKey = account
        )
        allPending.update { list ->
            list.filterNot { it.audiobookId == audiobookId && it.accountKey == account } + pending
        }
        savePendingProgress()
        publishVisible()

        // Also update the audiobook's progress in the downloaded book metadata
        updateDownloadedBookProgress(audiobookId, position)

        // Trigger background sync if we have network
        triggerSyncIfOnline()
    }

    /** Queued positions for the signed-in account only. */
    fun getPendingProgressList(): List<PendingProgress> {
        return _pendingProgress.value.values.toList()
    }

    /**
     * Remove a queued position after the server confirmed it (or deliberately
     * ignored it as stale). Only the signed-in account's entry is touched.
     */
    fun clearPendingProgress(audiobookId: Int) {
        val account = currentAccount() ?: return
        Log.d(TAG, "Clearing pending progress for book $audiobookId")
        allPending.update { list -> list.filterNot { it.audiobookId == audiobookId && it.accountKey == account } }
        savePendingProgress()
        publishVisible()
    }

    /**
     * Remove only the given entry, and only if it hasn't been replaced by a
     * newer position since it was read. Prevents a slow sync from deleting a
     * position recorded while the request was in flight.
     */
    fun clearPendingProgressIfUnchanged(entry: PendingProgress) {
        var removed = false
        allPending.update { list ->
            list.filterNot {
                val match = it == entry
                if (match) removed = true
                match
            }
        }
        if (removed) {
            savePendingProgress()
            publishVisible()
        }
    }

    /**
     * Logout: drop the signed-in account's queue. Positions that never synced
     * can't be sent once the session is revoked, and must not leak into the
     * next account that signs in.
     */
    fun clearPendingProgressForAccount(accountKey: String?) {
        allPending.update { list -> list.filterNot { it.accountKey == accountKey || it.accountKey == null } }
        savePendingProgress()
        publishVisible()
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
        val account = currentAccount()
        if (allDownloads.value.none { it.audiobook.id == audiobookId && it.accountKey == account }) return
        allDownloads.update { books ->
            books.map { book ->
                if (book.audiobook.id == audiobookId && book.accountKey == account) {
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
        publishVisible()
    }

    companion object {
        const val DOWNLOADS_ROOT = "audiobooks"
    }
}
