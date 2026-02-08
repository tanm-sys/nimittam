/*
 * Copyright 2025 Tanmay Patil
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.google.ai.edge.gallery.common

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Interface for crash reporting services.
 * Allows for different implementations (Firebase, local logging, etc.)
 */
interface CrashReporter {
    /**
     * Report a non-fatal exception to the crash reporting service.
     */
    fun reportException(throwable: Throwable, metadata: Map<String, String> = emptyMap())
    
    /**
     * Log an event for analytics.
     */
    fun logEvent(eventName: String, params: Map<String, String> = emptyMap())
    
    /**
     * Set a user property.
     */
    fun setUserProperty(key: String, value: String)
    
    /**
     * Record a breadcrumb for debugging.
     */
    fun recordBreadcrumb(message: String, category: String = "general")
}

/**
 * Local implementation of CrashReporter that logs to console and local files.
 * Works offline and serves as a fallback when remote crash reporting is unavailable.
 */
@Singleton
class LocalCrashReporter @Inject constructor() : CrashReporter {
    companion object {
        private const val TAG = "CrashReporter"
    }
    
    override fun reportException(throwable: Throwable, metadata: Map<String, String>) {
        Log.e(TAG, "Exception reported: ${throwable.message}")
        Log.e(TAG, "Metadata: $metadata")
        
        // Log stack trace
        Log.e(TAG, "Stack trace:", throwable)
    }
    
    override fun logEvent(eventName: String, params: Map<String, String>) {
        Log.d(TAG, "Event: $eventName, Params: $params")
    }
    
    override fun setUserProperty(key: String, value: String) {
        Log.d(TAG, "User Property - $key: $value")
    }
    
    override fun recordBreadcrumb(message: String, category: String) {
        Log.v(TAG, "Breadcrumb [$category]: $message")
    }
}

/**
 * Composite crash reporter that sends to multiple backends.
 * Can combine local logging with remote services.
 */
@Singleton
class CompositeCrashReporter @Inject constructor(
    private val localReporter: LocalCrashReporter
) : CrashReporter {
    
    private val reporters = mutableListOf<CrashReporter>(localReporter)
    
    /**
     * Add an additional crash reporter (e.g., Firebase).
     */
    fun addReporter(reporter: CrashReporter) {
        reporters.add(reporter)
    }
    
    override fun reportException(throwable: Throwable, metadata: Map<String, String>) {
        reporters.forEach { reporter ->
            try {
                reporter.reportException(throwable, metadata)
            } catch (e: Exception) {
                Log.e("CompositeCrashReporter", "Reporter failed", e)
            }
        }
    }
    
    override fun logEvent(eventName: String, params: Map<String, String>) {
        reporters.forEach { reporter ->
            try {
                reporter.logEvent(eventName, params)
            } catch (e: Exception) {
                Log.e("CompositeCrashReporter", "Reporter failed", e)
            }
        }
    }
    
    override fun setUserProperty(key: String, value: String) {
        reporters.forEach { reporter ->
            try {
                reporter.setUserProperty(key, value)
            } catch (e: Exception) {
                Log.e("CompositeCrashReporter", "Reporter failed", e)
            }
        }
    }
    
    override fun recordBreadcrumb(message: String, category: String) {
        reporters.forEach { reporter ->
            try {
                reporter.recordBreadcrumb(message, category)
            } catch (e: Exception) {
                Log.e("CompositeCrashReporter", "Reporter failed", e)
            }
        }
    }
}

/**
 * Extension functions for easier crash reporting usage.
 */
fun Throwable.report(reporter: CrashReporter, metadata: Map<String, String> = emptyMap()) {
    reporter.reportException(this, metadata)
}

fun Throwable.reportWithContext(reporter: CrashReporter, context: String) {
    reporter.reportException(this, mapOf("context" to context))
}
