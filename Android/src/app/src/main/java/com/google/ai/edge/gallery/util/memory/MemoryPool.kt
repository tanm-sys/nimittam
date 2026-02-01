/*
 * Copyright 2025-2026 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.ai.edge.gallery.util.memory

import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Object pool interface for reusable objects.
 */
interface ObjectPool<T> {
    /**
     * Acquire an object from the pool.
     * @return Object from pool or newly created if pool is empty
     */
    suspend fun acquire(): T

    /**
     * Release an object back to the pool.
     * @param obj Object to return to pool
     */
    suspend fun release(obj: T)

    /**
     * Get current pool size.
     */
    fun size(): Int

    /**
     * Get maximum pool size.
     */
    fun maxSize(): Int

    /**
     * Clear all objects from pool.
     */
    suspend fun clear()
}

/**
 * Configuration for object pool behavior.
 */
data class PoolConfig(
    val maxSize: Int = 10,
    val initialSize: Int = 0,
    val enableTracking: Boolean = false
)

/**
 * Generic object pool implementation with thread-safe operations.
 *
 * @param T Type of object to pool
 * @param config Pool configuration
 * @param factory Factory function to create new objects
 * @param reset Function to reset object state before returning to pool
 */
/**
 * Generic object pool implementation with thread-safe operations.
 *
 * OPTIMIZATION: Removed Mutex lock since ConcurrentLinkedQueue is already thread-safe.
 * This eliminates double-locking overhead while maintaining thread safety.
 *
 * @param T Type of object to pool
 * @param config Pool configuration
 * @param factory Factory function to create new objects
 * @param reset Function to reset object state before returning to pool
 */
class GenericObjectPool<T>(
    private val config: PoolConfig,
    private val factory: () -> T,
    private val reset: (T) -> Boolean = { true }
) : ObjectPool<T> {

    companion object {
        private const val TAG = "MemoryPool"
    }

    // ConcurrentLinkedQueue is thread-safe, no additional locking needed
    private val pool = ConcurrentLinkedQueue<T>()
    private val activeCount = AtomicInteger(0)

    init {
        // Pre-populate pool if initial size > 0
        repeat(config.initialSize) {
            pool.offer(factory())
        }
    }

    /**
     * Acquire an object from the pool.
     * Thread-safe without explicit locking due to ConcurrentLinkedQueue.
     */
    override suspend fun acquire(): T {
        val obj = pool.poll()
        return if (obj != null) {
            activeCount.incrementAndGet()
            if (config.enableTracking) {
                Log.d(TAG, "Acquired from pool. Active: ${activeCount.get()}, Available: ${pool.size}")
            }
            obj
        } else {
            activeCount.incrementAndGet()
            if (config.enableTracking) {
                Log.d(TAG, "Created new object. Active: ${activeCount.get()}")
            }
            factory()
        }
    }

    /**
     * Release an object back to the pool.
     * Thread-safe without explicit locking due to ConcurrentLinkedQueue.
     * Only adds object back to pool if reset() returns true.
     */
    override suspend fun release(obj: T) {
        val shouldReturn = reset(obj)
        activeCount.decrementAndGet()

        if (shouldReturn && pool.size < config.maxSize) {
            pool.offer(obj)
            if (config.enableTracking) {
                Log.d(TAG, "Released to pool. Active: ${activeCount.get()}, Available: ${pool.size}")
            }
        } else {
            if (config.enableTracking) {
                Log.d(TAG, "Pool full or reset failed, discarding object. Active: ${activeCount.get()}")
            }
        }
    }

    override fun size(): Int = pool.size

    override fun maxSize(): Int = config.maxSize

    /**
     * Clear all objects from pool.
     * Note: AtomicInteger operations are thread-safe.
     */
    override suspend fun clear() {
        pool.clear()
        activeCount.set(0)
    }

    /**
     * Get number of currently active (acquired) objects.
     */
    fun activeCount(): Int = activeCount.get()
}

