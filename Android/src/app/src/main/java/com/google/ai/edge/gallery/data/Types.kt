/*
 * Copyright 2025 Tanmay Patil
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.google.ai.edge.gallery.data

/**
 * Hardware accelerator types for AI inference.
 * Priority order for automatic selection: GPU > CPU (NPU requires Early Access)
 * - GPU: Graphics Processing Unit (best balance of speed and compatibility)
 * - CPU: Central Processing Unit (universal fallback)
 * - NPU: Neural Processing Unit (requires Google AI Edge Early Access program)
 *        Sign up at: https://ai.google.dev/edge/litert/next/npu
 *        When available: 22x faster prefill, 30x energy savings
 */
enum class Accelerator(val label: String, val priority: Int) {
  CPU(label = "CPU", priority = 0),
  GPU(label = "GPU", priority = 2),
  NPU(label = "NPU", priority = 1);  // Lower priority until Early Access libraries available

  companion object {
    /**
     * Get the accelerator with highest priority from available list.
     * Note: NPU is deprioritized until Early Access libraries are detected.
     */
    fun getPreferred(available: List<Accelerator>): Accelerator {
      return available.maxByOrNull { it.priority } ?: GPU
    }

    /**
     * Get accelerator by label, with GPU as default fallback.
     */
    fun fromLabel(label: String): Accelerator {
      return entries.find { it.label == label } ?: GPU
    }

    /**
     * Get fallback chain for a given accelerator.
     * Returns a list of accelerators to try in order.
     * NPU falls back to GPU first since both use similar delegation.
     */
    fun getFallbackChain(preferred: Accelerator): List<Accelerator> {
      return when (preferred) {
        NPU -> listOf(NPU, GPU, CPU)
        GPU -> listOf(GPU, CPU)
        CPU -> listOf(CPU)
      }
    }
  }
}
