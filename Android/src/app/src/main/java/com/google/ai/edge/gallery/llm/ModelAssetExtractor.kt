/*
 * Copyright 2025 Tanmay Patil
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.google.ai.edge.gallery.llm

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Extracts bundled model weights from APK assets to the app's files directory.
 * 
 * MLC-LLM requires model weights to be on the filesystem (not in compressed APK assets),
 * so we extract them on first launch.
 */
object ModelAssetExtractor {
    private const val TAG = "ModelAssetExtractor"
    
    // Model ID must match the bundled model directory name in assets
    const val MODEL_ID = "Qwen2.5-0.5B-Instruct-q4f16_1-MLC"
    const val MODEL_LIB = "qwen2_q4f16_1_dbc9845947d563a3c13bf93ebf315c83"
    
    // Config file that indicates a valid extraction
    private const val CONFIG_FILE = "mlc-chat-config.json"
    
    /**
     * Extract model from assets if not already extracted.
     * 
     * @param context Application context
     * @param onProgress Optional callback for progress updates (0-100)
     * @return Absolute path to the extracted model directory
     */
    suspend fun extractModelIfNeeded(
        context: Context,
        onProgress: ((Int, String) -> Unit)? = null
    ): String = withContext(Dispatchers.IO) {
        val modelDir = File(context.filesDir, MODEL_ID)
        val configFile = File(modelDir, CONFIG_FILE)
        
        // Check if already extracted
        if (configFile.exists()) {
            Log.i(TAG, "Model already extracted at: ${modelDir.absolutePath}")
            onProgress?.invoke(100, "Model ready")
            return@withContext modelDir.absolutePath
        }
        
        Log.i(TAG, "Extracting model from assets to: ${modelDir.absolutePath}")
        onProgress?.invoke(0, "Preparing model extraction...")
        
        try {
            // Create model directory
            if (!modelDir.exists()) {
                modelDir.mkdirs()
            }
            
            // List all files in the model asset directory
            val assetFiles = context.assets.list(MODEL_ID) ?: emptyArray()
            if (assetFiles.isEmpty()) {
                throw IllegalStateException("No model files found in assets/$MODEL_ID")
            }
            
            Log.i(TAG, "Found ${assetFiles.size} files to extract")
            
            var extractedCount = 0
            val totalFiles = assetFiles.size
            
            for (filename in assetFiles) {
                val assetPath = "$MODEL_ID/$filename"
                val outFile = File(modelDir, filename)
                
                // Skip if already exists and has content
                if (outFile.exists() && outFile.length() > 0) {
                    extractedCount++
                    continue
                }
                
                Log.d(TAG, "Extracting: $filename")
                
                context.assets.open(assetPath).use { input ->
                    FileOutputStream(outFile).use { output ->
                        input.copyTo(output, bufferSize = 8192)
                    }
                }
                
                extractedCount++
                val progress = (extractedCount * 100) / totalFiles
                onProgress?.invoke(progress, "Extracting: $filename")
            }
            
            // Verify extraction
            if (!configFile.exists()) {
                throw IllegalStateException("Model extraction failed: $CONFIG_FILE not found")
            }
            
            Log.i(TAG, "Model extraction complete: ${assetFiles.size} files")
            onProgress?.invoke(100, "Model ready")
            
            modelDir.absolutePath
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract model", e)
            // Clean up partial extraction
            modelDir.deleteRecursively()
            throw e
        }
    }
    
    /**
     * Check if the model is already extracted and ready.
     */
    fun isModelReady(context: Context): Boolean {
        val modelDir = File(context.filesDir, MODEL_ID)
        val configFile = File(modelDir, CONFIG_FILE)
        return configFile.exists()
    }
    
    /**
     * Get the path to the extracted model directory.
     * Returns null if model is not extracted.
     */
    fun getModelPath(context: Context): String? {
        val modelDir = File(context.filesDir, MODEL_ID)
        val configFile = File(modelDir, CONFIG_FILE)
        return if (configFile.exists()) modelDir.absolutePath else null
    }
    
    /**
     * Delete the extracted model to free space.
     */
    fun deleteExtractedModel(context: Context): Boolean {
        val modelDir = File(context.filesDir, MODEL_ID)
        return if (modelDir.exists()) {
            val deleted = modelDir.deleteRecursively()
            Log.i(TAG, "Deleted extracted model: $deleted")
            deleted
        } else {
            true
        }
    }
    
    /**
     * Get the size of the extracted model in bytes.
     */
    fun getExtractedModelSize(context: Context): Long {
        val modelDir = File(context.filesDir, MODEL_ID)
        if (!modelDir.exists()) return 0
        
        return modelDir.walkTopDown()
            .filter { it.isFile }
            .sumOf { it.length() }
    }
}
