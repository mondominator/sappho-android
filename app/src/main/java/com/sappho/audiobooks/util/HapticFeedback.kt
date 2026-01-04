package com.sappho.audiobooks.util

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.debugInspectorInfo
import androidx.compose.ui.semantics.Role

/**
 * Haptic feedback utilities for enhanced user interaction
 */
object SapphoHaptics {
    
    /**
     * Clickable modifier with haptic feedback
     */
    fun Modifier.hapticClickable(
        enabled: Boolean = true,
        onClickLabel: String? = null,
        role: Role? = null,
        hapticType: HapticFeedbackType = HapticFeedbackType.LongPress,
        onClick: () -> Unit
    ) = composed(
        inspectorInfo = debugInspectorInfo {
            name = "hapticClickable"
            properties["enabled"] = enabled
            properties["onClickLabel"] = onClickLabel
            properties["role"] = role
            properties["onClick"] = onClick
        }
    ) {
        val haptic = LocalHapticFeedback.current
        val interactionSource = remember { MutableInteractionSource() }
        
        this.clickable(
            interactionSource = interactionSource,
            indication = ripple(),
            enabled = enabled,
            onClickLabel = onClickLabel,
            role = role
        ) {
            if (enabled) {
                haptic.performHapticFeedback(hapticType)
                onClick()
            }
        }
    }
    
    /**
     * Toggleable modifier with haptic feedback
     */
    fun Modifier.hapticToggleable(
        value: Boolean,
        enabled: Boolean = true,
        role: Role? = null,
        hapticType: HapticFeedbackType = HapticFeedbackType.LongPress,
        onValueChange: (Boolean) -> Unit
    ) = composed(
        inspectorInfo = debugInspectorInfo {
            name = "hapticToggleable"
            properties["value"] = value
            properties["enabled"] = enabled
            properties["role"] = role
            properties["onValueChange"] = onValueChange
        }
    ) {
        val haptic = LocalHapticFeedback.current
        val interactionSource = remember { MutableInteractionSource() }
        
        this.toggleable(
            value = value,
            interactionSource = interactionSource,
            indication = ripple(),
            enabled = enabled,
            role = role
        ) { newValue ->
            if (enabled) {
                haptic.performHapticFeedback(hapticType)
                onValueChange(newValue)
            }
        }
    }
}

/**
 * Common haptic feedback patterns
 */
@Composable
fun rememberHapticPatterns() = LocalHapticFeedback.current

/**
 * Haptic feedback for common UI actions
 */
object HapticPatterns {
    
    @Composable
    fun lightTap(): () -> Unit {
        val haptic = rememberHapticPatterns()
        return {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }
    
    @Composable
    fun mediumTap(): () -> Unit {
        val haptic = rememberHapticPatterns()
        return {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }
    
    @Composable
    fun playButtonPress(): () -> Unit {
        val haptic = rememberHapticPatterns()
        return {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }
    
    @Composable
    fun favoriteToggle(): () -> Unit {
        val haptic = rememberHapticPatterns()
        return {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }
    
    @Composable
    fun downloadStart(): () -> Unit {
        val haptic = rememberHapticPatterns()
        return {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }
    
    @Composable
    fun downloadCancel(): () -> Unit {
        val haptic = rememberHapticPatterns()
        return {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }
    
    @Composable
    fun cardTap(): () -> Unit {
        val haptic = rememberHapticPatterns()
        return {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }
    
    @Composable
    fun navigationAction(): () -> Unit {
        val haptic = rememberHapticPatterns()
        return {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }
    
    @Composable
    fun errorOccurred(): () -> Unit {
        val haptic = rememberHapticPatterns()
        return {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }
    
    @Composable
    fun successAction(): () -> Unit {
        val haptic = rememberHapticPatterns()
        return {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }
    
    @Composable
    fun buttonPress(): () -> Unit {
        val haptic = rememberHapticPatterns()
        return {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }
}