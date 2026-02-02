/*
 * Copyright 2025 Tanmay Patil
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.google.ai.edge.gallery.ui.viewmodels

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.ai.edge.gallery.data.DataStoreRepository
import com.google.ai.edge.gallery.data.db.entity.ConversationEntity
import com.google.ai.edge.gallery.data.db.repository.ChatHistoryRepository
import com.google.ai.edge.gallery.llm.ChatMessage as LlmChatMessage
import com.google.ai.edge.gallery.llm.ChatRole
import com.google.ai.edge.gallery.llm.EngineLifecycleManager
import com.google.ai.edge.gallery.llm.EngineState
import com.google.ai.edge.gallery.llm.GenerationParams
import com.google.ai.edge.gallery.llm.GenerationResult
import com.google.ai.edge.gallery.llm.InitializationProgress
import com.google.ai.edge.gallery.llm.LlmEngine
import com.google.ai.edge.gallery.llm.LlmEngineState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/**
 * UI state for the Chat screen
 */
data class ChatUiState(
    val messages: List<ChatMessageUiModel> = emptyList(),
    val isLoading: Boolean = false,
    val isGenerating: Boolean = false,
    val errorMessage: String? = null,
    val currentConversationId: String? = null,
    val modelName: String = "Nimittam Lite",
    val inputText: String = "",
    val canSendMessage: Boolean = true,
    // New fields for initialization state handling
    val engineState: EngineState = EngineState.UNINITIALIZED,
    val initializationProgress: Int = 0,
    val initializationMessage: String? = null,
    val isInitializing: Boolean = false,
    val pendingPromptCount: Int = 0
)

/**
 * UI model for chat messages
 */
data class ChatMessageUiModel(
    val id: String = UUID.randomUUID().toString(),
    val content: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val isComplete: Boolean = true,
    val isError: Boolean = false
)

/**
 * Events emitted by the ChatViewModel
 */
sealed class ChatEvent {
    data class ShowError(val message: String) : ChatEvent()
    data class MessageSent(val messageId: String) : ChatEvent()
    data class ResponseComplete(val messageId: String) : ChatEvent()
    object ScrollToBottom : ChatEvent()
    data class ShowInitializationProgress(val progress: Int, val message: String?) : ChatEvent()
    object EngineReady : ChatEvent()
    data class PromptQueued(val position: Int) : ChatEvent()
}

