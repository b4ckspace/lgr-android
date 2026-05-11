package de.uhsemann.lgr.ui.screens

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BarcodeDetailScreen(viewModel: AppViewModel, onBack: () -> Unit, onScanContent: () -> Unit) {
    val state = viewModel.scannedBarcode
    val barcodeList = viewModel.barcodeListContext
    val currentIndex = viewModel.barcodeListIndex
    val swipeThresholdPx = with(LocalDensity.current) { 80.dp.toPx() }
    var dragTotal by remember(currentIndex) { mutableStateOf(0f) }

    val ownerName by produceState<String?>(null, state.data?.owner) {
        val ownerUrl = state.data?.owner
        value = if (ownerUrl != null) viewModel.resolveOwnerName(ownerUrl) else null
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
                    state.data?.let { barcode ->
                        IconButton(onClick = {
                            viewModel.toggleBarcodeSelection(barcode.code)
                            onBack()
                        }) {
                            Icon(Icons.Default.ShoppingCart, contentDescription = "Select for Loan")
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
                    if (barcodeList != null) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                when {
                                    dragTotal < -swipeThresholdPx && currentIndex < barcodeList.size - 1 ->
                                        viewModel.navigateToBarcodeInList(currentIndex + 1)
                                    dragTotal > swipeThresholdPx && currentIndex > 0 ->
                                        viewModel.navigateToBarcodeInList(currentIndex - 1)
                                }
                                dragTotal = 0f
                            },
                            onDragCancel = { dragTotal = 0f }
                        ) { _, dragAmount -> dragTotal += dragAmount }
                    }
                }
        ) {
            when {
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
                                BreadcrumbRow(
                                    label = "Location",
                                    ancestors = barcode.apiParentNames ?: emptyList()
                                )
                            }

                            item { DetailRow("Barcode", barcode.code) }
                            item { DetailRow("Item", barcode.itemName) }
                            if (barcode.itemDescription.isNotBlank())
                                item { DetailRow("Item description", barcode.itemDescription) }
                            if (barcode.description.isNotBlank())
                                item { DetailRow("Description", barcode.description) }
                            if (barcode.owner != null)
                                item { DetailRow("Owner", ownerName ?: "…") }
                            barcode.apiLoanInfo?.let { loan ->
                                item {
                                    val text = if (loan.loan)
                                        "On loan${loan.person?.let { " — $it" } ?: ""}"
                                    else "Available"
                                    val color = if (loan.loan) Color(0xFFE65100) else Color(0xFF4CAF50)
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

                            // Contents section
                            item {
                                ContentSection(
                                    viewModel = viewModel,
                                    barcode = barcode,
                                    onScanContent = onScanContent
                                )
                            }
                        }

                        // Prev / Next navigation bar
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
                                Text("${currentIndex + 1} / ${barcodeList.size}",
                                    style = MaterialTheme.typography.bodyMedium)
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
private fun BreadcrumbRow(label: String, ancestors: List<ChildInfo>) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(2.dp))
        if (ancestors.isEmpty()) {
            Text("—", style = MaterialTheme.typography.bodyLarge)
        } else {
            Text(
                text = buildAnnotatedString {
                    ancestors.forEachIndexed { i, info ->
                        if (i > 0) append(" › ")
                        append(info.name.removeSuffix(" (${info.code})"))
                        append(" ")
                        withStyle(SpanStyle(color = GREY)) { append("(${info.code})") }
                    }
                },
                style = MaterialTheme.typography.bodyLarge
            )
        }
        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun ContentSection(viewModel: AppViewModel, barcode: Barcode, onScanContent: () -> Unit) {
    val scanActive = viewModel.contentScanActive
    val saveState = viewModel.saveContentState
    val existingChildren = barcode.apiChildNames ?: emptyList()
    val totalCount = existingChildren.size + viewModel.newScannedBarcodes.size

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Contents ($totalCount)",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        existingChildren.forEach { child ->
            val color = when {
                !scanActive -> MaterialTheme.colorScheme.onSurface
                child.code in viewModel.scannedChildCodes -> MaterialTheme.colorScheme.onSurface
                else -> GREY
            }
            ChildRow(
                itemName = child.name.removeSuffix(" (${child.code})"),
                code = child.code,
                color = color,
                loanInfo = viewModel.childLoanInfos[child.code]
            )
        }

        viewModel.newScannedBarcodes.forEach { child ->
            ChildRow(
                itemName = child.itemName,
                code = child.code,
                color = GREEN,
                loanInfo = child.apiLoanInfo
            )
        }

        if (existingChildren.isEmpty() && viewModel.newScannedBarcodes.isEmpty()) {
            Text("—", style = MaterialTheme.typography.bodyLarge)
        }

        HorizontalDivider()

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    viewModel.startContentScan()
                    onScanContent()
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Scan content")
            }

            if (scanActive) {
                Button(
                    onClick = { viewModel.saveContentChanges(barcode) },
                    modifier = Modifier.weight(1f),
                    enabled = !saveState.isLoading
                ) {
                    if (saveState.isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Text("Save")
                    }
                }
            }
        }

        if (saveState.error != null) {
            Text(saveState.error, color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ChildRow(itemName: String, code: String, color: Color, loanInfo: LoanInfo? = null) {
    Column(modifier = Modifier.padding(vertical = 2.dp)) {
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
                color = Color(0xFFE65100)
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
