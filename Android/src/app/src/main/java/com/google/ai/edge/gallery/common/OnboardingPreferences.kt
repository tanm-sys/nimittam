/*
 * Copyright 2025 Tanmay Patil
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.google.ai.edge.gallery.common

import android.content.Context
import android.util.Log
import androidx.core.content.edit

private const val TAG = "OnboardingPreferences"

/**
 * SharedPreferences-based fallback for onboarding state.
 * 
 * Used when Proto DataStore fails during first launch (race condition).
 * Data is synced to DataStore on subsequent launches and then cleared.
 */
object OnboardingPreferences {
    
    private const val PREFS_NAME = "onboarding_fallback_prefs"
    private const val KEY_MODEL_TYPE = "selected_model_type"
    private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
    private const val KEY_FIRST_LAUNCH_TIMESTAMP = "first_launch_timestamp"
    
    private fun getPrefs(context: Context) = 
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    /**
     * Save selected model type to SharedPreferences.
     */
    fun saveModelType(context: Context, modelType: String) {
        getPrefs(context).edit { 
            putString(KEY_MODEL_TYPE, modelType) 
        }
        Log.d(TAG, "Saved model type to fallback prefs: $modelType")
    }
    
    /**
     * Get saved model type from SharedPreferences.
     * @return The saved model type, or null if not set.
     */
    fun getModelType(context: Context): String? {
        return getPrefs(context).getString(KEY_MODEL_TYPE, null)
    }
    
    /**
     * Mark onboarding as completed in SharedPreferences.
     */
    fun setOnboardingCompleted(context: Context, completed: Boolean) {
        getPrefs(context).edit { 
            putBoolean(KEY_ONBOARDING_COMPLETED, completed)
            if (completed) {
                putLong(KEY_FIRST_LAUNCH_TIMESTAMP, System.currentTimeMillis())
            }
        }
        Log.d(TAG, "Set onboarding completed to fallback prefs: $completed")
    }
    
    /**
     * Check if onboarding is marked as completed in SharedPreferences.
     */
    fun isOnboardingCompleted(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_ONBOARDING_COMPLETED, false)
    }
    
    /**
     * Get the first launch timestamp from SharedPreferences.
     */
    fun getFirstLaunchTimestamp(context: Context): Long {
        return getPrefs(context).getLong(KEY_FIRST_LAUNCH_TIMESTAMP, 0L)
    }
    
    /**
     * Check if there is any fallback data that needs to be synced.
     */
    fun hasFallbackData(context: Context): Boolean {
        val prefs = getPrefs(context)
        return prefs.contains(KEY_MODEL_TYPE) || prefs.contains(KEY_ONBOARDING_COMPLETED)
    }
    
    /**
     * Clear all fallback data after successful sync to DataStore.
     */
    fun clear(context: Context) {
        getPrefs(context).edit { clear() }
        Log.d(TAG, "Cleared fallback prefs after sync")
    }
}
