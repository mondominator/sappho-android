package com.sappho.audiobooks

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.util.DebugLogger
import kotlinx.coroutines.launch
import com.google.android.gms.cast.framework.CastContext
import com.sappho.audiobooks.cast.CastHelper
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

}
