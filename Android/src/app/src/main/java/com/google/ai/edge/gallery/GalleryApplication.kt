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
import com.google.ai.edge.gallery.llm.ModelManager
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

  override fun onCreate() {
    super.onCreate()

    // Initialize crash handler first (mission-critical reliability)
    CrashHandler.install(this)

    // Initialize optimization managers
    initializeManagers()

    // Check for crash recovery
    checkCrashRecovery()

    // Extract bundled LLM model in background with dedicated thread for I/O priority
    // OPTIMIZATION: Uses a dedicated single-threaded dispatcher for model extraction
    // to ensure it doesn't compete with other I/O operations and completes quickly
    val modelExtractionDispatcher = Dispatchers.IO.limitedParallelism(1)
    CoroutineScope(modelExtractionDispatcher).launch {
        val operationId = java.util.UUID.randomUUID().toString().take(8)
        val threadName = Thread.currentThread().name
        Log.d(TAG, "[DIAGNOSTIC] GalleryApplication[$operationId] Starting model extraction on thread: $threadName")
        try {
            modelManager.extractBundledModel()
            Log.d(TAG, "[DIAGNOSTIC] GalleryApplication[$operationId] Model extraction completed")
        } catch (e: Exception) {
            Log.e(TAG, "[DIAGNOSTIC] GalleryApplication[$operationId] Model extraction failed: ${e.javaClass.simpleName}: ${e.message}", e)
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
