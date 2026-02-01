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

package com.google.ai.edge.gallery.ui.theme

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.pow

/**
 * Nimittam Shape System
 * Material 3 Expressive with sharp corners and superellipse curves
 * Shape Language: Material 3 Expressive (sharp 0dp corners + 28dp superellipse curves)
 */

// === Standard Corner Radii ===
val CornerNone = 0.dp
val CornerExtraSmall = 4.dp
val CornerSmall = 8.dp
val CornerMedium = 12.dp
val CornerLarge = 16.dp
val CornerExtraLarge = 28.dp  // Superellipse curve
val CornerFull = 1000.dp  // Fully rounded/pill

// === Material 3 Expressive Shapes ===
val NimittamShapes = Shapes(
    extraSmall = RoundedCornerShape(CornerExtraSmall),
    small = RoundedCornerShape(CornerSmall),
    medium = RoundedCornerShape(CornerMedium),
    large = RoundedCornerShape(CornerLarge),
    extraLarge = RoundedCornerShape(CornerExtraLarge)
)

// === Custom Shape: Sharp (0dp corners) ===
val SharpShape = RoundedCornerShape(CornerNone)

// === Custom Shape: Superellipse (28dp all corners) ===
val SuperellipseRoundedShape = RoundedCornerShape(CornerExtraLarge)

// === Custom Shape: Mixed (top superellipse, bottom sharp) ===
val MixedTopSuperellipseShape = RoundedCornerShape(
    topStart = CornerExtraLarge,
    topEnd = CornerExtraLarge,
    bottomStart = CornerNone,
    bottomEnd = CornerNone
)

// === Custom Shape: Mixed (left sharp, right superellipse) ===
val MixedLeftSharpShape = RoundedCornerShape(
    topStart = CornerNone,
    topEnd = CornerExtraLarge,
    bottomStart = CornerNone,
    bottomEnd = CornerExtraLarge
)

// === Custom Shape: Mixed (left superellipse, right sharp) ===
val MixedRightSharpShape = RoundedCornerShape(
    topStart = CornerExtraLarge,
    topEnd = CornerNone,
    bottomStart = CornerExtraLarge,
    bottomEnd = CornerNone
)

// === Organic Superellipse Shape (Squircle) ===
class SuperellipseShape(
    private val cornerRadius: Dp = CornerExtraLarge,
    private val power: Float = 4f
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val radius = with(density) { cornerRadius.toPx() }
        val width = size.width
        val height = size.height

        return Outline.Generic(
            Path().apply {
                // Create superellipse path
                val n = power
                val a = width / 2f
                val b = height / 2f
                val centerX = width / 2f
                val centerY = height / 2f

                // Number of points to approximate the curve
                val points = 100

                moveTo(centerX + a, centerY)

                for (i in 1..points) {
                    val t = (i / points.toFloat()) * 2 * Math.PI
                    val cosT = kotlin.math.cos(t)
                    val sinT = kotlin.math.sin(t)

                    val x = centerX + a * kotlin.math.sign(cosT) * kotlin.math.abs(cosT).pow((2.0 / n.toDouble())).toFloat()
                    val y = centerY + b * kotlin.math.sign(sinT) * kotlin.math.abs(sinT).pow((2.0 / n.toDouble())).toFloat()
                    lineTo(x.toFloat(), y.toFloat())
                }

                close()
            }
        )
    }
}

// === Morphing Shape for Animations ===
class MorphingShape(
    private val startShape: CornerBasedShape,
    private val endShape: CornerBasedShape,
    private val progress: Float
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        // Interpolate between shapes based on progress (0.0 to 1.0)
        val interpolated = interpolateShapes(startShape, endShape, progress, density)
        return interpolated.createOutline(size, layoutDirection, density)
    }
}

private fun interpolateShapes(
    start: CornerBasedShape,
    end: CornerBasedShape,
    progress: Float,
    density: Density
): CornerBasedShape {
    // This is a simplified interpolation
    // In practice, you'd extract corner values and interpolate them
    return if (progress < 0.5f) start else end
}

// === Chat Message Shapes ===
val UserMessageShape = RoundedCornerShape(
    topStart = CornerMedium,
    topEnd = CornerNone,
    bottomStart = CornerMedium,
    bottomEnd = CornerMedium
)

val AiMessageShape = RoundedCornerShape(
    topStart = CornerNone,
    topEnd = CornerExtraLarge,
    bottomStart = CornerExtraLarge,
    bottomEnd = CornerExtraLarge
)

// === Card Shapes by Model Type ===
val LiteModelCardShape = SharpShape  // 0dp corners
val ProModelCardShape = MixedTopSuperellipseShape  // Mixed corners
val UltraModelCardShape = SuperellipseRoundedShape  // Organic superellipse

// === Input Composer Shape ===
val InputComposerCollapsedShape = RoundedCornerShape(CornerFull)
val InputComposerExpandedShape = RoundedCornerShape(CornerLarge)

// === Button Shapes ===
val ButtonPrimaryShape = RoundedCornerShape(CornerSmall)
val ButtonSecondaryShape = RoundedCornerShape(CornerExtraSmall)
val FabShape = SuperellipseRoundedShape

// === Menu/Dialog Shapes ===
val MenuShape = RoundedCornerShape(CornerLarge)
val DialogShape = RoundedCornerShape(CornerExtraLarge)
val BottomSheetShape = RoundedCornerShape(
    topStart = CornerExtraLarge,
    topEnd = CornerExtraLarge,
    bottomStart = CornerNone,
    bottomEnd = CornerNone
)
