---
title: "Traceability Matrices"
subtitle: "Requirements to Implementation Traceability"
version: "1.0.0"
date: "2026-02-01"
author: "Technical Architecture Team"
classification: "IEEE 830-1998"
status: "Active"
---

# Traceability Matrices

## Table of Contents

1. [Introduction](#introduction)
2. [Requirements Overview](#requirements-overview)
3. [Functional Requirements Traceability](#functional-requirements-traceability)
4. [Non-Functional Requirements Traceability](#non-functional-requirements-traceability)
5. [Component to Requirement Mapping](#component-to-requirement-mapping)
6. [Test Coverage Matrix](#test-coverage-matrix)
7. [Design Pattern to Requirement Mapping](#design-pattern-to-requirement-mapping)
8. [Risk to Mitigation Mapping](#risk-to-mitigation-mapping)
9. [Related Documents](#related-documents)

---

## Introduction

### Purpose

This document provides comprehensive traceability matrices mapping requirements to implementation artifacts, test cases, and design elements. It ensures complete coverage of requirements and enables impact analysis for changes.

### Traceability Types

| Type | Description | Direction |
|------|-------------|-----------|
| Forward | Requirements → Implementation | Forward |
| Backward | Implementation → Requirements | Backward |
| Bidirectional | Both directions | Bidirectional |

### Traceability Matrix Legend

| Symbol | Meaning |
|--------|---------|
| ✓ | Direct implementation |
| ○ | Partial implementation |
| - | Not applicable |

---

## Requirements Overview

### Functional Requirements (FR)

| ID | Requirement | Priority |
|----|-------------|----------|
| FR-001 | On-device LLM inference | High |
| FR-002 | Streaming token generation | High |
| FR-003 | Chat history persistence | High |
| FR-004 | Multi-level caching | Medium |
| FR-005 | Hardware backend selection | Medium |
| FR-006 | Memory pressure adaptation | High |
| FR-007 | Thermal throttling response | Medium |
| FR-008 | Battery-aware operation | Medium |

### Non-Functional Requirements (NFR)

| ID | Requirement | Priority |
|----|-------------|----------|
| NFR-001 | Token throughput >15 t/s | High |
| NFR-002 | UI frame time <16.67ms | High |
| NFR-003 | Cold start <2 seconds | Medium |
| NFR-004 | Memory pressure response <100ms | High |
| NFR-005 | Maintainability Index >65 | Medium |
| NFR-006 | Test coverage >80% | Medium |
| NFR-007 | No circular dependencies | High |

---

## Functional Requirements Traceability

### FR-001: On-device LLM Inference

| Artifact Type | Artifact ID | Implementation | Status |
|---------------|-------------|----------------|--------|
| Component | MlcLlmEngine | JNI bridge to MLC-LLM | ✓ |
| Interface | LlmEngine | Abstraction layer | ✓ |
| Pattern | Adapter | JNI adaptation | ✓ |
| Pattern | Singleton | Single engine instance | ✓ |
| Test | LlmEngineTest | Unit tests | ○ |

### FR-002: Streaming Token Generation

| Artifact Type | Artifact ID | Implementation | Status |
|---------------|-------------|----------------|--------|
| Component | MlcLlmEngine | callbackFlow implementation | ✓ |
| Interface | Flow<GenerationResult> | Reactive stream | ✓ |
| Pattern | Observer | StateFlow for UI updates | ✓ |
| Component | ChatScreen | Flow collection | ✓ |
| Test | StreamingTest | Integration tests | ○ |

### FR-003: Chat History Persistence

| Artifact Type | Artifact ID | Implementation | Status |
|---------------|-------------|----------------|--------|
| Component | ChatDatabase | Room database | ✓ |
| Component | ConversationDao | Data access | ✓ |
| Component | MessageDao | Data access | ✓ |
| Repository | ChatHistoryRepository | Repository pattern | ✓ |
| Pattern | Repository | Data abstraction | ✓ |
| Test | ChatRepositoryTest | Database tests | ○ |

### FR-004: Multi-level Caching

| Artifact Type | Artifact ID | Implementation | Status |
|---------------|-------------|----------------|--------|
| Component | CacheManager | Facade for cache layers | ✓ |
| Component | L1MemoryCache | In-memory LRU cache | ✓ |
| Component | L2DiskCache | Disk-based cache | ✓ |
| Pattern | Facade | Unified cache interface | ✓ |
| Pattern | Template Method | Cache operations | ✓ |
| Test | CacheManagerTest | Cache tests | ○ |

### FR-005: Hardware Backend Selection

| Artifact Type | Artifact ID | Implementation | Status |
|---------------|-------------|----------------|--------|
| Component | HardwareDetector | Capability detection | ✓ |
| Component | MlcLlmEngine | Backend selection logic | ✓ |
| Enum | HardwareBackend | Backend types | ✓ |
| Pattern | Strategy | Backend selection | ✓ |
| Test | HardwareDetectorTest | Detection tests | ○ |

### FR-006: Memory Pressure Adaptation

| Artifact Type | Artifact ID | Implementation | Status |
|---------------|-------------|----------------|--------|
| Component | AdaptiveMemoryManager | Pressure monitoring | ✓ |
| Component | MemoryProfile | Adaptive configuration | ✓ |
| Pattern | Observer | Pressure listeners | ✓ |
| Pattern | Strategy | Profile selection | ✓ |
| Test | MemoryPressureTest | Adaptation tests | ○ |

### FR-007: Thermal Throttling Response

| Artifact Type | Artifact ID | Implementation | Status |
|---------------|-------------|----------------|--------|
| Component | ThermalManager | Thermal monitoring | ✓ |
| Enum | ThermalAction | Response actions | ✓ |
| Component | MlcLlmEngine | Throttling integration | ✓ |
| Pattern | Strategy | Action selection | ✓ |
| Test | ThermalManagerTest | Thermal tests | ○ |

### FR-008: Battery-Aware Operation

| Artifact Type | Artifact ID | Implementation | Status |
|---------------|-------------|----------------|--------|
| Component | BatteryOptimizer | Battery monitoring | ✓ |
| Component | MlcLlmEngine | Battery integration | ✓ |
| Pattern | Strategy | Power mode selection | ✓ |
| Test | BatteryOptimizerTest | Battery tests | ○ |

---

## Non-Functional Requirements Traceability

### NFR-001: Token Throughput >15 t/s

| Metric | Target | Implementation | Verification |
|--------|--------|----------------|--------------|
| Throughput | >15 t/s | GPU acceleration | PerformanceTest |
| Backend | Vulkan/OpenCL | MlcLlmEngine | Benchmark |
| Optimization | KV-cache | Native layer | Profile |

**Traceability Chain:**
```
NFR-001 → MlcLlmEngine → nativeGenerate() → GPU Backend → Benchmark Tests
```

### NFR-002: UI Frame Time <16.67ms

| Metric | Target | Implementation | Verification |
|--------|--------|----------------|--------------|
| Frame time | <16.67ms | LazyColumn optimization | FrameMetrics |
| Jank rate | <5% | Smart scrolling | PerformanceMonitor |
| Recomposition | Minimal | Key-based items | Compose Profiler |

**Traceability Chain:**
```
NFR-002 → ChatScreen → LazyColumn → FrameMetrics → PerformanceMonitor
```

### NFR-003: Cold Start <2 seconds

| Metric | Target | Implementation | Verification |
|--------|--------|----------------|--------------|
| App launch | <500ms | Async initialization | StartupTracer |
| Model load | Background | Async extraction | StartupTracer |
| First frame | <400ms | Compose optimization | StartupTracer |

**Traceability Chain:**
```
NFR-003 → GalleryApplication → Async init → StartupTracer → Timing Tests
```

### NFR-004: Memory Pressure Response <100ms

| Metric | Target | Implementation | Verification |
|--------|--------|----------------|--------------|
| Detection | 5s polling | AdaptiveMemoryManager | Unit tests |
| Profile switch | <10ms | In-memory update | Unit tests |
| Emergency trim | <100ms | Cache clearing | Integration tests |

**Traceability Chain:**
```
NFR-004 → AdaptiveMemoryManager → MemoryProfile → CacheManager → Response Tests
```

### NFR-005: Maintainability Index >65

| Component | MI Score | Target | Status |
|-----------|----------|--------|--------|
| MlcLlmEngine | 78.4 | >65 | ✓ |
| CacheManager | 65.2 | >65 | ✓ |
| AdaptiveMemoryManager | 85.7 | >65 | ✓ |
| PerformanceMonitor | 79.3 | >65 | ✓ |
| ChatScreen | 82.1 | >65 | ✓ |

**Traceability Chain:**
```
NFR-005 → Static Analysis → Halstead/Complexity → Maintainability Index
```

### NFR-006: Test Coverage >80%

| Layer | Coverage | Target | Status |
|-------|----------|--------|--------|
| Data Layer | 85% | >80% | ✓ |
| Domain Layer | 78% | >80% | ○ |
| Service Layer | 82% | >80% | ✓ |
| UI Layer | 45% | >80% | ○ |

**Traceability Chain:**
```
NFR-006 → Test Suite → Coverage Report → Gap Analysis
```

### NFR-007: No Circular Dependencies

| Check | Tool | Result | Status |
|-------|------|--------|--------|
| Import cycles | Detekt | 0 cycles | ✓ |
| Module cycles | Gradle | 0 cycles | ✓ |
| Package cycles | Structure101 | 0 cycles | ✓ |

**Traceability Chain:**
```
NFR-007 → Dependency Analysis → Cycle Detection → Clean Architecture
```

---

## Component to Requirement Mapping

### Component Matrix

| Component | FR-001 | FR-002 | FR-003 | FR-004 | FR-005 | FR-006 | FR-007 | FR-008 |
|-----------|--------|--------|--------|--------|--------|--------|--------|--------|
| MlcLlmEngine | ✓ | ✓ | - | - | ✓ | ✓ | ✓ | ✓ |
| CacheManager | - | - | - | ✓ | - | ✓ | - | - |
| AdaptiveMemoryManager | - | - | - | - | - | ✓ | - | - |
| PerformanceMonitor | - | - | - | - | - | ✓ | ✓ | - |
| ThermalManager | - | - | - | - | - | - | ✓ | - |
| BatteryOptimizer | - | - | - | - | - | - | - | ✓ |
| ChatHistoryRepository | - | - | ✓ | - | - | - | - | - |
| ChatDatabase | - | - | ✓ | - | - | - | - | - |
| HardwareDetector | - | - | - | ✓ | ✓ | - | - | - |
| ChatScreen | - | ✓ | ✓ | - | - | - | - | - |

---

## Test Coverage Matrix

### Unit Tests

| Component | Test Class | Coverage | Status |
|-----------|------------|----------|--------|
| CacheManager | CacheManagerTest | 85% | ✓ |
| MemoryPool | MemoryPoolTest | 78% | ○ |
| AdaptiveMemoryManager | MemoryManagerTest | 82% | ✓ |
| PerformanceMonitor | PerformanceMonitorTest | 75% | ○ |
| ChatHistoryRepository | ChatRepositoryTest | 88% | ✓ |

### Integration Tests

| Feature | Test Class | Coverage | Status |
|---------|------------|----------|--------|
| Token Streaming | StreamingIntegrationTest | 70% | ○ |
| Cache Operations | CacheIntegrationTest | 80% | ✓ |
| Memory Adaptation | MemoryIntegrationTest | 75% | ○ |
| Database Operations | DatabaseIntegrationTest | 85% | ✓ |

### UI Tests

| Screen | Test Class | Coverage | Status |
|--------|------------|----------|--------|
| ChatScreen | ChatScreenTest | 60% | ○ |
| HistoryScreen | HistoryScreenTest | 55% | ○ |
| SettingsScreen | SettingsScreenTest | 50% | ○ |

### Chaos Tests

| Scenario | Test Class | Status |
|----------|------------|--------|
| Memory pressure | MemoryChaosTest | ✓ |
| Thermal throttling | ThermalChaosTest | ○ |
| Battery drain | BatteryChaosTest | ○ |
| Network failure | NetworkChaosTest | - |

---

## Design Pattern to Requirement Mapping

### Pattern Implementation Matrix

| Pattern | FR-001 | FR-002 | FR-003 | FR-004 | FR-005 | FR-006 | FR-007 | FR-008 |
|---------|--------|--------|--------|--------|--------|--------|--------|--------|
| Singleton | ✓ | - | - | ✓ | - | ✓ | - | - |
| Dependency Injection | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| Object Pool | ✓ | - | - | ✓ | - | ✓ | - | - |
| Adapter | ✓ | - | - | - | - | - | - | - |
| Facade | - | - | - | ✓ | - | ✓ | - | - |
| Proxy | - | - | - | ✓ | - | ✓ | - | - |
| Observer | - | ✓ | - | - | - | ✓ | ✓ | - |
| Strategy | ✓ | - | - | - | ✓ | ✓ | ✓ | ✓ |
| Command | - | - | - | ✓ | - | - | - | - |
| Template Method | - | - | - | ✓ | - | - | - | - |
| State | ✓ | - | - | - | - | - | - | - |
| Repository | - | - | ✓ | - | - | - | - | - |
| MVI | - | ✓ | - | - | - | - | - | - |

---

## Risk to Mitigation Mapping

### Risk Assessment Matrix

| Risk ID | Description | Probability | Impact | Mitigation | Status |
|---------|-------------|-------------|--------|------------|--------|
| R-001 | Memory exhaustion | Medium | High | AdaptiveMemoryManager | ✓ |
| R-002 | Thermal throttling | High | Medium | ThermalManager | ✓ |
| R-003 | Battery drain | High | Medium | BatteryOptimizer | ✓ |
| R-004 | JNI crashes | Low | High | Error boundaries | ✓ |
| R-005 | Cache corruption | Low | Medium | Checksum validation | ○ |
| R-006 | Database migration | Medium | Low | Migration tests | ✓ |
| R-007 | Model incompatibility | Low | High | Version checking | ✓ |

### Mitigation Implementation

| Risk | Component | Pattern | Test Coverage |
|------|-----------|---------|---------------|
| R-001 | AdaptiveMemoryManager | Observer, Strategy | 82% |
| R-002 | ThermalManager | Strategy | 70% |
| R-003 | BatteryOptimizer | Strategy | 75% |
| R-004 | CrashHandler | Singleton | 60% |
| R-005 | CacheManager | Facade, Command | 85% |
| R-006 | ChatDatabase | Repository | 88% |
| R-007 | ModelManager | Adapter | 65% |

---

## Change Impact Analysis

### Impact Matrix

| If Changed | Affects Requirements | Affected Components | Test Impact |
|------------|---------------------|---------------------|-------------|
| LlmEngine | FR-001, FR-002 | All UI, Services | High |
| CacheManager | FR-004 | Data Layer | Medium |
| AdaptiveMemoryManager | FR-006 | All Services | Medium |
| ChatDatabase | FR-003 | Repository Layer | Medium |
| PerformanceMonitor | NFR-002 | All UI | Low |
| Theme | - | UI Only | Low |

---

## Related Documents

| Document | Relationship | Description |
|----------|--------------|-------------|
| [Interfaces](interfaces.md) | Implements | API contracts |
| [Data Models](data-models.md) | Defines | Data structures |
| [Static Analysis](../analysis/static-analysis.md) | Validates | Quality metrics |
| [Architecture Overview](../architecture/overview.md) | Context | High-level design |
| [Glossary](../references/glossary.md) | Reference | Terminology |

---

*Document maintained by the Technical Architecture Team*  
*Last updated: 2026-02-01*  
*Classification: IEEE 830-1998*
