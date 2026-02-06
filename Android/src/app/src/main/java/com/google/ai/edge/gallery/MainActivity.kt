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
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.google.ai.edge.gallery.data.DataStoreRepository
import com.google.ai.edge.gallery.ui.navigation.NimittamNavigation
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
            dataStoreRepository.syncFromFallbackPreferences(applicationContext)
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
                    NimittamNavigation()
                }
            }
        }
    }
}