/**
 * Bitmap pool for efficient bitmap reuse with size bucketing.
 *
 * OPTIMIZATION: Implements size-bucketed pools to reduce memory waste.
 * Bitmaps are pooled by size category (small, medium, large) to minimize
 * the need to create new bitmaps when requested sizes don't match pooled ones.
 *
 * Prevents frequent GC from bitmap allocation/deallocation.
 */
class BitmapPool @Inject constructor() {

    // Size buckets for efficient bitmap reuse
    private data class SizeBucket(
        val maxWidth: Int,
        val maxHeight: Int,
        val label: String
    )
    
    private val sizeBuckets = listOf(
        SizeBucket(256, 256, "small"),   // Thumbnails, icons
        SizeBucket(512, 512, "medium"),  // Standard images
        SizeBucket(1024, 1024, "large"), // Full resolution
        SizeBucket(2048, 2048, "xlarge") // Maximum supported
    )
    
    // Nested map: Config -> SizeBucket -> Pool
    private val pools = mutableMapOf<Bitmap.Config, MutableMap<String, GenericObjectPool<Bitmap>>>()
    private val lock = Mutex()

    /**
     * Bitmap pool configuration.
     */
    data class BitmapPoolConfig(
        val maxPoolSize: Int = 5,
        val defaultWidth: Int = 512,
        val defaultHeight: Int = 512,
        val defaultConfig: Bitmap.Config = Bitmap.Config.ARGB_8888
    )

    private val bitmapConfig = BitmapPoolConfig()

    /**
     * Get the appropriate size bucket for given dimensions.
     */
    private fun getSizeBucket(width: Int, height: Int): SizeBucket {
        val maxDim = maxOf(width, height)
        return sizeBuckets.find { maxDim <= it.maxWidth } ?: sizeBuckets.last()
    }

    /**
     * Get or create a pool for specific bitmap configuration and size bucket.
     */
    private suspend fun getPool(config: Bitmap.Config, bucket: SizeBucket): GenericObjectPool<Bitmap> =
        lock.withLock {
            val configPools = pools.getOrPut(config) { mutableMapOf() }
            configPools.getOrPut(bucket.label) {
                GenericObjectPool(
                    config = PoolConfig(maxSize = bitmapConfig.maxPoolSize),
                    factory = {
                        Bitmap.createBitmap(
                            bucket.maxWidth,
                            bucket.maxHeight,
                            config
                        )
                    },
                    reset = { bitmap ->
                        // Clear bitmap pixels and return true to indicate successful reset
                        if (!bitmap.isRecycled) {
                            bitmap.eraseColor(0)
                            true
                        } else {
                            false // Don't return recycled bitmaps to pool
                        }
                    }
                )
            }
        }

    /**
     * Acquire a bitmap from the pool.
     * Uses size bucketing to minimize memory waste.
     *
     * @param width Desired width
     * @param height Desired height
     * @param config Bitmap configuration
     * @return Bitmap from pool or newly created
     */
    suspend fun acquire(
        width: Int = bitmapConfig.defaultWidth,
        height: Int = bitmapConfig.defaultHeight,
        config: Bitmap.Config = bitmapConfig.defaultConfig
    ): Bitmap {
        val bucket = getSizeBucket(width, height)
        val pool = getPool(config, bucket)
        val bitmap = pool.acquire()

        // Check if bitmap can be reused (it should always match since we use buckets)
        return if (bitmap.width >= width && bitmap.height >= height && bitmap.config == config && !bitmap.isRecycled) {
            bitmap
        } else {
            // Return to pool and create new (this shouldn't happen with proper bucketing)
            if (!bitmap.isRecycled) {
                pool.release(bitmap)
            }
            Bitmap.createBitmap(width, height, config)
        }
    }

    /**
     * Release a bitmap back to the pool.
     * Uses size bucketing to return bitmap to appropriate pool.
     *
     * @param bitmap Bitmap to release
     */
    suspend fun release(bitmap: Bitmap) {
        if (bitmap.isRecycled) return

        val bucket = getSizeBucket(bitmap.width, bitmap.height)
        val pool = getPool(bitmap.config ?: Bitmap.Config.ARGB_8888, bucket)
        pool.release(bitmap)
    }

