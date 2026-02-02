/*
 * Copyright 2025 Tanmay Patil
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.google.ai.edge.gallery.data

import android.util.Log
import androidx.datastore.core.DataStore
import com.google.ai.edge.gallery.proto.Settings
import com.google.ai.edge.gallery.proto.UserData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository interface for managing app settings and user data persistence.
 * Uses Proto DataStore for type-safe storage.
 */
interface DataStoreRepository {
    // Settings Flows
    val settingsFlow: Flow<Settings>
    val temperatureFlow: Flow<Float>
    val maxTokensFlow: Flow<Int>
    val topPFlow: Flow<Float>
    val topKFlow: Flow<Int>
    val repeatPenaltyFlow: Flow<Float>
    val darkThemeFlow: Flow<Boolean>
    val hapticFeedbackEnabledFlow: Flow<Boolean>
    val notificationsEnabledFlow: Flow<Boolean>
    val selectedModelFlow: Flow<String>
    val contextSizeFlow: Flow<Int>
    val hardwareBackendFlow: Flow<String>
    
    // User Data Flows
    val userDataFlow: Flow<UserData>
    val onboardingCompletedFlow: Flow<Boolean>
    val selectedModelTypeFlow: Flow<String>
    val lastConversationIdFlow: Flow<String>
    
    // Settings Operations
    suspend fun updateTemperature(temperature: Float): Result<Unit>
    suspend fun updateMaxTokens(maxTokens: Int): Result<Unit>
    suspend fun updateTopP(topP: Float): Result<Unit>
    suspend fun updateTopK(topK: Int): Result<Unit>
    suspend fun updateRepeatPenalty(repeatPenalty: Float): Result<Unit>
    suspend fun updateDarkTheme(enabled: Boolean): Result<Unit>
    suspend fun updateHapticFeedback(enabled: Boolean): Result<Unit>
    suspend fun updateNotifications(enabled: Boolean): Result<Unit>
    suspend fun updateSelectedModel(model: String): Result<Unit>
    suspend fun updateContextSize(size: Int): Result<Unit>
    suspend fun updateHardwareBackend(backend: String): Result<Unit>
    
    // User Data Operations
    suspend fun completeOnboarding(): Result<Unit>
    suspend fun updateSelectedModelType(modelType: String): Result<Unit>
    suspend fun updateLastConversationId(conversationId: String): Result<Unit>
    suspend fun incrementLaunchCount(): Result<Unit>
    
    // Get current values
    suspend fun getSettings(): Settings
    suspend fun getUserData(): UserData
}

/**
 * Default implementation of DataStoreRepository.
 * Provides reactive streams and safe error handling for DataStore operations.
 */
