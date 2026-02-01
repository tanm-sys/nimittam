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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
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
import kotlinx.coroutines.delay
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

data class ChatMessage(
    val id: String,
    val content: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val isComplete: Boolean = true
)

@Composable
fun ChatScreen(
    onNavigateToHistory: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToVoice: () -> Unit = {}
) {
    val messages = remember {
        mutableStateListOf(
            ChatMessage("1", "Hello! I'm Nimittam, your offline AI assistant. How can I help you today?", false)
        )
    }
    var inputText by remember { mutableStateOf("") }
    var isInputFocused by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    // Auto-scroll to bottom when new messages arrive
    // OPTIMIZATION: Use instant scroll for rapid updates to prevent UI blocking
    // during streaming responses. Only animate for the final message or when
    // updates are spaced apart by more than 100ms.
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            val lastIndex = messages.size - 1
            
            // Check if this is a rapid update (streaming scenario)
            val isRapidUpdate = messages.size > 1 && run {
                val lastMsg = messages[lastIndex]
                val prevMsg = messages[lastIndex - 1]
                // If messages arrived within 100ms, use instant scroll
                lastMsg.timestamp - prevMsg.timestamp < 100
            }
            
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
                modelName = "Nimittam Lite",
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
                items(messages, key = { it.id }) { message ->
                    ChatMessageItem(message = message)
                }
            }

            // Input composer
            ChatInputComposer(
                value = inputText,
                onValueChange = { inputText = it },
                isFocused = isInputFocused,
                onFocusChange = { isInputFocused = it },
                onSend = {
                    if (inputText.isNotBlank()) {
                        messages.add(ChatMessage(
                            id = System.currentTimeMillis().toString(),
                            content = inputText,
                            isUser = true
                        ))
                        inputText = ""

                        // Simulate AI response
                        // In real app, this would call the LLM engine
                    }
                },
                onVoiceClick = onNavigateToVoice
            )

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ChatHeader(
    modelName: String,
    onHistoryClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: History button
        IconButton(onClick = onHistoryClick) {
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
        IconButton(onClick = onSettingsClick) {
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
private fun ChatMessageItem(message: ChatMessage) {
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
    onVoiceClick: () -> Unit
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
                decorationBox = { innerTextField ->
                    Box {
                        if (value.isEmpty()) {
                            Text(
                                text = "Message Nimittam...",
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
                targetState = value.isNotEmpty(),
                transitionSpec = {
                    fadeIn(animationSpec = tween(200)) togetherWith
                            fadeOut(animationSpec = tween(200))
                },
                label = "input_action"
            ) { hasText ->
                if (hasText) {
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
