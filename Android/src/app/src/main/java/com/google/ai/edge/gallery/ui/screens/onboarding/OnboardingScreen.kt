/*
 * Copyright 2025 Tanmay Patil
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.google.ai.edge.gallery.ui.screens.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.ai.edge.gallery.ui.components.NoiseTexture
import com.google.ai.edge.gallery.ui.components.PulsingDot
import com.google.ai.edge.gallery.ui.components.GlassmorphismLevel
import com.google.ai.edge.gallery.ui.components.glassmorphic
import com.google.ai.edge.gallery.ui.theme.AnimationDuration
import com.google.ai.edge.gallery.ui.theme.Gray12
import com.google.ai.edge.gallery.ui.theme.Gray24
import com.google.ai.edge.gallery.ui.theme.Gray40
import com.google.ai.edge.gallery.ui.theme.Gray64
import com.google.ai.edge.gallery.ui.theme.Gray80
import com.google.ai.edge.gallery.ui.theme.LiteModelCardShape
import com.google.ai.edge.gallery.ui.theme.MaterialStandardEasing
import com.google.ai.edge.gallery.ui.theme.NimittamTheme
import com.google.ai.edge.gallery.ui.theme.ProModelCardShape
import com.google.ai.edge.gallery.ui.theme.PureBlack
import com.google.ai.edge.gallery.ui.theme.PureWhite
import com.google.ai.edge.gallery.ui.theme.SuperellipseRoundedShape
import com.google.ai.edge.gallery.ui.theme.UltraModelCardShape
import com.google.ai.edge.gallery.ui.viewmodels.ModelOptionUiModel
import com.google.ai.edge.gallery.ui.viewmodels.ModelType
import com.google.ai.edge.gallery.ui.viewmodels.OnboardingEvent
import com.google.ai.edge.gallery.ui.viewmodels.OnboardingViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

/**
 * Onboarding Model Selection Screen
 * Dark background, glassmorphic cards at Level 2 elevation
 * "Choose Your Intelligence" title
 * Three model cards with different corner treatments
 * Offline badge with pulsing dot
 */

