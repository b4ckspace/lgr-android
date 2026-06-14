// SPDX-FileCopyrightText: 2026 Andreas Uhsemann
// SPDX-License-Identifier: Apache-2.0

package de.backspace.lgr.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.outlined.FactCheck
import androidx.compose.material.icons.outlined.NoteAdd
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AddShoppingCart

import androidx.compose.material.icons.filled.RemoveShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.flow.collect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.roundToInt
import coil.compose.AsyncImage
import de.backspace.lgr.data.model.Barcode
import de.backspace.lgr.data.model.ChildInfo
import de.backspace.lgr.data.model.LoanInfo
import de.backspace.lgr.viewmodel.AppViewModel

private val GREY = Color(0xFF9E9E9E)
private val GREEN = Color(0xFF4CAF50)
private val RED = Color(0xFFE53935)
private val LOAN_BLUE = Color(0xFF1976D2)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun BarcodeDetailScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onScanContent: () -> Unit,
    onScanParent: () -> Unit,
    onAddContent: () -> Unit,
    onNewBarcode: () -> Unit = {},
    onNewBarcodeAsChild: () -> Unit = {},
    onEditBarcode: () -> Unit = {},
    onGoToCart: () -> Unit = {},
    onItemClick: (() -> Unit)? = null
) {
    val state = viewModel.scannedBarcode
    val barcodeList = viewModel.barcodeListContext
    val currentIndex = viewModel.barcodeListIndex
    val swipeThresholdPx = with(LocalDensity.current) { 80.dp.toPx() }
    var dragTotal by remember(currentIndex) { mutableStateOf(0f) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showFullscreenImage by remember { mutableStateOf(false) }

    val deleteState = viewModel.deleteBarcodeState
    LaunchedEffect(deleteState.data) {
        if (deleteState.data != null) {
            viewModel.resetDeleteBarcodeState()
            // If we got here by following a barcode link, return to the previous barcode in
            // place rather than popping the whole detail route back to the camera.
            val prev = viewModel.popBarcodeHistory()
            if (prev != null) {
                viewModel.loadBarcode(prev, restoreContentSession = true)
            } else {
                onBack()
            }
        }
    }

    val ownerName by produceState<String?>(null, state.data?.owner) {
        val ownerUrl = state.data?.owner
        value = if (ownerUrl != null) viewModel.resolveOwnerName(ownerUrl) else null
    }

    BackHandler(enabled = viewModel.barcodeHistory.isNotEmpty()) {
        val prevCode = viewModel.popBarcodeHistory() ?: return@BackHandler
        viewModel.loadBarcode(prevCode, restoreContentSession = true)
    }

    val onBarcodeClick: (String) -> Unit = { code -> viewModel.navigateToBarcode(code) }

    val pullToRefreshState = rememberPullToRefreshState()
    LaunchedEffect(pullToRefreshState.isRefreshing) {
        if (pullToRefreshState.isRefreshing) viewModel.refreshBarcodeDetail()
    }
    LaunchedEffect(state.isLoading) {
        if (!state.isLoading && pullToRefreshState.isRefreshing) pullToRefreshState.endRefresh()
    }

    val scrollState = rememberScrollState()
    val currentBarcodeCode by rememberUpdatedState(state.data?.code)
    // Root-relative tops of the scroll viewport and the Content section, used to scroll
    // the Content header to the top after a save (replaces LazyColumn's scrollToItem).
    var viewportTopY by remember { mutableStateOf(0f) }
    var contentSectionY by remember { mutableStateOf(0f) }

    LaunchedEffect(state.data?.code) {
        val code = state.data?.code ?: return@LaunchedEffect
        scrollState.scrollTo(viewModel.getScrollPosition(code))
    }

    LaunchedEffect(scrollState) {
        snapshotFlow { scrollState.value }
            .collect { value ->
                currentBarcodeCode?.let { viewModel.saveScrollPosition(it, value) }
            }
    }

    LaunchedEffect(deleteState.error) {
        if (deleteState.error != null) showDeleteDialog = true
    }

    if (showDeleteDialog) {
        val code = state.data?.code ?: ""
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false; viewModel.resetDeleteBarcodeState() },
            title = { Text("Delete Barcode") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Are you sure you want to permanently delete barcode:")
                    Text(code, style = MaterialTheme.typography.titleMedium)
                    deleteState.error?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.deleteBarcode(code)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RED)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false; viewModel.resetDeleteBarcodeState() }) {
                    Text("Cancel")
                }
            }
        )
    }

    val barcodeForFullscreen = state.data
    if (showFullscreenImage && barcodeForFullscreen?.itemImage != null) {
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
                    model = barcodeForFullscreen.itemImage,
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

    Box(modifier = Modifier.fillMaxSize().nestedScroll(pullToRefreshState.nestedScrollConnection)) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
            Text("Barcode", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.weight(1f))
            if (!viewModel.readonlyMode) {
                state.data?.let { barcode ->
                    Row(horizontalArrangement = Arrangement.spacedBy((-8).dp)) {
                        if (viewModel.barcodeJustCreated) {
                            IconButton(onClick = onNewBarcode) {
                                Icon(Icons.Default.NoteAdd, contentDescription = "New Barcode")
                            }
                        }
                        IconButton(onClick = onEditBarcode) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit Barcode")
                        }
                        BadgedBox(
                            badge = {
                                if (viewModel.selectedBarcodes.isNotEmpty()) {
                                    Badge { Text(viewModel.selectedBarcodes.size.toString()) }
                                }
                            }
                        ) {
                            val isInCart = barcode.code in viewModel.selectedBarcodes
                            val isOnLoan = barcode.apiLoanInfo?.loan == true
                            val cartEnabled = isInCart || !isOnLoan
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .combinedClickable(
                                        enabled = cartEnabled,
                                        onClick = { viewModel.toggleBarcodeSelection(barcode.code) },
                                        onLongClick = {
                                            viewModel.expressLoan(barcode)
                                            onGoToCart()
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    if (isInCart) Icons.Default.RemoveShoppingCart
                                    else Icons.Default.AddShoppingCart,
                                    contentDescription = if (isInCart) "Remove from loan cart"
                                                         else "Add to loan cart",
                                    tint = when {
                                        isInCart -> MaterialTheme.colorScheme.primary
                                        !cartEnabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                        else -> LocalContentColor.current
                                    }
                                )
                            }
                        }
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete")
                        }
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(currentIndex) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            when {
                                barcodeList != null && dragTotal < -swipeThresholdPx && currentIndex < barcodeList.size - 1 ->
                                    viewModel.navigateToBarcodeInList(currentIndex + 1)
                                barcodeList != null && dragTotal > swipeThresholdPx && currentIndex > 0 ->
                                    viewModel.navigateToBarcodeInList(currentIndex - 1)
                                barcodeList == null && dragTotal < -swipeThresholdPx && viewModel.barcodeForwardHistory.isNotEmpty() ->
                                    viewModel.navigateForward()
                            }
                            dragTotal = 0f
                        },
                        onDragCancel = { dragTotal = 0f }
                    ) { _, dragAmount -> dragTotal += dragAmount }
                }
        ) {
            if (deleteState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
            when {
                deleteState.isLoading -> Unit
                state.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                state.error != null -> Text(
                    text = state.error,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp)
                )
                state.data != null -> {
                    val barcode = state.data

                    LaunchedEffect(viewModel.saveContentState.data) {
                        if (viewModel.saveContentState.data != null) {
                            val target = (scrollState.value + (contentSectionY - viewportTopY))
                                .roundToInt().coerceAtLeast(0)
                            scrollState.animateScrollTo(target)
                        }
                    }

                    Column(modifier = Modifier.fillMaxSize().imePadding()) {
                        HorizontalDivider()
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .onGloballyPositioned { viewportTopY = it.boundsInRoot().top }
                                .verticalScrollbar(scrollState)
                                .verticalScroll(scrollState)
                                .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            DetailRow("Item", barcode.itemName, valueColor = MaterialTheme.colorScheme.onSurface, onClick = onItemClick)
                            DetailRow("Barcode", barcode.code)
                            if (barcode.itemImage != null)
                                ItemImagePreview(
                                    model = barcode.itemImage,
                                    imageLoader = viewModel.imageLoader,
                                    contentDescription = "Item image",
                                    maxHeight = 320.dp,
                                    onClick = { showFullscreenImage = true }
                                )

                            LocationSection(
                                barcode = barcode,
                                viewModel = viewModel,
                                onBarcodeClick = onBarcodeClick,
                                onScanParent = onScanParent
                            )
                            if (barcode.description.isNotBlank())
                                DetailRow("Barcode description", barcode.description)
                            if (barcode.itemDescription.isNotBlank())
                                DetailRow("Item description", barcode.itemDescription)
                            if (barcode.owner != null)
                                DetailRow("Owner", ownerName ?: "…")
                            barcode.apiLoanInfo?.let { loan ->
                                val text = if (loan.loan)
                                    "On loan${loan.person?.let { " — $it" } ?: ""}"
                                else "Available"
                                val color = if (loan.loan) LOAN_BLUE else GREEN
                                DetailRow("Loan", text, valueColor = color)
                            }
                            if (barcode.code in viewModel.selectedBarcodes)
                                Text(
                                    "Already in loan selection",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )

                            Box(modifier = Modifier.onGloballyPositioned { contentSectionY = it.boundsInRoot().top }) {
                                ContentListSection(
                                    viewModel = viewModel,
                                    barcode = barcode,
                                    onBarcodeClick = onBarcodeClick,
                                    onAddContent = onAddContent,
                                    onReScan = { viewModel.startContentScan(); onScanContent() },
                                    onNewBarcodeAsChild = onNewBarcodeAsChild
                                )
                            }
                        }

                        val existingChildren = barcode.apiChildNames ?: emptyList()
                        val contentHasChanges = viewModel.newScannedBarcodes.isNotEmpty() ||
                            existingChildren.any { it.code !in viewModel.scannedChildCodes }
                        val showSave = (viewModel.contentScanActive && contentHasChanges) ||
                            (viewModel.addContentScanActive && viewModel.newScannedBarcodes.isNotEmpty())
                        if (!viewModel.readonlyMode && showSave) {
                            HorizontalDivider()
                            ContentButtonsSection(viewModel = viewModel, barcode = barcode)
                        }

                        if (barcodeList != null) {
                            HorizontalDivider()
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = { viewModel.navigateToBarcodeInList(currentIndex - 1) },
                                    enabled = currentIndex > 0
                                ) {
                                    Icon(Icons.Default.KeyboardArrowLeft, "Previous barcode")
                                }
                                Text(
                                    "${currentIndex + 1} / ${barcodeList.size}",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                IconButton(
                                    onClick = { viewModel.navigateToBarcodeInList(currentIndex + 1) },
                                    enabled = currentIndex < barcodeList.size - 1
                                ) {
                                    Icon(Icons.Default.KeyboardArrowRight, "Next barcode")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    PullToRefreshContainer(state = pullToRefreshState, modifier = Modifier.align(Alignment.TopCenter))
    }
}

@Composable
private fun LocationSection(
    barcode: Barcode,
    viewModel: AppViewModel,
    onBarcodeClick: (String) -> Unit,
    onScanParent: () -> Unit
) {
    val pendingParent = viewModel.pendingNewParent
    val saveState = viewModel.saveParentState

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Location",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!viewModel.readonlyMode) {
                IconButton(
                    onClick = onScanParent,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Outlined.QrCodeScanner,
                        contentDescription = "Scan new parent",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
        Spacer(Modifier.height(2.dp))

        if (pendingParent == null) {
            // Normal location display
            val ancestors = barcode.apiParentNames ?: emptyList()
            if (ancestors.isEmpty()) {
                Text("—", style = MaterialTheme.typography.bodyLarge)
            } else {
                BreadcrumbList(
                    ancestors = ancestors.reversed(),
                    highlightCodes = emptySet(),
                    onBarcodeClick = onBarcodeClick
                )
            }
        } else {
            // Pending new parent display with colour coding
            val oldCodes = (barcode.apiParentNames ?: emptyList()).map { it.code }.toSet()
            val newChain = listOf(
                ChildInfo(name = "${pendingParent.itemName} (${pendingParent.code})", code = pendingParent.code)
            ) + (pendingParent.apiParentNames ?: emptyList()).reversed()
            val highlightCodes = newChain.filter { it.code !in oldCodes }.map { it.code }.toSet()

            BreadcrumbList(
                ancestors = newChain,
                highlightCodes = highlightCodes,
                onBarcodeClick = onBarcodeClick
            )

            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { viewModel.saveNewParent(barcode) },
                    enabled = !saveState.isLoading,
                    modifier = Modifier.weight(1f)
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
                OutlinedButton(
                    onClick = { viewModel.clearPendingNewParent() },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancel")
                }
            }
            if (saveState.error != null) {
                Text(
                    saveState.error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun BreadcrumbList(
    ancestors: List<ChildInfo>,
    highlightCodes: Set<String>,
    onBarcodeClick: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        ancestors.forEachIndexed { i, info ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (i > 0) {
                    Text("› ", style = MaterialTheme.typography.bodyLarge, color = GREY)
                }
                val linkColor = if (info.code in highlightCodes) GREEN
                                else MaterialTheme.colorScheme.onSurface
                Text(
                    text = buildAnnotatedString {
                        append(info.name.removeSuffix(" (${info.code})"))
                        append(" ")
                        withStyle(SpanStyle(color = if (info.code in highlightCodes) GREEN.copy(alpha = 0.7f) else GREY)) {
                            append("(${info.code})")
                        }
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = linkColor,
                    modifier = Modifier.clickable { onBarcodeClick(info.code) }
                )
            }
        }
    }
}

@Composable
private fun ContentListSection(
    viewModel: AppViewModel,
    barcode: Barcode,
    onBarcodeClick: (String) -> Unit,
    onAddContent: () -> Unit,
    onReScan: () -> Unit,
    onNewBarcodeAsChild: () -> Unit = {}
) {
    val scanActive = viewModel.contentScanActive
    val existingChildren = barcode.apiChildNames ?: emptyList()
    val totalCount = existingChildren.size + viewModel.newScannedBarcodes.size

    var showTextSearch by remember { mutableStateOf(false) }
    var textSearchQuery by remember { mutableStateOf("") }
    var textSearchSuggestions by remember { mutableStateOf<List<de.backspace.lgr.data.model.Barcode>>(emptyList()) }
    var showSuggestions by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel.addContentScanActive) {
        if (!viewModel.addContentScanActive) {
            showTextSearch = false
            textSearchQuery = ""
            textSearchSuggestions = emptyList()
            showSuggestions = false
        }
    }

    LaunchedEffect(textSearchQuery) {
        if (textSearchQuery.length < 2) {
            textSearchSuggestions = emptyList()
            showSuggestions = false
            return@LaunchedEffect
        }
        kotlinx.coroutines.delay(300)
        val results = viewModel.searchBarcodes(textSearchQuery)
        textSearchSuggestions = results
        showSuggestions = results.isNotEmpty()
    }

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
            if (!viewModel.readonlyMode) {
                Row {
                    // Search to add
                    IconButton(
                        onClick = {
                            showTextSearch = !showTextSearch
                            if (!showTextSearch) {
                                textSearchQuery = ""
                                textSearchSuggestions = emptyList()
                                showSuggestions = false
                            }
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            if (showTextSearch) Icons.Default.Close else Icons.Outlined.Search,
                            contentDescription = if (showTextSearch) "Close search" else "Search additional content",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    // Scan to add
                    IconButton(onClick = onAddContent, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Outlined.QrCodeScanner,
                            contentDescription = "Scan to add",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    // New barcode as child
                    IconButton(onClick = onNewBarcodeAsChild, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Outlined.NoteAdd,
                            contentDescription = "New barcode in this location",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    // Verify
                    IconButton(onClick = onReScan, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Outlined.FactCheck,
                            contentDescription = "Re-scan content",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        if (showTextSearch) {
            Column {
                OutlinedTextField(
                    value = textSearchQuery,
                    onValueChange = { textSearchQuery = it },
                    label = { Text("Search additional content") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = lgrTextFieldColors(),
                    trailingIcon = {
                        if (textSearchQuery.isNotEmpty()) {
                            IconButton(onClick = { textSearchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    }
                )
                if (showSuggestions) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Column {
                            textSearchSuggestions.forEach { suggestion ->
                                val isSelf = suggestion.code == barcode.code
                                val isAlreadyContent =
                                    existingChildren.any { it.code == suggestion.code } ||
                                    viewModel.newScannedBarcodes.any { it.code == suggestion.code }
                                val isDisabled = isSelf || isAlreadyContent
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .then(if (!isDisabled) Modifier.clickable {
                                            viewModel.addBarcodeToContent(suggestion)
                                        } else Modifier)
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = suggestion.itemName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (isDisabled) GREY else Color.Unspecified,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = "(${suggestion.code})",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = GREY
                                    )
                                }
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
        }

        if (scanActive) {
            // Verify mode: identical two-column "Current | Scanned" view as the Verify screen.
            VerifyContentColumns(
                dbChildren = existingChildren,
                scannedCodes = viewModel.scannedChildCodes,
                extraScanned = viewModel.newScannedBarcodes,
                onBarcodeClick = onBarcodeClick
            )
        } else {
            existingChildren.forEach { child ->
                ChildRow(
                    itemName = child.name.removeSuffix(" (${child.code})"),
                    code = child.code,
                    color = MaterialTheme.colorScheme.onSurface,
                    loanInfo = viewModel.childLoanInfos[child.code],
                    onClick = { onBarcodeClick(child.code) }
                )
            }

            viewModel.newScannedBarcodes.forEach { child ->
                ChildRow(
                    itemName = child.itemName,
                    code = child.code,
                    color = GREEN,
                    loanInfo = child.apiLoanInfo,
                    onClick = { onBarcodeClick(child.code) }
                )
            }

            if (existingChildren.isEmpty() && viewModel.newScannedBarcodes.isEmpty()) {
                Text("—", style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun ContentButtonsSection(viewModel: AppViewModel, barcode: Barcode) {
    val contentScanActive = viewModel.contentScanActive
    val saveState = viewModel.saveContentState

    Column(
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { viewModel.cancelContentScan() },
                modifier = Modifier.weight(1f),
                enabled = !saveState.isLoading
            ) { Text("Cancel") }
            Button(
                onClick = {
                    if (contentScanActive) viewModel.saveContentChanges(barcode)
                    else viewModel.saveAddedContent(barcode)
                },
                modifier = Modifier.weight(1f),
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
        }

        if (saveState.error != null) {
            Text(
                saveState.error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun ChildRow(
    itemName: String,
    code: String,
    color: Color,
    loanInfo: LoanInfo? = null,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp)
    ) {
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = color)) { append(itemName) }
                append(" ")
                withStyle(SpanStyle(color = if (color == GREEN) GREEN.copy(alpha = 0.7f) else GREY)) {
                    append("($code)")
                }
            },
            style = MaterialTheme.typography.bodyMedium
        )
        if (loanInfo?.loan == true) {
            Text(
                text = "On loan${loanInfo.person?.let { " — $it" } ?: ""}",
                style = MaterialTheme.typography.labelSmall,
                color = LOAN_BLUE
            )
        }
    }
}

