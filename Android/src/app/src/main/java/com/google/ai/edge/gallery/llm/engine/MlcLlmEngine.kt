/*
 * Copyright 2025 Tanmay Patil
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.google.ai.edge.gallery.llm.engine

import android.content.Context
import android.os.Build
import android.util.Log
import com.google.ai.edge.gallery.llm.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import java.io.File
import java.lang.ref.Cleaner
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * MLC-LLM Engine implementation with Vulkan/OpenCL GPU acceleration.
 * 
 * This engine provides the highest performance for on-device LLM inference
 * by leveraging Apache TVM's ML compilation for hardware-specific optimization.
 * 
 * Features:
 * - Integration with EngineLifecycleManager for robust initialization
 * - Thread-safe state management
 * - Prompt queuing during initialization
 * - Automatic retry with exponential backoff
 * - Native resource cleanup via Cleaner
 * 
 * Supports:
 * - Vulkan GPU backend (primary)
 * - OpenCL GPU backend (fallback for older devices)
 * - CPU backend (universal fallback)
 * 
 * Models must be pre-compiled using MLC-LLM toolchain for optimal performance.
 */
@Singleton
class MlcLlmEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val lifecycleManager: EngineLifecycleManager
) : LlmEngine {

    companion object {
        private const val TAG = "MlcLlmEngine"
        
        // Native library names
        private const val LIB_MLC_LLM = "mlc_llm"
        private const val LIB_TVM_RUNTIME = "tvm_runtime"
        private const val LIB_JNI = "mlc_llm_jni"
        
        // Shared Cleaner for native resource management
        private val cleaner = Cleaner.create()
        
        // Cache for supported backends to avoid repeated native checks
        @Volatile
        private var cachedBackends: List<HardwareBackend>? = null
        
        // Load native libraries
        init {
            try {
                // Try to load MLC-LLM core libraries (might be missing in partial builds)
                try {
                    System.loadLibrary(LIB_TVM_RUNTIME)
                    System.loadLibrary(LIB_MLC_LLM)
                } catch (e: UnsatisfiedLinkError) {
                    Log.w(TAG, "Core MLC-LLM libraries not found, functionality will be limited")
                }
                
                // Always load our JNI bridge
                System.loadLibrary(LIB_JNI)
                Log.i(TAG, "Native libraries loaded successfully")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Failed to load native libraries", e)
            }
        }
    }
    
    // Use AtomicLong to hold the native handle so the Cleaner can access the updated value.
    private val nativeHandleRef = AtomicLong(0L)

    // Legacy state - now delegated to lifecycleManager
    override val state: LlmEngineState
        get() = when (lifecycleManager.currentState) {
            EngineState.UNINITIALIZED -> LlmEngineState.UNINITIALIZED
            EngineState.INITIALIZING -> LlmEngineState.LOADING
            EngineState.READY -> LlmEngineState.READY
            EngineState.ERROR -> LlmEngineState.ERROR
            EngineState.SHUTTING_DOWN -> LlmEngineState.RELEASED
            EngineState.RELEASED -> LlmEngineState.RELEASED
        }

    private var _config: LlmEngineConfig = LlmEngineConfig()
    override val config: LlmEngineConfig get() = _config

    private var _lastMetrics: InferenceMetrics? = null
    override val lastMetrics: InferenceMetrics? get() = _lastMetrics

    // Native handle for backward compatibility
    private var nativeHandle: Long = 0L
    
    // Cleaner for automatic native resource cleanup
    private val cleanable: Cleaner.Cleanable
    
    // Generation control
    private var currentGenerationJob: Job? = null
    private val generationScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    // Conversation context
    private val conversationHistory = mutableListOf<ChatMessage>()
    
    // Initialization tracking
    private val isInitialized = AtomicBoolean(false)
    
    // Cached generation parameters to reduce JNI calls
    private data class CachedGenerationParams(
        val maxTokens: Int,
        val temperature: Float,
        val topP: Float,
        val topK: Int,
        val repeatPenalty: Float,
        val seed: Long
    )
    
    init {
        // Register with Cleaner for automatic native resource cleanup.
        cleanable = cleaner.register(this) {
            val handle = nativeHandleRef.get()
            if (handle != 0L) {
                try {
                    nativeRelease(handle)
                    Log.d(TAG, "Native handle cleaned up via Cleaner: $handle")
                } catch (e: Exception) {
                    Log.e(TAG, "Error cleaning up native handle", e)
                }
                nativeHandleRef.set(0L)
            }
        }
        
        // Register this engine with the lifecycle manager
        lifecycleManager.setEngine(this)
        
        Log.i(TAG, "MlcLlmEngine created with lifecycle management")
    }

    override suspend fun initialize(modelPath: String, config: LlmEngineConfig): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                _config = config
                
                Log.i(TAG, "Initializing MLC-LLM engine with model: $modelPath")
                Log.i(TAG, "Config: backend=${config.backend}, gpuLayers=${config.gpuLayers}, contextSize=${config.contextSize}")
                
                // Validate model path
                val modelFile = File(modelPath)
                if (!modelFile.exists()) {
                    return@withContext Result.failure(IllegalArgumentException("Model not found: $modelPath"))
                }
                
                // Detect optimal backend
                val actualBackend = selectOptimalBackend(config.backend)
                Log.i(TAG, "Selected backend: $actualBackend")
                
                // Check cancellation before native call to prevent resource leaks
                ensureActive()
                
                // Initialize native engine
                val handle = nativeInit(
                    modelPath = modelPath,
                    backend = actualBackend.ordinal,
                    gpuLayers = config.gpuLayers,
                    contextSize = config.contextSize,
                    batchSize = config.batchSize,
                    threads = config.threads,
                    useFlashAttention = config.useFlashAttention,
                    kvCacheType = config.kvCacheType.ordinal
                )
                nativeHandleRef.set(handle)
                nativeHandle = handle
                
                // Check cancellation after native call
                ensureActive()
                
                if (handle == 0L) {
                    return@withContext Result.failure(RuntimeException("Failed to initialize MLC-LLM engine"))
                }
                
                isInitialized.set(true)
                Log.i(TAG, "MLC-LLM engine initialized successfully")
                Result.success(Unit)
                
            } catch (e: CancellationException) {
                // Clean up native resources if initialization was cancelled
                cleanupNativeResources()
                Log.d(TAG, "Initialization cancelled")
                throw e
            } catch (e: Exception) {
                cleanupNativeResources()
                Log.e(TAG, "Failed to initialize MLC-LLM engine", e)
                Result.failure(e)
            }
        }
    }

    override fun generate(prompt: String, params: GenerationParams): Flow<GenerationResult> {
        return callbackFlow {
            // Check if engine is ready through lifecycle manager
            if (!lifecycleManager.canAcceptPrompts()) {
                // If initializing, queue the prompt
                if (lifecycleManager.shouldQueuePrompts()) {
                    val result = lifecycleManager.submitPrompt(prompt, null, params)
                    result.collect { send(it) }
                    close()
                    return@callbackFlow
                }
                
                val currentState = lifecycleManager.currentState
                trySend(GenerationResult.Error("Engine not ready, current state: $currentState"))
                close()
                return@callbackFlow
            }
            
            val startTime = System.currentTimeMillis()
            var promptTokens = 0
            var generatedTokens = 0
            var promptTimeMs = 0L
            
            // Cache generation parameters to reduce JNI call overhead
            val cachedParams = CachedGenerationParams(
                maxTokens = params.maxTokens,
                temperature = params.temperature,
                topP = params.topP,
                topK = params.topK,
                repeatPenalty = params.repeatPenalty,
                seed = params.seed
            )
            
            currentGenerationJob = generationScope.launch {
                try {
                    // Tokenize and process prompt
                    val promptStartTime = System.currentTimeMillis()
                    promptTokens = nativePrompt(nativeHandleRef.get(), prompt)
                    promptTimeMs = System.currentTimeMillis() - promptStartTime

                    Log.d(TAG, "Prompt processed: $promptTokens tokens in ${promptTimeMs}ms")

                    // Generate tokens using cached parameters for better performance
                    var shouldContinue = true
                    while (shouldContinue && isActive) {
                        val token = nativeGenerate(
                            handle = nativeHandleRef.get(),
                            maxTokens = cachedParams.maxTokens,
                            temperature = cachedParams.temperature,
                            topP = cachedParams.topP,
                            topK = cachedParams.topK,
                            repeatPenalty = cachedParams.repeatPenalty,
                            seed = cachedParams.seed
                        )
                        
                        if (token == null || token.isEmpty() || isStopSequence(token, params.stopSequences)) {
                            shouldContinue = false
                        } else {
                            generatedTokens++
                            trySend(GenerationResult.Token(token))
                        }
                        
                        if (generatedTokens >= cachedParams.maxTokens) {
                            shouldContinue = false
                        }
                    }
                    
                    // Calculate metrics
                    val totalTime = System.currentTimeMillis() - startTime
                    val generationTime = totalTime - promptTimeMs
                    
                    val metrics = InferenceMetrics(
                        promptTokens = promptTokens,
                        generatedTokens = generatedTokens,
                        promptTimeMs = promptTimeMs,
                        generationTimeMs = generationTime,
                        totalTimeMs = totalTime,
                        tokensPerSecond = if (generationTime > 0) generatedTokens * 1000f / generationTime else 0f,
                        promptTokensPerSecond = if (promptTimeMs > 0) promptTokens * 1000f / promptTimeMs else 0f,
                        memoryUsedMb = getMemoryUsage(),
                        backend = _config.backend
                    )
                    
                    _lastMetrics = metrics
                    trySend(GenerationResult.Complete(metrics))
                    
                } catch (e: CancellationException) {
                    Log.d(TAG, "Generation cancelled")
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Generation error", e)
                    trySend(GenerationResult.Error(e.message ?: "Unknown error", e))
                }
            }
            
            awaitClose {
                currentGenerationJob?.cancel()
            }
        }
    }

    override fun chat(messages: List<ChatMessage>, params: GenerationParams): Flow<GenerationResult> {
        // Build conversation prompt with chat template
        val prompt = buildChatPrompt(messages)
        return generate(prompt, params)
    }

    override suspend fun stopGeneration() {
        currentGenerationJob?.cancel()
        nativeStopGeneration(nativeHandleRef.get())
    }

    override suspend fun resetContext() {
        withContext(Dispatchers.IO) {
            nativeResetContext(nativeHandleRef.get())
            conversationHistory.clear()
        }
    }

    override suspend fun release() {
        // Cancel the generation scope to prevent coroutine leaks
        generationScope.cancel()

        withContext(Dispatchers.IO) {
            try {
                currentGenerationJob?.cancelAndJoin()
                cleanupNativeResources()
                cleanable.clean()
                Log.i(TAG, "MLC-LLM engine released")
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing engine", e)
            }
        }
    }

    override fun getSupportedBackends(): List<HardwareBackend> {
        // Return cached backends if available to avoid repeated native calls
        cachedBackends?.let { return it }
        
        val backends = mutableListOf<HardwareBackend>()
        
        // Always support CPU
        backends.add(HardwareBackend.CPU)
        
        // Check Vulkan support
        if (isVulkanSupported()) {
            backends.add(HardwareBackend.VULKAN_GPU)
        }
        
        // Check OpenCL support
        if (isOpenCLSupported()) {
            backends.add(HardwareBackend.OPENCL_GPU)
        }
        
        // Check NPU support (Qualcomm Hexagon)
        if (isHexagonNpuSupported()) {
            backends.add(HardwareBackend.NPU_HEXAGON)
        }
        
        // Cache the result for future calls
        cachedBackends = backends.toList()
        
        return backends
    }

    /**
     * Initialize the engine through the lifecycle manager.
     * This is the recommended way to initialize the engine.
     * 
     * @param modelPath Path to the model file
     * @param config Engine configuration
     * @return Result of initialization
     */
    suspend fun initializeWithLifecycle(
        modelPath: String,
        config: LlmEngineConfig = LlmEngineConfig()
    ): Result<Unit> {
        return lifecycleManager.initialize(modelPath, config)
    }

    /**
     * Submit a prompt for generation through the lifecycle manager.
     * This handles queuing during initialization automatically.
     * 
     * @param prompt The prompt text
     * @param messages Optional conversation history
     * @param params Generation parameters
     * @return Flow of generation results
     */
    fun submitPrompt(
        prompt: String,
        messages: List<ChatMessage>? = null,
        params: GenerationParams = GenerationParams()
    ): Flow<GenerationResult> {
        return flow {
            lifecycleManager.submitPrompt(prompt, messages, params).collect {
                emit(it)
            }
        }
    }

    /**
     * Get the current initialization progress.
     */
    val initializationProgress: StateFlow<InitializationProgress>
        get() = lifecycleManager.progressFlow

    /**
     * Get the current engine state.
     */
    val engineState: StateFlow<EngineState>
        get() = lifecycleManager.stateFlow

    /**
     * Wait for the engine to be ready.
     * 
     * @param timeoutMs Maximum time to wait in milliseconds
     * @return true if ready, false if timeout
     */
    suspend fun waitForReady(timeoutMs: Long = 30000L): Boolean {
        return lifecycleManager.waitForReady(timeoutMs)
    }

    /**
     * Check if the engine can accept prompts.
     */
    fun isReady(): Boolean = lifecycleManager.canAcceptPrompts()

    // ==================== Private Helper Methods ====================

    private fun cleanupNativeResources() {
        val handle = nativeHandleRef.get()
        if (handle != 0L) {
            try {
                nativeRelease(handle)
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing native handle during cleanup", e)
            }
            nativeHandleRef.set(0L)
            nativeHandle = 0L
        }
        isInitialized.set(false)
    }

    private fun selectOptimalBackend(preferred: HardwareBackend): HardwareBackend {
        val supported = getSupportedBackends()
        
        return when {
            preferred in supported -> preferred
            HardwareBackend.VULKAN_GPU in supported -> HardwareBackend.VULKAN_GPU
            HardwareBackend.OPENCL_GPU in supported -> HardwareBackend.OPENCL_GPU
            else -> HardwareBackend.CPU
        }
    }

    private fun isVulkanSupported(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && nativeCheckVulkan()
    }

    private fun isOpenCLSupported(): Boolean {
        return nativeCheckOpenCL()
    }

    private fun isHexagonNpuSupported(): Boolean {
        val chipset = Build.HARDWARE.lowercase()
        return chipset.contains("qcom") || chipset.contains("snapdragon")
    }

    private fun buildChatPrompt(messages: List<ChatMessage>): String {
        val sb = StringBuilder()
        
        // Use Llama-3 / Qwen chat template format
        for (message in messages) {
            when (message.role) {
                ChatRole.SYSTEM -> {
                    sb.append("<|start_header_id|>system<|end_header_id|>\n\n")
                    sb.append(message.content)
                    sb.append("<|eot_id|>")
                }
                ChatRole.USER -> {
                    sb.append("<|start_header_id|>user<|end_header_id|>\n\n")
                    sb.append(message.content)
                    sb.append("<|eot_id|>")
                }
                ChatRole.ASSISTANT -> {
                    sb.append("<|start_header_id|>assistant<|end_header_id|>\n\n")
                    sb.append(message.content)
                    sb.append("<|eot_id|>")
                }
            }
        }
        
        // Add assistant header for response
        sb.append("<|start_header_id|>assistant<|end_header_id|>\n\n")
        
        return sb.toString()
    }

    private fun isStopSequence(token: String, stopSequences: List<String>): Boolean {
        return stopSequences.any { stop -> token.contains(stop) }
    }

    private fun getMemoryUsage(): Long {
        val runtime = Runtime.getRuntime()
        return (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
    }

    // ==================== Native Methods ====================

    private external fun nativeInit(
        modelPath: String,
        backend: Int,
        gpuLayers: Int,
        contextSize: Int,
        batchSize: Int,
        threads: Int,
        useFlashAttention: Boolean,
        kvCacheType: Int
    ): Long

    private external fun nativePrompt(handle: Long, prompt: String): Int

    private external fun nativeGenerate(
        handle: Long,
        maxTokens: Int,
        temperature: Float,
        topP: Float,
        topK: Int,
        repeatPenalty: Float,
        seed: Long
    ): String?

    private external fun nativeStopGeneration(handle: Long)

    private external fun nativeResetContext(handle: Long)

    private external fun nativeRelease(handle: Long)

    private external fun nativeCheckVulkan(): Boolean

    private external fun nativeCheckOpenCL(): Boolean
}