/**
 * ViewModel for the Chat screen.
 * Manages chat state, LLM inference, and conversation persistence.
 * 
 * Enhanced with EngineLifecycleManager integration for robust initialization handling.
 */
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val llmEngine: LlmEngine,
    private val lifecycleManager: EngineLifecycleManager,
    private val chatHistoryRepository: ChatHistoryRepository,
    private val dataStoreRepository: DataStoreRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    companion object {
        private const val TAG = "ChatViewModel"
        private const val KEY_CONVERSATION_ID = "conversation_id"
    }

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<ChatEvent>()
    val events: SharedFlow<ChatEvent> = _events.asSharedFlow()

    private var generationJob: Job? = null
    private var currentConversation: ConversationEntity? = null

    init {
        // Observe engine state changes
        observeEngineState()
        
        // Observe initialization progress
        observeInitializationProgress()
        
        // Load settings
        viewModelScope.launch {
            dataStoreRepository.selectedModelFlow
                .catch { Log.e(TAG, "Error loading model name", it) }
                .collect { modelName ->
                    _uiState.update { it.copy(modelName = modelName) }
                }
        }

        // Check for existing conversation ID
        val conversationId = savedStateHandle.get<String>(KEY_CONVERSATION_ID)
        if (conversationId != null) {
            loadConversation(conversationId)
        } else {
            // Check for last conversation
            viewModelScope.launch {
                val lastId = dataStoreRepository.lastConversationIdFlow.first()
                if (lastId.isNotEmpty()) {
                    loadConversation(lastId)
                } else {
                    // Start with welcome message
                    addWelcomeMessage()
                }
            }
        }
    }

    /**
     * Observe engine state changes and update UI accordingly
     */
    private fun observeEngineState() {
        lifecycleManager.stateFlow
            .onEach { state ->
                Log.d(TAG, "Engine state changed: $state")
                _uiState.update { 
                    it.copy(
                        engineState = state,
                        isInitializing = state == EngineState.INITIALIZING,
                        canSendMessage = state == EngineState.READY || state == EngineState.INITIALIZING
                    )
                }
                
                when (state) {
                    EngineState.READY -> {
                        _events.emit(ChatEvent.EngineReady)
                    }
                    EngineState.ERROR -> {
                        _events.emit(ChatEvent.ShowError("Engine initialization failed. Please try again."))
                    }
                    else -> { /* No specific action for other states */ }
                }
            }
            .catch { e ->
                Log.e(TAG, "Error observing engine state", e)
            }
            .launchIn(viewModelScope)
    }

    /**
     * Observe initialization progress updates
     */
    private fun observeInitializationProgress() {
        lifecycleManager.progressFlow
            .onEach { progress ->
                when (progress) {
                    is InitializationProgress.InProgress -> {
                        _uiState.update {
                            it.copy(
                                initializationProgress = progress.percentage,
                                initializationMessage = progress.message
                            )
                        }
                        _events.emit(ChatEvent.ShowInitializationProgress(
                            progress.percentage,
                            progress.message
                        ))
                    }
                    is InitializationProgress.Complete -> {
                        _uiState.update {
                            it.copy(
                                initializationProgress = 100,
                                initializationMessage = "Ready",
                                isInitializing = false
                            )
                        }
                    }
                    is InitializationProgress.Failed -> {
                        _uiState.update {
                            it.copy(
                                initializationProgress = 0,
                                initializationMessage = "Failed: ${progress.error.message}",
                                isInitializing = false
                            )
                        }
                    }
                    else -> { /* No action for NotStarted */ }
                }
            }
            .catch { e ->
                Log.e(TAG, "Error observing initialization progress", e)
            }
            .launchIn(viewModelScope)
    }

    /**
     * Initialize the engine with the specified model.
     * This should be called when the user selects a model or on app startup.
     * 
     * @param modelPath Path to the model file
     * @return Result of initialization
     */
    suspend fun initializeEngine(modelPath: String): Result<Unit> {
        return lifecycleManager.initialize(modelPath)
    }

    /**
     * Check if the engine is ready to accept prompts.
     */
    fun isEngineReady(): Boolean = lifecycleManager.canAcceptPrompts()

    /**
     * Wait for the engine to be ready.
     * 
     * @param timeoutMs Maximum time to wait in milliseconds
     * @return true if ready, false if timeout
     */
    suspend fun waitForEngineReady(timeoutMs: Long = 30000L): Boolean {
        return lifecycleManager.waitForReady(timeoutMs)
    }

    /**
     * Add initial welcome message
     */
    private fun addWelcomeMessage() {
        val welcomeMessage = ChatMessageUiModel(
            content = "Hello! I'm Nimittam, your offline AI assistant. How can I help you today?",
            isUser = false
        )
        _uiState.update { state ->
            state.copy(messages = listOf(welcomeMessage))
        }
    }

    /**
     * Load an existing conversation
     */
    fun loadConversation(conversationId: String) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true, errorMessage = null) }

                // Load conversation
                val conversation = chatHistoryRepository.getConversation(conversationId)
                if (conversation != null) {
                    currentConversation = conversation
                    _uiState.update { 
                        it.copy(
                            currentConversationId = conversationId,
                            modelName = conversation.modelName ?: it.modelName
                        )
                    }

                    // Load messages
                    chatHistoryRepository.getMessagesForConversation(conversationId)
                        .catch { e ->
                            Log.e(TAG, "Error loading messages", e)
                            _uiState.update { state ->
                                state.copy(
                                    isLoading = false,
                                    errorMessage = "Failed to load messages"
                                )
                            }
                        }
                        .collect { messages ->
                            val uiMessages = messages.map { entity ->
                                ChatMessageUiModel(
                                    id = entity.id,
                                    content = entity.content,
                                    isUser = entity.isUser,
                                    timestamp = entity.timestamp,
                                    isComplete = true
                                )
                            }
                            _uiState.update { state ->
                                state.copy(
                                    messages = uiMessages,
                                    isLoading = false
                                )
                            }
                        }
                } else {
                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            errorMessage = "Conversation not found"
                        )
                    }
                    addWelcomeMessage()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading conversation", e)
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        errorMessage = "Failed to load conversation"
                    )
                }
                addWelcomeMessage()
            }
        }
    }

    /**
     * Create a new conversation
     */
    fun createNewConversation() {
        viewModelScope.launch {
            try {
                // Cancel any ongoing generation
                cancelGeneration()

                // Reset context
                llmEngine.resetContext()

                // Clear current conversation
                currentConversation = null
                _uiState.update { 
                    ChatUiState(
                        modelName = it.modelName,
                        currentConversationId = null,
                        engineState = it.engineState,
                        initializationProgress = it.initializationProgress,
                        initializationMessage = it.initializationMessage,
                        isInitializing = it.isInitializing
                    )
                }

                addWelcomeMessage()
            } catch (e: Exception) {
                Log.e(TAG, "Error creating new conversation", e)
            }
        }
    }

    /**
     * Update input text
     */
    fun updateInputText(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    /**
     * Send a message and generate AI response
     */
    fun sendMessage() {
        val messageText = _uiState.value.inputText.trim()
        if (messageText.isEmpty() || _uiState.value.isGenerating) return

        viewModelScope.launch {
            try {
                // Check if engine is ready
                if (!lifecycleManager.canAcceptPrompts()) {
                    if (lifecycleManager.shouldQueuePrompts()) {
                        // Engine is initializing, prompt will be queued
                        _events.emit(ChatEvent.ShowError("Engine is initializing. Your message will be processed shortly."))
                    } else {
                        _events.emit(ChatEvent.ShowError("Engine is not ready. Please wait or restart the app."))
                        return@launch
                    }
                }

                // Clear input
                _uiState.update { it.copy(inputText = "", errorMessage = null) }

                // Create conversation if needed
                if (currentConversation == null) {
                    val title = messageText.take(30) + if (messageText.length > 30) "..." else ""
                    currentConversation = chatHistoryRepository.createConversation(
                        title = title,
                        modelName = _uiState.value.modelName
                    )
                    _uiState.update { 
                        it.copy(currentConversationId = currentConversation!!.id) 
                    }
                    dataStoreRepository.updateLastConversationId(currentConversation!!.id)
                }

                // Add user message to UI
                val userMessage = ChatMessageUiModel(
                    content = messageText,
                    isUser = true
                )
                _uiState.update { state ->
                    state.copy(messages = state.messages + userMessage)
                }
                _events.emit(ChatEvent.ScrollToBottom)

                // Persist user message
                chatHistoryRepository.addMessage(
                    conversationId = currentConversation!!.id,
                    content = messageText,
                    isUser = true
                )

                // Generate AI response
                generateResponse(messageText)

            } catch (e: Exception) {
                Log.e(TAG, "Error sending message", e)
                _uiState.update { state ->
                    state.copy(errorMessage = "Failed to send message")
                }
                _events.emit(ChatEvent.ShowError("Failed to send message"))
            }
        }
    }

    /**
     * Generate AI response using LLM engine
     */
    private suspend fun generateResponse(userMessage: String) {
        generationJob = viewModelScope.launch {
            try {
                _uiState.update { it.copy(isGenerating = true) }

                // Get generation parameters from settings
                val temperature = dataStoreRepository.temperatureFlow.first()
                val maxTokens = dataStoreRepository.maxTokensFlow.first()
                val topP = dataStoreRepository.topPFlow.first()
                val topK = dataStoreRepository.topKFlow.first()
                val repeatPenalty = dataStoreRepository.repeatPenaltyFlow.first()

                val params = GenerationParams(
                    temperature = temperature,
                    maxTokens = maxTokens,
                    topP = topP,
                    topK = topK,
                    repeatPenalty = repeatPenalty
                )

                // Build conversation history
                val history = _uiState.value.messages.map { msg ->
                    LlmChatMessage(
                        role = if (msg.isUser) ChatRole.USER else ChatRole.ASSISTANT,
                        content = msg.content
                    )
                }

                // Add streaming response placeholder
                val aiMessage = ChatMessageUiModel(
                    content = "",
                    isUser = false,
                    isComplete = false
                )
                _uiState.update { state ->
                    state.copy(messages = state.messages + aiMessage)
                }

                // Generate response with streaming
                var fullResponse = ""
                lifecycleManager.submitPrompt(userMessage, history, params)
                    .catch { e ->
                        Log.e(TAG, "Generation error", e)
                        _uiState.update { state ->
                            val updatedMessages = state.messages.toMutableList()
                            val lastIndex = updatedMessages.lastIndex
                            if (lastIndex >= 0) {
                                updatedMessages[lastIndex] = updatedMessages[lastIndex].copy(
                                    content = "Sorry, I encountered an error. Please try again.",
                                    isComplete = true,
                                    isError = true
                                )
                            }
                            state.copy(
                                messages = updatedMessages,
                                isGenerating = false,
                                errorMessage = "Generation failed"
                            )
                        }
                        _events.emit(ChatEvent.ShowError("Failed to generate response"))
                    }
                    .collect { result ->
                        when (result) {
                            is GenerationResult.Token -> {
                                fullResponse += result.text
                                _uiState.update { state ->
                                    val updatedMessages = state.messages.toMutableList()
                                    val lastIndex = updatedMessages.lastIndex
                                    if (lastIndex >= 0) {
                                        updatedMessages[lastIndex] = updatedMessages[lastIndex].copy(
                                            content = fullResponse
                                        )
                                    }
                                    state.copy(messages = updatedMessages)
                                }
                            }
                            is GenerationResult.Complete -> {
                                // Persist AI response
                                currentConversation?.let { convo ->
                                    chatHistoryRepository.addMessage(
                                        conversationId = convo.id,
                                        content = fullResponse,
                                        isUser = false
                                    )
                                }

                                _uiState.update { state ->
                                    val updatedMessages = state.messages.toMutableList()
                                    val lastIndex = updatedMessages.lastIndex
                                    if (lastIndex >= 0) {
                                        updatedMessages[lastIndex] = updatedMessages[lastIndex].copy(
                                            isComplete = true
                                        )
                                    }
                                    state.copy(
                                        messages = updatedMessages,
                                        isGenerating = false
                                    )
                                }
                                _events.emit(ChatEvent.ResponseComplete(aiMessage.id))
                            }
                            is GenerationResult.Error -> {
                                _uiState.update { state ->
                                    val updatedMessages = state.messages.toMutableList()
                                    val lastIndex = updatedMessages.lastIndex
                                    if (lastIndex >= 0) {
                                        updatedMessages[lastIndex] = updatedMessages[lastIndex].copy(
                                            content = "Sorry, I encountered an error: ${result.message}",
                                            isComplete = true,
                                            isError = true
                                        )
                                    }
                                    state.copy(
                                        messages = updatedMessages,
                                        isGenerating = false,
                                        errorMessage = result.message
                                    )
                                }
                                _events.emit(ChatEvent.ShowError(result.message))
                            }
                        }
                    }

            } catch (e: CancellationException) {
                // Generation was cancelled
                Log.d(TAG, "Generation cancelled")
                _uiState.update { it.copy(isGenerating = false) }
            } catch (e: Exception) {
                Log.e(TAG, "Error generating response", e)
                _uiState.update { state ->
                    state.copy(
                        isGenerating = false,
                        errorMessage = "Failed to generate response"
                    )
                }
                _events.emit(ChatEvent.ShowError("Failed to generate response"))
            }
        }
    }

    /**
     * Cancel ongoing generation
     */
    fun cancelGeneration() {
        generationJob?.cancel()
        generationJob = null
        viewModelScope.launch {
            try {
                llmEngine.stopGeneration()
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping generation", e)
            }
        }
        _uiState.update { it.copy(isGenerating = false) }
    }

    /**
     * Retry the last failed message
     */
    fun retryLastMessage() {
        val messages = _uiState.value.messages
        if (messages.size < 2) return

        // Find last user message
        val lastUserMessageIndex = messages.indexOfLast { it.isUser }
        if (lastUserMessageIndex < 0) return

        val lastUserMessage = messages[lastUserMessageIndex]

        // Remove error message if present
        _uiState.update { state ->
            val updatedMessages = state.messages
                .filterIndexed { index, _ -> index != lastUserMessageIndex + 1 }
                .toMutableList()
            state.copy(messages = updatedMessages, errorMessage = null)
        }

        // Retry generation
        viewModelScope.launch {
            generateResponse(lastUserMessage.content)
        }
    }

    /**
     * Clear error message
     */
    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    override fun onCleared() {
        super.onCleared()
        generationJob?.cancel()
    }
}
