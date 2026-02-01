/*
 * Copyright 2025 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.ai.edge.gallery.data

/**
 * Hardware accelerator types for AI inference.
 *
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
