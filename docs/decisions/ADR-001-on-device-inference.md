---
title: "ADR-001: On-Device Inference Architecture"
date: "2026-02-01"
status: "Accepted"
author: "Technical Architecture Team"
---

# ADR-001: On-Device Inference Architecture

## Status

**Accepted**

## Context

The Nimittam application requires Large Language Model (LLM) inference capabilities. We need to decide between cloud-based inference (sending data to remote servers) versus on-device inference (running models locally on the user's device).

### Constraints

1. **Privacy**: User conversations should remain private
2. **Latency**: Response time should be minimal
3. **Offline Capability**: App should work without internet
4. **Cost**: Avoid ongoing cloud inference costs
5. **Device Requirements**: Limited to capable devices

### Options Considered

1. **Cloud-based Inference**
   - Pros: Unlimited model size, always up-to-date, works on all devices
   - Cons: Privacy concerns, network dependency, latency, ongoing costs

2. **On-Device Inference**
   - Pros: Privacy, no network dependency, low latency, no ongoing costs
   - Cons: Limited model size, higher device requirements, larger app size

3. **Hybrid Approach**
   - Pros: Flexibility, fallback options
   - Cons: Complexity, inconsistent experience, privacy still compromised

## Decision

**We will implement on-device inference exclusively.**

### Rationale

1. **Privacy is Non-Negotiable**: The primary value proposition of Nimittam is privacy-first AI. Cloud inference would compromise this.

2. **User Experience**: On-device inference provides consistent, low-latency responses regardless of network conditions.

3. **Cost Structure**: One-time model embedding vs. ongoing API costs makes the app sustainable.

4. **Technical Feasibility**: Modern mobile devices (8GB+ RAM) can run 0.5B-7B parameter models effectively.

## Consequences

### Positive

- Complete privacy - no data leaves device
- Works offline
- No inference costs
- Low latency (<200ms first token)
- No rate limiting

### Negative

- Larger app size (~500MB-2GB)
- Higher device requirements (8GB+ RAM recommended)
- Limited to smaller models
- Battery and thermal impact
- Model updates require app updates

### Mitigations

| Concern | Mitigation |
|---------|------------|
| App Size | ABI-specific APKs, model compression |
| Device Requirements | Hardware detection, graceful degradation |
| Battery | Adaptive power management, thermal throttling |
| Model Updates | OTA model updates (future consideration) |

## Implementation

### Architecture

```
┌─────────────────────────────────────┐
│           User Interface            │
├─────────────────────────────────────┤
│         MlcLlmEngine (JNI)          │
├─────────────────────────────────────┤
│      MLC-LLM Runtime (Native)       │
├─────────────────────────────────────┤
│   GPU (Vulkan/OpenCL) or CPU/NPU    │
└─────────────────────────────────────┘
```

### Key Components

1. **MlcLlmEngine**: Kotlin wrapper with JNI bridge
2. **Native Runtime**: MLC-LLM compiled for Android
3. **Model Files**: Embedded quantized models
4. **Hardware Detection**: Automatic backend selection

## Related Decisions

- [ADR-002: MLC-LLM Framework](ADR-002-mlc-llm-framework.md)
- [ADR-003: Reactive State Management](ADR-003-reactive-state-management.md)

## References

- [Architecture Overview](../architecture/overview.md)
- [Static Analysis](../analysis/static-analysis.md)
- MLC-LLM Documentation: https://llm.mlc.ai/

---

*Decision recorded: 2026-02-01*  
*Last reviewed: 2026-02-01*
