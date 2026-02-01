---
title: "Complexity Theory"
subtitle: "Algorithmic Complexity and Performance Analysis"
version: "1.0.0"
date: "2026-02-01"
author: "Technical Architecture Team"
classification: "Theoretical"
status: "Active"
---

# Complexity Theory

## Table of Contents

1. [Introduction](#introduction)
2. [Time Complexity](#time-complexity)
3. [Space Complexity](#space-complexity)
4. [Amortized Analysis](#amortized-analysis)
5. [Cache Complexity](#cache-complexity)
6. [Concurrency Complexity](#concurrency-complexity)
7. [Related Documents](#related-documents)

---

## Introduction

### Purpose

This document analyzes the algorithmic complexity of key operations in the Nimittam application, providing theoretical foundations for performance optimization and scalability planning.

### Complexity Classes

| Class | Description | Example in Nimittam |
|-------|-------------|---------------------|
| O(1) | Constant time | L1 cache lookup |
| O(log n) | Logarithmic | Binary search (not used) |
| O(n) | Linear | Token generation loop |
| O(n log n) | Linearithmic | L2 cache eviction sort |
| O(n²) | Quadratic | Nested message rendering |

---

## Time Complexity

### Cache Operations

#### L1 Memory Cache

```kotlin
// get(): O(1) - HashMap lookup
suspend fun get(key: String): CacheEntry<T>? = lock.withLock {
    cache.get(key)?.let { entry ->
        val updatedEntry = entry.copy(
            metadata = entry.metadata.withAccess()
        )
        cache.put(key, updatedEntry)
        updatedEntry
    }
}

// Operations breakdown:
// - cache.get(): O(1) average case
// - entry.copy(): O(1) object creation
// - cache.put(): O(1) average case
// Total: O(1)
```

#### L2 Disk Cache

```kotlin
// get(): O(n) where n = number of cache files
suspend fun get(key: String): CacheEntry<T>? {
    // 1. List files: O(n)
    val files = cacheDir.listFiles()
    
    // 2. Find matching file: O(n)
    val file = files?.find { it.name == key }
    
    // 3. Deserialize: O(1) for fixed-size metadata
    return file?.let { deserialize(it) }
}

// ensureSpace(): O(n log n)
private suspend fun ensureSpace() {
    // 1. List files: O(n)
    // 2. Map to entries: O(n)
    // 3. Sort by access time: O(n log n)
    val entries = cacheDir.listFiles()
        ?.mapNotNull { file ->
            // ...
        }
        ?.sortedBy { it.second.lastAccessed }
    
    // 4. Evict until under limit: O(n) worst case
    for ((key, _) in entries) {
        if (currentSize < maxSizeBytes * 0.8) break
        delete(key)
    }
}
```

### Token Generation

```kotlin
// generate(): O(t) where t = number of tokens
override fun generate(prompt: String, params: GenerationParams): Flow<GenerationResult> {
    return callbackFlow {
        while (shouldContinue && isActive) { // Loop: O(t)
            val token = nativeGenerate(...) // JNI: O(1)
            
            if (token == null || token.isEmpty() || isStopSequence(...)) {
                shouldContinue = false
            } else {
                generatedTokens++
                trySend(GenerationResult.Token(token)) // O(1)
            }
            
            if (generatedTokens >= cachedParams.maxTokens) {
                shouldContinue = false
            }
        }
    }
}

// isStopSequence(): O(s) where s = number of stop sequences
private fun isStopSequence(text: String, sequences: List<String>): Boolean {
    return sequences.any { text.endsWith(it) } // O(s)
}

// Total complexity: O(t × s)
// With bounded s (typically <10): O(t)
```

### Memory Profile Calculation

```kotlin
// calculateOptimalProfile(): O(1)
fun calculateOptimalProfile(): MemoryProfile {
    val memInfo = ActivityManager.MemoryInfo()
    activityManager.getMemoryInfo(memInfo)
    val totalMemoryMB = memInfo.totalMem / (1024 * 1024)
    
    // Fixed number of comparisons (5 branches)
    return when {
        totalMemoryMB >= 8192 -> MemoryProfile(...)
        totalMemoryMB >= 6144 -> MemoryProfile(...)
        totalMemoryMB >= 4096 -> MemoryProfile(...)
        totalMemoryMB >= 3072 -> MemoryProfile(...)
        else -> MemoryProfile(...)
    }
}
```

---

## Space Complexity

### Cache Memory Usage

```kotlin
// L1 Cache: O(k) where k = max entries
class L1MemoryCache<T> {
    private val cache = LruCache<String, CacheEntry<T>>(100)
    // Space: O(k × average_entry_size)
}

// L2 Cache: O(m) where m = max size in bytes
class L2DiskCache {
    private val maxSizeMB = 100
    // Space: O(m) on disk
}
```

### Token Generation Memory

```kotlin
// Generation memory: O(t) for token accumulation
// where t = max tokens

// Native memory (KV cache): O(t × d × l)
// where:
//   t = sequence length
//   d = embedding dimension
//   l = number of layers
```

### Conversation Storage

```kotlin
// Database: O(n × m) where:
//   n = number of conversations
//   m = average messages per conversation

// In-memory: O(m) for active conversation only
```

---

## Amortized Analysis

### Frame Time Tracking

```kotlin
// recordFrameTime(): O(1) amortized
fun recordFrameTime(frameTimeMs: Float) {
    val metrics = FrameMetrics.fromFrameTime(frameTimeMs)
    frameTimeSamples.offer(metrics) // O(1)
    
    // Occasionally remove old samples
    while (frameTimeSamples.size > MAX_SAMPLES) { // O(k) where k = overflow
        frameTimeSamples.poll() // O(1)
    }
}

// Amortized: O(1) per operation
// Worst case: O(k) when resizing
```

### Cache Eviction

```kotlin
// L1 Cache eviction: O(1) amortized
// Using LinkedHashMap with access order

// Each put/get: O(1)
// Eviction happens automatically on capacity overflow
// Cost of eviction is spread across operations
```

---

## Cache Complexity

### Cache-Oblivious Algorithms

The Nimittam cache system uses cache-aware (not cache-oblivious) algorithms:

| Operation | Cache Behavior |
|-----------|----------------|
| L1 Get | Cache hit: O(1), Cache miss: O(1) + L2 fetch |
| L2 Get | Cache miss: O(n) disk seek + read |
| Sequential read | Prefetching improves effective complexity |

### Memory Hierarchy

```
CPU Registers (fastest, smallest)
    ↓
L1 Cache (32-64KB per core)
    ↓
L2 Cache (256-512KB per core)
    ↓
L3 Cache (4-32MB shared)
    ↓
Main Memory (8-16GB)
    ↓
Disk/SSD (slowest, largest)
```

### Cache-Friendly Patterns

```kotlin
// Good: Sequential access
for (i in 0 until n) {
    process(array[i]) // Cache-friendly
}

// Bad: Random access
for (i in randomIndices) {
    process(array[i]) // Cache-unfriendly
}

// Good: Structure of Arrays for hot data
class CacheFriendlyData {
    val ids = IntArray(n)      // Hot
    val names = Array(n) { "" } // Cold
}
```

---

## Concurrency Complexity

### Lock Contention

```kotlin
// L1 Cache with mutex: O(1) but with contention
suspend fun get(key: String): CacheEntry<T>? = lock.withLock {
    // Contention increases effective latency
    // Under high contention: O(c) where c = number of waiters
}
```

### Lock-Free Operations

```kotlin
// Native handle access: O(1) lock-free
private val nativeHandleRef = AtomicLong(0L)

fun getHandle(): Long = nativeHandleRef.get() // O(1) lock-free
```

### Coroutine Complexity

```kotlin
// Flow collection: O(1) per emission
// But with backpressure handling:

val flow = callbackFlow {
    while (isActive) {
        val token = generateToken()
        trySend(token) // O(1), may suspend if buffer full
    }
}

// Buffer size affects memory: O(buffer_size)
```

---

## Complexity Summary Table

| Operation | Time | Space | Notes |
|-----------|------|-------|-------|
| L1 Cache Get | O(1) | O(1) | HashMap lookup |
| L2 Cache Get | O(n) | O(1) | File listing |
| L2 Eviction | O(n log n) | O(n) | Sorting |
| Token Generation | O(t) | O(t) | t = token count |
| Profile Calculation | O(1) | O(1) | Fixed branches |
| Memory Stats | O(1) | O(1) | System call |
| Frame Recording | O(1)* | O(1) | *Amortized |
| Message List Render | O(m) | O(m) | m = visible messages |

---

## Related Documents

| Document | Relationship | Description |
|----------|--------------|-------------|
| [Formal Methods](formal-methods.md) | Complements | Mathematical foundations |
| [Static Analysis](../analysis/static-analysis.md) | Applies | Complexity metrics |
| [Dynamic Analysis](../analysis/dynamic-analysis.md) | Validates | Runtime measurements |
| [Architecture Overview](../architecture/overview.md) | Context | System design |

---

## References

1. Cormen, T. H., et al. (2009). *Introduction to Algorithms* (3rd ed.). MIT Press.

2. Knuth, D. E. (1997). *The Art of Computer Programming, Vol. 1*. Addison-Wesley.

3. Tarjan, R. E. (1985). Amortized Computational Complexity. *SIAM Journal on Algebraic Discrete Methods*, 6(2), 306-318.

4. Frigo, M., et al. (1999). Cache-Oblivious Algorithms. *FOCS '99*.

---

*Document maintained by the Technical Architecture Team*  
*Last updated: 2026-02-01*  
*Classification: Theoretical*
