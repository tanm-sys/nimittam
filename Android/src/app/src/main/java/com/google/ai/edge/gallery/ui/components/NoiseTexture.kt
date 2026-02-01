/*
 * Copyright 2025 Tanmay Patil
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.google.ai.edge.gallery.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.tooling.preview.Preview
import com.google.ai.edge.gallery.ui.theme.PureWhite
import kotlin.random.Random

/**
 * Noise Texture Component
 * Subtle noise texture at 1-2% opacity for depth
 * No generic gradient backgrounds - only noise textures
 */

/**
 * Static noise texture that caches points across recompositions.
 * Uses remember to avoid regenerating noise points on every frame.
 */
@Composable
fun NoiseTexture(
    modifier: Modifier = Modifier,
    opacity: Float = 0.015f,
    density: Float = 0.5f,
    baseColor: Color = PureWhite,
    seed: Int = 42
) {
    // Cache noise points - only regenerate if seed or density changes
    val noisePoints = remember(seed, density) {
        generateNoisePoints(density, seed)
    }
    
    // Cache the color with opacity to avoid recalculation
    val drawColor = remember(baseColor, opacity) {
        baseColor.copy(alpha = opacity)
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        drawNoise(noisePoints, drawColor)
    }
}

/**
 * Animated noise texture with frame-based caching.
 * OPTIMIZATION: Uses remember with frame key to cache points per frame,
 * avoiding regeneration during unrelated recompositions.
 */
@Composable
fun AnimatedNoiseTexture(
    modifier: Modifier = Modifier,
    opacity: Float = 0.015f,
    density: Float = 0.5f,
    baseColor: Color = PureWhite,
    frame: Int = 0
) {
    // Cache noise points per frame - only regenerate when frame changes
    val noisePoints = remember(frame, density) {
        generateNoisePoints(density, frame)
    }
    
    // Cache the color with opacity
    val drawColor = remember(baseColor, opacity) {
        baseColor.copy(alpha = opacity)
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        drawNoise(noisePoints, drawColor)
    }
}

private fun generateNoisePoints(density: Float, seed: Int): List<NoisePoint> {
    val random = Random(seed)
    val count = (1000 * density).toInt()

    return List(count) {
        NoisePoint(
            x = random.nextFloat(),
            y = random.nextFloat(),
            alpha = random.nextFloat() * 0.5f + 0.5f,
            size = random.nextFloat() * 1.5f + 0.5f
        )
    }
}

private fun DrawScope.drawNoise(points: List<NoisePoint>, color: Color) {
    val width = size.width
    val height = size.height

    points.forEach { point ->
        drawCircle(
            color = color.copy(alpha = color.alpha * point.alpha),
            radius = point.size,
            center = Offset(point.x * width, point.y * height)
        )
    }
}

data class NoisePoint(
    val x: Float,
    val y: Float,
    val alpha: Float,
    val size: Float
)

/**
 * Mesh Gradient Background with Noise
 * Animated mesh gradient at 2% opacity over pure black
 */
@Composable
fun MeshGradientBackground(
    modifier: Modifier = Modifier,
    time: Float = 0f,
    opacity: Float = 0.02f
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        // Create subtle mesh gradient effect
        val points = listOf(
            Offset(0f, 0f),
            Offset(width * 0.5f, height * 0.3f + kotlin.math.sin(time) * 50f),
            Offset(width, 0f),
            Offset(width * 0.3f, height * 0.7f + kotlin.math.cos(time * 0.7f) * 40f),
            Offset(width * 0.7f, height * 0.5f + kotlin.math.sin(time * 0.5f) * 60f),
            Offset(0f, height),
            Offset(width * 0.5f, height),
            Offset(width, height)
        )

        // Draw gradient mesh with very low opacity
        points.forEachIndexed { index, point ->
            val alpha = (kotlin.math.sin(time + index) + 1f) * 0.5f * opacity
            drawCircle(
                color = PureWhite.copy(alpha = alpha),
                radius = width * 0.4f,
                center = point
            )
        }
    }
}

@Preview
@Composable
private fun NoiseTexturePreview() {
    Box(modifier = Modifier.background(Color.Black)) {
        NoiseTexture(opacity = 0.03f)
    }
}
