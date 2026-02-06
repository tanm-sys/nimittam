/*
 * Copyright 2025 Tanmay Patil
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.google.ai.edge.gallery.llm

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.PriorityBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Represents a queued prompt with metadata.
 */
data class QueuedPrompt(
    val id: String,
    val prompt: String,
    val messages: List<ChatMessage>? = null,
    val params: GenerationParams = GenerationParams(),
    val enqueueTime: Long = System.currentTimeMillis(),
    val timeoutMs: Long = 30000L,
    val priority: PromptPriority = PromptPriority.NORMAL
) {
    /**
     * Check if this prompt has timed out.
     */
    fun isExpired(): Boolean = System.currentTimeMillis() - enqueueTime > timeoutMs
    
    /**
     * Get the age of this prompt in milliseconds.
     */
    fun ageMs(): Long = System.currentTimeMillis() - enqueueTime
}

/**
 * Priority levels for queued prompts.
 */
enum class PromptPriority(val value: Int) {
    LOW(0),
    NORMAL(1),
    HIGH(2),
    CRITICAL(3)
}

/**
 * Result of enqueueing a prompt.
 */
sealed class EnqueueResult {
    /**
     * Prompt was successfully queued.
     */
    data class Success(val promptId: String, val position: Int) : EnqueueResult()
    
    /**
     * Prompt was rejected (queue full or not accepting).
     */
    data class Rejected(val reason: String) : EnqueueResult()
    
    /**
     * Prompt was dropped due to overflow policy.
     */
    data class Dropped(val droppedPromptId: String, val reason: String) : EnqueueResult()
    
    /**
     * Prompt was processed immediately (engine was ready).
     */
    data class ProcessedImmediately(val result: Flow<GenerationResult>) : EnqueueResult()
}

/**
 * Events emitted by the PromptQueue.
 */
sealed class PromptQueueEvent {
    data class PromptEnqueued(val promptId: String, val queueSize: Int) : PromptQueueEvent()
    data class PromptDequeued(val promptId: String, val queueSize: Int) : PromptQueueEvent()
    data class PromptProcessed(val promptId: String, val processingTimeMs: Long) : PromptQueueEvent()
    data class PromptExpired(val promptId: String, val ageMs: Long) : PromptQueueEvent()
    data class PromptRejected(val promptId: String, val reason: String) : PromptQueueEvent()
    data class PromptDropped(val promptId: String, val reason: String) : PromptQueueEvent()
    data class QueueOverflow(val droppedCount: Int) : PromptQueueEvent()
    object QueueCleared : PromptQueueEvent()
    object ProcessingStarted : PromptQueueEvent()
    object ProcessingStopped : PromptQueueEvent()
}

/**
 * Thread-safe queue for buffering prompts during engine initialization.
 * 
 * Features:
 * - Priority-based queuing
 * - Configurable size limits and overflow handling
 * - Timeout support for queued prompts
 * - Async processing when engine becomes ready
 * - Event-driven architecture for UI updates
 */
