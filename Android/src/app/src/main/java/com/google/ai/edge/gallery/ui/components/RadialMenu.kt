/*
 * Copyright 2025 Tanmay Patil
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.google.ai.edge.gallery.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Column
import com.google.ai.edge.gallery.ui.components.GlassmorphismLevel
import com.google.ai.edge.gallery.ui.components.glassmorphic
import com.google.ai.edge.gallery.ui.theme.AnimationDuration
import com.google.ai.edge.gallery.ui.theme.Gray40
import com.google.ai.edge.gallery.ui.theme.Gray64
import com.google.ai.edge.gallery.ui.theme.Gray80
import com.google.ai.edge.gallery.ui.theme.MaterialStandardEasing
import com.google.ai.edge.gallery.ui.theme.NimittamTheme
import com.google.ai.edge.gallery.ui.theme.PureBlack
import com.google.ai.edge.gallery.ui.theme.PureWhite
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Long-Press Context Menu (Radial Menu)
 * Radial menu with 6 options
 * Circular arrangement
 * Blur 40px background
 * Shape morphing selection
 */

data class RadialMenuItem(
    val id: String,
    val icon: ImageVector,
    val label: String
)

@Composable
fun RadialContextMenu(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    onItemSelected: (String) -> Unit,
    centerPosition: Offset = Offset(540f, 960f)  // Center of 1080x1920 screen
) {
    val menuItems = remember {
        listOf(
            RadialMenuItem("copy", Icons.Default.ContentCopy, "Copy"),
            RadialMenuItem("reply", Icons.AutoMirrored.Filled.Reply, "Reply"),
            RadialMenuItem("edit", Icons.Default.Edit, "Edit"),
            RadialMenuItem("share", Icons.Default.Share, "Share"),
            RadialMenuItem("delete", Icons.Default.Delete, "Delete"),
            RadialMenuItem("more", Icons.Default.MoreVert, "More")
        )
    }

    var selectedIndex by remember { mutableIntStateOf(-1) }

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(animationSpec = tween(200)),
        exit = fadeOut(animationSpec = tween(200))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(PureBlack.copy(alpha = 0.7f))
                .blur(40.dp)
                .pointerInput(Unit) {
                    detectTapGestures { onDismiss() }
                },
            contentAlignment = Alignment.Center
        ) {
            // Radial menu items
            val radius = 140.dp

            menuItems.forEachIndexed { index, item ->
                val angle = (index * 60 - 90) * (Math.PI / 180f)
                val x = (cos(angle) * radius.value).roundToInt()
                val y = (sin(angle) * radius.value).roundToInt()

                RadialMenuButton(
                    item = item,
                    isSelected = selectedIndex == index,
                    onClick = {
                        selectedIndex = index
                        onItemSelected(item.id)
                    },
                    modifier = Modifier.offset {
                        IntOffset(x, y)
                    },
                    delayMillis = index * 50
                )
            }

            // Center indicator
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(PureWhite.copy(alpha = 0.5f))
            )
        }
    }
}

@Composable
private fun RadialMenuButton(
    item: RadialMenuItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    delayMillis: Int = 0
) {
    var isVisible by remember { mutableStateOf(false) }
    val scale = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        delay(delayMillis.toLong())
        isVisible = true
        scale.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = 300,
                easing = MaterialStandardEasing
            )
        )
    }

    // Selection animation
    val selectionScale = remember { Animatable(1f) }

    LaunchedEffect(isSelected) {
        if (isSelected) {
            selectionScale.animateTo(
                targetValue = 1.2f,
                animationSpec = tween(200)
            )
            selectionScale.animateTo(
                targetValue = 1f,
                animationSpec = tween(200)
            )
        }
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = scaleIn(animationSpec = tween(300)),
        exit = scaleOut(animationSpec = tween(200))
    ) {
        Box(
            modifier = modifier
                .scale(scale.value * selectionScale.value)
                .size(64.dp)
                .glassmorphic(
                    if (isSelected) GlassmorphismLevel.HEAVY else GlassmorphismLevel.MEDIUM,
                    if (isSelected) RoundedCornerShape(20.dp) else CircleShape
                )
                .clip(if (isSelected) RoundedCornerShape(20.dp) else CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = item.label,
                    tint = if (isSelected) PureWhite else Gray80,
                    modifier = Modifier.size(24.dp)
                )

                if (isSelected) {
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = PureWhite
                    )
                }
            }
        }
    }
}

@Composable
fun LongPressContextMenuDemo(
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    var selectedItem by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PureBlack)
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = { showMenu = true }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        // Demo content
        Box(
            modifier = Modifier
                .size(200.dp)
                .glassmorphic(GlassmorphismLevel.MEDIUM, RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp))
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Long press anywhere",
                style = MaterialTheme.typography.bodyMedium,
                color = Gray64
            )
        }

        // Radial menu
        RadialContextMenu(
            isVisible = showMenu,
            onDismiss = { showMenu = false },
            onItemSelected = { itemId ->
                selectedItem = itemId
                showMenu = false
            }
        )

        // Selected item feedback
        selectedItem?.let {
            Text(
                text = "Selected: $it",
                modifier = Modifier.align(Alignment.BottomCenter)
                    .padding(bottom = 48.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = PureWhite
            )
        }
    }
}

@Preview(device = "id:pixel_8")
@Composable
private fun RadialContextMenuPreview() {
    NimittamTheme {
        LongPressContextMenuDemo()
    }
}
