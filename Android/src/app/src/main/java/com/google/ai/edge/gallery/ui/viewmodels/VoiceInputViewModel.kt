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
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.random.Random

/**
 * State of voice input
 */
enum class VoiceInputState {
    IDLE,
    LISTENING,
    PROCESSING,
    COMPLETE,
    ERROR
}

/**
 * UI state for the Voice Input screen
 */
data class VoiceInputUiState(
    val state: VoiceInputState = VoiceInputState.IDLE,
    val audioLevel: Float = 0.3f,
    val transcription: String = "",
    val errorMessage: String? = null,
    val isRecording: Boolean = false,
    val recordingDurationMs: Long = 0
)

/**
 * Events emitted by the VoiceInputViewModel
 */
sealed class VoiceInputEvent {
    data class TranscriptionComplete(val text: String) : VoiceInputEvent()
    object Dismiss : VoiceInputEvent()
    data class ShowError(val message: String) : VoiceInputEvent()
}

/**
 * ViewModel for the Voice Input screen.
 * Manages voice recording state and speech-to-text (mock implementation).
 */
@HiltViewModel
class VoiceInputViewModel @Inject constructor() : ViewModel() {

    companion object {
        private const val TAG = "VoiceInputViewModel"
        private const val MAX_RECORDING_DURATION_MS = 30000L // 30 seconds
        private val SIMULATION_WORDS = listOf(
            "Hello",
            "How can you help me today",
            "What's the weather like",
            "Tell me a joke",
            "Explain quantum computing",
            "Write a poem about nature",
            "Help me with my homework",
            "What is the meaning of life",
            "Translate this to Spanish",
            "Summarize this article"
        )
    }

    private val _uiState = MutableStateFlow(VoiceInputUiState())
    val uiState: StateFlow<VoiceInputUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<VoiceInputEvent>()
    val events: SharedFlow<VoiceInputEvent> = _events.asSharedFlow()

    private var recordingJob: Job? = null
    private var audioSimulationJob: Job? = null
    private var startTime: Long = 0

    /**
     * Start voice recording
     */
    fun startRecording() {
        if (_uiState.value.state == VoiceInputState.LISTENING) return

        viewModelScope.launch {
            try {
                _uiState.update { 
                    it.copy(
                        state = VoiceInputState.LISTENING,
                        isRecording = true,
                        errorMessage = null,
                        transcription = ""
                    )
                }
                startTime = System.currentTimeMillis()

                // Start audio level simulation
                simulateAudioLevels()

                // Start recording duration tracking
                recordingJob = launch {
                    while (_uiState.value.isRecording) {
                        val duration = System.currentTimeMillis() - startTime
                        _uiState.update { it.copy(recordingDurationMs = duration) }
                        
                        // Auto-stop after max duration
                        if (duration >= MAX_RECORDING_DURATION_MS) {
                            stopRecording()
                            break
                        }
                        
                        delay(100)
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error starting recording", e)
                _uiState.update { 
                    it.copy(
                        state = VoiceInputState.ERROR,
                        errorMessage = "Failed to start recording"
                    )
                }
                _events.emit(VoiceInputEvent.ShowError("Failed to start recording"))
            }
        }
    }

    /**
     * Stop voice recording and start processing
     */
    fun stopRecording() {
        if (_uiState.value.state != VoiceInputState.LISTENING) return

        viewModelScope.launch {
            try {
                // Stop recording
                recordingJob?.cancel()
                audioSimulationJob?.cancel()
                
                _uiState.update { 
                    it.copy(
                        state = VoiceInputState.PROCESSING,
                        isRecording = false,
                        audioLevel = 0f
                    )
                }

                // Simulate speech-to-text processing
                simulateSpeechToText()

            } catch (e: Exception) {
                Log.e(TAG, "Error stopping recording", e)
                _uiState.update { 
                    it.copy(
                        state = VoiceInputState.ERROR,
                        errorMessage = "Failed to process recording"
                    )
                }
                _events.emit(VoiceInputEvent.ShowError("Failed to process recording"))
            }
        }
    }

    /**
     * Cancel recording without processing
     */
    fun cancelRecording() {
        recordingJob?.cancel()
        audioSimulationJob?.cancel()
        _uiState.update { 
            VoiceInputUiState(
                state = VoiceInputState.IDLE
            )
        }
    }

    /**
     * Dismiss the voice input screen
     */
    fun dismiss() {
        cancelRecording()
        viewModelScope.launch {
            _events.emit(VoiceInputEvent.Dismiss)
        }
    }

    /**
     * Confirm transcription and send
     */
    fun confirmTranscription() {
        val transcription = _uiState.value.transcription
        if (transcription.isNotBlank()) {
            viewModelScope.launch {
                _events.emit(VoiceInputEvent.TranscriptionComplete(transcription))
            }
        }
    }

    /**
     * Retry recording
     */
    fun retry() {
        _uiState.update { 
            VoiceInputUiState(
                state = VoiceInputState.IDLE
            )
        }
    }

    /**
     * Clear error message
     */
    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    /**
     * Simulate audio level changes during recording
     */
    private fun simulateAudioLevels() {
        audioSimulationJob = viewModelScope.launch {
            try {
                while (_uiState.value.isRecording) {
                    // Simulate varying audio levels
                    val level = 0.2f + Random.nextFloat() * 0.6f
                    _uiState.update { it.copy(audioLevel = level) }
                    delay(50 + Random.nextLong(100))
                }
            } catch (e: CancellationException) {
                // Expected when cancelled
            }
        }
    }

    /**
     * Simulate speech-to-text processing
     * In a real implementation, this would use Android's SpeechRecognizer or a cloud API
     */
    private suspend fun simulateSpeechToText() {
        try {
            // Simulate processing delay
            delay(800)

            // Select a random phrase based on recording duration
            val duration = _uiState.value.recordingDurationMs
            val phrase = if (duration < 1000) {
                SIMULATION_WORDS.random()
            } else {
                // For longer recordings, combine phrases
                val numPhrases = (duration / 2000).coerceIn(1, 3).toInt()
                SIMULATION_WORDS.shuffled().take(numPhrases).joinToString(" ")
            }

            _uiState.update { 
                it.copy(
                    state = VoiceInputState.COMPLETE,
                    transcription = phrase
                )
            }

            // Auto-confirm after a short delay
            delay(1000)
            confirmTranscription()

        } catch (e: CancellationException) {
            // Expected when cancelled
        } catch (e: Exception) {
            Log.e(TAG, "Error in speech-to-text", e)
            _uiState.update { 
                it.copy(
                    state = VoiceInputState.ERROR,
                    errorMessage = "Speech recognition failed"
                )
            }
            _events.emit(VoiceInputEvent.ShowError("Speech recognition failed"))
        }
    }

    /**
     * Format recording duration as MM:SS
     */
    fun formatDuration(durationMs: Long): String {
        val seconds = (durationMs / 1000) % 60
        val minutes = (durationMs / 1000) / 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    override fun onCleared() {
        super.onCleared()
        recordingJob?.cancel()
        audioSimulationJob?.cancel()
    }
}
