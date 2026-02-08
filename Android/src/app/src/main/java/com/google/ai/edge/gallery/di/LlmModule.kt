/*
 * Copyright 2025 Tanmay Patil
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.google.ai.edge.gallery.di

import android.content.Context
import com.google.ai.edge.gallery.llm.*
import com.google.ai.edge.gallery.llm.engine.MlcLlmEngine
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hilt module for LLM-related dependencies.
 * 
 * Provides:
 * - EngineStateManager for thread-safe state management
 * - PromptQueue for buffering prompts during initialization
 * - EngineLifecycleManager for orchestrating engine lifecycle
 * - MlcLlmEngine as the concrete LlmEngine implementation
 * 
 * Architecture Notes:
 * - Uses Provider pattern to break circular dependency between MlcLlmEngine and EngineLifecycleManager
 * - EngineInitializer is created with a deferred InitOperation that's set after LlmEngine is available
 * - This allows proper dependency injection without circular references
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class LlmModule {
    
    @Binds
    @Singleton
    abstract fun bindLlmEngine(
        engine: MlcLlmEngine
    ): LlmEngine

    companion object {
        
        /**
         * Provides the EngineStateManager singleton.
         */
        @Provides
        @Singleton
        fun provideEngineStateManager(): EngineStateManager {
            return EngineStateManager.create { event ->
                // Telemetry callback - can be integrated with analytics
                // For now, just log the event
                android.util.Log.d("EngineTelemetry", 
                    "State transition: ${event.fromState} -> ${event.toState} at ${event.timestamp}"
                )
            }
        }
        
        /**
         * Provides the PromptQueue singleton.
         */
        @Provides
        @Singleton
        fun providePromptQueue(): PromptQueue {
            return PromptQueue(PromptQueueConfig.DEFAULT)
        }
        
        /**
         * Provides the EngineInitializer with deferred operation binding.
         * The actual InitOperation is set by EngineLifecycleManager after LlmEngine is available.
         * This breaks the circular dependency: LlmEngine -> LifecycleManager -> Initializer -> LlmEngine
         */
        @Provides
        @Singleton
        fun provideEngineInitializer(
            @ApplicationContext context: Context,
            stateManager: EngineStateManager,
            retryPolicy: RetryPolicy
        ): EngineInitializerProvider {
            // Create a provider that holds the initializer with a placeholder operation
            // The real operation will be set later via setInitOperation()
            return EngineInitializerProvider(context, stateManager, retryPolicy)
        }
        
        /**
         * Provides the EngineLifecycleManager singleton.
         * Injects the initializer provider to break circular dependency.
         */
        @Provides
        @Singleton
        fun provideEngineLifecycleManager(
            @ApplicationContext context: Context,
            stateManager: EngineStateManager,
            promptQueue: PromptQueue,
            initializerProvider: EngineInitializerProvider
        ): EngineLifecycleManager {
            return EngineLifecycleManager(
                context = context,
                stateManager = stateManager,
                promptQueue = promptQueue,
                retryPolicy = RetryPolicy.DEFAULT,
                initializerProvider = initializerProvider
            )
        }
        
        /**
         * Provides the default retry policy.
         */
        @Provides
        @Singleton
        fun provideRetryPolicy(): RetryPolicy {
            return RetryPolicy.DEFAULT
        }
        
        /**
         * Provides the default prompt queue configuration.
         */
        @Provides
        @Singleton
        fun providePromptQueueConfig(): PromptQueueConfig {
            return PromptQueueConfig.DEFAULT
        }
    }
}

/**
 * Provider class that holds EngineInitializer and allows deferred init operation binding.
 * This breaks the circular dependency between LlmEngine and EngineLifecycleManager.
 */
@Singleton
class EngineInitializerProvider @Inject constructor(
    private val context: Context,
    private val stateManager: EngineStateManager,
    private val retryPolicy: RetryPolicy
) {
    private var _initializer: EngineInitializer? = null
    private var _initOperation: InitOperation? = null
    
    /**
     * Gets or creates the EngineInitializer.
     * If an init operation has been set, it will be used; otherwise a no-op is used temporarily.
     */
    fun getInitializer(): EngineInitializer {
        return _initializer ?: createInitializer().also { _initializer = it }
    }
    
    /**
     * Sets the actual InitOperation. Should be called after LlmEngine is available.
     */
    fun setInitOperation(operation: InitOperation) {
        _initOperation = operation
        // If initializer already exists, we can't change it (immutable by design)
        // This is why we need to set it before first use
    }
    
    /**
     * Checks if init operation has been set.
     */
    fun hasInitOperation(): Boolean = _initOperation != null
    
    private fun createInitializer(): EngineInitializer {
        return EngineInitializer(
            context = context,
            stateManager = stateManager,
            retryPolicy = retryPolicy,
            initOperation = _initOperation ?: InitOperation { _, _, _ ->
                // Placeholder operation - will fail until real operation is set
                Result.failure(IllegalStateException("InitOperation not set. Call setEngine() on EngineLifecycleManager first."))
            }
        )
    }
}
