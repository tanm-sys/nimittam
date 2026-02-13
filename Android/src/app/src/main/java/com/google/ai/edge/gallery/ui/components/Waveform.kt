/*
 * Copyright 2025 Tanmay Patil
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.google.ai.edge.gallery.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.google.ai.edge.gallery.ui.theme.Gray40
import com.google.ai.edge.gallery.ui.theme.Gray64
import com.google.ai.edge.gallery.ui.theme.PureWhite
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * Waveform Components
 * DeepMind style animated waveform visuals
 * M3 expressive shapes
 */

@Composable
fun AudioWaveform(
    modifier: Modifier = Modifier,
    amplitudes: List<Float> = List(20) { 0.3f + Random.nextFloat() * 0.7f },
    color: Color = PureWhite,
    barWidth: Float = 4f,
    barGap: Float = 4f,
    isAnimated: Boolean = true
) {
    val infiniteTransition = rememberInfiniteTransition(label = "waveform")

    val animatedProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2 * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(2000),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave"
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
    ) {
        val canvasWidth = size.width
        val canvasHeight = size.height
        val centerY = canvasHeight / 2

        val totalBarWidth = barWidth + barGap
        val numBars = ((canvasWidth + barGap) / totalBarWidth).toInt()

        for (i in 0 until numBars.coerceAtMost(amplitudes.size)) {
            val amplitude = amplitudes.getOrElse(i) { 0.5f }
            val animatedAmplitude = if (isAnimated) {
                amplitude * (0.7f + 0.3f * sin(animatedProgress + i * 0.3f))
            } else {
                amplitude
            }

            val barHeight = animatedAmplitude * canvasHeight * 0.8f
            val x = i * totalBarWidth + barWidth / 2

            drawLine(
                color = color.copy(alpha = 0.6f + animatedAmplitude * 0.4f),
                start = Offset(x, centerY - barHeight / 2),
                end = Offset(x, centerY + barHeight / 2),
                strokeWidth = barWidth,
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
fun VoiceInputOrb(
    modifier: Modifier = Modifier,
    isListening: Boolean = true,
    color: Color = PureWhite
) {
    val infiniteTransition = rememberInfiniteTransition(label = "orb")

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    // Simulated amplitude for visual effect
    val amplitude by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "amplitude"
    )

    Canvas(modifier = modifier) {
        val centerX = size.width / 2
        val centerY = size.height / 2
        val baseRadius = size.minDimension * 0.3f
        val animatedRadius = baseRadius * (1f + amplitude * 0.3f) * if (isListening) pulseScale else 1f

        // Outer ring
        drawCircle(
            color = color.copy(alpha = 0.3f),
            radius = animatedRadius * 1.3f,
            center = Offset(centerX, centerY)
        )

        // Main orb
        drawCircle(
            color = color.copy(alpha = 0.8f + amplitude * 0.2f),
            radius = animatedRadius,
            center = Offset(centerX, centerY)
        )

        // Inner glow
        drawCircle(
            color = color.copy(alpha = 0.4f),
            radius = animatedRadius * 0.6f,
            center = Offset(centerX, centerY)
        )
    }
}

@Composable
fun OfflineStatusWaveform(
    modifier: Modifier = Modifier,
    isActive: Boolean = true
) {
    val infiniteTransition = rememberInfiniteTransition(label = "offline_waveform")

    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2 * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(3000),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(32.dp)
    ) {
        val canvasWidth = size.width
        val canvasHeight = size.height
        val centerY = canvasHeight / 2

        val path = Path()
        path.moveTo(0f, centerY)

        val points = 100
        for (i in 0..points) {
            val x = (i / points.toFloat()) * canvasWidth
            val normalizedX = i / points.toFloat()

            val wave1 = sin(phase + normalizedX * 4 * PI.toFloat())
            val wave2 = sin(phase * 1.5f + normalizedX * 8 * PI.toFloat()) * 0.5f
            val wave3 = sin(phase * 0.5f + normalizedX * 2 * PI.toFloat()) * 0.3f

            val combinedWave = (wave1 + wave2 + wave3) / 3f
            val y = centerY + combinedWave * canvasHeight * 0.4f

            if (i == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }

        drawPath(
            path = path,
            color = if (isActive) PureWhite else Gray40,
            style = Stroke(width = 2f, cap = StrokeCap.Round)
        )

        drawRect(
            color = Gray64.copy(alpha = 0.1f),
            size = Size(canvasWidth, canvasHeight)
        )
    }
}

@Composable
fun RealtimeTranscriptionWaveform(
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "transcription_waveform")
    
    // Simulated audio level for visual effect
    val audioLevel by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(400),
            repeatMode = RepeatMode.Reverse
        ),
        label = "audio_level"
    )

    val barCount = 30
    val barValues = remember(audioLevel) {
        List(barCount) { index ->
            // Create wave-like pattern based on index and simulated audio level
            val position = index / barCount.toFloat()
            val wave = sin(position * PI.toFloat() * 2)
            val randomVariation = Random.nextFloat() * 0.3f
            ((audioLevel * 0.7f + randomVariation) * (0.5f + wave * 0.5f)).coerceIn(0.1f, 1f)
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
    ) {
        val canvasWidth = size.width
        val canvasHeight = size.height
        val centerY = canvasHeight / 2

        val barWidth = canvasWidth / (barCount * 1.5f)
        val gap = barWidth * 0.5f

        barValues.forEachIndexed { index, value ->
            val barHeight = value * canvasHeight * 0.9f
            val x = index * (barWidth + gap) + barWidth / 2

            val topY = centerY - barHeight / 2
            val bottomY = centerY + barHeight / 2

            drawLine(
                color = PureWhite.copy(alpha = 0.6f + value * 0.4f),
                start = Offset(x, topY),
                end = Offset(x, bottomY),
                strokeWidth = barWidth,
                cap = StrokeCap.Round
            )
        }
    }
}

@Preview
@Composable
private fun AudioWaveformPreview() {
    AudioWaveform()
}

@Preview
@Composable
private fun OfflineStatusWaveformPreview() {
    OfflineStatusWaveform()
}

@Preview
@Composable
private fun RealtimeTranscriptionWaveformPreview() {
    RealtimeTranscriptionWaveform()
}
