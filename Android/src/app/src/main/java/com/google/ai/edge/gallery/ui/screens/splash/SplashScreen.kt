/*
 * Copyright 2025 Tanmay Patil
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.google.ai.edge.gallery.ui.screens.splash

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.ai.edge.gallery.ui.components.AnimatedMorphingRing
import com.google.ai.edge.gallery.ui.components.MeshGradientBackground
import com.google.ai.edge.gallery.ui.components.NoiseTexture
import com.google.ai.edge.gallery.ui.theme.AnimationDuration
import com.google.ai.edge.gallery.ui.theme.FluidSpring
import com.google.ai.edge.gallery.ui.theme.Gray64
import com.google.ai.edge.gallery.ui.theme.MaterialStandardEasing
import com.google.ai.edge.gallery.ui.theme.NimittamTheme
import com.google.ai.edge.gallery.ui.theme.PureBlack
import com.google.ai.edge.gallery.ui.theme.PureWhite
import com.google.ai.edge.gallery.ui.theme.WordmarkStyle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Splash Loading Screen
 * Deep black background, animated mesh gradient at 2% opacity
 * Nimittam wordmark with variable font animation (400→700)
 * "Offline Intelligence" in Label Small
 * Circular shape morphing progress indicator
 * 3D hero object floating
 */

@Composable
fun SplashScreen(
    onLoadingComplete: () -> Unit = {}
) {
    var showContent by remember { mutableStateOf(false) }
    var showSubtitle by remember { mutableStateOf(false) }
    var showProgress by remember { mutableStateOf(false) }
    var loadingProgress by remember { mutableStateOf(0f) }

    val infiniteTransition = rememberInfiniteTransition(label = "splash_animation")

    // Mesh gradient animation
    val meshTime by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2 * 3.14159f,
        animationSpec = infiniteRepeatable(
            animation = tween(10000),
            repeatMode = RepeatMode.Restart
        ),
        label = "mesh"
    )

    // Hero object floating animation
    val heroOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 20f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = MaterialStandardEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "float"
    )

    // Wordmark font weight animation
    val fontWeightProgress = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        showContent = true
        delay(300)
        showSubtitle = true
        delay(200)
        showProgress = true

        // Animate font weight from 400 to 700
        launch {
            fontWeightProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = 1500,
                    easing = MaterialStandardEasing
                )
            )
        }

        // Simulate loading progress
        launch {
            repeat(100) { i ->
                loadingProgress = i / 100f
                delay(30)
            }
            delay(500)
            onLoadingComplete()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PureBlack)
    ) {
        // Animated mesh gradient background at 2% opacity
        MeshGradientBackground(
            modifier = Modifier.fillMaxSize(),
            time = meshTime,
            opacity = 0.02f
        )

        // Noise texture overlay
        NoiseTexture(
            modifier = Modifier.fillMaxSize(),
            opacity = 0.015f
        )

        // Main content
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // 3D Hero Object (Geometric Node)
            AnimatedVisibility(
                visible = showContent,
                enter = fadeIn(animationSpec = tween(800))
            ) {
                HeroObject(
                    modifier = Modifier.size(120.dp),
                    offsetY = heroOffset
                )
            }

            Spacer(modifier = Modifier.height(48.dp))

            // Nimittam Wordmark with variable font animation
            AnimatedVisibility(
                visible = showContent,
                enter = fadeIn(animationSpec = tween(600))
            ) {
                val weight = FontWeight.W400.weight + (fontWeightProgress.value * 300).toInt()
                Text(
                    text = "Nimittam",
                    style = WordmarkStyle.copy(
                        fontWeight = FontWeight(weight),
                        fontSize = 36.sp,
                        letterSpacing = 2.sp
                    ),
                    color = PureWhite
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // "Offline Intelligence" subtitle
            AnimatedVisibility(
                visible = showSubtitle,
                enter = fadeIn(animationSpec = tween(400)),
                exit = fadeOut()
            ) {
                Text(
                    text = "Offline Intelligence",
                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                    color = Gray64,
                    letterSpacing = 3.sp
                )
            }

            Spacer(modifier = Modifier.height(64.dp))

            // Shape morphing progress indicator
            AnimatedVisibility(
                visible = showProgress,
                enter = fadeIn(animationSpec = tween(400))
            ) {
                AnimatedMorphingRing(
                    modifier = Modifier.size(48.dp),
                    color = PureWhite,
                    size = 48f
                )
            }
        }
    }
}

@Composable
private fun HeroObject(
    modifier: Modifier = Modifier,
    offsetY: Float = 0f
) {
    val infiniteTransition = rememberInfiniteTransition(label = "hero_rotation")

    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(20000),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Canvas(
        modifier = modifier
    ) {
        val canvasSize = size.minDimension
        val center = Offset(size.width / 2, size.height / 2 + offsetY)
        val radius = canvasSize * 0.35f

        rotate(rotation, center) {
            // Outer hexagon
            drawHexagon(
                center = center,
                radius = radius,
                color = PureWhite.copy(alpha = 0.3f),
                strokeWidth = 2f
            )

            // Inner rotating triangle
            rotate(rotation * 1.5f, center) {
                drawTriangle(
                    center = center,
                    radius = radius * 0.6f,
                    color = PureWhite.copy(alpha = 0.6f),
                    strokeWidth = 2f
                )
            }

            // Center node
            drawCircle(
                color = PureWhite,
                radius = radius * 0.15f,
                center = center
            )

            // Connection lines
            for (i in 0..5) {
                val angle = (i * 60f + rotation * 0.5f) * (Math.PI / 180f)
                val endX = center.x + kotlin.math.cos(angle).toFloat() * radius * 1.2f
                val endY = center.y + kotlin.math.sin(angle).toFloat() * radius * 1.2f

                drawLine(
                    color = PureWhite.copy(alpha = 0.2f),
                    start = center,
                    end = Offset(endX, endY),
                    strokeWidth = 1f
                )

                // Outer nodes
                drawCircle(
                    color = PureWhite.copy(alpha = 0.4f),
                    radius = 4f,
                    center = Offset(endX, endY)
                )
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawHexagon(
    center: Offset,
    radius: Float,
    color: Color,
    strokeWidth: Float
) {
    val path = androidx.compose.ui.graphics.Path()
    for (i in 0..6) {
        val angle = (i * 60f - 30f) * (Math.PI / 180f)
        val x = center.x + kotlin.math.cos(angle).toFloat() * radius
        val y = center.y + kotlin.math.sin(angle).toFloat() * radius
        if (i == 0) {
            path.moveTo(x, y)
        } else {
            path.lineTo(x, y)
        }
    }
    path.close()

    drawPath(
        path = path,
        color = color,
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth)
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawTriangle(
    center: Offset,
    radius: Float,
    color: Color,
    strokeWidth: Float
) {
    val path = androidx.compose.ui.graphics.Path()
    for (i in 0..3) {
        val angle = (i * 120f - 90f) * (Math.PI / 180f)
        val x = center.x + kotlin.math.cos(angle).toFloat() * radius
        val y = center.y + kotlin.math.sin(angle).toFloat() * radius
        if (i == 0) {
            path.moveTo(x, y)
        } else {
            path.lineTo(x, y)
        }
    }
    path.close()

    drawPath(
        path = path,
        color = color,
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth)
    )
}

@Preview(device = "id:pixel_8")
@Composable
private fun SplashScreenPreview() {
    NimittamTheme {
        SplashScreen()
    }
}