class PromptQueue(
    private val config: PromptQueueConfig = PromptQueueConfig.DEFAULT
) {
    companion object {
        private const val TAG = "PromptQueue"
    }
    
    // Internal priority queue - higher priority values are dequeued first
    // Uses a comparator that orders by priority (descending) then by enqueue time (ascending)
    private val queue = PriorityBlockingQueue<QueuedPrompt>(
        config.maxSize.coerceAtLeast(11), // Initial capacity (default 11 min)
        compareByDescending<QueuedPrompt> { it.priority.value }
            .thenBy { it.enqueueTime }
    )
    
    // Mutex for queue operations
    private val queueMutex = Mutex()
    
    // Atomic counter for prompt IDs
    private val idCounter = AtomicInteger(0)
    
    // Flag indicating if queue is accepting new prompts
    private val isAccepting = AtomicBoolean(true)
    
    // Flag indicating if queue is currently processing
    private val isProcessing = AtomicBoolean(false)
    
    // Event channel for queue events
    private val _events = MutableSharedFlow<PromptQueueEvent>(extraBufferCapacity = 64)
    val events: Flow<PromptQueueEvent> = _events.asSharedFlow()
    
    // Coroutine scope for queue processing
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    // Channel for triggering processing
    private val processChannel = Channel<Unit>(Channel.CONFLATED)
    
    // Statistics
    private val stats = QueueStatistics()
    
    init {
        // Start the processing loop
        startProcessingLoop()
    }
    
    /**
     * Get the current queue size.
     */
    fun size(): Int = queue.size
    
    /**
     * Check if the queue is empty.
     */
    fun isEmpty(): Boolean = queue.isEmpty()
    
    /**
     * Check if the queue is full.
     */
    fun isFull(): Boolean = queue.size >= config.maxSize
    
    /**
     * Check if the queue is accepting new prompts.
     */
    fun isAccepting(): Boolean = isAccepting.get()
    
    /**
     * Enqueue a prompt for later processing.
     * 
     * @param prompt The prompt text
     * @param messages Optional conversation history
     * @param params Generation parameters
     * @param priority Priority level
     * @param timeoutMs Timeout for this specific prompt
     * @return Result indicating success, rejection, or drop
     */
    suspend fun enqueue(
        prompt: String,
        messages: List<ChatMessage>? = null,
        params: GenerationParams = GenerationParams(),
        priority: PromptPriority = PromptPriority.NORMAL,
        timeoutMs: Long = config.defaultTimeoutMs
    ): EnqueueResult = queueMutex.withLock {
        
        if (!isAccepting.get()) {
            stats.recordRejection()
            return EnqueueResult.Rejected("Queue not accepting prompts")
        }
        
        // Check for expired prompts and remove them
        cleanupExpiredPrompts()
        
        // Check if queue is full
        if (isFull()) {
            return handleOverflow(prompt, messages, params, priority, timeoutMs)
        }
        
        // Create queued prompt
        val queuedPrompt = QueuedPrompt(
            id = generatePromptId(),
            prompt = prompt,
            messages = messages,
            params = params,
            timeoutMs = timeoutMs,
            priority = priority
        )
        
        // Add to queue (maintaining priority order)
        addWithPriority(queuedPrompt)
        
        val position = queue.size
        stats.recordEnqueue()
        _events.tryEmit(PromptQueueEvent.PromptEnqueued(queuedPrompt.id, position))
        
        Log.d(TAG, "Prompt enqueued: ${queuedPrompt.id}, position: $position, queue size: ${queue.size}")
        
        // Trigger processing
        processChannel.trySend(Unit)
        
        return EnqueueResult.Success(queuedPrompt.id, position)
    }
    
    /**
     * Dequeue the next prompt for processing.
     * 
     * @return The next prompt or null if queue is empty
     */
    suspend fun dequeue(): QueuedPrompt? = queueMutex.withLock {
        cleanupExpiredPrompts()
        
        val prompt = queue.poll()
        if (prompt != null) {
            stats.recordDequeue()
            _events.tryEmit(PromptQueueEvent.PromptDequeued(prompt.id, queue.size))
            Log.d(TAG, "Prompt dequeued: ${prompt.id}, remaining: ${queue.size}")
        }
        return prompt
    }
    
    /**
     * Peek at the next prompt without removing it.
     */
    fun peek(): QueuedPrompt? = queue.peek()
    
    /**
     * Remove a specific prompt from the queue.
     */
    suspend fun remove(promptId: String): Boolean = queueMutex.withLock {
        // Find the prompt first, then remove it
        val promptToRemove = queue.find { it.id == promptId }
        if (promptToRemove != null && queue.remove(promptToRemove)) {
            _events.tryEmit(PromptQueueEvent.PromptDropped(promptId, "Explicitly removed"))
            return true
        }
        return false
    }
    
    /**
     * Clear all prompts from the queue.
     */
    suspend fun clear() = queueMutex.withLock {
        val count = queue.size
        queue.clear()
        stats.recordClear(count)
        _events.tryEmit(PromptQueueEvent.QueueCleared)
        Log.d(TAG, "Queue cleared, removed $count prompts")
    }
    
    /**
     * Stop accepting new prompts.
     */
    fun stopAccepting() {
        isAccepting.set(false)
        Log.d(TAG, "Queue stopped accepting new prompts")
    }
    
    /**
     * Resume accepting new prompts.
     */
    fun resumeAccepting() {
        isAccepting.set(true)
        Log.d(TAG, "Queue resumed accepting new prompts")
    }
    
    /**
     * Process all queued prompts using the provided processor.
     * 
     * @param processor Function to process each prompt
     */
    suspend fun processAll(
        processor: suspend (QueuedPrompt) -> Flow<GenerationResult>
    ): Unit = queueMutex.withLock {
        if (isProcessing.getAndSet(true)) {
            Log.d(TAG, "Processing already in progress")
            return@withLock
        }
        
        _events.tryEmit(PromptQueueEvent.ProcessingStarted)
        
        try {
            while (true) {
                cleanupExpiredPrompts()
                
                val prompt = queue.poll() ?: break
                
                val startTime = System.currentTimeMillis()
                stats.recordDequeue()
                _events.tryEmit(PromptQueueEvent.PromptDequeued(prompt.id, queue.size))
                
                try {
                    processor(prompt)
                    val processingTime = System.currentTimeMillis() - startTime
                    stats.recordProcessing(processingTime)
                    _events.tryEmit(PromptQueueEvent.PromptProcessed(prompt.id, processingTime))
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing prompt ${prompt.id}", e)
                    stats.recordError()
                }
            }
        } finally {
            isProcessing.set(false)
            _events.tryEmit(PromptQueueEvent.ProcessingStopped)
        }
    }
    
    /**
     * Get current queue statistics.
     */
    fun getStatistics(): QueueStatistics.Snapshot = stats.snapshot()
    
    /**
     * Release all resources.
     */
    fun release() {
        stopAccepting()
        scope.cancel()
        queue.clear()
        Log.d(TAG, "PromptQueue released")
    }
    
    // Private helper methods
    
    private fun generatePromptId(): String {
        return "prompt_${System.currentTimeMillis()}_${idCounter.incrementAndGet()}"
    }
    
    private fun addWithPriority(prompt: QueuedPrompt) {
        // PriorityBlockingQueue automatically maintains priority ordering
        // via the comparator provided at construction time
        queue.add(prompt)
    }
    
    private suspend fun handleOverflow(
        prompt: String,
        messages: List<ChatMessage>?,
        params: GenerationParams,
        priority: PromptPriority,
        timeoutMs: Long
    ): EnqueueResult {
        return when (config.overflowPolicy) {
            QueueOverflowPolicy.REJECT -> {
                stats.recordRejection()
                val reason = "Queue full (max size: ${config.maxSize})"
                _events.tryEmit(PromptQueueEvent.PromptRejected("unknown", reason))
                EnqueueResult.Rejected(reason)
            }
            
            QueueOverflowPolicy.DROP_OLDEST -> {
                val oldest = queue.poll()
                if (oldest != null) {
                    _events.tryEmit(PromptQueueEvent.PromptDropped(oldest.id, "Dropped for new prompt (oldest)"))
                    _events.tryEmit(PromptQueueEvent.QueueOverflow(1))
                }
                
                val newPrompt = QueuedPrompt(
                    id = generatePromptId(),
                    prompt = prompt,
                    messages = messages,
                    params = params,
                    timeoutMs = timeoutMs,
                    priority = priority
                )
                addWithPriority(newPrompt)
                
                val position = queue.size
                stats.recordEnqueue()
                _events.tryEmit(PromptQueueEvent.PromptEnqueued(newPrompt.id, position))
                
                EnqueueResult.Dropped(
                    droppedPromptId = oldest?.id ?: "none",
                    reason = "Oldest prompt dropped to make room"
                )
            }
            
            QueueOverflowPolicy.DROP_NEWEST -> {
                stats.recordRejection()
                val reason = "Queue full, new prompt dropped"
                _events.tryEmit(PromptQueueEvent.PromptRejected("unknown", reason))
                EnqueueResult.Rejected(reason)
            }
        }
    }
    
    private fun cleanupExpiredPrompts() {
        // Collect expired prompts first to avoid concurrent modification issues
        // PriorityBlockingQueue's iterator is weakly consistent
        val expiredPrompts = queue.filter { it.isExpired() }
        
        expiredPrompts.forEach { prompt ->
            if (queue.remove(prompt)) {
                _events.tryEmit(PromptQueueEvent.PromptExpired(prompt.id, prompt.ageMs()))
                Log.d(TAG, "Expired prompt removed: ${prompt.id}, age: ${prompt.ageMs()}ms")
            }
        }
        
        if (expiredPrompts.isNotEmpty()) {
            stats.recordExpired(expiredPrompts.size)
        }
    }
    
    private fun startProcessingLoop() {
        scope.launch {
            for (event in processChannel) {
                // Processing is triggered, but actual processing happens via processAll()
                // This is a placeholder for future auto-processing capabilities
            }
        }
    }
}

