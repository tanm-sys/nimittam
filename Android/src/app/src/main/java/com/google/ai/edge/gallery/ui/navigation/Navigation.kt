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

package com.google.ai.edge.gallery.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.google.ai.edge.gallery.ui.screens.capability.ModelCapabilityScreen
import com.google.ai.edge.gallery.ui.screens.capability.ProcessingStage
import com.google.ai.edge.gallery.ui.screens.chat.ChatScreen
import com.google.ai.edge.gallery.ui.screens.history.HistoryScreen
import com.google.ai.edge.gallery.ui.screens.onboarding.ModelType
import com.google.ai.edge.gallery.ui.screens.onboarding.OnboardingScreen
import com.google.ai.edge.gallery.ui.screens.settings.SettingsScreen
import com.google.ai.edge.gallery.ui.screens.splash.SplashScreen
import com.google.ai.edge.gallery.ui.screens.voice.VoiceInputScreen
import com.google.ai.edge.gallery.ui.theme.AnimationDuration
import com.google.ai.edge.gallery.ui.theme.MaterialStandardEasing
import com.google.ai.edge.gallery.ui.theme.NimittamEasing

/**
 * Nimittam Navigation
 * Seamless transitions between screens
 * 120fps micro-interactions
 */

object Routes {
    const val SPLASH = "splash"
    const val ONBOARDING = "onboarding"
    const val CHAT = "chat"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val VOICE = "voice"
    const val CAPABILITY = "capability?stage={stage}&progress={progress}"

    fun capability(stage: String = "reasoning", progress: Float = 0f): String {
        return "capability?stage=$stage&progress=$progress"
    }
}

@Composable
fun NimittamNavigation(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    startDestination: String = Routes.SPLASH
) {
    val animationSpec = tween<IntOffset>(
        durationMillis = AnimationDuration.MEDIUM,
        easing = NimittamEasing
    )

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
        enterTransition = {
            slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Left,
                animationSpec = animationSpec
            )
        },
        exitTransition = {
            slideOutOfContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Left,
                animationSpec = animationSpec
            )
        },
        popEnterTransition = {
            slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Right,
                animationSpec = animationSpec
            )
        },
        popExitTransition = {
            slideOutOfContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Right,
                animationSpec = animationSpec
            )
        }
    ) {
        // Splash Screen
        composable(Routes.SPLASH) {
            SplashScreen(
                onLoadingComplete = {
                    navController.navigate(Routes.ONBOARDING) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                }
            )
        }

        // Onboarding Screen
        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                onModelSelected = { modelType ->
                    navController.navigate(Routes.CHAT) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                }
            )
        }

        // Main Chat Screen
        composable(Routes.CHAT) {
            ChatScreen(
                onNavigateToHistory = {
                    navController.navigate(Routes.HISTORY)
                },
                onNavigateToSettings = {
                    navController.navigate(Routes.SETTINGS)
                },
                onNavigateToVoice = {
                    navController.navigate(Routes.VOICE)
                }
            )
        }

        // History Screen
        composable(Routes.HISTORY) {
            HistoryScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNewChat = {
                    navController.popBackStack()
                },
                onConversationClick = { conversationId ->
                    navController.popBackStack()
                }
            )
        }

        // Settings Screen
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        // Voice Input Screen
        composable(Routes.VOICE) {
            VoiceInputScreen(
                onDismiss = {
                    navController.popBackStack()
                },
                onComplete = { transcription ->
                    navController.popBackStack()
                }
            )
        }

        // Model Capability Screen
        composable(
            route = Routes.CAPABILITY,
            arguments = listOf(
                navArgument("stage") {
                    type = NavType.StringType
                    defaultValue = "reasoning"
                },
                navArgument("progress") {
                    type = NavType.FloatType
                    defaultValue = 0f
                }
            )
        ) { backStackEntry ->
            val stage = backStackEntry.arguments?.getString("stage") ?: "reasoning"
            val progress = backStackEntry.arguments?.getFloat("progress") ?: 0f

            val processingStage = when (stage) {
                "analyzing" -> ProcessingStage.ANALYZING
                "reasoning" -> ProcessingStage.REASONING
                "generating" -> ProcessingStage.GENERATING
                "complete" -> ProcessingStage.COMPLETE
                else -> ProcessingStage.REASONING
            }

            ModelCapabilityScreen(
                stage = processingStage,
                progress = progress,
                onComplete = {
                    navController.popBackStack()
                }
            )
        }
    }
}

// Type alias for IntOffset to avoid import issues
typealias IntOffset = androidx.compose.ui.unit.IntOffset
