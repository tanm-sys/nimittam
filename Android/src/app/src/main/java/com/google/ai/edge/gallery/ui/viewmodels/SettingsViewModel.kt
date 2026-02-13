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
 * Storage info for UI display
 */
data class StorageInfo(
    val totalSpace: Long,
    val freeSpace: Long,
    val usedByApp: Long
)

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
 * UI-only version with mock data for preview/testing.
 */
class SettingsViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(
        SettingsUiState(
            storageInfo = StorageInfo(
                totalSpace = 128_000_000_000L,
                freeSpace = 64_000_000_000L,
                usedByApp = 500_000_000L
            )
        )
    )
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<SettingsEvent>()
    val events: SharedFlow<SettingsEvent> = _events.asSharedFlow()

    /**
     * Update temperature setting
     */
    fun updateTemperature(temperature: Float) {
        _uiState.update { it.copy(temperature = temperature) }
    }

    /**
     * Update max tokens setting
     */
    fun updateMaxTokens(maxTokens: Int) {
        _uiState.update { it.copy(maxTokens = maxTokens) }
    }

    /**
     * Update topP setting
     */
    fun updateTopP(topP: Float) {
        _uiState.update { it.copy(topP = topP) }
    }

    /**
     * Update topK setting
     */
    fun updateTopK(topK: Int) {
        _uiState.update { it.copy(topK = topK) }
    }

    /**
     * Update repeat penalty setting
     */
    fun updateRepeatPenalty(repeatPenalty: Float) {
        _uiState.update { it.copy(repeatPenalty = repeatPenalty) }
    }

    /**
     * Update dark theme setting
     */
    fun updateDarkTheme(enabled: Boolean) {
        _uiState.update { it.copy(darkTheme = enabled) }
        viewModelScope.launch {
            _events.emit(SettingsEvent.ShowSuccess(if (enabled) "Dark theme enabled" else "Light theme enabled"))
        }
    }

    /**
     * Update haptic feedback setting
     */
    fun updateHapticFeedback(enabled: Boolean) {
        _uiState.update { it.copy(hapticFeedbackEnabled = enabled) }
    }

    /**
     * Update notifications setting
     */
    fun updateNotifications(enabled: Boolean) {
        _uiState.update { it.copy(notificationsEnabled = enabled) }
        viewModelScope.launch {
            _events.emit(SettingsEvent.ShowSuccess(if (enabled) "Notifications enabled" else "Notifications disabled"))
        }
    }

    /**
     * Update selected model
     */
    fun updateSelectedModel(model: String) {
        _uiState.update { it.copy(selectedModel = model) }
        viewModelScope.launch {
            _events.emit(SettingsEvent.ShowSuccess("Model changed to $model"))
        }
    }

    /**
     * Update context size
     */
    fun updateContextSize(size: Int) {
        _uiState.update { it.copy(contextSize = size) }
    }

    /**
     * Update hardware backend
     */
    fun updateHardwareBackend(backend: String) {
        _uiState.update { it.copy(hardwareBackend = backend) }
        viewModelScope.launch {
            _events.emit(SettingsEvent.ShowSuccess("Hardware backend changed to $backend"))
        }
    }

    /**
     * Clear all chat history - no-op in UI-only mode
     */
    fun clearChatHistory() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            kotlinx.coroutines.delay(500)
            _uiState.update { it.copy(isLoading = false) }
            _events.emit(SettingsEvent.ShowSuccess("Chat history cleared"))
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
     * Refresh storage information - no-op in UI-only mode
     */
    fun refreshStorageInfo() { }
}
