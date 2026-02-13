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

/**
 * UI model for conversation list items
 */
data class ConversationUiModel(
    val id: String,
    val title: String,
    val preview: String,
    val timestamp: Long,
    val messageCount: Int,
    val isArchived: Boolean,
    val category: ConversationCategory
)

/**
 * Categories for organizing conversations
 */
enum class ConversationCategory {
    GENERAL,
    WORK,
    CREATIVE,
    CODE
}

/**
 * UI state for the History screen
 */
data class HistoryUiState(
    val conversations: List<ConversationUiModel> = emptyList(),
    val filteredConversations: List<ConversationUiModel> = emptyList(),
    val isLoading: Boolean = false,
    val isEmpty: Boolean = true,
    val searchQuery: String = "",
    val errorMessage: String? = null,
    val selectedConversationId: String? = null
)

/**
 * Events emitted by the HistoryViewModel
 */
sealed class HistoryEvent {
    data class NavigateToConversation(val conversationId: String) : HistoryEvent()
    object NavigateToNewChat : HistoryEvent()
    object NavigateBack : HistoryEvent()
    data class ShowError(val message: String) : HistoryEvent()
    data class ShowSuccess(val message: String) : HistoryEvent()
    data class ConfirmDelete(val conversationId: String) : HistoryEvent()
}

/**
 * ViewModel for the History screen.
 * UI-only version with mock data for preview/testing.
 */
class HistoryViewModel : ViewModel() {

    // Mock conversation data for UI display
    private val mockConversations = listOf(
        ConversationUiModel(
            id = "1",
            title = "Getting started with Nimittam",
            preview = "Hello! I'm Nimittam, your offline AI assistant...",
            timestamp = System.currentTimeMillis(),
            messageCount = 5,
            isArchived = false,
            category = ConversationCategory.GENERAL
        ),
        ConversationUiModel(
            id = "2",
            title = "Kotlin programming tips",
            preview = "Here are some best practices for Kotlin development...",
            timestamp = System.currentTimeMillis() - 86400000,
            messageCount = 12,
            isArchived = false,
            category = ConversationCategory.CODE
        ),
        ConversationUiModel(
            id = "3",
            title = "Project ideas brainstorming",
            preview = "Let me suggest some creative project ideas...",
            timestamp = System.currentTimeMillis() - 172800000,
            messageCount = 8,
            isArchived = false,
            category = ConversationCategory.CREATIVE
        )
    )

    private val _uiState = MutableStateFlow(
        HistoryUiState(
            conversations = mockConversations,
            filteredConversations = mockConversations,
            isEmpty = false
        )
    )
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<HistoryEvent>()
    val events: SharedFlow<HistoryEvent> = _events.asSharedFlow()

    /**
     * Search/filter conversations - simple in-memory filtering
     */
    fun searchConversations(query: String) {
        val filtered = if (query.isEmpty()) {
            mockConversations
        } else {
            val lowerQuery = query.lowercase()
            mockConversations.filter { conversation ->
                conversation.title.lowercase().contains(lowerQuery) ||
                conversation.preview.lowercase().contains(lowerQuery)
            }
        }

        _uiState.update { state ->
            state.copy(
                searchQuery = query,
                filteredConversations = filtered
            )
        }
    }

    /**
     * Clear search query
     */
    fun clearSearch() {
        _uiState.update { state ->
            state.copy(
                searchQuery = "",
                filteredConversations = mockConversations
            )
        }
    }

    /**
     * Delete a conversation - no-op in UI-only mode
     */
    fun deleteConversation(conversationId: String) {
        viewModelScope.launch {
            _events.emit(HistoryEvent.ShowSuccess("Conversation deleted"))
        }
    }

    /**
     * Request delete confirmation
     */
    fun requestDeleteConversation(conversationId: String) {
        viewModelScope.launch {
            _events.emit(HistoryEvent.ConfirmDelete(conversationId))
        }
    }

    /**
     * Archive a conversation - no-op in UI-only mode
     */
    fun archiveConversation(conversationId: String) {
        viewModelScope.launch {
            _events.emit(HistoryEvent.ShowSuccess("Conversation archived"))
        }
    }

    /**
     * Unarchive a conversation - no-op in UI-only mode
     */
    fun unarchiveConversation(conversationId: String) {
        viewModelScope.launch {
            _events.emit(HistoryEvent.ShowSuccess("Conversation restored"))
        }
    }

    /**
     * Select a conversation to navigate to
     */
    fun selectConversation(conversationId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(selectedConversationId = conversationId) }
            _events.emit(HistoryEvent.NavigateToConversation(conversationId))
        }
    }

    /**
     * Create a new chat
     */
    fun createNewChat() {
        viewModelScope.launch {
            _events.emit(HistoryEvent.NavigateToNewChat)
        }
    }

    /**
     * Navigate back
     */
    fun navigateBack() {
        viewModelScope.launch {
            _events.emit(HistoryEvent.NavigateBack)
        }
    }

    /**
     * Refresh conversations list - no-op in UI-only mode
     */
    fun refresh() { }

    /**
     * Clear error message
     */
    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
