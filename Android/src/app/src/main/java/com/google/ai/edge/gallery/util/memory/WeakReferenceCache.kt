/*
 * Copyright 2025 Tanmay Patil
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.google.ai.edge.gallery.util.memory

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.lang.ref.ReferenceQueue
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Weak reference cache entry that tracks cleanup.
 */
private class WeakCacheEntry<T>(
    referent: T,
    val key: String,
    queue: ReferenceQueue<T>,
    val onEvicted: ((String) -> Unit)? = null
) : WeakReference<T>(referent, queue)

/**
 * Weak reference cache that allows GC to collect values when memory is low.
 * Use this for large objects that can be recomputed/reloaded:
 * - Bitmaps
 * - Large data structures
 * - Cached computation results
 * @param T Type of cached values
 */
class WeakReferenceCache<T> @Inject constructor() {

    companion object {
        private const val TAG = "WeakReferenceCache"
    }

    private val cache = ConcurrentHashMap<String, WeakCacheEntry<T>>()
    private val referenceQueue = ReferenceQueue<T>()
    private val lock = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var cleanupJobStarted = false

    init {
        startCleanupJob()
    }

    /**
     * Put a value into the weak reference cache.
     *
     * @param key Cache key
     * @param value Value to cache
     * @param onEvicted Optional callback when value is evicted by GC
     */
    fun put(key: String, value: T, onEvicted: ((String) -> Unit)? = null) {
        val entry = WeakCacheEntry(value, key, referenceQueue, onEvicted)
        cache[key] = entry
    }

    /**
     * Get a value from the weak reference cache.
     *
     * @param key Cache key
     * @return Value if still referenced, null otherwise
     */
    fun get(key: String): T? {
        val entry = cache[key]
        return entry?.get()
    }

    /**
     * Remove a value from the cache.
     *
     * @param key Cache key
     * @return Removed value if present
     */
    fun remove(key: String): T? {
        val entry = cache.remove(key)
        return entry?.get()
    }

    /**
     * Check if key exists in cache and value is still referenced.
     *
     * @param key Cache key
     * @return true if value exists and is referenced
     */
    fun containsKey(key: String): Boolean {
        val entry = cache[key]
        return entry?.get() != null
    }

    /**
     * Get all keys with valid references.
     */
    fun getValidKeys(): Set<String> {
        return cache.entries
            .filter { it.value.get() != null }
            .map { it.key }
            .toSet()
    }

    /**
     * Clear all entries from cache.
     */
    suspend fun clear() = lock.withLock {
        cache.clear()
    }

    /**
     * Get cache size (including cleared references).
     */
    fun size(): Int = cache.size

    /**
     * Get count of valid (non-cleared) references.
     */
    fun validSize(): Int {
        return cache.values.count { it.get() != null }
    }

    /**
     * Clean up cleared references.
     */
    private suspend fun cleanup() = lock.withLock {
        var cleaned = 0
        var entry = referenceQueue.poll()

        while (entry != null) {
            @Suppress("UNCHECKED_CAST")
            val weakEntry = entry as WeakCacheEntry<T>
            cache.remove(weakEntry.key)
            weakEntry.onEvicted?.invoke(weakEntry.key)
            cleaned++
            entry = referenceQueue.poll()
        }

        if (cleaned > 0) {
            Log.d(TAG, "Cleaned up $cleaned weak references")
        }
    }

    private fun startCleanupJob() {
        if (cleanupJobStarted) return
        cleanupJobStarted = true

        scope.launch {
            while (isActive) {
                cleanup()
                delay(30000) // Check every 30 seconds
            }
        }
    }
}

/**
 * Soft reference cache that keeps values until memory is needed.
 * Soft references are cleared before OutOfMemoryError is thrown.
 * Use for memory-sensitive caching where you want to keep data
 * as long as possible.
 */
class SoftReferenceCache<T> @Inject constructor() {

    companion object {
        private const val TAG = "SoftReferenceCache"
    }

    private val cache = ConcurrentHashMap<String, java.lang.ref.SoftReference<T>>()
    private val lock = Mutex()

    /**
     * Put a value into the soft reference cache.
     */
    suspend fun put(key: String, value: T) = lock.withLock {
        cache[key] = java.lang.ref.SoftReference(value)
    }

    /**
     * Get a value from the soft reference cache.
     */
    suspend fun get(key: String): T? = lock.withLock {
        val ref = cache[key]
        val value = ref?.get()

        // Remove if reference was cleared
        if (ref != null && value == null) {
            cache.remove(key)
        }

        value
    }

    /**
     * Remove a value from the cache.
     */
    suspend fun remove(key: String): T? = lock.withLock {
        cache.remove(key)?.get()
    }

    /**
     * Clear all entries.
     */
    suspend fun clear() = lock.withLock {
        cache.clear()
    }

    /**
     * Clean up cleared references.
     */
    suspend fun cleanup() = lock.withLock {
        val iterator = cache.entries.iterator()
        var cleaned = 0

        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.value.get() == null) {
                iterator.remove()
                cleaned++
            }
        }

        if (cleaned > 0) {
            Log.d(TAG, "Cleaned up $cleaned soft references")
        }
    }

    fun size(): Int = cache.size
}

/**
 * Reference cache manager that coordinates weak and soft reference caches.
 */
@Singleton
class ReferenceCacheManager @Inject constructor(
    val weakCache: WeakReferenceCache<Any>,
    val softCache: SoftReferenceCache<Any>
) {
    /**
     * Clear all reference caches.
     */
    suspend fun clearAll() {
        weakCache.clear()
        softCache.clear()
    }

    /**
     * Clean up cleared references from all caches.
     */
    suspend fun cleanup() {
        softCache.cleanup()
    }

    /**
     * Get combined statistics.
     */
    fun getStats(): ReferenceCacheStats {
        return ReferenceCacheStats(
            weakCacheSize = weakCache.size(),
            weakCacheValidSize = weakCache.validSize(),
            softCacheSize = softCache.size()
        )
    }

    data class ReferenceCacheStats(
        val weakCacheSize: Int,
        val weakCacheValidSize: Int,
        val softCacheSize: Int
    )
}
