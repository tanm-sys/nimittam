/*
 * Copyright 2025 Tanmay Patil
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.google.ai.edge.gallery.llm

/**
 * Represents the lifecycle states of the LLM engine.
 * 
 * This enum provides explicit state tracking with thread-safe transitions
 * to prevent race conditions during initialization and operation.
 */
enum class EngineState {
    /**
     * Engine has not been initialized yet.
     * This is the default state when the engine is first created.
     */
    UNINITIALIZED,
    
    /**
     * Engine initialization is in progress.
     * Prompts sent during this state will be queued for processing.
     */
    INITIALIZING,
    
    /**
     * Engine is fully initialized and ready to process prompts.
     * This is the operational state where normal inference can occur.
     */
    READY,
    
    /**
     * Engine has encountered an error during initialization or operation.
     * The engine may need to be reinitialized or the error must be resolved.
     */
    ERROR,
    
    /**
     * Engine is currently shutting down and releasing resources.
     * No new operations should be initiated during this state.
     */
    SHUTTING_DOWN,
    
    /**
     * Engine has been released and all resources cleaned up.
     * The engine instance should not be used after reaching this state.
     */
    RELEASED;
    
    /**
     * Check if the engine can accept new prompts in the current state.
     */
    fun canAcceptPrompts(): Boolean = this == READY
    
    /**
     * Check if the engine can be initialized from the current state.
     */
    fun canInitialize(): Boolean = this == UNINITIALIZED || this == ERROR
    
    /**
     * Check if the engine is in a terminal state (cannot transition to other states).
     */
    fun isTerminal(): Boolean = this == RELEASED
    
    /**
     * Check if prompts should be queued rather than rejected.
     */
    fun shouldQueuePrompts(): Boolean = this == INITIALIZING
    
    companion object {
        /**
         * Valid state transitions map.
         * Each state maps to the set of states it can transition to.
         */
        val VALID_TRANSITIONS: Map<EngineState, Set<EngineState>> = mapOf(
            UNINITIALIZED to setOf(INITIALIZING, RELEASED),
            INITIALIZING to setOf(READY, ERROR, SHUTTING_DOWN),
            READY to setOf(SHUTTING_DOWN, ERROR),
            ERROR to setOf(INITIALIZING, SHUTTING_DOWN),
            SHUTTING_DOWN to setOf(RELEASED, ERROR),
            RELEASED to emptySet()
        )
        
        /**
         * Check if a state transition is valid.
         */
        fun isValidTransition(from: EngineState, to: EngineState): Boolean {
            return VALID_TRANSITIONS[from]?.contains(to) ?: false
        }
    }
}

/**
 * Data class representing a state transition event.
 * Used for telemetry and logging of state changes.
 */
data class StateTransitionEvent(
    val fromState: EngineState,
    val toState: EngineState,
    val timestamp: Long = System.currentTimeMillis(),
    val reason: String? = null,
    val error: Throwable? = null
)

/**
 * Sealed class representing initialization progress.
 */
sealed class InitializationProgress {
    /**
     * Initialization has not started.
     */
    object NotStarted : InitializationProgress()
    
    /**
     * Initialization is in progress with a percentage (0-100).
     */
    data class InProgress(val percentage: Int, val message: String? = null) : InitializationProgress() {
        init {
            require(percentage in 0..100) { "Percentage must be between 0 and 100" }
        }
    }
    
    /**
     * Initialization completed successfully.
     */
    object Complete : InitializationProgress()
    
    /**
     * Initialization failed with an error.
     */
    data class Failed(val error: Throwable, val retryCount: Int = 0) : InitializationProgress()
    
    /**
     * Get the current percentage (0-100), or null if not applicable.
     */
    fun percentage(): Int? = when (this) {
        is InProgress -> percentage
        is Complete -> 100
        else -> null
    }
    
    /**
     * Check if initialization is complete (successfully or with failure).
     */
    fun isComplete(): Boolean = this is Complete || this is Failed
}

/**
 * Configuration for engine initialization retry policy.
 */
data class RetryPolicy(
    val maxRetries: Int = 3,
    val initialDelayMs: Long = 1000L,
    val maxDelayMs: Long = 30000L,
    val backoffMultiplier: Double = 2.0,
    val timeoutMs: Long = 30000L
) {
    /**
     * Calculate the delay for a specific retry attempt using exponential backoff.
     */
    fun calculateDelay(attempt: Int): Long {
        require(attempt >= 0) { "Attempt must be non-negative" }
        val delay = (initialDelayMs * Math.pow(backoffMultiplier, attempt.toDouble())).toLong()
        return delay.coerceAtMost(maxDelayMs)
    }
    
    companion object {
        /**
         * Default retry policy with conservative settings.
         */
        val DEFAULT = RetryPolicy()
        
        /**
         * Aggressive retry policy for faster recovery.
         */
        val AGGRESSIVE = RetryPolicy(
            maxRetries = 5,
            initialDelayMs = 500L,
            maxDelayMs = 10000L,
            backoffMultiplier = 1.5
        )
        
        /**
         * Conservative retry policy for resource-constrained environments.
         */
        val CONSERVATIVE = RetryPolicy(
            maxRetries = 2,
            initialDelayMs = 2000L,
            maxDelayMs = 60000L,
            backoffMultiplier = 2.5
        )
    }
}

/**
 * Configuration for prompt queue behavior.
 */
data class PromptQueueConfig(
    val maxSize: Int = 10,
    val defaultTimeoutMs: Long = 30000L,
    val overflowPolicy: QueueOverflowPolicy = QueueOverflowPolicy.DROP_OLDEST
) {
    companion object {
        val DEFAULT = PromptQueueConfig()
        val UNLIMITED = PromptQueueConfig(maxSize = Int.MAX_VALUE)
        val STRICT = PromptQueueConfig(maxSize = 5, overflowPolicy = QueueOverflowPolicy.REJECT)
    }
}

/**
 * Policy for handling queue overflow when max size is reached.
 */
enum class QueueOverflowPolicy {
    /**
     * Reject new prompts when queue is full.
     */
    REJECT,
    
    /**
     * Drop the oldest prompt to make room for new ones.
     */
    DROP_OLDEST,
    
    /**
     * Drop the newest prompt (the one being added).
     */
    DROP_NEWEST
}

/**
 * Interface for state change callbacks.
 */
interface EngineStateCallback {
    /**
     * Called when the engine state changes.
     */
    fun onStateChanged(from: EngineState, to: EngineState, event: StateTransitionEvent)
    
    /**
     * Called when initialization progress updates.
     */
    fun onProgressUpdate(progress: InitializationProgress) {}
    
    /**
     * Called when an error occurs.
     */
    fun onError(error: Throwable, state: EngineState) {}
}
