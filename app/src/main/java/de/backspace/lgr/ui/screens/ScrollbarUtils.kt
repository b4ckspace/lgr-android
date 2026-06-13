// SPDX-FileCopyrightText: 2026 Andreas Uhsemann
// SPDX-License-Identifier: Apache-2.0

package de.backspace.lgr.ui.screens

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val SCROLLBAR_WIDTH = 4.dp

// IMPORTANT — two rules that make this scrollbar correct:
//
// 1. All values driving the scrollbar are read at COMPOSITION time (inside composed {}),
//    never inside drawWithContent.  drawWithContent runs in the draw phase, outside
//    Compose's snapshot observation scope — state reads there are invisible to the
//    snapshot system and never trigger a re-draw on scroll.
//
// 2. Item heights are MEASURED ONCE AND CACHED per index.  A lazy list only ever lays
//    out the items currently on screen, so the naive approach (estimate total height from
//    the average of whatever is visible right now) makes the estimate jump every frame an
//    item of a different height enters/leaves the viewport edges.  With items of very
//    different heights (e.g. a huge content section among small rows) that produces a
//    constantly-resizing thumb.  Caching the real measured height of each item as it
//    scrolls into view means the total only changes when a genuinely-new item is seen for
//    the first time, then stays put.

fun Modifier.verticalScrollbar(state: LazyListState, width: Dp = SCROLLBAR_WIDTH): Modifier = composed {
    val color      = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)

    val info       = state.layoutInfo
    val visible    = info.visibleItemsInfo
    val totalItems = info.totalItemsCount
    val vpH        = (info.viewportEndOffset - info.viewportStartOffset).toFloat()
    val firstIndex = state.firstVisibleItemIndex
    val firstOff   = state.firstVisibleItemScrollOffset.toFloat()

    // Per-index measured heights + the (constant) inter-item spacing.  Cleared when the
    // item count changes, because indices would otherwise shift and desync.
    val heights = remember(state, totalItems) { mutableStateMapOf<Int, Int>() }
    val spacing = remember(state, totalItems) { mutableStateOf(0f) }

    // Commit this frame's real measurements after composition (writes may schedule one
    // extra recomposition until every visible item is cached, then it settles).
    SideEffect {
        for (it in visible) if (heights[it.index] != it.size) heights[it.index] = it.size
        for (i in 0 until visible.size - 1) {
            val a = visible[i]; val b = visible[i + 1]
            if (b.index == a.index + 1) {
                val sp = (b.offset - a.offset - a.size).toFloat()
                if (sp >= 0f && spacing.value != sp) spacing.value = sp
                break
            }
        }
    }

    val knownCount = heights.size
    val knownSum   = heights.values.sum()
    val avgH       = if (knownCount > 0) knownSum.toFloat() / knownCount
                     else visible.firstOrNull()?.size?.toFloat() ?: 0f
    val sp         = spacing.value

    // Total content height: sum of known heights + estimate for not-yet-seen items.
    val totalH     = knownSum + (totalItems - knownCount).coerceAtLeast(0) * avgH +
                     sp * (totalItems - 1).coerceAtLeast(0)
    // Pixels scrolled above the viewport top.  Items before firstIndex have all been seen
    // (you scrolled through them), so their cached heights make this exact.
    var before     = sp * firstIndex
    for (i in 0 until firstIndex) before += heights[i]?.toFloat() ?: avgH
    val scrolledPx = before + firstOff

    val show = visible.isNotEmpty() && totalH > vpH && vpH > 0f

    drawWithContent {
        drawContent()
        if (!show) return@drawWithContent
        val thumbH    = (vpH * vpH / totalH).coerceAtLeast(40.dp.toPx()).coerceAtMost(vpH)
        val maxScroll = totalH - vpH
        val thumbY    = if (maxScroll > 0f)
            (scrolledPx / maxScroll * (vpH - thumbH)).coerceIn(0f, vpH - thumbH) else 0f
        val w = width.toPx()
        drawRoundRect(color, Offset(size.width - w - 2.dp.toPx(), thumbY), Size(w, thumbH), CornerRadius(w / 2))
    }
}

