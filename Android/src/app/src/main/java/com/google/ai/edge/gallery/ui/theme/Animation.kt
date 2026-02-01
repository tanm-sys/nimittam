/*
 * Copyright 2025 Tanmay Patil
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.google.ai.edge.gallery.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.ui.unit.IntOffset

/**
 * Nimittam Animation System
 * Fluid Dynamics with spring physics
 * 120fps micro-interactions
 * Shape morphing transitions
 */

// === Spring Physics Configuration ===
val NimittamSpring = spring<Float>(
    dampingRatio = Spring.DampingRatioMediumBouncy,  // 0.7-0.8 damping
    stiffness = Spring.StiffnessMedium  // ~300 stiffness
)

val NimittamSpringLow = spring<Float>(
    dampingRatio = Spring.DampingRatioLowBouncy,
    stiffness = Spring.StiffnessLow
)

val NimittamSpringHigh = spring<Float>(
    dampingRatio = Spring.DampingRatioHighBouncy,
    stiffness = Spring.StiffnessHigh
)

// Custom spring with exact specifications
val FluidSpring = spring<Float>(
    dampingRatio = 0.8f,
    stiffness = 380f
)

// === Easing Functions ===
// Material Design standard easing
val MaterialStandardEasing: Easing = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1.0f)

// Material Design decelerate easing
val MaterialDecelerateEasing: Easing = CubicBezierEasing(0.0f, 0.0f, 0.2f, 1.0f)

// Material Design accelerate easing
val MaterialAccelerateEasing: Easing = CubicBezierEasing(0.4f, 0.0f, 1.0f, 1.0f)

// Nimittam custom easing: cubic-bezier(0.2, 0.0, 0, 1.0)
val NimittamEasing: Easing = CubicBezierEasing(0.2f, 0.0f, 0f, 1.0f)

// === Animation Durations ===
object AnimationDuration {
    const val INSTANT = 50
    const val FAST = 150
    const val NORMAL = 200
    const val MEDIUM = 300
    const val SLOW = 400
    const val DELIBERATE = 600
    const val SHAPE_MORPH = 800
}

// === Standard Tweens ===
fun <T> fastTween() = tween<T>(
    durationMillis = AnimationDuration.FAST,
    easing = NimittamEasing
)

fun <T> normalTween() = tween<T>(
    durationMillis = AnimationDuration.NORMAL,
    easing = NimittamEasing
)

fun <T> mediumTween() = tween<T>(
    durationMillis = AnimationDuration.MEDIUM,
    easing = NimittamEasing
)

fun <T> slowTween() = tween<T>(
    durationMillis = AnimationDuration.SLOW,
    easing = NimittamEasing
)

fun <T> shapeMorphTween() = tween<T>(
    durationMillis = AnimationDuration.SHAPE_MORPH,
    easing = MaterialStandardEasing
)

// === Enter/Exit Transitions ===
val FadeIn = fadeIn(animationSpec = normalTween())
val FadeOut = fadeOut(animationSpec = normalTween())

val ScaleIn = scaleIn(
    initialScale = 0.8f,
    animationSpec = NimittamSpring
)
val ScaleOut = scaleOut(
    targetScale = 0.8f,
    animationSpec = normalTween()
)

val SlideInFromBottom = slideInVertically(
    initialOffsetY = { it },
    animationSpec = tween(300)
)
val SlideOutToBottom = slideOutVertically(
    targetOffsetY = { it },
    animationSpec = normalTween()
)

val SlideInFromTop = slideInVertically(
    initialOffsetY = { -it },
    animationSpec = tween(300)
)
val SlideOutToTop = slideOutVertically(
    targetOffsetY = { -it },
    animationSpec = normalTween()
)

val SlideInFromRight = slideInHorizontally(
    initialOffsetX = { it },
    animationSpec = tween(300)
)
val SlideOutToRight = slideOutHorizontally(
    targetOffsetX = { it },
    animationSpec = normalTween()
)

val SlideInFromLeft = slideInHorizontally(
    initialOffsetX = { -it },
    animationSpec = tween(300)
)
val SlideOutToLeft = slideOutHorizontally(
    targetOffsetX = { -it },
    animationSpec = normalTween()
)

// === Combined Transitions ===
val PopIn = scaleIn(initialScale = 0.5f, animationSpec = tween(300)) +
        fadeIn(animationSpec = fastTween())

val PopOut = scaleOut(targetScale = 0.5f, animationSpec = normalTween()) +
        fadeOut(animationSpec = fastTween())

val SlideUpFadeIn = slideInVertically(
    initialOffsetY = { it / 4 },
    animationSpec = tween(300)
) + fadeIn(animationSpec = mediumTween())

val SlideDownFadeOut = slideOutVertically(
    targetOffsetY = { it / 4 },
    animationSpec = normalTween()
) + fadeOut(animationSpec = fastTween())

// === Micro-interactions ===
val PressScaleDown = tween<Float>(
    durationMillis = AnimationDuration.INSTANT,
    easing = MaterialAccelerateEasing
)

val ReleaseScaleUp = spring<Float>(
    dampingRatio = Spring.DampingRatioMediumBouncy,
    stiffness = Spring.StiffnessHigh
)

// === Stagger Animation Delays ===
object StaggerDelay {
    const val NONE = 0
    const val FAST = 25
    const val NORMAL = 50
    const val SLOW = 100
}

// === Progress Animation ===
val ProgressAnimation = tween<Float>(
    durationMillis = AnimationDuration.DELIBERATE,
    easing = MaterialStandardEasing
)

// === Pulse Animation ===
val PulseAnimation = tween<Float>(
    durationMillis = 1200,
    easing = FastOutSlowInEasing
)

private val LinearEasingVal: Easing = { it }

// === Waveform Animation ===
val WaveformAnimation = tween<Float>(
    durationMillis = 100,
    easing = LinearEasingVal
)

// === Typing Indicator Animation ===
val TypingDotDelay1 = 0
val TypingDotDelay2 = 150
val TypingDotDelay3 = 300

// === Page Transitions ===
val PageEnterTransition = slideInHorizontally(
    initialOffsetX = { it },
    animationSpec = mediumTween()
) + fadeIn(animationSpec = mediumTween())

val PageExitTransition = slideOutHorizontally(
    targetOffsetX = { -it / 3 },
    animationSpec = normalTween()
) + fadeOut(animationSpec = normalTween())

val PagePopEnterTransition = slideInHorizontally(
    initialOffsetX = { -it / 3 },
    animationSpec = mediumTween()
) + fadeIn(animationSpec = mediumTween())

val PagePopExitTransition = slideOutHorizontally(
    targetOffsetX = { it },
    animationSpec = normalTween()
) + fadeOut(animationSpec = normalTween())
