---
title: "ADR-003: Reactive State Management with StateFlow"
date: "2026-02-01"
status: "Accepted"
author: "Technical Architecture Team"
---

# ADR-003: Reactive State Management with StateFlow

## Status

**Accepted**

## Context

The Nimittam application requires a state management solution that:
- Supports reactive UI updates with Jetpack Compose
- Handles asynchronous data streams (LLM token generation)
- Provides lifecycle-aware state observation
- Enables clean separation between UI and business logic

### Options Considered

1. **StateFlow + ViewModel (Selected)**
   - Pros: Native Kotlin, Compose integration, lifecycle-aware
   - Cons: Learning curve for developers new to reactive programming

2. **LiveData + ViewModel**
   - Pros: Mature, well-documented
   - Cons: Less flexible for complex streams, requires Transformations

3. **RxJava**
   - Pros: Powerful stream operations, extensive operators
   - Cons: Steep learning curve, additional dependency, overkill for simple cases

4. **Manual Callbacks**
   - Pros: Simple, no dependencies
   - Cons: Boilerplate, error-prone, no lifecycle awareness

## Decision

**We will use StateFlow with ViewModel for state management.**

### Rationale

1. **Compose Integration**: StateFlow is the recommended approach for Compose, with `collectAsState()` providing automatic lifecycle-aware collection.

2. **Cold vs Hot Streams**: StateFlow is a hot stream that always has a value, making it ideal for UI state that must always be available.

3. **Backpressure Handling**: Built-in backpressure management through conflation (keeping only latest value).

4. **Testing**: StateFlow is easily testable with turbine library.

5. **Consistency**: Single approach across the codebase reduces cognitive load.

## Consequences

### Positive

- Native Kotlin solution (no external dependencies)
- Excellent Compose integration
- Lifecycle-aware by default
- Thread-safe state updates
- Easy testing

### Negative

- Requires understanding of coroutines and flows
- Potential for excessive recomposition if not careful
- Debugging reactive streams can be challenging

### Mitigations

| Concern | Mitigation |
|---------|------------|
| Learning Curve | Team training, code reviews |
| Recomposition | Use `derivedStateOf`, key-based lists |
| Debugging | Logging, Flow debugging tools |

## Implementation

### Pattern: MVI with StateFlow

```kotlin
// State
sealed class ChatState {
    object Loading : ChatState()
    data class Messages(val messages: List<ChatMessage>) : ChatState()
    data class Error(val message: String) : ChatState()
}

// ViewModel
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val llmEngine: LlmEngine
) : ViewModel() {
    
    private val _state = MutableStateFlow<ChatState>(ChatState.Loading)
    val state: StateFlow<ChatState> = _state.asStateFlow()
    
    fun sendMessage(text: String) {
        viewModelScope.launch {
            _state.value = ChatState.Messages(currentMessages + userMessage(text))
            
            llmEngine.generate(text, params).collect { result ->
                when (result) {
                    is GenerationResult.Token -> appendToken(result.text)
                    is GenerationResult.Complete -> finishGeneration()
                    is GenerationResult.Error -> showError(result.message)
                }
            }
        }
    }
}

// Composable
@Composable
fun ChatScreen(viewModel: ChatViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    
    when (val currentState = state) {
        is ChatState.Loading -> LoadingIndicator()
        is ChatState.Messages -> MessageList(currentState.messages)
        is ChatState.Error -> ErrorView(currentState.message)
    }
}
```

### Best Practices

1. **Expose StateFlow, Hide MutableStateFlow**
   ```kotlin
   private val _state = MutableStateFlow<State>(InitialState)
   val state: StateFlow<State> = _state.asStateFlow()
   ```

2. **Use `collectAsStateWithLifecycle()`**
   ```kotlin
   val state by viewModel.state.collectAsStateWithLifecycle()
   ```

3. **Avoid StateFlow for One-time Events**
   Use `SharedFlow` or callbacks for navigation, toasts, etc.

4. **Use `derivedStateOf` for Computed Values**
   ```kotlin
   val sortedMessages by remember(messages) {
       derivedStateOf { messages.sortedBy { it.timestamp } }
   }
   ```

## Related Decisions

- [ADR-001: On-Device Inference](ADR-001-on-device-inference.md)
- [ADR-002: MLC-LLM Framework](ADR-002-mlc-llm-framework.md)

## References

- [Architecture Overview](../architecture/overview.md)
- [Components](../architecture/components.md)
- [Kotlin Flow Documentation](https://kotlinlang.org/docs/flow.html)

---

*Decision recorded: 2026-02-01*  
*Last reviewed: 2026-02-01*
