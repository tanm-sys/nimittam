/*
 * Copyright 2025 Tanmay Patil
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.google.ai.edge.gallery.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Error boundary for Compose UI.
 * Provides a way to catch and display errors gracefully.
 */

/**
 * Global error handler for Compose UI.
 * Default behavior is to re-throw errors.
 */
val LocalErrorHandler = staticCompositionLocalOf<(Throwable) -> Unit> { 
    { throw it }
}

/**
 * Error boundary that catches exceptions from its children.
 * Displays a user-friendly error screen when a crash occurs.
 * 
 * Usage:
 * ```kotlin
 * ErrorBoundary {
 *     MyScreen()
 * }
 * ```
 */
@Composable
fun ErrorBoundary(
    modifier: Modifier = Modifier,
    fallback: @Composable (Throwable, () -> Unit) -> Unit = { error, onRetry ->
        DefaultErrorScreen(error = error, onRetry = onRetry)
    },
    content: @Composable () -> Unit
) {
    var error by remember { mutableStateOf<Throwable?>(null) }
    var retryCount by remember { mutableIntStateOf(0) }
    
    val handleError: (Throwable) -> Unit = { throwable ->
        error = throwable
    }
    
    val retry: () -> Unit = {
        error = null
        retryCount++
    }
    
    CompositionLocalProvider(LocalErrorHandler provides handleError) {
        if (error != null) {
            fallback(error!!, retry)
        } else {
            androidx.compose.runtime.key(retryCount) {
                Box(modifier = modifier) {
                    content()
                }
            }
        }
    }
}

/**
 * Default error screen shown when a component crashes.
 */
@Composable
fun DefaultErrorScreen(
    error: Throwable,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Error,
                contentDescription = "Error",
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.error
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "Something went wrong",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = error.message ?: "An unexpected error occurred",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text("Retry")
            }
        }
    }
}

/**
 * Wraps a specific component with its own error boundary.
 * Useful for isolating errors in specific parts of the UI.
 * 
 * Usage:
 * ```kotlin
 * ComponentErrorBoundary("ChatScreen") {
 *     ChatScreen()
 * }
 * ```
 */
@Composable
fun ComponentErrorBoundary(
    componentName: String,
    modifier: Modifier = Modifier,
    fallback: @Composable (Throwable, () -> Unit) -> Unit = { error, onRetry ->
        ComponentErrorFallback(componentName = componentName, error = error, onRetry = onRetry)
    },
    content: @Composable () -> Unit
) {
    ErrorBoundary(
        modifier = modifier,
        fallback = { error, onRetry ->
            fallback(error, onRetry)
        }
    ) {
        content()
    }
}

/**
 * Fallback UI for component-specific errors.
 */
@Composable
private fun ComponentErrorFallback(
    componentName: String,
    error: Throwable,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1C1C1E))
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Error,
                contentDescription = null,
                tint = Color(0xFFFF453A),
                modifier = Modifier.size(48.dp)
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = "$componentName failed to load",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Button(onClick = onRetry) {
                Text("Try Again")
            }
        }
    }
}
