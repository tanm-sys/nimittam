/*
 * Copyright 2025 Tanmay Patil
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.google.ai.edge.gallery.ui.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.ai.edge.gallery.data.DataStoreRepository
import com.google.ai.edge.gallery.data.db.entity.ConversationEntity
import com.google.ai.edge.gallery.data.db.repository.ChatHistoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

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
 * Manages conversation list state and operations.
 */
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val chatHistoryRepository: ChatHistoryRepository,
    private val dataStoreRepository: DataStoreRepository
) : ViewModel() {

    companion object {
        private const val TAG = "HistoryViewModel"
    }

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<HistoryEvent>()
    val events: SharedFlow<HistoryEvent> = _events.asSharedFlow()

    init {
        loadConversations()
    }

    /**
     * Load all conversations from repository
     */
    private fun loadConversations() {
        _uiState.update { it.copy(isLoading = true) }

        chatHistoryRepository.allConversations
            .catch { e ->
                Log.e(TAG, "Error loading conversations", e)
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        errorMessage = "Failed to load conversations"
                    )
                }
                _events.emit(HistoryEvent.ShowError("Failed to load conversations"))
            }
            .onEach { entities ->
                val uiModels = entities.map { entity ->
                    entity.toUiModel()
                }.sortedByDescending { it.timestamp }

                val filtered = if (_uiState.value.searchQuery.isNotEmpty()) {
                    filterConversations(uiModels, _uiState.value.searchQuery)
                } else {
                    uiModels
                }

                _uiState.update { state ->
                    state.copy(
                        conversations = uiModels,
                        filteredConversations = filtered,
                        isLoading = false,
                        isEmpty = uiModels.isEmpty()
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    /**
     * Search/filter conversations
     */
    fun searchConversations(query: String) {
        val filtered = if (query.isEmpty()) {
            _uiState.value.conversations
        } else {
            filterConversations(_uiState.value.conversations, query)
        }

        _uiState.update { state ->
            state.copy(
                searchQuery = query,
                filteredConversations = filtered
            )
        }
    }

    /**
     * Filter conversations based on search query
     */
    private fun filterConversations(
        conversations: List<ConversationUiModel>,
        query: String
    ): List<ConversationUiModel> {
        val lowerQuery = query.lowercase()
        return conversations.filter { conversation ->
            conversation.title.lowercase().contains(lowerQuery) ||
            conversation.preview.lowercase().contains(lowerQuery)
        }
    }

    /**
     * Clear search query
     */
    fun clearSearch() {
        _uiState.update { state ->
            state.copy(
                searchQuery = "",
                filteredConversations = state.conversations
            )
        }
    }

    /**
     * Delete a conversation
     */
    fun deleteConversation(conversationId: String) {
        viewModelScope.launch {
            try {
                chatHistoryRepository.deleteConversation(conversationId)
                
                // If this was the last conversation, clear last conversation ID
                val currentLastId = dataStoreRepository.lastConversationIdFlow
                    .catch { "" }
                    .onEach { }
                    .launchIn(viewModelScope)
                
                dataStoreRepository.updateLastConversationId("")
                
                _events.emit(HistoryEvent.ShowSuccess("Conversation deleted"))
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting conversation", e)
                _events.emit(HistoryEvent.ShowError("Failed to delete conversation"))
            }
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
     * Archive a conversation
     */
    fun archiveConversation(conversationId: String) {
        viewModelScope.launch {
            try {
                chatHistoryRepository.archiveConversation(conversationId, true)
                _events.emit(HistoryEvent.ShowSuccess("Conversation archived"))
            } catch (e: Exception) {
                Log.e(TAG, "Error archiving conversation", e)
                _events.emit(HistoryEvent.ShowError("Failed to archive conversation"))
            }
        }
    }

    /**
     * Unarchive a conversation
     */
    fun unarchiveConversation(conversationId: String) {
        viewModelScope.launch {
            try {
                chatHistoryRepository.archiveConversation(conversationId, false)
                _events.emit(HistoryEvent.ShowSuccess("Conversation restored"))
            } catch (e: Exception) {
                Log.e(TAG, "Error unarchiving conversation", e)
                _events.emit(HistoryEvent.ShowError("Failed to restore conversation"))
            }
        }
    }

    /**
     * Select a conversation to navigate to
     */
    fun selectConversation(conversationId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(selectedConversationId = conversationId) }
            dataStoreRepository.updateLastConversationId(conversationId)
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
     * Refresh conversations list
     */
    fun refresh() {
        loadConversations()
    }

    /**
     * Clear error message
     */
    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    /**
     * Convert ConversationEntity to ConversationUiModel
     */
    private fun ConversationEntity.toUiModel(): ConversationUiModel {
        return ConversationUiModel(
            id = id,
            title = title,
            preview = previewText,
            timestamp = updatedAt,
            messageCount = messageCount,
            isArchived = isArchived,
            category = categorizeConversation(title, previewText)
        )
    }

    /**
     * Categorize conversation based on content
     */
    private fun categorizeConversation(title: String, preview: String): ConversationCategory {
        val text = (title + " " + preview).lowercase()
        return when {
            text.contains("code") || 
            text.contains("program") || 
            text.contains("kotlin") || 
            text.contains("java") || 
            text.contains("python") ||
            text.contains("function") ||
            text.contains("bug") ||
            text.contains("error") -> ConversationCategory.CODE
            
            text.contains("write") || 
            text.contains("story") || 
            text.contains("poem") || 
            text.contains("creative") ||
            text.contains("imagine") ||
            text.contains("design") -> ConversationCategory.CREATIVE
            
            text.contains("work") || 
            text.contains("project") || 
            text.contains("meeting") || 
            text.contains("task") ||
            text.contains("deadline") ||
            text.contains("report") -> ConversationCategory.WORK
            
            else -> ConversationCategory.GENERAL
        }
    }
}
