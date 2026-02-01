/*
 * Copyright 2025 Tanmay Patil
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.google.ai.edge.gallery.performance

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Debug
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

@PublishedApi
internal const val TAG = "PerformanceMonitor"
private const val MAX_FRAME_TIME_SAMPLES = 120 // 2 seconds at 60fps
private const val MAX_MEMORY_SAMPLES = 60 // 1 minute at 1 sample/sec
private const val ANR_THRESHOLD_MS = 5000L // 5 seconds
private const val FRAME_TIME_WARNING_THRESHOLD = 16.67f // ~60fps
private const val FRAME_TIME_CRITICAL_THRESHOLD = 33.33f // ~30fps

/**
 * Frame timing metrics.
 * @property frameTimeMs Time to render frame in milliseconds
 * @property isJank Whether this frame missed the deadline
 * @property timestamp When the frame was rendered
 */
data class FrameMetrics(
    val frameTimeMs: Float,
    val isJank: Boolean,
    val timestamp: Long = System.currentTimeMillis()
) {
    companion object {
        fun fromFrameTime(frameTimeMs: Float): FrameMetrics {
            return FrameMetrics(
                frameTimeMs = frameTimeMs,
                isJank = frameTimeMs > FRAME_TIME_WARNING_THRESHOLD
            )
        }
    }
}

/**
 * Memory usage metrics.
 * @property heapSize Total heap size in MB
 * @property heapUsed Used heap in MB
 * @property nativeHeap Native heap in MB
 * @property totalPss Total PSS in MB
 * @property availableMemory Available system memory in MB
 * @property timestamp When the measurement was taken
 */
