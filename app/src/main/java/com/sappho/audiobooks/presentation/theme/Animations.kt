package com.sappho.audiobooks.presentation.theme

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.ripple
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.debugInspectorInfo
import androidx.compose.ui.semantics.Role
import android.provider.Settings

/**
 * CompositionLocal for reduce motion preference
 */
val LocalReduceMotion = compositionLocalOf { false }

/**
 * Check if system "reduce motion" / "remove animations" setting is enabled
 */
@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        try {
            val scale = Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            )
            scale == 0f
        } catch (e: Exception) {
            false
        }
    }
}

/**
 * Common animation specifications used throughout the app
 */
object SapphoAnimations {
    
    // Duration constants
    const val FAST_DURATION = 150
    const val NORMAL_DURATION = 300
    const val SLOW_DURATION = 500
    
    // Easing functions
    val FastEaseOut = CubicBezierEasing(0.0f, 0.0f, 0.2f, 1.0f)
    val StandardEaseInOut = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1.0f)
    val SlowEaseOut = CubicBezierEasing(0.0f, 0.0f, 0.0f, 1.0f)
    
    // Common tween animations
    val fastFadeIn = fadeIn(animationSpec = tween(FAST_DURATION, easing = FastEaseOut))
    val fastFadeOut = fadeOut(animationSpec = tween(FAST_DURATION, easing = FastEaseOut))
    
    val normalFadeIn = fadeIn(animationSpec = tween(NORMAL_DURATION, easing = StandardEaseInOut))
    val normalFadeOut = fadeOut(animationSpec = tween(NORMAL_DURATION, easing = StandardEaseInOut))
    
    val scaleInAnimation = scaleIn(
        initialScale = 0.8f,
        animationSpec = tween(NORMAL_DURATION, easing = StandardEaseInOut)
    )
    val scaleOutAnimation = scaleOut(
        targetScale = 0.8f,
        animationSpec = tween(FAST_DURATION, easing = FastEaseOut)
    )
    
    val slideInFromBottom = slideInVertically(
        initialOffsetY = { it / 2 },
        animationSpec = tween(NORMAL_DURATION, easing = StandardEaseInOut)
    )
    val slideOutToBottom = slideOutVertically(
        targetOffsetY = { it / 2 },
        animationSpec = tween(FAST_DURATION, easing = FastEaseOut)
    )
}

/**
 * Animated visibility with common enter/exit animations.
 * Respects system "reduce motion" setting by using instant transitions.
 */
@Composable
fun SapphoAnimatedVisibility(
    visible: Boolean,
    modifier: Modifier = Modifier,
    enter: androidx.compose.animation.EnterTransition = SapphoAnimations.normalFadeIn + SapphoAnimations.scaleInAnimation,
    exit: androidx.compose.animation.ExitTransition = SapphoAnimations.fastFadeOut + SapphoAnimations.scaleOutAnimation,
    content: @Composable AnimatedVisibilityScope.() -> Unit
) {
    val reduceMotion = LocalReduceMotion.current

    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = if (reduceMotion) fadeIn(animationSpec = tween(0)) else enter,
        exit = if (reduceMotion) fadeOut(animationSpec = tween(0)) else exit,
        content = content
    )
}

/**
 * Bouncy clickable modifier with scale animation.
 * Respects system "reduce motion" setting.
 */
fun Modifier.bouncyClickable(
    enabled: Boolean = true,
    onClickLabel: String? = null,
    role: Role? = null,
    onClick: () -> Unit
) = composed(
    inspectorInfo = debugInspectorInfo {
        name = "bouncyClickable"
        properties["enabled"] = enabled
        properties["onClickLabel"] = onClickLabel
        properties["role"] = role
        properties["onClick"] = onClick
    }
) {
    val reduceMotion = LocalReduceMotion.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed && !reduceMotion) 0.95f else 1f,
        animationSpec = if (reduceMotion) {
            tween(0)
        } else {
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessHigh
            )
        },
        label = "scale"
    )

    this
        .scale(scale)
        .clickable(
            interactionSource = interactionSource,
            indication = ripple(),
            enabled = enabled,
            onClickLabel = onClickLabel,
            role = role,
            onClick = onClick
        )
}

/**
 * Progressive reveal animation for lists
 */
fun Modifier.progressiveReveal(
    index: Int,
    visible: Boolean = true,
    delayBase: Int = 50
) = composed {
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(
            durationMillis = 300,
            delayMillis = index * delayBase,
            easing = EaseOutCubic
        ),
        label = "progressive_reveal_alpha"
    )
    
    val translationY by animateFloatAsState(
        targetValue = if (visible) 0f else 20f,
        animationSpec = tween(
            durationMillis = 400,
            delayMillis = index * delayBase,
            easing = EaseOutBack
        ),
        label = "progressive_reveal_translation"
    )
    
    this
        .alpha(alpha)
        .offset(y = translationY.dp)
}
