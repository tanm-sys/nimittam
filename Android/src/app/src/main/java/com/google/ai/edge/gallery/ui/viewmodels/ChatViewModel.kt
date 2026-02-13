/*
 * Copyright 2025 Tanmay Patil
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.google.ai.edge.gallery.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

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
    val engineState: EngineState = EngineState.READY,
    val initializationProgress: Int = 100,
    val initializationMessage: String? = "Ready",
    val isInitializing: Boolean = false,
    val pendingPromptCount: Int = 0
)

/**
 * Engine states for UI display
 */
enum class EngineState {
    UNINITIALIZED,
    INITIALIZING,
    READY,
    ERROR
}

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
 * UI-only version with mock data for preview/testing.
 */
class ChatViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(
        ChatUiState(
            messages = listOf(
                ChatMessageUiModel(
                    content = "Hello! I'm Nimittam, your offline AI assistant. How can I help you today?",
                    isUser = false
                )
            )
        )
    )
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<ChatEvent>()
    val events: SharedFlow<ChatEvent> = _events.asSharedFlow()

    /**
     * Initialize the engine - no-op in UI-only mode
     */
    suspend fun initializeEngine(modelPath: String): Result<Unit> {
        return Result.success(Unit)
    }

    /**
     * Check if the engine is ready - always returns true in UI-only mode
     */
    fun isEngineReady(): Boolean = true

    /**
     * Wait for the engine to be ready - no-op in UI-only mode
     */
    suspend fun waitForEngineReady(timeoutMs: Long = 30000L): Boolean = true

    /**
     * Load an existing conversation - no-op in UI-only mode
     */
    fun loadConversation(conversationId: String) { }

    /**
     * Create a new conversation - resets to mock state
     */
    fun createNewConversation() {
        _uiState.update { 
            ChatUiState(
                messages = listOf(
                    ChatMessageUiModel(
                        content = "Hello! I'm Nimittam, your offline AI assistant. How can I help you today?",
                        isUser = false
                    )
                )
            )
        }
    }

    /**
     * Update input text
     */
    fun updateInputText(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    /**
     * Send a message - no-op in UI-only mode
     */
    fun sendMessage() { }

    /**
     * Cancel ongoing generation - no-op in UI-only mode
     */
    fun cancelGeneration() { }

    /**
     * Retry the last failed message - no-op in UI-only mode
     */
    fun retryLastMessage() { }

    /**
     * Clear error message
     */
    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
