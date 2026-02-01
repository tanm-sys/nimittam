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

package com.google.ai.edge.gallery.data.cache

import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache
import androidx.collection.LruCache as CollectionLruCache
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cache entry metadata for tracking staleness and usage.
 *
 * @property timestamp When the entry was cached
 * @property accessCount Number of times accessed
 * @property lastAccessed Last access timestamp
 * @property ttlMillis Time-to-live in milliseconds
 */
data class CacheMetadata(
    val timestamp: Long = System.currentTimeMillis(),
    val accessCount: Int = 0,
    val lastAccessed: Long = System.currentTimeMillis(),
    val ttlMillis: Long = DEFAULT_TTL_MILLIS
) : Serializable {
    companion object {
        const val DEFAULT_TTL_MILLIS = 24 * 60 * 60 * 1000L // 24 hours
        const val STALE_TTL_MILLIS = 5 * 60 * 1000L // 5 minutes for stale-while-revalidate
    }

    /**
     * Check if the cache entry is expired based on TTL.
     */
    fun isExpired(): Boolean =
        System.currentTimeMillis() - timestamp > ttlMillis

    /**
     * Check if the cache entry is stale (older than stale threshold but not expired).
     */
    fun isStale(): Boolean {
        val age = System.currentTimeMillis() - timestamp
        return age > STALE_TTL_MILLIS && age <= ttlMillis
    }

    /**
     * Create a new metadata with incremented access count.
     */
    fun withAccess(): CacheMetadata = copy(
        accessCount = accessCount + 1,
        lastAccessed = System.currentTimeMillis()
    )
}

/**
 * Wrapper for cached values with metadata.
 */
data class CacheEntry<T>(
    val value: T,
    val metadata: CacheMetadata
) : Serializable

/**
 * Resource wrapper for cache operations with source information.
 */
sealed class CachedResource<out T> {
    abstract val data: T?

    data class Success<T>(
        override val data: T,
        val isFromCache: Boolean,
        val isStale: Boolean = false
    ) : CachedResource<T>()

    data class Error<T>(
        val exception: Throwable,
        override val data: T? = null,
        val isFromCache: Boolean = false
    ) : CachedResource<T>()

    data class Loading<T>(override val data: T? = null) : CachedResource<T>()
}

/**
 * L1 (Memory) Cache implementation using LRU eviction policy.
 *
 * Features:
 * - Thread-safe operations
 * - Size-based eviction
 * - Metadata tracking for each entry
 */
class L1MemoryCache<T>(maxSize: Int = DEFAULT_MAX_SIZE) {
    companion object {
        const val DEFAULT_MAX_SIZE = 50 // Default max entries
    }

    private val cache = object : LruCache<String, CacheEntry<T>>(maxSize) {
        override fun entryRemoved(
            evicted: Boolean,
            key: String,
            oldValue: CacheEntry<T>,
            newValue: CacheEntry<T>?
        ) {
            if (evicted) {
                onEntryEvicted?.invoke(key, oldValue)
            }
        }
    }

    private val lock = Mutex()
    var onEntryEvicted: ((String, CacheEntry<T>) -> Unit)? = null

    /**
     * Get entry from L1 cache.
     */
    suspend fun get(key: String): CacheEntry<T>? = lock.withLock {
        cache.get(key)?.let { entry ->
            // Update access metadata
            val updatedEntry = entry.copy(metadata = entry.metadata.withAccess())
            cache.put(key, updatedEntry)
            updatedEntry
        }
    }

    /**
     * Put entry into L1 cache.
     */
    suspend fun put(key: String, value: T, ttlMillis: Long = CacheMetadata.DEFAULT_TTL_MILLIS) =
        lock.withLock {
            val entry = CacheEntry(value, CacheMetadata(ttlMillis = ttlMillis))
            cache.put(key, entry)
        }

    /**
     * Remove entry from L1 cache.
     */
    suspend fun remove(key: String): CacheEntry<T>? = lock.withLock {
        cache.remove(key)
    }

