---
title: "API Specifications"
subtitle: "Interface Contracts and API Documentation"
version: "1.0.0"
date: "2026-02-01"
author: "Technical Architecture Team"
classification: "IEEE 830-1998"
status: "Active"
---

# API Specifications

## Table of Contents

1. [Introduction](#introduction)
2. [Core Interfaces](#core-interfaces)
3. [LLM Engine API](#llm-engine-api)
4. [Cache Management API](#cache-management-api)
5. [Performance Monitoring API](#performance-monitoring-api)
6. [Memory Management API](#memory-management-api)
7. [Data Access API](#data-access-api)
8. [UI Component API](#ui-component-api)
9. [Error Handling](#error-handling)
10. [Related Documents](#related-documents)

---

## Introduction

### Purpose

This document defines the interface contracts for all public APIs within the Nimittam Android application. It serves as the definitive reference for developers integrating with or extending the codebase.

### Scope

This specification covers:
- Core engine interfaces
- Service layer APIs
- Data access contracts
- UI component interfaces
- Error handling protocols

### Conventions

- **Interface**: Abstract contract defining behavior
- **Implementation**: Concrete class fulfilling interface contract
- **DTO**: Data Transfer Object for API parameters/returns
- **Flow**: Kotlin Flow for reactive streams
- **StateFlow**: Observable state holder

---

## Core Interfaces

### 2.1 Base Interface Patterns

All interfaces in the codebase follow these patterns:

1. **Suspension**: Async operations use `suspend` functions
2. **Result Types**: Operations return `Result<T>` for error handling
3. **Reactive Streams**: Streaming data uses `Flow<T>`
4. **State Observation**: Observable state uses `StateFlow<T>`

### 2.2 Common Data Types

```kotlin
// Result Types
sealed class GenerationResult {
    data class Token(val text: String) : GenerationResult()
    data class Complete(val metrics: InferenceMetrics) : GenerationResult()
    data class Error(val message: String) : GenerationResult()
}

// Configuration Types
data class GenerationParams(
    val maxTokens: Int = 1024,
    val temperature: Float = 0.7f,
    val topP: Float = 0.9f,
    val preferredBackend: HardwareBackend = HardwareBackend.VULKAN_GPU
)

data class ModelConfig(
    val modelId: String,
    val quantization: QuantizationMode,
    val contextLength: Int
)

// Message Types
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val content: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)
```

---

## LLM Engine API

### 3.1 LlmEngine Interface

**Package**: `com.google.ai.edge.gallery.llm`

**Purpose**: Primary interface for LLM inference operations

```kotlin
interface LlmEngine {
    /**
     * Current engine state
     */
    val state: StateFlow<LlmEngineState>
    
    /**
     * Last inference metrics
     */
    val lastMetrics: StateFlow<InferenceMetrics?>
    
    /**
     * Initialize the engine with model and configuration
     * 
     * @param modelPath Path to model files
     * @param config Model configuration
     * @return Result indicating success or failure
     * @throws IllegalStateException if already initialized
     */
    suspend fun initialize(
        modelPath: String, 
        config: ModelConfig
    ): Result<Unit>
    
    /**
     * Generate text from prompt with streaming output
     * 
     * @param prompt Input text prompt
     * @param params Generation parameters
     * @return Flow of generation results (tokens, completion, errors)
     * @throws IllegalStateException if not in READY state
     */
    fun generate(
        prompt: String, 
        params: GenerationParams
    ): Flow<GenerationResult>
    
    /**
     * Chat with message history
     * 
     * @param messages List of chat messages
     * @param params Generation parameters
     * @return Flow of generation results
     */
    fun chat(
        messages: List<ChatMessage>, 
        params: GenerationParams
    ): Flow<GenerationResult>
    
    /**
     * Stop ongoing generation
     */
    fun stopGeneration()
    
    /**
     * Reset conversation context
     */
    fun resetContext()
    
    /**
     * Release all resources
     */
    fun release()
}
```

### 3.2 LlmEngineState

```kotlin
sealed class LlmEngineState {
    abstract val isOperational: Boolean
    
    object UNINITIALIZED : LlmEngineState() {
        override val isOperational = false
    }
    
    object LOADING : LlmEngineState() {
        override val isOperational = false
    }
    
    object READY : LlmEngineState() {
        override val isOperational = true
    }
    
    object GENERATING : LlmEngineState() {
        override val isOperational = true
    }
    
    data class ERROR(
        val exception: Throwable
    ) : LlmEngineState() {
        override val isOperational = false
    }
    
    object RELEASED : LlmEngineState() {
        override val isOperational = false
    }
}
```

### 3.3 InferenceMetrics

```kotlin
data class InferenceMetrics(
    val promptTokens: Int,
    val generatedTokens: Int,
    val promptTimeMs: Long,
    val generationTimeMs: Long,
    val tokensPerSecond: Float,
    val backendUsed: HardwareBackend
)
```

### 3.4 HardwareBackend

```kotlin
enum class HardwareBackend {
    CPU,
    VULKAN_GPU,
    OPENCL_GPU,
    NPU_HEXAGON
}
```

---

## Cache Management API

### 4.1 CacheManager

**Package**: `com.google.ai.edge.gallery.data.cache`

**Purpose**: Multi-level caching with stale-while-revalidate support

```kotlin
interface CacheManager {
    /**
     * Get cached value by key
     * 
     * @param key Cache key
     * @return Cached value or null if not found/expired
     */
    suspend fun <T : Serializable> get(key: String): T?
    
    /**
     * Store value in cache
     * 
     * @param key Cache key
     * @param value Value to cache
     * @param ttl Time-to-live (null for default)
     */
    suspend fun <T : Serializable> put(
        key: String, 
        value: T, 
        ttl: Duration? = null
    )
    
    /**
     * Get with stale-while-revalidate pattern
     * 
     * @param key Cache key
     * @param fetcher Suspend function to fetch fresh data
     * @param forceRefresh Skip cache and force refresh
     * @return Flow emitting cached then fresh data
     */
    fun <T : Serializable> getWithSwr(
        key: String,
        fetcher: suspend () -> T,
        forceRefresh: Boolean = false
    ): Flow<CachedResource<T>>
    
    /**
     * Remove entry from cache
     */
    suspend fun remove(key: String): Boolean
    
    /**
     * Clear all cache levels
     */
    suspend fun clear()
    
    /**
     * Prefetch keys for anticipated access
     */
    fun prefetch(keys: List<String>, fetcher: suspend (String) -> Any)
    
    /**
     * Current cache statistics
     */
    val stats: StateFlow<CacheStats>
}
```

### 4.2 CachedResource

```kotlin
sealed class CachedResource<T> {
    data class Success<T>(
        val data: T,
        val isFromCache: Boolean,
        val isStale: Boolean
    ) : CachedResource<T>()
    
    data class Error<T>(
        val exception: Throwable,
        val cachedData: T? = null
    ) : CachedResource<T>()
    
    class Loading<T> : CachedResource<T>()
}
```

### 4.3 CacheStats

```kotlin
data class CacheStats(
    val totalHits: Long,
    val totalMisses: Long,
    val hitRate: Float,
    val l1Size: Int,
    val l2Size: Int,
    val memoryUsageBytes: Long
)
```

---

## Performance Monitoring API

### 5.1 PerformanceMonitor

**Package**: `com.google.ai.edge.gallery.performance`

**Purpose**: System performance monitoring and metrics collection

```kotlin
interface PerformanceMonitor {
    /**
     * Current frame metrics
     */
    val currentFrameMetrics: StateFlow<FrameMetrics?>
    
    /**
     * Current memory metrics
     */
    val currentMemoryMetrics: StateFlow<MemoryMetrics?>
    
    /**
     * Performance report history
     */
    val reports: StateFlow<List<PerformanceReport>>
    
    /**
     * Start monitoring
     */
    fun startMonitoring()
    
    /**
     * Stop monitoring
     */
    fun stopMonitoring()
    
    /**
     * Add performance listener
     */
    fun addListener(listener: PerformanceListener)
    
    /**
     * Remove performance listener
     */
    fun removeListener(listener: PerformanceListener)
    
    /**
     * Generate current performance report
     */
    fun generateReport(): PerformanceReport
}
```

### 5.2 PerformanceListener

```kotlin
interface PerformanceListener {
    fun onFrameTimeExceeded(threshold: Float, actual: Float)
    fun onMemoryWarning(level: MemoryWarningLevel)
    fun onAnrDetected(stackTrace: String)
    fun onPerformanceReport(report: PerformanceReport)
}
```

### 5.3 FrameMetrics

```kotlin
data class FrameMetrics(
    val frameTimeMs: Float,
    val isJank: Boolean,
    val timestamp: Long
) {
    companion object {
        fun fromFrameTime(frameTimeMs: Float): FrameMetrics =
            FrameMetrics(
                frameTimeMs = frameTimeMs,
                isJank = frameTimeMs > 16.67f,
                timestamp = System.currentTimeMillis()
            )
    }
}
```

### 5.4 MemoryMetrics

```kotlin
data class MemoryMetrics(
    val heapSize: Long,
    val heapUsed: Long,
    val nativeHeap: Long,
    val totalPss: Long,
    val availableMemory: Long,
    val timestamp: Long
)
```

### 5.5 PerformanceReport

```kotlin
data class PerformanceReport(
    val timestamp: Long,
    val averageFrameTime: Float,
    val jankFrames: Int,
    val totalFrames: Int,
    val memoryUsage: MemoryMetrics,
    val cpuUsage: Float
)
```

---

## Memory Management API

### 6.1 AdaptiveMemoryManager

**Package**: `com.google.ai.edge.gallery.util.memory`

**Purpose**: Dynamic memory pressure detection and adaptive response

```kotlin
interface AdaptiveMemoryManager {
    /**
     * Current memory pressure level
     */
    val memoryPressure: StateFlow<MemoryPressure>
    
    /**
     * Current memory profile
     */
    val currentProfile: StateFlow<MemoryProfile>
    
    /**
     * Current memory statistics
     */
    val memoryStats: StateFlow<MemoryStats>
    
    /**
     * Add memory pressure listener
     */
    fun addListener(listener: MemoryPressureListener)
    
    /**
     * Remove memory pressure listener
     */
    fun removeListener(listener: MemoryPressureListener)
    
    /**
     * Check if operation can be performed given memory requirements
     */
    fun canPerformOperation(requiredMemoryMB: Int): Boolean
    
    /**
     * Request memory trim
     */
    suspend fun trimMemory()
    
    /**
     * Start monitoring
     */
    fun startMonitoring()
    
    /**
     * Stop monitoring
     */
    fun stopMonitoring()
}
```

### 6.2 MemoryPressureListener

```kotlin
interface MemoryPressureListener {
    fun onMemoryPressureChanged(
        pressure: MemoryPressure, 
        profile: MemoryProfile
    )
}
```

### 6.3 MemoryPressure

```kotlin
enum class MemoryPressure {
    NORMAL,
    ELEVATED,
    HIGH,
    CRITICAL
}
```

### 6.4 MemoryProfile

```kotlin
data class MemoryProfile(
    val maxTokens: Int,
    val maxCacheSizeMB: Int,
    val enableAnimations: Boolean,
    val gcThreshold: Float
)
```

### 6.5 MemoryStats

```kotlin
data class MemoryStats(
    val totalMemoryMB: Long,
    val availableMemoryMB: Long,
    val usedMemoryMB: Long,
    val usagePercent: Float
)
```

---

## Data Access API

### 7.1 ChatHistoryRepository

**Package**: `com.google.ai.edge.gallery.data.db.repository`

**Purpose**: Conversation and message persistence

```kotlin
interface ChatHistoryRepository {
    /**
     * Get all conversations
     */
    suspend fun getConversations(): List<Conversation>
    
    /**
     * Get conversation by ID
     */
    suspend fun getConversation(id: Long): Conversation?
    
    /**
     * Save conversation
     * @return Conversation ID
     */
    suspend fun saveConversation(conversation: Conversation): Long
    
    /**
     * Delete conversation
     */
    suspend fun deleteConversation(id: Long)
    
    /**
     * Get messages for conversation
     */
    suspend fun getMessages(conversationId: Long): List<ChatMessage>
    
    /**
     * Save message
     * @return Message ID
     */
    suspend fun saveMessage(message: ChatMessage): Long
    
    /**
     * Delete all messages in conversation
     */
    suspend fun deleteMessages(conversationId: Long)
}
```

### 7.2 Conversation

```kotlin
data class Conversation(
    val id: Long = 0,
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val messageCount: Int = 0
)
```

### 7.3 DataStoreRepository

**Package**: `com.google.ai.edge.gallery.data`

**Purpose**: Typed preferences storage

```kotlin
interface DataStoreRepository {
    /**
     * Get string preference
     */
    suspend fun getString(key: String, default: String = ""): String
    
    /**
     * Set string preference
     */
    suspend fun setString(key: String, value: String)
    
    /**
     * Get int preference
     */
    suspend fun getInt(key: String, default: Int = 0): Int
    
    /**
     * Set int preference
     */
    suspend fun setInt(key: String, value: Int)
    
    /**
     * Get boolean preference
     */
    suspend fun getBoolean(key: String, default: Boolean = false): Boolean
    
    /**
     * Set boolean preference
     */
    suspend fun setBoolean(key: String, value: Boolean)
    
    /**
     * Remove preference
     */
    suspend fun remove(key: String)
    
    /**
     * Clear all preferences
     */
    suspend fun clear()
}
```

---

## UI Component API

### 8.1 ChatScreen

**Package**: `com.google.ai.edge.gallery.ui.screens.chat`

```kotlin
@Composable
fun ChatScreen(
    viewModel: ChatViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {}
)
```

### 8.2 ChatViewModel

```kotlin
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val llmEngine: LlmEngine,
    private val chatRepository: ChatHistoryRepository
) : ViewModel() {
    
    val messages: StateFlow<List<ChatMessage>>
    val isGenerating: StateFlow<Boolean>
    val error: StateFlow<String?>
    
    fun sendMessage(text: String)
    fun clearChat()
    fun retryLastMessage()
}
```

---

## Error Handling

### 9.1 Error Types

```kotlin
sealed class NimittamError : Exception() {
    // Engine errors
    class EngineNotInitialized : NimittamError()
    class EngineError(override val message: String) : NimittamError()
    class GenerationCancelled : NimittamError()
    
    // Memory errors
    class OutOfMemory(override val message: String) : NimittamError()
    class MemoryPressureCritical : NimittamError()
    
    // Cache errors
    class CacheError(override val message: String) : NimittamError()
    
    // Network errors (for model download if applicable)
    class NetworkError(override val message: String) : NimittamError()
}
```

### 9.2 Error Handling Patterns

1. **Result Type**: Use `Result<T>` for operations that can fail
2. **Flow Errors**: Emit `GenerationResult.Error` for stream errors
3. **State Errors**: Use `LlmEngineState.ERROR` for state machine errors
4. **Global Handler**: `CrashHandler` for uncaught exceptions

---

## Related Documents

| Document | Relationship | Description |
|----------|--------------|-------------|
| [Data Models](data-models.md) | Complements | Data structure definitions |
| [Traceability](traceability.md) | Maps to | Requirements traceability |
| [Architecture Overview](../architecture/overview.md) | Context | High-level architecture |
| [Components](../architecture/components.md) | Implements | Component details |
| [Glossary](../references/glossary.md) | Reference | Terminology |

---

*Document maintained by the Technical Architecture Team*  
*Last updated: 2026-02-01*  
*Classification: IEEE 830-1998*
