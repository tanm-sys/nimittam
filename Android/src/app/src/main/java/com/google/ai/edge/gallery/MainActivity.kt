/*
 * Copyright 2025 Tanmay Patil
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/..
 */

package com.google.ai.edge.gallery

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.google.ai.edge.gallery.common.DeviceCompatibilityChecker
import com.google.ai.edge.gallery.data.DataStoreRepository
import com.google.ai.edge.gallery.ui.components.ErrorBoundary
import com.google.ai.edge.gallery.ui.navigation.NimittamNavigation
import com.google.ai.edge.gallery.ui.screens.compatibility.CompatibilityErrorScreen
import com.google.ai.edge.gallery.ui.theme.NimittamTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var dataStoreRepository: DataStoreRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Sync fallback preferences to DataStore on startup
        lifecycleScope.launch {
            try {
                dataStoreRepository.syncFromFallbackPreferences(applicationContext)
            } catch (e: Exception) {
                // Log but don't crash if sync fails
                android.util.Log.w("MainActivity", "Failed to sync fallback preferences", e)
            }
        }

        // Enable edge-to-edge display
        enableEdgeToEdge()

        // Configure window insets
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            NimittamTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ErrorBoundary {
                        var showCompatibilityScreen by remember { 
                            mutableStateOf(false) 
                        }
                        var compatibilityResult by remember { 
                            mutableStateOf(DeviceCompatibilityChecker.CompatibilityResult(
                                isCompatible = true,
                                issues = emptyList(),
                                warnings = emptyList()
                            ))
                        }
                        
                        // Check compatibility on first composition
                        androidx.compose.runtime.LaunchedEffect(Unit) {
                            val result = DeviceCompatibilityChecker.checkCompatibility(applicationContext)
                            if (!result.isCompatible || result.warnings.isNotEmpty()) {
                                compatibilityResult = result
                                showCompatibilityScreen = true
                            }
                        }
                        
                        if (showCompatibilityScreen) {
                            CompatibilityErrorScreen(
                                issues = compatibilityResult.issues,
                                warnings = compatibilityResult.warnings,
                                onRetry = {
                                    val result = DeviceCompatibilityChecker.checkCompatibility(applicationContext)
                                    compatibilityResult = result
                                    if (result.isCompatible && result.warnings.isEmpty()) {
                                        showCompatibilityScreen = false
                                    }
                                },
                                onContinueAnyway = if (compatibilityResult.canRunWithWarnings) {
                                    { showCompatibilityScreen = false }
                                } else null
                            )
                        } else {
                            NimittamNavigation()
                        }
                    }
                }
            }
        }
    }
}
