package com.sappho.audiobooks.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.sappho.audiobooks.R
import com.sappho.audiobooks.data.remote.SapphoApi
import com.sappho.audiobooks.data.repository.AuthRepository
import com.sappho.audiobooks.domain.model.Audiobook
import com.sappho.audiobooks.download.DownloadManager
import com.sappho.audiobooks.download.DownloadQueue
import com.sappho.audiobooks.download.DownloadResume
import com.sappho.audiobooks.download.DownloadState
import com.sappho.audiobooks.presentation.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@AndroidEntryPoint
class DownloadService : Service() {

    @Inject
    lateinit var authRepository: AuthRepository

    @Inject
    lateinit var downloadManager: DownloadManager

    @Inject
    lateinit var api: SapphoApi

    // The app's client: adds the Authorization header (no token in the URL)
    // and refreshes an expired access token instead of failing with 401.
    @Inject
    lateinit var okHttpClient: OkHttpClient

    private val downloadClient: OkHttpClient by lazy {
        okHttpClient.newBuilder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.MINUTES)
            .writeTimeout(10, TimeUnit.MINUTES)
            .build()
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Queue + the currently transferring download. All access under [lock].
    private val lock = Any()
    private val queue = DownloadQueue()
    private val titles = mutableMapOf<Int, Pair<String, String>>()
    private var worker: Job? = null
    private var activeJob: Job? = null
    // Cancelling the coroutine alone does NOT interrupt the blocking read; the
    // OkHttp call itself must be cancelled so the socket read aborts.
    private var activeCall: okhttp3.Call? = null
    private var lastStartId = 0
    private var swept = false

