# On-Device LLM Integration Guide

## Overview

This project implements a high-performance on-device LLM (Large Language Model) inference engine for Android using **MLC-LLM** with **Vulkan GPU acceleration**.

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                    Gallery App (Kotlin/Compose)                  │
├─────────────────────────────────────────────────────────────────┤
│  LlmChatScreen  │  LlmChatViewModel  │  Chat History (Room DB)  │
├─────────────────────────────────────────────────────────────────┤
│                    LLM Abstraction Layer                        │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │  LlmEngine (interface)                                    │   │
│  │  └─> MlcLlmEngine (Vulkan/OpenCL GPU acceleration)       │   │
│  └──────────────────────────────────────────────────────────┘   │
├─────────────────────────────────────────────────────────────────┤
│                    Support Layer                                 │
│  ┌──────────────┬──────────────┬──────────────────────────┐    │
│  │ ModelManager │ HardwareDetector │  JNI Native Bridge    │    │
│  └──────────────┴──────────────┴──────────────────────────┘    │
├─────────────────────────────────────────────────────────────────┤
│              Hardware Acceleration                               │
│  ┌──────────┬──────────┬──────────┬──────────┐                  │
│  │  Vulkan  │  OpenCL  │   NPU    │   CPU    │                  │
│  │   GPU    │   GPU    │ Hexagon  │  ARM64   │                  │
│  └──────────┴──────────┴──────────┴──────────┘                  │
└─────────────────────────────────────────────────────────────────┘
```

## Key Components

### 1. LlmEngine (Interface)
- `LlmEngine.kt` - Core abstraction for LLM inference
- Supports multiple backends (Vulkan, OpenCL, CPU, NPU)
- Streaming token generation via Kotlin Flow

### 2. MlcLlmEngine (Implementation)
- `engine/MlcLlmEngine.kt` - MLC-LLM integration
- Uses JNI to communicate with native C++ code
- Automatic backend selection based on device capabilities

### 3. ModelManager
- `ModelManager.kt` - Model download and storage management
- Resume-capable downloads
- Device-appropriate model recommendations

### 4. HardwareDetector
- `HardwareDetector.kt` - Device capability detection
- GPU vendor/model identification
- Snapdragon generation detection for NPU support

### 5. LlmChatViewModel
- `LlmChatViewModel.kt` - Chat state management
- Integrates all LLM components
- Handles conversation history

### 6. LlmChatScreen
- `ui/chat/LlmChatScreen.kt` - Compose UI
- Model selection, download progress
- Streaming chat interface with metrics

## Setup Instructions

### Prerequisites

1. **Android Studio** Ladybug (2024.2.1+)
2. **Android NDK** r26+ (r27 recommended)
3. **CMake** 3.22+
4. **Device**: Android 12+ with ARM64 CPU

### Step 1: Install NDK and CMake

In Android Studio:
1. Go to **SDK Manager** → **SDK Tools**
2. Install:
   - NDK (Side by side) - version 27.2.12479018
   - CMake - version 3.22.1+

### Step 2: Download MLC-LLM Libraries (Optional for Full Integration)

For full GPU-accelerated inference, you need to build MLC-LLM:

```bash
# Clone MLC-LLM
git clone --recursive https://github.com/mlc-ai/mlc-llm.git
cd mlc-llm

# Build for Android
./scripts/build_android.sh

# Copy libraries to your project
cp build/lib/arm64-v8a/libtvm_runtime.so \
   ../gallery-1.0.9/Android/src/app/src/main/jniLibs/arm64-v8a/

cp build/lib/arm64-v8a/libmlc_llm.so \
   ../gallery-1.0.9/Android/src/app/src/main/jniLibs/arm64-v8a/
```

### Step 3: Download a Model

Recommended models (in order of quality/size tradeoff):

| Model | Size | RAM Needed | Use Case |
|-------|------|------------|----------|
| Gemma 3 1B | 529MB | 4GB | Fastest, basic tasks |
| Qwen3-4B | 2.6GB | 8GB | Best quality/speed balance |
| Llama 3.2 3B | 2GB | 6GB | Good general purpose |
| Phi-3.5-mini | 2.2GB | 6GB | Best reasoning |

Download pre-compiled MLC models from Hugging Face:
```bash
# Example: Gemma 3 1B
huggingface-cli download mlc-ai/gemma-3-1b-it-q4f16_1-MLC \
    --local-dir ./models/gemma-3-1b
```

Or use the in-app model downloader.

### Step 4: Build and Run

```bash
cd Android/src
./gradlew assembleDebug

# Install on device
adb install -r app/build/outputs/apk/debug/app-arm64-v8a-debug.apk
```

## Usage

### Basic Chat Integration

```kotlin
@Composable
fun MyScreen() {
    // Navigate to LLM chat
    LlmChatScreen(
        onNavigateBack = { /* handle back */ }
    )
}
```

### Programmatic Usage

```kotlin
@HiltViewModel
class MyViewModel @Inject constructor(
    private val engine: MlcLlmEngine,
    private val modelManager: ModelManager,
    private val hardwareDetector: HardwareDetector
) : ViewModel() {

    fun chat(prompt: String) {
        viewModelScope.launch {
            // Get optimal config for device
            val config = hardwareDetector.getOptimalConfig(modelSizeBytes = 2_000_000_000L)
            
            // Initialize engine
            engine.initialize("/path/to/model.mlc", config)
            
            // Generate response
            engine.generate(prompt).collect { result ->
                when (result) {
                    is GenerationResult.Token -> {
                        // Handle streaming token
                        print(result.text)
                    }
                    is GenerationResult.Complete -> {
                        println("\n\nSpeed: ${result.metrics.tokensPerSecond} tok/s")
                    }
                    is GenerationResult.Error -> {
                        println("Error: ${result.message}")
                    }
                }
            }
        }
    }
}
```

## Performance Benchmarks

Expected performance on different devices:

| Device | SoC | Backend | Model | Tokens/s |
|--------|-----|---------|-------|----------|
| Pixel 9 Pro | Tensor G4 | Vulkan | Gemma 3 1B | 60-80 |
| Samsung S24 Ultra | SD 8 Gen 3 | Vulkan | Qwen3-4B | 40-50 |
| OnePlus 12 | SD 8 Gen 3 | OpenCL | Llama 3.2 3B | 35-45 |
| Pixel 8 | Tensor G3 | Vulkan | Phi-3.5-mini | 25-35 |

## Troubleshooting

### "Vulkan not available"
- Ensure device supports Vulkan (Android 7.0+ with GPU driver support)
- Fall back to OpenCL or CPU backend

### "Model failed to load"
- Check available RAM (need ~2x model size)
- Try a smaller quantization (Q4 instead of Q8)
- Ensure model format matches engine (MLC vs GGUF)

### Slow inference
- Enable GPU offloading (gpuLayers > 0)
- Use Vulkan backend instead of OpenCL
- Reduce context size
- Use smaller model or higher quantization

## File Structure

```
llm/
├── LlmEngine.kt           # Core interface and types
├── LlmChatViewModel.kt    # ViewModel for chat
├── ModelManager.kt        # Model download/management
├── HardwareDetector.kt    # Device capability detection
└── engine/
    └── MlcLlmEngine.kt    # MLC-LLM implementation

ui/chat/
└── LlmChatScreen.kt       # Compose chat UI

di/
└── LlmModule.kt           # Hilt dependency injection

cpp/
├── CMakeLists.txt         # Native build config
└── mlc_llm_jni.cpp        # JNI bridge
```

## License

MPL-2.0 - See LICENSE file for details.
