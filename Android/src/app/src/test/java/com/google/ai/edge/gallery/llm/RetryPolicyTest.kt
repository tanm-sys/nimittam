/*
 * Copyright 2025 Tanmay Patil
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.google.ai.edge.gallery.llm

import org.junit.Assert.*
import org.junit.Test

class RetryPolicyTest {

    @Test
    fun `default retry policy should have expected values`() {
        val policy = RetryPolicy.DEFAULT
        
        assertEquals(3, policy.maxRetries)
        assertEquals(1000L, policy.initialDelayMs)
        assertEquals(30000L, policy.maxDelayMs)
        assertEquals(2.0, policy.backoffMultiplier, 0.001)
        assertEquals(30000L, policy.timeoutMs)
    }

    @Test
    fun `aggressive retry policy should have shorter delays`() {
        val policy = RetryPolicy.AGGRESSIVE
        
        assertEquals(5, policy.maxRetries)
        assertEquals(500L, policy.initialDelayMs)
        assertEquals(10000L, policy.maxDelayMs)
        assertEquals(1.5, policy.backoffMultiplier, 0.001)
    }

    @Test
    fun `conservative retry policy should have longer delays`() {
        val policy = RetryPolicy.CONSERVATIVE
        
        assertEquals(2, policy.maxRetries)
        assertEquals(2000L, policy.initialDelayMs)
        assertEquals(60000L, policy.maxDelayMs)
        assertEquals(2.5, policy.backoffMultiplier, 0.001)
    }

    @Test
    fun `calculateDelay should return initial delay for first attempt`() {
        val policy = RetryPolicy.DEFAULT
        
        val delay = policy.calculateDelay(0)
        
        assertEquals(1000L, delay)
    }

    @Test
    fun `calculateDelay should use exponential backoff`() {
        val policy = RetryPolicy(
            initialDelayMs = 1000L,
            backoffMultiplier = 2.0
        )
        
        assertEquals(1000L, policy.calculateDelay(0))   // 1000 * 2^0
        assertEquals(2000L, policy.calculateDelay(1))   // 1000 * 2^1
        assertEquals(4000L, policy.calculateDelay(2))   // 1000 * 2^2
        assertEquals(8000L, policy.calculateDelay(3))   // 1000 * 2^3
    }

    @Test
    fun `calculateDelay should respect maxDelayMs`() {
        val policy = RetryPolicy(
            initialDelayMs = 1000L,
            maxDelayMs = 5000L,
            backoffMultiplier = 2.0
        )
        
        assertEquals(1000L, policy.calculateDelay(0))
        assertEquals(2000L, policy.calculateDelay(1))
        assertEquals(4000L, policy.calculateDelay(2))
        assertEquals(5000L, policy.calculateDelay(3))  // Capped at maxDelayMs
        assertEquals(5000L, policy.calculateDelay(4))  // Still capped
    }

    @Test(expected = IllegalArgumentException::class)
    fun `calculateDelay should throw for negative attempt`() {
        val policy = RetryPolicy.DEFAULT
        
        policy.calculateDelay(-1)
    }

    @Test
    fun `custom retry policy should accept all parameters`() {
        val policy = RetryPolicy(
            maxRetries = 5,
            initialDelayMs = 500L,
            maxDelayMs = 10000L,
            backoffMultiplier = 1.5,
            timeoutMs = 60000L
        )
        
        assertEquals(5, policy.maxRetries)
        assertEquals(500L, policy.initialDelayMs)
        assertEquals(10000L, policy.maxDelayMs)
        assertEquals(1.5, policy.backoffMultiplier, 0.001)
        assertEquals(60000L, policy.timeoutMs)
    }

    @Test
    fun `exponential backoff with fractional multiplier`() {
        val policy = RetryPolicy(
            initialDelayMs = 1000L,
            backoffMultiplier = 1.5
        )
        
        assertEquals(1000L, policy.calculateDelay(0))   // 1000 * 1.5^0
        assertEquals(1500L, policy.calculateDelay(1))   // 1000 * 1.5^1
        assertEquals(2250L, policy.calculateDelay(2))   // 1000 * 1.5^2
        assertEquals(3375L, policy.calculateDelay(3))   // 1000 * 1.5^3
    }
}