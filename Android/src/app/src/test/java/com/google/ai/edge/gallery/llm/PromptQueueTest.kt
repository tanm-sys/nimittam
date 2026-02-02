/*
 * Copyright 2025 Tanmay Patil
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.google.ai.edge.gallery.llm

import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PromptQueueTest {

    private lateinit var promptQueue: PromptQueue

    @Before
    fun setup() {
        promptQueue = PromptQueue(PromptQueueConfig.DEFAULT)
    }

    @After
    fun tearDown() {
        promptQueue.release()
    }

    @Test
    fun `initial queue should be empty`() {
        assertTrue(promptQueue.isEmpty())
        assertEquals(0, promptQueue.size())
    }

    @Test
    fun `enqueue should add prompt to queue`() = runTest {
        val result = promptQueue.enqueue("Test prompt")
        
        assertTrue(result is EnqueueResult.Success)
        assertEquals(1, promptQueue.size())
        assertFalse(promptQueue.isEmpty())
    }

    @Test
    fun `dequeue should remove and return prompt`() = runTest {
        promptQueue.enqueue("Test prompt")
        
        val prompt = promptQueue.dequeue()
        
        assertNotNull(prompt)
        assertEquals("Test prompt", prompt?.prompt)
        assertTrue(promptQueue.isEmpty())
    }

    @Test
    fun `dequeue from empty queue should return null`() = runTest {
        val prompt = promptQueue.dequeue()
        assertNull(prompt)
    }

    @Test
    fun `queue should respect max size limit`() = runTest {
        val config = PromptQueueConfig(maxSize = 2, overflowPolicy = QueueOverflowPolicy.REJECT)
        val limitedQueue = PromptQueue(config)
        
        // Enqueue 2 prompts (at limit)
        limitedQueue.enqueue("Prompt 1")
        limitedQueue.enqueue("Prompt 2")
        assertEquals(2, limitedQueue.size())
        
        // Third prompt should be rejected
        val result = limitedQueue.enqueue("Prompt 3")
        assertTrue(result is EnqueueResult.Rejected)
        assertEquals(2, limitedQueue.size())
        
        limitedQueue.release()
    }

    @Test
    fun `overflow policy DROP_OLDEST should remove oldest prompt`() = runTest {
        val config = PromptQueueConfig(maxSize = 2, overflowPolicy = QueueOverflowPolicy.DROP_OLDEST)
        val queue = PromptQueue(config)
        
        queue.enqueue("Prompt 1")
        queue.enqueue("Prompt 2")
        
        // Third prompt should drop oldest
        val result = queue.enqueue("Prompt 3")
        
        assertTrue(result is EnqueueResult.Dropped)
        assertEquals(2, queue.size())
        
        // Oldest should be removed
        val first = queue.dequeue()
        assertEquals("Prompt 2", first?.prompt)
        
        queue.release()
    }

    @Test
    fun `stopAccepting should prevent new prompts`() = runTest {
        promptQueue.enqueue("Prompt 1")
        promptQueue.stopAccepting()
        
        val result = promptQueue.enqueue("Prompt 2")
        
        assertTrue(result is EnqueueResult.Rejected)
        assertEquals(1, promptQueue.size())
    }

    @Test
    fun `clear should remove all prompts`() = runTest {
        promptQueue.enqueue("Prompt 1")
        promptQueue.enqueue("Prompt 2")
        promptQueue.enqueue("Prompt 3")
        
        assertEquals(3, promptQueue.size())
        
        promptQueue.clear()
        
        assertTrue(promptQueue.isEmpty())
        assertEquals(0, promptQueue.size())
    }

    @Test
    fun `remove should remove specific prompt`() = runTest {
        val result = promptQueue.enqueue("Test prompt")
        val promptId = (result as EnqueueResult.Success).promptId
        
        val removed = promptQueue.remove(promptId)
        
        assertTrue(removed)
        assertTrue(promptQueue.isEmpty())
    }

    @Test
    fun `remove non-existent prompt should return false`() = runTest {
        promptQueue.enqueue("Test prompt")
        
        val removed = promptQueue.remove("non-existent-id")
        
        assertFalse(removed)
        assertEquals(1, promptQueue.size())
    }

    @Test
    fun `expired prompts should be cleaned up on dequeue`() = runTest {
        val config = PromptQueueConfig(defaultTimeoutMs = 1) // 1ms timeout
        val queue = PromptQueue(config)
        
        queue.enqueue("Expired prompt")
        
        // Wait for expiration
        kotlinx.coroutines.delay(10)
        
        val prompt = queue.dequeue()
        
        assertNull(prompt)
        assertTrue(queue.isEmpty())
        
        queue.release()
    }

    @Test
    fun `priority ordering should be maintained`() = runTest {
        promptQueue.enqueue("Low priority", priority = PromptPriority.LOW)
        promptQueue.enqueue("High priority", priority = PromptPriority.HIGH)
        promptQueue.enqueue("Normal priority", priority = PromptPriority.NORMAL)
        
        // High priority should be first
        val first = promptQueue.dequeue()
        assertEquals("High priority", first?.prompt)
        
        // Normal priority should be second
        val second = promptQueue.dequeue()
        assertEquals("Normal priority", second?.prompt)
        
        // Low priority should be last
        val third = promptQueue.dequeue()
        assertEquals("Low priority", third?.prompt)
    }

    @Test
    fun `queue events should be emitted`() = runTest {
        promptQueue.events.test {
            promptQueue.enqueue("Test prompt")
            
            val event = awaitItem()
            assertTrue(event is PromptQueueEvent.PromptEnqueued)
            
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `statistics should track operations`() = runTest {
        promptQueue.enqueue("Prompt 1")
        promptQueue.enqueue("Prompt 2")
        promptQueue.dequeue()
        
        val stats = promptQueue.getStatistics()
        
        assertEquals(2, stats.totalEnqueued)
        assertEquals(1, stats.totalDequeued)
    }

    @Test
    fun `concurrent enqueue operations should be thread-safe`() = runTest {
        val iterations = 100
        val jobs = List(iterations) {
            launch {
                promptQueue.enqueue("Prompt $it")
            }
        }
        
        jobs.forEach { it.join() }
        
        assertEquals(iterations, promptQueue.size())
    }

    @Test
    fun `concurrent dequeue operations should be thread-safe`() = runTest {
        // Enqueue items first
        repeat(100) {
            promptQueue.enqueue("Prompt $it")
        }
        
        val dequeuedItems = mutableListOf<QueuedPrompt?>()
        val jobs = List(100) {
            launch {
                dequeuedItems.add(promptQueue.dequeue())
            }
        }
        
        jobs.forEach { it.join() }
        
        // All items should be dequeued (some may be null due to race conditions)
        assertTrue(promptQueue.isEmpty())
    }

    @Test
    fun `peek should not remove prompt`() = runTest {
        promptQueue.enqueue("Test prompt")
        
        val peeked = promptQueue.peek()
        val sizeAfterPeek = promptQueue.size()
        
        assertNotNull(peeked)
        assertEquals(1, sizeAfterPeek)
    }

    @Test
    fun `isFull should return true when at capacity`() = runTest {
        val config = PromptQueueConfig(maxSize = 2)
        val queue = PromptQueue(config)
        
        assertFalse(queue.isFull())
        
        queue.enqueue("Prompt 1")
        assertFalse(queue.isFull())
        
        queue.enqueue("Prompt 2")
        assertTrue(queue.isFull())
        
        queue.release()
    }

    @Test
    fun `queued prompt should track age correctly`() {
        val prompt = QueuedPrompt(
            id = "test",
            prompt = "Test",
            enqueueTime = System.currentTimeMillis() - 5000 // 5 seconds ago
        )
        
        assertTrue(prompt.ageMs() >= 5000)
        assertFalse(prompt.isExpired()) // Default timeout is 30s
    }

    @Test
    fun `queued prompt should detect expiration`() {
        val prompt = QueuedPrompt(
            id = "test",
            prompt = "Test",
            enqueueTime = System.currentTimeMillis() - 100,
            timeoutMs = 50 // 50ms timeout
        )
        
        assertTrue(prompt.isExpired())
    }
}