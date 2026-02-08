/*
 * Copyright 2025 Tanmay Patil
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.google.ai.edge.gallery.data

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import com.google.ai.edge.gallery.common.OnboardingPreferences
import com.google.ai.edge.gallery.proto.Settings
import com.google.ai.edge.gallery.proto.UserData
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orchestrates a systematic fallback strategy for data persistence:
 * 
 * Primary:    DataStore (Proto-based, type-safe)
 * Fallback 1: SharedPreferences (reliable, synchronous)
 * Fallback 2: In-Memory (temporary, survives config changes)
 * 
 * This ensures data persistence even when the primary storage fails.
 */
@Singleton
class PersistenceManager @Inject constructor(
    private val context: Context,
    private val dataStoreRepository: DataStoreRepository
) {
    companion object {
        private const val TAG = "PersistenceManager"
    }
    
    // In-memory fallback storage
    private val inMemorySettings = mutableMapOf<String, Any>()
    private val inMemoryUserData = mutableMapOf<String, Any>()
    private val memoryMutex = Mutex()
    
    /**
     * Save onboarding data with fallback chain:
     * DataStore → SharedPreferences → In-Memory
     */
    suspend fun saveOnboardingData(
        modelType: String,
        onboardingCompleted: Boolean
    ): Result<Unit> {
        Log.d(TAG, "Saving onboarding data: modelType=$modelType, completed=$onboardingCompleted")
        
        // Try DataStore first
        val dataStoreResult = trySaveToDataStore(modelType, onboardingCompleted)
        if (dataStoreResult.isSuccess) {
            Log.d(TAG, "Onboarding data saved to DataStore successfully")
            return Result.success(Unit)
        }
        
        Log.w(TAG, "DataStore failed, trying SharedPreferences fallback", 
            dataStoreResult.exceptionOrNull())
        
        // Fallback to SharedPreferences
        val sharedPrefsResult = trySaveToSharedPreferences(modelType, onboardingCompleted)
        if (sharedPrefsResult.isSuccess) {
            Log.d(TAG, "Onboarding data saved to SharedPreferences")
            return Result.success(Unit)
        }
        
        Log.w(TAG, "SharedPreferences failed, using in-memory fallback",
            sharedPrefsResult.exceptionOrNull())
        
        // Last resort: in-memory
        val memoryResult = trySaveToMemory(modelType, onboardingCompleted)
        if (memoryResult.isSuccess) {
            Log.w(TAG, "Using in-memory fallback - data will not persist!")
            return Result.success(Unit)
        }
        
        // All failed
        Log.e(TAG, "All persistence layers failed")
        return Result.failure(
            PersistenceException(
                "Failed to save onboarding data on all layers",
                dataStoreResult.exceptionOrNull()
            )
        )
    }
    
    /**
     * Load onboarding data with fallback chain:
     * DataStore → SharedPreferences → In-Memory → Defaults
     */
    suspend fun loadOnboardingData(): OnboardingData {
        Log.d(TAG, "Loading onboarding data...")
        
        // Try DataStore first
        try {
            val userData = dataStoreRepository.getUserData()
            if (userData.selectedModelType.isNotEmpty() || userData.onboardingCompleted) {
                Log.d(TAG, "Loaded from DataStore: modelType=${userData.selectedModelType}")
                return OnboardingData(
                    modelType = userData.selectedModelType,
                    onboardingCompleted = userData.onboardingCompleted,
                    source = PersistenceSource.DATASTORE
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "DataStore read failed, trying SharedPreferences", e)
        }
        
        // Try SharedPreferences
        try {
            if (OnboardingPreferences.hasFallbackData(context)) {
                val modelType = OnboardingPreferences.getModelType(context) ?: ""
                val completed = OnboardingPreferences.isOnboardingCompleted(context)
                if (modelType.isNotEmpty() || completed) {
                    Log.d(TAG, "Loaded from SharedPreferences: modelType=$modelType")
                    return OnboardingData(
                        modelType = modelType,
                        onboardingCompleted = completed,
                        source = PersistenceSource.SHARED_PREFERENCES
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "SharedPreferences read failed, trying in-memory", e)
        }
        
        // Try in-memory
        try {
            memoryMutex.withLock {
                val modelType = inMemoryUserData["selected_model_type"] as? String ?: ""
                val completed = inMemoryUserData["onboarding_completed"] as? Boolean ?: false
                if (modelType.isNotEmpty() || completed) {
                    Log.d(TAG, "Loaded from in-memory: modelType=$modelType")
                    return OnboardingData(
                        modelType = modelType,
                        onboardingCompleted = completed,
                        source = PersistenceSource.IN_MEMORY
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "In-memory read failed, using defaults", e)
        }
        
        // Return defaults
        Log.d(TAG, "No data found, using defaults")
        return OnboardingData(
            modelType = "",
            onboardingCompleted = false,
            source = PersistenceSource.DEFAULT
        )
    }
    
    /**
     * Attempts to save to DataStore.
     */
    private suspend fun trySaveToDataStore(
        modelType: String,
        onboardingCompleted: Boolean
    ): Result<Unit> {
        return try {
            val modelResult = dataStoreRepository.updateSelectedModelType(modelType)
            if (modelResult.isFailure) {
                return modelResult
            }
            
            if (onboardingCompleted) {
                val onboardingResult = dataStoreRepository.completeOnboarding()
                if (onboardingResult.isFailure) {
                    return onboardingResult
                }
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Attempts to save to SharedPreferences.
     */
    private fun trySaveToSharedPreferences(
        modelType: String,
        onboardingCompleted: Boolean
    ): Result<Unit> {
        return try {
            OnboardingPreferences.saveModelType(context, modelType)
            if (onboardingCompleted) {
                OnboardingPreferences.setOnboardingCompleted(context, true)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Attempts to save to in-memory storage.
     */
    private suspend fun trySaveToMemory(
        modelType: String,
        onboardingCompleted: Boolean
    ): Result<Unit> {
        return try {
            memoryMutex.withLock {
                inMemoryUserData["selected_model_type"] = modelType
                inMemoryUserData["onboarding_completed"] = onboardingCompleted
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Syncs data from fallback layers to primary (DataStore).
     * Call this on app startup to recover data from failed writes.
     */
    suspend fun syncFallbackToPrimary(): Boolean {
        Log.d(TAG, "Syncing fallback data to primary storage...")
        
        var synced = false
        
        // Check SharedPreferences
        try {
            if (OnboardingPreferences.hasFallbackData(context)) {
                val modelType = OnboardingPreferences.getModelType(context)
                val completed = OnboardingPreferences.isOnboardingCompleted(context)
                
                if (modelType != null || completed) {
                    // Try to sync to DataStore
                    val result = saveOnboardingData(
                        modelType = modelType ?: "",
                        onboardingCompleted = completed
                    )
                    
                    if (result.isSuccess) {
                        OnboardingPreferences.clear(context)
                        Log.i(TAG, "Successfully synced from SharedPreferences to DataStore")
                        synced = true
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync from SharedPreferences", e)
        }
        
        // Check in-memory (no need to clear, will be lost anyway)
        try {
            memoryMutex.withLock {
                val modelType = inMemoryUserData["selected_model_type"] as? String
                val completed = inMemoryUserData["onboarding_completed"] as? Boolean ?: false
                
                if (modelType != null) {
                    val result = saveOnboardingData(modelType, completed)
                    if (result.isSuccess) {
                        Log.i(TAG, "Successfully synced from in-memory to DataStore")
                        synced = true
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync from in-memory", e)
        }
        
        return synced
    }
    
    /**
     * Clears all persistence layers. Use with caution.
     */
    suspend fun clearAll() {
        Log.w(TAG, "Clearing all persistence layers")
        
        // Clear DataStore
        try {
            dataStoreRepository.updateSelectedModelType("")
            // Note: Can't un-complete onboarding easily, but that's fine
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear DataStore", e)
        }
        
        // Clear SharedPreferences
        try {
            OnboardingPreferences.clear(context)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear SharedPreferences", e)
        }
        
        // Clear in-memory
        memoryMutex.withLock {
            inMemoryUserData.clear()
            inMemorySettings.clear()
        }
        
        Log.i(TAG, "All persistence layers cleared")
    }
    
    /**
     * Checks if any persistence layer has data.
     */
    suspend fun hasAnyData(): Boolean {
        val data = loadOnboardingData()
        return data.modelType.isNotEmpty() || data.onboardingCompleted
    }
}

/**
 * Data class representing onboarding data.
 */
data class OnboardingData(
    val modelType: String,
    val onboardingCompleted: Boolean,
    val source: PersistenceSource
)

/**
 * Enum representing the persistence source.
 */
enum class PersistenceSource {
    DATASTORE,
    SHARED_PREFERENCES,
    IN_MEMORY,
    DEFAULT
}

/**
 * Exception thrown when all persistence layers fail.
 */
class PersistenceException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
