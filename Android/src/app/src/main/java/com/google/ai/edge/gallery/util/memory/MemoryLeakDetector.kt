/*
 * Copyright 2025 Tanmay Patil
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.google.ai.edge.gallery.util.memory

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.reflect.KClass

private const val WATCH_DELAY_MS = 5000L // 5 seconds
private const val GC_WAIT_MS = 100L

/**
 * Leak detection severity levels.
 */
enum class LeakSeverity {
    LOW,      // Small leak, may be acceptable
    MEDIUM,   // Moderate leak, should be investigated
    HIGH      // Serious leak, must be fixed
}

/**
 * Memory leak report data.
 */
data class LeakReport(
    val leakedObjectClass: String,
    val leakedObjectHash: Int,
    val retentionTimeMs: Long,
    val severity: LeakSeverity,
    val stackTrace: String? = null
)

/**
 * Listener interface for leak detection events.
 */
interface LeakDetectionListener {
    fun onLeakDetected(report: LeakReport)
    fun onLeakAnalysisStarted(watchedObject: Any)
}

/**
 * Watcher for objects that should be garbage collected.
 */
private class ObjectWatcher(
    val watchedObject: WeakReference<Any>,
    val objectClass: KClass<*>,
    val watchStartTime: Long,
    val description: String,
    val stackTrace: String? = null
)

/**
 * Memory leak detector that watches for objects that should be GC'd.
 * Features:
 * - Automatic activity lifecycle monitoring
 * - Manual object watching
 * - Configurable retention thresholds
 * - Leak reporting with stack traces
 */
