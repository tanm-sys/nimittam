/*
 * Copyright 2025 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import kotlinx.coroutines.delay

/**
 * Onboarding Model Selection Screen
 * Dark background, glassmorphic cards at Level 2 elevation
 * "Choose Your Intelligence" title
 * Three model cards with different corner treatments
 * Offline badge with pulsing dot
 */

enum class ModelType {
    LITE, PRO, ULTRA
}

data class ModelInfo(
    val type: ModelType,
    val name: String,
    val description: String,
    val parameters: String,
    val speed: String,
    val memory: String,
    val features: List<String>
)

@Composable
fun OnboardingScreen(
    onModelSelected: (ModelType) -> Unit = {}
) {
    var selectedModel by remember { mutableStateOf<ModelType?>(null) }
    var showTitle by remember { mutableStateOf(false) }
    var showCards by remember { mutableStateOf(false) }
    var showBadge by remember { mutableStateOf(false) }

    val models = listOf(
        ModelInfo(
            type = ModelType.LITE,
            name = "Nimittam Lite",
            description = "Fast & Efficient",
            parameters = "0.5B",
            speed = "Ultra Fast",
            memory = "Low",
            features = listOf("Quick responses", "Minimal battery", "Instant load")
        ),
        ModelInfo(
            type = ModelType.PRO,
            name = "Nimittam Pro",
            description = "Balanced Performance",
            parameters = "1.5B",
            speed = "Fast",
            memory = "Medium",
            features = listOf("Smart reasoning", "Code generation", "Creative writing")
        ),
        ModelInfo(
            type = ModelType.ULTRA,
            name = "Nimittam Ultra",
            description = "Maximum Capability",
            parameters = "3B",
            speed = "Standard",
            memory = "High",
            features = listOf("Complex analysis", "Long context", "Advanced reasoning")
        )
    )

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

            // Model cards
            models.forEachIndexed { index, model ->
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
                        isSelected = selectedModel == model.type,
                        onClick = { selectedModel = model.type }
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Continue button
            AnimatedVisibility(
                visible = selectedModel != null,
                enter = fadeIn(animationSpec = tween(300))
            ) {
                ContinueButton(
                    onClick = { selectedModel?.let(onModelSelected) }
                )
            }
        }
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
    model: ModelInfo,
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
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(PureWhite)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Continue",
            style = MaterialTheme.typography.labelLarge,
            color = PureBlack,
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
