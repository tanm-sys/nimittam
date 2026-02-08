/*
 * Copyright 2025 Tanmay Patil
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.google.ai.edge.gallery

import android.app.Application
import android.content.Context
import android.util.Log
import com.google.ai.edge.gallery.common.BatteryOptimizer
import com.google.ai.edge.gallery.common.CrashHandler
import com.google.ai.edge.gallery.common.DeviceCompatibilityChecker
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "GalleryApplication"

@HiltAndroidApp
class GalleryApplication : Application() {

  @Inject lateinit var dataStoreRepository: DataStoreRepository
  @Inject lateinit var modelManager: ModelManager
  @Inject lateinit var engineLifecycleManager: EngineLifecycleManager
  @Inject lateinit var mlcLlmEngine: MlcLlmEngine

  /**
   * Application-scoped coroutine scope for background operations.
   * Uses SupervisorJob so failure of one child doesn't cancel others.
   * Must be cancelled in onTerminate() to prevent memory leaks.
   */
  private val applicationScope = CoroutineScope(
    SupervisorJob() + Dispatchers.IO.limitedParallelism(2)
  )
  
  companion object {
    @Volatile
    private var instance: GalleryApplication? = null
    
    fun getAppContext(): Context? = instance?.applicationContext
  }

    override fun onCreate() {
    super.onCreate()
    instance = this

    // Initialize crash handler first (mission-critical reliability)
    CrashHandler.install(this)
    
    Log.i(TAG, "GalleryApplication.onCreate() started")
    Log.i(TAG, "Android Version: ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
    Log.i(TAG, "Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
    
    // TEMPORARY: Skip LLM initialization on Android 16+ due to native library incompatibility
    // This prevents crashes until a proper Android 16 compatible build is available
    val sdkVersion = android.os.Build.VERSION.SDK_INT
    if (sdkVersion >= 36) {
      Log.w(TAG, "⚠️ ANDROID 16+ DETECTED - AI features disabled")
      Log.w(TAG, "  The native AI library is incompatible with Android 16.")
      Log.w(TAG, "  The app will run in limited mode without AI chat features.")
      Log.w(TAG, "  Please use Android 15 or earlier for full functionality.")
      
      // Still initialize basic managers so app doesn't crash
      initializeManagers()
      return
    }
    
    // Check device compatibility before proceeding
    val compatibility = DeviceCompatibilityChecker.checkCompatibility(this)
    if (!compatibility.isCompatible) {
      Log.e(TAG, "Device compatibility check FAILED")
      compatibility.issues.forEach { issue ->
        Log.e(TAG, "[${issue.severity}] ${issue.title}: ${issue.message}")
      }
      // Store compatibility issues for UI to display
      // Don't proceed with LLM initialization on incompatible devices
      return
    }
    
    if (compatibility.warnings.isNotEmpty()) {
      Log.w(TAG, "Device compatibility check PASSED with warnings:")
      compatibility.warnings.forEach { warning ->
        Log.w(TAG, "[WARNING] ${warning.title}: ${warning.message}")
      }
    } else {
      Log.i(TAG, "Device compatibility check PASSED")
    }

    // Initialize optimization managers
    initializeManagers()

    // Check for crash recovery
    checkCrashRecovery()

    // CRITICAL: Register the MLC-LLM engine with the lifecycle manager
    // This breaks the circular DI dependency by deferring registration
    try {
      mlcLlmEngine.ensureRegistered()
      Log.i(TAG, "MlcLlmEngine registered successfully")
    } catch (e: Exception) {
      Log.e(TAG, "Failed to register MlcLlmEngine", e)
      // Continue - error will be shown when user tries to chat
    }

    // Extract bundled MLC-LLM model and initialize engine in background
    // This is the critical path for app startup with LLM capabilities
    // Delay initialization to ensure app UI is responsive first
    applicationScope.launch {
      kotlinx.coroutines.delay(2000) // Wait 2 seconds for UI to render
      initializeLlmEngine()
    }
  }

  /**
   * Initialize the LLM engine in a background coroutine.
   * Uses applicationScope which is properly managed.
   */
  private fun initializeLlmEngine() {
    applicationScope.launch {
      val operationId = java.util.UUID.randomUUID().toString().take(8)
      val threadName = Thread.currentThread().name
      Log.i(TAG, "[$operationId] Starting LLM initialization on thread: $threadName")

      try {
        // Step 1: Extract model weights from APK assets to filesystem
        Log.i(TAG, "[$operationId] Step 1: Extracting model...")
        val modelPath = ModelAssetExtractor.extractModelIfNeeded(
          context = this@GalleryApplication,
          onProgress = { progress, message ->
            Log.d(TAG, "[$operationId] Extraction: $progress% - $message")
          }
        )
        Log.i(TAG, "[$operationId] Model extracted to: $modelPath")

        // Step 2: Initialize the LLM engine with extracted model
        Log.i(TAG, "[$operationId] Step 2: Initializing LLM engine...")
        val initResult = engineLifecycleManager.initialize(modelPath)

        initResult.onSuccess {
          Log.i(TAG, "[$operationId] LLM engine initialized successfully")
        }.onFailure { error ->
          Log.e(TAG, "[$operationId] LLM engine initialization failed: ${error.message}", error)
          Log.e(TAG, "[$operationId] Stack trace:", error)
        }

      } catch (e: CancellationException) {
        Log.d(TAG, "[$operationId] Model setup cancelled")
      } catch (e: UnsatisfiedLinkError) {
        Log.e(TAG, "[$operationId] Native library error: ${e.message}", e)
        Log.e(TAG, "[$operationId] This usually means the native library is incompatible with this device")
      } catch (e: Exception) {
        Log.e(TAG, "[$operationId] Model setup failed: ${e.javaClass.simpleName}: ${e.message}", e)
        Log.e(TAG, "[$operationId] Full stack trace:", e)
      }
    }
  }

  override fun onTerminate() {
    super.onTerminate()
    // Cancel the application scope to prevent memory leaks
    Log.d(TAG, "Application terminating, cancelling background operations")
    applicationScope.cancel()
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
