/*
 * Copyright 2025 Tanmay Patil
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.google.ai.edge.gallery.common

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Enum representing different types of haptic feedback patterns.
 * Each type corresponds to a specific user interaction context.
 */
enum class HapticType {
    /** Light, pleasant feedback for successful actions like button presses and toggles */
    CONFIRMATION,

    /** Sharp, attention-grabbing feedback for errors and validation failures */
    ERROR,

    /** Subtle feedback for list/item selection */
    SELECTION,

    /** Medium feedback for significant actions like sending messages or deleting */
    IMPACT,

    /** Celebratory pattern for completions like model downloads or task finishes */
    SUCCESS,

    /** Cautionary feedback for destructive actions */
    WARNING
}

/**
 * Data class representing device haptic capabilities.
 */
data class HapticCapabilities(
    val hasVibrator: Boolean,
    val hasAmplitudeControl: Boolean,
    val supportsPredefinedEffects: Boolean,
    val supportsCustomWaveforms: Boolean
)

/**
 * Manager class for handling haptic feedback throughout the application.
 *
 * This class provides a centralized way to trigger haptic feedback with different
 * patterns based on the interaction type. It supports Android 10+ (API 29+) haptic
 * constants and provides graceful fallbacks for older devices.
 *
 * Features:
 * - Contextually appropriate intensity levels for different interaction types
 * - Precise timing with no delay between action and feedback
 * - Distinct tactile patterns for different interaction types
 * - Automatic respect for user's accessibility settings
 * - Battery-conscious implementation avoiding excessive haptic usage
 *
 * @param context Application context for accessing system services
 */
