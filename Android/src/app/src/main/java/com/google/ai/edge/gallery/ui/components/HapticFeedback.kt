/*
 * Copyright 2025 Tanmay Patil
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.google.ai.edge.gallery.ui.components

import androidx.compose.foundation.Indication
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import kotlinx.coroutines.flow.collectLatest

/**
 * Haptic Feedback Component
 * UI feedback effects for user interactions
 * Simple wrapper around Compose's built-in haptic feedback
 */

/**
 * A composable wrapper that adds haptic feedback to any clickable element.
 *
 * @param hapticType The type of haptic feedback to trigger
 * @param enabled Whether haptic feedback is enabled
 * @param onClick Callback when the element is clicked
 * @param content The content to display
 */
@Composable
fun HapticFeedback(
    hapticType: HapticFeedbackType = HapticFeedbackType.TextHandleMove,
    enabled: Boolean = true,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    val hapticFeedback = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }

    var isPressed by remember { mutableStateOf(false) }

    LaunchedEffect(interactionSource, enabled) {
        if (!enabled) return@LaunchedEffect

        interactionSource.interactions.collectLatest { interaction ->
            when (interaction) {
                is PressInteraction.Press -> {
                    isPressed = true
                    hapticFeedback.performHapticFeedback(hapticType)
                }
                is PressInteraction.Release, is PressInteraction.Cancel -> {
                    isPressed = false
                }
            }
        }
    }

    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick
            )
    ) {
        content()
    }
}

/**
 * Modifier extension that adds haptic feedback to clickable elements.
 *
 * @param type The type of haptic feedback to trigger
 * @param enabled Whether haptic feedback is enabled
 * @param onClickLabel Optional label for accessibility
 * @param role Optional role for accessibility
 * @param indication Optional custom indication for the clickable
 * @param interactionSource Optional custom interaction source
 */
fun Modifier.hapticClickable(
    type: HapticFeedbackType = HapticFeedbackType.TextHandleMove,
    enabled: Boolean = true,
    onClickLabel: String? = null,
    role: Role? = null,
    indication: Indication? = null,
    interactionSource: MutableInteractionSource? = null,
    onClick: () -> Unit
): Modifier = composed {
    val hapticFeedback = LocalHapticFeedback.current
    val actualInteractionSource = interactionSource ?: remember { MutableInteractionSource() }

    LaunchedEffect(actualInteractionSource, enabled) {
        if (!enabled) return@LaunchedEffect

        actualInteractionSource.interactions.collectLatest { interaction ->
            if (interaction is PressInteraction.Press) {
                hapticFeedback.performHapticFeedback(type)
            }
        }
    }

    this.clickable(
        interactionSource = actualInteractionSource,
        indication = indication ?: LocalIndication.current,
        enabled = enabled,
        onClickLabel = onClickLabel,
        role = role,
        onClick = onClick
    )
}

/**
 * A composable that provides haptic feedback for long-press interactions.
 *
 * @param hapticType The type of haptic feedback to trigger
 * @param enabled Whether haptic feedback is enabled
 * @param onLongClick Callback when long press occurs
 * @param content The content to display
 */
@Composable
fun HapticLongPressFeedback(
    hapticType: HapticFeedbackType = HapticFeedbackType.LongPress,
    enabled: Boolean = true,
    onLongClick: () -> Unit,
    content: @Composable () -> Unit
) {
    val hapticFeedback = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }

    LaunchedEffect(interactionSource, enabled) {
        if (!enabled) return@LaunchedEffect

        interactionSource.interactions.collectLatest { interaction ->
            when (interaction) {
                is PressInteraction.Press -> {
                    kotlinx.coroutines.delay(400)
                    hapticFeedback.performHapticFeedback(hapticType)
                }
                else -> { /* no-op */ }
            }
        }
    }

    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = { }
            )
    ) {
        content()
    }
}
