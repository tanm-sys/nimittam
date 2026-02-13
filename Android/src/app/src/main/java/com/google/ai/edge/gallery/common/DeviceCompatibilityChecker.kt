/*
 * Copyright 2025 Tanmay Patil
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.google.ai.edge.gallery.common

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File

/**
 * Comprehensive device compatibility checker.
 * Validates device capabilities before attempting to load AI models.
 */
object DeviceCompatibilityChecker {
    
    private const val TAG = "DeviceCompatibility"
    
    // Minimum requirements
    private const val MIN_RAM_GB = 4L  // 4GB minimum
    private const val RECOMMENDED_RAM_GB = 6L  // 6GB recommended
    private const val MIN_STORAGE_MB = 800L  // 800MB free space needed
    private const val MIN_SDK_VERSION = 31  // Android 12
    
    // Required ABI
    private val SUPPORTED_ABIS = setOf("arm64-v8a")
    
    /**
     * Result of compatibility check
     */
    data class CompatibilityResult(
        val isCompatible: Boolean,
        val issues: List<CompatibilityIssue>,
        val warnings: List<CompatibilityIssue>
    ) {
        val canRunWithWarnings: Boolean
            get() = isCompatible || (issues.isEmpty() && warnings.isNotEmpty())
    }
    
    /**
     * Individual compatibility issue
     */
    data class CompatibilityIssue(
        val severity: IssueSeverity,
        val title: String,
        val message: String,
        val canContinue: Boolean = false
    )
    
    enum class IssueSeverity {
        CRITICAL,  // Cannot run
        WARNING,   // Can run with limitations
        INFO       // Informational
    }
    
    /**
     * Check all device compatibility requirements
     */
    fun checkCompatibility(context: Context): CompatibilityResult {
        val issues = mutableListOf<CompatibilityIssue>()
        val warnings = mutableListOf<CompatibilityIssue>()
        
        // Check 1: ABI Support
        checkAbiSupport()?.let { 
            if (it.severity == IssueSeverity.CRITICAL) issues.add(it) else warnings.add(it)
        }
        
        // Check 2: Android Version
        checkAndroidVersion()?.let {
            if (it.severity == IssueSeverity.CRITICAL) issues.add(it) else warnings.add(it)
        }
        
        // Check 3: RAM
        checkRam(context)?.let {
            if (it.severity == IssueSeverity.CRITICAL) issues.add(it) else warnings.add(it)
        }
        
        // Check 4: Storage
        checkStorage(context)?.let {
            if (it.severity == IssueSeverity.CRITICAL) issues.add(it) else warnings.add(it)
        }
        
        // Check 5: Native Library
        checkNativeLibrary()?.let {
            if (it.severity == IssueSeverity.CRITICAL) issues.add(it) else warnings.add(it)
        }
        
        // Check 6: GPU/OpenCL (warning only - can fallback to CPU)
        checkGpuSupport(context)?.let { warnings.add(it) }
        
        // Check 7: Device Model (for known incompatible devices)
        checkDeviceModel()?.let {
            if (it.severity == IssueSeverity.CRITICAL) issues.add(it) else warnings.add(it)
        }
        
        val isCompatible = issues.none { it.severity == IssueSeverity.CRITICAL && !it.canContinue }
        
        // Log results
        Log.i(TAG, "Compatibility check complete:")
        Log.i(TAG, "  Compatible: $isCompatible")
        Log.i(TAG, "  Critical issues: ${issues.filter { it.severity == IssueSeverity.CRITICAL }.size}")
        Log.i(TAG, "  Warnings: ${warnings.size}")
        
        return CompatibilityResult(isCompatible, issues, warnings)
    }
    
    /**
     * Quick check - returns true if device should be able to run
     */
    fun isDeviceCompatible(context: Context): Boolean {
        return checkCompatibility(context).isCompatible
    }
    
    private fun checkAbiSupport(): CompatibilityIssue? {
        val supportedAbis = Build.SUPPORTED_ABIS?.toList() ?: emptyList()
        Log.d(TAG, "Device ABIs: $supportedAbis")
        
        val hasSupportedAbi = supportedAbis.any { it in SUPPORTED_ABIS }
        
        return if (!hasSupportedAbi) {
            CompatibilityIssue(
                severity = IssueSeverity.CRITICAL,
                title = "Unsupported Device Architecture",
                message = "This app requires a 64-bit ARM processor (arm64-v8a). Your device uses: ${supportedAbis.joinToString()}",
                canContinue = false
            )
        } else {
            null
        }
    }
    
    private fun checkAndroidVersion(): CompatibilityIssue? {
        val sdkVersion = Build.VERSION.SDK_INT
        Log.d(TAG, "Android SDK version: $sdkVersion")
        
        return when {
            sdkVersion < MIN_SDK_VERSION -> {
                CompatibilityIssue(
                    severity = IssueSeverity.CRITICAL,
                    title = "Android Version Too Old",
                    message = "This app requires Android 12 (API $MIN_SDK_VERSION) or newer. Your device is running Android ${Build.VERSION.RELEASE} (API $sdkVersion)",
                    canContinue = false
                )
            }
            sdkVersion >= 36 -> {  // Android 16+ - 16KB page size REQUIRED
                // Native library has been rebuilt with 16KB alignment
                // App is fully compatible with Android 16+
                CompatibilityIssue(
                    severity = IssueSeverity.INFO,
                    title = "Android 16+ Support",
                    message = "Your device runs Android 16 (API $sdkVersion). The app is fully compatible with 16KB page size memory architecture.",
                    canContinue = true
                )
            }
            sdkVersion >= 35 -> {  // Android 15 - 16KB page size MAY be used
                CompatibilityIssue(
                    severity = IssueSeverity.WARNING,
                    title = "Android 15 Detected - 16KB Pages",
                    message = "Your device runs Android 15 (API 35). Some Android 15 devices use 16KB memory pages " +
                             "which may cause this app to crash. If the app crashes on startup, please use an Android 14 or older device.",
                    canContinue = true
                )
            }
            else -> null
        }
    }
    
