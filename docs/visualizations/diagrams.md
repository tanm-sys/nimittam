---
title: "System Diagrams"
subtitle: "Mermaid Diagrams for Architecture Visualization"
version: "1.0.0"
date: "2026-02-01"
author: "Technical Architecture Team"
classification: "ISO/IEC/IEEE 42010"
status: "Active"
---

# System Diagrams

## Table of Contents

1. [Architecture Diagrams](#architecture-diagrams)
2. [Sequence Diagrams](#sequence-diagrams)
3. [State Diagrams](#state-diagrams)
4. [Component Diagrams](#component-diagrams)
5. [Data Flow Diagrams](#data-flow-diagrams)
6. [Related Documents](#related-documents)

---

## Architecture Diagrams

### System Context Diagram

```mermaid
graph TB
    subgraph "Nimittam System"
        APP[Nimittam App]
    end
    
    subgraph "External Systems"
        USER[User]
        OS[Android OS]
        GPU[GPU/Vulkan]
        NPU[Hexagon NPU]
    end
    
    subgraph "Embedded Resources"
        MODEL[Qwen2.5 Model]
        CACHE[Local Cache]
    end
    
    USER -->|Text/Voice Input| APP
    APP -->|Display Output| USER
    APP -->|Memory Stats| OS
    APP -->|Inference| GPU
    APP -->|NPU Acceleration| NPU
    APP -->|Load| MODEL
    APP -->|Read/Write| CACHE
```

### Layered Architecture

```mermaid
graph TB
    subgraph "Presentation Layer"
        UI[UI Components]
        SCREEN[Screens]
        THEME[Theme System]
    end
    
    subgraph "Domain Layer"
        LLM[LLM Engine]
        MM[Model Manager]
        HD[Hardware Detector]
    end
    
    subgraph "Service Layer"
        PERF[Performance Monitor]
        MEM[Memory Manager]
        CACHE[Cache Manager]
    end
    
    subgraph "Data Layer"
        DB[(Room Database)]
        DS[DataStore]
        FS[File System]
    end
    
    UI --> SCREEN
    SCREEN --> LLM
    SCREEN --> PERF
    LLM --> MM
    LLM --> HD
    LLM --> MEM
    PERF --> MEM
    MEM --> CACHE
    CACHE --> FS
    LLM --> DB
    SCREEN --> DS
```

### Module Hierarchy

```mermaid
graph TB
    subgraph "com.google.ai.edge.gallery"
        APP[GalleryApplication]
        
        subgraph "ui"
            UI_COMP[components]
            UI_NAV[navigation]
            UI_SCREEN[screens]
            UI_THEME[theme]
        end
        
        subgraph "data"
            DATA_CACHE[cache]
            DATA_DB[db]
            DATA_REPO[repository]
        end
        
        subgraph "llm"
            LLM_ENGINE[engine]
            LLM_CORE[core]
        end
        
        subgraph "performance"
            PERF_MON[monitor]
            PERF_RUM[rum]
        end
        
        subgraph "util"
            UTIL_MEM[memory]
        end
        
        subgraph "di"
            DI_MODULES[modules]
        end
    end
    
    APP --> UI_SCREEN
    APP --> LLM_ENGINE
    APP --> PERF_MON
    UI_SCREEN --> UI_COMP
    UI_SCREEN --> UI_THEME
    LLM_ENGINE --> DATA_CACHE
    LLM_ENGINE --> UTIL_MEM
    PERF_MON --> UTIL_MEM
```

---

## Sequence Diagrams

### Token Generation Flow

```mermaid
sequenceDiagram
    actor User
    participant ChatScreen as ChatScreen
    participant ViewModel as ChatViewModel
    participant MlcLlm as MlcLlmEngine
    participant JNI as Native Layer
    participant GPU as GPU Backend

    User->>ChatScreen: Enter prompt & send
    ChatScreen->>ViewModel: sendMessage(prompt)
    ViewModel->>MlcLlm: generate(prompt, params)
    
    activate MlcLlm
    MlcLlm->>MlcLlm: callbackFlow { ... }
    MlcLlm->>JNI: nativePrompt(handle, prompt)
    activate JNI
    JNI->>GPU: Tokenize prompt
    GPU-->>JNI: promptTokens count
    JNI-->>MlcLlm: return token count
    deactivate JNI
    
    loop Token Generation
        MlcLlm->>JNI: nativeGenerate(handle, ...)
        activate JNI
        JNI->>GPU: Run inference step
        GPU-->>JNI: Next token
        JNI-->>MlcLlm: return token string
        deactivate JNI
        
        MlcLlm->>ViewModel: trySend(GenerationResult.Token)
        ViewModel->>ViewModel: Update message buffer
        ViewModel->>ChatScreen: _messages.value = updatedList
        ChatScreen->>ChatScreen: Recompose with new text
    end
    
    MlcLlm->>ViewModel: trySend(GenerationResult.Complete)
    deactivate MlcLlm
    ViewModel->>ChatScreen: Final state update
```

### Memory Pressure Adaptation

```mermaid
sequenceDiagram
    participant AMM as AdaptiveMemoryManager
    participant ActivityMgr as ActivityManager
    participant Listeners as MemoryPressureListeners
    participant CacheMgr as CacheManager
    participant PoolMgr as MemoryPoolManager

    activate AMM
    loop Every 5 seconds
        AMM->>ActivityMgr: getMemoryInfo(memInfo)
        ActivityMgr-->>AMM: totalMem, availMem, lowMemory
        
        AMM->>AMM: Calculate usagePercent
        AMM->>AMM: Determine MemoryPressure level
        
        alt Pressure Level Changed
            AMM->>AMM: adjustProfileForPressure()
            AMM->_currentProfile: emit(newProfile)
            
            AMM->>Listeners: onMemoryPressureChanged(pressure, profile)
            
            alt CRITICAL Pressure
                AMM->>CacheMgr: trimMemory()
                AMM->>PoolMgr: clearAllPools()
                AMM->>AMM: System.gc()
            end
        end
        
        AMM->>AMM: delay(5000ms)
    end
    deactivate AMM
```

### Cache Resolution Flow

```mermaid
sequenceDiagram
    participant Client as Cache Client
    participant CacheMgr as CacheManager
    participant L1 as L1MemoryCache
    participant L2 as L2DiskCache
    participant Source as Data Source

    Client->>CacheMgr: get(key)
    
    CacheMgr->>L1: get(key)
    alt L1 Hit
        L1-->>CacheMgr: CacheEntry(value, metadata)
        CacheMgr->>CacheMgr: updateStats(hit)
        CacheMgr-->>Client: return value
    else L1 Miss
        L1-->>CacheMgr: null
        
        CacheMgr->>L2: get(key)
        alt L2 Hit
            L2-->>CacheMgr: CacheEntry(value, metadata)
            CacheMgr->>L1: put(key, value)
            CacheMgr->>CacheMgr: updateStats(hit)
            CacheMgr-->>Client: return value
        else L2 Miss
            L2-->>CacheMgr: null
            CacheMgr->>Source: fetch(key)
            Source-->>CacheMgr: freshData
            
            par Parallel Write
                CacheMgr->>L1: put(key, freshData)
                CacheMgr->>L2: put(key, freshData)
            end
            
            CacheMgr->>CacheMgr: updateStats(miss)
            CacheMgr-->>Client: return freshData
        end
    end
```

### UI State Flow

```mermaid
sequenceDiagram
    actor User
    participant ChatScreen as ChatScreen
    participant ViewModel as ChatViewModel
    participant StateFlow as _messages: StateFlow
    participant Compose as Compose Runtime

    User->>ChatScreen: Type message
    ChatScreen->>ChatScreen: inputText state update
    
    User->>ChatScreen: Click Send
    ChatScreen->>ViewModel: onSendMessage(inputText)
    
    ViewModel->>ViewModel: Create ChatMessage
    ViewModel->>StateFlow: _messages.value = updatedList
    
    StateFlow->>Compose: State change notification
    Compose->>Compose: Schedule recomposition
    
    Note over Compose: Next frame (16ms budget)
    Compose->>ChatScreen: Recompose with new message
    ChatScreen->>ChatScreen: LazyColumn items update
    ChatScreen->>ChatScreen: Auto-scroll to bottom
    
    alt Streaming Response
        loop Each token
            ViewModel->>StateFlow: Update last message text
            StateFlow->>Compose: State change
            Compose->>ChatScreen: Recompose message
        end
    end
```

---

## State Diagrams

### LLM Engine State Machine

```mermaid
stateDiagram-v2
    [*] --> UNINITIALIZED
    UNINITIALIZED --> LOADING : initialize()
    LOADING --> READY : success
    LOADING --> ERROR : failure
    READY --> GENERATING : generate()
    GENERATING --> READY : complete/cancel
    READY --> RELEASED : release()
    ERROR --> LOADING : retry
    GENERATING --> ERROR : exception
    
    note right of UNINITIALIZED
        Initial state
        Engine not ready
    end note
    
    note right of READY
        Engine ready
        Can generate
    end note
    
    note right of GENERATING
        Active generation
        Streaming tokens
    end note
```

### Memory Pressure State Machine

```mermaid
stateDiagram-v2
    [*] --> NORMAL
    NORMAL --> ELEVATED : usage > 70%
    ELEVATED --> HIGH : usage > 80%
    HIGH --> CRITICAL : usage > 90%
    CRITICAL --> HIGH : usage < 85%
    HIGH --> ELEVATED : usage < 75%
    ELEVATED --> NORMAL : usage < 65%
    
    NORMAL --> NORMAL : check interval
    ELEVATED --> ELEVATED : check interval
    HIGH --> HIGH : check interval
    CRITICAL --> CRITICAL : check interval
```

### Cache Entry Lifecycle

```mermaid
stateDiagram-v2
    [*] --> CREATED : put()
    CREATED --> ACCESSED : get()
    ACCESSED --> ACCESSED : get()
    ACCESSED --> EXPIRED : TTL reached
    ACCESSED --> STALE : stale threshold
    STALE --> REFRESHING : fetch()
    REFRESHING --> ACCESSED : success
    REFRESHING --> EXPIRED : failure
    EXPIRED --> [*] : evict()
    CREATED --> EVICTED : LRU eviction
    ACCESSED --> EVICTED : LRU eviction
```

---

## Component Diagrams

### Dependency Injection Graph

```mermaid
graph TB
    subgraph "DI Modules"
        AM[AppModule]
        DM[DatabaseModule]
        LM[LlmModule]
        OM[OptimizationModule]
    end
    
    subgraph "Provided Components"
        DSR[DataStoreRepository]
        CDB[ChatDatabase]
        MLE[MlcLlmEngine]
        CM[CacheManager]
        AMM[AdaptiveMemoryManager]
        PM[PerformanceMonitor]
    end
    
    AM --> DSR
    DM --> CDB
    LM --> MLE
    OM --> CM
    OM --> AMM
    OM --> PM
```

### Component Dependencies

```mermaid
graph TB
    subgraph UI["UI Layer"]
        CS[ChatScreen]
        HS[HistoryScreen]
        SS[SettingsScreen]
        VS[VoiceInputScreen]
    end
    
    subgraph LLM["LLM Layer"]
        MLE[MlcLlmEngine]
        LE[LlmEngine Interface]
        MM[ModelManager]
        HD[HardwareDetector]
    end
    
    subgraph Data["Data Layer"]
        CM[CacheManager]
        L1[L1MemoryCache]
        L2[L2DiskCache]
        BC[BitmapMemoryCache]
        DB[(Room Database)]
    end
    
    subgraph Memory["Memory Layer"]
        AMM[AdaptiveMemoryManager]
        MPM[MemoryPoolManager]
        MLD[MemoryLeakDetector]
        RC[ReferenceCacheManager]
    end
    
    subgraph Perf["Performance Layer"]
        PM[PerformanceMonitor]
        RPM[RumPerformanceMonitor]
        ST[StartupTracer]
    end
    
    subgraph DI["Dependency Injection"]
        LM[LlmModule]
        OM[OptimizationModule]
        DM[DatabaseModule]
    end
    
    CS --> MLE
    MLE --> LE
    MLE --> AMM
    CM --> L1
    CM --> L2
    CM --> BC
    CM --> RC
    AMM --> MPM
    AMM --> RC
    PM --> AMM
    LM --> MLE
    OM --> CM
    OM --> AMM
    OM --> PM
    DM --> DB
```

---

## Data Flow Diagrams

### Data Flow - Chat Message

```mermaid
graph LR
    A[User Input] --> B[ChatScreen]
    B --> C[ChatViewModel]
    C --> D{Save to DB}
    D --> E[(Room Database)]
    C --> F[LlmEngine]
    F --> G[Native Layer]
    G --> H[GPU]
    H --> G
    G --> F
    F --> C
    C --> B
    B --> I[Display]
```

### Data Flow - Cache Operation

```mermaid
graph TD
    A[Client Request] --> B{Check L1}
    B -->|Hit| C[Return Value]
    B -->|Miss| D{Check L2}
    D -->|Hit| E[Promote to L1]
    E --> C
    D -->|Miss| F[Fetch from Source]
    F --> G[Write to L1]
    F --> H[Write to L2]
    G --> C
    H --> C
```

### Data Flow - Memory Monitoring

```mermaid
graph TD
    A[5s Timer] --> B[Get Memory Info]
    B --> C[Calculate Usage]
    C --> D{Pressure Changed?}
    D -->|No| A
    D -->|Yes| E[Update Profile]
    E --> F[Notify Listeners]
    F --> G{Critical?}
    G -->|Yes| H[Emergency Trim]
    G -->|No| A
    H --> I[Clear Pools]
    H --> J[Clear Cache]
    H --> K[Request GC]
    I --> A
    J --> A
    K --> A
```

---

## Related Documents

| Document | Relationship | Description |
|----------|--------------|-------------|
| [Architecture Overview](../architecture/overview.md) | Describes | High-level architecture |
| [Components](../architecture/components.md) | Details | Component breakdown |
| [Interactive Visualizations](interactive.md) | Complements | p5.js visualizations |
| [Glossary](../references/glossary.md) | Reference | Terminology |

---

*Document maintained by the Technical Architecture Team*  
*Last updated: 2026-02-01*  
*Classification: ISO/IEC/IEEE 42010*
