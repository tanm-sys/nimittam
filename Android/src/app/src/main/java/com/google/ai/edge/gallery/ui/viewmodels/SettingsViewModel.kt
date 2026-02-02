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
import com.google.ai.edge.gallery.data.db.repository.ChatHistoryRepository
import com.google.ai.edge.gallery.llm.ModelManager
import com.google.ai.edge.gallery.llm.StorageInfo
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
 * UI state for the Settings screen
 */
data class SettingsUiState(
    val temperature: Float = 0.7f,
    val maxTokens: Int = 2048,
    val topP: Float = 0.9f,
    val topK: Int = 40,
    val repeatPenalty: Float = 1.1f,
    val darkTheme: Boolean = true,
    val hapticFeedbackEnabled: Boolean = true,
    val notificationsEnabled: Boolean = false,
    val selectedModel: String = "Nimittam Lite",
    val contextSize: Int = 4096,
    val hardwareBackend: String = "VULKAN_GPU",
    val storageInfo: StorageInfo? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null
)

/**
 * Events emitted by the SettingsViewModel
 */
sealed class SettingsEvent {
    data class ShowError(val message: String) : SettingsEvent()
    data class ShowSuccess(val message: String) : SettingsEvent()
    object NavigateBack : SettingsEvent()
    object ConfirmClearHistory : SettingsEvent()
}

