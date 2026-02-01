---
title: "Glossary"
subtitle: "Terminology and Definitions"
version: "1.0.0"
date: "2026-02-01"
author: "Technical Architecture Team"
classification: "Reference"
status: "Active"
---

# Glossary

## Table of Contents

1. [A](#a)
2. [B](#b)
3. [C](#c)
4. [D](#d)
5. [E](#e)
6. [F](#f)
7. [G](#g)
8. [H](#h)
9. [I](#i)
10. [J](#j)
11. [K](#k)
12. [L](#l)
13. [M](#m)
14. [N](#n)
15. [O](#o)
16. [P](#p)
17. [Q](#q)
18. [R](#r)
19. [S](#s)
20. [T](#t)
21. [U](#u)
22. [V](#v)
23. [W](#w)

---

## A

**Adapter Pattern**  
A structural design pattern that allows objects with incompatible interfaces to collaborate. In Nimittam, `MlcLlmEngine` adapts the native MLC-LLM library to the `LlmEngine` Kotlin interface.

**ADR (Architecture Decision Record)**  
A document that captures an important architectural decision made along with its context and consequences.

**Afferent Coupling (Ca)**  
The number of classes outside a component that depend on classes within the component. Higher Ca indicates the component is more relied upon.

**Amortized Analysis** 
A method for analyzing a given algorithm's complexity by considering both the costly and less costly operations together over the entire sequence of operations.

**ANR (Application Not Responding)**  
An Android error state that occurs when the main thread is blocked for more than 5 seconds.

---

## B

**Backpressure**  
A mechanism for handling situations where a data producer emits items faster than the consumer can process them. Kotlin Flow provides built-in backpressure handling.

**Battery Optimizer**  
A component in Nimittam that monitors battery level and adjusts inference parameters to conserve power.

**Big O Notation**  
A mathematical notation that describes the limiting behavior of a function when the argument tends towards a particular value or infinity. Used to classify algorithms by their complexity.

**Bitmap Pool**  
An object pool pattern implementation for reusing Bitmap instances, reducing GC pressure from image operations.

---

## C

**Cache Hit Rate**  
The percentage of cache accesses that result in a cache hit (data found in cache). Nimittam targets >85% hit rate across L1 and L2 caches.

**CallbackFlow**  
A Kotlin Flow builder that converts callback-based APIs into Flows, used in `MlcLlmEngine` for streaming token generation.

**Cyclomatic Complexity (CC)**  
A software metric used to indicate the complexity of a program. Calculated as CC = E - N + 2P, where E = edges, N = nodes, P = connected components.

**Clean Architecture**  
An architectural pattern by Robert C. Martin that separates concerns into layers: Entities, Use Cases, Interface Adapters, and Frameworks.

**Cold Start**  
The time taken for an application to launch when it is not already in memory. Nimittam targets <2 seconds cold start time.

**Command Pattern**  
A behavioral design pattern that turns a request into a stand-alone object containing all information about the request.

**Compose (Jetpack Compose)**  
Android's modern toolkit for building native UI using a declarative approach.

**Coroutines**  
Kotlin's solution for asynchronous programming, providing a way to write non-blocking code sequentially.

---

## D

**DAO (Data Access Object)**  
A pattern that provides an abstract interface to some type of database or other persistence mechanism.

**Dependency Injection (DI)**  
A design pattern that implements Inversion of Control for resolving dependencies. Nimittam uses Hilt for DI.

**Detekt**  
A static code analysis tool for Kotlin that helps identify code smells and complexity issues.

**Dispatcher (Coroutines)**  
Determines what thread or threads the corresponding coroutine uses for its execution. Options include Default, IO, Main, and Unconfined.

**DTO (Data Transfer Object)**  
An object that carries data between processes, used to reduce the number of method calls.

---

## E

**Efferent Coupling (Ce)**  
The number of classes inside a component that depend on classes outside the component. Higher Ce indicates the component depends more on others.

**Embedding (Model)**  
The process of including a pre-trained model within an application package for on-device inference.

**Eviction (Cache)**  
The process of removing entries from a cache when it reaches capacity, typically using LRU (Least Recently Used) policy.

---

## F

**Facade Pattern**  
A structural design pattern that provides a simplified interface to a library, framework, or complex set of classes. `CacheManager` acts as a facade for multi-level caching.

**First Token Latency**  
The time from submitting a prompt to receiving the first generated token. Nimittam targets <200ms.

**Flow**  
A Kotlin API for asynchronous data streams that sequentially emits values and completes normally or with an exception.

**Frame Time**  
The time taken to render a single frame of the UI. Target is <16.67ms for 60fps.

---

## G

**GenerationParams**  
Configuration parameters for text generation including temperature, topP, maxTokens, and preferredBackend.

**GoF (Gang of Four)**  
Refers to the authors of "Design Patterns: Elements of Reusable Object-Oriented Software": Erich Gamma, Richard Helm, Ralph Johnson, and John Vlissides.

**GPU (Graphics Processing Unit)**  
Hardware accelerator used for LLM inference. Nimittam supports Vulkan and OpenCL GPU backends.

---

## H

**Halstead Metrics**  
Software metrics introduced by Maurice Halstead in 1977, measuring complexity based on operator and operand counts.

**Hilt**  
A dependency injection library for Android that reduces the boilerplate of doing manual dependency injection.

**Hot Stream**  
A stream that produces values regardless of whether there are collectors. `StateFlow` is a hot stream.

---

## I

**Instability Metric (I)**  
Calculated as I = Ce / (Ca + Ce), measuring a component's susceptibility to change. Range: 0 (stable) to 1 (unstable).

**Inference**  
The process of generating predictions or outputs from a trained machine learning model.

**Interface Segregation Principle**  
The I in SOLID principles: Clients should not be forced to depend on methods they do not use.

---

## J

**Jank**  
Visual stuttering in UI animation caused by frames taking too long to render (>16.67ms for 60fps).

**Jetpack**  
A suite of libraries, tools, and guidance to help developers write high-quality apps more easily.

**JNI (Java Native Interface)**  
A framework that enables Java code running in a JVM to call and be called by native applications and libraries.

---

## K

**KV Cache (Key-Value Cache)**  
A cache used in transformer models to store key and value vectors from previous tokens to avoid recomputation.

**Kotlin**  
A modern, statically typed programming language that makes developers more productive. Primary language for Nimittam.

---

## L

**LazyColumn**  
A Jetpack Compose component that renders only visible items in a list, similar to RecyclerView.

**LLM (Large Language Model)**  
A type of artificial intelligence model trained on vast amounts of text data to understand and generate human-like text.

**LruCache**  
A cache that holds strong references to a limited number of values, evicting least recently used when full.

---

## M

**Maintainability Index (MI)**  
A software metric that measures how maintainable source code is, calculated from Halstead Volume, Cyclomatic Complexity, and Lines of Code.

**MLC-LLM**  
Machine Learning Compilation for Large Language Models, a framework for deploying LLMs on various devices.

**MVI (Model-View-Intent)**  
An architectural pattern where the View sends Intents to the Model, which updates State that flows back to the View.

**MutableStateFlow**  
A mutable version of StateFlow used internally in ViewModels, exposed as immutable StateFlow to observers.

---

## N

**NDK (Native Development Kit)**  
A set of tools that allows using C and C++ code with Android, required for MLC-LLM integration.

**NPU (Neural Processing Unit)**  
Specialized hardware for accelerating neural network operations. Nimittam supports Qualcomm Hexagon NPU.

---

## O

**Object Pool Pattern**  
A creational design pattern that uses a set of initialized objects kept ready to use rather than allocating and destroying them on demand.

**Observer Pattern**  
A behavioral design pattern where an object (subject) maintains a list of dependents (observers) and notifies them of state changes.

**On-Device Inference**  
Running machine learning models directly on the user's device rather than sending data to cloud servers.

---

## P

**p5.js**  
A JavaScript library for creative coding, used in Nimittam documentation for interactive visualizations.

**Prompt**  
The input text provided to an LLM to generate a response.

**Proto DataStore**  
A data storage solution that stores typed objects using protocol buffers, replacing SharedPreferences.

---

## Q

**QNN (Qualcomm Neural Network)**  
Qualcomm's SDK for running neural networks on Hexagon NPU, supported by MLC-LLM.

**Quantization**  
The process of reducing the precision of model weights (e.g., from FP32 to INT8 or INT4) to reduce size and improve inference speed.

---

## R

**Reactive Programming**  
A programming paradigm oriented around data flows and the propagation of change.

**Repository Pattern**  
A design pattern that mediates between the domain and data mapping layers, abstracting data access.

**Room**  
An abstraction layer over SQLite provided by Android Jetpack for database access.

---

## S

**Sealed Class**  
A Kotlin class that restricts which classes can inherit from it, used for representing restricted hierarchies like `GenerationResult`.

**Singleton Pattern**  
A creational design pattern that restricts the instantiation of a class to one single instance.

**StateFlow**  
A hot observable data holder that emits the current and new state updates to its collectors.

**State Pattern**  
A behavioral design pattern that allows an object to alter its behavior when its internal state changes.

**Strategy Pattern**  
A behavioral design pattern that enables selecting an algorithm at runtime. Used for backend selection and memory profiles.

**SupervisorJob**  
A coroutine job that prevents failures in child coroutines from affecting siblings.

**SWR (Stale-While-Revalidate)**  
A caching pattern that returns stale data immediately while fetching fresh data in the background.

---

## T

**Template Method Pattern**  
A behavioral design pattern that defines the skeleton of an algorithm in a base class, letting subclasses override specific steps.

**Thermal Throttling**  
A mechanism that reduces device performance to prevent overheating. Nimittam adapts to thermal states.

**Throughput**  
The rate at which tokens are generated, measured in tokens per second. Nimittam targets 15-30 t/s.

**Token**  
A unit of text (word or subword) processed by LLMs. Generation produces tokens sequentially.

**TTFT (Time To First Token)**  
The latency from submitting a prompt to receiving the first generated token.

---

## U

**UI (User Interface)**  
The visual elements through which users interact with the application. Nimittam uses Jetpack Compose.

**Unidirectional Data Flow**  
A pattern where data flows in a single direction, making state changes predictable.

---

## V

**ViewModel**  
An Android Architecture Component designed to store and manage UI-related data in a lifecycle-conscious way.

**Vulkan**  
A low-overhead, cross-platform API for 3D graphics and computing, used as a GPU backend for inference.

---

## W

**WeakReference**  
A reference that does not prevent its referent from being garbage collected, used in `WeakReferenceCache`.

---

## Related Documents

| Document | Relationship | Description |
|----------|--------------|-------------|
| [Bibliography](bibliography.md) | Cites | Reference sources |
| [Architecture Overview](../architecture/overview.md) | Uses | Architecture terms |
| [Patterns](../architecture/patterns.md) | Defines | Design patterns |

---

*Document maintained by the Technical Architecture Team*  
*Last updated: 2026-02-01*  
*Classification: Reference*
