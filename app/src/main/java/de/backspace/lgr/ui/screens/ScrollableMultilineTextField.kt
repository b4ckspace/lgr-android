// SPDX-FileCopyrightText: 2026 Andreas Uhsemann
// SPDX-License-Identifier: Apache-2.0

package de.backspace.lgr.ui.screens

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// A multi-line text field whose Material outline and floating label stay put while only the
// text content scrolls inside a bounded height, with a thin scrollbar on the right edge.
//
// Built on OutlinedTextFieldDefaults.DecorationBox rather than a plain OutlinedTextField:
// foundation 1.6 (Compose BOM 2024.06) has no BasicTextField(state, scrollState) overload,
// so the only way to own the ScrollState (needed to draw the scrollbar and to keep the
// caret line visible) and keep the border from scrolling with the text is to wrap the inner
// text field in our own scroll container.
//
// Scroll behaviour: the field opens scrolled to the top; the position is then remembered
// (rememberScrollState is saveable, so it survives navigating away and back) and reset to the
// top only when the screen/entry is opened fresh. The view only auto-scrolls to follow the
// caret on an actual edit, so the user's own scrolling is never fought.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScrollableMultilineTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    minLines: Int = 2,
    maxHeight: Dp = 140.dp
) {
    val scroll = rememberScrollState()
    val interactionSource = remember { MutableInteractionSource() }
    val colors = lgrTextFieldColors()
    val textColor = if (enabled) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    val textStyle = LocalTextStyle.current.copy(color = textColor)

    // TextFieldValue is the single source of truth for what's displayed (BasicTextField needs
    // it to expose the caret). We only adopt the hoisted String when it changes for an external
    // reason (programmatic clear/prefill); echoes of the user's own edits are ignored via
    // lastEmitted, which is what prevents the caret from jumping to the end during fast typing.
    //
    // The caret starts at offset 0 (not the end): the field opens scrolled to the top, and when
    // it is first focused the text field brings the caret into view — anchoring it at the top
    // avoids a jump-to-bottom on focus that would otherwise scroll the line the user tapped out
    // of sight.
    var tfv by remember { mutableStateOf(TextFieldValue(value, TextRange(0))) }
    var lastEmitted by remember { mutableStateOf(value) }
    if (value != lastEmitted && value != tfv.text) {
        tfv = TextFieldValue(value, TextRange(0))
        lastEmitted = value
    }

    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    var viewportPx by remember { mutableStateOf(0) }
    // Bumped on every user edit; the caret-follow only runs in response to this, never on the
    // initial open or a plain recomposition (so the field stays scrolled to the top until typed in).
    var editTick by remember { mutableStateOf(0) }

    LaunchedEffect(editTick, layout, viewportPx) {
        if (editTick == 0) return@LaunchedEffect
        val l = layout ?: return@LaunchedEffect
        if (viewportPx == 0) return@LaunchedEffect
        val caret = l.getCursorRect(tfv.selection.end.coerceIn(0, l.layoutInput.text.length))
        val viewTop = scroll.value
        val viewBottom = viewTop + viewportPx
        when {
            caret.bottom > viewBottom -> scroll.scrollTo((caret.bottom - viewportPx).toInt().coerceAtLeast(0))
            caret.top < viewTop -> scroll.scrollTo(caret.top.toInt().coerceAtLeast(0))
        }
    }

    BasicTextField(
        value = tfv,
        onValueChange = {
            val textChanged = it.text != tfv.text
            tfv = it
            if (textChanged) {
                lastEmitted = it.text
                onValueChange(it.text)
                editTick++
            }
        },
        enabled = enabled,
        textStyle = textStyle,
        minLines = minLines,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        interactionSource = interactionSource,
        onTextLayout = { layout = it },
        modifier = modifier,
        decorationBox = { innerTextField ->
            OutlinedTextFieldDefaults.DecorationBox(
                value = tfv.text,
                innerTextField = {
                    Box(
                        Modifier
                            .heightIn(max = maxHeight)
                            .onSizeChanged { viewportPx = it.height }
                            .verticalScrollbar(scroll, endPadding = 0.dp)
                            .verticalScroll(scroll)
                            // Keep the text clear of the bar without leaving a wide gutter.
                            .padding(end = 6.dp)
                    ) { innerTextField() }
                },
                enabled = enabled,
                singleLine = false,
                visualTransformation = VisualTransformation.None,
                interactionSource = interactionSource,
                label = { Text(label) },
                colors = colors,
                // Tighten the right inset so the scrollbar sits close to the outline.
                contentPadding = OutlinedTextFieldDefaults.contentPadding(end = 4.dp)
            )
        }
    )
}
