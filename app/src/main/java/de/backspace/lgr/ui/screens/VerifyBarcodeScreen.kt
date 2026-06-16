// SPDX-FileCopyrightText: 2026 Andreas Uhsemann
// SPDX-License-Identifier: Apache-2.0

package de.backspace.lgr.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.FactCheck
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import de.backspace.lgr.viewmodel.AppViewModel
import kotlin.math.roundToInt

private val VERIFY_GREY = Color(0xFF9E9E9E)
private val VERIFY_GREEN = Color(0xFF4CAF50)
private val VERIFY_LOAN_BLUE = Color(0xFF1976D2)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VerifyBarcodeScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onRescan: () -> Unit,
    onCancel: () -> Unit,
    onSaved: () -> Unit,
    onVerifyNext: () -> Unit,
    onOk: () -> Unit,
    onBarcodeClick: ((String) -> Unit)? = null,
    onItemClick: (() -> Unit)? = null
) {
    // Keep rendering the last scanned location/contents while the screen animates away after
    // Cancel/Save/OK/Verify-next clear the verify state, so it doesn't briefly flash
    // "No location scanned." during the exit transition.
    val liveLocation = viewModel.verifyLocation
    val locationState = remember { mutableStateOf(liveLocation) }
    val scannedState = remember { mutableStateOf(viewModel.verifyContents) }
    if (liveLocation != null) {
        locationState.value = liveLocation
        scannedState.value = viewModel.verifyContents
    }
    val location = locationState.value
    val scanned = scannedState.value
    val saveState = viewModel.verifyState

    LaunchedEffect(saveState.data) {
        if (saveState.data != null) onSaved()
    }

    if (location == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No location scanned.", color = MaterialTheme.colorScheme.error)
        }
        return
    }

    val dbChildren = location.apiChildNames ?: emptyList()
    val scannedCodes = scanned.map { it.code }.toSet()
    val dbCodes = dbChildren.map { it.code }.toSet()
    val hasMismatches = dbChildren.any { it.code !in scannedCodes } || scanned.any { it.code !in dbCodes }
    val totalCount = dbChildren.size + scanned.count { it.code !in dbCodes }

    val ownerName by produceState<String?>(null, location.owner) {
        val o = location.owner
        value = if (o != null) viewModel.resolveOwnerName(o) else null
    }

    // item indices: 0=item, 1=barcode, [image], location, then optional descriptions/owner/loan, then content
    val contentItemIndex = (if (location.itemImage != null) 4 else 3) + listOf(
        location.description.isNotBlank(),
        location.itemDescription.isNotBlank(),
        location.owner != null,
        location.apiLoanInfo != null
    ).count { it }
    val listState = rememberLazyListState()
    LaunchedEffect(Unit) {
        listState.animateScrollToItem(contentItemIndex)
    }

    val pullRefreshState = rememberPullToRefreshState()
    LaunchedEffect(pullRefreshState.isRefreshing) {
        if (pullRefreshState.isRefreshing) {
            viewModel.refreshVerifyLocation().join()
            pullRefreshState.endRefresh()
        }
    }

    var showFullscreenImage by remember { mutableStateOf(false) }
    if (showFullscreenImage && location.itemImage != null) {
        Dialog(
            onDismissRequest = { showFullscreenImage = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            var scale by remember { mutableStateOf(1f) }
            var offset by remember { mutableStateOf(Offset.Zero) }
            val transformableState = rememberTransformableState { zoomChange, panChange, _ ->
                scale = (scale * zoomChange).coerceIn(1f, 8f)
                offset = if (scale > 1f) offset + panChange / scale else Offset.Zero
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .transformable(state = transformableState)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { if (scale <= 1.05f) showFullscreenImage = false },
                            onDoubleTap = {
                                if (scale > 1f) { scale = 1f; offset = Offset.Zero } else scale = 2f
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = location.itemImage,
                    contentDescription = "Full size item image",
                    imageLoader = viewModel.imageLoader,
                    modifier = Modifier
                        .fillMaxSize()
                        .scale(scale)
                        .offset { IntOffset(offset.x.roundToInt(), offset.y.roundToInt()) },
                    contentScale = ContentScale.Fit
                )
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text("Verify", style = MaterialTheme.typography.titleLarge)
            }
            HorizontalDivider()
            Box(modifier = Modifier.weight(1f).fillMaxWidth().nestedScroll(pullRefreshState.nestedScrollConnection)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth().verticalScrollbar(listState),
                contentPadding = PaddingValues(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item { DetailRow("Item", location.itemName, valueColor = MaterialTheme.colorScheme.onSurface, onClick = onItemClick) }
                item { DetailRow("Barcode", location.code) }
                if (location.itemImage != null) {
                    item {
                        ItemImagePreview(
                            model = location.itemImage,
                            imageLoader = viewModel.imageLoader,
                            contentDescription = "Item image",
                            maxHeight = 320.dp,
                            onClick = { showFullscreenImage = true }
                        )
                    }
                }

                // Location section
                item {
                    val ancestors = location.apiParentNames ?: emptyList()
                    Column {
                        Text(
                            "Location",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(2.dp))
                        if (ancestors.isEmpty()) {
                            Text("—", style = MaterialTheme.typography.bodyLarge)
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                ancestors.reversed().forEachIndexed { i, info ->
                                    Row(
                                        modifier = Modifier.then(
                                            if (onBarcodeClick != null)
                                                Modifier.clickable { onBarcodeClick.invoke(info.code) }
                                            else Modifier
                                        ),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (i > 0) Text("› ", style = MaterialTheme.typography.bodyLarge, color = VERIFY_GREY)
                                        val name = info.name.removeSuffix(" (${info.code})")
                                        Text(
                                            text = buildAnnotatedString {
                                                append(name)
                                                append(" ")
                                                withStyle(SpanStyle(color = VERIFY_GREY)) { append("(${info.code})") }
                                            },
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = Color.Unspecified
                                        )
                                    }
                                }
                            }
                        }
                        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
                    }
                }
                if (location.description.isNotBlank())
                    item { DetailRow("Barcode description", location.description) }
                if (location.itemDescription.isNotBlank())
                    item { DetailRow("Item description", location.itemDescription) }
                if (location.owner != null)
                    item { DetailRow("Owner", ownerName ?: "…") }
                location.apiLoanInfo?.let { loan ->
                    item {
                        val text = if (loan.loan) "On loan${loan.person?.let { " — $it" } ?: ""}" else "Available"
                        DetailRow("Loan", text, valueColor = if (loan.loan) VERIFY_LOAN_BLUE else VERIFY_GREEN)
                    }
                }

                // Two-column contents section (shared with the Barcode Detail content-verify mode)
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Content ($totalCount)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            IconButton(
                                onClick = {
                                    viewModel.addMoreVerifyContent()
                                    onRescan()
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Outlined.FactCheck,
                                    contentDescription = "Scan additional content",
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        VerifyContentColumns(
                            dbChildren = dbChildren,
                            scannedCodes = scannedCodes,
                            extraScanned = scanned,
                            onBarcodeClick = onBarcodeClick
                        )
                    }
                }

                if (saveState.error != null) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                        ) {
                            Text(
                                text = saveState.error,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.fillMaxWidth().padding(12.dp)
                            )
                        }
                    }
                }
            }
            PullToRefreshContainer(state = pullRefreshState, modifier = Modifier.align(Alignment.TopCenter))
            } // Box

            HorizontalDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
            ) {
                if (hasMismatches && !viewModel.readonlyMode) {
                    OutlinedButton(onClick = onCancel) { Text("Cancel") }
                    Button(
                        onClick = { viewModel.saveVerifyChanges() },
                        enabled = !saveState.isLoading
                    ) {
                        if (saveState.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Text("Save")
                        }
                    }
                } else {
                    OutlinedButton(onClick = onVerifyNext) { Text("Verify next") }
                    Button(onClick = onOk) { Text("OK") }
                }
            }
        }
}

