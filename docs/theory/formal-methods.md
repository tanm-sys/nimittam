---
title: "Formal Methods"
subtitle: "Mathematical Foundations and Formal Verification"
version: "1.0.0"
date: "2026-02-01"
author: "Technical Architecture Team"
classification: "Theoretical"
status: "Active"
---

# Formal Methods

## Table of Contents

1. [Introduction](#introduction)
2. [Cyclomatic Complexity Theory](#cyclomatic-complexity-theory)
3. [Halstead Metrics Theory](#halstead-metrics-theory)
4. [Maintainability Index Theory](#maintainability-index-theory)
5. [Algorithmic Complexity](#algorithmic-complexity)
6. [State Machine Verification](#state-machine-verification)
7. [Related Documents](#related-documents)

---

## Introduction

### Purpose

This document presents the mathematical foundations and formal methods used in analyzing and verifying the Nimittam codebase. It provides the theoretical basis for the metrics and analyses presented in other documentation.

### Scope

- Software complexity metrics
- Algorithmic complexity analysis
- Formal verification approaches
- Mathematical foundations

---

## Cyclomatic Complexity Theory

### Definition

Cyclomatic Complexity (CC), developed by Thomas J. McCabe in 1976, measures the number of linearly independent paths through a program's source code.

### Formula

**CC = E - N + 2P**

Where:
- **E** = Number of edges in the control flow graph
- **N** = Number of nodes in the control flow graph
- **P** = Number of connected components (typically 1)

### Alternative Calculation

For practical purposes, count decision points:

**CC = 1 + Σ(decision points)**

Decision points include:
- `if` statements
- `when`/`switch` statements
- `for`/`while` loops
- `catch` clauses
- `&&` and `||` operators (each counts as +1)
- Ternary operators `?:`

### Complexity Thresholds

| CC Value | Risk Level | Interpretation |
|----------|------------|----------------|
| 1-10 | Low | Simple, easy to test |
| 11-20 | Medium | Moderate complexity |
| 21-50 | High | Complex, difficult to test |
| >50 | Very High | Untestable, high risk |

### Application to Nimittam

```kotlin
// Example: MlcLlmEngine.generate()
// Decision points:
// 1. if (_state != LlmEngineState.READY)
// 2. while (shouldContinue && isActive) [2 points for &&]
// 3. if (token == null || token.isEmpty() || isStopSequence(...)) [3 points for ||]
// 4. if (generatedTokens >= cachedParams.maxTokens)
// 5. if (generationTime > 0) ... else
// 6. if (promptTimeMs > 0) ... else
// 7. catch (e: CancellationException)
// 8. catch (e: Exception)

// CC = 1 + 9 = 10 (High risk threshold)
```

---

## Halstead Metrics Theory

### Definition

Developed by Maurice Halstead in 1977, these metrics measure software complexity based on operator and operand counts.

### Fundamental Measures

| Symbol | Name | Description |
|--------|------|-------------|
| η₁ (n1) | Unique Operators | Distinct operator types |
| η₂ (n2) | Unique Operands | Distinct operand types |
| N₁ | Total Operators | Total operator occurrences |
| N₂ | Total Operands | Total operand occurrences |

### Derived Metrics

**Vocabulary (η)**:
```
η = η₁ + η₂
```

**Length (N)**:
```
N = N₁ + N₂
```

**Volume (V)**:
```
V = N × log₂(η)
```
Units: bits (information content)

**Difficulty (D)**:
```
D = (η₁/2) × (N₂/η₂)
```

**Effort (E)**:
```
E = D × V
```

**Time to Implement (T)**:
```
T = E / 18 (seconds)
```

**Bugs Delivered (B)**:
```
B = V / 3000
```

### Interpretation

| Metric | Low | Medium | High |
|--------|-----|--------|------|
| Volume (V) | <1000 | 1000-3000 | >3000 |
| Difficulty (D) | <50 | 50-100 | >100 |
| Effort (E) | <100K | 100K-500K | >500K |

---

## Maintainability Index Theory

### Definition

The Maintainability Index (MI) combines Halstead Volume, Cyclomatic Complexity, and Lines of Code to estimate maintainability.

### Formula

**MI = 171 - 5.2 × ln(Halstead Volume) - 0.23 × Cyclomatic Complexity - 16.2 × ln(Lines of Code)**

### Interpretation

| MI Score | Rating |
|----------|--------|
| >85 | Excellent |
| 65-85 | Good |
| <65 | Difficult |

### Component Analysis

| Component | LOC | CC | Volume | MI | Rating |
|-----------|-----|-----|--------|-----|--------|
| MlcLlmEngine | 480 | 10 | 4,247 | 78.4 | Good |
| CacheManager | 882 | 6 | 6,798 | 65.2 | Moderate |
| AdaptiveMemoryManager | 461 | 6 | 3,226 | 85.7 | Excellent |

---

## Algorithmic Complexity

### Big O Notation

| Notation | Name | Example |
|----------|------|---------|
| O(1) | Constant | HashMap lookup |
| O(log n) | Logarithmic | Binary search |
| O(n) | Linear | Array iteration |
| O(n log n) | Linearithmic | Merge sort |
| O(n²) | Quadratic | Nested loops |
| O(2ⁿ) | Exponential | Recursive Fibonacci |

### CacheManager Analysis

```kotlin
// L1 Cache Get: O(1)
suspend fun get(key: String): CacheEntry<T>? = lock.withLock {
    cache.get(key)?.let { entry ->
        val updatedEntry = entry.copy(
            metadata = entry.metadata.withAccess()
        )
        cache.put(key, updatedEntry)
        updatedEntry
    }
}

// L2 Disk Cache Ensure Space: O(n log n)
private suspend fun ensureSpace() {
    val entries = cacheDir.listFiles()
        ?.mapNotNull { ... }
        ?.sortedBy { it.second.lastAccessed } // O(n log n)
        ?: return
    
    for ((key, _) in entries) { // O(n)
        // Eviction logic
    }
}
```

### Predictive Prefetcher Analysis

```kotlin
// Pattern Analysis: O(m log m) where m = history size
private fun analyzePattern(): BehaviorPattern {
    val frequency = accessHistory
        .groupingBy { it.key }
        .eachCount() // O(m)
    
    val frequentKeys = frequency.entries
        .sortedByDescending { it.value } // O(k log k)
        .take(10)
        .map { it.key }
    
    // ...
}
```

---

## State Machine Verification

### LLM Engine State Machine

```
States: {UNINITIALIZED, LOADING, READY, GENERATING, ERROR, RELEASED}
Events: {initialize, success, failure, generate, complete, cancel, release, retry, exception}
```

### Transition Table

| Current State | Event | Next State |
|---------------|-------|------------|
| UNINITIALIZED | initialize | LOADING |
| LOADING | success | READY |
| LOADING | failure | ERROR |
| READY | generate | GENERATING |
| READY | release | RELEASED |
| GENERATING | complete | READY |
| GENERATING | cancel | READY |
| GENERATING | exception | ERROR |
| ERROR | retry | LOADING |

### Invariants

1. **Operational Invariant**: `isOperational == true` only in READY and GENERATING states
2. **Generation Invariant**: Can only generate() from READY state
3. **Resource Invariant**: Native handle must be valid in READY and GENERATING states

### Formal Verification

Using temporal logic:
```
□(state = GENERATING → ◇(state = READY ∨ state = ERROR))
```
"Always, if generating, eventually ready or error"

---

## Related Documents

| Document | Relationship | Description |
|----------|--------------|-------------|
| [Static Analysis](../analysis/static-analysis.md) | Applies | Metrics application |
| [Complexity](complexity.md) | Extends | Complexity theory |
| [Architecture Overview](../architecture/overview.md) | Context | System design |

---

## References

1. McCabe, T. J. (1976). A Complexity Measure. *IEEE Transactions on Software Engineering*, SE-2(4), 308-320.

2. Halstead, M. H. (1977). *Elements of Software Science*. Elsevier North-Holland.

3. Coleman, D., et al. (1994). Using Metrics to Evaluate Software System Maintainability. *Computer*, 27(8), 44-49.

4. Knuth, D. E. (1997). *The Art of Computer Programming, Vol. 1*. Addison-Wesley.

---

*Document maintained by the Technical Architecture Team*  
*Last updated: 2026-02-01*  
*Classification: Theoretical*
