/*
 * Copyright 2025 Tanmay Patil
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.google.ai.edge.gallery.ui.screens.chat

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.ai.edge.gallery.ui.components.NoiseTexture
import com.google.ai.edge.gallery.ui.components.OfflineStatusWaveform
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.sp
import com.google.ai.edge.gallery.ui.components.GlassmorphismLevel
import com.google.ai.edge.gallery.ui.components.glassmorphic
import com.google.ai.edge.gallery.ui.theme.AiMessageShape
import com.google.ai.edge.gallery.ui.theme.AnimationDuration
import com.google.ai.edge.gallery.ui.theme.ChatMessageText
import com.google.ai.edge.gallery.ui.theme.ChatTimestamp
import com.google.ai.edge.gallery.ui.theme.Gray12
import com.google.ai.edge.gallery.ui.theme.Gray24
import com.google.ai.edge.gallery.ui.theme.Gray40
import com.google.ai.edge.gallery.ui.theme.Gray64
import com.google.ai.edge.gallery.ui.theme.Gray80
import com.google.ai.edge.gallery.ui.theme.InputComposerCollapsedShape
import com.google.ai.edge.gallery.ui.theme.InputComposerExpandedShape
import com.google.ai.edge.gallery.ui.theme.MaterialStandardEasing
import com.google.ai.edge.gallery.ui.theme.NimittamTheme
import com.google.ai.edge.gallery.ui.theme.Obsidian
import com.google.ai.edge.gallery.ui.theme.PureBlack
import com.google.ai.edge.gallery.ui.theme.PureWhite
import com.google.ai.edge.gallery.ui.theme.UserMessageShape
import com.google.ai.edge.gallery.ui.viewmodels.ChatEvent
import com.google.ai.edge.gallery.ui.viewmodels.ChatMessageUiModel
import com.google.ai.edge.gallery.ui.viewmodels.ChatViewModel
import kotlinx.coroutines.flow.collectLatest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Main Chat Interface (PRIMARY SCREEN)
 * Header with Nimittam logomark, model name, settings icon
 * User messages: right-aligned, obsidian #0A0A0A, sharp corners, 1px border
 * AI messages: left-aligned, pure black, 28dp superellipse, left border accent
 * Input composer: Level 3 glassmorphism, 56dp→200dp expanding
 * Offline status bar: 32dp height, animated waveform (DeepMind style)
 */

@Composable
fun ChatScreen(
    onNavigateToHistory: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToVoice: () -> Unit = {},
    viewModel: ChatViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val hapticFeedback = LocalHapticFeedback.current

    // Handle events from ViewModel
    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is ChatEvent.ScrollToBottom -> {
                    if (uiState.messages.isNotEmpty()) {
                        listState.animateScrollToItem(uiState.messages.size - 1)
                    }
                }
                is ChatEvent.ShowError -> {
                    // Error is shown via Snackbar
                }
                else -> {}
            }
        }
    }

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            val lastIndex = uiState.messages.size - 1
            val lastMessage = uiState.messages.lastOrNull()
            
            // Check if this is a rapid update (streaming scenario)
            val isRapidUpdate = uiState.messages.size > 1 && lastMessage?.isComplete == false
            
            if (isRapidUpdate) {
                // Use instant scroll for streaming to avoid animation overhead
                listState.scrollToItem(lastIndex)
            } else {
                // Use smooth animation for user-initiated messages
                listState.animateScrollToItem(lastIndex)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PureBlack)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        // Noise texture
        NoiseTexture(
            modifier = Modifier.fillMaxSize(),
            opacity = 0.01f
        )

        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Header
            ChatHeader(
                modelName = uiState.modelName,
                onHistoryClick = onNavigateToHistory,
                onSettingsClick = onNavigateToSettings
            )

            // Offline status bar with waveform
            OfflineStatusBar()

            // Messages list
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                state = listState,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Bottom)
            ) {
                items(uiState.messages, key = { it.id }) { message ->
                    ChatMessageItem(message = message)
                }
            }

            // Error message
            uiState.errorMessage?.let { error ->
                Snackbar(
                    modifier = Modifier.padding(16.dp),
                    action = {
                        TextButton(onClick = { viewModel.retryLastMessage() }) {
                            Text("Retry", color = PureWhite)
                        }
                    }
                ) {
                    Text(error)
                }
            }

            // Input composer
            ChatInputComposer(
                value = uiState.inputText,
                onValueChange = { viewModel.updateInputText(it) },
                isFocused = false,
                onFocusChange = { },
                onSend = {
                    if (uiState.inputText.isNotBlank()) {
                        // Haptic feedback for sending message (impact)
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.sendMessage()
                    }
                },
                onVoiceClick = {
                    // Haptic feedback for voice input activation (impact)
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    onNavigateToVoice()
                },
                isGenerating = uiState.isGenerating,
                hapticFeedback = hapticFeedback
            )

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun TextButton(
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        content()
    }
}

