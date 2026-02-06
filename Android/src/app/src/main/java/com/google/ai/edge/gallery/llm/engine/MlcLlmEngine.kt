/*
 * Copyright 2025 Tanmay Patil
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.google.ai.edge.gallery.llm.engine

import android.content.Context
import android.util.Log
import ai.mlc.mlcllm.MLCEngine
import ai.mlc.mlcllm.OpenAIProtocol.*
import com.google.ai.edge.gallery.llm.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * MLC-LLM Engine implementation using the official MLC-LLM Android SDK.
 * 
 * This engine provides high-performance on-device LLM inference by leveraging
 * Apache TVM's ML compilation for hardware-specific optimization with OpenCL GPU.
 * 
 * Features:
 * - OpenAI-compatible chat completion API
 * - Streaming token generation
 * - Conversation context management
 * - Automatic model library loading
 */
@Singleton
class MlcLlmEngine @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val lifecycleManager: dagger.Lazy<EngineLifecycleManager>
) : LlmEngine {

    companion object {
        private const val TAG = "MlcLlmEngine"
        
        // Model configuration from mlc-app-config.json
        private const val MODEL_ID = "Qwen2.5-0.5B-Instruct-q4f16_1-MLC"
        private const val MODEL_LIB = "qwen2_q4f16_1_dbc9845947d563a3c13bf93ebf315c83"
    }
    
    // MLC-LLM Engine instance
    private var mlcEngine: MLCEngine? = null
    
    // Engine state management
    private val _state = MutableStateFlow(LlmEngineState.UNINITIALIZED)
    override val state: LlmEngineState
        get() = _state.value

    private var _config: LlmEngineConfig = LlmEngineConfig()
    override val config: LlmEngineConfig get() = _config

    private var _lastMetrics: InferenceMetrics? = null
    override val lastMetrics: InferenceMetrics? get() = _lastMetrics

    // Generation control
    private var currentGenerationJob: Job? = null
    private val generationScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    // Conversation context
    private val conversationHistory = mutableListOf<ChatCompletionMessage>()
    
    // Initialization tracking
    private val isInitialized = AtomicBoolean(false)
    private val isRegistered = AtomicBoolean(false)
    
    // Model path for reload
    private var currentModelPath: String? = null

    /**
     * Register this engine with the lifecycle manager.
     * Must be called before any initialization attempt.
     * This breaks the circular dependency by deferring registration.
     */
    fun ensureRegistered() {
        if (isRegistered.compareAndSet(false, true)) {
            lifecycleManager.get().setEngine(this)
            Log.i(TAG, "MlcLlmEngine registered with lifecycle manager")
        }
    }

    override suspend fun initialize(modelPath: String, config: LlmEngineConfig): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                _config = config
                _state.value = LlmEngineState.LOADING
                
                Log.i(TAG, "Initializing MLC-LLM engine with model: $modelPath")
                
                // Validate model path - must be a directory containing mlc-chat-config.json
                val modelDir = File(modelPath)
                val configFile = File(modelDir, "mlc-chat-config.json")
                
                if (!modelDir.exists() || !modelDir.isDirectory) {
                    val error = "Model directory not found: $modelPath"
                    Log.e(TAG, error)
                    _state.value = LlmEngineState.ERROR
                    return@withContext Result.failure(IllegalArgumentException(error))
                }
                
                if (!configFile.exists()) {
                    val error = "Model config not found: ${configFile.absolutePath}"
                    Log.e(TAG, error)
                    _state.value = LlmEngineState.ERROR
                    return@withContext Result.failure(IllegalArgumentException(error))
                }
                
                Log.i(TAG, "Model directory validated: $modelPath")
                Log.i(TAG, "Using model library: $MODEL_LIB")
                
                currentModelPath = modelPath
                
                // Create MLC Engine
                mlcEngine = MLCEngine()
                
                // Reload the model with the model path and library
                // modelPath must be the directory containing mlc-chat-config.json and weight shards
                // MODEL_LIB is the compiled model library prefix (system://<prefix>)
                mlcEngine?.reload(modelDir.absolutePath, MODEL_LIB)
                
                isInitialized.set(true)
                _state.value = LlmEngineState.READY
                
                Log.i(TAG, "MLC-LLM engine initialized successfully")
                Result.success(Unit)
                
            } catch (e: CancellationException) {
                _state.value = LlmEngineState.ERROR
                Log.d(TAG, "Initialization cancelled")
                throw e
            } catch (e: Exception) {
                _state.value = LlmEngineState.ERROR
                Log.e(TAG, "Failed to initialize MLC-LLM engine", e)
                Result.failure(e)
            }
        }
    }

    override fun generate(prompt: String, params: GenerationParams): Flow<GenerationResult> {
        return callbackFlow {
            // Check if engine is ready
            if (!isInitialized.get() || mlcEngine == null) {
                // If initializing, queue the prompt through lifecycle manager
                if (lifecycleManager.get().shouldQueuePrompts()) {
                    val result = lifecycleManager.get().submitPrompt(prompt, null, params)
                    result.collect { send(it) }
                    close()
                    return@callbackFlow
                }
                
                trySend(GenerationResult.Error("Engine not initialized"))
                close()
                return@callbackFlow
            }
            
            val startTime = System.currentTimeMillis()
            var generatedTokens = 0
            val responseBuilder = StringBuilder()
            
            currentGenerationJob = generationScope.launch {
                try {
                    // Build message for the API
                    val userMessage = ChatCompletionMessage(
                        role = ChatCompletionRole.user,
                        content = prompt
                    )
                    
                    val messages = conversationHistory.toMutableList()
                    messages.add(userMessage)
                    
                    // Create streaming chat completion request
                    val responseChannel = mlcEngine!!.chat.completions.create(
                        messages = messages,
                        temperature = params.temperature,
                        top_p = params.topP,
                        max_tokens = params.maxTokens,
                        stream = true,
                        stream_options = StreamOptions(include_usage = true)
                    )
                    
                    // Collect streaming responses
                    for (response in responseChannel) {
                        if (!isActive) break
                        
                        response.choices.firstOrNull()?.let { choice ->
                            choice.delta.content?.let { contentWrapper ->
                                val content = contentWrapper.asText()
                                if (content.isNotEmpty()) {
                                    generatedTokens++
                                    responseBuilder.append(content)
                                    trySend(GenerationResult.Token(content))
                                }
                            }
                            
                            // Check for finish reason
                            if (choice.finish_reason != null) {
                                Log.d(TAG, "Generation finished: ${choice.finish_reason}")
                            }
                        }
                        
                        // Handle usage stats in final message
                        response.usage?.let { usage ->
                            val totalTime = System.currentTimeMillis() - startTime
                            
                            val metrics = InferenceMetrics(
                                promptTokens = usage.prompt_tokens,
                                generatedTokens = usage.completion_tokens,
                                promptTimeMs = 0L, // Not available in streaming API
                                generationTimeMs = totalTime,
                                totalTimeMs = totalTime,
                                tokensPerSecond = usage.extra?.decode_tokens_per_s ?: 
                                    (if (totalTime > 0) usage.completion_tokens * 1000f / totalTime else 0f),
                                promptTokensPerSecond = usage.extra?.prefill_tokens_per_s ?: 0f,
                                memoryUsedMb = getMemoryUsage(),
                                backend = _config.backend
                            )
                            
                            _lastMetrics = metrics
                            Log.d(TAG, "Generation metrics: ${metrics.tokensPerSecond} tok/s")
                        }
                    }
                    
                    // Add assistant response to history
                    if (responseBuilder.isNotEmpty()) {
                        conversationHistory.add(userMessage)
                        conversationHistory.add(ChatCompletionMessage(
                            role = ChatCompletionRole.assistant,
                            content = responseBuilder.toString()
                        ))
                    }
                    
                    // Calculate final metrics if not provided by API
                    val totalTime = System.currentTimeMillis() - startTime
                    val finalMetrics = _lastMetrics ?: InferenceMetrics(
                        promptTokens = 0,
                        generatedTokens = generatedTokens,
                        promptTimeMs = 0L,
                        generationTimeMs = totalTime,
                        totalTimeMs = totalTime,
                        tokensPerSecond = if (totalTime > 0) generatedTokens * 1000f / totalTime else 0f,
                        promptTokensPerSecond = 0f,
                        memoryUsedMb = getMemoryUsage(),
                        backend = _config.backend
                    )
                    
                    _lastMetrics = finalMetrics
                    trySend(GenerationResult.Complete(finalMetrics))
                    
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
        // Convert ChatMessage to OpenAI format and generate
        val prompt = messages.lastOrNull { it.role == ChatRole.USER }?.content ?: ""
        
        // Add system and previous messages to conversation history
        conversationHistory.clear()
        messages.forEach { msg ->
            val role = when (msg.role) {
                ChatRole.SYSTEM -> ChatCompletionRole.system
                ChatRole.USER -> ChatCompletionRole.user
                ChatRole.ASSISTANT -> ChatCompletionRole.assistant
            }
            if (msg.role != ChatRole.USER || msg != messages.last()) {
                conversationHistory.add(ChatCompletionMessage(role = role, content = msg.content))
            }
        }
        
        return generate(prompt, params)
    }

    override suspend fun stopGeneration() {
        currentGenerationJob?.cancel()
        // MLC-LLM handles stop internally when channel is closed
    }

    override suspend fun resetContext() {
        withContext(Dispatchers.IO) {
            mlcEngine?.reset()
            conversationHistory.clear()
            Log.d(TAG, "Context reset")
        }
    }

    override suspend fun release() {
        generationScope.cancel()
        
        withContext(Dispatchers.IO) {
            try {
                currentGenerationJob?.cancelAndJoin()
                mlcEngine?.unload()
                mlcEngine = null
                isInitialized.set(false)
                _state.value = LlmEngineState.RELEASED
                Log.i(TAG, "MLC-LLM engine released")
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing engine", e)
            }
        }
    }

    override fun getSupportedBackends(): List<HardwareBackend> {
        // MLC-LLM on Android uses OpenCL GPU backend
        return listOf(
            HardwareBackend.OPENCL_GPU,
            HardwareBackend.CPU
        )
    }

    /**
     * Initialize the engine through the lifecycle manager.
     */
    suspend fun initializeWithLifecycle(
        modelPath: String,
        config: LlmEngineConfig = LlmEngineConfig()
    ): Result<Unit> {
        ensureRegistered()
        return lifecycleManager.get().initialize(modelPath, config)
    }

    /**
     * Submit a prompt for generation through the lifecycle manager.
     */
    fun submitPrompt(
        prompt: String,
        messages: List<ChatMessage>? = null,
        params: GenerationParams = GenerationParams()
    ): Flow<GenerationResult> {
        return flow {
            lifecycleManager.get().submitPrompt(prompt, messages, params).collect {
                emit(it)
            }
        }
    }

    /**
     * Get the current initialization progress.
     */
    val initializationProgress: StateFlow<InitializationProgress>
        get() = lifecycleManager.get().progressFlow

    /**
     * Get the current engine state.
     */
    val engineState: StateFlow<EngineState>
        get() = lifecycleManager.get().stateFlow

    /**
     * Wait for the engine to be ready.
     */
    suspend fun waitForReady(timeoutMs: Long = 30000L): Boolean {
        return lifecycleManager.get().waitForReady(timeoutMs)
    }

    /**
     * Check if the engine can accept prompts.
     */
    fun isReady(): Boolean = isInitialized.get() && mlcEngine != null

    private fun getMemoryUsage(): Long {
        val runtime = Runtime.getRuntime()
        return (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
    }
}
