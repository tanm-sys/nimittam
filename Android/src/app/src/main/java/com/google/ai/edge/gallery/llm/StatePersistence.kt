/*
 * Copyright 2025 Tanmay Patil
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.google.ai.edge.gallery.llm

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Handles persistence of engine state across process death.
 * Uses SharedPreferences for lightweight state storage.
 */
class StatePersistence(context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME, 
        Context.MODE_PRIVATE
    )
    
    companion object {
        private const val TAG = "StatePersistence"
        private const val PREFS_NAME = "engine_state_persistence"
        private const val KEY_ENGINE_STATE = "engine_state"
        private const val KEY_STATE_TIMESTAMP = "state_timestamp"
        private const val STATE_VALIDITY_MS = 300_000L // 5 minutes
    }
    
    /**
     * Data class for serializable state representation.
     */
    @Serializable
    data class PersistedState(
        val state: String,
        val timestamp: Long,
        val modelPath: String? = null,
        val errorMessage: String? = null
    )
    
    /**
     * Saves the current engine state.
     */
    fun saveState(state: EngineState, modelPath: String? = null, errorMessage: String? = null) {
        try {
            val persistedState = PersistedState(
                state = state.name,
                timestamp = System.currentTimeMillis(),
                modelPath = modelPath,
                errorMessage = errorMessage
            )
            
            val json = Json.encodeToString(persistedState)
            prefs.edit()
                .putString(KEY_ENGINE_STATE, json)
                .putLong(KEY_STATE_TIMESTAMP, persistedState.timestamp)
                .apply()
            
            Log.d(TAG, "State saved: $state")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save state", e)
        }
    }
    
    /**
     * Retrieves the last saved state if it's still valid (within 5 minutes).
     * Returns null if no valid state exists.
     */
    fun getSavedState(): PersistedState? {
        return try {
            val json = prefs.getString(KEY_ENGINE_STATE, null)
            val timestamp = prefs.getLong(KEY_STATE_TIMESTAMP, 0L)
            
            if (json == null) {
                Log.d(TAG, "No saved state found")
                return null
            }
            
            // Check if state is still valid (within 5 minutes)
            val age = System.currentTimeMillis() - timestamp
            if (age > STATE_VALIDITY_MS) {
                Log.d(TAG, "Saved state expired (age: ${age}ms)")
                clearState()
                return null
            }
            
            val state = Json.decodeFromString<PersistedState>(json)
            Log.d(TAG, "State restored: ${state.state}")
            state
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore state", e)
            clearState()
            null
        }
    }
    
    /**
     * Checks if there's a valid saved state.
     */
    fun hasValidState(): Boolean {
        return getSavedState() != null
    }
    
    /**
     * Clears the saved state.
     */
    fun clearState() {
        prefs.edit()
            .remove(KEY_ENGINE_STATE)
            .remove(KEY_STATE_TIMESTAMP)
            .apply()
        Log.d(TAG, "State cleared")
    }
    
    /**
     * Gets the engine state from persisted data.
     */
    fun getRestoredEngineState(): EngineState? {
        return getSavedState()?.let { persisted ->
            try {
                EngineState.valueOf(persisted.state)
            } catch (e: IllegalArgumentException) {
                Log.e(TAG, "Unknown state: ${persisted.state}")
                null
            }
        }
    }
}

/**
 * Enhanced EngineStateManager with persistence support.
 */
class PersistentEngineStateManager private constructor(
    private val persistence: StatePersistence?,
    private val telemetryCallback: ((StateTransitionEvent) -> Unit)?
) {
    
    private val _state = MutableStateFlow(EngineState.UNINITIALIZED)
    val stateFlow: StateFlow<EngineState> = _state
    
    val currentState: EngineState get() = _state.value
    
    companion object {
        private const val TAG = "PersistentEngineStateManager"
        
        /**
         * Creates a new instance with optional persistence.
         */
        fun create(
            context: Context? = null,
            telemetryCallback: ((StateTransitionEvent) -> Unit)? = null
        ): PersistentEngineStateManager {
            val persistence = context?.let { StatePersistence(it) }
            return PersistentEngineStateManager(persistence, telemetryCallback).apply {
                // Try to restore state on creation
                restoreState()
            }
        }
    }
    
    init {
        // Set up state change listener to persist changes
        _state.value = EngineState.UNINITIALIZED
    }
    
    /**
     * Transitions to a new state and persists it.
     */
    fun transitionTo(
        newState: EngineState, 
        reason: String = "",
        error: Throwable? = null
    ): Result<Unit> {
        val fromState = _state.value
        
        // Validate transition
        if (!isValidTransition(fromState, newState)) {
            val message = "Invalid state transition: $fromState -> $newState"
            Log.w(TAG, message)
            return Result.failure(IllegalStateException(message))
        }
        
        // Perform transition
        _state.value = newState
        
        // Persist state
        persistence?.saveState(
            state = newState,
            errorMessage = error?.message
        )
        
        // Notify telemetry
        telemetryCallback?.invoke(
            StateTransitionEvent(
                fromState = fromState,
                toState = newState,
                reason = reason,
                timestamp = System.currentTimeMillis()
            )
        )
        
        Log.i(TAG, "State transition: $fromState -> $newState ($reason)")
        return Result.success(Unit)
    }
    
    /**
     * Restores state from persistence if valid.
     */
    private fun restoreState() {
        persistence?.getRestoredEngineState()?.let { restoredState ->
            Log.i(TAG, "Restoring state from persistence: $restoredState")
            _state.value = restoredState
        }
    }
    
    /**
     * Clears any persisted state. Call when app shuts down gracefully.
     */
    fun clearPersistence() {
        persistence?.clearState()
    }
    
    private fun isValidTransition(from: EngineState, to: EngineState): Boolean {
        return when (from) {
            EngineState.UNINITIALIZED -> to in listOf(
                EngineState.INITIALIZING, EngineState.ERROR, EngineState.RELEASED
            )
            EngineState.INITIALIZING -> to in listOf(
                EngineState.READY, EngineState.ERROR, EngineState.SHUTTING_DOWN
            )
            EngineState.READY -> to in listOf(
                EngineState.SHUTTING_DOWN, EngineState.ERROR
            )
            EngineState.ERROR -> to in listOf(
                EngineState.INITIALIZING, EngineState.SHUTTING_DOWN, EngineState.RELEASED
            )
            EngineState.SHUTTING_DOWN -> to in listOf(
                EngineState.RELEASED, EngineState.ERROR
            )
            EngineState.RELEASED -> to in listOf(
                EngineState.INITIALIZING
            )
        }
    }
}
