/*
 * Copyright 2025 Tanmay Patil
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.google.ai.edge.gallery.util.memory

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Debug
import android.util.Log
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
import java.lang.ref.WeakReference
import javax.inject.Inject
import javax.inject.Singleton

@PublishedApi
internal const val TAG = "AdaptiveMemoryManager"
private const val MONITORING_INTERVAL_MS = 5000L // 5 seconds
private const val MEMORY_PRESSURE_THRESHOLD = 0.85f // 85% memory usage

/**
 * Memory profile configuration based on device capabilities.
 * @property maxTokens Maximum LLM tokens
 * @property thumbnailCacheSizeMB Thumbnail cache size in MB
 * @property imageDecodeSize Maximum image decode dimension
 * @property enableAnimations Whether animations should be enabled
 * @property enableEffects Whether visual effects should be enabled
 * @property maxConcurrentOperations Maximum concurrent operations
 */
data class MemoryProfile(
    val maxTokens: Int,
    val thumbnailCacheSizeMB: Int,
    val imageDecodeSize: Int,
    val enableAnimations: Boolean,
    val enableEffects: Boolean,
    val maxConcurrentOperations: Int
)

/**
 * Memory pressure levels.
 */
enum class MemoryPressure {
    NORMAL,     // Normal operation
    ELEVATED,   // Slightly elevated, minor adjustments
    HIGH,       // High pressure, significant adjustments
    CRITICAL    // Critical, emergency measures
}

/**
 * Memory usage statistics.
 */