    private fun checkRam(context: Context): CompatibilityIssue? {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        
        val totalRam = memoryInfo.totalMem / (1024 * 1024 * 1024)  // Convert to GB
        val availRam = memoryInfo.availMem / (1024 * 1024 * 1024)  // Convert to GB
        
        Log.d(TAG, "Total RAM: ${totalRam}GB, Available: ${availRam}GB")
        
        return when {
            totalRam < MIN_RAM_GB -> {
                CompatibilityIssue(
                    severity = IssueSeverity.CRITICAL,
                    title = "Insufficient RAM",
                    message = "This app requires at least $MIN_RAM_GB GB of RAM. Your device has ${totalRam}GB. The AI features will not work properly.",
                    canContinue = false
                )
            }
            totalRam < RECOMMENDED_RAM_GB -> {
                CompatibilityIssue(
                    severity = IssueSeverity.WARNING,
                    title = "Low RAM",
                    message = "Your device has ${totalRam}GB RAM. For best performance, ${RECOMMENDED_RAM_GB}GB or more is recommended. The app may run slowly.",
                    canContinue = true
                )
            }
            else -> null
        }
    }
    
    private fun checkStorage(context: Context): CompatibilityIssue? {
        val filesDir = context.filesDir
        val freeSpace = filesDir.freeSpace / (1024 * 1024)  // Convert to MB
        
        Log.d(TAG, "Free storage: ${freeSpace}MB")
        
        return if (freeSpace < MIN_STORAGE_MB) {
            CompatibilityIssue(
                severity = IssueSeverity.CRITICAL,
                title = "Insufficient Storage",
                message = "This app needs at least $MIN_STORAGE_MB MB of free space. Your device has ${freeSpace}MB available. Please free up space and try again.",
                canContinue = false
            )
        } else {
            null
        }
    }
    
    private fun checkNativeLibrary(): CompatibilityIssue? {
        return try {
            System.loadLibrary("tvm4j_runtime_packed")
            null  // Library loaded successfully
        } catch (e: UnsatisfiedLinkError) {
            CompatibilityIssue(
                severity = IssueSeverity.CRITICAL,
                title = "Native Library Error",
                message = "Failed to load required native library. This may be due to:\n" +
                         "• Incompatible device architecture\n" +
                         "• Corrupted app installation\n" +
                         "• Missing system libraries\n\n" +
                         "Please reinstall the app or contact support.",
                canContinue = false
            )
        } catch (e: Exception) {
            CompatibilityIssue(
                severity = IssueSeverity.CRITICAL,
                title = "Library Loading Error",
                message = "Unexpected error loading native library: ${e.message}",
                canContinue = false
            )
        }
    }
    
    private fun checkGpuSupport(context: Context): CompatibilityIssue? {
        // Check for OpenCL support
        val hasOpenCL = try {
            System.loadLibrary("OpenCL")
            true
        } catch (e: Exception) {
            false
        }
        
        // Check for GPU renderer
        val hasGpu = (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager)
            .deviceConfigurationInfo.reqGlEsVersion >= 0x20000
        
        Log.d(TAG, "OpenCL support: $hasOpenCL, GPU: $hasGpu")
        
        return if (!hasOpenCL && !hasGpu) {
            CompatibilityIssue(
                severity = IssueSeverity.WARNING,
                title = "Limited GPU Support",
                message = "Your device has limited GPU acceleration. The AI will run slower using CPU-only mode. For best performance, a device with OpenCL support is recommended.",
                canContinue = true
            )
        } else {
            null
        }
    }
    
    private fun checkDeviceModel(): CompatibilityIssue? {
        val model = Build.MODEL
        val manufacturer = Build.MANUFACTURER
        
        Log.d(TAG, "Device: $manufacturer $model")
        
        // List of known problematic devices (example)
        val knownIssues = mapOf(
            "SM-N9005" to "Note 3 has compatibility issues with OpenCL",
            "Nexus 5" to "Limited RAM may cause instability"
        )
        
        return knownIssues[model]?.let { issue ->
            CompatibilityIssue(
                severity = IssueSeverity.WARNING,
                title = "Known Device Issue",
                message = "Your device ($model) has known compatibility issues: $issue",
                canContinue = true
            )
        }
    }
    
    /**
     * Get device info for debugging
     */
    fun getDeviceInfo(context: Context): Map<String, String> {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager?.getMemoryInfo(memoryInfo)
        
        val info = mutableMapOf<String, String>()
        info["Manufacturer"] = Build.MANUFACTURER
        info["Model"] = Build.MODEL
        info["Device"] = Build.DEVICE
        info["Android Version"] = Build.VERSION.RELEASE
        info["SDK Version"] = Build.VERSION.SDK_INT.toString()
        info["ABIs"] = Build.SUPPORTED_ABIS?.joinToString() ?: "unknown"
        info["Total RAM"] = "${memoryInfo.totalMem / (1024 * 1024 * 1024)}GB"
        info["Available RAM"] = "${memoryInfo.availMem / (1024 * 1024)}MB"
        info["Board"] = Build.BOARD
        info["Hardware"] = Build.HARDWARE
        return info
    }
}