    /**
     * Clear all entries from L1 cache.
     */
    suspend fun clear() = lock.withLock {
        cache.evictAll()
    }

    /**
     * Get current cache size.
     */
    fun size(): Int = cache.size()

    /**
     * Get max cache size.
     */
    fun maxSize(): Int = cache.maxSize()

    /**
     * Get all keys in cache.
     */
    fun keys(): Set<String> = cache.snapshot().keys
}

/**
 * Specialized L1 cache for bitmaps with size-based eviction.
 */
class BitmapMemoryCache(maxMemoryMB: Int = DEFAULT_MAX_MEMORY_MB) {
    companion object {
        const val DEFAULT_MAX_MEMORY_MB = 64 // 64MB default
    }

    private val maxMemoryKB = maxMemoryMB * 1024

    private val cache = object : LruCache<String, Bitmap>(maxMemoryKB) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount / 1024
        }

        override fun entryRemoved(
            evicted: Boolean,
            key: String,
            oldValue: Bitmap,
            newValue: Bitmap?
        ) {
            // Bitmap is recycled by the system, but we can add custom cleanup here
            if (evicted && !oldValue.isRecycled) {
                // Optionally recycle if not used elsewhere
            }
        }
    }

    private val metadataMap = ConcurrentHashMap<String, CacheMetadata>()
    private val lock = Mutex()

    /**
     * Get bitmap from cache.
     */
    suspend fun get(key: String): Bitmap? = lock.withLock {
        cache.get(key)?.also {
            metadataMap[key] = metadataMap[key]?.withAccess() ?: CacheMetadata()
        }
    }

    /**
     * Put bitmap into cache.
     */
    suspend fun put(key: String, bitmap: Bitmap, ttlMillis: Long = CacheMetadata.DEFAULT_TTL_MILLIS) =
        lock.withLock {
            cache.put(key, bitmap)
            metadataMap[key] = CacheMetadata(ttlMillis = ttlMillis)
        }

    /**
     * Remove bitmap from cache.
     */
    suspend fun remove(key: String): Bitmap? = lock.withLock {
        metadataMap.remove(key)
        cache.remove(key)
    }

    /**
     * Clear all bitmaps from cache.
     */
    suspend fun clear() = lock.withLock {
        metadataMap.clear()
        cache.evictAll()
    }

    /**
     * Get cache statistics.
     */
    fun getStats(): CacheStats = CacheStats(
        size = cache.size(),
        maxSize = cache.maxSize(),
        hitCount = cache.hitCount(),
        missCount = cache.missCount(),
        putCount = cache.putCount(),
        evictionCount = cache.evictionCount()
    )
}

/**
 * Cache statistics data class.
 */
data class CacheStats(
    val size: Int,
    val maxSize: Int,
    val hitCount: Int,
    val missCount: Int,
    val putCount: Int,
    val evictionCount: Int
) {
    val hitRate: Float
        get() = if (hitCount + missCount > 0) {
            hitCount.toFloat() / (hitCount + missCount)
        } else 0f
}

/**
 * L2 (Disk) Cache implementation for persistent storage.
 *
 * Features:
 * - File-based storage with serialization
 * - Automatic cleanup of expired entries
 * - Size-limited with LRU eviction
 */
