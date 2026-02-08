/*
 * Copyright 2025 Tanmay Patil
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.google.ai.edge.gallery.ui.screens.compatibility

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.ai.edge.gallery.common.DeviceCompatibilityChecker
import com.google.ai.edge.gallery.common.DeviceCompatibilityChecker.CompatibilityIssue
import com.google.ai.edge.gallery.common.DeviceCompatibilityChecker.IssueSeverity

/**
 * Screen shown when device is incompatible with the app.
 */
@Composable
fun CompatibilityErrorScreen(
    issues: List<CompatibilityIssue>,
    warnings: List<CompatibilityIssue>,
    onRetry: () -> Unit,
    onContinueAnyway: (() -> Unit)? = null
) {
    val criticalIssues = issues.filter { it.severity == IssueSeverity.CRITICAL }
    val canContinueWithWarnings = issues.isEmpty() && warnings.isNotEmpty()
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1C1C1E))
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(32.dp))
            
            // Icon
            Icon(
                imageVector = if (criticalIssues.isNotEmpty()) 
                    Icons.Default.Error else Icons.Default.Warning,
                contentDescription = null,
                tint = if (criticalIssues.isNotEmpty()) Color(0xFFFF453A) else Color(0xFFFFD60A),
                modifier = Modifier.size(80.dp)
            )
            
            // Title
            Text(
                text = if (criticalIssues.isNotEmpty()) 
                    "Device Not Compatible" else "Device Compatibility Warning",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Subtitle
            Text(
                text = if (criticalIssues.isNotEmpty())
                    "Your device doesn't meet the minimum requirements to run this app."
                else
                    "Your device can run the app but may experience issues.",
                style = MaterialTheme.typography.bodyLarge,
                color = Color(0xFF98989D),
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Critical Issues
            if (criticalIssues.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFFF453A).copy(alpha = 0.1f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Critical Issues",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color(0xFFFF453A)
                        )
                        
                        criticalIssues.forEach { issue ->
                            IssueItem(issue, Color(0xFFFF453A))
                        }
                    }
                }
            }
            
            // Warnings
            if (warnings.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFFFD60A).copy(alpha = 0.1f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Warnings",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color(0xFFFFD60A)
                        )
                        
                        warnings.forEach { issue ->
                            IssueItem(issue, Color(0xFFFFD60A))
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Device Info
            val context = androidx.compose.ui.platform.LocalContext.current
            val deviceInfo = DeviceCompatibilityChecker.getDeviceInfo(context)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF2C2C2E)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Device Information",
                        style = MaterialTheme.typography.titleSmall,
                        color = Color(0xFF98989D)
                    )
                    
                    deviceInfo.forEach { (key, value) ->
                        Text(
                            text = "$key: $value",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFEBEBF5)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Action Buttons
            if (canContinueWithWarnings && onContinueAnyway != null) {
                Button(
                    onClick = onContinueAnyway,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFFD60A)
                    )
                ) {
                    Text("Continue Anyway", color = Color.Black)
                }
                
                Spacer(modifier = Modifier.height(8.dp))
            }
            
            Button(
                onClick = onRetry,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF0A84FF)
                ),
                enabled = criticalIssues.isEmpty() || canContinueWithWarnings
            ) {
                Text("Check Again")
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun IssueItem(issue: CompatibilityIssue, color: Color) {
    Column {
        Text(
            text = issue.title,
            style = MaterialTheme.typography.titleSmall,
            color = color
        )
        Text(
            text = issue.message,
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFFEBEBF5)
        )
    }
}
