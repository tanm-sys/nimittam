/*
 * Copyright 2025 Tanmay Patil
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.google.ai.edge.gallery.di

import android.content.Context
import com.google.ai.edge.gallery.data.cache.CacheManager
import com.google.ai.edge.gallery.performance.PerformanceMonitor
import com.google.ai.edge.gallery.performance.StartupTracer
import com.google.ai.edge.gallery.util.memory.AdaptiveMemoryManager
import com.google.ai.edge.gallery.util.memory.BitmapPool
import com.google.ai.edge.gallery.util.memory.ByteArrayPool
import com.google.ai.edge.gallery.util.memory.MemoryLeakDetector
import com.google.ai.edge.gallery.util.memory.MemoryPoolManager
import com.google.ai.edge.gallery.util.memory.ReferenceCacheManager
import com.google.ai.edge.gallery.util.memory.SoftReferenceCache
import com.google.ai.edge.gallery.util.memory.StringBuilderPool
import com.google.ai.edge.gallery.util.memory.WeakReferenceCache
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for providing optimization-related dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
object OptimizationModule {

    //region Cache Management

    @Provides
    @Singleton
    fun provideCacheManager(
        @ApplicationContext context: Context
    ): CacheManager {
        return CacheManager(context)
    }

    //endregion

    //region Memory Management

    @Provides
    @Singleton
    fun provideBitmapPool(): BitmapPool {
        return BitmapPool()
    }

    @Provides
    @Singleton
    fun provideByteArrayPool(): ByteArrayPool {
        return ByteArrayPool()
    }

    @Provides
    @Singleton
    fun provideStringBuilderPool(): StringBuilderPool {
        return StringBuilderPool()
    }

    @Provides
    @Singleton
    fun provideMemoryPoolManager(
        bitmapPool: BitmapPool,
        byteArrayPool: ByteArrayPool,
        stringBuilderPool: StringBuilderPool
    ): MemoryPoolManager {
        return MemoryPoolManager(bitmapPool, byteArrayPool, stringBuilderPool)
    }

    @Provides
    @Singleton
    fun provideWeakReferenceCache(): WeakReferenceCache<Any> {
        return WeakReferenceCache()
    }

    @Provides
    @Singleton
    fun provideSoftReferenceCache(): SoftReferenceCache<Any> {
        return SoftReferenceCache()
    }

    @Provides
    @Singleton
    fun provideReferenceCacheManager(
        weakCache: WeakReferenceCache<Any>,
        softCache: SoftReferenceCache<Any>
    ): ReferenceCacheManager {
        return ReferenceCacheManager(weakCache, softCache)
    }

    @Provides
    @Singleton
    fun provideMemoryLeakDetector(
        @ApplicationContext context: Context
    ): MemoryLeakDetector {
        return MemoryLeakDetector(context as android.app.Application)
    }

    @Provides
    @Singleton
    fun provideAdaptiveMemoryManager(
        @ApplicationContext context: Context,
        memoryPoolManager: MemoryPoolManager,
        referenceCacheManager: ReferenceCacheManager
    ): AdaptiveMemoryManager {
        return AdaptiveMemoryManager(context, memoryPoolManager, referenceCacheManager)
    }

    //endregion

    //region Performance Monitoring

    @Provides
    @Singleton
    fun providePerformanceMonitor(
        @ApplicationContext context: Context
    ): PerformanceMonitor {
        return PerformanceMonitor(context)
    }

    @Provides
    @Singleton
    fun provideStartupTracer(): StartupTracer {
        return StartupTracer()
    }

    //endregion
}
