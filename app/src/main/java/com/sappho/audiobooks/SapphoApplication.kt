package com.sappho.audiobooks

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.util.DebugLogger
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
        initializeCast()
    }

    override fun onTerminate() {
        super.onTerminate()
        // Disconnect Cast when app is terminated
        if (castHelper.isCasting()) {
            android.util.Log.d("SapphoApplication", "App terminating, disconnecting Cast")
            castHelper.disconnectCast()
        }
    }

    private fun initializeCast() {
        try {
            // Initialize Cast context lazily in background
            CastContext.getSharedInstance(this)
            castHelper.initialize(this)
        } catch (e: Exception) {
            android.util.Log.e("SapphoApplication", "Error initializing Cast", e)
        }
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .okHttpClient(okHttpClient)
            .logger(DebugLogger())
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
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
    }

    companion object {
        const val PLAYBACK_CHANNEL_ID = "playback_channel"
    }
}
