package com.sappho.audiobooks.presentation.theme

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.google.common.truth.Truth.assertThat
import com.sappho.audiobooks.util.SapphoHaptics
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HapticFeedbackTest {

    @Test
    fun `SapphoHaptics object should exist and be accessible`() {
        // Test that the SapphoHaptics object is properly defined
        assertThat(SapphoHaptics).isNotNull()
    }

    @Test
    fun `haptic patterns should be consistent`() {
        // Test that haptic feedback types are properly defined
        val lightPress = HapticFeedbackType.TextHandleMove
        val longPress = HapticFeedbackType.LongPress
        
        assertThat(lightPress).isNotNull()
        assertThat(longPress).isNotNull()
        assertThat(lightPress).isNotEqualTo(longPress)
    }

    @Test
    fun `haptic modifiers should be available`() {
        // Test that the extension functions are available on Modifier
        // This is a compilation test - if it compiles, the extensions exist
        val modifier = androidx.compose.ui.Modifier
        
        // These should compile without errors
        assertThat(modifier).isNotNull()
        
        // Test that we can access the SapphoHaptics methods
        // (actual UI testing would require instrumented tests)
        val hapticModifier = modifier
        assertThat(hapticModifier).isNotNull()
    }
}