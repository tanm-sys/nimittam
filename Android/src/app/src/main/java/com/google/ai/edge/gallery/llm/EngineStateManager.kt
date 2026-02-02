/*
 * Copyright 2025 Tanmay Patil
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.google.ai.edge.gallery.llm

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference

/**
 * Thread-safe state manager for the LLM engine.
 * 
 * This class provides atomic state transitions with proper synchronization,
 * ensuring no race conditions occur during initialization or operation.
 * 
 * Features:
 * - Mutex-based state transitions
 * - Atomic state queries
 * - State change callbacks
 * - Transition validation
 * - Telemetry event generation
 */
class EngineStateManager private constructor(
    private val telemetryCallback: ((StateTransitionEvent) -> Unit)?
) {
    companion object {
        private const val TAG = "EngineStateManager"
        
        /**
         * Create a new EngineStateManager instance.
         * 
         * @param telemetryCallback Optional callback for telemetry events
         */
        fun create(telemetryCallback: ((StateTransitionEvent) -> Unit)? = null): EngineStateManager {
            return EngineStateManager(telemetryCallback)
        }
    }
    
    // Mutex for state transition synchronization
    private val stateMutex = Mutex()
    
    // Atomic reference for fast read-only access
    private val atomicState = AtomicReference(EngineState.UNINITIALIZED)
    
    // StateFlow for reactive state observation
    private val _stateFlow = MutableStateFlow(EngineState.UNINITIALIZED)
    val stateFlow: StateFlow<EngineState> = _stateFlow.asStateFlow()
    
    // Current initialization progress
    private val _progressFlow = MutableStateFlow<InitializationProgress>(InitializationProgress.NotStarted)
    val progressFlow: StateFlow<InitializationProgress> = _progressFlow.asStateFlow()
    
    // Registered callbacks for state changes
    private val callbacks = CopyOnWriteArrayList<EngineStateCallback>()
    
    // History of state transitions for debugging
    private val transitionHistory = mutableListOf<StateTransitionEvent>()
    private val historyMutex = Mutex()
    
    /**
     * Get the current state (thread-safe, non-blocking read).
     */
    fun getCurrentState(): EngineState = atomicState.get()
    
    /**
     * Check if the current state can accept prompts.
     */
    fun canAcceptPrompts(): Boolean = getCurrentState().canAcceptPrompts()
    
    /**
     * Check if prompts should be queued.
     */
    fun shouldQueuePrompts(): Boolean = getCurrentState().shouldQueuePrompts()
    
    /**
     * Check if the engine can be initialized.
     */
    fun canInitialize(): Boolean = getCurrentState().canInitialize()
    
    /**
     * Check if the engine is in a terminal state.
     */
    fun isTerminal(): Boolean = getCurrentState().isTerminal()
    
    /**
     * Register a callback for state changes.
     */
    fun registerCallback(callback: EngineStateCallback) {
        callbacks.add(callback)
    }
    
    /**
     * Unregister a callback.
     */
    fun unregisterCallback(callback: EngineStateCallback) {
        callbacks.remove(callback)
    }
    
    /**
     * Attempt to transition to a new state.
     * 
     * @param newState The target state
     * @param reason Optional reason for the transition
     * @param error Optional error if transitioning to ERROR state
     * @return Result indicating success or failure with reason
     */
    suspend fun transitionTo(
        newState: EngineState,
        reason: String? = null,
        error: Throwable? = null
    ): Result<Unit> = stateMutex.withLock {
        val currentState = atomicState.get()
        
        // Validate transition
        if (!EngineState.isValidTransition(currentState, newState)) {
            val message = "Invalid state transition: $currentState -> $newState"
            Log.w(TAG, message)
            return Result.failure(IllegalStateException(message))
        }
        
        // Perform transition
        atomicState.set(newState)
        _stateFlow.value = newState
        
        // Create transition event
        val event = StateTransitionEvent(
            fromState = currentState,
            toState = newState,
            reason = reason,
            error = error
        )
        
        // Record in history
        recordTransition(event)
        
        // Notify callbacks
        notifyStateChanged(currentState, newState, event)
        
        // Send telemetry
        telemetryCallback?.invoke(event)
        
        Log.d(TAG, "State transition: $currentState -> $newState${reason?.let { " ($it)" } ?: ""}")
        
        Result.success(Unit)
    }
    
    /**
     * Update initialization progress.
     */
    fun updateProgress(progress: InitializationProgress) {
        _progressFlow.value = progress
        
        // Notify callbacks
        callbacks.forEach { callback ->
            try {
                callback.onProgressUpdate(progress)
            } catch (e: Exception) {
                Log.e(TAG, "Error in progress callback", e)
            }
        }
    }
    
    /**
     * Wait for a specific state with timeout.
     * 
     * @param targetState The state to wait for
     * @param timeoutMs Maximum time to wait in milliseconds
     * @return true if the state was reached, false if timeout
     */
    suspend fun waitForState(targetState: EngineState, timeoutMs: Long = 30000L): Boolean {
        val startTime = System.currentTimeMillis()
        
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            if (getCurrentState() == targetState) {
                return true
            }
            if (isTerminal() && targetState != getCurrentState()) {
                return false
            }
            kotlinx.coroutines.delay(50)
        }
        
        return false
    }
    
    /**
     * Wait for the engine to be ready (READY state) with timeout.
     */
    suspend fun waitForReady(timeoutMs: Long = 30000L): Boolean {
        return waitForState(EngineState.READY, timeoutMs)
    }
    
    /**
     * Wait for any terminal state with timeout.
     */
    suspend fun waitForTerminal(timeoutMs: Long = 30000L): Boolean {
        val startTime = System.currentTimeMillis()
        
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            if (isTerminal()) {
                return true
            }
            kotlinx.coroutines.delay(50)
        }
        
        return false
    }
    
    /**
     * Get the transition history (for debugging).
     */
    suspend fun getTransitionHistory(): List<StateTransitionEvent> = historyMutex.withLock {
        transitionHistory.toList()
    }
    
    /**
     * Clear the transition history.
     */
    suspend fun clearHistory() = historyMutex.withLock {
        transitionHistory.clear()
    }
    
    /**
     * Execute an action only if the current state matches the expected state.
     * 
     * @param expectedState The expected current state
     * @param action The action to execute
     * @return Result of the action or failure if state doesn't match
     */
    suspend fun <T> withState(expectedState: EngineState, action: suspend () -> T): Result<T> {
        return stateMutex.withLock {
            val currentState = atomicState.get()
            if (currentState != expectedState) {
                Result.failure(
                    IllegalStateException("Expected state $expectedState but was $currentState")
                )
            } else {
                Result.success(action())
            }
        }
    }
    
    /**
     * Execute an action if the current state is in the allowed set.
     * 
     * @param allowedStates Set of allowed states
     * @param action The action to execute
     * @return Result of the action or failure if state not allowed
     */
    suspend fun <T> withAnyState(allowedStates: Set<EngineState>, action: suspend () -> T): Result<T> {
        return stateMutex.withLock {
            val currentState = atomicState.get()
            if (currentState !in allowedStates) {
                Result.failure(
                    IllegalStateException("State $currentState not in allowed states: $allowedStates")
                )
            } else {
                Result.success(action())
            }
        }
    }
    
    // Private helper methods
    
    private suspend fun recordTransition(event: StateTransitionEvent) = historyMutex.withLock {
        transitionHistory.add(event)
        // Keep only last 100 transitions to prevent memory leaks
        if (transitionHistory.size > 100) {
            transitionHistory.removeAt(0)
        }
    }
    
    private fun notifyStateChanged(from: EngineState, to: EngineState, event: StateTransitionEvent) {
        callbacks.forEach { callback ->
            try {
                callback.onStateChanged(from, to, event)
            } catch (e: Exception) {
                Log.e(TAG, "Error in state callback", e)
            }
        }
        
        // Also notify error callbacks if transitioning to error state
        if (to == EngineState.ERROR && event.error != null) {
            callbacks.forEach { callback ->
                try {
                    callback.onError(event.error, to)
                } catch (e: Exception) {
                    Log.e(TAG, "Error in error callback", e)
                }
            }
        }
    }
}

/**
 * Exception thrown when an invalid state transition is attempted.
 */
class InvalidStateTransitionException(
    val fromState: EngineState,
    val toState: EngineState,
    message: String = "Invalid state transition: $fromState -> $toState"
) : IllegalStateException(message)

/**
 * Exception thrown when an operation is attempted in an inappropriate state.
 */
class InvalidStateException(
    val currentState: EngineState,
    val expectedStates: Set<EngineState>,
    message: String = "Operation not allowed in state $currentState. Expected: $expectedStates"
) : IllegalStateException(message)
