package com.sappho.audiobooks.presentation.theme

import androidx.compose.animation.AnimatedContent
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.with
import androidx.compose.ui.Alignment
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.ripple
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
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
 * Slide in from bottom animation for dialogs and sheets
 */
@Composable
fun SlideInFromBottomAnimatedVisibility(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable AnimatedVisibilityScope.() -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = SapphoAnimations.normalFadeIn + SapphoAnimations.slideInFromBottom,
        exit = SapphoAnimations.fastFadeOut + SapphoAnimations.slideOutToBottom,
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
 * Gentle pulse animation for attention
 */
@Composable
fun rememberPulseAnimation(): Float {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    
    return infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    ).value
}

/**
 * Shimmer animation for loading states
 */
@Composable
fun rememberShimmerAnimation(): Float {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    
    return infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer"
    ).value
}

/**
 * Scale animation for button states (pressed/unpressed)
 */
@Composable
fun rememberButtonScaleAnimation(isPressed: Boolean): Float {
    return animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "button_scale"
    ).value
}

/**
 * Elevation animation for card hover/press states
 */
@Composable
fun rememberElevationAnimation(isElevated: Boolean): Dp {
    return animateDpAsState(
        targetValue = if (isElevated) 8.dp else 2.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "elevation"
    ).value
}

/**
 * Color animation for interactive states
 */
@Composable
fun rememberColorAnimation(targetColor: Color): Color {
    return animateColorAsState(
        targetValue = targetColor,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "color"
    ).value
}

/**
 * Rotation animation for loading spinners
 */
@Composable
fun rememberSpinnerAnimation(): Float {
    val infiniteTransition = rememberInfiniteTransition(label = "spinner")
    
    return infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "spinner_rotation"
    ).value
}

/**
 * Success checkmark animation
 */
@Composable
fun rememberSuccessAnimation(isVisible: Boolean): Float {
    return animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "success"
    ).value
}

/**
 * Error shake animation
 */
@Composable
fun rememberShakeAnimation(trigger: Boolean): Float {
    var shake by remember { mutableStateOf(false) }
    
    LaunchedEffect(trigger) {
        if (trigger) {
            shake = true
        }
    }
    
    val shakeOffset by animateFloatAsState(
        targetValue = if (shake) 0f else 0f,
        animationSpec = keyframes {
            durationMillis = 500
            0f at 0
            10f at 50
            -10f at 100
            8f at 150
            -8f at 200
            5f at 250
            -5f at 300
            2f at 350
            -2f at 400
            0f at 500
        },
        finishedListener = { shake = false },
        label = "shake"
    )
    
    return shakeOffset
}

/**
 * Enhanced clickable with sophisticated animations
 */
fun Modifier.enhancedClickable(
    enabled: Boolean = true,
    onClickLabel: String? = null,
    role: Role? = null,
    onClick: () -> Unit
) = composed(
    inspectorInfo = debugInspectorInfo {
        name = "enhancedClickable"
        properties["enabled"] = enabled
        properties["onClickLabel"] = onClickLabel
        properties["role"] = role
        properties["onClick"] = onClick
    }
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "enhanced_scale"
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
 * Smooth state transition animation
 */
@Composable
fun animateFloatStateTransition(
    targetState: Float,
    animationSpec: FiniteAnimationSpec<Float> = spring(),
    label: String = ""
): State<Float> {
    return animateFloatAsState(
        targetValue = targetState,
        animationSpec = animationSpec,
        label = label
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

/**
 * Smooth content swap animation
 */
@OptIn(androidx.compose.animation.ExperimentalAnimationApi::class)
@Composable
fun AnimatedContentSwap(
    targetState: Boolean,
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable AnimatedVisibilityScope.(targetState: Boolean) -> Unit
) {
    AnimatedContent(
        targetState = targetState,
        modifier = modifier,
        transitionSpec = {
            slideInVertically { it / 2 } + fadeIn() with
                    slideOutVertically { -it / 2 } + fadeOut()
        },
        contentAlignment = contentAlignment,
        content = content,
        label = "content_swap"
    )
}

/**
 * Breathing animation for active states
 */
@Composable
fun rememberBreathingAnimation(isActive: Boolean): Float {
    val infiniteTransition = rememberInfiniteTransition(label = "breathing")
    
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isActive) 1.02f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathing_scale"
    )
    
    return scale
}

/**
 * Content state animation for loading/error/success states
 */
@Composable
fun ContentStateAnimation(
    isLoading: Boolean,
    hasError: Boolean,
    hasContent: Boolean,
    modifier: Modifier = Modifier,
    loadingContent: @Composable () -> Unit,
    errorContent: @Composable () -> Unit,
    successContent: @Composable () -> Unit
) {
    SapphoAnimatedVisibility(
        visible = isLoading,
        modifier = modifier
    ) {
        loadingContent()
    }
    
    SapphoAnimatedVisibility(
        visible = hasError && !isLoading,
        modifier = modifier
    ) {
        errorContent()
    }
    
    SapphoAnimatedVisibility(
        visible = hasContent && !isLoading && !hasError,
        modifier = modifier
    ) {
        successContent()
    }
}