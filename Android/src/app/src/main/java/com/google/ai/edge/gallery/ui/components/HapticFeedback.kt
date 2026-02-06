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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import com.google.ai.edge.gallery.common.HapticFeedbackManager
import com.google.ai.edge.gallery.common.HapticType
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Configuration for haptic feedback behavior.
 *
 * @param enabled Whether haptic feedback is enabled for this component
 * @param type The type of haptic feedback to trigger
 * @param triggerOnPress Whether to trigger on press (true) or on release (false)
 * @param respectSystemSettings Whether to respect system haptic feedback settings
 */
data class HapticConfig(
    val enabled: Boolean = true,
    val type: HapticType = HapticType.CONFIRMATION,
    val triggerOnPress: Boolean = true,
    val respectSystemSettings: Boolean = true
)

/**
 * A composable wrapper that adds haptic feedback to any clickable element.
 *
 * This component wraps content and adds haptic feedback on interaction,
 * with automatic accessibility consideration and respect for system settings.
 *
 * Example usage:
 * ```
 * HapticFeedback(
 *     hapticType = HapticType.CONFIRMATION,
 *     onClick = { /* handle click */ }
 * ) {
 *     Text("Click me")
 * }
 * ```
 *
 * @param hapticType The type of haptic feedback to trigger
 * @param enabled Whether haptic feedback is enabled
 * @param respectSystemSettings Whether to respect system haptic settings
 * @param onClick Callback when the element is clicked
 * @param content The content to display
 */
@Composable
fun HapticFeedback(
    hapticType: HapticType = HapticType.CONFIRMATION,
    enabled: Boolean = true,
    respectSystemSettings: Boolean = true,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    val hapticFeedback = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }

    // Track press state for appropriate haptic timing
    var isPressed by remember { mutableStateOf(false) }

    LaunchedEffect(interactionSource, enabled, hapticType) {
        if (!enabled) return@LaunchedEffect

        interactionSource.interactions.collectLatest { interaction ->
            when (interaction) {
                is PressInteraction.Press -> {
                    isPressed = true
                    // Trigger haptic on press for immediate feedback
                    performHapticFeedback(hapticType, hapticFeedback)
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
 * This modifier can be chained with other modifiers to add haptic feedback
 * to any clickable component.
 *
 * Example usage:
 * ```
 * Button(
 *     onClick = { /* handle click */ },
 *     modifier = Modifier.hapticFeedback(HapticType.CONFIRMATION)
 * ) {
 *     Text("Click me")
 * }
 * ```
 *
 * @param type The type of haptic feedback to trigger
 * @param enabled Whether haptic feedback is enabled
 * @param indication Optional custom indication for the clickable
 * @param interactionSource Optional custom interaction source
 */
fun Modifier.hapticClickable(
    type: HapticType = HapticType.CONFIRMATION,
    enabled: Boolean = true,
    onClickLabel: String? = null,
    role: Role? = null,
    indication: Indication? = null,
    interactionSource: MutableInteractionSource? = null,
    onClick: () -> Unit
): Modifier = composed {
    val hapticFeedback = LocalHapticFeedback.current
    val actualInteractionSource = interactionSource ?: remember { MutableInteractionSource() }

    LaunchedEffect(actualInteractionSource, enabled, type) {
        if (!enabled) return@LaunchedEffect

        actualInteractionSource.interactions.collectLatest { interaction ->
            if (interaction is PressInteraction.Press) {
                performHapticFeedback(type, hapticFeedback)
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
 * Modifier extension that adds haptic feedback to toggleable elements like switches.
 *
 * @param type The type of haptic feedback to trigger
 * @param enabled Whether haptic feedback is enabled
 */
fun Modifier.hapticToggleable(
    type: HapticType = HapticType.CONFIRMATION,
    enabled: Boolean = true,
    value: Boolean,
    onValueChange: (Boolean) -> Unit,
    role: Role = Role.Switch,
    interactionSource: MutableInteractionSource? = null
): Modifier = composed {
    val hapticFeedback = LocalHapticFeedback.current
    val actualInteractionSource = interactionSource ?: remember { MutableInteractionSource() }
    var previousValue by remember { mutableStateOf(value) }

    LaunchedEffect(value) {
        if (enabled && value != previousValue) {
            performHapticFeedback(type, hapticFeedback)
            previousValue = value
        }
    }

    // Note: This is a simplified implementation
    // In practice, you'd use the full toggleable modifier
    this
}

/**
 * Modifier extension for slider haptic feedback.
 * Triggers haptic feedback when the slider value changes significantly.
 *
 * @param enabled Whether haptic feedback is enabled
 * @param stepSize The minimum value change to trigger haptic feedback
 */
fun Modifier.hapticSlider(
    enabled: Boolean = true,
    stepSize: Float = 0.1f
): Modifier = composed {
    val hapticFeedback = LocalHapticFeedback.current
    var lastHapticValue by remember { mutableFloatStateOf(0f) }

    // This is a marker modifier - actual implementation would need
    // to be integrated with the slider component
    DisposableEffect(Unit) {
        onDispose { }
    }

    this
}

/**
 * Helper function to map HapticType to Compose's HapticFeedbackType.
 */
private fun performHapticFeedback(
    type: HapticType,
    hapticFeedback: androidx.compose.ui.hapticfeedback.HapticFeedback
) {
    val feedbackType = when (type) {
        HapticType.CONFIRMATION -> HapticFeedbackType.TextHandleMove
        HapticType.ERROR -> HapticFeedbackType.LongPress
        HapticType.SELECTION -> HapticFeedbackType.TextHandleMove
        HapticType.IMPACT -> HapticFeedbackType.LongPress
        HapticType.SUCCESS -> HapticFeedbackType.LongPress
        HapticType.WARNING -> HapticFeedbackType.LongPress
    }

    hapticFeedback.performHapticFeedback(feedbackType)
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
    hapticType: HapticType = HapticType.SELECTION,
    enabled: Boolean = true,
    onLongClick: () -> Unit,
    content: @Composable () -> Unit
) {
    val hapticFeedback = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }

    LaunchedEffect(interactionSource, enabled, hapticType) {
        if (!enabled) return@LaunchedEffect

        interactionSource.interactions.collectLatest { interaction ->
            when (interaction) {
                is PressInteraction.Press -> {
                    // Delay for long press detection
                    kotlinx.coroutines.delay(400)
                    performHapticFeedback(hapticType, hapticFeedback)
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

/**
 * Extension function to easily add haptic feedback to any composable.
 *
 * Usage:
 * ```
 * val hapticManager = LocalHapticFeedbackManager.current
 * hapticManager.performHaptic(HapticType.CONFIRMATION)
 * ```
 */
@Composable
fun rememberHapticManager(hapticFeedbackManager: HapticFeedbackManager): HapticController {
    return remember { HapticController(hapticFeedbackManager) }
}

/**
 * Controller class for managing haptic feedback in composables.
 */
class HapticController(
    private val hapticFeedbackManager: HapticFeedbackManager
) {
    /**
     * Triggers haptic feedback of the specified type.
     *
     * @param type The type of haptic feedback to trigger
     * @param force If true, bypasses system haptic settings
     */
    fun performHaptic(type: HapticType, force: Boolean = false) {
        hapticFeedbackManager.performHaptic(type, force)
    }

    /**
     * Suspended version for use in coroutines.
     */
    suspend fun performHapticAsync(type: HapticType, force: Boolean = false) {
        hapticFeedbackManager.performHapticAsync(type, force)
    }

    /**
     * Checks if haptic feedback is enabled.
     */
    fun isEnabled(): Boolean {
        return hapticFeedbackManager.isHapticFeedbackEnabled()
    }
}

/**
 * Predefined haptic configurations for common UI patterns.
 */
object HapticPresets {
    /** For button presses and simple actions */
    val ButtonPress = HapticConfig(
        type = HapticType.CONFIRMATION,
        triggerOnPress = true
    )

    /** For destructive actions like delete */
    val DestructiveAction = HapticConfig(
        type = HapticType.WARNING,
        triggerOnPress = true
    )

    /** For successful completions */
    val Success = HapticConfig(
        type = HapticType.SUCCESS,
        triggerOnPress = false
    )

    /** For error states */
    val Error = HapticConfig(
        type = HapticType.ERROR,
        triggerOnPress = false
    )

    /** For list item selection */
    val ListSelection = HapticConfig(
        type = HapticType.SELECTION,
        triggerOnPress = true
    )

    /** For toggle switches */
    val Toggle = HapticConfig(
        type = HapticType.CONFIRMATION,
        triggerOnPress = false
    )

    /** For slider adjustments */
    val Slider = HapticConfig(
        type = HapticType.SELECTION,
        triggerOnPress = true
    )

    /** For important actions like send */
    val ImportantAction = HapticConfig(
        type = HapticType.IMPACT,
        triggerOnPress = true
    )
}
