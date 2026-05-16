package de.uhsemann.lgr.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import de.uhsemann.lgr.data.model.Barcode
import de.uhsemann.lgr.data.model.ChildInfo
import de.uhsemann.lgr.data.model.LoanInfo
import de.uhsemann.lgr.viewmodel.AppViewModel

private val GREY = Color(0xFF9E9E9E)
private val GREEN = Color(0xFF4CAF50)
private val RED = Color(0xFFE53935)
private val LOAN_BLUE = Color(0xFF1976D2)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BarcodeDetailScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onScanContent: () -> Unit,
    onScanParent: () -> Unit,
    onAddContent: () -> Unit
) {
    val state = viewModel.scannedBarcode
    val barcodeList = viewModel.barcodeListContext
    val currentIndex = viewModel.barcodeListIndex
    val swipeThresholdPx = with(LocalDensity.current) { 80.dp.toPx() }
    var dragTotal by remember(currentIndex) { mutableStateOf(0f) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val deleteState = viewModel.deleteBarcodeState
    LaunchedEffect(deleteState.data) {
        if (deleteState.data != null) {
            viewModel.resetDeleteBarcodeState()
            onBack()
        }
    }

    val ownerName by produceState<String?>(null, state.data?.owner) {
        val ownerUrl = state.data?.owner
        value = if (ownerUrl != null) viewModel.resolveOwnerName(ownerUrl) else null
    }

    BackHandler(enabled = viewModel.barcodeHistory.isNotEmpty()) {
        val prevCode = viewModel.popBarcodeHistory() ?: return@BackHandler
        viewModel.loadBarcode(prevCode)
    }

    val onBarcodeClick: (String) -> Unit = { code -> viewModel.navigateToBarcode(code) }

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (barcodeList != null) Text("Barcode ${currentIndex + 1} / ${barcodeList.size}")
                    else Text("Barcode")
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (!viewModel.readonlyMode) {
                        state.data?.let { barcode ->
                            IconButton(onClick = { showDeleteDialog = true }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete")
                            }
                            IconButton(onClick = {
                                viewModel.toggleBarcodeSelection(barcode.code)
                                onBack()
                            }) {
                                Icon(Icons.Default.ShoppingCart, contentDescription = "Select for Loan")
                            }
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
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

                    Column(modifier = Modifier.fillMaxSize()) {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            contentPadding = PaddingValues(24.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            item {
                                LocationSection(
                                    barcode = barcode,
                                    viewModel = viewModel,
                                    onBarcodeClick = onBarcodeClick,
                                    onScanParent = onScanParent
                                )
                            }

                            item { DetailRow("Barcode", barcode.code) }
                            item { DetailRow("Item", barcode.itemName) }
                            if (barcode.description.isNotBlank())
                                item { DetailRow("Description", barcode.description) }
                            if (barcode.itemDescription.isNotBlank())
                                item { DetailRow("Item description", barcode.itemDescription) }
                            if (barcode.owner != null)
                                item { DetailRow("Owner", ownerName ?: "…") }
                            barcode.apiLoanInfo?.let { loan ->
                                item {
                                    val text = if (loan.loan)
                                        "On loan${loan.person?.let { " — $it" } ?: ""}"
                                    else "Available"
                                    val color = if (loan.loan) LOAN_BLUE else GREEN
                                    DetailRow("Loan", text, valueColor = color)
                                }
                            }
                            if (barcode.code in viewModel.selectedBarcodes)
                                item {
                                    Text(
                                        "Already in loan selection",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                            item {
                                ContentListSection(
                                    viewModel = viewModel,
                                    barcode = barcode,
                                    onBarcodeClick = onBarcodeClick,
                                    onAddContent = onAddContent
                                )
                            }
                        }

                        if (!viewModel.readonlyMode) {
                            HorizontalDivider()
                            ContentButtonsSection(
                                viewModel = viewModel,
                                barcode = barcode,
                                onScanContent = onScanContent
                            )
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
                        Icons.Default.QrCodeScanner,
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
    onAddContent: () -> Unit
) {
    val scanActive = viewModel.contentScanActive
    val existingChildren = barcode.apiChildNames ?: emptyList()
    val totalCount = existingChildren.size + viewModel.newScannedBarcodes.size

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
                IconButton(
                    onClick = onAddContent,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.QrCodeScanner,
                        contentDescription = "Add contents",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        existingChildren.forEach { child ->
            val color = when {
                !scanActive -> MaterialTheme.colorScheme.onSurface
                child.code in viewModel.scannedChildCodes -> MaterialTheme.colorScheme.onSurface
                viewModel.childLoanInfos[child.code]?.loan == true -> GREY
                else -> RED
            }
            ChildRow(
                itemName = child.name.removeSuffix(" (${child.code})"),
                code = child.code,
                color = color,
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

@Composable
private fun ContentButtonsSection(viewModel: AppViewModel, barcode: Barcode, onScanContent: () -> Unit) {
    val contentScanActive = viewModel.contentScanActive
    val addContentScanActive = viewModel.addContentScanActive
    val showSave = contentScanActive || addContentScanActive
    val saveState = viewModel.saveContentState

    Column(
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    viewModel.startContentScan()
                    onScanContent()
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Re-scan all content")
            }

            if (showSave) {
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

@Composable
private fun DetailRow(label: String, value: String, valueColor: Color = Color.Unspecified) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(2.dp))
        Text(text = value, style = MaterialTheme.typography.bodyLarge, color = valueColor)
        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
    }
}
