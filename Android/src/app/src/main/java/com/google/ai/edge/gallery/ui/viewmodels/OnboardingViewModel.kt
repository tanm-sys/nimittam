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
import com.google.ai.edge.gallery.llm.DownloadProgress
import com.google.ai.edge.gallery.llm.ModelInfo
import com.google.ai.edge.gallery.llm.ModelManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

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
    object NotStarted : DownloadState()
    data class InProgress(val progress: DownloadProgress) : DownloadState()
    object Completed : DownloadState()
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
    object NavigateToChat : OnboardingEvent()
    data class ShowError(val message: String) : OnboardingEvent()
    data class ShowSuccess(val message: String) : OnboardingEvent()
    data class DownloadProgressUpdate(val progress: Float) : OnboardingEvent()
    object RequestStoragePermission : OnboardingEvent()
}

/**
 * ViewModel for the Onboarding screen.
 * Manages model selection and download state.
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val dataStoreRepository: DataStoreRepository,
    private val modelManager: ModelManager
) : ViewModel() {

    companion object {
        private const val TAG = "OnboardingViewModel"
        private const val LITE_MODEL_PATH = "qwen2.5-0.5b"
    }

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<OnboardingEvent>()
    val events: SharedFlow<OnboardingEvent> = _events.asSharedFlow()

    init {
        checkOnboardingStatus()
        loadModelOptions()
        extractBundledModel()
    }

    /**
     * Check if onboarding is already completed
     */
    private fun checkOnboardingStatus() {
        dataStoreRepository.onboardingCompletedFlow
            .catch { e ->
                Log.e(TAG, "Error checking onboarding status", e)
            }
            .onEach { completed ->
                _uiState.update { it.copy(isOnboardingCompleted = completed) }
                if (completed) {
                    _events.emit(OnboardingEvent.NavigateToChat)
                }
            }
            .launchIn(viewModelScope)
    }

    /**
     * Load available model options
     */
    private fun loadModelOptions() {
        val options = listOf(
            ModelOptionUiModel(
                type = ModelType.LITE,
                name = "Nimittam Lite",
                description = "Fast & Efficient",
                parameters = "0.5B",
                speed = "Ultra Fast",
                memory = "Low",
                features = listOf("Quick responses", "Minimal battery", "Instant load"),
                isAvailable = true,
                isDownloaded = true // Bundled model
            ),
            ModelOptionUiModel(
                type = ModelType.PRO,
                name = "Nimittam Pro",
                description = "Balanced Performance",
                parameters = "1.5B",
                speed = "Fast",
                memory = "Medium",
                features = listOf("Smart reasoning", "Code generation", "Creative writing"),
                isAvailable = false, // Would require download
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
                isAvailable = false, // Would require download
                isDownloaded = false
            )
        )

        _uiState.update { it.copy(modelOptions = options) }
    }

    /**
     * Extract bundled model from assets
     */
    private fun extractBundledModel() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true) }
                modelManager.extractBundledModel()
                _uiState.update { it.copy(isLoading = false) }
                Log.i(TAG, "Bundled model extracted successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error extracting bundled model", e)
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        errorMessage = "Failed to prepare model"
                    )
                }
            }
        }
    }

    /**
     * Select a model type
     */
    fun selectModel(modelType: ModelType) {
        val selectedOption = _uiState.value.modelOptions.find { it.type == modelType }
        
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
            try {
                _uiState.update { it.copy(isLoading = true) }

                // Save selected model type - check result for success
                val modelTypeResult = dataStoreRepository.updateSelectedModelType(selectedModel.name)
                modelTypeResult.onFailure { error ->
                    Log.e(TAG, "Failed to save model type", error)
                    _uiState.update { 
                        it.copy(
                            isLoading = false,
                            errorMessage = "Failed to save model selection"
                        )
                    }
                    _events.emit(OnboardingEvent.ShowError("Failed to save model selection"))
                    return@launch
                }

                // Mark onboarding as completed - check result for success
                val onboardingResult = dataStoreRepository.completeOnboarding()
                onboardingResult.onFailure { error ->
                    Log.e(TAG, "Failed to complete onboarding", error)
                    _uiState.update { 
                        it.copy(
                            isLoading = false,
                            errorMessage = "Failed to complete setup"
                        )
                    }
                    _events.emit(OnboardingEvent.ShowError("Failed to complete setup"))
                    return@launch
                }

                _uiState.update { it.copy(isLoading = false) }
                _events.emit(OnboardingEvent.NavigateToChat)

            } catch (e: Exception) {
                Log.e(TAG, "Error completing onboarding", e)
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        errorMessage = "Failed to complete setup"
                    )
                }
                _events.emit(OnboardingEvent.ShowError("Failed to complete setup"))
            }
        }
    }

    /**
     * Download a model (for non-bundled models)
     */
    fun downloadModel(modelType: ModelType) {
        // For now, only LITE model is available (bundled)
        // PRO and ULTRA would require actual download implementation
        viewModelScope.launch {
            _events.emit(OnboardingEvent.ShowError("This model will be available in a future update"))
        }
    }

    /**
     * Skip onboarding (for development/testing)
     */
    fun skipOnboarding() {
        viewModelScope.launch {
            try {
                // Select LITE as default
                dataStoreRepository.updateSelectedModelType(ModelType.LITE.name)
                dataStoreRepository.completeOnboarding()
                _events.emit(OnboardingEvent.NavigateToChat)
            } catch (e: Exception) {
                Log.e(TAG, "Error skipping onboarding", e)
            }
        }
    }

    /**
     * Clear error message
     */
    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
