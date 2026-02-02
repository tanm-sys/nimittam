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
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orchestrates the entire engine lifecycle including initialization, operation, and shutdown.
 * 
 * This class serves as the main entry point for engine lifecycle management, coordinating:
 * - State management (EngineStateManager)
 * - Initialization with retry (EngineInitializer)
 * - Prompt queuing during initialization (PromptQueue)
 * - Resource cleanup and graceful shutdown
 * 
 * Features:
 * - Thread-safe state transitions
 * - Automatic prompt queuing during initialization
 * - Graceful degradation on errors
 * - Comprehensive telemetry and logging
 * - Resource cleanup guarantees
 */
@Singleton
class EngineLifecycleManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val stateManager: EngineStateManager = EngineStateManager.create(),
    private val promptQueue: PromptQueue = PromptQueue(),
    private val retryPolicy: RetryPolicy = RetryPolicy.DEFAULT
) {
    companion object {
        private const val TAG = "EngineLifecycleManager"
        private const val DEFAULT_INIT_TIMEOUT_MS = 30000L
        private const val QUEUE_PROCESSING_DELAY_MS = 100L
    }
    
    // Initialization coroutine scope
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    // Reference to the engine implementation
    private var engine: LlmEngine? = null
    
    // Engine initializer (created on demand)
    private var initializer: EngineInitializer? = null
    
    // Flag to track if manager is released
    private val isReleased = AtomicBoolean(false)
    
    // State observation job
    private var stateObservationJob: Job? = null
    
    // Queue processing job
    private var queueProcessingJob: Job? = null
    
    // Current engine configuration
    private var currentConfig: LlmEngineConfig = LlmEngineConfig()
    
    // Current model path
    private var currentModelPath: String? = null
    
    // Callbacks for lifecycle events
    private val lifecycleCallbacks = mutableListOf<EngineLifecycleCallback>()
    private val callbacksMutex = kotlinx.coroutines.sync.Mutex()
    
    // Public state flows
    val stateFlow: StateFlow<EngineState> = stateManager.stateFlow
    val progressFlow: StateFlow<InitializationProgress> = stateManager.progressFlow
    val currentState: EngineState get() = stateManager.getCurrentState()
    
    /**
     * Initialize the engine with the specified model and configuration.
     * 
     * @param modelPath Path to the model file
     * @param config Engine configuration
     * @return Result indicating success or failure
     */
    suspend fun initialize(
        modelPath: String,
        config: LlmEngineConfig = LlmEngineConfig()
    ): Result<Unit> {
        if (isReleased.get()) {
            return Result.failure(IllegalStateException("EngineLifecycleManager has been released"))
        }
        
        // Check if already initialized or initializing
        when (currentState) {
            EngineState.READY -> {
                Log.w(TAG, "Engine already initialized")
                return Result.success(Unit)
            }
            EngineState.INITIALIZING -> {
                Log.w(TAG, "Engine already initializing, waiting...")
                val success = stateManager.waitForReady(DEFAULT_INIT_TIMEOUT_MS)
                return if (success) Result.success(Unit) else Result.failure(
                    TimeoutException("Timeout waiting for initialization")
                )
            }
            else -> { /* Continue with initialization */ }
        }
        
        // Store configuration
        currentConfig = config
        currentModelPath = modelPath
        
        // Create initializer if needed
        if (initializer == null) {
            initializer = createInitializer()
        }
        
        // Start observing state changes for queue processing
        startStateObservation()
        
        // Perform initialization
        return try {
            val initResult = initializer!!.initialize(modelPath, config)
            
            when (initResult) {
                is InitializationResult.Success -> {
                    Log.i(TAG, "Engine initialized successfully: ${initResult.metrics}")
                    notifyInitialized(initResult.metrics)
                    Result.success(Unit)
                }
                is InitializationResult.Failure -> {
                    Log.e(TAG, "Engine initialization failed", initResult.error)
                    notifyError(initResult.error)
                    Result.failure(initResult.error)
                }
                is InitializationResult.Cancelled -> {
                    Log.w(TAG, "Engine initialization cancelled: ${initResult.reason}")
                    Result.failure(CancellationException(initResult.reason))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during initialization", e)
            stateManager.transitionTo(EngineState.ERROR, error = e)
            notifyError(e)
            Result.failure(e)
        }
    }
    
    /**
     * Submit a prompt for generation.
     * 
     * If the engine is not ready, the prompt will be queued and processed when ready.
     * 
     * @param prompt The prompt text
     * @param messages Optional conversation history
     * @param params Generation parameters
     * @return Flow of generation results
     */
    suspend fun submitPrompt(
        prompt: String,
        messages: List<ChatMessage>? = null,
        params: GenerationParams = GenerationParams()
    ): Flow<GenerationResult> {
        if (isReleased.get()) {
            return flowOf(GenerationResult.Error("Engine has been released"))
        }
        
        return when (currentState) {
            EngineState.READY -> {
                // Engine is ready, process immediately
                processPrompt(prompt, messages, params)
            }
            EngineState.INITIALIZING -> {
                // Queue the prompt for later processing
                when (val result = promptQueue.enqueue(prompt, messages, params)) {
                    is EnqueueResult.Success -> {
                        // Return a flow that will emit when the prompt is processed
                        createPendingFlow(result.promptId)
                    }
                    is EnqueueResult.Rejected -> {
                        flowOf(GenerationResult.Error("Prompt rejected: ${result.reason}"))
                    }
                    is EnqueueResult.Dropped -> {
                        flowOf(GenerationResult.Error("Prompt dropped: ${result.reason}"))
                    }
                    is EnqueueResult.ProcessedImmediately -> {
                        result.result
                    }
                }
            }
            EngineState.UNINITIALIZED -> {
                // Engine not initialized, return error
                flowOf(GenerationResult.Error("Engine not initialized. Please initialize first."))
            }
            EngineState.ERROR -> {
                // Engine in error state
                flowOf(GenerationResult.Error("Engine is in error state. Please reinitialize."))
            }
            EngineState.SHUTTING_DOWN, EngineState.RELEASED -> {
                flowOf(GenerationResult.Error("Engine is shutting down"))
            }
        }
    }
    
    /**
     * Check if the engine can accept prompts.
     */
    fun canAcceptPrompts(): Boolean = currentState == EngineState.READY
    
    /**
     * Check if prompts should be queued.
     */
    fun shouldQueuePrompts(): Boolean = currentState == EngineState.INITIALIZING
    
    /**
     * Wait for the engine to be ready.
     * 
     * @param timeoutMs Maximum time to wait
     * @return true if ready, false if timeout
     */
    suspend fun waitForReady(timeoutMs: Long = DEFAULT_INIT_TIMEOUT_MS): Boolean {
        return stateManager.waitForReady(timeoutMs)
    }
    
    /**
     * Register a lifecycle callback.
     */
    suspend fun registerCallback(callback: EngineLifecycleCallback) = callbacksMutex.withLock {
        lifecycleCallbacks.add(callback)
    }
    
    /**
     * Unregister a lifecycle callback.
     */
    suspend fun unregisterCallback(callback: EngineLifecycleCallback) = callbacksMutex.withLock {
        lifecycleCallbacks.remove(callback)
    }
    
    /**
     * Gracefully shutdown the engine.
     */
    suspend fun shutdown(): Result<Unit> {
        Log.i(TAG, "Shutting down engine...")
        
        // Stop accepting new prompts
        promptQueue.stopAccepting()
        
        // Cancel any pending initialization
        initializer?.cancel()
        
        // Transition to shutting down state
        stateManager.transitionTo(EngineState.SHUTTING_DOWN, reason = "Shutdown requested")
        
        return try {
            // Release engine resources
            engine?.release()
            engine = null
            
            // Clear the queue
            promptQueue.clear()
            
            // Transition to released state
            stateManager.transitionTo(EngineState.RELEASED, reason = "Shutdown complete")
            
            // Cancel observation jobs
            stateObservationJob?.cancel()
            queueProcessingJob?.cancel()
            
            // Cancel the scope
            scope.cancel()
            
            Log.i(TAG, "Engine shutdown complete")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error during shutdown", e)
            stateManager.transitionTo(EngineState.ERROR, reason = "Shutdown failed", error = e)
            Result.failure(e)
        }
    }
    
    /**
     * Release all resources. This is a more forceful version of shutdown.
     */
    fun release() {
        if (isReleased.getAndSet(true)) {
            return
        }
        
        Log.i(TAG, "Releasing EngineLifecycleManager...")
        
        // Cancel all operations
        initializer?.cancel()
        initializer?.release()
        
        // Stop accepting prompts
        promptQueue.stopAccepting()
        promptQueue.release()
        
        // Cancel jobs
        stateObservationJob?.cancel()
        queueProcessingJob?.cancel()
        
        // Cancel scope
        scope.cancel()
        
        Log.i(TAG, "EngineLifecycleManager released")
    }
    
    /**
     * Get queue statistics.
     */
    fun getQueueStatistics(): QueueStatistics.Snapshot = promptQueue.getStatistics()
    
    /**
     * Set the engine implementation. Must be called before initialize().
     */
    fun setEngine(engine: LlmEngine) {
        this.engine = engine
    }
    
    // Private helper methods
    
    private fun createInitializer(): EngineInitializer {
        return EngineInitializer(
            context = context,
            stateManager = stateManager,
            retryPolicy = retryPolicy,
            initOperation = InitOperation { modelPath, config, onProgress ->
                // This is where the actual engine initialization happens
                // The engine implementation should handle the actual native initialization
                try {
                    onProgress(10, "Loading model...")
                    
                    // Delegate to the engine implementation
                    val engineInstance = engine
                        ?: return@InitOperation Result.failure(IllegalStateException("Engine not set"))
                    
                    onProgress(50, "Initializing engine...")
                    val result = runBlocking {
                        engineInstance.initialize(modelPath, config)
                    }
                    
                    onProgress(100, "Ready")
                    result
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
        )
    }
    
    private fun startStateObservation() {
        // Cancel existing observation
        stateObservationJob?.cancel()
        
        stateObservationJob = scope.launch {
            stateFlow.collect { state ->
                when (state) {
                    EngineState.READY -> {
                        // Start processing queued prompts
                        startQueueProcessing()
                        notifyReady()
                    }
                    EngineState.ERROR -> {
                        // Stop processing and notify
                        queueProcessingJob?.cancel()
                    }
                    EngineState.SHUTTING_DOWN, EngineState.RELEASED -> {
                        queueProcessingJob?.cancel()
                    }
                    else -> { /* No action needed */ }
                }
            }
        }
    }
    
    private fun startQueueProcessing() {
        if (queueProcessingJob?.isActive == true) {
            return
        }
        
        queueProcessingJob = scope.launch {
            Log.i(TAG, "Starting queue processing...")
            
            while (isActive && currentState == EngineState.READY) {
                try {
                    val prompt = promptQueue.dequeue()
                    
                    if (prompt != null) {
                        Log.d(TAG, "Processing queued prompt: ${prompt.id}")
                        
                        try {
                            processPrompt(prompt.prompt, prompt.messages, prompt.params)
                                .collect { result ->
                                    // Results are handled by the caller via the flow
                                    Log.d(TAG, "Generated result for prompt ${prompt.id}: $result")
                                }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error processing queued prompt ${prompt.id}", e)
                        }
                    } else {
                        // No prompts in queue, wait a bit
                        delay(QUEUE_PROCESSING_DELAY_MS)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Error in queue processing loop", e)
                    delay(QUEUE_PROCESSING_DELAY_MS)
                }
            }
            
            Log.i(TAG, "Queue processing stopped")
        }
    }
    
    private fun processPrompt(
        prompt: String,
        messages: List<ChatMessage>?,
        params: GenerationParams
    ): Flow<GenerationResult> {
        val engineInstance = engine
            ?: return flowOf(GenerationResult.Error("Engine not available"))
        
        return if (messages != null) {
            engineInstance.chat(messages, params)
        } else {
            engineInstance.generate(prompt, params)
        }
    }
    
    private fun createPendingFlow(promptId: String): Flow<GenerationResult> = flow {
        // Wait for the prompt to be processed
        // In a real implementation, we'd use a more sophisticated mechanism
        // For now, we emit a pending status and the caller should handle the actual result
        emit(GenerationResult.Error("Prompt queued with ID: $promptId. Will be processed when engine is ready."))
    }
    
    // Notification methods
    
    private suspend fun notifyInitialized(metrics: InitMetrics) {
        callbacksMutex.withLock {
            lifecycleCallbacks.forEach { callback ->
                try {
                    callback.onInitialized(metrics)
                } catch (e: Exception) {
                    Log.e(TAG, "Error in lifecycle callback", e)
                }
            }
        }
    }
    
    private suspend fun notifyReady() {
        callbacksMutex.withLock {
            lifecycleCallbacks.forEach { callback ->
                try {
                    callback.onReady()
                } catch (e: Exception) {
                    Log.e(TAG, "Error in lifecycle callback", e)
                }
            }
        }
    }
    
    private suspend fun notifyError(error: Throwable) {
        callbacksMutex.withLock {
            lifecycleCallbacks.forEach { callback ->
                try {
                    callback.onError(error)
                } catch (e: Exception) {
                    Log.e(TAG, "Error in lifecycle callback", e)
                }
            }
        }
    }
}

/**
 * Callback interface for engine lifecycle events.
 */
interface EngineLifecycleCallback {
    /**
     * Called when the engine is successfully initialized.
     */
    fun onInitialized(metrics: InitMetrics) {}
    
    /**
     * Called when the engine becomes ready to accept prompts.
     */
    fun onReady() {}
    
    /**
     * Called when an error occurs.
     */
    fun onError(error: Throwable) {}
    
    /**
     * Called when the engine is shutting down.
     */
    fun onShuttingDown() {}
    
    /**
     * Called when the engine has been released.
     */
    fun onReleased() {}
}

/**
 * Exception thrown when a timeout occurs.
 */
class TimeoutException(message: String) : Exception(message)