data class MemoryStats(
    val totalMemoryMB: Long,
    val availableMemoryMB: Long,
    val usedMemoryMB: Long,
    val appMemoryMB: Long,
    val memoryPressure: MemoryPressure,
    val usagePercent: Float,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Adaptive memory manager that adjusts app behavior based on available memory.
 * Features:
 * - Dynamic memory profile selection
 * - Real-time memory monitoring
 * - Automatic quality degradation under pressure
 * - Memory pressure notifications
 */
@Singleton
class AdaptiveMemoryManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val memoryPoolManager: MemoryPoolManager,
    private val referenceCacheManager: ReferenceCacheManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

    private val _currentProfile = MutableStateFlow(calculateOptimalProfile())
    val currentProfile: StateFlow<MemoryProfile> = _currentProfile.asStateFlow()

    private val _memoryStats = MutableStateFlow(getCurrentMemoryStats())
    val memoryStats: StateFlow<MemoryStats> = _memoryStats.asStateFlow()

    private val _memoryPressure = MutableStateFlow(MemoryPressure.NORMAL)
    val memoryPressure: StateFlow<MemoryPressure> = _memoryPressure.asStateFlow()

    // Use WeakReferences for listeners to prevent memory leaks
    // Listeners that are no longer referenced elsewhere will be automatically cleaned up
    private val listeners = mutableListOf<WeakReference<MemoryPressureListener>>()

    interface MemoryPressureListener {
        fun onMemoryPressureChanged(pressure: MemoryPressure, profile: MemoryProfile)
    }

    init {
        startMonitoring()
    }

    /**
     * Add a memory pressure listener.
     * Uses WeakReference to prevent memory leaks - listeners will be automatically
     * cleaned up when no longer referenced elsewhere.
     */
    fun addListener(listener: MemoryPressureListener) {
        // Check if listener is already registered (dereferencing weak refs)
        if (listeners.none { it.get() == listener }) {
            listeners.add(WeakReference(listener))
        }
        // Clean up any null references while we're at it
        listeners.removeAll { it.get() == null }
    }

    /**
     * Remove a memory pressure listener.
     */
    fun removeListener(listener: MemoryPressureListener) {
        listeners.removeAll { it.get() == listener || it.get() == null }
    }

    /**
     * Calculate optimal memory profile based on device capabilities.
     */
    fun calculateOptimalProfile(): MemoryProfile {
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)

        val totalMemoryMB = memInfo.totalMem / (1024 * 1024)

        return when {
            totalMemoryMB >= 8192 -> MemoryProfile(
                maxTokens = 4096,
                thumbnailCacheSizeMB = 128,
                imageDecodeSize = 2048,
                enableAnimations = true,
                enableEffects = true,
                maxConcurrentOperations = 4
            )
            totalMemoryMB >= 6144 -> MemoryProfile(
                maxTokens = 2048,
                thumbnailCacheSizeMB = 96,
                imageDecodeSize = 1536,
                enableAnimations = true,
                enableEffects = true,
                maxConcurrentOperations = 3
            )
            totalMemoryMB >= 4096 -> MemoryProfile(
                maxTokens = 1024,
                thumbnailCacheSizeMB = 64,
                imageDecodeSize = 1024,
                enableAnimations = true,
                enableEffects = false,
                maxConcurrentOperations = 2
            )
            totalMemoryMB >= 3072 -> MemoryProfile(
                maxTokens = 512,
                thumbnailCacheSizeMB = 48,
                imageDecodeSize = 768,
                enableAnimations = false,
                enableEffects = false,
                maxConcurrentOperations = 2
            )
            else -> MemoryProfile(
                maxTokens = 256,
                thumbnailCacheSizeMB = 32,
                imageDecodeSize = 512,
                enableAnimations = false,
                enableEffects = false,
                maxConcurrentOperations = 1
            )
        }
    }

    /**
     * Get current memory statistics.
     */
    fun getCurrentMemoryStats(): MemoryStats {
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)

        val totalMemoryMB = memInfo.totalMem / (1024 * 1024)
        val availableMemoryMB = memInfo.availMem / (1024 * 1024)
        val usedMemoryMB = totalMemoryMB - availableMemoryMB
        val usagePercent = usedMemoryMB.toFloat() / totalMemoryMB

        val appMemoryMB = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Debug.MemoryInfo().also {
                Debug.getMemoryInfo(it)
            }.totalPss / 1024L
        } else {
            Runtime.getRuntime().let {
                (it.totalMemory() - it.freeMemory()) / (1024 * 1024)
            }
        }

        val pressure = when {
            usagePercent > 0.95f || memInfo.lowMemory -> MemoryPressure.CRITICAL
            usagePercent > 0.90f -> MemoryPressure.HIGH
            usagePercent > 0.80f -> MemoryPressure.ELEVATED
            else -> MemoryPressure.NORMAL
        }

        return MemoryStats(
            totalMemoryMB = totalMemoryMB,
            availableMemoryMB = availableMemoryMB,
            usedMemoryMB = usedMemoryMB,
            appMemoryMB = appMemoryMB,
            memoryPressure = pressure,
            usagePercent = usagePercent
        )
    }

    /**
     * Adjust memory profile based on current pressure.
     */
    fun adjustProfileForPressure(pressure: MemoryPressure): MemoryProfile {
        val baseProfile = calculateOptimalProfile()

        return when (pressure) {
            MemoryPressure.NORMAL -> baseProfile
            MemoryPressure.ELEVATED -> baseProfile.copy(
                thumbnailCacheSizeMB = (baseProfile.thumbnailCacheSizeMB * 0.8).toInt(),
                maxConcurrentOperations = (baseProfile.maxConcurrentOperations * 0.8).toInt().coerceAtLeast(1)
            )
            MemoryPressure.HIGH -> baseProfile.copy(
                maxTokens = baseProfile.maxTokens / 2,
                thumbnailCacheSizeMB = baseProfile.thumbnailCacheSizeMB / 2,
                imageDecodeSize = baseProfile.imageDecodeSize / 2,
                enableAnimations = false,
                enableEffects = false,
                maxConcurrentOperations = 1
            )
            MemoryPressure.CRITICAL -> baseProfile.copy(
                maxTokens = baseProfile.maxTokens / 4,
                thumbnailCacheSizeMB = baseProfile.thumbnailCacheSizeMB / 4,
                imageDecodeSize = baseProfile.imageDecodeSize / 4,
                enableAnimations = false,
                enableEffects = false,
                maxConcurrentOperations = 1
            )
        }
    }

    /**
     * Request memory trimming.
     */
    suspend fun trimMemory() {
        Log.d(TAG, "Trimming memory...")

        // Clear object pools
        memoryPoolManager.clearAllPools()

        // Clear reference caches
        referenceCacheManager.clearAll()

        // Trigger GC
        System.gc()

        // Update stats
        updateMemoryStats()

        Log.d(TAG, "Memory trimmed")
    }

    /**
     * Check if current memory state allows an operation.
     *
     * @param requiredMemoryMB Estimated memory required in MB
     * @return true if operation can proceed
     */
    fun canPerformOperation(requiredMemoryMB: Int): Boolean {
        val stats = getCurrentMemoryStats()
        return stats.availableMemoryMB >= requiredMemoryMB &&
               stats.memoryPressure != MemoryPressure.CRITICAL
    }

    /**
     * Get recommended image decode size based on current profile.
     */
    fun getRecommendedImageDecodeSize(): Int {
        return _currentProfile.value.imageDecodeSize
    }

    /**
     * Check if animations should be enabled.
     */
    fun shouldEnableAnimations(): Boolean {
        return _currentProfile.value.enableAnimations
    }

    /**
     * Check if visual effects should be enabled.
     */
    fun shouldEnableEffects(): Boolean {
        return _currentProfile.value.enableEffects
    }

    private fun startMonitoring() {
        scope.launch {
            while (isActive) {
                updateMemoryStats()
                delay(MONITORING_INTERVAL_MS)
            }
        }
    }

    private suspend fun updateMemoryStats() {
        // Move memory stats collection to IO dispatcher to avoid blocking main thread
        val stats = withContext(Dispatchers.IO) {
            getCurrentMemoryStats()
        }
        _memoryStats.value = stats

        val newPressure = stats.memoryPressure
        if (newPressure != _memoryPressure.value) {
            _memoryPressure.value = newPressure

            // Adjust profile for new pressure level
            val adjustedProfile = adjustProfileForPressure(newPressure)
            _currentProfile.value = adjustedProfile

            // Notify listeners using WeakReferences - clean up null refs as we go
            listeners.removeAll { ref ->
                val listener = ref.get()
                if (listener == null) {
                    true // Remove null reference
                } else {
                    listener.onMemoryPressureChanged(newPressure, adjustedProfile)
                    false // Keep valid reference
                }
            }

            Log.d(TAG, "Memory pressure changed to $newPressure, profile adjusted")

            // Take emergency action if critical
            if (newPressure == MemoryPressure.CRITICAL) {
                trimMemory()
            }
        }
    }
}

