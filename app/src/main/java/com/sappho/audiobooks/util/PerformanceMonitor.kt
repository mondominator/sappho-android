package com.sappho.audiobooks.util

import android.os.Debug
import android.util.Log
import com.sappho.audiobooks.BuildConfig
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PerformanceMonitor @Inject constructor() {

    companion object {
        const val TAG = "PerformanceMonitor"
        val isEnabled = BuildConfig.DEBUG
    }

    /**
     * Measure execution time of a suspend function
     */
    suspend inline fun <T> measureTime(
        operation: String,
        crossinline block: suspend () -> T
    ): T {
        if (!isEnabled) return block()

        val startTime = System.currentTimeMillis()
        val result = block()
        val endTime = System.currentTimeMillis()

        Log.d(TAG, "$operation took ${endTime - startTime}ms")
        return result
    }

    /**
     * Log memory usage
     */
    fun logMemoryUsage(context: String) {
        if (!isEnabled) return

        val runtime = Runtime.getRuntime()
        val usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
        val maxMemory = runtime.maxMemory() / (1024 * 1024)
        val nativeHeap = Debug.getNativeHeapAllocatedSize() / (1024 * 1024)

        Log.d(TAG, "$context - Memory: ${usedMemory}MB / ${maxMemory}MB, Native: ${nativeHeap}MB")
    }
}
