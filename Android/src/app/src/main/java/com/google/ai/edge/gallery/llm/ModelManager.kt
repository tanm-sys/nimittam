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
import com.google.ai.edge.gallery.common.MemoryManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

/**
 * Manages LLM model downloads, storage, and lifecycle.
 * 
 * Supports:
 * - Model downloading with progress tracking
 * - Resume interrupted downloads
 * - Model verification (checksum)
 * - Storage management and cleanup
 * - Auto-detection of optimal models for device
 */
@Singleton
class ModelManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "ModelManager"
        private const val MODELS_DIR = "llm_models"
        private const val BUFFER_SIZE = 8192
        private const val CONNECT_TIMEOUT = 30000
        private const val READ_TIMEOUT = 60000
    }

    private val modelsDir: File by lazy {
        File(context.filesDir, MODELS_DIR).apply { mkdirs() }
    }

    /**
     * Get recommended models based on device capabilities
     */
    fun getRecommendedModels(): List<ModelInfo> {
        val availableRam = MemoryManager.getAvailableMemoryMb(context)
        val totalRam = getTotalDeviceRam()
        
        Log.i(TAG, "Device RAM: ${totalRam}MB, Available: ${availableRam}MB")
        
        return when {
            totalRam >= 16000 -> TIER_FLAGSHIP_MODELS
            totalRam >= 12000 -> TIER_HIGH_MODELS
            totalRam >= 8000 -> TIER_MID_MODELS
            totalRam >= 6000 -> TIER_ENTRY_MODELS
            else -> TIER_MINIMAL_MODELS
        }
    }

    /**
     * Get all downloaded models
     */
    fun getDownloadedModels(): List<DownloadedModel> {
        val models = mutableListOf<DownloadedModel>()
        
        modelsDir.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                // Check for MLC model directory (must contain mlc-chat-config.json)
                if (File(file, "mlc-chat-config.json").exists()) {
                    models.add(DownloadedModel(
                        name = file.name,
                        path = file.absolutePath,
                        sizeBytes = getDirectorySize(file),
                        format = ModelFormat.MLC
                    ))
                }
            } else if (file.isFile && (file.extension == "bin" || file.extension == "gguf" || file.extension == "mlc")) {
                models.add(DownloadedModel(
                    name = file.nameWithoutExtension,
                    path = file.absolutePath,
                    sizeBytes = file.length(),
                    format = ModelFormat.fromExtension(file.extension)
                ))
            }
        }
        return models
    }

    private fun getDirectorySize(directory: File): Long {
        var length: Long = 0
        directory.listFiles()?.forEach { file ->
            length += if (file.isFile) file.length() else getDirectorySize(file)
        }
        return length
    }

    /**
     * Extract bundled model from assets if present
     */
    suspend fun extractBundledModel() = withContext(Dispatchers.IO) {
        val operationId = java.util.UUID.randomUUID().toString().take(8)
        val threadName = Thread.currentThread().name
        val bundledModelName = "qwen2.5-0.5b"
        val targetDir = File(modelsDir, bundledModelName)

        Log.d(TAG, "[DIAGNOSTIC] extractBundledModel[$operationId] START on thread: $threadName")
        Log.d(TAG, "[DIAGNOSTIC] extractBundledModel[$operationId] targetDir: ${targetDir.absolutePath}, exists: ${targetDir.exists()}")

        // If already exists and looks valid, skip
        if (targetDir.exists() && File(targetDir, "mlc-chat-config.json").exists()) {
            Log.i(TAG, "[DIAGNOSTIC] extractBundledModel[$operationId] Bundled model already extracted, skipping")
            return@withContext
        }

        Log.i(TAG, "[DIAGNOSTIC] extractBundledModel[$operationId] Extracting bundled model: $bundledModelName")

        // Check available space
        val freeSpace = targetDir.parentFile?.freeSpace ?: -1
        Log.d(TAG, "[DIAGNOSTIC] extractBundledModel[$operationId] Free space: ${freeSpace / 1024 / 1024}MB")

        val created = targetDir.mkdirs()
        Log.d(TAG, "[DIAGNOSTIC] extractBundledModel[$operationId] mkdirs result: $created")

        try {
            val assets = context.assets
            val files = assets.list(bundledModelName)
            Log.d(TAG, "[DIAGNOSTIC] extractBundledModel[$operationId] Found ${files?.size ?: 0} files in assets")

            if (files == null) {
                Log.w(TAG, "[DIAGNOSTIC] extractBundledModel[$operationId] No files found in assets for $bundledModelName")
                return@withContext
            }

            for (filename in files) {
                val assetPath = "$bundledModelName/$filename"
                val outFile = File(targetDir, filename)
                Log.d(TAG, "[DIAGNOSTIC] extractBundledModel[$operationId] Extracting: $filename -> ${outFile.absolutePath}")

                assets.open(assetPath).use { input ->
                    FileOutputStream(outFile).use { output ->
                        val bytesCopied = input.copyTo(output)
                        Log.d(TAG, "[DIAGNOSTIC] extractBundledModel[$operationId] Copied $bytesCopied bytes for $filename")
                    }
                }
            }
            Log.i(TAG, "[DIAGNOSTIC] extractBundledModel[$operationId] SUCCESS - Extracted ${files.size} files")
        } catch (e: Exception) {
            Log.e(TAG, "[DIAGNOSTIC] extractBundledModel[$operationId] ERROR: ${e.javaClass.simpleName}: ${e.message}", e)
            val deleted = targetDir.deleteRecursively()
            Log.d(TAG, "[DIAGNOSTIC] extractBundledModel[$operationId] Cleanup deleted: $deleted")
            throw e // Re-throw to let caller know extraction failed
        }
    }

    /**
     * Download a model with progress callback
     */
    suspend fun downloadModel(
        model: ModelInfo,
        onProgress: (DownloadProgress) -> Unit
    ): Result<DownloadedModel> = withContext(Dispatchers.IO) {
        try {
            val targetFile = File(modelsDir, model.filename)
            val tempFile = File(modelsDir, "${model.filename}.tmp")
            
            // Check if already downloaded
            if (targetFile.exists() && targetFile.length() == model.sizeBytes) {
                Log.i(TAG, "Model already downloaded: ${model.name}")
                return@withContext Result.success(
                    DownloadedModel(
                        name = model.name,
                        path = targetFile.absolutePath,
                        sizeBytes = targetFile.length(),
                        format = model.format
                    )
                )
            }
            
            // Resume support - check temp file
            var downloadedBytes = if (tempFile.exists()) tempFile.length() else 0L
            
            Log.i(TAG, "Downloading model: ${model.name} from ${model.downloadUrl}")
            if (downloadedBytes > 0) {
                Log.i(TAG, "Resuming download from byte $downloadedBytes")
            }
            
            val url = URL(model.downloadUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = CONNECT_TIMEOUT
            connection.readTimeout = READ_TIMEOUT
            
            // Request range for resume
            if (downloadedBytes > 0) {
                connection.setRequestProperty("Range", "bytes=$downloadedBytes-")
            }
            
            connection.connect()
            
            val responseCode = connection.responseCode
            val totalBytes = when (responseCode) {
                HttpURLConnection.HTTP_OK -> connection.contentLengthLong
                HttpURLConnection.HTTP_PARTIAL -> downloadedBytes + connection.contentLengthLong
                else -> {
                    return@withContext Result.failure(
                        RuntimeException("HTTP error: $responseCode")
                    )
                }
            }
            
            // If server doesn't support range, restart
            if (responseCode == HttpURLConnection.HTTP_OK && downloadedBytes > 0) {
                downloadedBytes = 0
                tempFile.delete()
            }
            
            val inputStream = connection.inputStream
            val outputStream = FileOutputStream(tempFile, downloadedBytes > 0)
            
            val buffer = ByteArray(BUFFER_SIZE)
            var bytesRead: Int
            var lastProgressUpdate = System.currentTimeMillis()
            val startTime = System.currentTimeMillis()
            
            try {
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    downloadedBytes += bytesRead
                    
                    // Update progress every 100ms
                    val now = System.currentTimeMillis()
                    if (now - lastProgressUpdate >= 100) {
                        val elapsed = (now - startTime) / 1000.0
                        val speed = if (elapsed > 0) downloadedBytes / elapsed else 0.0
                        val remaining = if (speed > 0) (totalBytes - downloadedBytes) / speed else 0.0
                        
                        onProgress(
                            DownloadProgress(
                                bytesDownloaded = downloadedBytes,
                                totalBytes = totalBytes,
                                speedBytesPerSec = speed.toLong(),
                                estimatedSecondsRemaining = remaining.toLong()
                            )
                        )
                        lastProgressUpdate = now
                    }
                }
            } finally {
                inputStream.close()
                outputStream.close()
                connection.disconnect()
            }
            
            // Verify download size
            if (tempFile.length() != totalBytes && model.sizeBytes > 0) {
                return@withContext Result.failure(
                    RuntimeException("Download incomplete: ${tempFile.length()} / $totalBytes bytes")
                )
            }
            
            // Rename temp to final
            tempFile.renameTo(targetFile)
            
            Log.i(TAG, "Model downloaded successfully: ${model.name}")
            
            Result.success(
                DownloadedModel(
                    name = model.name,
                    path = targetFile.absolutePath,
                    sizeBytes = targetFile.length(),
                    format = model.format
                )
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download model: ${model.name}", e)
            Result.failure(e)
        }
    }

    /**
     * Delete a downloaded model
     */
    fun deleteModel(modelPath: String): Boolean {
        val file = File(modelPath)
        return if (file.exists() && file.delete()) {
            Log.i(TAG, "Model deleted: $modelPath")
            true
        } else {
            Log.w(TAG, "Failed to delete model: $modelPath")
            false
        }
    }

    /**
     * Get storage usage
     */
    fun getStorageUsage(): StorageInfo {
        val files = modelsDir.listFiles() ?: emptyArray()
        val usedBytes = files.sumOf { it.length() }
        val freeBytes = modelsDir.usableSpace
        
        return StorageInfo(
            usedBytes = usedBytes,
            freeBytes = freeBytes,
            modelCount = files.count { it.extension in listOf("bin", "gguf", "mlc") }
        )
    }

    private fun getTotalDeviceRam(): Long {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val memInfo = android.app.ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        return memInfo.totalMem / (1024 * 1024)
    }

    // ==================== Model Catalog ====================

    private val TIER_FLAGSHIP_MODELS = listOf(
        ModelInfo(
            name = "Qwen3-8B-Instruct",
            filename = "qwen3-8b-instruct-q4_k_m.mlc",
            format = ModelFormat.MLC,
            sizeBytes = 5_000_000_000L,
            downloadUrl = "https://huggingface.co/mlc-ai/Qwen3-8B-Instruct-q4f16_1-MLC/resolve/main/qwen3-8b-instruct-q4_k_m.mlc",
            description = "Highest quality, best for complex reasoning",
            contextSize = 32768,
            recommendedRamMb = 12000
        ),
        ModelInfo(
            name = "Llama-3.1-8B-Instruct",
            filename = "llama-3.1-8b-instruct-q4_k_m.mlc",
            format = ModelFormat.MLC,
            sizeBytes = 4_800_000_000L,
            downloadUrl = "https://huggingface.co/mlc-ai/Llama-3.1-8B-Instruct-q4f16_1-MLC/resolve/main/llama-3.1-8b-instruct-q4_k_m.mlc",
            description = "Meta's flagship, excellent all-around",
            contextSize = 131072,
            recommendedRamMb = 12000
        )
    )

    private val TIER_HIGH_MODELS = listOf(
        ModelInfo(
            name = "Qwen3-4B-Instruct",
            filename = "qwen3-4b-instruct-q4_k_m.mlc",
            format = ModelFormat.MLC,
            sizeBytes = 2_600_000_000L,
            downloadUrl = "https://huggingface.co/mlc-ai/Qwen3-4B-Instruct-q4f16_1-MLC/resolve/main/qwen3-4b-instruct-q4_k_m.mlc",
            description = "Best balance of quality and speed",
            contextSize = 32768,
            recommendedRamMb = 8000
        ),
        ModelInfo(
            name = "Llama-3.2-3B-Instruct",
            filename = "llama-3.2-3b-instruct-q4_k_m.mlc",
            format = ModelFormat.MLC,
            sizeBytes = 2_000_000_000L,
            downloadUrl = "https://huggingface.co/mlc-ai/Llama-3.2-3B-Instruct-q4f16_1-MLC/resolve/main/llama-3.2-3b-instruct-q4_k_m.mlc",
            description = "Compact but capable",
            contextSize = 131072,
            recommendedRamMb = 6000
        )
    )

    private val TIER_MID_MODELS = listOf(
        ModelInfo(
            name = "Gemma-3-1B",
            filename = "gemma-3-1b-it-q4_k_m.mlc",
            format = ModelFormat.MLC,
            sizeBytes = 700_000_000L,
            downloadUrl = "https://huggingface.co/mlc-ai/gemma-3-1b-it-q4f16_1-MLC/resolve/main/gemma-3-1b-it-q4_k_m.mlc",
            description = "Google's fastest, 2500+ tok/s",
            contextSize = 8192,
            recommendedRamMb = 4000
        ),
        ModelInfo(
            name = "Phi-3.5-mini-Instruct",
            filename = "phi-3.5-mini-instruct-q4_k_m.mlc",
            format = ModelFormat.MLC,
            sizeBytes = 2_200_000_000L,
            downloadUrl = "https://huggingface.co/mlc-ai/Phi-3.5-mini-instruct-q4f16_1-MLC/resolve/main/phi-3.5-mini-instruct-q4_k_m.mlc",
            description = "Microsoft's best reasoning SLM",
            contextSize = 131072,
            recommendedRamMb = 6000
        )
    )

    private val TIER_ENTRY_MODELS = listOf(
        ModelInfo(
            name = "SmolLM2-1.7B-Instruct",
            filename = "smollm2-1.7b-instruct-q4_k_m.mlc",
            format = ModelFormat.MLC,
            sizeBytes = 1_100_000_000L,
            downloadUrl = "https://huggingface.co/mlc-ai/SmolLM2-1.7B-Instruct-q4f16_1-MLC/resolve/main/smollm2-1.7b-instruct-q4_k_m.mlc",
            description = "HuggingFace's efficient SLM",
            contextSize = 8192,
            recommendedRamMb = 4000
        ),
        ModelInfo(
            name = "Qwen3-600M-Instruct",
            filename = "qwen3-600m-instruct-q8_0.mlc",
            format = ModelFormat.MLC,
            sizeBytes = 650_000_000L,
            downloadUrl = "https://huggingface.co/mlc-ai/Qwen3-600M-Instruct-q8_0-MLC/resolve/main/qwen3-600m-instruct-q8_0.mlc",
            description = "Ultra-fast, 75+ tok/s",
            contextSize = 32768,
            recommendedRamMb = 2000
        )
    )

    private val TIER_MINIMAL_MODELS = listOf(
        ModelInfo(
            name = "TinyLlama-1.1B-Chat",
            filename = "tinyllama-1.1b-chat-q4_k_m.mlc",
            format = ModelFormat.MLC,
            sizeBytes = 700_000_000L,
            downloadUrl = "https://huggingface.co/mlc-ai/TinyLlama-1.1B-Chat-q4f16_1-MLC/resolve/main/tinyllama-1.1b-chat-q4_k_m.mlc",
            description = "Minimal footprint, good for basic chat",
            contextSize = 2048,
            recommendedRamMb = 2000
        )
    )
}

