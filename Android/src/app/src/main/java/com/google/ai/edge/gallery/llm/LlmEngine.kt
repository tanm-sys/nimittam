/*
 * Copyright 2025 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.ai.edge.gallery.llm

import kotlinx.coroutines.flow.Flow

/**
 * Core LLM Engine interface for on-device inference.
 * Supports multiple backends: MLC-LLM, llama.cpp, LiteRT
 */
interface LlmEngine {
    
    /** Current engine state */
    val state: LlmEngineState
    
    /** Engine configuration */
    val config: LlmEngineConfig
    
    /** Performance metrics from last inference */
    val lastMetrics: InferenceMetrics?
    
    /**
     * Initialize the engine with a model.
     * @param modelPath Path to the model file
     * @param config Engine configuration
     */
    suspend fun initialize(modelPath: String, config: LlmEngineConfig): Result<Unit>
    
    /**
     * Generate a response for the given prompt.
     * Returns a Flow of tokens for streaming output.
     */
    fun generate(
        prompt: String,
        params: GenerationParams = GenerationParams()
    ): Flow<GenerationResult>
    
    /**
     * Generate with conversation history context.
     */
    fun chat(
        messages: List<ChatMessage>,
        params: GenerationParams = GenerationParams()
    ): Flow<GenerationResult>
    
    /**
     * Stop the current generation.
     */
    suspend fun stopGeneration()
    
    /**
     * Reset the KV cache to start a new conversation.
     */
    suspend fun resetContext()
    
    /**
     * Release all resources.
     */
    suspend fun release()
    
    /**
     * Get supported hardware backends.
     */
    fun getSupportedBackends(): List<HardwareBackend>
}

/**
 * Engine state
 */
enum class LlmEngineState {
    UNINITIALIZED,
    LOADING,
    READY,
    GENERATING,
    ERROR,
    RELEASED
}

/**
 * Hardware acceleration backends
 */
enum class HardwareBackend {
    CPU,
    VULKAN_GPU,
    OPENCL_GPU,
    NPU_HEXAGON,
    NPU_MEDIATEK,
    METAL_GPU  // iOS only
}

/**
 * Engine configuration
 */
data class LlmEngineConfig(
    val backend: HardwareBackend = HardwareBackend.VULKAN_GPU,
    val gpuLayers: Int = 32,
    val contextSize: Int = 4096,
    val batchSize: Int = 512,
    val threads: Int = 4,
    val useFlashAttention: Boolean = true,
    val kvCacheType: KvCacheType = KvCacheType.F16,
    val memoryLimit: Long = 0L  // 0 = auto
)

/**
 * KV Cache precision types
 */
enum class KvCacheType {
    F32,
    F16,
    Q8_0,
    Q4_0
}

/**
 * Generation parameters
 */
data class GenerationParams(
    val maxTokens: Int = 2048,
    val temperature: Float = 0.7f,
    val topP: Float = 0.9f,
    val topK: Int = 40,
    val repeatPenalty: Float = 1.1f,
    val stopSequences: List<String> = listOf("<|end|>", "<|eot_id|>", "</s>"),
    val seed: Long = -1L  // -1 = random
)

/**
 * Chat message for conversation
 */
data class ChatMessage(
    val role: ChatRole,
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)

enum class ChatRole {
    SYSTEM,
    USER,
    ASSISTANT
}

/**
 * Generation result - either a token or completion info
 */
sealed class GenerationResult {
    data class Token(val text: String, val logprob: Float = 0f) : GenerationResult()
    data class Complete(val metrics: InferenceMetrics) : GenerationResult()
    data class Error(val message: String, val cause: Throwable? = null) : GenerationResult()
}

/**
 * Performance metrics for inference
 */
data class InferenceMetrics(
    val promptTokens: Int,
    val generatedTokens: Int,
    val promptTimeMs: Long,
    val generationTimeMs: Long,
    val totalTimeMs: Long,
    val tokensPerSecond: Float,
    val promptTokensPerSecond: Float,
    val memoryUsedMb: Long,
    val backend: HardwareBackend
) {
    companion object {
        fun empty() = InferenceMetrics(
            promptTokens = 0,
            generatedTokens = 0,
            promptTimeMs = 0,
            generationTimeMs = 0,
            totalTimeMs = 0,
            tokensPerSecond = 0f,
            promptTokensPerSecond = 0f,
            memoryUsedMb = 0,
            backend = HardwareBackend.CPU
        )
    }
}