    /**
     * Clear all bitmap pools.
     */
    suspend fun clear() {
        lock.withLock {
            pools.values.forEach { configPools ->
                configPools.values.forEach { it.clear() }
                configPools.clear()
            }
            pools.clear()
        }
    }

    /**
     * Get total pooled bitmap count across all size buckets.
     */
    suspend fun getTotalPooledCount(): Int = lock.withLock {
        pools.values.sumOf { configPools ->
            configPools.values.sumOf { it.size() }
        }
    }
}

/**
 * Byte array pool for buffer reuse.
 */
class ByteArrayPool @Inject constructor() {

    private val pools = mutableMapOf<Int, GenericObjectPool<ByteArray>>()
    private val lock = Mutex()

    companion object {
        private const val DEFAULT_MAX_SIZE = 10
        private val STANDARD_SIZES = listOf(1024, 4096, 8192, 16384, 32768, 65536)
    }

    /**
     * Acquire a byte array of at least the requested size.
     *
     * @param minSize Minimum size needed
     * @return Byte array from pool or newly created
     */
    suspend fun acquire(minSize: Int): ByteArray {
        val size = STANDARD_SIZES.find { it >= minSize } ?: minSize
        val pool = getPool(size)
        val array = pool.acquire()

        return if (array.size >= minSize) {
            array
        } else {
            pool.release(array)
            ByteArray(minSize)
        }
    }

    /**
     * Release a byte array back to the pool.
     *
     * @param array Byte array to release
     */
    suspend fun release(array: ByteArray) {
        val size = STANDARD_SIZES.find { it >= array.size } ?: array.size
        val pool = getPool(size)
        pool.release(array)
    }

    private suspend fun getPool(size: Int): GenericObjectPool<ByteArray> =
        lock.withLock {
            pools.getOrPut(size) {
                GenericObjectPool(
                    config = PoolConfig(maxSize = DEFAULT_MAX_SIZE),
                    factory = { ByteArray(size) },
                    reset = { array ->
                        // Clear array and return true to indicate successful reset
                        array.fill(0)
                        true
                    }
                )
            }
        }

    /**
     * Clear all pools.
     */
    suspend fun clear() {
        lock.withLock {
            pools.values.forEach { it.clear() }
            pools.clear()
        }
    }
}

/**
 * String builder pool for efficient string construction.
 */
class StringBuilderPool @Inject constructor() {

    private val pool = GenericObjectPool(
        config = PoolConfig(maxSize = 20),
        factory = { StringBuilder(256) },
        reset = {
            it.clear()
            true // Return true to indicate successful reset
        }
    )

    /**
     * Acquire a StringBuilder from the pool.
     */
    suspend fun acquire(): StringBuilder = pool.acquire()

    /**
     * Release a StringBuilder back to the pool.
     */
    suspend fun release(builder: StringBuilder) = pool.release(builder)

    /**
     * Execute block with a pooled StringBuilder.
     */
    suspend inline fun <R> use(block: (StringBuilder) -> R): R {
        val builder = acquire()
        try {
            return block(builder)
        } finally {
            release(builder)
        }
    }
}

/**
 * Memory pool manager that coordinates all object pools.
 */
@Singleton
class MemoryPoolManager @Inject constructor(
    val bitmapPool: BitmapPool,
    val byteArrayPool: ByteArrayPool,
    val stringBuilderPool: StringBuilderPool
) {
    /**
     * Clear all pools.
     */
    suspend fun clearAllPools() {
        bitmapPool.clear()
        byteArrayPool.clear()
    }

    /**
     * Get pool statistics.
     */
    suspend fun getStats(): PoolStats {
        return PoolStats(
            bitmapPoolSize = bitmapPool.getTotalPooledCount()
        )
    }

    data class PoolStats(
        val bitmapPoolSize: Int
    )
}
