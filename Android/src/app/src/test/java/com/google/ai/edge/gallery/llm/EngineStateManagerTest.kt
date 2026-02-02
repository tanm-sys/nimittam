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
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EngineStateManagerTest {

    private lateinit var stateManager: EngineStateManager
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Before
    fun setup() {
        stateManager = EngineStateManager.create()
    }

    @After
    fun tearDown() {
        // Cleanup if needed
    }

    @Test
    fun `initial state should be UNINITIALIZED`() {
        assertEquals(EngineState.UNINITIALIZED, stateManager.getCurrentState())
    }

    @Test
    fun `valid transition from UNINITIALIZED to INITIALIZING should succeed`() = runTest {
        val result = stateManager.transitionTo(EngineState.INITIALIZING)
        
        assertTrue(result.isSuccess)
        assertEquals(EngineState.INITIALIZING, stateManager.getCurrentState())
    }

    @Test
    fun `invalid transition from UNINITIALIZED to READY should fail`() = runTest {
        val result = stateManager.transitionTo(EngineState.READY)
        
        assertTrue(result.isFailure)
        assertEquals(EngineState.UNINITIALIZED, stateManager.getCurrentState())
    }

    @Test
    fun `transition to ERROR should be possible from any non-terminal state`() = runTest {
        // From UNINITIALIZED - not valid
        var result = stateManager.transitionTo(EngineState.ERROR)
        assertTrue(result.isFailure)
        
        // From INITIALIZING
        stateManager.transitionTo(EngineState.INITIALIZING)
        result = stateManager.transitionTo(EngineState.ERROR, error = RuntimeException("Test error"))
        assertTrue(result.isSuccess)
        assertEquals(EngineState.ERROR, stateManager.getCurrentState())
    }

    @Test
    fun `state flow should emit state changes`() = runTest {
        stateManager.stateFlow.test {
            // Initial state
            assertEquals(EngineState.UNINITIALIZED, awaitItem())
            
            // Transition to INITIALIZING
            stateManager.transitionTo(EngineState.INITIALIZING)
            assertEquals(EngineState.INITIALIZING, awaitItem())
            
            // Transition to READY
            stateManager.transitionTo(EngineState.READY)
            assertEquals(EngineState.READY, awaitItem())
            
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `canAcceptPrompts should return true only in READY state`() = runTest {
        assertFalse(stateManager.canAcceptPrompts())
        
        stateManager.transitionTo(EngineState.INITIALIZING)
        assertFalse(stateManager.canAcceptPrompts())
        
        stateManager.transitionTo(EngineState.READY)
        assertTrue(stateManager.canAcceptPrompts())
        
        stateManager.transitionTo(EngineState.ERROR)
        assertFalse(stateManager.canAcceptPrompts())
    }

    @Test
    fun `shouldQueuePrompts should return true only in INITIALIZING state`() = runTest {
        assertFalse(stateManager.shouldQueuePrompts())
        
        stateManager.transitionTo(EngineState.INITIALIZING)
        assertTrue(stateManager.shouldQueuePrompts())
        
        stateManager.transitionTo(EngineState.READY)
        assertFalse(stateManager.shouldQueuePrompts())
    }

    @Test
    fun `waitForState should return true when state is reached`() = runTest {
        // Start a coroutine that will transition the state after a delay
        launch {
            kotlinx.coroutines.delay(100)
            stateManager.transitionTo(EngineState.INITIALIZING)
            kotlinx.coroutines.delay(100)
            stateManager.transitionTo(EngineState.READY)
        }
        
        val result = stateManager.waitForState(EngineState.READY, timeoutMs = 1000)
        assertTrue(result)
        assertEquals(EngineState.READY, stateManager.getCurrentState())
    }

    @Test
    fun `waitForState should return false on timeout`() = runTest {
        val result = stateManager.waitForState(EngineState.READY, timeoutMs = 50)
        assertFalse(result)
    }

    @Test
    fun `waitForReady should return true when engine becomes ready`() = runTest {
        launch {
            kotlinx.coroutines.delay(50)
            stateManager.transitionTo(EngineState.INITIALIZING)
            kotlinx.coroutines.delay(50)
            stateManager.transitionTo(EngineState.READY)
        }
        
        val result = stateManager.waitForReady(timeoutMs = 1000)
        assertTrue(result)
    }

    @Test
    fun `concurrent state transitions should be thread-safe`() = runTest {
        val iterations = 100
        val jobs = List(iterations) {
            launch {
                stateManager.transitionTo(EngineState.INITIALIZING)
                stateManager.transitionTo(EngineState.READY)
                stateManager.transitionTo(EngineState.SHUTTING_DOWN)
                stateManager.transitionTo(EngineState.RELEASED)
            }
        }
        
        jobs.forEach { it.join() }
        
        // After all transitions, state should be terminal
        assertTrue(stateManager.isTerminal())
    }

    @Test
    fun `progress updates should be reflected in progress flow`() = runTest {
        stateManager.progressFlow.test {
            // Initial progress
            assertTrue(awaitItem() is InitializationProgress.NotStarted)
            
            // Update progress
            stateManager.updateProgress(InitializationProgress.InProgress(50, "Loading model..."))
            val progress = awaitItem()
            assertTrue(progress is InitializationProgress.InProgress)
            assertEquals(50, (progress as InitializationProgress.InProgress).percentage)
            
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `transition history should record all transitions`() = runTest {
        stateManager.transitionTo(EngineState.INITIALIZING, reason = "Starting up")
        stateManager.transitionTo(EngineState.READY, reason = "Complete")
        stateManager.transitionTo(EngineState.SHUTTING_DOWN, reason = "Shutting down")
        
        val history = stateManager.getTransitionHistory()
        assertEquals(3, history.size)
        
        assertEquals(EngineState.UNINITIALIZED, history[0].fromState)
        assertEquals(EngineState.INITIALIZING, history[0].toState)
        assertEquals("Starting up", history[0].reason)
        
        assertEquals(EngineState.INITIALIZING, history[1].fromState)
        assertEquals(EngineState.READY, history[1].toState)
    }

    @Test
    fun `withState should execute action only in expected state`() = runTest {
        stateManager.transitionTo(EngineState.INITIALIZING)
        
        // Should succeed in INITIALIZING state
        var result = stateManager.withState(EngineState.INITIALIZING) { "success" }
        assertTrue(result.isSuccess)
        assertEquals("success", result.getOrNull())
        
        // Should fail in wrong state
        result = stateManager.withState(EngineState.READY) { "should not execute" }
        assertTrue(result.isFailure)
    }

    @Test
    fun `withAnyState should execute action in any allowed state`() = runTest {
        stateManager.transitionTo(EngineState.READY)
        
        val allowedStates = setOf(EngineState.READY, EngineState.ERROR)
        val result = stateManager.withAnyState(allowedStates) { "executed" }
        
        assertTrue(result.isSuccess)
        assertEquals("executed", result.getOrNull())
    }

    @Test
    fun `isTerminal should return true only for RELEASED state`() = runTest {
        assertFalse(stateManager.isTerminal())
        
        stateManager.transitionTo(EngineState.INITIALIZING)
        assertFalse(stateManager.isTerminal())
        
        stateManager.transitionTo(EngineState.READY)
        assertFalse(stateManager.isTerminal())
        
        stateManager.transitionTo(EngineState.SHUTTING_DOWN)
        assertFalse(stateManager.isTerminal())
        
        stateManager.transitionTo(EngineState.RELEASED)
        assertTrue(stateManager.isTerminal())
    }

    @Test
    fun `state callback should be invoked on state changes`() = runTest {
        var callbackInvoked = false
        var capturedFromState: EngineState? = null
        var capturedToState: EngineState? = null
        
        val callback = object : EngineStateCallback {
            override fun onStateChanged(from: EngineState, to: EngineState, event: StateTransitionEvent) {
                callbackInvoked = true
                capturedFromState = from
                capturedToState = to
            }
        }
        
        stateManager.registerCallback(callback)
        stateManager.transitionTo(EngineState.INITIALIZING)
        
        assertTrue(callbackInvoked)
        assertEquals(EngineState.UNINITIALIZED, capturedFromState)
        assertEquals(EngineState.INITIALIZING, capturedToState)
        
        stateManager.unregisterCallback(callback)
    }

    @Test
    fun `clearHistory should remove all transition records`() = runTest {
        stateManager.transitionTo(EngineState.INITIALIZING)
        stateManager.transitionTo(EngineState.READY)
        
        assertEquals(2, stateManager.getTransitionHistory().size)
        
        stateManager.clearHistory()
        
        assertEquals(0, stateManager.getTransitionHistory().size)
    }
}