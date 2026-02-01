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
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.Serializable

@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CacheManagerTest {

    private lateinit var context: Context
    private lateinit var cacheManager: CacheManager
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    // Test data class
    data class TestData(val id: String, val value: Int) : Serializable

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        cacheManager = CacheManager(context)
    }

    @After
    fun tearDown() = testScope.runTest {
        cacheManager.clearAll()
    }

    @Test
    fun `put and get should store and retrieve value`() = testScope.runTest {
        // Given
        val key = "test_key"
        val value = TestData("1", 100)

        // When
        cacheManager.put(key, value)
        val result = cacheManager.get<TestData>(key)

        // Then
        assertNotNull(result)
        assertEquals(value, result)
    }

    @Test
    fun `get should return null for non-existent key`() = testScope.runTest {
        // Given
        val key = "non_existent_key"

        // When
        val result = cacheManager.get<TestData>(key)

        // Then
        assertNull(result)
    }

    @Test
    fun `remove should delete value from cache`() = testScope.runTest {
        // Given
        val key = "test_key"
        val value = TestData("1", 100)
        cacheManager.put(key, value)

        // When
        cacheManager.remove(key)
        val result = cacheManager.get<TestData>(key)

        // Then
        assertNull(result)
    }

    @Test
    fun `clearAll should remove all values`() = testScope.runTest {
        // Given
        cacheManager.put("key1", TestData("1", 100))
        cacheManager.put("key2", TestData("2", 200))
        cacheManager.put("key3", TestData("3", 300))

        // When
        cacheManager.clearAll()

        // Then
        assertNull(cacheManager.get<TestData>("key1"))
        assertNull(cacheManager.get<TestData>("key2"))
        assertNull(cacheManager.get<TestData>("key3"))
    }

    @Test
    fun `getWithSwr should emit cached value first`() = testScope.runTest {
        // Given
        val key = "swr_key"
        val cachedValue = TestData("1", 100)
        cacheManager.put(key, cachedValue)

        // When
        val result = cacheManager.getWithSwr(
            key = key,
            fetcher = { TestData("2", 200) }
        ).first()

        // Then
        assertTrue(result is CachedResource.Success)
        assertEquals(cachedValue, (result as CachedResource.Success).data)
        assertTrue(result.isFromCache)
    }

    @Test
    fun `normalizeKey should create consistent keys`() {
        // Given
        val parts = listOf("part1", "part2", "part3")

        // When
        val key1 = cacheManager.normalizeKey(*parts.toTypedArray())
        val key2 = cacheManager.normalizeKey(*parts.toTypedArray())

        // Then
        assertEquals(key1, key2)
        assertEquals("part1:part2:part3", key1)
    }

    @Test
    fun `CacheKeys should generate correct keys`() {
        // Test conversation key
        assertEquals("conversation:123", CacheKeys.conversation("123"))

        // Test message key
        assertEquals("message:456", CacheKeys.message("456"))

        // Test thumbnail key
        assertEquals("thumb:789", CacheKeys.thumbnail("789"))
    }

    @Test
    fun `withCache extension should use cache on second call`() = testScope.runTest {
        // Given
        val key = "extension_key"
        var fetchCount = 0
        val fetcher: suspend () -> TestData = {
            fetchCount++
            TestData("1", fetchCount)
        }

        // When - First call should fetch
        val result1 = cacheManager.withCache(key, fetcher = fetcher)

        // When - Second call should use cache
        val result2 = cacheManager.withCache(key, fetcher = fetcher)

        // Then
        assertEquals(1, fetchCount)
        assertEquals(result1, result2)
    }
}

@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class L1MemoryCacheTest {

    private lateinit var l1Cache: L1MemoryCache<String>
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Before
    fun setup() {
        l1Cache = L1MemoryCache(maxSize = 5)
    }

    @Test
    fun `put and get should work correctly`() = testScope.runTest {
        // Given
        val key = "key"
        val value = "value"

        // When
        l1Cache.put(key, value)
        val result = l1Cache.get(key)

        // Then
        assertNotNull(result)
        assertEquals(value, result?.value)
    }

    @Test
    fun `cache should evict oldest entries when full`() = testScope.runTest {
        // Given - Fill cache beyond capacity
        repeat(10) { index ->
            l1Cache.put("key$index", "value$index")
        }

        // Then - Only maxSize entries should remain
        assertTrue(l1Cache.size() <= 5)
    }

    @Test
    fun `access count should increment on get`() = testScope.runTest {
        // Given
        l1Cache.put("key", "value")

        // When
        l1Cache.get("key")
        l1Cache.get("key")
        val result = l1Cache.get("key")

        // Then
        assertEquals(3, result?.metadata?.accessCount)
    }
}

@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CacheMetadataTest {

    @Test
    fun `isExpired should return true when TTL exceeded`() {
        // Given
        val metadata = CacheMetadata(
            timestamp = System.currentTimeMillis() - 100000, // 100 seconds ago
            ttlMillis = 50000 // 50 second TTL
        )

        // Then
        assertTrue(metadata.isExpired())
    }

    @Test
    fun `isStale should return true when stale threshold exceeded`() {
        // Given - 10 minutes old with 5 minute stale threshold
        val metadata = CacheMetadata(
            timestamp = System.currentTimeMillis() - 600000,
            ttlMillis = 3600000 // 1 hour TTL
        )

        // Then
        assertTrue(metadata.isStale())
    }

    @Test
    fun `withAccess should increment access count`() {
        // Given
        val metadata = CacheMetadata(accessCount = 5)

        // When
        val updated = metadata.withAccess()

        // Then
        assertEquals(6, updated.accessCount)
    }
}
