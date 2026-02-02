package com.sappho.audiobooks

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.util.DebugLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.google.android.gms.cast.framework.CastContext
import com.sappho.audiobooks.cast.CastHelper
import com.sappho.audiobooks.di.NetworkModule
import dagger.hilt.android.HiltAndroidApp
import okhttp3.OkHttpClient
import javax.inject.Inject

@HiltAndroidApp
class SapphoApplication : Application(), ImageLoaderFactory {

    @Inject
    lateinit var okHttpClient: OkHttpClient

    @Inject
    lateinit var castHelper: CastHelper

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        // Initialize Cast asynchronously to not block app startup
        initializeCastAsync()
    }

    override fun onTerminate() {
        super.onTerminate()
        // Disconnect Cast when app is terminated
        if (castHelper.isCasting()) {
            castHelper.disconnectCast()
        }
    }

    private fun initializeCastAsync() {
        // CastContext.getSharedInstance() MUST be called from the main thread
        // Using Dispatchers.Main to ensure this requirement is met
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
            try {
                // Initialize Cast context on main thread (required by Cast SDK)
                CastContext.getSharedInstance(this@SapphoApplication)
                castHelper.initialize(this@SapphoApplication)
                android.util.Log.d("SapphoApplication", "Cast initialized successfully")
            } catch (e: Exception) {
                android.util.Log.e("SapphoApplication", "Error initializing Cast", e)
            }
        }
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .okHttpClient(okHttpClient)
            // Remove debug logger for production performance
            .logger(if (BuildConfig.DEBUG) DebugLogger() else null)
            // Configure memory cache (25% of available memory)
            .memoryCache {
                coil.memory.MemoryCache.Builder(this@SapphoApplication)
                    .maxSizePercent(0.25)
                    .build()
            }
            // Configure disk cache (50MB)
            .diskCache {
                coil.disk.DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(50 * 1024 * 1024) // 50MB
                    .build()
            }
            // Enable hardware bitmaps for better memory efficiency
            .allowHardware(true)
            // Crossfade animation for smoother UX
            .crossfade(300)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            PLAYBACK_CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_description)
            setShowBadge(false)
        }

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    companion object {
        const val PLAYBACK_CHANNEL_ID = "playback_channel"
    }
}
