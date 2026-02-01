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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.ai.edge.gallery.ui.theme.GlassmorphismHeavy
import com.google.ai.edge.gallery.ui.theme.GlassmorphismLight
import com.google.ai.edge.gallery.ui.theme.GlassmorphismMedium
import com.google.ai.edge.gallery.ui.theme.PureBlack
import com.google.ai.edge.gallery.ui.theme.PureWhite

/**
 * Glassmorphism Components
 * Pure monochrome glassmorphism effects
 * Z-depth layering with 0px to 32dp elevation
 */

/**
 * Glassmorphic surface with optimized blur rendering.
 *
 * OPTIMIZATION: Caches paint objects and uses graphicsLayer for hardware acceleration.
 * The paint is remembered across recompositions to avoid object churn.
 */
@Composable
fun GlassmorphicSurface(
    modifier: Modifier = Modifier,
    blurRadius: Dp = 20.dp,
    backgroundAlpha: Float = 0.08f,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(16.dp),
    content: @Composable BoxScope.() -> Unit
) {
    val density = LocalDensity.current
    val blurRadiusPx = with(density) { blurRadius.toPx() }
    
    // Cache paint object to avoid recreation on every recomposition
    val paint = remember(blurRadiusPx, backgroundAlpha) {
        Paint().apply {
            asFrameworkPaint().apply {
                isAntiAlias = true
                color = android.graphics.Color.argb(
                    (backgroundAlpha * 255).toInt(),
                    255, 255, 255
                )
                maskFilter = android.graphics.BlurMaskFilter(
                    blurRadiusPx,
                    android.graphics.BlurMaskFilter.Blur.NORMAL
                )
            }
        }
    }
    
    // Cache background color to avoid recalculation
    val backgroundColor = remember(backgroundAlpha) {
        PureWhite.copy(alpha = backgroundAlpha)
    }

    Box(
        modifier = modifier
            .graphicsLayer {
                // Enable hardware layer for blur effect
                // Using alpha close to 1.0 but not exactly 1.0 to trigger hardware layer
                this.alpha = 0.99f
                // Enable hardware acceleration
                this.compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen
            }
            .drawBehind {
                drawIntoCanvas { canvas ->
                    canvas.drawRect(Rect(0f, 0f, size.width, size.height), paint)
                }
            }
            .background(
                color = backgroundColor,
                shape = shape
            ),
        content = content
    )
}

@Composable
fun GlassmorphicCard(
    modifier: Modifier = Modifier,
    level: GlassmorphismLevel = GlassmorphismLevel.MEDIUM,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(16.dp),
    content: @Composable BoxScope.() -> Unit
) {
    val (blurRadius, backgroundAlpha, tonalElevation) = when (level) {
        GlassmorphismLevel.LIGHT -> Triple(10.dp, 0.05f, 1.dp)
        GlassmorphismLevel.MEDIUM -> Triple(20.dp, 0.08f, 2.dp)
        GlassmorphismLevel.HEAVY -> Triple(40.dp, 0.16f, 4.dp)
    }

    Surface(
        modifier = modifier,
        shape = shape,
        color = PureWhite.copy(alpha = backgroundAlpha),
        tonalElevation = tonalElevation,
        shadowElevation = 0.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .blur(blurRadius)
                .background(PureBlack.copy(alpha = 0.01f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(PureWhite.copy(alpha = backgroundAlpha))
            ) {
                content()
            }
        }
    }
}

@Composable
fun BlurBackdrop(
    modifier: Modifier = Modifier,
    blurRadius: Dp = 40.dp,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .blur(blurRadius)
            .background(PureBlack.copy(alpha = 0.8f))
    ) {
        content()
    }
}

@Composable
fun GlassmorphicOverlay(
    modifier: Modifier = Modifier,
    blurAmount: Dp = 40.dp,
    backgroundColor: Color = PureBlack.copy(alpha = 0.9f),
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
            .blur(blurAmount)
    ) {
        content()
    }
}

enum class GlassmorphismLevel {
    LIGHT,   // Level 1: 8dp blur, 5% opacity
    MEDIUM,  // Level 2: 20dp blur, 8% opacity
    HEAVY    // Level 3: 40dp blur, 16% opacity
}

// === Predefined Glassmorphism Modifiers ===
fun Modifier.glassmorphic(
    level: GlassmorphismLevel = GlassmorphismLevel.MEDIUM,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(16.dp)
): Modifier {
    val alpha = when (level) {
        GlassmorphismLevel.LIGHT -> 0.05f
        GlassmorphismLevel.MEDIUM -> 0.08f
        GlassmorphismLevel.HEAVY -> 0.16f
    }

    return this
        .background(PureWhite.copy(alpha = alpha), shape)
        .graphicsLayer {
            // Use shadowAlpha to avoid conflict with the alpha parameter
            shadowElevation = 0f
            // Trigger hardware layer without using alpha property name
            this.alpha = 0.99f
        }
}

fun Modifier.glassmorphicLight(
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(16.dp)
): Modifier = glassmorphic(GlassmorphismLevel.LIGHT, shape)

fun Modifier.glassmorphicMedium(
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(16.dp)
): Modifier = glassmorphic(GlassmorphismLevel.MEDIUM, shape)

fun Modifier.glassmorphicHeavy(
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(16.dp)
): Modifier = glassmorphic(GlassmorphismLevel.HEAVY, shape)