@Singleton
class DefaultDataStoreRepository @Inject constructor(
    private val dataStore: DataStore<Settings>,
    private val userDataDataStore: DataStore<UserData>,
) : DataStoreRepository {
    
    companion object {
        private const val TAG = "DataStoreRepository"
        
        // Default values
        const val DEFAULT_TEMPERATURE = 0.7f
        const val DEFAULT_MAX_TOKENS = 2048
        const val DEFAULT_TOP_P = 0.9f
        const val DEFAULT_TOP_K = 40
        const val DEFAULT_REPEAT_PENALTY = 1.1f
        const val DEFAULT_CONTEXT_SIZE = 4096
        const val DEFAULT_HARDWARE_BACKEND = "VULKAN_GPU"
    }
    
    // ==================== Settings Flows ====================
    
    override val settingsFlow: Flow<Settings> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                Log.e(TAG, "Error reading settings", exception)
                emit(Settings.getDefaultInstance())
            } else {
                throw exception
            }
        }
    
    override val temperatureFlow: Flow<Float> = settingsFlow
        .map { it.temperature.takeIf { t -> t > 0 } ?: DEFAULT_TEMPERATURE }
    
    override val maxTokensFlow: Flow<Int> = settingsFlow
        .map { it.maxTokens.takeIf { t -> t > 0 } ?: DEFAULT_MAX_TOKENS }
    
    override val topPFlow: Flow<Float> = settingsFlow
        .map { it.topP.takeIf { p -> p > 0 } ?: DEFAULT_TOP_P }
    
    override val topKFlow: Flow<Int> = settingsFlow
        .map { it.topK.takeIf { k -> k > 0 } ?: DEFAULT_TOP_K }
    
    override val repeatPenaltyFlow: Flow<Float> = settingsFlow
        .map { it.repeatPenalty.takeIf { p -> p > 0 } ?: DEFAULT_REPEAT_PENALTY }
    
    override val darkThemeFlow: Flow<Boolean> = settingsFlow
        .map { it.darkTheme }
    
    override val hapticFeedbackEnabledFlow: Flow<Boolean> = settingsFlow
        .map { it.hapticFeedbackEnabled }
    
    override val notificationsEnabledFlow: Flow<Boolean> = settingsFlow
        .map { it.notificationsEnabled }
    
    override val selectedModelFlow: Flow<String> = settingsFlow
        .map { it.selectedModel.takeIf { it.isNotEmpty() } ?: "Nimittam Lite" }
    
    override val contextSizeFlow: Flow<Int> = settingsFlow
        .map { it.contextSize.takeIf { s -> s > 0 } ?: DEFAULT_CONTEXT_SIZE }
    
    override val hardwareBackendFlow: Flow<String> = settingsFlow
        .map { it.hardwareBackend.takeIf { it.isNotEmpty() } ?: DEFAULT_HARDWARE_BACKEND }
    
    // ==================== User Data Flows ====================
    
    override val userDataFlow: Flow<UserData> = userDataDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                Log.e(TAG, "Error reading user data", exception)
                emit(UserData.getDefaultInstance())
            } else {
                throw exception
            }
        }
    
    override val onboardingCompletedFlow: Flow<Boolean> = userDataFlow
        .map { it.onboardingCompleted }
    
    override val selectedModelTypeFlow: Flow<String> = userDataFlow
        .map { it.selectedModelType }
    
    override val lastConversationIdFlow: Flow<String> = userDataFlow
        .map { it.lastConversationId }
    
    // ==================== Settings Operations ====================
    
    override suspend fun updateTemperature(temperature: Float): Result<Unit> = 
        updateSettings { it.setTemperature(temperature.coerceIn(0f, 2f)) }
    
    override suspend fun updateMaxTokens(maxTokens: Int): Result<Unit> = 
        updateSettings { it.setMaxTokens(maxTokens.coerceIn(1, 8192)) }
    
    override suspend fun updateTopP(topP: Float): Result<Unit> = 
        updateSettings { it.setTopP(topP.coerceIn(0f, 1f)) }
    
    override suspend fun updateTopK(topK: Int): Result<Unit> = 
        updateSettings { it.setTopK(topK.coerceIn(1, 100)) }
    
    override suspend fun updateRepeatPenalty(repeatPenalty: Float): Result<Unit> = 
        updateSettings { it.setRepeatPenalty(repeatPenalty.coerceIn(1f, 2f)) }
    
    override suspend fun updateDarkTheme(enabled: Boolean): Result<Unit> = 
        updateSettings { it.setDarkTheme(enabled) }
    
    override suspend fun updateHapticFeedback(enabled: Boolean): Result<Unit> = 
        updateSettings { it.setHapticFeedbackEnabled(enabled) }
    
    override suspend fun updateNotifications(enabled: Boolean): Result<Unit> = 
        updateSettings { it.setNotificationsEnabled(enabled) }
    
    override suspend fun updateSelectedModel(model: String): Result<Unit> = 
        updateSettings { it.setSelectedModel(model) }
    
    override suspend fun updateContextSize(size: Int): Result<Unit> = 
        updateSettings { it.setContextSize(size.coerceIn(512, 32768)) }
    
    override suspend fun updateHardwareBackend(backend: String): Result<Unit> = 
        updateSettings { it.setHardwareBackend(backend) }
    
    // ==================== User Data Operations ====================
    
    override suspend fun completeOnboarding(): Result<Unit> = 
        updateUserData { 
            it.setOnboardingCompleted(true)
                .setFirstLaunchTimestamp(System.currentTimeMillis())
        }
    
    override suspend fun updateSelectedModelType(modelType: String): Result<Unit> = 
        updateUserData { it.setSelectedModelType(modelType) }
    
    override suspend fun updateLastConversationId(conversationId: String): Result<Unit> = 
        updateUserData { it.setLastConversationId(conversationId) }
    
    override suspend fun incrementLaunchCount(): Result<Unit> = 
        updateUserData { 
            val currentCount = it.launchCount
            it.setLaunchCount(currentCount + 1)
        }
    
    // ==================== Get Current Values ====================
    
    override suspend fun getSettings(): Settings = 
        try {
            dataStore.data.first()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting settings", e)
            Settings.getDefaultInstance()
        }
    
    override suspend fun getUserData(): UserData = 
        try {
            userDataDataStore.data.first()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting user data", e)
            UserData.getDefaultInstance()
        }
    
    // ==================== Private Helper Methods ====================
    
    private suspend fun updateSettings(transform: (Settings.Builder) -> Settings.Builder): Result<Unit> {
        return try {
            dataStore.updateData { currentSettings ->
                transform(currentSettings.toBuilder()).build()
            }
            Result.success(Unit)
        } catch (e: IOException) {
            Log.e(TAG, "Error updating settings", e)
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error updating settings", e)
            Result.failure(e)
        }
    }
    
    private suspend fun updateUserData(transform: (UserData.Builder) -> UserData.Builder): Result<Unit> {
        return try {
            userDataDataStore.updateData { currentUserData ->
                transform(currentUserData.toBuilder()).build()
            }
            Result.success(Unit)
        } catch (e: IOException) {
            Log.e(TAG, "Error updating user data", e)
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error updating user data", e)
            Result.failure(e)
        }
    }
}