/**
 * ViewModel for the Settings screen.
 * Manages settings state and persists changes to DataStore.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val dataStoreRepository: DataStoreRepository,
    private val chatHistoryRepository: ChatHistoryRepository,
    private val modelManager: ModelManager
) : ViewModel() {

    companion object {
        private const val TAG = "SettingsViewModel"
    }

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<SettingsEvent>()
    val events: SharedFlow<SettingsEvent> = _events.asSharedFlow()

    init {
        loadSettings()
        loadStorageInfo()
    }

    /**
     * Load all settings from DataStore
     */
    @Suppress("UNCHECKED_CAST")
    private fun loadSettings() {
        combine(
            dataStoreRepository.temperatureFlow,
            dataStoreRepository.maxTokensFlow,
            dataStoreRepository.topPFlow,
            dataStoreRepository.topKFlow,
            dataStoreRepository.repeatPenaltyFlow,
            dataStoreRepository.darkThemeFlow,
            dataStoreRepository.hapticFeedbackEnabledFlow,
            dataStoreRepository.notificationsEnabledFlow,
            dataStoreRepository.selectedModelFlow,
            dataStoreRepository.contextSizeFlow,
            dataStoreRepository.hardwareBackendFlow
        ) { values: Array<Any> ->
            SettingsUiState(
                temperature = values[0] as Float,
                maxTokens = values[1] as Int,
                topP = values[2] as Float,
                topK = values[3] as Int,
                repeatPenalty = values[4] as Float,
                darkTheme = values[5] as Boolean,
                hapticFeedbackEnabled = values[6] as Boolean,
                notificationsEnabled = values[7] as Boolean,
                selectedModel = values[8] as String,
                contextSize = values[9] as Int,
                hardwareBackend = values[10] as String,
                storageInfo = _uiState.value.storageInfo
            )
        }
            .catch { e ->
                Log.e(TAG, "Error loading settings", e)
                _uiState.update { it.copy(errorMessage = "Failed to load settings") }
            }
            .onEach { state ->
                _uiState.value = state
            }
            .launchIn(viewModelScope)
    }

    /**
     * Load storage information
     */
    private fun loadStorageInfo() {
        viewModelScope.launch {
            try {
                val storageInfo = modelManager.getStorageUsage()
                _uiState.update { it.copy(storageInfo = storageInfo) }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading storage info", e)
            }
        }
    }

    /**
     * Update temperature setting
     */
    fun updateTemperature(temperature: Float) {
        viewModelScope.launch {
            val result = dataStoreRepository.updateTemperature(temperature)
            result.onFailure { e ->
                Log.e(TAG, "Error updating temperature", e)
                _events.emit(SettingsEvent.ShowError("Failed to update temperature"))
            }
        }
    }

    /**
     * Update max tokens setting
     */
    fun updateMaxTokens(maxTokens: Int) {
        viewModelScope.launch {
            val result = dataStoreRepository.updateMaxTokens(maxTokens)
            result.onFailure { e ->
                Log.e(TAG, "Error updating max tokens", e)
                _events.emit(SettingsEvent.ShowError("Failed to update max tokens"))
            }
        }
    }

    /**
     * Update topP setting
     */
    fun updateTopP(topP: Float) {
        viewModelScope.launch {
            val result = dataStoreRepository.updateTopP(topP)
            result.onFailure { e ->
                Log.e(TAG, "Error updating topP", e)
                _events.emit(SettingsEvent.ShowError("Failed to update topP"))
            }
        }
    }

    /**
     * Update topK setting
     */
    fun updateTopK(topK: Int) {
        viewModelScope.launch {
            val result = dataStoreRepository.updateTopK(topK)
            result.onFailure { e ->
                Log.e(TAG, "Error updating topK", e)
                _events.emit(SettingsEvent.ShowError("Failed to update topK"))
            }
        }
    }

    /**
     * Update repeat penalty setting
     */
    fun updateRepeatPenalty(repeatPenalty: Float) {
        viewModelScope.launch {
            val result = dataStoreRepository.updateRepeatPenalty(repeatPenalty)
            result.onFailure { e ->
                Log.e(TAG, "Error updating repeat penalty", e)
                _events.emit(SettingsEvent.ShowError("Failed to update repeat penalty"))
            }
        }
    }

    /**
     * Update dark theme setting
     */
    fun updateDarkTheme(enabled: Boolean) {
        viewModelScope.launch {
            val result = dataStoreRepository.updateDarkTheme(enabled)
            result.onSuccess {
                _events.emit(SettingsEvent.ShowSuccess(if (enabled) "Dark theme enabled" else "Light theme enabled"))
            }.onFailure { e ->
                Log.e(TAG, "Error updating dark theme", e)
                _events.emit(SettingsEvent.ShowError("Failed to update theme"))
            }
        }
    }

    /**
     * Update haptic feedback setting
     */
    fun updateHapticFeedback(enabled: Boolean) {
        viewModelScope.launch {
            val result = dataStoreRepository.updateHapticFeedback(enabled)
            result.onFailure { e ->
                Log.e(TAG, "Error updating haptic feedback", e)
                _events.emit(SettingsEvent.ShowError("Failed to update haptic feedback"))
            }
        }
    }

    /**
     * Update notifications setting
     */
    fun updateNotifications(enabled: Boolean) {
        viewModelScope.launch {
            val result = dataStoreRepository.updateNotifications(enabled)
            result.onSuccess {
                _events.emit(SettingsEvent.ShowSuccess(if (enabled) "Notifications enabled" else "Notifications disabled"))
            }.onFailure { e ->
                Log.e(TAG, "Error updating notifications", e)
                _events.emit(SettingsEvent.ShowError("Failed to update notifications"))
            }
        }
    }

    /**
     * Update selected model
     */
    fun updateSelectedModel(model: String) {
        viewModelScope.launch {
            val result = dataStoreRepository.updateSelectedModel(model)
            result.onSuccess {
                _events.emit(SettingsEvent.ShowSuccess("Model changed to $model"))
            }.onFailure { e ->
                Log.e(TAG, "Error updating selected model", e)
                _events.emit(SettingsEvent.ShowError("Failed to change model"))
            }
        }
    }

    /**
     * Update context size
     */
    fun updateContextSize(size: Int) {
        viewModelScope.launch {
            val result = dataStoreRepository.updateContextSize(size)
            result.onFailure { e ->
                Log.e(TAG, "Error updating context size", e)
                _events.emit(SettingsEvent.ShowError("Failed to update context size"))
            }
        }
    }

    /**
     * Update hardware backend
     */
    fun updateHardwareBackend(backend: String) {
        viewModelScope.launch {
            val result = dataStoreRepository.updateHardwareBackend(backend)
            result.onSuccess {
                _events.emit(SettingsEvent.ShowSuccess("Hardware backend changed to $backend"))
            }.onFailure { e ->
                Log.e(TAG, "Error updating hardware backend", e)
                _events.emit(SettingsEvent.ShowError("Failed to change hardware backend"))
            }
        }
    }

    /**
     * Clear all chat history
     */
    fun clearChatHistory() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true) }
                
                // Get all conversations and delete them
                chatHistoryRepository.allConversations
                    .catch { e ->
                        Log.e(TAG, "Error getting conversations", e)
                        _uiState.update { it.copy(isLoading = false, errorMessage = "Failed to clear history") }
                        _events.emit(SettingsEvent.ShowError("Failed to clear chat history"))
                    }
                    .collect { conversations ->
                        conversations.forEach { conversation ->
                            chatHistoryRepository.deleteConversation(conversation.id)
                        }
                        
                        // Clear last conversation ID
                        dataStoreRepository.updateLastConversationId("")
                        
                        // Refresh storage info
                        loadStorageInfo()
                        
                        _uiState.update { it.copy(isLoading = false) }
                        _events.emit(SettingsEvent.ShowSuccess("Chat history cleared"))
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Error clearing chat history", e)
                _uiState.update { it.copy(isLoading = false, errorMessage = "Failed to clear history") }
                _events.emit(SettingsEvent.ShowError("Failed to clear chat history"))
            }
        }
    }

    /**
     * Request to clear history (shows confirmation)
     */
    fun requestClearHistory() {
        viewModelScope.launch {
            _events.emit(SettingsEvent.ConfirmClearHistory)
        }
    }

    /**
     * Navigate back
     */
    fun navigateBack() {
        viewModelScope.launch {
            _events.emit(SettingsEvent.NavigateBack)
        }
    }

    /**
     * Clear error message
     */
    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    /**
     * Clear success message
     */
    fun clearSuccess() {
        _uiState.update { it.copy(successMessage = null) }
    }

    /**
     * Refresh storage information
     */
    fun refreshStorageInfo() {
        loadStorageInfo()
    }
}
