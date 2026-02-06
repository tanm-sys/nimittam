/*
 * Copyright 2025 Tanmay Patil
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.google.ai.edge.gallery.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Nimittam Theme
 * Monochrome Materiality - Pure black and white design system
 */

// === Dark Color Scheme (Primary) ===
private val DarkColorScheme = darkColorScheme(
    primary = PureWhite,
    onPrimary = PureBlack,
    primaryContainer = Gray16,
    onPrimaryContainer = PureWhite,

    secondary = Gray80,
    onSecondary = PureBlack,
    secondaryContainer = Gray12,
    onSecondaryContainer = Gray96,

    tertiary = Gray64,
    onTertiary = PureBlack,
    tertiaryContainer = Gray08,
    onTertiaryContainer = Gray88,

    background = BackgroundPrimary,
    onBackground = TextPrimary,

    surface = SurfaceLevel1,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceLevel2,
    onSurfaceVariant = TextSecondary,

    error = Gray80,
    onError = PureBlack,
    errorContainer = Gray16,
    onErrorContainer = Gray96,

    outline = BorderPrimary,
    outlineVariant = BorderSecondary,

    scrim = PureBlack.copy(alpha = 0.8f),
    inverseSurface = PureWhite,
    inverseOnSurface = PureBlack,
    inversePrimary = PureBlack
)

// === Light Color Scheme (Secondary) ===
private val LightColorScheme = darkColorScheme(
    // Nimittam is primarily a dark-themed app
    // Light theme uses inverted monochrome
    primary = PureBlack,
    onPrimary = PureWhite,
    primaryContainer = InverseGray16,
    onPrimaryContainer = PureBlack,

    secondary = InverseGray80,
    onSecondary = PureWhite,
    secondaryContainer = InverseGray08,
    onSecondaryContainer = InverseGray96,

    tertiary = InverseGray64,
    onTertiary = PureWhite,
    tertiaryContainer = InverseGray05,
    onTertiaryContainer = InverseGray88,

    background = PureWhite,
    onBackground = PureBlack,

    surface = InverseGray05,
    onSurface = PureBlack,
    surfaceVariant = InverseGray08,
    onSurfaceVariant = InverseGray80,

    error = InverseGray80,
    onError = PureWhite,
    errorContainer = InverseGray16,
    onErrorContainer = InverseGray96,

    outline = InverseGray24,
    outlineVariant = InverseGray16,

    scrim = PureBlack.copy(alpha = 0.6f),
    inverseSurface = PureBlack,
    inverseOnSurface = PureWhite,
    inversePrimary = PureWhite
)

@Composable
fun NimittamTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,  // Disabled - we use strict monochrome
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.setDecorFitsSystemWindows(window, false)
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !darkTheme
            insetsController.isAppearanceLightNavigationBars = !darkTheme
            @Suppress("DEPRECATION")
            window.statusBarColor = Color.Transparent.toArgb()
            @Suppress("DEPRECATION")
            window.navigationBarColor = Color.Transparent.toArgb()
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = NimittamTypography,
        shapes = NimittamShapes,
        content = content
    )
}

// === Theme Extensions ===
object NimittamTheme {
    val colors: NimittamColors
        @Composable
        get() = NimittamColors()

    val spacing: NimittamSpacing
        get() = NimittamSpacing
}

class NimittamColors {
    val pureBlack = PureBlack
    val pureWhite = PureWhite
    val obsidian = Obsidian
    val obsidianLight = ObsidianLight

    val gray01 = Gray01
    val gray02 = Gray02
    val gray05 = Gray05
    val gray08 = Gray08
    val gray10 = Gray10
    val gray12 = Gray12
    val gray16 = Gray16
    val gray20 = Gray20
    val gray24 = Gray24
    val gray32 = Gray32
    val gray40 = Gray40
    val gray48 = Gray48
    val gray56 = Gray56
    val gray64 = Gray64
    val gray72 = Gray72
    val gray80 = Gray80
    val gray88 = Gray88
    val gray96 = Gray96

    val surfaceLevel0 = SurfaceLevel0
    val surfaceLevel1 = SurfaceLevel1
    val surfaceLevel2 = SurfaceLevel2
    val surfaceLevel3 = SurfaceLevel3
    val surfaceLevel4 = SurfaceLevel4

    val textPrimary = TextPrimary
    val textSecondary = TextSecondary
    val textTertiary = TextTertiary
    val textDisabled = TextDisabled

    val borderPrimary = BorderPrimary
    val borderSecondary = BorderSecondary
    val borderAccent = BorderAccent

    val glassmorphismLight = GlassmorphismLight
    val glassmorphismMedium = GlassmorphismMedium
    val glassmorphismHeavy = GlassmorphismHeavy
}

object NimittamSpacing {
    val dp0 = androidx.compose.ui.unit.Dp(0f)
    val dp4 = androidx.compose.ui.unit.Dp(4f)
    val dp8 = androidx.compose.ui.unit.Dp(8f)
    val dp12 = androidx.compose.ui.unit.Dp(12f)
    val dp16 = androidx.compose.ui.unit.Dp(16f)
    val dp20 = androidx.compose.ui.unit.Dp(20f)
    val dp24 = androidx.compose.ui.unit.Dp(24f)
    val dp32 = androidx.compose.ui.unit.Dp(32f)
    val dp40 = androidx.compose.ui.unit.Dp(40f)
    val dp48 = androidx.compose.ui.unit.Dp(48f)
    val dp56 = androidx.compose.ui.unit.Dp(56f)
    val dp64 = androidx.compose.ui.unit.Dp(64f)
    val dp72 = androidx.compose.ui.unit.Dp(72f)
    val dp80 = androidx.compose.ui.unit.Dp(80f)
    val dp88 = androidx.compose.ui.unit.Dp(88f)
    val dp96 = androidx.compose.ui.unit.Dp(96f)
}
