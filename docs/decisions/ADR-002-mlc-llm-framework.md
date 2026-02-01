---
title: "ADR-002: MLC-LLM Framework Selection"
date: "2026-02-01"
status: "Accepted"
author: "Technical Architecture Team"
---

# ADR-002: MLC-LLM Framework Selection

## Status

**Accepted**

## Context

Having decided on on-device inference (ADR-001), we need to select a framework for running LLMs on Android devices. The framework must support:

- Multiple hardware backends (GPU, NPU, CPU)
- Model quantization for size reduction
- Streaming token generation
- Efficient memory usage
- Active maintenance and community

### Options Considered

1. **MLC-LLM (Apache TVM)**
   - Pros: Multi-backend, quantization, Apache 2.0 license, active development
   - Cons: JNI complexity, build system complexity

2. **llama.cpp**
   - Pros: Widely used, CPU-optimized, simple API
   - Cons: Limited GPU support on Android, no NPU support

3. **TensorFlow Lite**
   - Pros: Google's backing, good tooling
   - Cons: Limited LLM optimizations, model conversion complexity

4. **ONNX Runtime**
   - Pros: Standard format, cross-platform
   - Cons: Limited mobile optimizations for LLMs

## Decision

**We will use MLC-LLM (Apache TVM) as our inference framework.**

### Rationale

1. **Multi-Backend Support**: MLC-LLM supports Vulkan, OpenCL, and CPU backends, with NPU support via Qualcomm QNN.

2. **Performance**: Optimized for mobile deployment with kernel fusion and memory planning.

3. **Quantization**: Supports 4-bit and 8-bit quantization, reducing model size by 50-75%.

4. **Streaming**: Native support for streaming token generation via callback mechanisms.

5. **License**: Apache 2.0 license is compatible with our distribution requirements.

## Consequences

### Positive

- Best-in-class mobile LLM performance
- Multiple hardware acceleration options
- Active development and community
- Flexible model compilation pipeline

### Negative

- JNI bridge complexity for Kotlin integration
- Build system requires CMake and specific NDK setup
- Model compilation requires TVM toolchain
- Documentation gaps for advanced features

### Mitigations

| Concern | Mitigation |
|---------|------------|
| JNI Complexity | Well-abstracted LlmEngine interface |
| Build Complexity | Documented build scripts and CI/CD |
| Model Compilation | Pre-compiled models bundled with app |

## Implementation

### Build Configuration

```kotlin
// build.gradle.kts
android {
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
    ndkVersion = "27.2.12479018"
}
```

### JNI Bridge

```kotlin
// MlcLlmEngine.kt
class MlcLlmEngine : LlmEngine {
    private external fun nativeInit(modelPath: String, config: String): Long
    private external fun nativeGenerate(handle: Long, temp: Float, topP: Float): String?
    // ... other native methods
}
```

### Model Deployment

1. Compile model using MLC-LLM Python tools
2. Bundle compiled model in `assets/`
3. Extract on first launch
4. Initialize native runtime with model path

## Related Decisions

- [ADR-001: On-Device Inference](ADR-001-on-device-inference.md)
- [ADR-003: Reactive State Management](ADR-003-reactive-state-management.md)

## References

- MLC-LLM Documentation: https://llm.mlc.ai/
- Apache TVM: https://tvm.apache.org/
- [Architecture Overview](../architecture/overview.md)

---

*Decision recorded: 2026-02-01*  
*Last reviewed: 2026-02-01*