class L2DiskCache(
    private val context: Context,
    private val cacheDirName: String = "l2_cache",
    private val maxSizeMB: Int = DEFAULT_MAX_SIZE_MB
) {
    companion object {
        const val DEFAULT_MAX_SIZE_MB = 100 // 100MB default
        const val METADATA_SUFFIX = ".meta"
    }

    private val cacheDir: File by lazy {
        File(context.cacheDir, cacheDirName).apply {
            if (!exists()) mkdirs()
        }
    }

    private val lock = Mutex()

    /**
     * Get entry from L2 cache.
     */
    @Suppress("UNCHECKED_CAST")
    suspend fun <T : Serializable> get(key: String): CacheEntry<T>? = lock.withLock {
        withContext(Dispatchers.IO) {
            val file = getCacheFile(key)
            val metaFile = getMetadataFile(key)

            if (!file.exists() || !metaFile.exists()) {
                return@withContext null
            }

            try {
                // Read metadata first to check expiration
                val metadata = ObjectInputStream(FileInputStream(metaFile)).use {
                    it.readObject() as CacheMetadata
                }

                if (metadata.isExpired()) {
                    // Delete expired entry
                    file.delete()
                    metaFile.delete()
                    return@withContext null
                }

                // Read value
                val value = ObjectInputStream(FileInputStream(file)).use {
                    it.readObject() as T
                }

                // Update access metadata
                val updatedMetadata = metadata.withAccess()
                ObjectOutputStream(FileOutputStream(metaFile)).use {
                    it.writeObject(updatedMetadata)
                }

                CacheEntry(value, updatedMetadata)
            } catch (e: Exception) {
                // Corrupted cache entry, delete it
                file.delete()
                metaFile.delete()
                null
            }
        }
    }

    /**
     * Put entry into L2 cache.
     */
    suspend fun <T : Serializable> put(
        key: String,
        value: T,
        ttlMillis: Long = CacheMetadata.DEFAULT_TTL_MILLIS
    ) = lock.withLock {
        withContext(Dispatchers.IO) {
            // Check if we need to evict entries
            ensureSpace()

            val file = getCacheFile(key)
            val metaFile = getMetadataFile(key)

            try {
                // Write value
                ObjectOutputStream(FileOutputStream(file)).use {
                    it.writeObject(value)
                }

                // Write metadata
                val metadata = CacheMetadata(ttlMillis = ttlMillis)
                ObjectOutputStream(FileOutputStream(metaFile)).use {
                    it.writeObject(metadata)
                }
            } catch (e: Exception) {
                // Clean up on failure
                file.delete()
                metaFile.delete()
                throw e
            }
        }
    }

    /**
     * Remove entry from L2 cache.
     */
    suspend fun remove(key: String): Boolean = lock.withLock {
        withContext(Dispatchers.IO) {
            val file = getCacheFile(key)
            val metaFile = getMetadataFile(key)
            file.delete() || metaFile.delete()
        }
    }

    /**
     * Clear all entries from L2 cache.
     */
    suspend fun clear() = lock.withLock {
        withContext(Dispatchers.IO) {
            cacheDir.listFiles()?.forEach { it.delete() }
        }
    }

    /**
     * Get current cache size in bytes.
     */
    suspend fun getSize(): Long = lock.withLock {
        withContext(Dispatchers.IO) {
            cacheDir.listFiles()?.sumOf { it.length() } ?: 0L
        }
    }

    /**
     * Clean up expired entries.
     */
    suspend fun cleanup() = lock.withLock {
        withContext(Dispatchers.IO) {
            cacheDir.listFiles { _, name -> name.endsWith(METADATA_SUFFIX) }
                ?.forEach { metaFile ->
                    try {
                        val metadata = ObjectInputStream(FileInputStream(metaFile)).use {
                            it.readObject() as CacheMetadata
                        }

                        if (metadata.isExpired()) {
                            val key = metaFile.name.removeSuffix(METADATA_SUFFIX)
                            getCacheFile(key).delete()
                            metaFile.delete()
                        }
                    } catch (e: Exception) {
                        metaFile.delete()
                    }
                }
        }
    }

    private fun getCacheFile(key: String): File {
        return File(cacheDir, hashKey(key))
    }

    private fun getMetadataFile(key: String): File {
        return File(cacheDir, hashKey(key) + METADATA_SUFFIX)
    }

    private fun hashKey(key: String): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(key.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    /**
     * Ensure we have space in the cache by evicting oldest entries.
     * OPTIMIZATION: Added yield() calls for cooperative multitasking
     * to prevent blocking the thread during large cleanup operations.
     */
    private suspend fun ensureSpace() {
        val maxSizeBytes = maxSizeMB * 1024L * 1024L
        var currentSize = getSize()

        if (currentSize < maxSizeBytes) return

        // Evict oldest entries until we have space
        val entries = cacheDir.listFiles { _, name -> name.endsWith(METADATA_SUFFIX) }
            ?.mapNotNull { metaFile ->
                try {
                    val metadata = ObjectInputStream(FileInputStream(metaFile)).use {
                        it.readObject() as CacheMetadata
                    }
                    metaFile.name.removeSuffix(METADATA_SUFFIX) to metadata
                } catch (e: Exception) {
                    metaFile.delete()
                    null
                }
            }
            ?.sortedBy { it.second.lastAccessed }
            ?: return

        for ((key, _) in entries) {
            if (currentSize < maxSizeBytes * 0.8) break // Target 80% capacity

            val file = getCacheFile(key)
            val metaFile = getMetadataFile(key)
            currentSize -= file.length() + metaFile.length()
            file.delete()
            metaFile.delete()
            
            // OPTIMIZATION: Yield periodically to allow other coroutines to run
            // This prevents blocking the thread during large cleanup operations
            if (entries.indexOfFirst { it.first == key } % 10 == 0) {
                kotlinx.coroutines.yield()
            }
        }
    }
}

/**
 * Predictive prefetching manager based on user behavior analysis.
 */
class PredictivePrefetcher @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * User behavior pattern for prediction.
     */
    data class BehaviorPattern(
        val frequentKeys: Set<String>,
        val accessSequence: List<String>,
        val timeBasedPatterns: Map<Int, Set<String>> // Hour of day -> keys
    )

    private val accessHistory = mutableListOf<AccessRecord>()
    private val lock = Mutex()

    data class AccessRecord(
        val key: String,
        val timestamp: Long,
        val hourOfDay: Int
    )

    /**
     * Record a cache access for pattern analysis.
     */
    suspend fun recordAccess(key: String) = lock.withLock {
        val now = System.currentTimeMillis()
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        accessHistory.add(AccessRecord(key, now, hour))

        // Keep only last 1000 records
        if (accessHistory.size > 1000) {
            accessHistory.removeAt(0)
        }
    }

    /**
     * Predict keys that are likely to be accessed next.
     */
    suspend fun predictNextKeys(currentKey: String?, count: Int = 3): List<String> = lock.withLock {
        val pattern = analyzePattern()

        // Simple prediction based on sequence patterns
        val predictions = mutableListOf<String>()

        // Add frequently accessed keys
        predictions.addAll(pattern.frequentKeys.take(count))

        // Add time-based predictions
        val currentHour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        pattern.timeBasedPatterns[currentHour]?.let { keys ->
            predictions.addAll(keys.take(count - predictions.size))
        }

        predictions.distinct().take(count)
    }

    /**
     * Analyze current behavior pattern.
     */
    private fun analyzePattern(): BehaviorPattern {
        val frequency = accessHistory.groupingBy { it.key }.eachCount()
        val frequentKeys = frequency.entries
            .sortedByDescending { it.value }
            .take(10)
            .map { it.key }
            .toSet()

        val timePatterns = accessHistory.groupBy { it.hourOfDay }
            .mapValues { entry -> entry.value.map { it.key }.toSet() }

        return BehaviorPattern(
            frequentKeys = frequentKeys,
            accessSequence = accessHistory.map { it.key },
            timeBasedPatterns = timePatterns
        )
    }

    /**
     * Schedule prefetch for predicted keys.
     */
    fun <T> schedulePrefetch(
        keys: List<String>,
        fetcher: suspend (String) -> T,
        onPrefetched: suspend (String, T) -> Unit
    ) {
        scope.launch {
            keys.forEach { key ->
                try {
                    val value = fetcher(key)
                    onPrefetched(key, value)
                } catch (e: Exception) {
                    // Prefetch failure is non-critical
                }
            }
        }
    }
}

