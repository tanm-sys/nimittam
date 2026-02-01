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

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Nimittam Typography System
 * SF Pro Display and Roboto Flex variable fonts with optical sizing
 * 8px baseline grid system
 */

// === Font Families (using system fonts as base) ===
val SFProDisplay = FontFamily.Default

val RobotoFlex = FontFamily.Default

// Placeholder font references for when custom fonts are added
// In production, add actual font files to res/font/ and reference them here
// val SFProDisplay = FontFamily(
//     Font(R.font.sf_pro_display_regular, FontWeight.Normal),
//     Font(R.font.sf_pro_display_medium, FontWeight.Medium),
//     Font(R.font.sf_pro_display_semibold, FontWeight.SemiBold),
//     Font(R.font.sf_pro_display_bold, FontWeight.Bold),
// )

// === Typography Scale ===
val NimittamTypography = Typography(
    // === Display Large ===
    // Usage: Hero headlines, splash screen
    displayLarge = TextStyle(
        fontFamily = SFProDisplay,
        fontWeight = FontWeight.Bold,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = (-0.25).sp
    ),

    // === Display Medium ===
    // Usage: Large headlines
    displayMedium = TextStyle(
        fontFamily = SFProDisplay,
        fontWeight = FontWeight.SemiBold,
        fontSize = 45.sp,
        lineHeight = 52.sp,
        letterSpacing = 0.sp
    ),

    // === Display Small ===
    // Usage: Medium headlines
    displaySmall = TextStyle(
        fontFamily = SFProDisplay,
        fontWeight = FontWeight.SemiBold,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = 0.sp
    ),

    // === Headline Large ===
    // Usage: Screen titles
    headlineLarge = TextStyle(
        fontFamily = SFProDisplay,
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.sp
    ),

    // === Headline Medium ===
    // Usage: Section headers
    headlineMedium = TextStyle(
        fontFamily = SFProDisplay,
        fontWeight = FontWeight.Medium,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp
    ),

    // === Headline Small ===
    // Usage: Card titles
    headlineSmall = TextStyle(
        fontFamily = SFProDisplay,
        fontWeight = FontWeight.Medium,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp
    ),

    // === Title Large ===
    // Usage: Primary content titles
    titleLarge = TextStyle(
        fontFamily = SFProDisplay,
        fontWeight = FontWeight.Medium,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),

    // === Title Medium ===
    // Usage: Secondary content titles
    titleMedium = TextStyle(
        fontFamily = SFProDisplay,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp
    ),

    // === Title Small ===
    // Usage: Tertiary content titles
    titleSmall = TextStyle(
        fontFamily = SFProDisplay,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),

    // === Body Large ===
    // Usage: Primary body text
    bodyLarge = TextStyle(
        fontFamily = RobotoFlex,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),

    // === Body Medium ===
    // Usage: Secondary body text
    bodyMedium = TextStyle(
        fontFamily = RobotoFlex,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp
    ),

    // === Body Small ===
    // Usage: Captions, metadata
    bodySmall = TextStyle(
        fontFamily = RobotoFlex,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp
    ),

    // === Label Large ===
    // Usage: Buttons, interactive elements
    labelLarge = TextStyle(
        fontFamily = SFProDisplay,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),

    // === Label Medium ===
    // Usage: Small buttons, chips
    labelMedium = TextStyle(
        fontFamily = SFProDisplay,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    ),

    // === Label Small ===
    // Usage: Tags, badges, captions
    labelSmall = TextStyle(
        fontFamily = SFProDisplay,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
)

// === Variable Font Animation Helpers ===
fun variableFontStyle(
    weight: FontWeight = FontWeight.Normal,
    size: Int = 16
): TextStyle {
    return TextStyle(
        fontFamily = RobotoFlex,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = (size + 8).sp,
        letterSpacing = 0.sp
    )
}

// === Chat-specific Typography ===
val ChatMessageText = TextStyle(
    fontFamily = RobotoFlex,
    fontWeight = FontWeight.Normal,
    fontSize = 16.sp,
    lineHeight = 24.sp,
    letterSpacing = 0.sp
)

val ChatTimestamp = TextStyle(
    fontFamily = SFProDisplay,
    fontWeight = FontWeight.Medium,
    fontSize = 11.sp,
    lineHeight = 16.sp,
    letterSpacing = 0.5.sp
)

val InputPlaceholder = TextStyle(
    fontFamily = RobotoFlex,
    fontWeight = FontWeight.Normal,
    fontSize = 16.sp,
    lineHeight = 24.sp,
    letterSpacing = 0.25.sp
)

val WordmarkStyle = TextStyle(
    fontFamily = SFProDisplay,
    fontWeight = FontWeight.SemiBold,
    fontSize = 24.sp,
    lineHeight = 32.sp,
    letterSpacing = 0.5.sp
)
