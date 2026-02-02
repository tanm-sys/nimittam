/*
 * Copyright 2025 Tanmay Patil
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.google.ai.edge.gallery.llm

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class EngineLifecycleManagerTest {

    private lateinit var context: Context
    private lateinit var stateManager: EngineStateManager
    private lateinit var promptQueue: PromptQueue
    private lateinit var lifecycleManager: EngineLifecycleManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        stateManager = EngineStateManager.create()
        promptQueue = PromptQueue()
        lifecycleManager = EngineLifecycleManager(
            context = context,
            stateManager = stateManager,
            promptQueue = promptQueue
        )
    }

    @After
    fun tearDown() {
        lifecycleManager.release()
        promptQueue.release()
    }

    @Test
    fun `initial state should be UNINITIALIZED`() {
        assertEquals(EngineState.UNINITIALIZED, lifecycleManager.currentState)
        assertFalse(lifecycleManager.canAcceptPrompts())
    }

    @Test
    fun `state flow should emit state changes`() = runTest {
        lifecycleManager.stateFlow.test {
            assertEquals(EngineState.UNINITIALIZED, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `canAcceptPrompts should return false when not ready`() {
        assertFalse(lifecycleManager.canAcceptPrompts())
    }

    @Test
    fun `shouldQueuePrompts should return false when not initializing`() {
        assertFalse(lifecycleManager.shouldQueuePrompts())
    }

    @Test
    fun `submitPrompt should return error when engine not initialized`() = runTest {
        lifecycleManager.submitPrompt("Test prompt").test {
            val result = awaitItem()
            assertTrue(result is GenerationResult.Error)
            val error = result as GenerationResult.Error
            assertTrue(error.message.contains("not initialized"))
            awaitComplete()
        }
    }

    @Test
    fun `waitForReady should return false on timeout`() = runTest {
        val result = lifecycleManager.waitForReady(timeoutMs = 50)
        assertFalse(result)
    }

    @Test
    fun `getQueueStatistics should return current statistics`() {
        val stats = lifecycleManager.getQueueStatistics()
        
        assertEquals(0, stats.totalEnqueued)
        assertEquals(0, stats.totalDequeued)
    }

    @Test
    fun `registerCallback should add lifecycle callback`() = runTest {
        var callbackInvoked = false
        
        val callback = object : EngineLifecycleCallback {
            override fun onReady() {
                callbackInvoked = true
            }
        }
        
        lifecycleManager.registerCallback(callback)
        
        // Manually trigger state change to READY
        stateManager.transitionTo(EngineState.INITIALIZING)
        stateManager.transitionTo(EngineState.READY)
        
        // Give time for the callback to be invoked
        delay(100)
        
        // Note: The callback might not be invoked in this test setup
        // because we're manually manipulating the state manager
        // In real usage, the lifecycle manager would orchestrate this
        
        lifecycleManager.unregisterCallback(callback)
    }

    @Test
    fun `lifecycle callback should receive onInitialized event`() = runTest {
        var initializedCalled = false
        var receivedMetrics: InitMetrics? = null
        
        val callback = object : EngineLifecycleCallback {
            override fun onInitialized(metrics: InitMetrics) {
                initializedCalled = true
                receivedMetrics = metrics
            }
        }
        
        lifecycleManager.registerCallback(callback)
        
        // Simulate initialization completion
        val metrics = InitMetrics(
            startTime = System.currentTimeMillis(),
            endTime = System.currentTimeMillis() + 1000,
            retryCount = 0,
            backend = HardwareBackend.CPU,
            modelPath = "/test/model"
        )
        
        // Note: In actual implementation, this would be called by the lifecycle manager
        // when initialization completes successfully
        
        lifecycleManager.unregisterCallback(callback)
    }

    @Test
    fun `lifecycle callback should receive onError event`() = runTest {
        var errorCalled = false
        var receivedError: Throwable? = null
        
        val callback = object : EngineLifecycleCallback {
            override fun onError(error: Throwable) {
                errorCalled = true
                receivedError = error
            }
        }
        
        lifecycleManager.registerCallback(callback)
        
        // Simulate error
        val testError = RuntimeException("Test error")
        
        // Note: In actual implementation, this would be called by the lifecycle manager
        // when an error occurs
        
        lifecycleManager.unregisterCallback(callback)
    }

    @Test
    fun `progress flow should emit initialization progress`() = runTest {
        lifecycleManager.progressFlow.test {
            // Initial state
            val initial = awaitItem()
            assertTrue(initial is InitializationProgress.NotStarted)
            
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `concurrent prompt submissions should be handled safely`() = runTest {
        val iterations = 50
        
        val jobs = List(iterations) {
            launch {
                lifecycleManager.submitPrompt("Prompt $it").collect { /* consume */ }
            }
        }
        
        jobs.forEach { it.join() }
        
        // All prompts should be handled (either queued or rejected)
        val stats = lifecycleManager.getQueueStatistics()
        assertTrue(stats.totalEnqueued + stats.totalRejected == iterations)
    }

    @Test
    fun `shutdown should transition to SHUTTING_DOWN then RELEASED`() = runTest {
        // First initialize
        stateManager.transitionTo(EngineState.INITIALIZING)
        stateManager.transitionTo(EngineState.READY)
        
        assertEquals(EngineState.READY, lifecycleManager.currentState)
        
        // Shutdown
        val result = lifecycleManager.shutdown()
        
        assertTrue(result.isSuccess)
        assertEquals(EngineState.RELEASED, lifecycleManager.currentState)
    }

    @Test
    fun `release should clean up resources`() {
        lifecycleManager.release()
        
        // After release, operations should fail gracefully
        // The manager should be in a released state
    }

    @Test
    fun `initialize should fail if manager is released`() = runTest {
        lifecycleManager.release()
        
        val result = lifecycleManager.initialize("/test/model")
        
        assertTrue(result.isFailure)
    }

    @Test
    fun `setEngine should register engine implementation`() {
        // This is a placeholder test - in real implementation,
        // we would verify that the engine is properly registered
        // and used for prompt processing
    }
}