/**
 * Main Cache Manager implementing multi-level caching with stale-while-revalidate pattern.
 *
 * Features:
 * - L1 (Memory) and L2 (Disk) caching
 * - Stale-while-revalidate pattern
 * - Predictive prefetching
 * - Cache normalization
 * - Thread-safe operations
 */
@Singleton
class CacheManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // L1 Memory Cache for general objects
    private val l1Cache = L1MemoryCache<Any>(maxSize = 100)

    // Specialized bitmap cache
    private val bitmapCache = BitmapMemoryCache(maxMemoryMB = 64)

    // L2 Disk Cache
    private val l2Cache = L2DiskCache(context, maxSizeMB = 100)

    // Predictive prefetcher
    private val prefetcher = PredictivePrefetcher(context)

    // Cache statistics
    private val _stats = MutableStateFlow(CacheManagerStats())
    val stats: StateFlow<CacheManagerStats> = _stats.asStateFlow()

    init {
        // Periodic cleanup job
        scope.launch {
            while (true) {
                kotlinx.coroutines.delay(60 * 60 * 1000L) // Every hour
                l2Cache.cleanup()
            }
        }
    }

    /**
     * Cache manager statistics.
     */
    data class CacheManagerStats(
        val l1Size: Int = 0,
        val l1MaxSize: Int = 0,
        val bitmapCacheStats: CacheStats = CacheStats(0, 0, 0, 0, 0, 0),
        val l2SizeBytes: Long = 0L,
        val totalHits: Long = 0L,
        val totalMisses: Long = 0L
    ) {
        val hitRate: Float
            get() = if (totalHits + totalMisses > 0) {
                totalHits.toFloat() / (totalHits + totalMisses)
            } else 0f
    }

    /**
     * Get value from cache (L1 -> L2).
     */
    @Suppress("UNCHECKED_CAST")
    suspend fun <T : Serializable> get(key: String): T? {
        // Try L1 first
        l1Cache.get(key)?.let { entry ->
            if (!entry.metadata.isExpired()) {
                updateStats { copy(totalHits = totalHits + 1) }
                return entry.value as T
            }
        }

        // Try L2
        l2Cache.get<T>(key)?.let { entry ->
            if (!entry.metadata.isExpired()) {
                // Promote to L1
                l1Cache.put(key, entry.value, entry.metadata.ttlMillis)
                updateStats { copy(totalHits = totalHits + 1) }
                return entry.value
            }
        }

        updateStats { copy(totalMisses = totalMisses + 1) }
        return null
    }

    /**
     * Put value into cache (L1 and L2).
     */
    suspend fun <T : Serializable> put(
        key: String,
        value: T,
        ttlMillis: Long = CacheMetadata.DEFAULT_TTL_MILLIS
    ) {
        l1Cache.put(key, value, ttlMillis)
        l2Cache.put(key, value, ttlMillis)
        updateStats()
    }

    /**
     * Remove value from all cache levels.
     */
    suspend fun remove(key: String) {
        l1Cache.remove(key)
        l2Cache.remove(key)
        updateStats()
    }

    /**
     * Clear all caches.
     */
    suspend fun clearAll() {
        l1Cache.clear()
        bitmapCache.clear()
        l2Cache.clear()
        updateStats()
    }

    /**
     * Get bitmap from cache.
     */
    suspend fun getBitmap(key: String): Bitmap? {
        return bitmapCache.get(key)
    }

    /**
     * Put bitmap into cache.
     */
    suspend fun putBitmap(key: String, bitmap: Bitmap, ttlMillis: Long = CacheMetadata.DEFAULT_TTL_MILLIS) {
        bitmapCache.put(key, bitmap, ttlMillis)
        updateStats()
    }

    /**
     * Stale-while-revalidate pattern implementation.
     *
     * Returns cached data immediately (even if stale), then refreshes in background.
     *
     * @param key Cache key
     * @param fetcher Suspend function to fetch fresh data
     * @param forceRefresh Force a refresh even if cache is valid
     * @return Flow of cached resources
     */
    fun <T : Serializable> getWithSwr(
        key: String,
        fetcher: suspend () -> T,
        forceRefresh: Boolean = false
    ): Flow<CachedResource<T>> = flow {
        emit(CachedResource.Loading())

        // Try to get cached value
        val cachedEntry: CacheEntry<T>? = l1Cache.get(key) as? CacheEntry<T>
            ?: l2Cache.get<T>(key)

        val cachedValue = cachedEntry?.value
        val isStale = cachedEntry?.metadata?.isStale() == true

        // Emit cached value immediately if available and not forcing refresh
        if (cachedValue != null && !forceRefresh) {
            emit(CachedResource.Success(cachedValue, isFromCache = true, isStale = isStale))

            // If not stale and not forced, we're done
            if (!isStale) {
                return@flow
            }
        }

        // Fetch fresh data
        try {
            val freshData = fetcher()

            // Update caches
            put(key, freshData)
            prefetcher.recordAccess(key)

            emit(CachedResource.Success(freshData, isFromCache = false, isStale = false))
        } catch (e: Exception) {
            // If we have cached data, emit error with cached data
            if (cachedValue != null) {
                emit(CachedResource.Error(e, cachedValue, isFromCache = true))
            } else {
                emit(CachedResource.Error(e))
            }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Prefetch data for predicted keys.
     */
    suspend fun <T : Serializable> prefetch(
        keys: List<String>,
        fetcher: suspend (String) -> T
    ) {
        prefetcher.schedulePrefetch(keys, fetcher) { key, value ->
            put(key, value)
        }
    }

    /**
     * Predict and prefetch likely next accesses.
     */
    suspend fun <T : Serializable> predictivePrefetch(
        currentKey: String?,
        fetcher: suspend (String) -> T
    ) {
        val predictedKeys = prefetcher.predictNextKeys(currentKey)
        prefetch(predictedKeys, fetcher)
    }

    /**
     * Normalize cache key for consistent lookup.
     */
    fun normalizeKey(vararg parts: String): String {
        return parts.joinToString(separator = ":")
    }

    /**
     * Get current cache statistics.
     */
    suspend fun getStats(): CacheManagerStats {
        updateStats()
        return _stats.value
    }

    private suspend fun updateStats() {
        _stats.value = CacheManagerStats(
            l1Size = l1Cache.size(),
            l1MaxSize = l1Cache.maxSize(),
            bitmapCacheStats = bitmapCache.getStats(),
            l2SizeBytes = l2Cache.getSize(),
            totalHits = _stats.value.totalHits,
            totalMisses = _stats.value.totalMisses
        )
    }

    private fun updateStats(transform: CacheManagerStats.() -> CacheManagerStats) {
        _stats.value = _stats.value.transform()
    }
}

/**
 * Extension functions for easier cache usage.
 */

/**
 * Cache key builder for different data types.
 */
object CacheKeys {
    fun conversation(id: String): String = "conversation:$id"
    fun message(id: String): String = "message:$id"
    fun messagesForConversation(conversationId: String): String = "messages:$conversationId"
    fun thumbnail(mediaId: String): String = "thumb:$mediaId"
    fun settings(key: String): String = "settings:$key"
    fun modelMetadata(modelId: String): String = "model:$modelId"
}

/**
 * Execute block with caching.
 */
suspend inline fun <reified T : Serializable> CacheManager.withCache(
    key: String,
    ttlMillis: Long = CacheMetadata.DEFAULT_TTL_MILLIS,
    crossinline fetcher: suspend () -> T
): T {
    return get<T>(key) ?: fetcher().also { put(key, it, ttlMillis) }
}