fun Modifier.verticalScrollbar(state: LazyGridState, width: Dp = SCROLLBAR_WIDTH): Modifier = composed {
    val color      = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)

    val info       = state.layoutInfo
    val visible    = info.visibleItemsInfo
    val totalItems = info.totalItemsCount
    val vpH        = (info.viewportEndOffset - info.viewportStartOffset).toFloat()

    // Column count = largest number of items sharing one Y offset (handles partial last row).
    val cols       = remember(state, totalItems) { mutableStateOf(1) }
    val rowH       = remember(state, totalItems) { mutableStateMapOf<Int, Int>() }
    val rowSpacing = remember(state, totalItems) { mutableStateOf(0f) }

    SideEffect {
        val c = visible.groupBy { it.offset.y }.values.maxOfOrNull { it.size } ?: 1
        if (c > 0 && cols.value != c) cols.value = c
        val byRow = visible.groupBy { it.index / cols.value }
        for ((r, items) in byRow) {
            val h = items.maxOf { it.size.height }
            if (rowH[r] != h) rowH[r] = h
        }
        val rows = byRow.entries.sortedBy { it.key }
        for (i in 0 until rows.size - 1) {
            val a = rows[i]; val b = rows[i + 1]
            if (b.key == a.key + 1) {
                val sp = (b.value.first().offset.y - a.value.first().offset.y -
                          a.value.maxOf { it.size.height }).toFloat()
                if (sp >= 0f && rowSpacing.value != sp) rowSpacing.value = sp
                break
            }
        }
    }

    val colCount   = cols.value.coerceAtLeast(1)
    val numRows     = (totalItems + colCount - 1) / colCount
    val firstRow    = (visible.firstOrNull()?.index ?: 0) / colCount
    val firstRowTop = visible.firstOrNull()?.offset?.y?.toFloat() ?: 0f
    val knownRows   = rowH.size
    val knownSum    = rowH.values.sum()
    val avgRowH     = if (knownRows > 0) knownSum.toFloat() / knownRows
                      else visible.firstOrNull()?.size?.height?.toFloat() ?: 0f
    val sp          = rowSpacing.value

    val totalH      = knownSum + (numRows - knownRows).coerceAtLeast(0) * avgRowH +
                      sp * (numRows - 1).coerceAtLeast(0)
    var before      = sp * firstRow
    for (r in 0 until firstRow) before += rowH[r]?.toFloat() ?: avgRowH
    val scrolledPx  = before + (-firstRowTop).coerceAtLeast(0f)

    val show = visible.isNotEmpty() && totalH > vpH && vpH > 0f

    drawWithContent {
        drawContent()
        if (!show) return@drawWithContent
        val thumbH    = (vpH * vpH / totalH).coerceAtLeast(40.dp.toPx()).coerceAtMost(vpH)
        val maxScroll = totalH - vpH
        val thumbY    = if (maxScroll > 0f)
            (scrolledPx / maxScroll * (vpH - thumbH)).coerceIn(0f, vpH - thumbH) else 0f
        val w = width.toPx()
        drawRoundRect(color, Offset(size.width - w - 2.dp.toPx(), thumbY), Size(w, thumbH), CornerRadius(w / 2))
    }
}

fun Modifier.verticalScrollbar(state: ScrollState, width: Dp = SCROLLBAR_WIDTH, endPadding: Dp = 2.dp): Modifier = composed {
    val color       = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)

    // Read at composition time — ScrollState.value and maxValue are MutableState-backed.
    // This variant is pixel-exact: a non-lazy scrollable knows its full content height.
    val scrollValue = state.value
    val maxScroll   = state.maxValue.toFloat()

    drawWithContent {
        drawContent()
        if (maxScroll <= 0f) return@drawWithContent
        val vpH = size.height
        val thumbH = (vpH * vpH / (vpH + maxScroll)).coerceAtLeast(40.dp.toPx())
        val thumbY = (scrollValue / maxScroll) * (vpH - thumbH)
        val w = width.toPx()
        drawRoundRect(color, Offset(size.width - w - endPadding.toPx(), thumbY), Size(w, thumbH), CornerRadius(w / 2))
    }
}
