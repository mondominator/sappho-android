package com.sappho.audiobooks.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.sappho.audiobooks.R
import com.sappho.audiobooks.data.remote.SapphoApi
import com.sappho.audiobooks.presentation.MainActivity
import com.sappho.audiobooks.util.ProgressRequestBody
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import javax.inject.Inject

@AndroidEntryPoint
class UploadService : Service() {

    @Inject
    lateinit var api: SapphoApi

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var uploadJob: Job? = null

    companion object {
        private const val TAG = "UploadService"
        private const val NOTIFICATION_ID = 3
        private const val CHANNEL_ID = "audiobook_upload"

        private const val ACTION_UPLOAD_SEPARATE = "com.sappho.audiobooks.UPLOAD_SEPARATE"
        private const val ACTION_UPLOAD_SINGLE = "com.sappho.audiobooks.UPLOAD_SINGLE"
        private const val ACTION_CANCEL_UPLOAD = "com.sappho.audiobooks.CANCEL_UPLOAD"

        private const val EXTRA_URIS = "uris"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_AUTHOR = "author"
        private const val EXTRA_NARRATOR = "narrator"

        // Shared upload state observable by UI
        private val _uploadState = MutableStateFlow(UploadServiceState())
        val uploadState: StateFlow<UploadServiceState> = _uploadState

        fun resetState() {
            _uploadState.value = UploadServiceState()
        }

        fun startUploadSeparate(
            context: Context,
            uris: List<Uri>,
            title: String?,
            author: String?,
            narrator: String?
        ) {
            _uploadState.value = UploadServiceState(status = UploadStatus.UPLOADING)
            val intent = Intent(context, UploadService::class.java).apply {
                action = ACTION_UPLOAD_SEPARATE
                putParcelableArrayListExtra(EXTRA_URIS, ArrayList(uris))
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_AUTHOR, author)
                putExtra(EXTRA_NARRATOR, narrator)
            }
            context.startForegroundService(intent)
        }

