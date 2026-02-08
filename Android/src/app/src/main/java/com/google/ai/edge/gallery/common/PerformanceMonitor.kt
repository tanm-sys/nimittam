/*
 * Copyright 2025 Tanmay Patil
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.google.ai.edge.gallery.common

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Performance metrics data class.
 */
data class PerformanceMetrics(
    val operationName: String,
    val durationMs: Long,
    val startTime: Long,
    val endTime: Long,
    val attributes: Map<String, String> = emptyMap()
)

/**
 * Interface for performance monitoring.
 */
interface PerformanceMonitor {
    /**
     * Start tracking a performance trace.
     */
    fun startTrace(name: String, attributes: Map<String, String> = emptyMap()): TraceHandle
    
    /**
     * Record a metric value.
     */
    fun recordMetric(name: String, value: Long, attributes: Map<String, String> = emptyMap())
    
    /**
     * Get all recorded metrics.
     */
    fun getMetrics(): List<PerformanceMetrics>
    
    /**
     * Clear all metrics.
     */
    fun clearMetrics()
}

/**
 * Handle for an active performance trace.
 */
interface TraceHandle {
    /**
     * Stop the trace and record the metrics.
     */
    fun stop(attributes: Map<String, String> = emptyMap())
    
    /**
     * Cancel the trace without recording.
     */
    fun cancel()
}

/**
 * Local implementation of PerformanceMonitor.
 * Stores metrics in memory and logs to console.
 */
@Singleton
class LocalPerformanceMonitor @Inject constructor() : PerformanceMonitor {
    
    private val activeTraces = ConcurrentHashMap<String, Long>()
    private val recordedMetrics = mutableListOf<PerformanceMetrics>()
    private val metricsMutex = Object()
    
    companion object {
        private const val TAG = "PerformanceMonitor"
    }
    
    override fun startTrace(name: String, attributes: Map<String, String>): TraceHandle {
        val startTime = System.currentTimeMillis()
        val traceId = "$name-$startTime"
        activeTraces[traceId] = startTime
        
        Log.d(TAG, "Trace started: $name")
        
        return object : TraceHandle {
            override fun stop(additionalAttributes: Map<String, String>) {
                val start = activeTraces.remove(traceId)
                if (start != null) {
                    val endTime = System.currentTimeMillis()
                    val duration = endTime - start
                    
                    val metric = PerformanceMetrics(
                        operationName = name,
                        durationMs = duration,
                        startTime = start,
                        endTime = endTime,
                        attributes = attributes + additionalAttributes
                    )
                    
                    synchronized(metricsMutex) {
                        recordedMetrics.add(metric)
                    }
                    
                    Log.d(TAG, "Trace stopped: $name, Duration: ${duration}ms")
                }
            }
            
            override fun cancel() {
                activeTraces.remove(traceId)
                Log.d(TAG, "Trace cancelled: $name")
            }
        }
    }
    
    override fun recordMetric(name: String, value: Long, attributes: Map<String, String>) {
        val now = System.currentTimeMillis()
        val metric = PerformanceMetrics(
            operationName = name,
            durationMs = value,
            startTime = now,
            endTime = now,
            attributes = attributes
        )
        
        synchronized(metricsMutex) {
            recordedMetrics.add(metric)
        }
        
        Log.d(TAG, "Metric recorded: $name = $value")
    }
    
    override fun getMetrics(): List<PerformanceMetrics> {
        return synchronized(metricsMutex) {
            recordedMetrics.toList()
        }
    }
    
    override fun clearMetrics() {
        synchronized(metricsMutex) {
            recordedMetrics.clear()
        }
    }
    
    /**
     * Get metrics summary as a formatted string.
     */
    fun getMetricsSummary(): String {
        val metrics = getMetrics()
        val grouped = metrics.groupBy { it.operationName }
        
        return buildString {
            appendLine("=== Performance Metrics Summary ===")
            appendLine("Total operations: ${metrics.size}")
            appendLine()
            
            grouped.forEach { (name, ops) ->
                val durations = ops.map { it.durationMs }
                val avg = durations.average()
                val min = durations.minOrNull() ?: 0
                val max = durations.maxOrNull() ?: 0
                
                appendLine("$name:")
                appendLine("  Count: ${ops.size}")
                appendLine("  Avg: ${avg.toInt()}ms")
                appendLine("  Min: ${min}ms")
                appendLine("  Max: ${max}ms")
                appendLine()
            }
        }
    }
}

/**
 * Helper class for measuring code block performance.
 */
class PerformanceMeasurer(
    private val monitor: PerformanceMonitor,
    private val operationName: String,
    private val attributes: Map<String, String> = emptyMap()
) {
    private var traceHandle: TraceHandle? = null
    
    fun start() {
        traceHandle = monitor.startTrace(operationName, attributes)
    }
    
    fun stop(additionalAttributes: Map<String, String> = emptyMap()) {
        traceHandle?.stop(additionalAttributes)
        traceHandle = null
    }
    
    fun <T> measure(block: () -> T): T {
        start()
        return try {
            block()
        } finally {
            stop()
        }
    }
    
    suspend fun <T> measureSuspend(block: suspend () -> T): T {
        start()
        return try {
            block()
        } finally {
            stop()
        }
    }
}

/**
 * Extension functions for easier performance monitoring.
 */
inline fun <T> PerformanceMonitor.measure(
    operationName: String,
    attributes: Map<String, String> = emptyMap(),
    block: () -> T
): T {
    val handle = startTrace(operationName, attributes)
    return try {
        block()
    } finally {
        handle.stop()
    }
}

suspend inline fun <T> PerformanceMonitor.measureSuspend(
    operationName: String,
    attributes: Map<String, String> = emptyMap(),
    crossinline block: suspend () -> T
): T {
    val handle = startTrace(operationName, attributes)
    return try {
        block()
    } finally {
        handle.stop()
    }
}

/**
 * Performance tracking for Compose screens.
 */
@Singleton
class ComposePerformanceTracker @Inject constructor(
    private val monitor: PerformanceMonitor
) {
    private val screenTraces = ConcurrentHashMap<String, TraceHandle>()
    
    /**
     * Start tracking screen load time.
     */
    fun onScreenEnter(screenName: String) {
        val handle = monitor.startTrace("screen_$screenName", mapOf("action" to "load"))
        screenTraces[screenName] = handle
    }
    
    /**
     * Stop tracking screen load time.
     */
    fun onScreenExit(screenName: String) {
        screenTraces.remove(screenName)?.stop()
    }
    
    /**
     * Track a user interaction.
     */
    fun trackInteraction(screenName: String, interactionType: String) {
        monitor.recordMetric(
            name = "interaction",
            value = 1,
            attributes = mapOf(
                "screen" to screenName,
                "type" to interactionType
            )
        )
    }
}
