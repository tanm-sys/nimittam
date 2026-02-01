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

package com.google.ai.edge.gallery.ui.screens.voice

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.google.ai.edge.gallery.ui.components.NoiseTexture
import com.google.ai.edge.gallery.ui.components.RealtimeTranscriptionWaveform
import com.google.ai.edge.gallery.ui.theme.AnimationDuration
import com.google.ai.edge.gallery.ui.theme.FluidSpring
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
 * Voice Input Mode Screen
 * Full-screen dark canvas
 * Pulsing circle orb morphing to square
 * Waveform with M3 expressive shapes
 * Real-time transcription
 */

enum class VoiceInputState {
    IDLE,
    LISTENING,
    PROCESSING,
    COMPLETE
}

@Composable
fun VoiceInputScreen(
    onDismiss: () -> Unit = {},
    onComplete: (String) -> Unit = {}
) {
    var state by remember { mutableStateOf(VoiceInputState.IDLE) }
    var audioLevel by remember { mutableFloatStateOf(0.3f) }
    var transcription by remember { mutableStateOf("") }

    // Simulate voice input
    LaunchedEffect(state) {
        when (state) {
            VoiceInputState.IDLE -> {
                delay(500)
                state = VoiceInputState.LISTENING
            }
            VoiceInputState.LISTENING -> {
                // Simulate audio levels
                repeat(50) {
                    audioLevel = 0.2f + Math.random().toFloat() * 0.6f
                    delay(100)
                }
                state = VoiceInputState.PROCESSING
            }
            VoiceInputState.PROCESSING -> {
                delay(800)
                transcription = "What's the weather like today?"
                state = VoiceInputState.COMPLETE
                delay(1000)
                onComplete(transcription)
            }
            VoiceInputState.COMPLETE -> {}
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PureBlack)
    ) {
        // Noise texture
        NoiseTexture(
            modifier = Modifier.fillMaxSize(),
            opacity = 0.01f
        )

        // Close button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            contentAlignment = Alignment.TopEnd
        ) {
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = Gray80,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        // Main content
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Morphing orb
            MorphingVoiceOrb(
                state = state,
                audioLevel = audioLevel,
                modifier = Modifier.size(200.dp)
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Status text
            AnimatedContent(
                targetState = state,
                transitionSpec = {
                    fadeIn(animationSpec = tween(300)) togetherWith
                            fadeOut(animationSpec = tween(200))
                },
                label = "status"
            ) { currentState ->
                val statusText = when (currentState) {
                    VoiceInputState.IDLE -> "Tap to speak"
                    VoiceInputState.LISTENING -> "Listening..."
                    VoiceInputState.PROCESSING -> "Processing..."
                    VoiceInputState.COMPLETE -> "Done"
                }

                Text(
                    text = statusText,
                    style = MaterialTheme.typography.headlineSmall,
                    color = PureWhite
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Waveform visualization
            if (state == VoiceInputState.LISTENING) {
                RealtimeTranscriptionWaveform(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 48.dp),
                    audioLevel = audioLevel
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Transcription text
            AnimatedContent(
                targetState = transcription,
                transitionSpec = {
                    fadeIn(animationSpec = tween(400)) togetherWith
                            fadeOut(animationSpec = tween(200))
                },
                label = "transcription"
            ) { text ->
                if (text.isNotEmpty()) {
                    Text(
                        text = """$text""",
                        style = MaterialTheme.typography.titleMedium,
                        color = Gray80,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 48.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun MorphingVoiceOrb(
    state: VoiceInputState,
    audioLevel: Float,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "orb")

    // Idle pulse
    val idlePulse by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = MaterialStandardEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    // Shape morph progress (0 = circle, 1 = square)
    val morphProgress = remember { Animatable(0f) }

    LaunchedEffect(state) {
        when (state) {
            VoiceInputState.LISTENING -> {
                morphProgress.animateTo(
                    targetValue = 0.3f,
                    animationSpec = tween(500, easing = MaterialStandardEasing)
                )
            }
            VoiceInputState.PROCESSING -> {
                morphProgress.animateTo(
                    targetValue = 0.7f,
                    animationSpec = tween(600, easing = MaterialStandardEasing)
                )
            }
            VoiceInputState.COMPLETE -> {
                morphProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(400, easing = MaterialStandardEasing)
                )
            }
            else -> {
                morphProgress.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(300)
                )
            }
        }
    }

    Canvas(modifier = modifier) {
        val centerX = size.width / 2
        val centerY = size.height / 2
        val baseRadius = size.minDimension * 0.35f

        // Calculate current size based on state and audio level
        val currentScale = when (state) {
            VoiceInputState.IDLE -> idlePulse
            VoiceInputState.LISTENING -> 1f + audioLevel * 0.3f
            VoiceInputState.PROCESSING -> 0.9f
            VoiceInputState.COMPLETE -> 0.8f
        }

        val radius = baseRadius * currentScale

        // Morph between circle and rounded square
        val morph = morphProgress.value
        val cornerRadius = radius * morph * 0.3f

        // Draw outer glow
        for (i in 3 downTo 1) {
            drawCircle(
                color = PureWhite.copy(alpha = 0.1f * i),
                radius = radius * (1f + i * 0.15f),
                center = Offset(centerX, centerY)
            )
        }

        // Draw main orb with morphed shape
        if (morph < 0.5f) {
            // Circle shape
            drawCircle(
                color = PureWhite.copy(alpha = 0.9f),
                radius = radius,
                center = Offset(centerX, centerY)
            )
        } else {
            // Rounded square shape
            val rect = Rect(
                centerX - radius,
                centerY - radius,
                centerX + radius,
                centerY + radius
            )

            val path = Path().apply {
                addRoundRect(
                    androidx.compose.ui.geometry.RoundRect(
                        rect = rect,
                        radiusX = cornerRadius,
                        radiusY = cornerRadius
                    )
                )
            }

            drawPath(
                path = path,
                color = PureWhite.copy(alpha = 0.9f)
            )
        }

        // Inner icon/mic indicator
        if (state == VoiceInputState.IDLE || state == VoiceInputState.LISTENING) {
            drawCircle(
                color = PureBlack,
                radius = radius * 0.3f,
                center = Offset(centerX, centerY)
            )
        }

        // Processing indicator ring
        if (state == VoiceInputState.PROCESSING) {
            drawCircle(
                color = PureBlack,
                radius = radius * 0.8f,
                center = Offset(centerX, centerY),
                style = Stroke(width = 4f)
            )
        }
    }
}

@Composable
private fun ExpressiveWaveform(
    modifier: Modifier = Modifier,
    audioLevel: Float
) {
    val infiniteTransition = rememberInfiniteTransition(label = "waveform")

    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2 * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(2000),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    Canvas(modifier = modifier) {
        val canvasWidth = size.width
        val canvasHeight = size.height
        val centerY = canvasHeight / 2

        // M3 expressive waveform with varying bar shapes
        val bars = 20
        val barWidth = canvasWidth / (bars * 1.5f)
        val gap = barWidth * 0.5f

        for (i in 0 until bars) {
            val x = i * (barWidth + gap) + barWidth / 2

            // Create expressive shape using sine waves
            val wave1 = sin(phase + i * 0.5f)
            val wave2 = cos(phase * 0.7f + i * 0.3f)
            val combinedWave = (wave1 + wave2) / 2f

            val barHeight = (0.3f + audioLevel * 0.7f + combinedWave * 0.2f) * canvasHeight * 0.8f

            // Vary the shape based on position
            val shapeFactor = sin(i * 0.3f)
            val topRadius = if (shapeFactor > 0) barWidth * 0.5f else 0f
            val bottomRadius = if (shapeFactor < 0) barWidth * 0.5f else 0f

            // Draw bar with rounded ends (M3 expressive)
            drawLine(
                color = PureWhite.copy(alpha = 0.6f + combinedWave * 0.2f),
                start = Offset(x, centerY - barHeight / 2),
                end = Offset(x, centerY + barHeight / 2),
                strokeWidth = barWidth,
                cap = StrokeCap.Round
            )
        }
    }
}

@Preview(device = "id:pixel_8")
@Composable
private fun VoiceInputScreenPreview() {
    NimittamTheme {
        VoiceInputScreen()
    }
}
