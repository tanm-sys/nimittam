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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.google.ai.edge.gallery.ui.theme.PureBlack
import com.google.ai.edge.gallery.ui.theme.PureWhite

/**
 * Scroll Indicators
 * Fade gradients for scrollable content
 * Material 3 expressive design
 */

@Composable
fun TopFadeIndicator(
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(200)),
        exit = fadeOut(animationSpec = tween(200)),
        modifier = modifier
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
        ) {
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        PureBlack.copy(alpha = 0.8f),
                        PureBlack.copy(alpha = 0f)
                    ),
                    startY = 0f,
                    endY = size.height
                ),
                topLeft = Offset(0f, 0f),
                size = size
            )
        }
    }
}

@Composable
fun BottomFadeIndicator(
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(200)),
        exit = fadeOut(animationSpec = tween(200)),
        modifier = modifier
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
        ) {
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        PureBlack.copy(alpha = 0f),
                        PureBlack.copy(alpha = 0.8f)
                    ),
                    startY = 0f,
                    endY = size.height
                ),
                topLeft = Offset(0f, 0f),
                size = size
            )
        }
    }
}

@Composable
fun ScrollIndicatorContainer(
    listState: LazyListState,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val showTopFade by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0
        }
    }

    val showBottomFade by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisibleItem < totalItems - 1
        }
    }

    Box(modifier = modifier) {
        content()

        TopFadeIndicator(
            visible = showTopFade,
            modifier = Modifier.align(Alignment.TopCenter)
        )

        BottomFadeIndicator(
            visible = showBottomFade,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}
