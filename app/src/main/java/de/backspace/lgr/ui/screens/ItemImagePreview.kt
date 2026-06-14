// SPDX-FileCopyrightText: 2026 Andreas Uhsemann
// SPDX-License-Identifier: Apache-2.0

package de.backspace.lgr.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage

// Inline image preview that always shows the whole image (no cropping), for any uploaded
// dimensions. The box takes the image's own aspect ratio so landscape photos fill the width
// with proportional height; portrait photos are capped at maxHeight and centered (letterboxed
// against a subtle background). Used everywhere an item image is shown inline.
@Composable
fun ItemImagePreview(
    model: Any?,
    imageLoader: ImageLoader,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    maxHeight: Dp = 300.dp,
    onClick: (() -> Unit)? = null,
) {
    // Reset when the source changes (e.g. swiping between barcodes) so the height re-derives.
    var aspect by remember(model) { mutableStateOf<Float?>(null) }
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val a = aspect
        val height = if (a != null && a > 0f) minOf(maxHeight, maxWidth / a) else maxHeight
        AsyncImage(
            model = model,
            imageLoader = imageLoader,
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            onSuccess = { state ->
                val d = state.result.drawable
                if (d.intrinsicWidth > 0 && d.intrinsicHeight > 0) {
                    aspect = d.intrinsicWidth.toFloat() / d.intrinsicHeight.toFloat()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        )
    }
}
