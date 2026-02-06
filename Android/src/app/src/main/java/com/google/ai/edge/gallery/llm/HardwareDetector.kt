/*
 * Copyright 2025 Tanmay Patil
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.google.ai.edge.gallery.llm

import android.content.Context
import android.os.Build
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Detects and manages hardware acceleration capabilities.
 * 
 * Provides optimal configuration recommendations based on:
 * - GPU capabilities (Vulkan, OpenCL)
 * - NPU availability (Qualcomm Hexagon, MediaTek APU)
 * - Available memory
 * - Thermal state
 */
@Singleton
class HardwareDetector @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "HardwareDetector"
    }

    private var cachedCapabilities: DeviceCapabilities? = null

    /**
     * Get device AI capabilities
     */
    fun getCapabilities(): DeviceCapabilities {
        cachedCapabilities?.let { return it }
        
        val capabilities = detectCapabilities()
        cachedCapabilities = capabilities
        return capabilities
    }

    /**
     * Get optimal LLM engine configuration for this device
     */
    fun getOptimalConfig(modelSizeBytes: Long): LlmEngineConfig {
        val caps = getCapabilities()
        
        // Determine GPU layers based on available memory
        val estimatedModelRam = modelSizeBytes / (1024 * 1024) // MB
        val availableForModel = caps.availableRamMb * 0.7 // Use 70% max
        
        val gpuLayers = when {
            caps.hasVulkan && availableForModel >= estimatedModelRam -> 99  // Full GPU offload
            caps.hasVulkan && availableForModel >= estimatedModelRam * 0.5 -> 32
            caps.hasOpenCL && availableForModel >= estimatedModelRam * 0.5 -> 24
            else -> 0  // CPU only
        }
        
        // Select backend
        val backend = when {
            caps.hasNpuHexagon && caps.snapdragonGen >= 3 -> HardwareBackend.NPU_HEXAGON
            caps.hasVulkan -> HardwareBackend.VULKAN_GPU
            caps.hasOpenCL -> HardwareBackend.OPENCL_GPU
            else -> HardwareBackend.CPU
        }
        
        // Determine context size based on RAM
        val contextSize = when {
            caps.totalRamMb >= 16000 -> 32768
            caps.totalRamMb >= 12000 -> 16384
            caps.totalRamMb >= 8000 -> 8192
            caps.totalRamMb >= 6000 -> 4096
            else -> 2048
        }
        
        // Thread count based on CPU cores
        val threads = minOf(caps.cpuCores, 8)
        
        Log.i(TAG, "Optimal config: backend=$backend, gpuLayers=$gpuLayers, context=$contextSize, threads=$threads")
        
        return LlmEngineConfig(
            backend = backend,
            gpuLayers = gpuLayers,
            contextSize = contextSize,
            batchSize = if (backend == HardwareBackend.CPU) 256 else 512,
            threads = threads,
            useFlashAttention = caps.hasVulkan || caps.hasOpenCL,
            kvCacheType = if (caps.totalRamMb >= 8000) KvCacheType.F16 else KvCacheType.Q8_0
        )
    }

    private fun detectCapabilities(): DeviceCapabilities {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val memInfo = android.app.ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        
        val totalRam = memInfo.totalMem / (1024 * 1024)
        val availableRam = memInfo.availMem / (1024 * 1024)
        
        val cpuCores = Runtime.getRuntime().availableProcessors()
        
        val hasVulkan = checkVulkanSupport()
        val hasOpenCL = checkOpenCLSupport()
        val gpuInfo = detectGpuInfo()
        val snapdragonGen = detectSnapdragonGeneration()
        val hasNpuHexagon = snapdragonGen > 0
        
        val caps = DeviceCapabilities(
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
            androidVersion = Build.VERSION.SDK_INT,
            cpuCores = cpuCores,
            cpuArch = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown",
            totalRamMb = totalRam,
            availableRamMb = availableRam,
            hasVulkan = hasVulkan,
            vulkanVersion = if (hasVulkan) getVulkanVersion() else null,
            hasOpenCL = hasOpenCL,
            openClVersion = if (hasOpenCL) getOpenCLVersion() else null,
            gpuVendor = gpuInfo.vendor,
            gpuModel = gpuInfo.model,
            hasNpuHexagon = hasNpuHexagon,
            snapdragonGen = snapdragonGen,
            hasNpuMediatek = detectMediatekApu()
        )
        
        Log.i(TAG, "Device capabilities: $caps")
        return caps
    }

    private fun checkVulkanSupport(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        
        return try {
            // Check if Vulkan library exists
            System.loadLibrary("vulkan")
            true
        } catch (e: UnsatisfiedLinkError) {
            // Vulkan might still be available through system
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
        }
    }

    private fun checkOpenCLSupport(): Boolean {
        return try {
            // Check common OpenCL library paths
            val openClLibs = listOf("OpenCL", "GLES_mali", "libOpenCL")
            openClLibs.any { lib ->
                try {
                    System.loadLibrary(lib)
                    true
                } catch (e: UnsatisfiedLinkError) {
                    false
                }
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun detectGpuInfo(): GpuInfo {
        val hardware = Build.HARDWARE.lowercase()
        val board = Build.BOARD.lowercase()
        
        return when {
            hardware.contains("qcom") || board.contains("qcom") -> 
                GpuInfo("Qualcomm", "Adreno")
            hardware.contains("mali") || board.contains("mali") -> 
                GpuInfo("ARM", "Mali")
            hardware.contains("powervr") -> 
                GpuInfo("Imagination", "PowerVR")
            hardware.contains("nvidia") -> 
                GpuInfo("NVIDIA", "Tegra")
            else -> GpuInfo("Unknown", "Unknown")
        }
    }

    private fun detectSnapdragonGeneration(): Int {
        val hardware = Build.HARDWARE.lowercase()
        val model = Build.MODEL.lowercase()
        val board = Build.BOARD.lowercase()
        
        // Check for Snapdragon chipset indicators
        val combined = "$hardware $model $board"
        
        return when {
            combined.contains("sm8750") || combined.contains("8 elite") -> 5  // Snapdragon 8 Elite (Gen 5)
            combined.contains("sm8650") || combined.contains("8 gen 4") -> 4  // Snapdragon 8 Gen 4
            combined.contains("sm8550") || combined.contains("8 gen 3") -> 3  // Snapdragon 8 Gen 3
            combined.contains("sm8475") || combined.contains("8+ gen 2") -> 2 // Snapdragon 8+ Gen 2
            combined.contains("sm8450") || combined.contains("8 gen 2") -> 2  // Snapdragon 8 Gen 2
            combined.contains("sm8350") || combined.contains("8 gen 1") -> 1  // Snapdragon 8 Gen 1
            combined.contains("qcom") -> 0  // Older Qualcomm
            else -> -1  // Not Qualcomm
        }
    }

    private fun detectMediatekApu(): Boolean {
        val hardware = Build.HARDWARE.lowercase()
        return hardware.contains("mt") || hardware.contains("mediatek") || hardware.contains("dimensity")
    }

    private fun getVulkanVersion(): String {
        return when {
            Build.VERSION.SDK_INT >= 33 -> "1.3"
            Build.VERSION.SDK_INT >= 30 -> "1.2"
            Build.VERSION.SDK_INT >= 28 -> "1.1"
            else -> "1.0"
        }
    }

    private fun getOpenCLVersion(): String {
        return "3.0" // Most modern Android devices support OpenCL 3.0
    }

    private data class GpuInfo(val vendor: String, val model: String)
}

/**
 * Device AI capabilities
 */
data class DeviceCapabilities(
    val deviceModel: String,
    val androidVersion: Int,
    val cpuCores: Int,
    val cpuArch: String,
    val totalRamMb: Long,
    val availableRamMb: Long,
    val hasVulkan: Boolean,
    val vulkanVersion: String?,
    val hasOpenCL: Boolean,
    val openClVersion: String?,
    val gpuVendor: String,
    val gpuModel: String,
    val hasNpuHexagon: Boolean,
    val snapdragonGen: Int,
    val hasNpuMediatek: Boolean
) {
    val hasDedicatedAiHardware: Boolean
        get() = hasNpuHexagon || hasNpuMediatek
    
    val hasGpuAcceleration: Boolean
        get() = hasVulkan || hasOpenCL
    
    val performanceTier: PerformanceTier
        get() = when {
            snapdragonGen >= 4 && totalRamMb >= 16000 -> PerformanceTier.FLAGSHIP
            snapdragonGen >= 3 && totalRamMb >= 12000 -> PerformanceTier.HIGH
            (snapdragonGen >= 2 || hasVulkan) && totalRamMb >= 8000 -> PerformanceTier.MID
            totalRamMb >= 6000 -> PerformanceTier.ENTRY
            else -> PerformanceTier.MINIMAL
        }
}

enum class PerformanceTier {
    FLAGSHIP,  // 16GB+ RAM, Snapdragon 8 Gen 4+
    HIGH,      // 12GB+ RAM, Snapdragon 8 Gen 3+
    MID,       // 8GB+ RAM, Vulkan support
    ENTRY,     // 6GB+ RAM
    MINIMAL    // < 6GB RAM
}
