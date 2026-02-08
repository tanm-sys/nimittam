/*
 * Copyright 2025 Tanmay Patil
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.google.ai.edge.gallery.common

import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ANR (Application Not Responding) Detector.
 * 
 * Monitors the main thread by periodically posting tasks and checking if they execute.
 * If the main thread is blocked for too long, an ANR is detected and reported.
 * 
 * Usage:
 * ```kotlin
 * // In Application.onCreate()
 * anrDetector.start()
 * 
 * // In Application.onTerminate()
 * anrDetector.stop()
 * ```
 */
@Singleton
class AnrDetector @Inject constructor(
    private val crashReporter: CrashReporter
) : Runnable {
    
    companion object {
        private const val TAG = "AnrDetector"
        
        // ANR threshold - Android kills apps after 5 seconds of unresponsiveness
        // We detect at 3 seconds to get early warning
        private const val ANR_THRESHOLD_MS = 3000L
        
        // Check interval - how often to test the main thread
        private const val CHECK_INTERVAL_MS = 1000L
        
        // Minimum number of consecutive slow responses before reporting
        private const val MIN_CONSECUTIVE_SLOW_RESPONSES = 2
    }
    
    private val mainHandler = Handler(Looper.getMainLooper())
    private var monitoringThread: Thread? = null
    private var isRunning = false
    private var consecutiveSlowResponses = 0
    private var lastAnrTime = 0L
    private var totalAnrCount = 0
    
    /**
     * Start monitoring for ANRs.
     */
    fun start() {
        if (isRunning) {
            Log.w(TAG, "ANR detector already running")
            return
        }
        
        isRunning = true
        monitoringThread = Thread(this, "ANR-Monitor").apply {
            isDaemon = true
            start()
        }
        
        Log.i(TAG, "ANR detector started (threshold: ${ANR_THRESHOLD_MS}ms)")
    }
    
    /**
     * Stop monitoring.
     */
    fun stop() {
        isRunning = false
        monitoringThread?.interrupt()
        monitoringThread = null
        Log.i(TAG, "ANR detector stopped")
    }
    
    override fun run() {
        Log.d(TAG, "ANR monitoring thread started")
        
        while (isRunning && !Thread.interrupted()) {
            try {
                // Create a latch that will be counted down by the main thread
                val latch = CountDownLatch(1)
                val postTime = System.currentTimeMillis()
                
                // Post a task to the main thread
                mainHandler.post {
                    latch.countDown()
                }
                
                // Wait for the main thread to execute the task
                val responded = latch.await(ANR_THRESHOLD_MS, TimeUnit.MILLISECONDS)
                val responseTime = System.currentTimeMillis() - postTime
                
                if (!responded) {
                    // Main thread didn't respond within threshold
                    consecutiveSlowResponses++
                    
                    if (consecutiveSlowResponses >= MIN_CONSECUTIVE_SLOW_RESPONSES) {
                        reportAnr(responseTime)
                    } else {
                        Log.w(TAG, "Slow main thread response #${consecutiveSlowResponses}: ${responseTime}ms")
                    }
                } else {
                    // Main thread responded normally
                    if (consecutiveSlowResponses > 0) {
                        Log.d(TAG, "Main thread recovered, response time: ${responseTime}ms")
                        consecutiveSlowResponses = 0
                    }
                }
                
                // Wait before next check
                Thread.sleep(CHECK_INTERVAL_MS)
                
            } catch (e: InterruptedException) {
                Log.d(TAG, "ANR monitoring thread interrupted")
                Thread.currentThread().interrupt()
                break
            } catch (e: Exception) {
                Log.e(TAG, "Error in ANR detector", e)
                crashReporter.reportException(e, mapOf("context" to "AnrDetector"))
            }
        }
        
        Log.d(TAG, "ANR monitoring thread stopped")
    }
    
    private fun reportAnr(blockedDuration: Long) {
        totalAnrCount++
        lastAnrTime = System.currentTimeMillis()
        
        Log.e(TAG, "ANR DETECTED! Main thread blocked for ${blockedDuration}ms (total: $totalAnrCount)")
        
        // Report to crash reporter
        val anrException = AnrException("Main thread blocked for ${blockedDuration}ms")
        crashReporter.reportException(
            anrException,
            mapOf(
                "blocked_duration_ms" to blockedDuration.toString(),
                "total_anr_count" to totalAnrCount.toString(),
                "anr_threshold_ms" to ANR_THRESHOLD_MS.toString()
            )
        )
        
        // Record breadcrumb
        crashReporter.recordBreadcrumb(
            "ANR detected: main thread blocked for ${blockedDuration}ms",
            "performance"
        )
        
        // Log performance metrics if available
        Log.w(TAG, "Main thread ANR - possible causes:")
        Log.w(TAG, "  - Long-running operation on main thread")
        Log.w(TAG, "  - Deadlock or thread contention")
        Log.w(TAG, "  - Excessive work during UI update")
        Log.w(TAG, "  - Native code blocking")
    }
    
    /**
     * Check if an ANR was recently detected (within the last minute).
     */
    fun hadRecentAnr(): Boolean {
        val oneMinuteAgo = System.currentTimeMillis() - 60_000
        return lastAnrTime > oneMinuteAgo
    }
    
    /**
     * Get total ANR count since app start.
     */
    fun getTotalAnrCount(): Int = totalAnrCount
    
    /**
     * Get time since last ANR in milliseconds.
     * Returns Long.MAX_VALUE if no ANR has occurred.
     */
    fun getTimeSinceLastAnr(): Long {
        return if (lastAnrTime == 0L) {
            Long.MAX_VALUE
        } else {
            System.currentTimeMillis() - lastAnrTime
        }
    }
}

/**
 * Exception representing an ANR condition.
 */
class AnrException(message: String) : Exception(message)

/**
 * Helper to detect ANR-prone code blocks.
 * Use this to track potentially slow operations.
 */
class AnrWatchdog(
    private val name: String,
    private val thresholdMs: Long = 100L, // Warn if main thread operation takes >100ms
    private val crashReporter: CrashReporter
) {
    private var startTime: Long = 0
    
    fun start() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            startTime = System.currentTimeMillis()
        }
    }
    
    fun stop() {
        if (Looper.myLooper() == Looper.getMainLooper() && startTime > 0) {
            val duration = System.currentTimeMillis() - startTime
            if (duration > thresholdMs) {
                Log.w("AnrWatchdog", "Slow main thread operation '$name': ${duration}ms")
                crashReporter.recordBreadcrumb(
                    "Slow main thread operation '$name': ${duration}ms",
                    "performance"
                )
            }
        }
        startTime = 0
    }
    
    inline fun <T> watch(block: () -> T): T {
        start()
        return try {
            block()
        } finally {
            stop()
        }
    }
}
