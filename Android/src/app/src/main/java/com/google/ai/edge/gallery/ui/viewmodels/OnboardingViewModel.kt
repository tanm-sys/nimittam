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
 * UI model for model selection
 */
data class ModelOptionUiModel(
    val type: ModelType,
    val name: String,
    val description: String,
    val parameters: String,
    val speed: String,
    val memory: String,
    val features: List<String>,
    val isAvailable: Boolean = true,
    val isDownloaded: Boolean = false
)

enum class ModelType {
    LITE, PRO, ULTRA
}

/**
 * Download state for model
 */
sealed class DownloadState {
    data object NotStarted : DownloadState()
    data class InProgress(val progress: Float) : DownloadState()
    data object Completed : DownloadState()
    data class Error(val message: String) : DownloadState()
}

/**
 * UI state for the Onboarding screen
 */
data class OnboardingUiState(
    val selectedModel: ModelType? = null,
    val modelOptions: List<ModelOptionUiModel> = emptyList(),
    val downloadState: DownloadState = DownloadState.NotStarted,
    val isLoading: Boolean = false,
    val isOnboardingCompleted: Boolean = false,
    val errorMessage: String? = null,
    val canContinue: Boolean = false
)

/**
 * Events emitted by the OnboardingViewModel
 */
sealed class OnboardingEvent {
    data object NavigateToChat : OnboardingEvent()
    class ShowError(val message: String) : OnboardingEvent()
    class ShowSuccess(val message: String) : OnboardingEvent()
    class DownloadProgressUpdate(val progress: Float) : OnboardingEvent()
    data object RequestStoragePermission : OnboardingEvent()
}

/**
 * ViewModel for the Onboarding screen.
 * UI-only version with mock data for preview/testing.
 */
class OnboardingViewModel : ViewModel() {

    // Mock model options for UI display
    private val mockModelOptions = listOf(
        ModelOptionUiModel(
            type = ModelType.LITE,
            name = "Nimittam Lite",
            description = "Fast & Efficient",
            parameters = "0.5B",
            speed = "Ultra Fast",
            memory = "Low",
            features = listOf("Quick responses", "Minimal battery", "Instant load"),
            isAvailable = true,
            isDownloaded = true
        ),
        ModelOptionUiModel(
            type = ModelType.PRO,
            name = "Nimittam Pro",
            description = "Balanced Performance",
            parameters = "1.5B",
            speed = "Fast",
            memory = "Medium",
            features = listOf("Smart reasoning", "Code generation", "Creative writing"),
            isAvailable = false,
            isDownloaded = false
        ),
        ModelOptionUiModel(
            type = ModelType.ULTRA,
            name = "Nimittam Ultra",
            description = "Maximum Capability",
            parameters = "3B",
            speed = "Standard",
            memory = "High",
            features = listOf("Complex analysis", "Long context", "Advanced reasoning"),
            isAvailable = false,
            isDownloaded = false
        )
    )

    private val _uiState = MutableStateFlow(
        OnboardingUiState(modelOptions = mockModelOptions)
    )
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<OnboardingEvent>()
    val events: SharedFlow<OnboardingEvent> = _events.asSharedFlow()

    /**
     * Select a model type
     */
    fun selectModel(modelType: ModelType) {
        val selectedOption = mockModelOptions.find { it.type == modelType }
        
        _uiState.update { state ->
            state.copy(
                selectedModel = modelType,
                canContinue = selectedOption?.isAvailable == true || selectedOption?.isDownloaded == true,
                errorMessage = null
            )
        }
    }

    /**
     * Continue after model selection
     */
    fun continueToApp() {
        val selectedModel = _uiState.value.selectedModel
        if (selectedModel == null) {
            viewModelScope.launch {
                _events.emit(OnboardingEvent.ShowError("Please select a model"))
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            // Simulate a brief loading delay
            kotlinx.coroutines.delay(500)
            _uiState.update { it.copy(isLoading = false, isOnboardingCompleted = true) }
            _events.emit(OnboardingEvent.NavigateToChat)
        }
    }

    /**
     * Download a model - no-op in UI-only mode
     */
    fun downloadModel(modelType: ModelType) {
        viewModelScope.launch {
            _events.emit(OnboardingEvent.ShowError("This model will be available in a future update"))
        }
    }

    /**
     * Skip onboarding (for development/testing)
     */
    fun skipOnboarding() {
        viewModelScope.launch {
            _uiState.update { it.copy(selectedModel = ModelType.LITE) }
            _events.emit(OnboardingEvent.NavigateToChat)
        }
    }

    /**
     * Clear error message
     */
    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
