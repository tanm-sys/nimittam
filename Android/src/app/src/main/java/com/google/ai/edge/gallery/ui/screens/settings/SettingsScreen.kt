/*
 * Copyright 2025 Tanmay Patil
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.google.ai.edge.gallery.ui.screens.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.google.ai.edge.gallery.ui.components.NoiseTexture
import androidx.compose.ui.unit.sp
import com.google.ai.edge.gallery.ui.components.GlassmorphismLevel
import com.google.ai.edge.gallery.ui.components.glassmorphic
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Settings Screen
 * Grouped sections with 32dp spacing
 * Slider controls with haptic feedback visualization
 * Black/white toggle with smooth morphing
 * Storage visualization with geometric bars
 */

@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit = {}
) {
    var showContent by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(100)
        showContent = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PureBlack)
    ) {
        NoiseTexture(
            modifier = Modifier.fillMaxSize(),
            opacity = 0.01f
        )

        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Header
            SettingsHeader(onNavigateBack = onNavigateBack)

            // Content
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
            ) {
                // Model Section
                SettingsSection(title = "Model") {
                    ModelSettingsCard()
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Interface Section
                SettingsSection(title = "Interface") {
                    InterfaceSettingsCard()
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Storage Section
                SettingsSection(title = "Storage") {
                    StorageSettingsCard()
                }

                Spacer(modifier = Modifier.height(32.dp))

                // About Section
                SettingsSection(title = "About") {
                    AboutSettingsCard()
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun SettingsHeader(onNavigateBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onNavigateBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = Gray80
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineSmall,
            color = PureWhite
        )
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = Gray40,
            letterSpacing = 2.sp
        )

        Spacer(modifier = Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .glassmorphic(GlassmorphismLevel.MEDIUM, RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun ModelSettingsCard() {
    Column {
        SettingsItem(
            icon = Icons.Default.Memory,
            title = "Active Model",
            subtitle = "Nimittam Lite (0.5B)"
        )

        SettingsDivider()

        // Temperature slider
        var temperature by remember { mutableFloatStateOf(0.7f) }

        Text(
            text = "Temperature",
            style = MaterialTheme.typography.bodyMedium,
            color = PureWhite
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Precise",
                style = MaterialTheme.typography.labelSmall,
                color = Gray64
            )

            Slider(
                value = temperature,
                onValueChange = { temperature = it },
                valueRange = 0f..1f,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = PureWhite,
                    activeTrackColor = PureWhite,
                    inactiveTrackColor = Gray24
                )
            )

            Text(
                text = "Creative",
                style = MaterialTheme.typography.labelSmall,
                color = Gray64
            )
        }

        Text(
            text = "${(temperature * 100).toInt()}%",
            style = MaterialTheme.typography.labelMedium,
            color = Gray80,
            modifier = Modifier.align(Alignment.End)
        )
    }
}

@Composable
private fun InterfaceSettingsCard() {
    Column {
        // Theme toggle
        var isDarkTheme by remember { mutableStateOf(true) }

        SettingsToggleItem(
            icon = Icons.Default.DarkMode,
            title = "Dark Theme",
            checked = isDarkTheme,
            onCheckedChange = { isDarkTheme = it }
        )

        SettingsDivider()

        // Haptic feedback
        var hapticEnabled by remember { mutableStateOf(true) }

        SettingsToggleItem(
            icon = Icons.Default.Vibration,
            title = "Haptic Feedback",
            checked = hapticEnabled,
            onCheckedChange = { hapticEnabled = it }
        )

        SettingsDivider()

        // Notifications
        var notificationsEnabled by remember { mutableStateOf(false) }

        SettingsToggleItem(
            icon = Icons.Default.Notifications,
            title = "Notifications",
            checked = notificationsEnabled,
            onCheckedChange = { notificationsEnabled = it }
        )
    }
}

@Composable
private fun StorageSettingsCard() {
    Column {
        // Storage visualization
        StorageVisualization(
            usedBytes = 512 * 1024 * 1024L,  // 512 MB
            totalBytes = 2L * 1024 * 1024 * 1024  // 2 GB
        )

        Spacer(modifier = Modifier.height(16.dp))

        SettingsDivider()

        SettingsActionItem(
            icon = Icons.Default.Delete,
            title = "Clear Chat History",
            subtitle = "Delete all conversations",
            onClick = { }
        )
    }
}

@Composable
private fun AboutSettingsCard() {
    Column {
        SettingsItem(
            icon = Icons.Default.Info,
            title = "Version",
            subtitle = "1.1.0 (Build 18)"
        )

        SettingsDivider()

        SettingsItem(
            icon = Icons.Default.Storage,
            title = "Model Version",
            subtitle = "Qwen2.5-0.5B"
        )
    }
}

@Composable
private fun SettingsItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Gray64,
            modifier = Modifier.size(24.dp)
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = PureWhite
            )

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Gray64
            )
        }
    }
}

@Composable
private fun SettingsToggleItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Gray64,
            modifier = Modifier.size(24.dp)
        )

        Spacer(modifier = Modifier.width(16.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = PureWhite,
            modifier = Modifier.weight(1f)
        )

        MonochromeSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun SettingsActionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Gray64,
            modifier = Modifier.size(24.dp)
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = PureWhite
            )

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Gray64
            )
        }
    }
}

@Composable
private fun MonochromeSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val thumbPosition = remember { Animatable(if (checked) 1f else 0f) }

    LaunchedEffect(checked) {
        launch {
            thumbPosition.animateTo(
                targetValue = if (checked) 1f else 0f,
                animationSpec = tween(
                    durationMillis = AnimationDuration.NORMAL,
                    easing = MaterialStandardEasing
                )
            )
        }
    }

    Box(
        modifier = Modifier
            .width(52.dp)
            .height(32.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (checked) PureWhite else Gray24)
            .clickable { onCheckedChange(!checked) }
            .padding(4.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (checked) PureBlack else Gray64)
        )
    }
}

@Composable
private fun StorageVisualization(
    usedBytes: Long,
    totalBytes: Long
) {
    val usedPercentage = (usedBytes.toFloat() / totalBytes).coerceIn(0f, 1f)

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Storage Used",
                style = MaterialTheme.typography.bodyMedium,
                color = PureWhite
            )

            Text(
                text = "${formatBytes(usedBytes)} / ${formatBytes(totalBytes)}",
                style = MaterialTheme.typography.labelMedium,
                color = Gray80
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Geometric storage bars
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
        ) {
            val barWidth = size.width / 10f
            val barGap = 4f
            val filledBars = (usedPercentage * 10).toInt()

            for (i in 0 until 10) {
                val x = i * (barWidth + barGap)
                val isFilled = i < filledBars

                drawRect(
                    color = if (isFilled) PureWhite else Gray24,
                    topLeft = Offset(x, 0f),
                    size = Size(barWidth - barGap, size.height)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "${(usedPercentage * 100).toInt()}% used",
            style = MaterialTheme.typography.labelSmall,
            color = Gray64
        )
    }
}

private fun formatBytes(bytes: Long): String {
    return when {
        bytes >= 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024 * 1024)} GB"
        bytes >= 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
        bytes >= 1024 -> "${bytes / 1024} KB"
        else -> "$bytes B"
    }
}

@Composable
private fun SettingsDivider() {
    Spacer(modifier = Modifier.height(12.dp))
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Gray24)
    )
    Spacer(modifier = Modifier.height(12.dp))
}

@Preview(device = "id:pixel_8")
@Composable
private fun SettingsScreenPreview() {
    NimittamTheme {
        SettingsScreen()
    }
}
