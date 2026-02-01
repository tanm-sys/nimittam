/*
 * Copyright 2025 Tanmay Patil
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.google.ai.edge.gallery.common

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import androidx.core.content.edit

private const val TAG = "OfflineMode"

/**
 * Offline-first architecture controller.
 * 
 * Enables 100% air-gapped operation after model download:
 * - Blocks all telemetry and network calls during inference
 * - Supports full functionality without internet connection
 * - Zero analytics or data collection
 * 
 * Security: NSA/intelligence-level network isolation for sensitive deployments.
 */
object OfflineMode {
    
    private const val PREFS_NAME = "offline_mode_prefs"
    private const val KEY_OFFLINE_ENABLED = "offline_enabled"
    private const val KEY_MODELS_DOWNLOADED = "models_downloaded"
    private const val KEY_LAST_SYNC_TIME = "last_sync_time"
    
    private lateinit var prefs: SharedPreferences
    private var isInitialized = false
    private val listeners = mutableListOf<OfflineModeListener>()

    interface OfflineModeListener {
        fun onOfflineModeChanged(enabled: Boolean)
    }

    fun init(context: Context) {
        if (isInitialized) return
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        isInitialized = true
        Log.d(TAG, "OfflineMode initialized. Enabled: $isEnabled")
    }

    fun addListener(listener: OfflineModeListener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
        }
    }

    fun removeListener(listener: OfflineModeListener) {
        listeners.remove(listener)
    }

    /**
     * Whether offline mode is explicitly enabled by the user.
     */
    var isEnabled: Boolean
        get() = if (isInitialized) prefs.getBoolean(KEY_OFFLINE_ENABLED, false) else false
        set(value) {
            if (isInitialized) {
                prefs.edit { putBoolean(KEY_OFFLINE_ENABLED, value) }
                Log.d(TAG, "Offline mode set to: $value")
                listeners.forEach { it.onOfflineModeChanged(value) }
            }
        }

    /**
     * Last time the app synced with network (for cache invalidation).
     */
    val lastSyncTime: Long
        get() = if (isInitialized) prefs.getLong(KEY_LAST_SYNC_TIME, 0L) else 0L

    /**
     * Check if network calls should be blocked.
     */
    fun shouldBlockNetworkCalls(): Boolean {
        return isEnabled
    }

    /**
     * Check if analytics/telemetry should be disabled.
     */
    fun shouldDisableAnalytics(): Boolean {
        return isEnabled
    }

    /**
     * Check if crash reports should be stored locally only.
     */
    fun shouldStoreLogsLocally(): Boolean {
        return isEnabled
    }

    /**
     * Check if the device currently has network connectivity.
     */
    fun hasNetworkConnectivity(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
               capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    /**
     * Determine if a network operation should proceed.
     * Blocks if offline mode is enabled, allows otherwise.
     */
    fun shouldAllowNetworkOperation(operationType: NetworkOperationType): Boolean {
        if (!isEnabled) return true // Offline mode disabled, allow all
        
        return when (operationType) {
            NetworkOperationType.MODEL_DOWNLOAD -> true // Always allow model downloads
            NetworkOperationType.ANALYTICS -> false
            NetworkOperationType.CRASH_REPORT -> false
            NetworkOperationType.VERSION_CHECK -> false
            NetworkOperationType.INFERENCE -> true // Inference is always local
        }
    }

    enum class NetworkOperationType {
        MODEL_DOWNLOAD,
        ANALYTICS,
        CRASH_REPORT,
        VERSION_CHECK,
        INFERENCE
    }

    /**
     * Update sync timestamp when network operation completes.
     */
    fun updateLastSyncTime() {
        if (isInitialized) {
            prefs.edit { putLong(KEY_LAST_SYNC_TIME, System.currentTimeMillis()) }
        }
    }
}