data class MemoryMetrics(
    val heapSize: Long,
    val heapUsed: Long,
    val nativeHeap: Long,
    val totalPss: Long,
    val availableMemory: Long,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * ANR (Application Not Responding) information.
 * @property threadName Name of the thread that was blocked
 * @property blockedDurationMs How long the thread was blocked
 * @property stackTrace Stack trace at the time of detection
 * @property timestamp When the ANR condition was detected
 */
data class AnrInfo(
    val threadName: String,
    val blockedDurationMs: Long,
    val stackTrace: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Performance snapshot containing all metrics at a point in time.
 * @property timestamp When the snapshot was taken
 * @property averageFrameTime Average frame time over the window
 * @property jankRate Percentage of frames that were janky
 * @property memoryMetrics Current memory metrics
 * @property cpuUsagePercent CPU usage percentage
 */
data class PerformanceSnapshot(
    val timestamp: Long,
    val averageFrameTime: Float,
    val jankRate: Float,
    val memoryMetrics: MemoryMetrics,
    val cpuUsagePercent: Float
)

/**
 * Performance report for analytics/logging.
 * @property sessionDurationMs Duration of the monitoring session
 * @property totalFrames Total frames rendered
 * @property jankFrames Number of janky frames
 * @property averageFrameTimeMs Average frame time
 * @property maxFrameTimeMs Maximum frame time observed
 * @property averageMemoryMB Average memory usage
 * @property peakMemoryMB Peak memory usage
 * @property anrCount Number of ANR conditions detected
 */
data class PerformanceReport(
    val sessionDurationMs: Long,
    val totalFrames: Int,
    val jankFrames: Int,
    val averageFrameTimeMs: Float,
    val maxFrameTimeMs: Float,
    val averageMemoryMB: Long,
    val peakMemoryMB: Long,
    val anrCount: Int
)

/**
 * Listener interface for performance events.
 */
interface PerformanceListener {
    fun onFrameTimeExceeded(threshold: Float, actualTime: Float)
    fun onMemoryWarning(usedMB: Long, availableMB: Long)
    fun onAnrDetected(anrInfo: AnrInfo)
    fun onPerformanceReport(report: PerformanceReport)
}

/**
 * Real User Monitoring (RUM) performance monitor.
 * Tracks:
 * - Frame times and jank detection
 * - Memory usage over time
 * - ANR detection
 * - CPU usage
 * - Custom performance metrics
 * All data is kept local (privacy-first) with optional export.
 */
@Singleton
class PerformanceMonitor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

    // Frame time tracking
    private val frameTimeSamples = ConcurrentLinkedQueue<FrameMetrics>()
    private val _currentFrameMetrics = MutableStateFlow<FrameMetrics?>(null)
    val currentFrameMetrics: StateFlow<FrameMetrics?> = _currentFrameMetrics.asStateFlow()

    // Memory tracking
    private val memorySamples = ConcurrentLinkedQueue<MemoryMetrics>()
    private val _currentMemoryMetrics = MutableStateFlow<MemoryMetrics?>(null)
    val currentMemoryMetrics: StateFlow<MemoryMetrics?> = _currentMemoryMetrics.asStateFlow()

    // ANR tracking
    private val _anrHistory = MutableStateFlow<List<AnrInfo>>(emptyList())
    val anrHistory: StateFlow<List<AnrInfo>> = _anrHistory.asStateFlow()

    // Performance snapshot
    private val _performanceSnapshot = MutableStateFlow<PerformanceSnapshot?>(null)
    val performanceSnapshot: StateFlow<PerformanceSnapshot?> = _performanceSnapshot.asStateFlow()

    // Listeners
    private val listeners = mutableListOf<PerformanceListener>()

    // Monitoring state
    private var isMonitoring = false
    private var sessionStartTime = 0L
    private var lastAnrCheckTime = 0L
    private var uiThreadStartTime = 0L

    // CPU tracking
    private var lastCpuTime = 0L
    private var lastAppCpuTime = 0L

    init {
        // Setup ANR detection on main thread
        setupAnrDetection()
    }

    /**
     * Start performance monitoring.
     */
    fun startMonitoring() {
        if (isMonitoring) return

        isMonitoring = true
        sessionStartTime = SystemClock.elapsedRealtime()
        lastCpuTime = SystemClock.elapsedRealtime()
        lastAppCpuTime = Process.getElapsedCpuTime()

        // Start memory monitoring
        scope.launch {
            while (isActive && isMonitoring) {
                recordMemoryUsage()
                delay(1000) // Sample every second
            }
        }

        // Start performance snapshot updates
        scope.launch {
            while (isActive && isMonitoring) {
                updatePerformanceSnapshot()
                delay(5000) // Update every 5 seconds
            }
        }

        Log.d(TAG, "Performance monitoring started")
    }

    /**
     * Stop performance monitoring.
     */
    fun stopMonitoring() {
        isMonitoring = false

        // Generate final report
        val report = generateReport()
        listeners.forEach { it.onPerformanceReport(report) }

        Log.d(TAG, "Performance monitoring stopped")
    }

    /**
     * Record a frame time measurement.
     *
     * @param frameTimeMs Time to render the frame in milliseconds
     */
    fun recordFrameTime(frameTimeMs: Float) {
        if (!isMonitoring) return

        val metrics = FrameMetrics.fromFrameTime(frameTimeMs)
        frameTimeSamples.offer(metrics)
        _currentFrameMetrics.value = metrics

        // Maintain max samples
        while (frameTimeSamples.size > MAX_FRAME_TIME_SAMPLES) {
            frameTimeSamples.poll()
        }

        // Notify listeners if frame time is critical
        if (frameTimeMs > FRAME_TIME_CRITICAL_THRESHOLD) {
            listeners.forEach { it.onFrameTimeExceeded(FRAME_TIME_CRITICAL_THRESHOLD, frameTimeMs) }
        }
    }

    /**
     * Record frame time from Choreographer callback.
     */
    fun recordFrameTimeFromChoreographer(frameTimeNanos: Long) {
        val frameTimeMs = frameTimeNanos / 1_000_000f
        recordFrameTime(frameTimeMs)
    }

    /**
     * Record current memory usage.
     */
    suspend fun recordMemoryUsage() {
        val metrics = getCurrentMemoryMetrics()
        memorySamples.offer(metrics)
        _currentMemoryMetrics.value = metrics

        // Maintain max samples
        while (memorySamples.size > MAX_MEMORY_SAMPLES) {
            memorySamples.poll()
        }

        // Check for memory warnings
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)

        if (memoryInfo.lowMemory) {
            listeners.forEach { it.onMemoryWarning(metrics.heapUsed, metrics.availableMemory) }
        }
    }

    /**
     * Get current memory metrics.
     */
    fun getCurrentMemoryMetrics(): MemoryMetrics {
        val runtime = Runtime.getRuntime()
        val heapSize = runtime.totalMemory() / (1024 * 1024)
        val heapFree = runtime.freeMemory() / (1024 * 1024)
        val heapUsed = heapSize - heapFree

        val nativeHeap = Debug.getNativeHeapSize() / (1024 * 1024)

        val totalPss = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val memoryInfo = Debug.MemoryInfo()
            Debug.getMemoryInfo(memoryInfo)
            memoryInfo.totalPss / 1024L
        } else {
            0L
        }

        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        val availableMemory = memInfo.availMem / (1024 * 1024)

        return MemoryMetrics(
            heapSize = heapSize,
            heapUsed = heapUsed,
            nativeHeap = nativeHeap,
            totalPss = totalPss,
            availableMemory = availableMemory
        )
    }

    /**
     * Add a performance listener.
     */
    fun addListener(listener: PerformanceListener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
        }
    }

    /**
     * Remove a performance listener.
     */
    fun removeListener(listener: PerformanceListener) {
        listeners.remove(listener)
    }

    /**
     * Get average frame time over the sampling window.
     */
    fun getAverageFrameTime(): Float {
        val samples = frameTimeSamples.toList()
        return if (samples.isNotEmpty()) {
            samples.map { it.frameTimeMs }.average().toFloat()
        } else 0f
    }

    /**
     * Get jank rate (percentage of frames that missed deadline).
     */
    fun getJankRate(): Float {
        val samples = frameTimeSamples.toList()
        return if (samples.isNotEmpty()) {
            samples.count { it.isJank }.toFloat() / samples.size
        } else 0f
    }

    /**
     * Get current CPU usage percentage.
     */
    fun getCpuUsage(): Float {
        val currentTime = SystemClock.elapsedRealtime()
        val currentCpuTime = Process.getElapsedCpuTime()

        val timeDiff = currentTime - lastCpuTime
        val cpuDiff = currentCpuTime - lastAppCpuTime

        return if (timeDiff > 0) {
            (cpuDiff.toFloat() / timeDiff.toFloat()) * 100f
        } else 0f
    }

    /**
     * Generate performance report.
     */
    fun generateReport(): PerformanceReport {
        val sessionDuration = SystemClock.elapsedRealtime() - sessionStartTime
        val frames = frameTimeSamples.toList()
        val memories = memorySamples.toList()

        return PerformanceReport(
            sessionDurationMs = sessionDuration,
            totalFrames = frames.size,
            jankFrames = frames.count { it.isJank },
            averageFrameTimeMs = getAverageFrameTime(),
            maxFrameTimeMs = frames.maxOfOrNull { it.frameTimeMs } ?: 0f,
            averageMemoryMB = if (memories.isNotEmpty()) {
                memories.map { it.heapUsed }.average().toLong()
            } else 0L,
            peakMemoryMB = memories.maxOfOrNull { it.heapUsed } ?: 0L,
            anrCount = _anrHistory.value.size
        )
    }

    /**
     * Export performance data to file.
     */
    suspend fun exportToFile(): File = withContext(Dispatchers.IO) {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(context.cacheDir, "performance_report_$timestamp.txt")

        file.bufferedWriter().use { writer ->
            writer.write("Performance Report - ${Date()}\n")
            writer.write("=" * 50 + "\n\n")

            val report = generateReport()
            writer.write("Session Duration: ${report.sessionDurationMs}ms\n")
            writer.write("Total Frames: ${report.totalFrames}\n")
            writer.write("Jank Frames: ${report.jankFrames}\n")
            writer.write("Average Frame Time: ${report.averageFrameTimeMs}ms\n")
            writer.write("Max Frame Time: ${report.maxFrameTimeMs}ms\n")
            writer.write("Average Memory: ${report.averageMemoryMB}MB\n")
            writer.write("Peak Memory: ${report.peakMemoryMB}MB\n")
            writer.write("ANR Count: ${report.anrCount}\n\n")

            writer.write("Frame Time Samples:\n")
            frameTimeSamples.forEach { sample ->
                writer.write("  ${sample.timestamp}: ${sample.frameTimeMs}ms (jank: ${sample.isJank})\n")
            }

            writer.write("\nMemory Samples:\n")
            memorySamples.forEach { sample ->
                writer.write("  ${sample.timestamp}: ${sample.heapUsed}MB used\n")
            }

            writer.write("\nANR History:\n")
            _anrHistory.value.forEach { anr ->
                writer.write("  ${anr.timestamp}: ${anr.threadName} blocked for ${anr.blockedDurationMs}ms\n")
            }
        }

        file
    }

    private fun setupAnrDetection() {
        // Monitor UI thread for ANR conditions
        mainHandler.post(object : Runnable {
            override fun run() {
                if (!isMonitoring) {
                    mainHandler.postDelayed(this, 1000)
                    return
                }

                val currentTime = SystemClock.uptimeMillis()

                if (uiThreadStartTime > 0) {
                    val blockedTime = currentTime - uiThreadStartTime
                    if (blockedTime > ANR_THRESHOLD_MS) {
                        detectAnr(blockedTime)
                    }
                }

                uiThreadStartTime = currentTime
                mainHandler.postDelayed(this, 100)
            }
        })
    }

    private fun detectAnr(blockedTime: Long) {
        val stackTrace = Looper.getMainLooper().thread.stackTrace
            .joinToString("\n") { "    at $it" }

        val anrInfo = AnrInfo(
            threadName = "main",
            blockedDurationMs = blockedTime,
            stackTrace = stackTrace
        )

        _anrHistory.value = _anrHistory.value + anrInfo
        listeners.forEach { it.onAnrDetected(anrInfo) }

        Log.w(TAG, "ANR detected: main thread blocked for ${blockedTime}ms")
    }

    private suspend fun updatePerformanceSnapshot() {
        val snapshot = PerformanceSnapshot(
            timestamp = System.currentTimeMillis(),
            averageFrameTime = getAverageFrameTime(),
            jankRate = getJankRate(),
            memoryMetrics = getCurrentMemoryMetrics(),
            cpuUsagePercent = getCpuUsage()
        )
        _performanceSnapshot.value = snapshot
    }

    private operator fun String.times(count: Int): String = repeat(count)
}

/**
 * Composable to track frame metrics for a specific screen.
 */
@Composable
fun FrameMetricsTracker(
    screenName: String,
    monitor: PerformanceMonitor
) {
    val view = LocalView.current

    DisposableEffect(screenName) {
        val window = ViewCompat.getWindowInsetsController(view)

        // Setup frame callback
        val choreographer = android.view.Choreographer.getInstance()
        val frameCallback = android.view.Choreographer.FrameCallback { frameTimeNanos ->
            monitor.recordFrameTimeFromChoreographer(frameTimeNanos)
        }

        choreographer.postFrameCallback(frameCallback)

        onDispose {
            choreographer.removeFrameCallback(frameCallback)
        }
    }
}

/**
 * Extension to track composable performance.
 */
@Composable
inline fun <T> trackComposition(
    monitor: PerformanceMonitor,
    name: String,
    content: @Composable () -> T
): T {
    val startTime = SystemClock.elapsedRealtime()
    val result = content()
    val duration = SystemClock.elapsedRealtime() - startTime

    if (duration > 16) { // Log slow compositions
        Log.d(TAG, "Slow composition '$name': ${duration}ms")
    }

    return result
}
