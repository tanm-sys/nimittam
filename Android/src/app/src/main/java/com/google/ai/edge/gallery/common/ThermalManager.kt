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

package com.google.ai.edge.gallery.common

import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.util.Log

private const val TAG = "ThermalManager"

/**
 * Thermal throttling detection and response system.
 * 
 * Monitors device thermal state and adjusts AI inference accordingly:
 * - NORMAL: Full GPU acceleration
 * - MODERATE: Reduce decode speed
 * - SEVERE: Switch to CPU backend
 * - CRITICAL: Pause inference, notify user
 */
object ThermalManager {

    private var currentThermalStatus: Int = PowerManager.THERMAL_STATUS_NONE
    private var isInitialized: Boolean = false
    private val listeners = mutableListOf<ThermalStateListener>()

    enum class ThermalAction {
        NORMAL,           // Full performance
        REDUCE_SPEED,     // Reduce inference speed
        USE_CPU_ONLY,     // Avoid GPU, use CPU
        PAUSE_INFERENCE,  // Stop inference temporarily
        EMERGENCY_STOP    // Immediate shutdown
    }

    interface ThermalStateListener {
        fun onThermalStateChanged(action: ThermalAction)
    }

    fun init(context: Context) {
        if (isInitialized) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            currentThermalStatus = powerManager.currentThermalStatus

            // Register thermal status listener
            powerManager.addThermalStatusListener { status ->
                val previousStatus = currentThermalStatus
                currentThermalStatus = status
                Log.d(TAG, "Thermal status changed: $previousStatus -> $status")

                if (previousStatus != status) {
                    notifyListeners()
                }
            }
        }

        isInitialized = true
        Log.d(TAG, "ThermalManager initialized. Status: ${getThermalStatusName()}")
    }

    fun addListener(listener: ThermalStateListener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
        }
    }

    fun removeListener(listener: ThermalStateListener) {
        listeners.remove(listener)
    }

    /**
     * Get current thermal status from PowerManager.
     */
    fun getCurrentThermalStatus(context: Context): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            powerManager.currentThermalStatus
        } else {
            PowerManager.THERMAL_STATUS_NONE
        }
    }

    /**
     * Determine appropriate action based on thermal state.
     */
    fun getRecommendedAction(): ThermalAction {
        return when (currentThermalStatus) {
            PowerManager.THERMAL_STATUS_NONE,
            PowerManager.THERMAL_STATUS_LIGHT -> ThermalAction.NORMAL

            PowerManager.THERMAL_STATUS_MODERATE -> ThermalAction.REDUCE_SPEED

            PowerManager.THERMAL_STATUS_SEVERE -> ThermalAction.USE_CPU_ONLY

            PowerManager.THERMAL_STATUS_CRITICAL -> ThermalAction.PAUSE_INFERENCE

            PowerManager.THERMAL_STATUS_EMERGENCY,
            PowerManager.THERMAL_STATUS_SHUTDOWN -> ThermalAction.EMERGENCY_STOP

            else -> ThermalAction.NORMAL
        }
    }

    /**
     * Check if GPU should be used based on thermal state.
     */
    fun shouldUseGpu(): Boolean {
        return when (getRecommendedAction()) {
            ThermalAction.NORMAL,
            ThermalAction.REDUCE_SPEED -> true
            else -> false
        }
    }

    /**
     * Check if inference should be paused.
     */
    fun shouldPauseInference(): Boolean {
        return when (getRecommendedAction()) {
            ThermalAction.PAUSE_INFERENCE,
            ThermalAction.EMERGENCY_STOP -> true
            else -> false
        }
    }

    /**
     * Get token generation delay based on thermal state (for throttling).
     */
    fun getTokenDelayMs(): Long {
        return when (getRecommendedAction()) {
            ThermalAction.NORMAL -> 0L
            ThermalAction.REDUCE_SPEED -> 10L
            ThermalAction.USE_CPU_ONLY -> 20L
            ThermalAction.PAUSE_INFERENCE -> Long.MAX_VALUE
            ThermalAction.EMERGENCY_STOP -> Long.MAX_VALUE
        }
    }

    /**
     * Check if device is thermally safe for intensive operations.
     */
    fun isThermallySafe(): Boolean {
        return currentThermalStatus <= PowerManager.THERMAL_STATUS_MODERATE
    }

    fun getThermalStatusName(): String {
        return when (currentThermalStatus) {
            PowerManager.THERMAL_STATUS_NONE -> "NONE"
            PowerManager.THERMAL_STATUS_LIGHT -> "LIGHT"
            PowerManager.THERMAL_STATUS_MODERATE -> "MODERATE"
            PowerManager.THERMAL_STATUS_SEVERE -> "SEVERE"
            PowerManager.THERMAL_STATUS_CRITICAL -> "CRITICAL"
            PowerManager.THERMAL_STATUS_EMERGENCY -> "EMERGENCY"
            PowerManager.THERMAL_STATUS_SHUTDOWN -> "SHUTDOWN"
            else -> "UNKNOWN"
        }
    }

    private fun notifyListeners() {
        val action = getRecommendedAction()
        listeners.forEach { it.onThermalStateChanged(action) }
    }
}
