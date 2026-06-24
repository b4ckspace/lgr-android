// SPDX-FileCopyrightText: 2026 Andreas Uhsemann
// SPDX-License-Identifier: Apache-2.0

package de.backspace.lgr.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

// A Content-header action whose icon reflects whether its mode is active. Idle: the outlined icon.
// Active: the filled icon variant sitting on a faint green pill, so an active mode reads at a glance.
// Both states keep the normal primary green; the active cue is the filled silhouette plus the pill.
// Shared by the Barcode Detail content header and the standalone Verify result screen.
@Composable
internal fun ModeIconButton(
    active: Boolean,
    onClick: () -> Unit,
    idleIcon: ImageVector,
    activeIcon: ImageVector,
    contentDescription: String?
) {
    IconButton(onClick = onClick, modifier = Modifier.size(32.dp)) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .then(
                    if (active) Modifier.background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                    else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (active) activeIcon else idleIcon,
                contentDescription = contentDescription,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}