@Composable
fun OnboardingScreen(
    onModelSelected: (ModelType) -> Unit = {},
    onNavigateToChat: () -> Unit = {},
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val hapticFeedback = LocalHapticFeedback.current
    val snackbarHostState = remember { SnackbarHostState() }
    var showTitle by remember { mutableStateOf(false) }
    var showCards by remember { mutableStateOf(false) }
    var showBadge by remember { mutableStateOf(false) }

    // Handle events from ViewModel
    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is OnboardingEvent.NavigateToChat -> onNavigateToChat()
                is OnboardingEvent.ShowError -> {
                    snackbarHostState.showSnackbar(event.message)
                }
                is OnboardingEvent.ShowSuccess -> {
                    snackbarHostState.showSnackbar(event.message)
                }
                is OnboardingEvent.DownloadProgressUpdate -> {
                    // Handle download progress update
                }
                OnboardingEvent.RequestStoragePermission -> {
                    // Handle storage permission request
                }
            }
        }
    }

    // Staggered animation
    LaunchedEffect(Unit) {
        delay(200)
        showTitle = true
        delay(300)
        showCards = true
        delay(400)
        showBadge = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PureBlack)
    ) {
        // Noise texture background
        NoiseTexture(
            modifier = Modifier.fillMaxSize(),
            opacity = 0.01f
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Offline badge with pulsing dot
            AnimatedVisibility(
                visible = showBadge,
                enter = fadeIn(animationSpec = tween(400))
            ) {
                OfflineBadge()
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Title
            AnimatedVisibility(
                visible = showTitle,
                enter = fadeIn(animationSpec = tween(600)) +
                        slideInVertically(initialOffsetY = { it / 4 })
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Choose Your",
                        style = MaterialTheme.typography.headlineMedium,
                        color = Gray80
                    )
                    Text(
                        text = "Intelligence",
                        style = MaterialTheme.typography.headlineLarge,
                        color = PureWhite
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Subtitle
            AnimatedVisibility(
                visible = showTitle,
                enter = fadeIn(animationSpec = tween(600, delayMillis = 200))
            ) {
                Text(
                    text = "Select the model that fits your needs",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Gray64
                )
            }

            Spacer(modifier = Modifier.height(48.dp))

            // Loading state
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    color = PureWhite,
                    strokeWidth = 2.dp
                )
            } else {
                // Model cards
                uiState.modelOptions.forEachIndexed { index, model ->
                    AnimatedVisibility(
                        visible = showCards,
                        enter = fadeIn(
                            animationSpec = tween(
                                500,
                                delayMillis = index * 150,
                                easing = MaterialStandardEasing
                            )
                        ) + slideInVertically(
                            initialOffsetY = { it / 2 },
                            animationSpec = tween(
                                500,
                                delayMillis = index * 150
                            )
                        )
                    ) {
                        ModelCard(
                            model = model,
                            isSelected = uiState.selectedModel == model.type,
                            onClick = {
                                // Haptic feedback for model selection (confirmation)
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.selectModel(model.type)
                            }
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Continue button
                AnimatedVisibility(
                    visible = uiState.selectedModel != null,
                    enter = fadeIn(animationSpec = tween(300))
                ) {
                    ContinueButton(
                        onClick = {
                            uiState.selectedModel?.let {
                                // Haptic feedback for completion (success)
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                // Only notify that model was selected - ViewModel will handle navigation
                                onModelSelected(it)
                                // Save data and navigate via ViewModel event
                                viewModel.continueToApp()
                            }
                        },
                        enabled = uiState.canContinue && !uiState.isLoading
                    )
                }
            }
        }

        // Snackbar
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun OfflineBadge() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier
            .glassmorphic(GlassmorphismLevel.LIGHT, RoundedCornerShape(20.dp))
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        PulsingDot(
            color = PureWhite,
            size = 6f
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "100% OFFLINE",
            style = MaterialTheme.typography.labelSmall,
            color = Gray80,
            letterSpacing = 2.sp
        )
    }
}

@Composable
private fun ModelCard(
    model: ModelOptionUiModel,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val shape = when (model.type) {
        ModelType.LITE -> LiteModelCardShape
        ModelType.PRO -> ProModelCardShape
        ModelType.ULTRA -> UltraModelCardShape
    }

    val borderColor = if (isSelected) PureWhite else Gray24
    val backgroundAlpha = if (isSelected) 0.12f else 0.05f

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = borderColor,
                shape = shape
            ),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = PureWhite.copy(alpha = backgroundAlpha)
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = model.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = PureWhite,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = model.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = Gray64
                    )
                }

                // Selection indicator
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = PureWhite,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Specs row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                SpecItem(
                    icon = Icons.Outlined.Memory,
                    label = "Parameters",
                    value = model.parameters
                )
                SpecItem(
                    icon = Icons.Outlined.Speed,
                    label = "Speed",
                    value = model.speed
                )
                SpecItem(
                    icon = Icons.Outlined.Bolt,
                    label = "Memory",
                    value = model.memory
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Features
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                model.features.forEach { feature ->
                    FeatureChip(text = feature)
                }
            }

            // Availability indicator
            if (!model.isAvailable) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Available in future update",
                    style = MaterialTheme.typography.labelSmall,
                    color = Gray40
                )
            }
        }
    }
}

@Composable
private fun SpecItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = Gray64,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = PureWhite
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Gray40
        )
    }
}

@Composable
private fun FeatureChip(text: String) {
    Box(
        modifier = Modifier
            .glassmorphic(GlassmorphismLevel.LIGHT, RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = Gray80
        )
    }
}

@Composable
private fun ContinueButton(
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    val hapticFeedback = LocalHapticFeedback.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (enabled) PureWhite else Gray40)
            .clickable(enabled = enabled) {
                // Haptic feedback for continue button (impact)
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Continue",
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) PureBlack else Gray64,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Preview(device = "id:pixel_8")
@Composable
private fun OnboardingScreenPreview() {
    NimittamTheme {
        OnboardingScreen()
    }
}
