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
import com.sappho.audiobooks.download.DownloadState
import com.sappho.audiobooks.presentation.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
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

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var downloadJob: Job? = null
    private var currentDownloadId: Int? = null

    private val downloadClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.MINUTES)
        .writeTimeout(10, TimeUnit.MINUTES)
        .build()

    companion object {
        private const val TAG = "DownloadService"
        private const val NOTIFICATION_ID = 2
        private const val CHANNEL_ID = "audiobook_download"

        private const val ACTION_START_DOWNLOAD = "com.sappho.audiobooks.START_DOWNLOAD"
        private const val ACTION_CANCEL_DOWNLOAD = "com.sappho.audiobooks.CANCEL_DOWNLOAD"
        private const val EXTRA_AUDIOBOOK_ID = "audiobook_id"
        private const val EXTRA_AUDIOBOOK_TITLE = "audiobook_title"
        private const val EXTRA_AUDIOBOOK_AUTHOR = "audiobook_author"

        fun startDownload(context: Context, audiobook: Audiobook) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_START_DOWNLOAD
                putExtra(EXTRA_AUDIOBOOK_ID, audiobook.id)
                putExtra(EXTRA_AUDIOBOOK_TITLE, audiobook.title)
                putExtra(EXTRA_AUDIOBOOK_AUTHOR, audiobook.author ?: "")
            }
            context.startForegroundService(intent)
        }

        fun cancelDownload(context: Context) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_CANCEL_DOWNLOAD
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_DOWNLOAD -> {
                val audiobookId = intent.getIntExtra(EXTRA_AUDIOBOOK_ID, -1)
                val title = intent.getStringExtra(EXTRA_AUDIOBOOK_TITLE) ?: "Audiobook"
                val author = intent.getStringExtra(EXTRA_AUDIOBOOK_AUTHOR) ?: ""

                if (audiobookId != -1) {
                    startForeground(NOTIFICATION_ID, createNotification(title, author, 0))
                    startDownload(audiobookId, title, author)
                }
            }
            ACTION_CANCEL_DOWNLOAD -> {
                cancelCurrentDownload()
            }
        }
        return START_NOT_STICKY
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

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Downloading: $title")
            .setContentText(if (progress < 100) "$progress%" else "Completing...")
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

    private fun startDownload(audiobookId: Int, title: String, author: String) {
        currentDownloadId = audiobookId

        // Update DownloadManager state
        downloadManager.updateDownloadStateExternal(audiobookId, DownloadState(
            audiobookId = audiobookId,
            progress = 0f,
            isDownloading = true,
            isCompleted = false
        ))

        downloadJob = serviceScope.launch {
            try {
                val serverUrl = authRepository.getServerUrlSync() ?: throw Exception("No server URL")
                val token = authRepository.getTokenSync() ?: throw Exception("No auth token")

                val downloadUrl = "$serverUrl/api/audiobooks/$audiobookId/stream"
                Log.d(TAG, "Downloading from: $downloadUrl")

                val request = Request.Builder()
                    .url(downloadUrl)
                    .addHeader("Authorization", "Bearer $token")
                    .build()

                val response = downloadClient.newCall(request).execute()

                if (!response.isSuccessful) {
                    throw Exception("Download failed: ${response.code}")
                }

                val body = response.body ?: throw Exception("Empty response body")
                val contentLength = body.contentLength()

                val downloadsDir = File(filesDir, "audiobooks").also { 
                    if (!it.exists() && !it.mkdirs()) {
                        throw IOException("Failed to create download directory")
                    }
                }
                val file = File(downloadsDir, "audiobook_$audiobookId.m4b")
                
                // Check available storage space
                val freeSpace = downloadsDir.freeSpace
                if (contentLength > 0 && freeSpace < contentLength * 1.1) { // 10% buffer
                    throw IOException("Insufficient storage space. Need ${contentLength / 1024 / 1024}MB, but only ${freeSpace / 1024 / 1024}MB available")
                }
                
                val outputStream = FileOutputStream(file)
                val inputStream = body.byteStream()

                val buffer = ByteArray(8192)
                var bytesRead: Int
                var totalBytesRead = 0L
                var lastProgress = 0

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    totalBytesRead += bytesRead

                    val progress = if (contentLength > 0) {
                        ((totalBytesRead.toFloat() / contentLength.toFloat()) * 100).toInt()
                    } else {
                        0
                    }

                    // Update notification every 1%
                    if (progress != lastProgress) {
                        lastProgress = progress
                        updateNotification(title, author, progress)

                        // Update DownloadManager state
                        downloadManager.updateDownloadStateExternal(audiobookId, DownloadState(
                            audiobookId = audiobookId,
                            progress = progress / 100f,
                            isDownloading = true,
                            isCompleted = false
                        ))
                    }
                }

                outputStream.close()
                inputStream.close()

                // Fetch audiobook details and chapters
                val audiobookResponse = api.getAudiobook(audiobookId)
                val audiobook = audiobookResponse.body()

                val chapters = try {
                    val chaptersResponse = api.getChapters(audiobookId)
                    if (chaptersResponse.isSuccessful) {
                        chaptersResponse.body() ?: emptyList()
                    } else {
                        emptyList()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to fetch chapters", e)
                    emptyList()
                }

                // Save to DownloadManager
                if (audiobook != null) {
                    downloadManager.saveDownloadedBook(audiobook, file.absolutePath, file.length(), chapters)
                }

                // Update state to completed
                downloadManager.updateDownloadStateExternal(audiobookId, DownloadState(
                    audiobookId = audiobookId,
                    progress = 1f,
                    isDownloading = false,
                    isCompleted = true
                ))

                Log.d(TAG, "Download complete: ${file.absolutePath}")

                // Show completion notification
                withContext(Dispatchers.Main) {
                    val notificationManager = getSystemService(NotificationManager::class.java)
                    notificationManager.notify(NOTIFICATION_ID + 1, createCompletedNotification(title, true))
                }

            } catch (e: Exception) {
                Log.e(TAG, "Download failed", e)

                // Clean up partial download
                try {
                    val downloadsDir = File(filesDir, "audiobooks")
                    val file = File(downloadsDir, "audiobook_$audiobookId.m4b")
                    if (file.exists()) {
                        file.delete()
                    }
                } catch (deleteError: Exception) {
                    Log.e(TAG, "Failed to clean up partial download", deleteError)
                }

                val errorMessage = when (e) {
                    is IOException -> e.message ?: "Download failed"
                    is SecurityException -> "Permission denied"
                    is OutOfMemoryError -> "Out of memory"
                    else -> "Download failed: ${e.message}"
                }

                downloadManager.updateDownloadStateExternal(audiobookId, DownloadState(
                    audiobookId = audiobookId,
                    progress = 0f,
                    isDownloading = false,
                    isCompleted = false,
                    error = errorMessage
                ))

                // Show failure notification with specific error
                withContext(Dispatchers.Main) {
                    val notificationManager = getSystemService(NotificationManager::class.java)
                    notificationManager.notify(NOTIFICATION_ID + 1, createCompletedNotification(title, false, errorMessage))
                }
            } finally {
                currentDownloadId = null
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun updateNotification(title: String, author: String, progress: Int) {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, createNotification(title, author, progress))
    }

    private fun cancelCurrentDownload() {
        downloadJob?.cancel()
        currentDownloadId?.let { id ->
            downloadManager.updateDownloadStateExternal(id, DownloadState(
                audiobookId = id,
                progress = 0f,
                isDownloading = false,
                isCompleted = false,
                error = "Cancelled"
            ))
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        downloadJob?.cancel()
        super.onDestroy()
    }
}
