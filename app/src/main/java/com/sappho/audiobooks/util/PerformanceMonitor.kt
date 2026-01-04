package com.sappho.audiobooks.util

import android.os.Debug
import android.util.Log
import androidx.compose.runtime.*
import com.sappho.audiobooks.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
     * Measure execution time of a regular function
     */
    inline fun <T> measureTimeSync(
        operation: String,
        crossinline block: () -> T
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
    
    /**
     * Start monitoring memory usage in background
     */
    fun startMemoryMonitoring() {
        if (!isEnabled) return
        
        CoroutineScope(Dispatchers.IO).launch {
            while (true) {
                logMemoryUsage("Background Monitor")
                kotlinx.coroutines.delay(30_000) // Log every 30 seconds
            }
        }
    }
}

/**
 * Composable for monitoring composition performance
 */
@Composable
fun PerformanceTracker(name: String) {
    if (!BuildConfig.DEBUG) return
    
    val recompositionCount = remember { mutableIntStateOf(0) }
    val startTime = remember { System.currentTimeMillis() }
    
    LaunchedEffect(Unit) {
        Log.d("CompositionPerf", "$name: Initial composition")
    }
    
    SideEffect {
        recompositionCount.intValue++
        val currentTime = System.currentTimeMillis()
        val timeSinceStart = currentTime - startTime
        
        if (recompositionCount.intValue > 1) {
            Log.d("CompositionPerf", "$name: Recomposition #${recompositionCount.intValue} at ${timeSinceStart}ms")
        }
    }
}