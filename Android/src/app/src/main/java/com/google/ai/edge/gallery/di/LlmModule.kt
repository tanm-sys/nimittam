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
import javax.inject.Singleton

/**
 * Hilt module for LLM-related dependencies.
 * 
 * Provides:
 * - EngineStateManager for thread-safe state management
 * - PromptQueue for buffering prompts during initialization
 * - EngineLifecycleManager for orchestrating engine lifecycle
 * - MlcLlmEngine as the concrete LlmEngine implementation
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
         * Provides the EngineLifecycleManager singleton.
         */
        @Provides
        @Singleton
        fun provideEngineLifecycleManager(
            @ApplicationContext context: Context,
            stateManager: EngineStateManager,
            promptQueue: PromptQueue
        ): EngineLifecycleManager {
            return EngineLifecycleManager(
                context = context,
                stateManager = stateManager,
                promptQueue = promptQueue,
                retryPolicy = RetryPolicy.DEFAULT
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
