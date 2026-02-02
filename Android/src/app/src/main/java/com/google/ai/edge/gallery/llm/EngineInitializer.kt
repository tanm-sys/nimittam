/*
 * Copyright 2025 Tanmay Patil
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.google.ai.edge.gallery.llm

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Result of an initialization attempt.
 */
sealed class InitializationResult {
    /**
     * Initialization succeeded.
     */
    data class Success(val metrics: InitMetrics) : InitializationResult()
    
    /**
     * Initialization failed.
     */
    data class Failure(val error: Throwable, val retryCount: Int, val willRetry: Boolean) : InitializationResult()
    
    /**
     * Initialization was cancelled.
     */
    data class Cancelled(val reason: String) : InitializationResult()
}

/**
 * Metrics collected during initialization.
 */
data class InitMetrics(
    val startTime: Long,
    val endTime: Long,
    val retryCount: Int,
    val backend: HardwareBackend,
    val modelPath: String
) {
    val durationMs: Long get() = endTime - startTime
}

/**
 * Functional interface for the actual initialization operation.
 */
fun interface InitOperation {
    suspend operator fun invoke(
        modelPath: String,
        config: LlmEngineConfig,
        onProgress: (Int, String?) -> Unit
    ): Result<Unit>
}

/**
 * Handles engine initialization with retry logic, timeouts, and progress tracking.
 * 
 * Features:
 * - Exponential backoff retry
 * - Configurable timeouts
 * - Progress callbacks
 * - Cancellation support
 * - Comprehensive error handling
 */
