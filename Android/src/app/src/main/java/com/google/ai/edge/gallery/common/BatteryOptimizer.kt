/*
 * Copyright 2025 Tanmay Patil
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.google.ai.edge.gallery.common

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.util.Log

private const val TAG = "BatteryOptimizer"

/**
 * Battery-aware optimization for AI inference.
 * 
 * Implements intelligent power management:
 * - Reduces inference load when battery is low (<15%)
 * - Respects system power save mode
 * - Monitors charging state for optimal performance
 * 
 * Target: 0.75% battery for 25 conversations (Gemma 3 270M-IT)
 */
object BatteryOptimizer {

    private var batteryLevel: Int = 100
    private var isCharging: Boolean = false
    private var isPowerSaveMode: Boolean = false
    private var isInitialized: Boolean = false
    private val listeners = mutableListOf<BatteryStateListener>()

    private const val LOW_BATTERY_THRESHOLD = 15
    private const val CRITICAL_BATTERY_THRESHOLD = 5

    enum class BatteryState {
        NORMAL,
        LOW,
        CRITICAL,
        CHARGING
    }

    interface BatteryStateListener {
        fun onBatteryStateChanged(state: BatteryState)
    }

    fun init(context: Context) {
        if (isInitialized) return

        // Register battery receiver
        val batteryFilter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_BATTERY_LOW)
            addAction(Intent.ACTION_BATTERY_OKAY)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        context.applicationContext.registerReceiver(BatteryReceiver, batteryFilter)

        // Check initial power save mode
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        isPowerSaveMode = powerManager.isPowerSaveMode

        // Register power save mode receiver
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val powerSaveFilter = IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
            context.applicationContext.registerReceiver(PowerSaveReceiver, powerSaveFilter)
        }

        isInitialized = true
        Log.d(TAG, "BatteryOptimizer initialized. Battery: $batteryLevel%, Charging: $isCharging, PowerSave: $isPowerSaveMode")
    }

    fun addListener(listener: BatteryStateListener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
        }
    }

    fun removeListener(listener: BatteryStateListener) {
        listeners.remove(listener)
    }

    /**
     * Get current battery state.
     */
    fun getCurrentState(): BatteryState {
        return when {
            isCharging -> BatteryState.CHARGING
            batteryLevel <= CRITICAL_BATTERY_THRESHOLD -> BatteryState.CRITICAL
            batteryLevel <= LOW_BATTERY_THRESHOLD -> BatteryState.LOW
            else -> BatteryState.NORMAL
        }
    }

    /**
     * Check if inference should be throttled due to battery constraints.
     */
    fun shouldThrottleInference(): Boolean {
        return (batteryLevel <= LOW_BATTERY_THRESHOLD && !isCharging) || isPowerSaveMode
    }

    /**
     * Check if GPU should be avoided to save power.
     */
    fun shouldAvoidGpu(): Boolean {
        return (batteryLevel <= CRITICAL_BATTERY_THRESHOLD && !isCharging) || isPowerSaveMode
    }

    /**
     * Get recommended max tokens based on battery state.
     */
    fun getRecommendedMaxTokens(baseTokens: Int): Int {
        return when {
            isCharging -> baseTokens // Full power when charging
            batteryLevel <= CRITICAL_BATTERY_THRESHOLD -> baseTokens / 4
            batteryLevel <= LOW_BATTERY_THRESHOLD -> baseTokens / 2
            isPowerSaveMode -> baseTokens / 2
            else -> baseTokens
        }
    }

    /**
     * Check if device can handle intensive inference.
     */
    fun canRunIntensiveInference(): Boolean {
        return (batteryLevel > LOW_BATTERY_THRESHOLD || isCharging) && !isPowerSaveMode
    }

    fun getBatteryLevel(): Int = batteryLevel

    fun isDeviceCharging(): Boolean = isCharging

    fun isInPowerSaveMode(): Boolean = isPowerSaveMode

    private fun notifyListeners() {
        val state = getCurrentState()
        listeners.forEach { it.onBatteryStateChanged(state) }
    }

    private object BatteryReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_BATTERY_CHANGED -> {
                    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                    val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)

                    batteryLevel = if (scale > 0) (level * 100 / scale) else level
                    isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                            status == BatteryManager.BATTERY_STATUS_FULL

                    Log.d(TAG, "Battery updated: $batteryLevel%, charging: $isCharging")
                    notifyListeners()
                }
                Intent.ACTION_POWER_CONNECTED -> {
                    isCharging = true
                    Log.d(TAG, "Power connected")
                    notifyListeners()
                }
                Intent.ACTION_POWER_DISCONNECTED -> {
                    isCharging = false
                    Log.d(TAG, "Power disconnected")
                    notifyListeners()
                }
            }
        }
    }

    private object PowerSaveReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == PowerManager.ACTION_POWER_SAVE_MODE_CHANGED) {
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                isPowerSaveMode = powerManager.isPowerSaveMode
                Log.d(TAG, "Power save mode changed: $isPowerSaveMode")
                notifyListeners()
            }
        }
    }
}
