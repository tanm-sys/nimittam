/*
 * Copyright 2025 Tanmay Patil
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.google.ai.edge.gallery.ui.screens.capability

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.google.ai.edge.gallery.ui.components.AnimatedMorphingRing
import com.google.ai.edge.gallery.ui.theme.AnimationDuration
import com.google.ai.edge.gallery.ui.theme.Gray40
import com.google.ai.edge.gallery.ui.theme.Gray64
import com.google.ai.edge.gallery.ui.theme.Gray80
import com.google.ai.edge.gallery.ui.theme.MaterialStandardEasing
import com.google.ai.edge.gallery.ui.theme.NimittamTheme
import com.google.ai.edge.gallery.ui.theme.PureBlack
import com.google.ai.edge.gallery.ui.theme.PureWhite
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Model Capability Visualization Screen
 * Full-screen overlay, 90% black backdrop
 * 3D node network visualization (pure white wireframe)
 * "Reasoning" text with animated ellipsis
 * Shape-morphing progress ring
 */

enum class ProcessingStage {
    ANALYZING,
    REASONING,
    GENERATING,
    COMPLETE
}

@Composable
fun ModelCapabilityScreen(
    stage: ProcessingStage = ProcessingStage.REASONING,
    progress: Float = 0.6f,
    onComplete: () -> Unit = {}
) {
    val infiniteTransition = rememberInfiniteTransition(label = "capability")

    // Node network rotation
    val networkRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(30000),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    // Pulse animation for nodes
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    var showContent by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(100)
        showContent = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PureBlack.copy(alpha = 0.95f))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        AnimatedVisibility(
            visible = showContent,
            enter = fadeIn(animationSpec = tween(400))
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // 3D Node Network Visualization
                NodeNetworkVisualization(
                    modifier = Modifier.size(280.dp),
                    rotation = networkRotation,
                    pulseScale = pulseScale
                )

                Spacer(modifier = Modifier.height(48.dp))

                // Processing stage text with animated ellipsis
                AnimatedContent(
                    targetState = stage,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(300)) togetherWith
                                fadeOut(animationSpec = tween(200))
                    },
                    label = "stage"
                ) { currentStage ->
                    ProcessingStageText(stage = currentStage)
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Shape-morphing progress ring
                MorphingProgressRing(
                    progress = progress,
                    modifier = Modifier.size(64.dp)
                )
            }
        }
    }
}

@Composable
private fun NodeNetworkVisualization(
    modifier: Modifier = Modifier,
    rotation: Float,
    pulseScale: Float
) {
    Canvas(modifier = modifier) {
        val centerX = size.width / 2
        val centerY = size.height / 2
        val center = Offset(centerX, centerY)

        // Define node positions in 3D space (simplified to 2D projection)
        val nodes = listOf(
            Triple(0f, 0f, 0f),      // Center
            Triple(1f, 0f, 0f),      // Right
            Triple(-1f, 0f, 0f),     // Left
            Triple(0f, 1f, 0f),      // Top
            Triple(0f, -1f, 0f),     // Bottom
            Triple(0.7f, 0.7f, 0f),  // Top-right
            Triple(-0.7f, 0.7f, 0f), // Top-left
            Triple(0.7f, -0.7f, 0f), // Bottom-right
            Triple(-0.7f, -0.7f, 0f),// Bottom-left
            Triple(0f, 0f, 0.5f),    // Front (z-axis)
            Triple(0f, 0f, -0.5f)    // Back (z-axis)
        )

        val radius = size.minDimension * 0.35f
        val nodeRadius = 6f * pulseScale

        // Rotate nodes
        val rotationRad = rotation * (PI / 180f)
        val cosR = cos(rotationRad).toFloat()
        val sinR = sin(rotationRad).toFloat()

        val projectedNodes = nodes.map { (x, y, z) ->
            // Apply rotation around Y axis
            val rotX = x * cosR - z * sinR
            val rotZ = x * sinR + z * cosR

            // Simple perspective projection
            val scale = 1f + rotZ * 0.3f
            val projX = centerX + rotX * radius * scale
            val projY = centerY + y * radius * scale

            Triple(projX, projY, scale)
        }

        // Draw connections
        val connections = listOf(
            0 to 1, 0 to 2, 0 to 3, 0 to 4,  // Center connections
            0 to 9, 0 to 10,                  // Z-axis connections
            1 to 5, 1 to 7,                   // Right connections
            2 to 6, 2 to 8,                   // Left connections
            3 to 5, 3 to 6,                   // Top connections
            4 to 7, 4 to 8,                   // Bottom connections
            5 to 6, 7 to 8,                   // Cross connections
            9 to 3, 9 to 5, 9 to 6,           // Front connections
            10 to 4, 10 to 7, 10 to 8         // Back connections
        )

        connections.forEach { (i, j) ->
            val (x1, y1, s1) = projectedNodes[i]
            val (x2, y2, s2) = projectedNodes[j]

            // Fade lines based on average depth
            val avgScale = (s1 + s2) / 2
            val alpha = ((avgScale - 0.7f) / 0.6f).coerceIn(0.1f, 0.5f)

            drawLine(
                color = PureWhite.copy(alpha = alpha),
                start = Offset(x1, y1),
                end = Offset(x2, y2),
                strokeWidth = 1f,
                cap = StrokeCap.Round
            )
        }

        // Draw nodes
        projectedNodes.forEachIndexed { index, (x, y, scale) ->
            val alpha = ((scale - 0.7f) / 0.6f).coerceIn(0.3f, 1f)
            val size = if (index == 0) nodeRadius * 1.5f else nodeRadius

            // Outer glow
            drawCircle(
                color = PureWhite.copy(alpha = alpha * 0.3f),
                radius = size * 2f,
                center = Offset(x, y)
            )

            // Core node
            drawCircle(
                color = PureWhite.copy(alpha = alpha),
                radius = size,
                center = Offset(x, y)
            )
        }
    }
}

