/*
 * Copyright 2025 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.ai.edge.gallery.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.google.ai.edge.gallery.ui.theme.AnimationDuration
import com.google.ai.edge.gallery.ui.theme.FluidSpring
import com.google.ai.edge.gallery.ui.theme.MaterialStandardEasing
import com.google.ai.edge.gallery.ui.theme.PureWhite
import com.google.ai.edge.gallery.ui.theme.shapeMorphTween
import kotlinx.coroutines.launch

/**
 * Shape Morphing Components
 * 800ms shape morphing transitions
 * Material 3 Expressive shape language
 */

@Composable
fun MorphingProgressIndicator(
    modifier: Modifier = Modifier,
    progress: Float = 0f,
    color: Color = PureWhite,
    strokeWidth: Float = 4f,
    size: Float = 48f
) {
    val animatedProgress = remember { Animatable(0f) }

    LaunchedEffect(progress) {
        animatedProgress.animateTo(
            targetValue = progress,
            animationSpec = shapeMorphTween()
        )
    }

    Canvas(modifier = modifier.size(size.dp)) {
        val canvasSize = size
        val center = Offset(canvasSize / 2, canvasSize / 2)
        val radius = (canvasSize - strokeWidth) / 2

        // Background circle
        drawCircle(
            color = color.copy(alpha = 0.2f),
            radius = radius,
            center = center,
            style = Stroke(width = strokeWidth)
        )

        // Progress arc with shape morphing effect
        val sweepAngle = animatedProgress.value * 360f
        drawArc(
            color = color,
            startAngle = -90f,
            sweepAngle = sweepAngle,
            useCenter = false,
            topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
            size = Size(canvasSize - strokeWidth, canvasSize - strokeWidth),
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )
    }
}

@Composable
fun AnimatedMorphingRing(
    modifier: Modifier = Modifier,
    color: Color = PureWhite,
    strokeWidth: Float = 3f,
    size: Float = 64f
) {
    val infiniteTransition = rememberInfiniteTransition(label = "morphing_ring")

    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = MaterialStandardEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    val morphProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = AnimationDuration.SHAPE_MORPH,
                easing = MaterialStandardEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "morph"
    )

    Canvas(modifier = modifier.size(size.dp)) {
        val canvasSize = size
        val center = Offset(canvasSize / 2, canvasSize / 2)

        // Morph between circle and rounded square
        val baseRadius = (canvasSize - strokeWidth) / 2
        val radiusVariation = baseRadius * 0.1f * kotlin.math.sin(morphProgress * Math.PI.toFloat() * 2)
        val radius = baseRadius + radiusVariation

        drawCircle(
            color = color.copy(alpha = 0.8f),
            radius = radius,
            center = center,
            style = Stroke(width = strokeWidth)
        )

        // Rotating accent
        val accentAngle = Math.toRadians(rotation.toDouble())
        val accentX = center.x + kotlin.math.cos(accentAngle).toFloat() * radius
        val accentY = center.y + kotlin.math.sin(accentAngle).toFloat() * radius

        drawCircle(
            color = color,
            radius = strokeWidth * 1.5f,
            center = Offset(accentX, accentY)
        )
    }
}

@Composable
fun ShapeMorphingButton(
    modifier: Modifier = Modifier,
    isPressed: Boolean = false,
    content: @Composable () -> Unit
) {
    val cornerRadius = remember { Animatable(28f) }

    LaunchedEffect(isPressed) {
        launch {
            cornerRadius.animateTo(
                targetValue = if (isPressed) 16f else 28f,
                animationSpec = FluidSpring
            )
        }
    }

    Box(
        modifier = modifier
        // Shape morphing applied via graphics layer in actual implementation
    ) {
        content()
    }
}

@Composable
fun PulsingDot(
    modifier: Modifier = Modifier,
    color: Color = PureWhite,
    size: Float = 8f
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")

    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = MaterialStandardEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = MaterialStandardEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Canvas(modifier = modifier.size((size * 2).dp)) {
        drawCircle(
            color = color.copy(alpha = alpha),
            radius = (size * scale),
            center = center
        )
    }
}

@Composable
fun MorphingGeometricShape(
    modifier: Modifier = Modifier,
    sides: Int = 6,
    color: Color = PureWhite,
    size: Float = 48f
) {
    val infiniteTransition = rememberInfiniteTransition(label = "morph_shape")

    val morphProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = AnimationDuration.SHAPE_MORPH * 2,
                easing = MaterialStandardEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "morph"
    )

    Canvas(modifier = modifier.size(size.dp)) {
        val canvasSize = size
        val centerX = canvasSize / 2
        val centerY = canvasSize / 2
        val radius = canvasSize * 0.4f

        // Interpolate between circle and polygon
        val path = androidx.compose.ui.graphics.Path()
        val actualSides = 3 + (sides - 3) * morphProgress.toInt().coerceIn(3, sides)

        for (i in 0 until actualSides) {
            val angle = (2 * Math.PI * i / actualSides - Math.PI / 2).toFloat()
            val x = centerX + radius * kotlin.math.cos(angle)
            val y = centerY + radius * kotlin.math.sin(angle)

            if (i == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }
        path.close()

        drawPath(
            path = path,
            color = color.copy(alpha = 0.8f),
            style = Stroke(width = 2f)
        )
    }
}

@Preview
@Composable
private fun MorphingProgressIndicatorPreview() {
    MorphingProgressIndicator(progress = 0.6f)
}

@Preview
@Composable
private fun AnimatedMorphingRingPreview() {
    AnimatedMorphingRing()
}

@Preview
@Composable
private fun PulsingDotPreview() {
    PulsingDot()
}
