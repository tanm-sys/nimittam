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

package com.google.ai.edge.gallery.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Nimittam Monochrome Materiality Color System
 * Pure black and white with anti-aliased grayscale gradients
 */

// === Pure Monochrome ===
val PureBlack = Color(0xFF000000)
val PureWhite = Color(0xFFFFFFFF)

// === Obsidian (User Message Background) ===
val Obsidian = Color(0xFF0A0A0A)
val ObsidianLight = Color(0xFF141414)

// === Grayscale Opacity Scale (1-99%) ===
val Gray01 = Color(0xFFFFFFFF).copy(alpha = 0.01f)
val Gray02 = Color(0xFFFFFFFF).copy(alpha = 0.02f)
val Gray05 = Color(0xFFFFFFFF).copy(alpha = 0.05f)
val Gray08 = Color(0xFFFFFFFF).copy(alpha = 0.08f)
val Gray10 = Color(0xFFFFFFFF).copy(alpha = 0.10f)
val Gray12 = Color(0xFFFFFFFF).copy(alpha = 0.12f)
val Gray16 = Color(0xFFFFFFFF).copy(alpha = 0.16f)
val Gray20 = Color(0xFFFFFFFF).copy(alpha = 0.20f)
val Gray24 = Color(0xFFFFFFFF).copy(alpha = 0.24f)
val Gray32 = Color(0xFFFFFFFF).copy(alpha = 0.32f)
val Gray40 = Color(0xFFFFFFFF).copy(alpha = 0.40f)
val Gray48 = Color(0xFFFFFFFF).copy(alpha = 0.48f)
val Gray56 = Color(0xFFFFFFFF).copy(alpha = 0.56f)
val Gray64 = Color(0xFFFFFFFF).copy(alpha = 0.64f)
val Gray72 = Color(0xFFFFFFFF).copy(alpha = 0.72f)
val Gray80 = Color(0xFFFFFFFF).copy(alpha = 0.80f)
val Gray88 = Color(0xFFFFFFFF).copy(alpha = 0.88f)
val Gray96 = Color(0xFFFFFFFF).copy(alpha = 0.96f)

// === Inverse Grayscale (for light backgrounds) ===
val InverseGray05 = Color(0xFF000000).copy(alpha = 0.05f)
val InverseGray08 = Color(0xFF000000).copy(alpha = 0.08f)
val InverseGray10 = Color(0xFF000000).copy(alpha = 0.10f)
val InverseGray12 = Color(0xFF000000).copy(alpha = 0.12f)
val InverseGray16 = Color(0xFF000000).copy(alpha = 0.16f)
val InverseGray20 = Color(0xFF000000).copy(alpha = 0.20f)
val InverseGray24 = Color(0xFF000000).copy(alpha = 0.24f)
val InverseGray32 = Color(0xFF000000).copy(alpha = 0.32f)
val InverseGray40 = Color(0xFF000000).copy(alpha = 0.40f)
val InverseGray48 = Color(0xFF000000).copy(alpha = 0.48f)
val InverseGray56 = Color(0xFF000000).copy(alpha = 0.56f)
val InverseGray64 = Color(0xFF000000).copy(alpha = 0.64f)
val InverseGray72 = Color(0xFF000000).copy(alpha = 0.72f)
val InverseGray80 = Color(0xFF000000).copy(alpha = 0.80f)
val InverseGray88 = Color(0xFF000000).copy(alpha = 0.88f)
val InverseGray96 = Color(0xFF000000).copy(alpha = 0.96f)

// === Semantic Colors ===
val BackgroundPrimary = PureBlack
val BackgroundSecondary = Obsidian
val BackgroundTertiary = ObsidianLight

val SurfaceLevel0 = PureBlack
val SurfaceLevel1 = Gray05
val SurfaceLevel2 = Gray08
val SurfaceLevel3 = Gray12
val SurfaceLevel4 = Gray16

val TextPrimary = PureWhite
val TextSecondary = Gray80
val TextTertiary = Gray56
val TextDisabled = Gray32

val BorderPrimary = Gray24
val BorderSecondary = Gray16
val BorderAccent = PureWhite

val GlassmorphismLight = Gray08
val GlassmorphismMedium = Gray12
val GlassmorphismHeavy = Gray20

// === Elevation Colors (for Z-depth layering) ===
val Elevation0dp = PureBlack
val Elevation1dp = Gray02
val Elevation2dp = Gray05
val Elevation3dp = Gray08
val Elevation4dp = Gray10
val Elevation8dp = Gray12
val Elevation16dp = Gray16
val Elevation24dp = Gray20
val Elevation32dp = Gray24

// === Status Colors (Monochrome only) ===
val StatusOnline = PureWhite
val StatusOffline = Gray40
val StatusSyncing = Gray64
val StatusError = Gray80
