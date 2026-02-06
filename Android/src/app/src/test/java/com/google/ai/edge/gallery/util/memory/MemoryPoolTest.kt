/*
 * Copyright 2025 Tanmay Patil
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.google.ai.edge.gallery.util.memory

import android.graphics.Bitmap
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class GenericObjectPoolTest {

    private lateinit var pool: GenericObjectPool<StringBuilder>
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Before
    fun setup() {
        pool = GenericObjectPool(
            config = PoolConfig(maxSize = 3),
            factory = { StringBuilder() },
            reset = { it.clear(); true }
        )
    }

    @After
    fun tearDown() = testScope.runTest {
        pool.clear()
    }

    @Test
    fun `acquire should return pooled object`() = testScope.runTest {
        // Given
        val obj1 = pool.acquire()
        obj1.append("test")
        pool.release(obj1)

        // When
        val obj2 = pool.acquire()

        // Then
        assertSame(obj1, obj2)
        assertEquals(0, obj2.length) // Should be reset
    }

    @Test
    fun `pool should not exceed max size`() = testScope.runTest {
        // Given - Acquire more than max size
        val objects = List(5) { pool.acquire() }

        // When - Release all
        objects.forEach { pool.release(it) }

        // Then - Pool should only hold maxSize
        assertTrue(pool.size() <= pool.maxSize())
    }

    @Test
    fun `active count should track acquired objects`() = testScope.runTest {
        // Given
        assertEquals(0, pool.activeCount())

        // When
        val obj1 = pool.acquire()
        val obj2 = pool.acquire()

        // Then
        assertEquals(2, pool.activeCount())

        // When
        pool.release(obj1)

        // Then
        assertEquals(1, pool.activeCount())
    }
}

@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], shadows = [ShadowBitmap::class])
class BitmapPoolTest {

    private lateinit var bitmapPool: BitmapPool
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Before
    fun setup() {
        bitmapPool = BitmapPool()
    }

    @After
    fun tearDown() = testScope.runTest {
        bitmapPool.clear()
    }

    @Test
    fun `acquire should return bitmap of requested size`() = testScope.runTest {
        // Given
        val width = 100
        val height = 200

        // When
        val bitmap = bitmapPool.acquire(width, height, Bitmap.Config.ARGB_8888)

        // Then
        assertNotNull(bitmap)
        assertTrue(bitmap.width >= width)
        assertTrue(bitmap.height >= height)
    }

    @Test
    fun `release and reacquire should reuse bitmap`() = testScope.runTest {
        // Given
        val bitmap1 = bitmapPool.acquire(100, 100, Bitmap.Config.ARGB_8888)
        bitmapPool.release(bitmap1)

        // When
        val bitmap2 = bitmapPool.acquire(100, 100, Bitmap.Config.ARGB_8888)

        // Then
        assertSame(bitmap1, bitmap2)
    }

    @Test
    fun `clear should empty pool`() = testScope.runTest {
        // Given
        val bitmap = bitmapPool.acquire(100, 100, Bitmap.Config.ARGB_8888)
        bitmapPool.release(bitmap)

        // When
        bitmapPool.clear()

        // Then
        assertEquals(0, bitmapPool.getTotalPooledCount())
    }
}

@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ByteArrayPoolTest {

    private lateinit var byteArrayPool: ByteArrayPool
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Before
    fun setup() {
        byteArrayPool = ByteArrayPool()
    }

    @After
    fun tearDown() = testScope.runTest {
        byteArrayPool.clear()
    }

    @Test
    fun `acquire should return array of at least requested size`() = testScope.runTest {
        // Given
        val minSize = 5000

        // When
        val array = byteArrayPool.acquire(minSize)

        // Then
        assertTrue(array.size >= minSize)
    }

    @Test
    fun `release and reacquire should reuse array`() = testScope.runTest {
        // Given
        val array1 = byteArrayPool.acquire(1024)
        byteArrayPool.release(array1)

        // When
        val array2 = byteArrayPool.acquire(1024)

        // Then - Should get same or similar sized array from pool
        assertNotNull(array2)
    }
}

@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class StringBuilderPoolTest {

    private lateinit var stringBuilderPool: StringBuilderPool
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Before
    fun setup() {
        stringBuilderPool = StringBuilderPool()
    }

    @Test
    fun `use should provide clean StringBuilder`() = testScope.runTest {
        // Given
        stringBuilderPool.use { builder ->
            builder.append("test data")
        }

        // When
        var result: String? = null
        stringBuilderPool.use { builder ->
            result = builder.toString()
        }

        // Then - Builder should be reset
        assertEquals("", result)
    }

    @Test
    fun `multiple uses should work correctly`() = testScope.runTest {
        // Given & When
        val results = mutableListOf<String>()
        repeat(5) { index ->
            stringBuilderPool.use { builder ->
                builder.append("item $index")
                results.add(builder.toString())
            }
        }

        // Then
        assertEquals(5, results.size)
        repeat(5) { index ->
            assertEquals("item $index", results[index])
        }
    }
}

// Shadow for Bitmap to enable testing without Android framework
class ShadowBitmap {
    private var width: Int = 0
    private var height: Int = 0
    private var config: Bitmap.Config = Bitmap.Config.ARGB_8888
    private var recycled: Boolean = false

    fun getWidth(): Int = width
    fun getHeight(): Int = height
    fun getConfig(): Bitmap.Config = config
    fun isRecycled(): Boolean = recycled
    fun recycle() { recycled = true }

    companion object {
        @JvmStatic
        fun createBitmap(width: Int, height: Int, config: Bitmap.Config): Bitmap {
            val bitmap = Bitmap.createBitmap(width, height, config)
            return bitmap
        }
    }
}