@Singleton
class MemoryLeakDetector @Inject constructor(
    private val application: Application
) : Application.ActivityLifecycleCallbacks {

    companion object {
        private const val TAG = "MemoryLeakDetector"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mainHandler = Handler(Looper.getMainLooper())

    private val watchedObjects = ConcurrentHashMap<String, ObjectWatcher>()
    private val listeners = CopyOnWriteArrayList<LeakDetectionListener>()

    private var isEnabled = false
    private var retentionThresholdMs = 5000L

    init {
        application.registerActivityLifecycleCallbacks(this)
    }

    /**
     * Enable or disable leak detection.
     */
    fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
        if (enabled) {
            startWatching()
        }
    }

    /**
     * Set retention threshold for leak detection.
     *
     * @param thresholdMs Time in milliseconds before considering an object leaked
     */
    fun setRetentionThreshold(thresholdMs: Long) {
        retentionThresholdMs = thresholdMs
    }

    /**
     * Add a leak detection listener.
     */
    fun addListener(listener: LeakDetectionListener) {
        listeners.addIfAbsent(listener)
    }

    /**
     * Remove a leak detection listener.
     */
    fun removeListener(listener: LeakDetectionListener) {
        listeners.remove(listener)
    }

    /**
     * Watch an object for potential leaks.
     *
     * @param watchedObject Object to watch
     * @param description Description of the object
     */
    fun watch(watchedObject: Any, description: String = watchedObject.javaClass.simpleName) {
        if (!isEnabled) return

        val key = "${description}@${watchedObject.hashCode()}"
        val stackTrace = if (Log.isLoggable(TAG, Log.DEBUG)) {
            Throwable("Object watched here").stackTraceToString()
        } else null

        watchedObjects[key] = ObjectWatcher(
            watchedObject = WeakReference(watchedObject),
            objectClass = watchedObject::class,
            watchStartTime = System.currentTimeMillis(),
            description = description,
            stackTrace = stackTrace
        )

        listeners.forEach { it.onLeakAnalysisStarted(watchedObject) }

        Log.d(TAG, "Watching $description")
    }

    /**
     * Stop watching an object.
     */
    fun unwatch(watchedObject: Any, description: String = watchedObject.javaClass.simpleName) {
        val key = "${description}@${watchedObject.hashCode()}"
        watchedObjects.remove(key)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        // No-op
    }

    override fun onActivityStarted(activity: Activity) {
        // No-op
    }

    override fun onActivityResumed(activity: Activity) {
        // No-op
    }

    override fun onActivityPaused(activity: Activity) {
        // No-op
    }

    override fun onActivityStopped(activity: Activity) {
        // No-op
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
        // No-op
    }

    override fun onActivityDestroyed(activity: Activity) {
        // Watch activity for leaks after destruction
        watch(activity, "Activity:${activity.javaClass.simpleName}")
    }

    private fun startWatching() {
        scope.launch {
            while (isActive && isEnabled) {
                checkForLeaks()
                delay(WATCH_DELAY_MS)
            }
        }
    }

    private suspend fun checkForLeaks() {
        // Trigger GC
        System.gc()
        delay(GC_WAIT_MS)

        val currentTime = System.currentTimeMillis()
        val iterator = watchedObjects.entries.iterator()

        while (iterator.hasNext()) {
            val (key, watcher) = iterator.next()
            val referent = watcher.watchedObject.get()

            if (referent == null) {
                // Object was GC'd, remove from watch
                iterator.remove()
                Log.d(TAG, "Object $key was properly GC'd")
            } else {
                // Object still exists, check if leaked
                val retentionTime = currentTime - watcher.watchStartTime

                if (retentionTime > retentionThresholdMs) {
                    // Leak detected
                    val severity = when {
                        retentionTime > retentionThresholdMs * 3 -> LeakSeverity.HIGH
                        retentionTime > retentionThresholdMs * 2 -> LeakSeverity.MEDIUM
                        else -> LeakSeverity.LOW
                    }

                    val report = LeakReport(
                        leakedObjectClass = watcher.objectClass.qualifiedName ?: "Unknown",
                        leakedObjectHash = referent.hashCode(),
                        retentionTimeMs = retentionTime,
                        severity = severity,
                        stackTrace = watcher.stackTrace
                    )

                    // Report on main thread
                    mainHandler.post {
                        listeners.forEach { it.onLeakDetected(report) }
                    }

                    Log.w(TAG, "Leak detected: $key retained for ${retentionTime}ms (severity: $severity)")

                    // Remove from watch to avoid duplicate reports
                    iterator.remove()
                }
            }
        }
    }

    /**
     * Get current number of watched objects.
     */
    fun getWatchedObjectCount(): Int = watchedObjects.size

    /**
     * Clear all watched objects.
     */
    fun clearWatchedObjects() {
        watchedObjects.clear()
    }
}

/**
 * Leak detection utilities for common scenarios.
 */
object LeakDetectionUtils {

    /**
     * Check if a class is likely to cause memory leaks.
     */
    fun isLeakProneClass(clazz: KClass<*>): Boolean {
        val leakPronePatterns = listOf(
            "Activity",
            "Fragment",
            "View",
            "Context",
            "Dialog",
            "Bitmap"
        )

        return leakPronePatterns.any { pattern ->
            clazz.simpleName?.contains(pattern) == true
        }
    }

    /**
     * Create a safe weak reference with automatic leak watching.
     */
    inline fun <reified T : Any> safeWeakReference(
        detector: MemoryLeakDetector,
        obj: T
    ): WeakReference<T> {
        detector.watch(obj, T::class.simpleName ?: "Unknown")
        return WeakReference(obj)
    }
}

/**
 * AutoCloseable wrapper that automatically unwatches objects.
 */
class WatchedReference<T : Any>(
    private val detector: MemoryLeakDetector,
    val value: T,
    private val description: String = value.javaClass.simpleName
) : AutoCloseable {

    init {
        detector.watch(value, description)
    }

    override fun close() {
        detector.unwatch(value, description)
    }
}

/**
 * Extension function to watch an object and get a closable reference.
 */
fun <T : Any> T.watched(detector: MemoryLeakDetector, description: String? = null): WatchedReference<T> {
    return WatchedReference(detector, this, description ?: this.javaClass.simpleName)
}