/**
 * Statistics for the prompt queue.
 */
class QueueStatistics {
    private val totalEnqueued = AtomicInteger(0)
    private val totalDequeued = AtomicInteger(0)
    private val totalExpired = AtomicInteger(0)
    private val totalRejected = AtomicInteger(0)
    private val totalErrors = AtomicInteger(0)
    private val totalCleared = AtomicInteger(0)
    private val totalProcessingTimeMs = AtomicLong(0)
    
    fun recordEnqueue() {
        totalEnqueued.incrementAndGet()
    }
    
    fun recordDequeue() {
        totalDequeued.incrementAndGet()
    }
    
    fun recordExpired(count: Int = 1) {
        totalExpired.addAndGet(count)
    }
    
    fun recordRejection() {
        totalRejected.incrementAndGet()
    }
    
    fun recordError() {
        totalErrors.incrementAndGet()
    }
    
    fun recordClear(count: Int) {
        totalCleared.addAndGet(count)
    }
    
    fun recordProcessing(timeMs: Long) {
        totalProcessingTimeMs.addAndGet(timeMs)
    }
    
    fun snapshot(): Snapshot {
        val enqueued = totalEnqueued.get()
        val dequeued = totalDequeued.get()
        val processingTime = totalProcessingTimeMs.get()
        val processed = dequeued - totalExpired.get() - totalErrors.get()
        
        return Snapshot(
            totalEnqueued = enqueued,
            totalDequeued = dequeued,
            totalExpired = totalExpired.get(),
            totalRejected = totalRejected.get(),
            totalErrors = totalErrors.get(),
            totalCleared = totalCleared.get(),
            averageProcessingTimeMs = if (processed > 0) processingTime / processed else 0,
            successRate = if (dequeued > 0) (processed * 100.0 / dequeued) else 0.0
        )
    }
    
    data class Snapshot(
        val totalEnqueued: Int,
        val totalDequeued: Int,
        val totalExpired: Int,
        val totalRejected: Int,
        val totalErrors: Int,
        val totalCleared: Int,
        val averageProcessingTimeMs: Long,
        val successRate: Double
    )
}