/**
 * Memory-aware executor that limits concurrent operations based on memory pressure.
 */
class MemoryAwareExecutor(
    private val memoryManager: AdaptiveMemoryManager
) {
    private val activeOperations = MutableStateFlow(0)

    /**
     * Execute block if memory allows, otherwise skip or defer.
     *
     * @param requiredMemoryMB Memory required for operation
     * @param block Operation to execute
     * @return Result of operation or null if skipped
     */
    suspend fun <T> executeIfAllowed(
        requiredMemoryMB: Int = 0,
        block: suspend () -> T
    ): T? {
        val profile = memoryManager.currentProfile.value

        if (activeOperations.value >= profile.maxConcurrentOperations) {
            Log.d(TAG, "Max concurrent operations reached, deferring")
            return null
        }

        if (requiredMemoryMB > 0 && !memoryManager.canPerformOperation(requiredMemoryMB)) {
            Log.d(TAG, "Insufficient memory for operation (need $requiredMemoryMB MB)")
            return null
        }

        activeOperations.value++
        try {
            return block()
        } finally {
            activeOperations.value--
        }
    }

    /**
     * Execute block with automatic memory check and retry.
     *
     * @param requiredMemoryMB Memory required
     * @param maxRetries Maximum retry attempts
     * @param block Operation to execute
     */
    suspend fun <T> executeWithRetry(
        requiredMemoryMB: Int = 0,
        maxRetries: Int = 3,
        block: suspend () -> T
    ): Result<T> {
        var lastException: Throwable? = null

        repeat(maxRetries) { attempt ->
            if (memoryManager.canPerformOperation(requiredMemoryMB)) {
                try {
                    val result = block()
                    return Result.success(result)
                } catch (e: OutOfMemoryError) {
                    lastException = e
                    memoryManager.trimMemory()
                    delay(100L * (attempt + 1)) // Exponential backoff
                } catch (e: Exception) {
                    return Result.failure(e)
                }
            } else {
                delay(100L * (attempt + 1))
            }
        }

        return Result.failure(
            lastException ?: IllegalStateException("Failed after $maxRetries attempts")
        )
    }
}

/**
 * Extension functions for memory-aware operations.
 */

/**
 * Execute block with memory check.
 */
suspend inline fun <T> AdaptiveMemoryManager.withMemoryCheck(
    requiredMemoryMB: Int = 0,
    crossinline block: suspend () -> T
): T? {
    return if (canPerformOperation(requiredMemoryMB)) {
        block()
    } else {
        Log.d(TAG, "Operation skipped due to memory constraints")
        null
    }
}