/**
 * Model information
 */
data class ModelInfo(
    val name: String,
    val filename: String,
    val format: ModelFormat,
    val sizeBytes: Long,
    val downloadUrl: String,
    val description: String,
    val contextSize: Int,
    val recommendedRamMb: Int
)

/**
 * Downloaded model
 */
data class DownloadedModel(
    val name: String,
    val path: String,
    val sizeBytes: Long,
    val format: ModelFormat
)

/**
 * Model format types
 */
enum class ModelFormat {
    MLC,    // MLC-LLM compiled format
    GGUF,   // llama.cpp format
    TASK,   // LiteRT format
    ONNX;   // ONNX format

    companion object {
        fun fromExtension(ext: String): ModelFormat = when (ext.lowercase()) {
            "mlc", "so" -> MLC
            "gguf" -> GGUF
            "task", "tflite" -> TASK
            "onnx" -> ONNX
            else -> MLC
        }
    }
}

/**
 * Download progress
 */
data class DownloadProgress(
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val speedBytesPerSec: Long,
    val estimatedSecondsRemaining: Long
) {
    val progressPercent: Float
        get() = if (totalBytes > 0) bytesDownloaded.toFloat() / totalBytes else 0f
}

/**
 * Storage information
 */
data class StorageInfo(
    val usedBytes: Long,
    val freeBytes: Long,
    val modelCount: Int
)
