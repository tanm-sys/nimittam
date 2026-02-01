/*
 * Copyright 2025 Tanmay Patil
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

@file:Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")

package com.google.ai.edge.gallery.common

import android.app.ActivityManager
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.util.Log

private const val TAG = "MemoryManager"

/**
 * Enterprise-grade memory pressure detection and response system.
 * Implements ComponentCallbacks2 for system memory pressure notifications.
 * 
 * Features:
 * - Dynamic maxTokens calculation based on available RAM
 * - Memory pressure detection with listener callbacks
 * - Graceful degradation under memory constraints
 */
object MemoryManager : ComponentCallbacks2 {

    private var isLowMemory = false
    private var memoryPressureLevel = MemoryPressureLevel.NORMAL
    private val listeners = mutableListOf<MemoryPressureListener>()
    private var isInitialized = false

    enum class MemoryPressureLevel {
        NORMAL,
        MODERATE,
        CRITICAL
    }

    interface MemoryPressureListener {
        fun onMemoryPressureChanged(level: MemoryPressureLevel)
        fun onLowMemory()
    }

    fun init(context: Context) {
        if (isInitialized) return
        context.applicationContext.registerComponentCallbacks(this)
        isInitialized = true
        Log.d(TAG, "MemoryManager initialized")
    }

    fun addListener(listener: MemoryPressureListener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
        }
    }

    fun removeListener(listener: MemoryPressureListener) {
        listeners.remove(listener)
    }

    /**
     * Get available memory in megabytes.
     */
    fun getAvailableMemoryMb(context: Context): Long {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        return memInfo.availMem / (1024 * 1024)
    }

    /**
     * Get total device memory in gigabytes.
     */
    fun getTotalMemoryGb(context: Context): Float {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        return memInfo.totalMem / (1024f * 1024f * 1024f)
    }

    /**
     * Calculate optimal maxTokens based on device RAM and current memory pressure.
     * 
     * Memory allocation strategy:
     * - 8GB+ RAM: 4096 tokens (full capability)
     * - 6GB+ RAM: 2048 tokens (balanced)
     * - 4GB+ RAM: 1024 tokens (conservative)
     * - <4GB RAM: 512 tokens (minimal)
     * 
     * Under memory pressure, tokens are further reduced.
     */
    fun calculateOptimalMaxTokens(context: Context): Int {
        val availableRamGb = getTotalMemoryGb(context)
        
        val baseTokens = when {
            availableRamGb >= 8f -> 4096
            availableRamGb >= 6f -> 2048
            availableRamGb >= 4f -> 1024
            else -> 512
        }

        // Reduce tokens under memory pressure
        return when (memoryPressureLevel) {
            MemoryPressureLevel.CRITICAL -> baseTokens / 4
            MemoryPressureLevel.MODERATE -> baseTokens / 2
            MemoryPressureLevel.NORMAL -> baseTokens
        }
    }

    /**
     * Check if quality should be reduced due to memory constraints.
     */
    fun shouldReduceQuality(context: Context): Boolean {
        return getAvailableMemoryMb(context) < 500 || isLowMemory || 
               memoryPressureLevel != MemoryPressureLevel.NORMAL
    }

    /**
     * Check if device has sufficient memory to run the model.
     */
    fun hasSufficientMemory(context: Context, requiredGb: Int): Boolean {
        val availableGb = getAvailableMemoryMb(context) / 1024f
        return availableGb >= requiredGb * 0.8f // 80% of required as safety margin
    }

    fun getCurrentPressureLevel(): MemoryPressureLevel = memoryPressureLevel

    override fun onTrimMemory(level: Int) {
        val previousLevel = memoryPressureLevel
        
        when (level) {
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL,
            ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> {
                Log.w(TAG, "Critical memory pressure detected, level: $level")
                memoryPressureLevel = MemoryPressureLevel.CRITICAL
                isLowMemory = true
            }
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW,
            ComponentCallbacks2.TRIM_MEMORY_MODERATE -> {
                Log.w(TAG, "Moderate memory pressure detected, level: $level")
                memoryPressureLevel = MemoryPressureLevel.MODERATE
            }
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE,
            ComponentCallbacks2.TRIM_MEMORY_BACKGROUND,
            ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> {
                Log.d(TAG, "Memory trim requested, level: $level")
                // Don't change pressure level for these
            }
        }

        if (previousLevel != memoryPressureLevel) {
            notifyListeners()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        // No action needed
    }

    override fun onLowMemory() {
        Log.w(TAG, "System low memory callback received")
        isLowMemory = true
        memoryPressureLevel = MemoryPressureLevel.CRITICAL
        listeners.forEach { it.onLowMemory() }
    }

    private fun notifyListeners() {
        listeners.forEach { it.onMemoryPressureChanged(memoryPressureLevel) }
    }

    /**
     * Reset memory pressure level (call when memory situation improves).
     */
    fun resetPressureLevel() {
        memoryPressureLevel = MemoryPressureLevel.NORMAL
        isLowMemory = false
        notifyListeners()
    }
}
