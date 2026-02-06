/*
 * Copyright 2025 Tanmay Patil
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.google.ai.edge.gallery.ui.viewmodels

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.ai.edge.gallery.common.OnboardingPreferences
import com.google.ai.edge.gallery.data.DataStoreRepository
import com.google.ai.edge.gallery.llm.DownloadProgress
import com.google.ai.edge.gallery.llm.ModelInfo
import com.google.ai.edge.gallery.llm.ModelManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
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
    data object NotStarted : DownloadState()
    data class InProgress(val progress: DownloadProgress) : DownloadState()
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
 * Manages model selection and download state with retry logic for DataStore operations.
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dataStoreRepository: DataStoreRepository,
    private val modelManager: ModelManager
) : ViewModel() {

    companion object {
        private const val TAG = "OnboardingViewModel"
        private const val LITE_MODEL_PATH = "qwen2.5-0.5b"
        
        // Retry configuration
        private const val MAX_RETRY_ATTEMPTS = 3
        private const val INITIAL_RETRY_DELAY_MS = 100L
        private const val MAX_RETRY_DELAY_MS = 1000L
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
     * Check if onboarding is already completed.
     * Also checks SharedPreferences fallback for cases where DataStore failed.
     */
    private fun checkOnboardingStatus() {
        // First check SharedPreferences fallback (for cases where DataStore failed)
        if (OnboardingPreferences.isOnboardingCompleted(context)) {
            Log.d(TAG, "Onboarding already completed (from SharedPreferences fallback)")
            _uiState.update { it.copy(isOnboardingCompleted = true) }
            viewModelScope.launch {
                _events.emit(OnboardingEvent.NavigateToChat)
            }
            return
        }
        
        // Then check DataStore
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
        val operationId = java.util.UUID.randomUUID().toString().take(8)
        val threadName = Thread.currentThread().name
        Log.d(TAG, "[DIAGNOSTIC] extractBundledModel[$operationId] START on thread: $threadName")

        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true) }
                Log.d(TAG, "[DIAGNOSTIC] extractBundledModel[$operationId] Calling modelManager.extractBundledModel()...")
                modelManager.extractBundledModel()
                _uiState.update { it.copy(isLoading = false) }
                Log.i(TAG, "[DIAGNOSTIC] extractBundledModel[$operationId] SUCCESS")
            } catch (e: Exception) {
                Log.e(TAG, "[DIAGNOSTIC] extractBundledModel[$operationId] ERROR: ${e.javaClass.simpleName}: ${e.message}", e)
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
     * Implements retry logic with exponential backoff for DataStore operations
     * to handle transient failures during first-launch initialization.
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
                Log.d(TAG, "Starting onboarding completion for model: ${selectedModel.name}")

                // Save selected model type with retry logic
                Log.d(TAG, "Attempting to save model type: ${selectedModel.name}")
                val modelTypeResult = saveModelTypeWithRetry(selectedModel.name)
                
                modelTypeResult.onFailure { error ->
                    Log.e(TAG, "Failed to save model type after $MAX_RETRY_ATTEMPTS attempts", error)
                    _uiState.update { 
                        it.copy(
                            isLoading = false,
                            errorMessage = "Failed to save model selection"
                        )
                    }
                    _events.emit(OnboardingEvent.ShowError("Failed to save model selection"))
                    return@launch
                }
                
                Log.d(TAG, "Successfully saved model type: ${selectedModel.name}")

                // Mark onboarding as completed with retry logic
                Log.d(TAG, "Attempting to complete onboarding")
                val onboardingResult = completeOnboardingWithRetry()
                
                onboardingResult.onFailure { error ->
                    Log.e(TAG, "Failed to complete onboarding after $MAX_RETRY_ATTEMPTS attempts", error)
                    _uiState.update { 
                        it.copy(
                            isLoading = false,
                            errorMessage = "Failed to complete setup"
                        )
                    }
                    _events.emit(OnboardingEvent.ShowError("Failed to complete setup"))
                    return@launch
                }
                
                Log.d(TAG, "Successfully completed onboarding")

                _uiState.update { it.copy(isLoading = false) }
                _events.emit(OnboardingEvent.NavigateToChat)

            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error completing onboarding", e)
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
     * Save model type with exponential backoff retry logic.
     * Handles transient DataStore initialization failures during first launch.
     * Falls back to SharedPreferences if DataStore fails.
     */
    private suspend fun saveModelTypeWithRetry(modelType: String): Result<Unit> {
        val operationId = java.util.UUID.randomUUID().toString().take(8)
        val threadName = Thread.currentThread().name
        Log.d(TAG, "[DIAGNOSTIC] saveModelTypeWithRetry[$operationId] START on thread: $threadName, modelType=$modelType")

        var lastException: Exception? = null
        var currentDelay = INITIAL_RETRY_DELAY_MS

        for (attempt in 1..MAX_RETRY_ATTEMPTS) {
            Log.d(TAG, "[DIAGNOSTIC] saveModelTypeWithRetry[$operationId] attempt $attempt/$MAX_RETRY_ATTEMPTS")

            try {
                Log.d(TAG, "[DIAGNOSTIC] saveModelTypeWithRetry[$operationId] Calling dataStoreRepository.updateSelectedModelType...")
                val result = dataStoreRepository.updateSelectedModelType(modelType)
                Log.d(TAG, "[DIAGNOSTIC] saveModelTypeWithRetry[$operationId] Result received: isSuccess=${result.isSuccess}, isFailure=${result.isFailure}")

                result.onSuccess {
                    Log.d(TAG, "[DIAGNOSTIC] saveModelTypeWithRetry[$operationId] SUCCESS on attempt $attempt")
                    return Result.success(Unit)
                }

                result.onFailure { error ->
                    Log.w(TAG, "[DIAGNOSTIC] saveModelTypeWithRetry[$operationId] attempt $attempt FAILED: ${error.javaClass.simpleName}: ${error.message}", error)
                    lastException = error as? Exception ?: Exception(error)
                }

                // Don't delay on the last attempt
                if (attempt < MAX_RETRY_ATTEMPTS) {
                    Log.d(TAG, "[DIAGNOSTIC] saveModelTypeWithRetry[$operationId] waiting ${currentDelay}ms before retry")
                    delay(currentDelay)
                    currentDelay = (currentDelay * 2).coerceAtMost(MAX_RETRY_DELAY_MS)
                }

            } catch (e: Exception) {
                Log.e(TAG, "[DIAGNOSTIC] saveModelTypeWithRetry[$operationId] attempt $attempt THREW EXCEPTION: ${e.javaClass.simpleName}: ${e.message}", e)
                lastException = e

                if (attempt < MAX_RETRY_ATTEMPTS) {
                    delay(currentDelay)
                    currentDelay = (currentDelay * 2).coerceAtMost(MAX_RETRY_DELAY_MS)
                }
            }
        }

        // Fallback to SharedPreferences if DataStore fails
        Log.w(TAG, "[DIAGNOSTIC] saveModelTypeWithRetry[$operationId] DataStore failed, trying SharedPreferences fallback")
        return try {
            OnboardingPreferences.saveModelType(context, modelType)
            Log.i(TAG, "[DIAGNOSTIC] saveModelTypeWithRetry[$operationId] SUCCESS via SharedPreferences fallback")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "[DIAGNOSTIC] saveModelTypeWithRetry[$operationId] SharedPreferences fallback also failed", e)
            Result.failure(lastException ?: e)
        }
    }
    
    /**
     * Complete onboarding with exponential backoff retry logic.
     * Falls back to SharedPreferences if DataStore fails.
     */
    private suspend fun completeOnboardingWithRetry(): Result<Unit> {
        var lastException: Exception? = null
        var currentDelay = INITIAL_RETRY_DELAY_MS
        
        for (attempt in 1..MAX_RETRY_ATTEMPTS) {
            try {
                Log.d(TAG, "completeOnboardingWithRetry: attempt $attempt/$MAX_RETRY_ATTEMPTS")
                
                val result = dataStoreRepository.completeOnboarding()
                
                result.onSuccess {
                    Log.d(TAG, "completeOnboardingWithRetry: success on attempt $attempt")
                    return Result.success(Unit)
                }
                
                result.onFailure { error ->
                    Log.w(TAG, "completeOnboardingWithRetry: attempt $attempt failed", error)
                    lastException = error as? Exception ?: Exception(error)
                }
                
                // Don't delay on the last attempt
                if (attempt < MAX_RETRY_ATTEMPTS) {
                    Log.d(TAG, "completeOnboardingWithRetry: waiting ${currentDelay}ms before retry")
                    delay(currentDelay)
                    currentDelay = (currentDelay * 2).coerceAtMost(MAX_RETRY_DELAY_MS)
                }
                
            } catch (e: Exception) {
                Log.w(TAG, "completeOnboardingWithRetry: attempt $attempt threw exception", e)
                lastException = e
                
                if (attempt < MAX_RETRY_ATTEMPTS) {
                    delay(currentDelay)
                    currentDelay = (currentDelay * 2).coerceAtMost(MAX_RETRY_DELAY_MS)
                }
            }
        }
        
        // Fallback to SharedPreferences if DataStore fails
        Log.w(TAG, "completeOnboardingWithRetry: DataStore failed, trying SharedPreferences fallback")
        return try {
            OnboardingPreferences.setOnboardingCompleted(context, true)
            Log.i(TAG, "completeOnboardingWithRetry: SUCCESS via SharedPreferences fallback")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "completeOnboardingWithRetry: SharedPreferences fallback also failed", e)
            Result.failure(lastException ?: e)
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
     * Uses the same retry logic as normal onboarding to ensure reliability.
     */
    fun skipOnboarding() {
        viewModelScope.launch {
            try {
                Log.d(TAG, "Skipping onboarding with LITE model as default")
                
                // Select LITE as default with retry logic
                val modelTypeResult = saveModelTypeWithRetry(ModelType.LITE.name)
                modelTypeResult.onFailure { error ->
                    Log.e(TAG, "Failed to save model type during skip", error)
                    _events.emit(OnboardingEvent.ShowError("Failed to save model selection"))
                    return@launch
                }
                
                val onboardingResult = completeOnboardingWithRetry()
                onboardingResult.onFailure { error ->
                    Log.e(TAG, "Failed to complete onboarding during skip", error)
                    _events.emit(OnboardingEvent.ShowError("Failed to complete setup"))
                    return@launch
                }
                
                Log.d(TAG, "Successfully skipped onboarding")
                _events.emit(OnboardingEvent.NavigateToChat)
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error skipping onboarding", e)
                _events.emit(OnboardingEvent.ShowError("Failed to complete setup"))
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