@Composable
private fun ChatHeader(
    modelName: String,
    onHistoryClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val hapticFeedback = LocalHapticFeedback.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: History button
        IconButton(
            onClick = {
                // Haptic feedback for navigation (confirmation)
                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onHistoryClick()
            }
        ) {
            Icon(
                imageVector = Icons.Default.History,
                contentDescription = "History",
                tint = Gray80,
                modifier = Modifier.size(24.dp)
            )
        }

        // Center: Logomark and model name
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Geometric node logomark
            Logomark(
                modifier = Modifier.size(28.dp)
            )

            Column {
                Text(
                    text = "Nimittam",
                    style = MaterialTheme.typography.titleSmall,
                    color = PureWhite,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = modelName,
                    style = MaterialTheme.typography.labelSmall,
                    color = Gray64
                )
            }
        }

        // Right: Settings button
        IconButton(
            onClick = {
                // Haptic feedback for navigation (confirmation)
                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onSettingsClick()
            }
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Settings",
                tint = Gray80,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun Logomark(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "logomark")

    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(20000),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    androidx.compose.foundation.Canvas(modifier = modifier) {
        val center = androidx.compose.ui.geometry.Offset(size.width / 2, size.height / 2)
        val radius = size.minDimension * 0.4f

        withTransform({
            rotate(rotation, center)
        }) {
            // Draw hexagon outline
            val path = androidx.compose.ui.graphics.Path()
            for (i in 0..6) {
                val angle = (i * 60f - 30f) * (Math.PI / 180f)
                val x = center.x + kotlin.math.cos(angle).toFloat() * radius
                val y = center.y + kotlin.math.sin(angle).toFloat() * radius
                if (i == 0) {
                    path.moveTo(x, y)
                } else {
                    path.lineTo(x, y)
                }
            }
            path.close()

            drawPath(
                path = path,
                color = PureWhite,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f)
            )

            // Center dot
            drawCircle(
                color = PureWhite,
                radius = radius * 0.2f,
                center = center
            )
        }
    }
}

@Composable
private fun OfflineStatusBar() {
    val infiniteTransition = rememberInfiniteTransition(label = "waveform")

    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2 * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(3000),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp)
            .glassmorphic(GlassmorphismLevel.LIGHT)
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Offline indicator dot
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(PureWhite)
            )

            Text(
                text = "OFFLINE",
                style = MaterialTheme.typography.labelSmall,
                color = Gray80,
                letterSpacing = 1.sp
            )

            // Animated waveform
            OfflineStatusWaveform(
                modifier = Modifier.weight(1f),
                isActive = true
            )
        }
    }
}

@Composable
private fun ChatMessageItem(message: ChatMessageUiModel) {
    val shape = if (message.isUser) UserMessageShape else AiMessageShape
    val backgroundColor = if (message.isUser) Obsidian else PureBlack
    val borderColor = if (message.isUser) Gray24 else Gray12
    val alignment = if (message.isUser) Alignment.CenterEnd else Alignment.CenterStart
    val borderWidth = if (message.isUser) 1.dp else 0.dp
    val accentBorder = if (!message.isUser) {
        Modifier.border(
            width = 2.dp,
            color = PureWhite.copy(alpha = 0.5f),
            shape = RoundedCornerShape(topStart = 0.dp, topEnd = 28.dp, bottomStart = 28.dp, bottomEnd = 28.dp)
        )
    } else Modifier

    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = alignment
    ) {
        Column(
            horizontalAlignment = if (message.isUser) Alignment.End else Alignment.Start
        ) {
            Box(
                modifier = Modifier
                    .then(accentBorder)
                    .clip(shape)
                    .background(backgroundColor)
                    .then(
                        if (message.isUser) {
                            Modifier.border(borderWidth, borderColor, shape)
                        } else Modifier
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    text = message.content,
                    style = ChatMessageText,
                    color = PureWhite
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = timeFormat.format(Date(message.timestamp)),
                style = ChatTimestamp,
                color = Gray40
            )
        }
    }
}

@Composable
private fun ChatInputComposer(
    value: String,
    onValueChange: (String) -> Unit,
    isFocused: Boolean,
    onFocusChange: (Boolean) -> Unit,
    onSend: () -> Unit,
    onVoiceClick: () -> Unit,
    isGenerating: Boolean,
    hapticFeedback: androidx.compose.ui.hapticfeedback.HapticFeedback
) {
    val shape = if (isFocused || value.isNotEmpty()) {
        InputComposerExpandedShape
    } else {
        InputComposerCollapsedShape
    }

    val height = if (isFocused || value.isNotEmpty()) {
        120.dp
    } else {
        56.dp
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .glassmorphic(GlassmorphismLevel.HEAVY, shape)
            .clip(shape)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp, max = 200.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Text input
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { onFocusChange(it.isFocused) },
                textStyle = TextStyle(
                    color = PureWhite,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Normal
                ),
                cursorBrush = SolidColor(PureWhite),
                enabled = !isGenerating,
                decorationBox = { innerTextField ->
                    Box {
                        if (value.isEmpty()) {
                            Text(
                                text = if (isGenerating) "Generating..." else "Message Nimittam...",
                                color = Gray40,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                        innerTextField()
                    }
                }
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Voice or Send button
            AnimatedContent(
                targetState = value.isNotEmpty() || isGenerating,
                transitionSpec = {
                    fadeIn(animationSpec = tween(200)) togetherWith
                            fadeOut(animationSpec = tween(200))
                },
                label = "input_action"
            ) { hasTextOrGenerating ->
                if (hasTextOrGenerating) {
                    if (isGenerating) {
                        // Loading indicator
                        CircularProgressIndicator(
                            modifier = Modifier.size(40.dp),
                            color = PureWhite,
                            strokeWidth = 2.dp
                        )
                    } else {
                        // Send button
                        IconButton(
                            onClick = onSend,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(PureWhite)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send",
                                tint = PureBlack,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                } else {
                    // Voice button
                    IconButton(
                        onClick = onVoiceClick,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Voice input",
                            tint = Gray80,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}

@Preview(device = "id:pixel_8")
@Composable
private fun ChatScreenPreview() {
    NimittamTheme {
        ChatScreen()
    }
}