    companion object {
        private const val TAG = "DownloadService"
        private const val NOTIFICATION_ID = 2
        private const val CHANNEL_ID = "audiobook_download"

        private const val ACTION_START_DOWNLOAD = "com.sappho.audiobooks.START_DOWNLOAD"
        private const val ACTION_CANCEL_DOWNLOAD = "com.sappho.audiobooks.CANCEL_DOWNLOAD"
        private const val EXTRA_AUDIOBOOK_ID = "audiobook_id"
        private const val EXTRA_AUDIOBOOK_TITLE = "audiobook_title"
        private const val EXTRA_AUDIOBOOK_AUTHOR = "audiobook_author"

        // Retries of a dropped connection, each resuming from the bytes on disk.
        private const val MAX_ATTEMPTS = 5
        private const val RETRY_BASE_DELAY_MS = 2_000L

        fun startDownload(context: Context, audiobook: Audiobook) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_START_DOWNLOAD
                putExtra(EXTRA_AUDIOBOOK_ID, audiobook.id)
                putExtra(EXTRA_AUDIOBOOK_TITLE, audiobook.title)
                putExtra(EXTRA_AUDIOBOOK_AUTHOR, audiobook.author ?: "")
            }
            context.startForegroundService(intent)
        }

        /** Cancel one book's download (active or queued); with no id, the active one. */
        fun cancelDownload(context: Context, audiobookId: Int? = null) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_CANCEL_DOWNLOAD
                audiobookId?.let { putExtra(EXTRA_AUDIOBOOK_ID, it) }
            }
            context.startService(intent)
        }

        internal fun partFileFor(finalFile: File) = File(finalFile.parentFile, "${finalFile.name}.part")
        internal fun etagFileFor(finalFile: File) = File(finalFile.parentFile, "${finalFile.name}.part.etag")
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        synchronized(lock) { lastStartId = startId }
        when (intent?.action) {
            ACTION_START_DOWNLOAD -> {
                val audiobookId = intent.getIntExtra(EXTRA_AUDIOBOOK_ID, -1)
                val title = intent.getStringExtra(EXTRA_AUDIOBOOK_TITLE) ?: "Audiobook"
                val author = intent.getStringExtra(EXTRA_AUDIOBOOK_AUTHOR) ?: ""
                // Always satisfy startForegroundService(), even for a bad id.
                startForeground(NOTIFICATION_ID, createNotification(title, author, 0))
                if (audiobookId != -1) enqueue(audiobookId, title, author)
                stopIfIdle()
            }
            ACTION_CANCEL_DOWNLOAD -> {
                val id = intent.getIntExtra(EXTRA_AUDIOBOOK_ID, -1).takeIf { it != -1 }
                cancel(id ?: synchronized(lock) { queue.active })
                stopIfIdle()
            }
            else -> stopIfIdle()
        }
        return START_NOT_STICKY
    }

    private fun enqueue(audiobookId: Int, title: String, author: String) {
        synchronized(lock) {
            titles[audiobookId] = title to author
            val result = queue.enqueue(audiobookId)
            if (result != DownloadQueue.EnqueueResult.ALREADY_PRESENT) {
                downloadManager.updateDownloadStateExternal(audiobookId, DownloadState(
                    audiobookId = audiobookId,
                    progress = 0f,
                    isDownloading = true,
                    isCompleted = false,
                    isQueued = result == DownloadQueue.EnqueueResult.QUEUED
                ))
            }
            pumpLocked()
        }
    }

    /** Start the single worker if it isn't running. Caller holds [lock]. */
    private fun pumpLocked() {
        if (worker?.isActive == true) return
        worker = serviceScope.launch {
            if (!swept) {
                // First run in this service instance, before any transfer starts:
                // leftover .part or untracked files are from a crash or a failed
                // metadata save. (Doing this in onCreate raced the first download.)
                swept = true
                downloadManager.sweepOrphanedFiles()
            }
            while (true) {
                val id = synchronized(lock) { queue.takeNext() } ?: break
                val (title, author) = synchronized(lock) { titles[id] } ?: ("Audiobook" to "")
                val job = serviceScope.launch { runDownload(id, title, author) }
                synchronized(lock) { activeJob = job }
                job.join()
                synchronized(lock) {
                    activeJob = null
                    queue.finish(id)
                    titles.remove(id)
                }
            }
            withContext(Dispatchers.Main) { stopIfIdle(calledFromWorker = true) }
        }
    }

    /**
     * Stop only when nothing is active or waiting. stopSelf(startId) is a no-op
     * if a newer start command arrived meanwhile, so a request racing the stop
     * is never lost.
     */
    private fun stopIfIdle(calledFromWorker: Boolean = false) {
        val (idle, startId) = synchronized(lock) {
            val workerDone = calledFromWorker || worker?.isActive != true
            (queue.isIdle && workerDone) to lastStartId
        }
        if (idle) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf(startId)
        }
    }

    private fun cancel(audiobookId: Int?) {
        audiobookId ?: return
        val result = synchronized(lock) { queue.cancel(audiobookId) }
        when (result) {
            DownloadQueue.CancelResult.CANCEL_ACTIVE -> {
                synchronized(lock) {
                    activeCall?.cancel()
                    activeJob?.cancel()
                }
            }
            DownloadQueue.CancelResult.REMOVED_FROM_QUEUE -> {
                synchronized(lock) { titles.remove(audiobookId) }
                downloadManager.clearDownloadState(audiobookId)
            }
            DownloadQueue.CancelResult.NOT_FOUND -> downloadManager.clearDownloadState(audiobookId)
        }
    }

    // API 35+: a dataSync foreground service gets ~6 h per day. When the system
    // says time is up we must stop, or the app is killed.
    override fun onTimeout(startId: Int, fgsType: Int) {
        Log.w(TAG, "Foreground time limit reached; stopping downloads")
        val waiting = synchronized(lock) { queue.clearWaiting() }
        waiting.forEach { failState(it, "Download paused by the system. Try again later.") }
        synchronized(lock) {
            activeCall?.cancel()
            activeJob?.cancel()
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Audiobook Downloads",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows download progress for audiobooks"
            setShowBadge(false)
        }
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    private fun createNotification(title: String, author: String, progress: Int): Notification {
        val contentIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this, 0, contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val cancelIntent = Intent(this, DownloadService::class.java).apply {
            action = ACTION_CANCEL_DOWNLOAD
        }
        val cancelPendingIntent = PendingIntent.getService(
            this, 1, cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val queued = synchronized(lock) { queue.waitingIds().size }
        val status = when {
            progress >= 100 -> "Completing..."
            queued > 0 -> "$progress% \u00b7 $queued more queued"
            else -> "$progress%"
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Downloading: $title")
            .setContentText(status)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, progress, progress == 0)
            .setOngoing(true)
            .setContentIntent(contentPendingIntent)
            .addAction(android.R.drawable.ic_delete, "Cancel", cancelPendingIntent)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun createCompletedNotification(title: String, success: Boolean, errorMessage: String? = null): Notification {
        val contentIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this, 0, contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(if (success) "Download Complete" else "Download Failed")
            .setContentText(if (success) title else errorMessage ?: "Failed to download $title")
            .setSmallIcon(if (success) android.R.drawable.stat_sys_download_done else android.R.drawable.stat_notify_error)
            .setContentIntent(contentPendingIntent)
            .setAutoCancel(true)
            .build()
    }

    /** One book, start to finish. Never throws except for cancellation. */
    private suspend fun runDownload(audiobookId: Int, title: String, author: String) {
        updateNotification(title, author, 0)
        downloadManager.updateDownloadStateExternal(audiobookId, DownloadState(
            audiobookId = audiobookId, progress = 0f, isDownloading = true, isCompleted = false
        ))
        var finalFile: File? = null
        try {
            val serverUrl = authRepository.getServerUrlSync() ?: throw IOException("Not signed in")
            val downloadsDir = downloadManager.downloadsDirForCurrentAccount()
                ?: throw IOException("Not signed in")
            if (!downloadsDir.exists() && !downloadsDir.mkdirs()) {
                throw IOException("Failed to create download directory")
            }
            val target = File(downloadsDir, "audiobook_$audiobookId.m4b")
            finalFile = target

            // Book details first: without them the file can't be registered,
            // so fail before spending the bandwidth.
            val audiobook = api.getAudiobook(audiobookId).body()
                ?: throw IOException("Couldn't load book details")

            val (etag, totalBytes) = transferWithResume(serverUrl, audiobookId, target, title, author)

            val chapters = try {
                val chaptersResponse = api.getChapters(audiobookId)
                if (chaptersResponse.isSuccessful) chaptersResponse.body() ?: emptyList() else emptyList()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch chapters", e)
                emptyList()
            }

            downloadManager.saveDownloadedBook(
                audiobook, target.absolutePath, target.length(), chapters,
                etag = etag, serverFileSize = totalBytes
            )
            downloadManager.updateDownloadStateExternal(audiobookId, DownloadState(
                audiobookId = audiobookId, progress = 1f, isDownloading = false, isCompleted = true
            ))
            Log.d(TAG, "Download complete: book $audiobookId")
            notifyDone(title, true)
        } catch (e: CancellationException) {
            // User cancel or service teardown: remove partial data quietly.
            Log.d(TAG, "Download cancelled: book $audiobookId")
            finalFile?.let { deletePartial(it) }
            downloadManager.clearDownloadState(audiobookId)
            throw e
        } catch (e: Exception) {
            val cancelled = synchronized(lock) { activeCall?.isCanceled() == true }
            finalFile?.let { deletePartial(it) }
            if (cancelled) {
                Log.d(TAG, "Download cancelled (call aborted): book $audiobookId")
                downloadManager.clearDownloadState(audiobookId)
            } else {
                Log.e(TAG, "Download failed: book $audiobookId", e)
                val errorMessage = when (e) {
                    is IOException -> e.message ?: "Download failed"
                    is SecurityException -> "Permission denied"
                    else -> "Download failed: ${e.message}"
                }
                failState(audiobookId, errorMessage)
                notifyDone(title, false, errorMessage)
            }
        } finally {
            synchronized(lock) { activeCall = null }
        }
    }

    /**
     * Download into `<file>.part`, resuming with a Range request after a
     * dropped connection, then rename onto [target]. Returns the server's ETag
     * and total size for later change detection.
     */
    private suspend fun transferWithResume(
        serverUrl: String,
        audiobookId: Int,
        target: File,
        title: String,
        author: String
    ): Pair<String?, Long?> {
        val part = partFileFor(target)
        val etagFile = etagFileFor(target)
        var attempt = 0
        while (true) {
            attempt++
            currentCoroutineContext().ensureActive()
            try {
                val result = transferOnce(serverUrl, audiobookId, part, etagFile, title, author)
                if (part.exists() && !part.renameTo(target)) {
                    throw IOException("Couldn't move downloaded file into place")
                }
                etagFile.delete()
                return result
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                val cancelled = synchronized(lock) { activeCall?.isCanceled() == true }
                if (cancelled || attempt >= MAX_ATTEMPTS || e is NonRetryableDownloadException) throw e
                val wait = RETRY_BASE_DELAY_MS * (1L shl (attempt - 1))
                Log.w(TAG, "Transfer interrupted (attempt $attempt), resuming in ${wait}ms", e)
                delay(wait)
            }
        }
    }

    private class NonRetryableDownloadException(message: String) : IOException(message)

    private suspend fun transferOnce(
        serverUrl: String,
        audiobookId: Int,
        part: File,
        etagFile: File,
        title: String,
        author: String
    ): Pair<String?, Long?> {
        val existing = if (part.exists()) part.length() else 0L
        val savedEtag = if (etagFile.exists()) etagFile.readText().trim().ifEmpty { null } else null

        val requestBuilder = Request.Builder().url("$serverUrl/api/audiobooks/$audiobookId/stream")
        DownloadResume.requestHeaders(existing, savedEtag).forEach { (k, v) -> requestBuilder.header(k, v) }

        val call = downloadClient.newCall(requestBuilder.build())
        synchronized(lock) { activeCall = call }
        call.execute().use { response ->
            val body = response.body
            val outcome = DownloadResume.interpret(
                response.code, response.header("Content-Range"), body?.contentLength() ?: -1, existing
            )
            val etag = response.header("ETag") ?: savedEtag
            val (append, total) = when (outcome) {
                is DownloadResume.Outcome.Append -> true to outcome.totalBytes
                is DownloadResume.Outcome.Restart -> false to outcome.totalBytes
                is DownloadResume.Outcome.AlreadyComplete -> return etag to outcome.totalBytes
                is DownloadResume.Outcome.Fail -> {
                    if (response.code == 401 || response.code == 403 || response.code == 404) {
                        throw NonRetryableDownloadException("Download failed: ${response.code}")
                    }
                    throw IOException("Download failed: ${response.code}")
                }
            }
            if (body == null) throw IOException("Empty response body")
            etag?.let { etagFile.writeText(it) }

            val totalBytes = total ?: body.contentLength().takeIf { it > 0 }
            val remaining = (totalBytes ?: 0L) - (if (append) existing else 0L)
            val freeSpace = part.parentFile?.freeSpace ?: Long.MAX_VALUE
            if (remaining > 0 && freeSpace < remaining * 1.1) {
                throw NonRetryableDownloadException(
                    "Insufficient storage space. Need ${remaining / 1024 / 1024}MB, but only ${freeSpace / 1024 / 1024}MB available"
                )
            }

            var written = if (append) existing else 0L
            var lastProgress = -1
            FileOutputStream(part, append).use { out ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = input.read(buffer)
                        if (n == -1) break
                        out.write(buffer, 0, n)
                        written += n
                        val progress = if (totalBytes != null && totalBytes > 0) {
                            ((written.toDouble() / totalBytes) * 100).toInt().coerceIn(0, 100)
                        } else 0
                        if (progress != lastProgress) {
                            lastProgress = progress
                            updateNotification(title, author, progress)
                            downloadManager.updateDownloadStateExternal(audiobookId, DownloadState(
                                audiobookId = audiobookId,
                                progress = progress / 100f,
                                isDownloading = true,
                                isCompleted = false
                            ))
                        }
                    }
                }
            }
            if (totalBytes != null && written != totalBytes) {
                // Connection closed early without an exception: resume.
                throw IOException("Incomplete download ($written of $totalBytes bytes)")
            }
            return etag to (totalBytes ?: written)
        }
    }

    private fun deletePartial(finalFile: File) {
        try {
            partFileFor(finalFile).delete()
            etagFileFor(finalFile).delete()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clean up partial download", e)
        }
    }

    private fun failState(audiobookId: Int, message: String) {
        downloadManager.updateDownloadStateExternal(audiobookId, DownloadState(
            audiobookId = audiobookId,
            progress = 0f,
            isDownloading = false,
            isCompleted = false,
            error = message
        ))
    }

    private suspend fun notifyDone(title: String, success: Boolean, errorMessage: String? = null) {
        withContext(Dispatchers.Main) {
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.notify(NOTIFICATION_ID + 1, createCompletedNotification(title, success, errorMessage))
        }
    }

    private fun updateNotification(title: String, author: String, progress: Int) {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, createNotification(title, author, progress))
    }

    override fun onDestroy() {
        synchronized(lock) { activeCall?.cancel() }
        // Cancel the whole scope, not just the active job — otherwise coroutines
        // launched here outlive the service.
        serviceScope.cancel()
        super.onDestroy()
    }
}
