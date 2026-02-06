/*
 * Copyright 2025 Tanmay Patil
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.google.ai.edge.gallery

import android.app.Application
import android.util.Log
import com.google.ai.edge.gallery.common.BatteryOptimizer
import com.google.ai.edge.gallery.common.CrashHandler
import com.google.ai.edge.gallery.common.MemoryManager
import com.google.ai.edge.gallery.common.OfflineMode
import com.google.ai.edge.gallery.common.SecureStorage
import com.google.ai.edge.gallery.common.ThermalManager
import com.google.ai.edge.gallery.data.DataStoreRepository
import com.google.ai.edge.gallery.llm.EngineLifecycleManager
import com.google.ai.edge.gallery.llm.ModelAssetExtractor
import com.google.ai.edge.gallery.llm.ModelManager
import com.google.ai.edge.gallery.llm.engine.MlcLlmEngine
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "GalleryApplication"

@HiltAndroidApp
class GalleryApplication : Application() {

  @Inject lateinit var dataStoreRepository: DataStoreRepository
  @Inject lateinit var modelManager: ModelManager
  @Inject lateinit var engineLifecycleManager: EngineLifecycleManager
  @Inject lateinit var mlcLlmEngine: MlcLlmEngine

  override fun onCreate() {
    super.onCreate()

    // Initialize crash handler first (mission-critical reliability)
    CrashHandler.install(this)

    // Initialize optimization managers
    initializeManagers()

    // Check for crash recovery
    checkCrashRecovery()

    // CRITICAL: Register the MLC-LLM engine with the lifecycle manager
    // This breaks the circular DI dependency by deferring registration
    mlcLlmEngine.ensureRegistered()

    // Extract bundled MLC-LLM model and initialize engine in background
    // This is the critical path for app startup with LLM capabilities
    val modelExtractionDispatcher = Dispatchers.IO.limitedParallelism(1)
    CoroutineScope(modelExtractionDispatcher).launch {
        val operationId = java.util.UUID.randomUUID().toString().take(8)
        val threadName = Thread.currentThread().name
        Log.d(TAG, "[$operationId] Starting model extraction on thread: $threadName")
        
        try {
            // Step 1: Extract model weights from APK assets to filesystem
            val modelPath = ModelAssetExtractor.extractModelIfNeeded(
                context = this@GalleryApplication,
                onProgress = { progress, message ->
                    Log.d(TAG, "[$operationId] Extraction progress: $progress% - $message")
                }
            )
            Log.i(TAG, "[$operationId] Model extracted to: $modelPath")
            
            // Step 2: Initialize the LLM engine with extracted model
            Log.i(TAG, "[$operationId] Initializing LLM engine...")
            val initResult = engineLifecycleManager.initialize(modelPath)
            
            initResult.onSuccess {
                Log.i(TAG, "[$operationId] LLM engine initialized successfully")
            }.onFailure { error ->
                Log.e(TAG, "[$operationId] LLM engine initialization failed: ${error.message}", error)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "[$operationId] Model setup failed: ${e.javaClass.simpleName}: ${e.message}", e)
        }
    }
  }

  private fun initializeManagers() {
    // Initialize in order of priority
    OfflineMode.init(this)
    SecureStorage.init(this)
    MemoryManager.init(this)
    BatteryOptimizer.init(this)
    ThermalManager.init(this)

    Log.d(TAG, "All optimization managers initialized")
    Log.d(TAG, "Device RAM: ${MemoryManager.getTotalMemoryGb(this)}GB")
    Log.d(TAG, "Optimal max tokens: ${MemoryManager.calculateOptimalMaxTokens(this)}")
  }

  private fun checkCrashRecovery() {
    if (CrashHandler.needsRecovery(this)) {
      val timeSinceCrash = CrashHandler.getTimeSinceLastCrash(this)
      Log.w(TAG, "App recovered from crash (${timeSinceCrash}ms ago)")
      CrashHandler.clearRecoveryFlag(this)
    }
  }
}
