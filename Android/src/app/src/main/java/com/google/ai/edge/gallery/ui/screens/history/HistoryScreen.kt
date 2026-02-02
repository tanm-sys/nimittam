/*
 * Copyright 2025 Tanmay Patil
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.google.ai.edge.gallery.ui.screens.history

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.ai.edge.gallery.ui.components.NoiseTexture
import com.google.ai.edge.gallery.ui.theme.AnimationDuration
import com.google.ai.edge.gallery.ui.theme.Gray12
import com.google.ai.edge.gallery.ui.theme.Gray24
import com.google.ai.edge.gallery.ui.theme.Gray40
import com.google.ai.edge.gallery.ui.theme.Gray64
import com.google.ai.edge.gallery.ui.theme.Gray80
import com.google.ai.edge.gallery.ui.theme.MaterialStandardEasing
import com.google.ai.edge.gallery.ui.theme.NimittamTheme
import com.google.ai.edge.gallery.ui.theme.PureBlack
import com.google.ai.edge.gallery.ui.theme.PureWhite
import com.google.ai.edge.gallery.ui.viewmodels.ConversationCategory
import com.google.ai.edge.gallery.ui.viewmodels.ConversationUiModel
import com.google.ai.edge.gallery.ui.viewmodels.HistoryEvent
import com.google.ai.edge.gallery.ui.viewmodels.HistoryViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Conversation History Screen
 * Vertical list with 88dp rows
 * Sharp left border indicating category
 * Swipe actions
 * Empty state with geometric illustration
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onNavigateBack: () -> Unit = {},
    onNewChat: () -> Unit = {},
    onConversationClick: (String) -> Unit = {},
    viewModel: HistoryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val hapticFeedback = LocalHapticFeedback.current
    val snackbarHostState = remember { SnackbarHostState() }
    var showContent by remember { mutableStateOf(false) }
    var conversationToDelete by remember { mutableStateOf<String?>(null) }

    // Handle events from ViewModel
    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is HistoryEvent.NavigateBack -> onNavigateBack()
                is HistoryEvent.NavigateToNewChat -> onNewChat()
                is HistoryEvent.NavigateToConversation -> onConversationClick(event.conversationId)
                is HistoryEvent.ShowError -> {
                    snackbarHostState.showSnackbar(event.message)
                }
                is HistoryEvent.ShowSuccess -> {
                    snackbarHostState.showSnackbar(event.message)
                }
                is HistoryEvent.ConfirmDelete -> {
                    conversationToDelete = event.conversationId
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        delay(100)
        showContent = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PureBlack)
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
            HistoryHeader(
                onNavigateBack = { viewModel.navigateBack() },
                onNewChat = { viewModel.createNewChat() }
            )

            // Content
            if (uiState.isLoading) {
                LoadingState()
            } else if (uiState.isEmpty) {
                EmptyHistoryState()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(
                        items = uiState.filteredConversations,
                        key = { it.id }
                    ) { conversation ->
                        AnimatedVisibility(
                            visible = showContent,
                            enter = fadeIn(
                                animationSpec = tween(
                                    400,
                                    easing = MaterialStandardEasing
                                )
                            ) + slideInHorizontally(
                                initialOffsetX = { -it / 4 },
                                animationSpec = tween(400)
                            ),
                            exit = fadeOut() + slideOutHorizontally()
                        ) {
                            SwipeableConversationItem(
                                conversation = conversation,
                                onClick = { viewModel.selectConversation(conversation.id) },
                                onDelete = { viewModel.requestDeleteConversation(conversation.id) }
                            )
                        }
                    }
                }
            }
        }

        // Snackbar
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        // Delete Confirmation Dialog
        conversationToDelete?.let { conversationId ->
            AlertDialog(
                onDismissRequest = { conversationToDelete = null },
                title = { Text("Delete Conversation") },
                text = { Text("This conversation will be permanently deleted. This action cannot be undone.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.deleteConversation(conversationId)
                            conversationToDelete = null
                        }
                    ) {
                        Text("Delete", color = androidx.compose.ui.graphics.Color.Red)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { conversationToDelete = null }) {
                        Text("Cancel")
                    }
                },
                containerColor = Gray12,
                titleContentColor = PureWhite,
                textContentColor = Gray80
            )
        }
    }
}

@Composable
private fun LoadingState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            color = PureWhite,
            strokeWidth = 2.dp
        )
    }
}

@Composable
private fun HistoryHeader(
    onNavigateBack: () -> Unit,
    onNewChat: () -> Unit
) {
    val hapticFeedback = LocalHapticFeedback.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = {
                    // Haptic feedback for navigation back (confirmation)
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onNavigateBack()
                }
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Gray80
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = "History",
                style = MaterialTheme.typography.headlineSmall,
                color = PureWhite
            )
        }

        IconButton(
            onClick = {
                // Haptic feedback for new chat (impact)
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                onNewChat()
            },
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(PureWhite)
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "New chat",
                tint = PureBlack
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableConversationItem(
    conversation: ConversationUiModel,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val hapticFeedback = LocalHapticFeedback.current
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                // Haptic feedback for delete action (warning)
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                onDelete()
                true
            } else {
                false
            }
        }
    )

    // Trigger haptic when swipe threshold is reached
    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart) {
            // Haptic feedback for swipe action (impact)
            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(androidx.compose.ui.graphics.Color.Red.copy(alpha = 0.2f))
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = PureWhite
                )
            }
        },
        content = {
            ConversationListItem(
                conversation = conversation,
                onClick = onClick,
                hapticFeedback = hapticFeedback
            )
        }
    )
}

@Composable
private fun ConversationListItem(
    conversation: ConversationUiModel,
    onClick: () -> Unit,
    hapticFeedback: androidx.compose.ui.hapticfeedback.HapticFeedback
) {
    val dateFormat = remember { SimpleDateFormat("MMM d", Locale.getDefault()) }
    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(88.dp)
            .clickable {
                // Haptic feedback for conversation selection (selection)
                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Category indicator (sharp left border)
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(48.dp)
                .background(getCategoryColor(conversation.category))
        )

        Spacer(modifier = Modifier.width(16.dp))

        // Content
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = conversation.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = PureWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Text(
                    text = dateFormat.format(Date(conversation.timestamp)),
                    style = MaterialTheme.typography.labelSmall,
                    color = Gray40
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = conversation.preview,
                style = MaterialTheme.typography.bodyMedium,
                color = Gray64,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun getCategoryColor(category: ConversationCategory): androidx.compose.ui.graphics.Color {
    return when (category) {
        ConversationCategory.GENERAL -> PureWhite
        ConversationCategory.WORK -> Gray80
        ConversationCategory.CREATIVE -> Gray64
        ConversationCategory.CODE -> Gray40
    }
}

@Composable
private fun EmptyHistoryState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Geometric illustration
        GeometricIllustration(
            modifier = Modifier.size(120.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "No conversations yet",
            style = MaterialTheme.typography.titleMedium,
            color = Gray80
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Start a new chat to begin",
            style = MaterialTheme.typography.bodyMedium,
            color = Gray64
        )
    }
}

@Composable
private fun GeometricIllustration(modifier: Modifier = Modifier) {
    val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition(label = "illustration")

    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
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
            // Outer circle
            drawCircle(
                color = Gray24,
                radius = radius,
                center = center,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f)
            )

            // Inner geometric shapes
            for (i in 0..2) {
                val angle = (i * 120f) * (Math.PI / 180f)
                val x = center.x + kotlin.math.cos(angle).toFloat() * radius * 0.5f
                val y = center.y + kotlin.math.sin(angle).toFloat() * radius * 0.5f

                drawCircle(
                    color = Gray40,
                    radius = 8f,
                    center = androidx.compose.ui.geometry.Offset(x, y)
                )
            }

            // Connection lines
            drawLine(
                color = Gray24,
                start = center,
                end = androidx.compose.ui.geometry.Offset(
                    center.x + radius * 0.5f,
                    center.y
                ),
                strokeWidth = 1f
            )
        }
    }
}

@Preview(device = "id:pixel_8")
@Composable
private fun HistoryScreenPreview() {
    NimittamTheme {
        HistoryScreen()
    }
}