@Singleton
class HapticFeedbackManager @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    /**
     * Checks the device's haptic capabilities.
     *
     * @return HapticCapabilities object containing device capabilities
     */
    fun getCapabilities(): HapticCapabilities {
        val hasVibrator = vibrator?.hasVibrator() ?: false
        val hasAmplitudeControl = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.hasAmplitudeControl() ?: false
        } else {
            false
        }

        return HapticCapabilities(
            hasVibrator = hasVibrator,
            hasAmplitudeControl = hasAmplitudeControl,
            supportsPredefinedEffects = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q,
            supportsCustomWaveforms = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
        )
    }

    /**
     * Checks if haptic feedback is enabled in system settings.
     *
     * @return true if haptic feedback is enabled, false otherwise
     */
    fun isHapticFeedbackEnabled(): Boolean {
        return try {
            Settings.System.getInt(
                context.contentResolver,
                Settings.System.HAPTIC_FEEDBACK_ENABLED,
                1
            ) == 1
        } catch (e: Exception) {
            true // Default to enabled if we can't read the setting
        }
    }

    /**
     * Triggers haptic feedback of the specified type.
     *
     * This method respects the user's accessibility settings and will not
     * trigger haptics if they are disabled system-wide.
     *
     * @param type The type of haptic feedback to trigger
     * @param force If true, bypasses the system haptic feedback setting (use sparingly)
     */
    fun performHaptic(type: HapticType, force: Boolean = false) {
        // Respect user's accessibility settings unless forced
        if (!force && !isHapticFeedbackEnabled()) {
            return
        }

        val vibrator = this.vibrator ?: return
        if (!vibrator.hasVibrator()) {
            return
        }

        when (type) {
            HapticType.CONFIRMATION -> performConfirmationHaptic(vibrator)
            HapticType.ERROR -> performErrorHaptic(vibrator)
            HapticType.SELECTION -> performSelectionHaptic(vibrator)
            HapticType.IMPACT -> performImpactHaptic(vibrator)
            HapticType.SUCCESS -> performSuccessHaptic(vibrator)
            HapticType.WARNING -> performWarningHaptic(vibrator)
        }
    }

    /**
     * Triggers haptic feedback asynchronously.
     * Use this when calling from coroutines to avoid blocking the main thread.
     *
     * @param type The type of haptic feedback to trigger
     * @param force If true, bypasses the system haptic feedback setting
     */
    suspend fun performHapticAsync(type: HapticType, force: Boolean = false) {
        withContext(Dispatchers.Default) {
            performHaptic(type, force)
        }
    }

    /**
     * Performs a light confirmation haptic - pleasant feedback for button presses.
     */
    private fun performConfirmationHaptic(vibrator: Vibrator) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Use predefined click effect for consistent feel
            val effect = VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
            vibrator.vibrate(effect)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Fallback: Short, light vibration
            val effect = VibrationEffect.createOneShot(10, 50)
            vibrator.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(10)
        }
    }

    /**
     * Performs an error haptic - sharp, attention-grabbing feedback.
     */
    private fun performErrorHaptic(vibrator: Vibrator) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Double pulse pattern for error indication
            val effect = VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK)
            vibrator.vibrate(effect)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Custom double-tap pattern: on-off-on-off
            val timings = longArrayOf(0, 30, 50, 30)
            val amplitudes = intArrayOf(0, 180, 0, 180)
            val effect = VibrationEffect.createWaveform(timings, amplitudes, -1)
            vibrator.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(longArrayOf(0, 30, 50, 30), -1)
        }
    }

    /**
     * Performs a selection haptic - subtle feedback for item selection.
     */
    private fun performSelectionHaptic(vibrator: Vibrator) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Use tick effect for subtle selection feedback
            val effect = VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
            vibrator.vibrate(effect)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Very short, subtle vibration
            val effect = VibrationEffect.createOneShot(5, 30)
            vibrator.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(5)
        }
    }

    /**
     * Performs an impact haptic - medium feedback for significant actions.
     */
    private fun performImpactHaptic(vibrator: Vibrator) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Use heavy click for impact
            val effect = VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
            vibrator.vibrate(effect)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Medium duration and amplitude
            val effect = VibrationEffect.createOneShot(20, 100)
            vibrator.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(20)
        }
    }

    /**
     * Performs a success haptic - celebratory pattern for completions.
     */
    private fun performSuccessHaptic(vibrator: Vibrator) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Try to use a pleasant success pattern
            // Rising then falling pattern: low-medium-high-medium
            val timings = longArrayOf(0, 30, 20, 40, 20, 30)
            val amplitudes = intArrayOf(0, 80, 0, 160, 0, 80)
            val effect = VibrationEffect.createWaveform(timings, amplitudes, -1)
            vibrator.vibrate(effect)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Rising pattern
            val timings = longArrayOf(0, 30, 20, 40)
            val amplitudes = intArrayOf(0, 80, 0, 160)
            val effect = VibrationEffect.createWaveform(timings, amplitudes, -1)
            vibrator.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(longArrayOf(0, 30, 20, 40), -1)
        }
    }

    /**
     * Performs a warning haptic - cautionary feedback for destructive actions.
     */
    private fun performWarningHaptic(vibrator: Vibrator) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Triple pulse pattern for warning
            val timings = longArrayOf(0, 25, 30, 25, 30, 25)
            val amplitudes = intArrayOf(0, 120, 0, 120, 0, 120)
            val effect = VibrationEffect.createWaveform(timings, amplitudes, -1)
            vibrator.vibrate(effect)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Warning pattern
            val timings = longArrayOf(0, 25, 30, 25, 30, 25)
            val amplitudes = intArrayOf(0, 120, 0, 120, 0, 120)
            val effect = VibrationEffect.createWaveform(timings, amplitudes, -1)
            vibrator.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(longArrayOf(0, 25, 30, 25, 30, 25), -1)
        }
    }

    /**
     * Performs a custom haptic pattern.
     * Use this for specialized haptic feedback not covered by standard types.
     *
     * @param timings Array of timing values in milliseconds
     * @param amplitudes Array of amplitude values (0-255), or null for default amplitude
     * @param repeat Index at which to repeat, or -1 for no repeat
     */
    fun performCustomPattern(timings: LongArray, amplitudes: IntArray?, repeat: Int = -1) {
        if (!isHapticFeedbackEnabled()) {
            return
        }

        val vibrator = this.vibrator ?: return
        if (!vibrator.hasVibrator()) {
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && amplitudes != null) {
            val effect = VibrationEffect.createWaveform(timings, amplitudes, repeat)
            vibrator.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(timings, repeat)
        }
    }

    companion object {
        /**
         * Predefined timing patterns for common haptic effects.
         * These can be used with performCustomPattern for consistent feedback.
         */
        object Patterns {
            /** Quick single tap pattern */
            val SINGLE_TAP = longArrayOf(0, 10)

            /** Double tap pattern */
            val DOUBLE_TAP = longArrayOf(0, 15, 30, 15)

            /** Heartbeat-like pattern */
            val HEARTBEAT = longArrayOf(0, 20, 40, 20, 100, 20, 40, 20)

            /** Ramp up pattern for progress indication */
            val RAMP_UP = longArrayOf(0, 20, 10, 30, 10, 40, 10, 50)

            /** Ramp down pattern for completion */
            val RAMP_DOWN = longArrayOf(0, 50, 10, 40, 10, 30, 10, 20)
        }

        /**
         * Predefined amplitude patterns for use with custom patterns.
         */
        object Amplitudes {
            /** Low intensity amplitudes */
            val LOW = intArrayOf(0, 40, 0, 40)

            /** Medium intensity amplitudes */
            val MEDIUM = intArrayOf(0, 100, 0, 100)

            /** High intensity amplitudes */
            val HIGH = intArrayOf(0, 180, 0, 180)

            /** Rising intensity amplitudes */
            val RISING = intArrayOf(0, 40, 0, 80, 0, 120, 0, 160)

            /** Falling intensity amplitudes */
            val FALLING = intArrayOf(0, 160, 0, 120, 0, 80, 0, 40)
        }
    }
}
