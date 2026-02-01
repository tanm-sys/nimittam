/*
 * Copyright 2025 Tanmay Patil
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.google.ai.edge.gallery.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.google.ai.edge.gallery.ui.theme.Gray64
import com.google.ai.edge.gallery.ui.theme.NimittamTheme
import com.google.ai.edge.gallery.ui.theme.PureWhite
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Typing Indicator Component
 * Animated dots for AI typing state
 * Material 3 expressive shapes
 */

@Composable
fun TypingIndicator(
    modifier: Modifier = Modifier,
    dotCount: Int = 3,
    dotSize: Float = 8f,
    dotSpacing: Float = 8f
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(dotSpacing.dp)
    ) {
        repeat(dotCount) { index ->
            TypingDot(
                index = index,
                dotSize = dotSize,
                delayMillis = index * 150
            )
        }
    }
}

@Composable
private fun TypingDot(
    index: Int,
    dotSize: Float,
    delayMillis: Int
) {
    val infiniteTransition = rememberInfiniteTransition(label = "typing_dot_$index")

    val scale by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, delayMillis = delayMillis),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, delayMillis = delayMillis),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Canvas(
        modifier = Modifier.size((dotSize * 1.5f).dp)
    ) {
        drawCircle(
            color = PureWhite.copy(alpha = alpha),
            radius = (dotSize * scale) / 2,
            center = center
        )
    }
}

@Preview
@Composable
private fun TypingIndicatorPreview() {
    NimittamTheme {
        TypingIndicator()
    }
}