class EngineInitializer(
    private val context: Context,
    private val stateManager: EngineStateManager,
    private val retryPolicy: RetryPolicy = RetryPolicy.DEFAULT,
    private val initOperation: InitOperation
) {
    companion object {
        private const val TAG = "EngineInitializer"
    }
    
    // Mutex to prevent concurrent initialization attempts
    private val initMutex = Mutex()
    
    // Flag to track if initialization is in progress
    private val isInitializing = AtomicBoolean(false)
    
    // Current initialization job (for cancellation)
    private val currentJob = AtomicReference<Job?>(null)
    
    // Retry counter
    private val retryCounter = AtomicInteger(0)
    
    // Progress tracking
    private val _progressFlow = MutableStateFlow<InitializationProgress>(InitializationProgress.NotStarted)
    val progressFlow: StateFlow<InitializationProgress> = _progressFlow.asStateFlow()
    
    // Initialization scope
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    /**
     * Check if initialization is currently in progress.
     */
    fun isInitializing(): Boolean = isInitializing.get()
    
    /**
     * Get the current retry count.
     */
    fun getRetryCount(): Int = retryCounter.get()
    
    /**
     * Initialize the engine with retry logic and timeout handling.
     * 
     * @param modelPath Path to the model
     * @param config Engine configuration
     * @return Result of initialization
     */
    suspend fun initialize(
        modelPath: String,
        config: LlmEngineConfig
    ): InitializationResult = initMutex.withLock {
        // Check if already initialized
        if (stateManager.getCurrentState() == EngineState.READY) {
            Log.w(TAG, "Engine already initialized")
            return InitializationResult.Success(
                InitMetrics(0, 0, 0, config.backend, modelPath)
            )
        }
        
        // Check if initialization is already in progress
        if (isInitializing.getAndSet(true)) {
            Log.w(TAG, "Initialization already in progress")
            return InitializationResult.Failure(
                error = IllegalStateException("Initialization already in progress"),
                retryCount = retryCounter.get(),
                willRetry = false
            )
        }
        
        // Reset retry counter
        retryCounter.set(0)
        
        // Transition to INITIALIZING state
        val transitionResult = stateManager.transitionTo(
            EngineState.INITIALIZING,
            reason = "Starting initialization"
        )
        
        if (transitionResult.isFailure) {
            isInitializing.set(false)
            return InitializationResult.Failure(
                error = transitionResult.exceptionOrNull() ?: IllegalStateException("State transition failed"),
                retryCount = 0,
                willRetry = false
            )
        }
        
        // Start initialization with retry loop
        val startTime = System.currentTimeMillis()
        var lastError: Throwable? = null
        
        try {
            while (retryCounter.get() <= retryPolicy.maxRetries) {
                val attempt = retryCounter.get()
                
                Log.i(TAG, "Initialization attempt ${attempt + 1}/${retryPolicy.maxRetries + 1}")
                
                // Create job for this attempt
                val job = scope.launch {
                    try {
                        updateProgress(0, "Starting initialization (attempt ${attempt + 1})...")
                        
                        // Perform initialization with timeout
                        val result = withTimeout(retryPolicy.timeoutMs) {
                            initOperation(modelPath, config) { progress, message ->
                                updateProgress(progress, message)
                            }
                        }
                        
                        if (result.isSuccess) {
                            // Success! Transition to READY state
                            stateManager.transitionTo(
                                EngineState.READY,
                                reason = "Initialization completed successfully"
                            )
                            updateProgress(100, "Initialization complete")
                        } else {
                            throw result.exceptionOrNull() ?: RuntimeException("Unknown initialization error")
                        }
                        
                    } catch (e: TimeoutCancellationException) {
                        Log.e(TAG, "Initialization timeout on attempt ${attempt + 1}", e)
                        throw InitTimeoutException("Initialization timed out after ${retryPolicy.timeoutMs}ms", e)
                    } catch (e: CancellationException) {
                        Log.d(TAG, "Initialization cancelled on attempt ${attempt + 1}")
                        throw e
                    } catch (e: Exception) {
                        Log.e(TAG, "Initialization failed on attempt ${attempt + 1}", e)
                        throw e
                    }
                }
                
                currentJob.set(job)
                
                // Wait for completion
                try {
                    job.join()
                    
                    // Check if successful
                    if (stateManager.getCurrentState() == EngineState.READY) {
                        val metrics = InitMetrics(
                            startTime = startTime,
                            endTime = System.currentTimeMillis(),
                            retryCount = attempt,
                            backend = config.backend,
                            modelPath = modelPath
                        )
                        
                        Log.i(TAG, "Initialization succeeded after ${attempt + 1} attempt(s), took ${metrics.durationMs}ms")
                        return InitializationResult.Success(metrics)
                    }
                } catch (e: CancellationException) {
                    Log.d(TAG, "Initialization cancelled")
                    stateManager.transitionTo(
                        EngineState.ERROR,
                        reason = "Initialization cancelled",
                        error = e
                    )
                    return InitializationResult.Cancelled("User cancelled")
                } catch (e: Exception) {
                    lastError = e
                    
                    // Check if we should retry
                    if (attempt < retryPolicy.maxRetries && shouldRetry(e)) {
                        retryCounter.incrementAndGet()
                        val delayMs = retryPolicy.calculateDelay(attempt)
                        
                        Log.i(TAG, "Retrying initialization in ${delayMs}ms...")
                        updateProgress(0, "Retrying in ${delayMs / 1000}s... (attempt ${attempt + 2})")
                        
                        delay(delayMs)
                        
                        // Reset state for retry
                        if (stateManager.getCurrentState() != EngineState.INITIALIZING) {
                            stateManager.transitionTo(
                                EngineState.INITIALIZING,
                                reason = "Retrying initialization"
                            )
                        }
                    } else {
                        // No more retries
                        break
                    }
                } finally {
                    currentJob.set(null)
                }
            }
            
            // All retries exhausted
            Log.e(TAG, "Initialization failed after ${retryCounter.get() + 1} attempt(s)", lastError)
            
            stateManager.transitionTo(
                EngineState.ERROR,
                reason = "Initialization failed after ${retryCounter.get() + 1} attempts",
                error = lastError
            )
            
            updateProgress(InitializationProgress.Failed(lastError ?: RuntimeException("Unknown error"), retryCounter.get()))
            
            return InitializationResult.Failure(
                error = lastError ?: RuntimeException("Initialization failed"),
                retryCount = retryCounter.get(),
                willRetry = false
            )
            
        } finally {
            isInitializing.set(false)
        }
    }
    
    /**
     * Cancel the current initialization attempt.
     */
    fun cancel() {
        currentJob.get()?.cancel()
        Log.d(TAG, "Initialization cancellation requested")
    }
    
    /**
     * Release all resources.
     */
    fun release() {
        cancel()
        scope.cancel()
        Log.d(TAG, "EngineInitializer released")
    }
    
    // Private helper methods
    
    private fun updateProgress(percentage: Int, message: String? = null) {
        _progressFlow.value = InitializationProgress.InProgress(percentage, message)
        stateManager.updateProgress(InitializationProgress.InProgress(percentage, message))
    }
    
    private fun updateProgress(progress: InitializationProgress) {
        _progressFlow.value = progress
        stateManager.updateProgress(progress)
    }
    
    private fun shouldRetry(error: Throwable): Boolean {
        // Don't retry on certain errors
        return when (error) {
            is InitTimeoutException -> true  // Timeout can be transient
            is IllegalArgumentException -> false  // Bad arguments won't fix themselves
            is IllegalStateException -> false  // Bad state is a programming error
            is SecurityException -> false  // Security issues won't fix themselves
            else -> true  // Retry on other errors by default
        }
    }
}

/**
 * Exception thrown when initialization times out.
 */
class InitTimeoutException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Exception thrown when maximum retry count is exceeded.
 */
class MaxRetriesExceededException(
    val retryCount: Int,
    message: String = "Initialization failed after $retryCount retries",
    cause: Throwable? = null
) : Exception(message, cause)

/**
 * Builder for creating EngineInitializer instances.
 */
class EngineInitializerBuilder {
    private var retryPolicy: RetryPolicy = RetryPolicy.DEFAULT
    private var initOperation: InitOperation? = null
    
    fun retryPolicy(policy: RetryPolicy) = apply { this.retryPolicy = policy }
    fun initOperation(operation: InitOperation) = apply { this.initOperation = operation }
    
    fun build(context: Context, stateManager: EngineStateManager): EngineInitializer {
        val operation = initOperation ?: throw IllegalStateException("InitOperation must be set")
        return EngineInitializer(context, stateManager, retryPolicy, operation)
    }
}