@Composable
private fun ProcessingStageText(stage: ProcessingStage) {
    val infiniteTransition = rememberInfiniteTransition(label = "ellipsis")

    val dotCount by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Restart
        ),
        label = "dots"
    )

    val stageText = when (stage) {
        ProcessingStage.ANALYZING -> "Analyzing"
        ProcessingStage.REASONING -> "Reasoning"
        ProcessingStage.GENERATING -> "Generating"
        ProcessingStage.COMPLETE -> "Complete"
    }

    val dots = ".".repeat(dotCount.toInt().coerceIn(0, 3))

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "$stageText$dots",
            style = MaterialTheme.typography.headlineMedium,
            color = PureWhite,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(8.dp))

        val subtext = when (stage) {
            ProcessingStage.ANALYZING -> "Understanding your request"
            ProcessingStage.REASONING -> "Processing context and logic"
            ProcessingStage.GENERATING -> "Crafting response"
            ProcessingStage.COMPLETE -> "Ready"
        }

        Text(
            text = subtext,
            style = MaterialTheme.typography.bodyMedium,
            color = Gray64
        )
    }
}

@Composable
private fun MorphingProgressRing(
    progress: Float,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "morph_ring")

    // Morph between circle and rounded square
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

    // Rotation
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Canvas(modifier = modifier) {
        val canvasSize = size.minDimension
        val center = Offset(size.width / 2, size.height / 2)
        val strokeWidth = 3f

        // Background ring
        drawCircle(
            color = Gray40.copy(alpha = 0.3f),
            radius = (canvasSize - strokeWidth) / 2,
            center = center,
            style = Stroke(width = strokeWidth)
        )

        // Progress arc
        val sweepAngle = progress * 360f

        // Morph the end cap shape
        val radiusVariation = 4f * morphProgress

        drawArc(
            color = PureWhite,
            startAngle = rotation - 90f,
            sweepAngle = sweepAngle,
            useCenter = false,
            topLeft = Offset(
                (size.width - canvasSize) / 2 + strokeWidth / 2,
                (size.height - canvasSize) / 2 + strokeWidth / 2
            ),
            size = androidx.compose.ui.geometry.Size(
                canvasSize - strokeWidth,
                canvasSize - strokeWidth
            ),
            style = Stroke(
                width = strokeWidth + radiusVariation,
                cap = StrokeCap.Round
            )
        )

        // Progress indicator dot
        val angleRad = Math.toRadians((rotation - 90f + sweepAngle).toDouble())
        val indicatorRadius = (canvasSize - strokeWidth) / 2
        val indicatorX = center.x + kotlin.math.cos(angleRad).toFloat() * indicatorRadius
        val indicatorY = center.y + kotlin.math.sin(angleRad).toFloat() * indicatorRadius

        drawCircle(
            color = PureWhite,
            radius = 4f + radiusVariation,
            center = Offset(indicatorX, indicatorY)
        )
    }
}

@Preview(device = "id:pixel_8")
@Composable
private fun ModelCapabilityScreenPreview() {
    NimittamTheme {
        ModelCapabilityScreen(
            stage = ProcessingStage.REASONING,
            progress = 0.65f
        )
    }
}
