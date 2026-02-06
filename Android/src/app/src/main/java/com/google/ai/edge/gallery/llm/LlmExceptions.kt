/*
 * Copyright 2025 Tanmay Patil
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.google.ai.edge.gallery.llm

/**
 * Exception thrown when available memory is insufficient for model extraction or loading.
 * 
 * This is a recoverable error - user can free up memory or use a smaller model.
 */
class InsufficientMemoryException(message: String) : Exception(message) {
    companion object {
        private const val serialVersionUID = 1L
    }
}

/**
 * Exception thrown when available storage space is insufficient for model download or extraction.
 * 
 * This is a recoverable error - user can free up storage space or use external storage.
 */
class InsufficientStorageException(message: String) : Exception(message) {
    companion object {
        private const val serialVersionUID = 1L
    }
}

/**
 * Exception thrown when native library fails to load or initialize.
 * 
 * This is typically caused by:
 * - Architecture mismatch (arm64-v8a vs armeabi-v7a)
 * - Corrupted library file
 * - Missing native dependencies
 * - Incompatible Android version
 * - 16KB page size incompatibility on Android 15+
 */
class NativeLibraryException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    companion object {
        private const val serialVersionUID = 1L
    }
    
    /**
     * Get user-friendly error message based on common causes
     */
    fun getUserFriendlyMessage(): String {
        val msg = message ?: ""
        return when {
            msg.contains("dlopen failed") -> 
                "Failed to load native library. This may be due to device incompatibility. " +
                "Please check if your device is supported or try updating the app."
            msg.contains("bad ELF magic") ->
                "Native library is corrupted or has wrong architecture. " +
                "Please reinstall the app or try clearing app data."
            msg.contains("UnsatisfiedLinkError") ->
                "Missing native library dependency. " +
                "This could indicate a device compatibility issue. " +
                "Please report this issue to support."
            msg.contains("CL_INVALID_WORK_GROUP_SIZE", ignoreCase = true) ->
                "GPU driver incompatibility detected. " +
                "Please try using CPU mode or check for device driver updates."
            msg.contains("CL_OUT_OF_RESOURCES", ignoreCase = true) ->
                "Not enough GPU memory. " +
                "Please try CPU mode, reduce model size, or close other apps."
            else ->
                "Native library error: $msg"
        }
    }
}

/**
 * Exception thrown when GPU backend initialization fails.
 * 
 * This allows fallback to CPU backend instead of crashing.
 */
class GpuInitializationException(message: String, val backendType: String, cause: Throwable? = null) : Exception(message, cause) {
    companion object {
        private const val serialVersionUID = 1L
    }
}

/**
 * Exception thrown when model file is corrupted or invalid.
 */
class ModelCorruptionException(message: String) : Exception(message) {
    companion object {
        private const val serialVersionUID = 1L
    }
}

/**
 * Exception thrown when device is not compatible with required features.
 */
class DeviceIncompatibleException(message: String, val capability: String) : Exception(message) {
    companion object {
        private const val serialVersionUID = 1L
    }
}
