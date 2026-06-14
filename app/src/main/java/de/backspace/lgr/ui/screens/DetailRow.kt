// SPDX-FileCopyrightText: 2026 Andreas Uhsemann
// SPDX-License-Identifier: Apache-2.0

package de.backspace.lgr.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp

// A labelled value row, shared by all the detail screens. Tapping fires onClick (the value turns
// primary-coloured to signal it's tappable); long-pressing copies the value to the clipboard.
// A trailing divider separates it from the next row; pass divider = false for the last row when
// nothing follows, so the page doesn't end with a dangling line.
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DetailRow(
    label: String,
    value: String,
    valueColor: Color = Color.Unspecified,
    onClick: (() -> Unit)? = null,
    divider: Boolean = true
) {
    val clipboardManager = LocalClipboardManager.current
    Column(modifier = Modifier.combinedClickable(
        onClick = onClick ?: {},
        onLongClick = { clipboardManager.setText(AnnotatedString(value)) }
    )) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = if (onClick != null && valueColor == Color.Unspecified) MaterialTheme.colorScheme.primary else valueColor
        )
        if (divider) HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
    }
}
