package com.sappho.audiobooks.util

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class PerformanceMonitorTest {

    private lateinit var performanceMonitor: PerformanceMonitor

    @Before
    fun setup() {
        performanceMonitor = PerformanceMonitor()
    }

    @Test
    fun `should measure time for suspend function`() = runTest {
        // Given
        var executed = false

        // When
        val result = performanceMonitor.measureTime("test operation") {
            delay(10) // Simulate some work
            executed = true
            "test result"
        }

        // Then
        assertThat(result).isEqualTo("test result")
        assertThat(executed).isTrue()
    }

    @Test
    fun `should log memory usage without crashing`() {
        // When/Then - Should not throw any exceptions
        performanceMonitor.logMemoryUsage("test context")

        // Test passes if no exception is thrown
        assertThat(true).isTrue()
    }
}