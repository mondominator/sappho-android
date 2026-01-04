package com.sappho.audiobooks

import android.app.Application

/**
 * Test application class for unit tests.
 * This prevents Hilt from being initialized during unit tests.
 */
class TestSapphoApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Don't initialize Hilt or other production dependencies
    }
}