        fun startUploadSingle(
            context: Context,
            uris: List<Uri>,
            title: String?,
            author: String?
        ) {
            _uploadState.value = UploadServiceState(status = UploadStatus.UPLOADING)
            val intent = Intent(context, UploadService::class.java).apply {
                action = ACTION_UPLOAD_SINGLE
                putParcelableArrayListExtra(EXTRA_URIS, ArrayList(uris))
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_AUTHOR, author)
            }
            context.startForegroundService(intent)
        }

        fun cancelUpload(context: Context) {
            val intent = Intent(context, UploadService::class.java).apply {
                action = ACTION_CANCEL_UPLOAD
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
            ACTION_UPLOAD_SEPARATE -> {
                val uris = intent.getParcelableArrayListExtra<Uri>(EXTRA_URIS) ?: emptyList()
                val title = intent.getStringExtra(EXTRA_TITLE)
                val author = intent.getStringExtra(EXTRA_AUTHOR)
                val narrator = intent.getStringExtra(EXTRA_NARRATOR)

                if (uris.isNotEmpty()) {
                    startForeground(NOTIFICATION_ID, createNotification("Preparing upload...", 0))
                    uploadSeparateBooks(uris, title, author, narrator)
                }
            }
            ACTION_UPLOAD_SINGLE -> {
                val uris = intent.getParcelableArrayListExtra<Uri>(EXTRA_URIS) ?: emptyList()
                val title = intent.getStringExtra(EXTRA_TITLE)
                val author = intent.getStringExtra(EXTRA_AUTHOR)

                if (uris.isNotEmpty()) {
                    startForeground(NOTIFICATION_ID, createNotification("Preparing upload...", 0))
                    uploadSingleBook(uris, title, author)
                }
            }
            ACTION_CANCEL_UPLOAD -> {
                cancelCurrentUpload()
            }
        }
        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Audiobook Uploads",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows upload progress for audiobooks"
            setShowBadge(false)
        }
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    private fun createNotification(text: String, progress: Int): Notification {
        val contentIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this, 0, contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val cancelIntent = Intent(this, UploadService::class.java).apply {
            action = ACTION_CANCEL_UPLOAD
        }
        val cancelPendingIntent = PendingIntent.getService(
            this, 1, cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Uploading Audiobook")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setProgress(100, progress, progress == 0)
            .setOngoing(true)
            .setContentIntent(contentPendingIntent)
            .addAction(android.R.drawable.ic_delete, "Cancel", cancelPendingIntent)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun createCompletedNotification(success: Boolean, message: String): Notification {
        val contentIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this, 0, contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(if (success) "Upload Complete" else "Upload Failed")
            .setContentText(message)
            .setSmallIcon(
                if (success) android.R.drawable.stat_sys_upload_done
                else android.R.drawable.stat_notify_error
            )
            .setContentIntent(contentPendingIntent)
            .setAutoCancel(true)
            .build()
    }

    private fun updateProgress(progress: Float, text: String) {
        _uploadState.value = _uploadState.value.copy(
            progress = progress,
            statusText = text
        )
        val percent = (progress * 100).toInt()
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, createNotification(text, percent))
    }

    private fun uploadSeparateBooks(
        uris: List<Uri>,
        title: String?,
        author: String?,
        narrator: String?
    ) {
        uploadJob = serviceScope.launch {
            val tempFiles = mutableListOf<File>()
            try {
                var successCount = 0
                var failCount = 0
                var totalBytes = 0L

                // Copy all files to temp
                val prepared = mutableListOf<Pair<File, String>>()
                uris.forEach { uri ->
                    val fileName = getFileNameFromUri(uri) ?: "audiobook_${System.currentTimeMillis()}.mp3"
                    val inputStream = contentResolver.openInputStream(uri)
                        ?: throw IOException("Cannot read file: $fileName")

                    val tempFile = File(cacheDir, "upload_${System.currentTimeMillis()}_$fileName")
                    tempFiles.add(tempFile)
                    inputStream.use { input ->
                        tempFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    totalBytes += tempFile.length()
                    prepared.add(Pair(tempFile, fileName))
                }

                var cumulativeBytesUploaded = 0L

                prepared.forEachIndexed { index, (tempFile, fileName) ->
                    val bytesBeforeThisFile = cumulativeBytesUploaded
                    val fileLabel = if (prepared.size > 1) "(${index + 1}/${prepared.size}) $fileName" else fileName

                    try {
                        val progressBody = ProgressRequestBody(
                            file = tempFile,
                            contentType = "audio/*".toMediaTypeOrNull()
                        ) { bytesWritten, _ ->
                            if (totalBytes > 0) {
                                val overallProgress = (bytesBeforeThisFile + bytesWritten).toFloat() / totalBytes
                                updateProgress(overallProgress.coerceIn(0f, 1f), "Uploading $fileLabel")
                            }
                        }
                        val filePart = MultipartBody.Part.createFormData("audiobook", fileName, progressBody)

                        val response = api.uploadAudiobook(
                            file = filePart,
                            title = title?.toRequestBody("text/plain".toMediaTypeOrNull()),
                            author = author?.toRequestBody("text/plain".toMediaTypeOrNull()),
                            narrator = narrator?.toRequestBody("text/plain".toMediaTypeOrNull())
                        )

                        if (response.isSuccessful) {
                            successCount++
                        } else {
                            failCount++
                            Log.e(TAG, "Upload failed for $fileName: ${response.code()}")
                        }

                        cumulativeBytesUploaded += tempFile.length()
                    } catch (e: Exception) {
                        failCount++
                        Log.e(TAG, "Upload error for $fileName", e)
                        cumulativeBytesUploaded += tempFile.length()
                    }
                }

                val success = failCount == 0
                val message = if (success) {
                    "Successfully uploaded $successCount audiobook(s)"
                } else {
                    "Uploaded $successCount, $failCount failed"
                }

                _uploadState.value = UploadServiceState(
                    status = if (success) UploadStatus.SUCCESS else UploadStatus.ERROR,
                    progress = 1f,
                    message = message
                )

                showCompletedNotification(success, message)

            } catch (e: Exception) {
                Log.e(TAG, "Upload error", e)
                val message = "Upload failed: ${e.message}"
                _uploadState.value = UploadServiceState(
                    status = UploadStatus.ERROR,
                    message = message
                )
                showCompletedNotification(false, message)
            } finally {
                tempFiles.forEach { file ->
                    try { if (file.exists()) file.delete() } catch (_: Exception) {}
                }
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun uploadSingleBook(
        uris: List<Uri>,
        title: String?,
        author: String?
    ) {
        uploadJob = serviceScope.launch {
            val tempFiles = mutableListOf<File>()
            try {
                // Prepare all files
                uris.forEachIndexed { index, uri ->
                    val fileName = getFileNameFromUri(uri) ?: "chapter_${index + 1}.mp3"
                    val inputStream = contentResolver.openInputStream(uri)
                        ?: throw IOException("Cannot read file: $fileName")

                    val tempFile = File(cacheDir, "upload_${System.currentTimeMillis()}_$fileName")
                    tempFiles.add(tempFile)
                    inputStream.use { input ->
                        tempFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }

                    updateProgress(
                        (index + 1).toFloat() / uris.size * 0.1f,
                        "Preparing file ${index + 1}/${uris.size}"
                    )
                }

                // Build multipart parts with progress tracking
                val totalBytes = tempFiles.sumOf { it.length() }
                var totalBytesWritten = 0L
                val fileParts = mutableListOf<MultipartBody.Part>()

                tempFiles.forEachIndexed { index, tempFile ->
                    val fileName = getFileNameFromUri(uris[index]) ?: "chapter_${index + 1}.mp3"
                    val bytesBeforeThisFile = totalBytesWritten

                    val progressBody = ProgressRequestBody(
                        file = tempFile,
                        contentType = "audio/*".toMediaTypeOrNull()
                    ) { bytesWritten, _ ->
                        if (totalBytes > 0) {
                            val uploadFraction = (bytesBeforeThisFile + bytesWritten).toFloat() / totalBytes
                            val progress = (0.1f + uploadFraction * 0.9f).coerceIn(0f, 1f)
                            updateProgress(progress, "Uploading... ${(progress * 100).toInt()}%")
                        }
                    }

                    totalBytesWritten += tempFile.length()
                    val filePart = MultipartBody.Part.createFormData("audiobooks", fileName, progressBody)
                    fileParts.add(filePart)
                }

                val response = api.uploadMultiFile(
                    files = fileParts,
                    title = title?.toRequestBody("text/plain".toMediaTypeOrNull()),
                    author = author?.toRequestBody("text/plain".toMediaTypeOrNull())
                )

                if (response.isSuccessful) {
                    val message = "Successfully uploaded audiobook with ${uris.size} file(s)"
                    _uploadState.value = UploadServiceState(
                        status = UploadStatus.SUCCESS,
                        progress = 1f,
                        message = message
                    )
                    showCompletedNotification(true, message)
                } else {
                    val message = "Upload failed: ${response.code()}"
                    _uploadState.value = UploadServiceState(
                        status = UploadStatus.ERROR,
                        message = message
                    )
                    showCompletedNotification(false, message)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Upload error", e)
                val message = "Upload failed: ${e.message}"
                _uploadState.value = UploadServiceState(
                    status = UploadStatus.ERROR,
                    message = message
                )
                showCompletedNotification(false, message)
            } finally {
                tempFiles.forEach { file ->
                    try { if (file.exists()) file.delete() } catch (_: Exception) {}
                }
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private suspend fun showCompletedNotification(success: Boolean, message: String) {
        withContext(Dispatchers.Main) {
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.notify(
                NOTIFICATION_ID + 1,
                createCompletedNotification(success, message)
            )
        }
    }

    private fun getFileNameFromUri(uri: Uri): String? {
        var fileName: String? = null
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) {
                    fileName = cursor.getString(nameIndex)
                }
            }
        }
        return fileName
    }

    private fun cancelCurrentUpload() {
        uploadJob?.cancel()
        _uploadState.value = UploadServiceState(
            status = UploadStatus.ERROR,
            message = "Upload cancelled"
        )
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        uploadJob?.cancel()
        super.onDestroy()
    }
}

enum class UploadStatus {
    IDLE,
    UPLOADING,
    SUCCESS,
    ERROR
}

data class UploadServiceState(
    val status: UploadStatus = UploadStatus.IDLE,
    val progress: Float = 0f,
    val message: String? = null,
    val statusText: String? = null
)
