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
    val location by produceState<List<Barcode>?>(null, state.data) {
        val b = state.data ?: return@produceState
        value = viewModel.resolveLocation(b)
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
                    val loc = location

                    Column(modifier = Modifier.fillMaxSize()) {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            contentPadding = PaddingValues(24.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Location breadcrumb
                            item {
                                BreadcrumbRow(
                                    label = "Location",
                                    ancestors = loc,
                                    hasParent = barcode.parent != null
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
private fun BreadcrumbRow(label: String, ancestors: List<Barcode>?, hasParent: Boolean) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(2.dp))
        when {
            !hasParent -> Text("—", style = MaterialTheme.typography.bodyLarge)
            ancestors == null -> Text("…", style = MaterialTheme.typography.bodyLarge)
            ancestors.isEmpty() -> Text("—", style = MaterialTheme.typography.bodyLarge)
            else -> Text(
                text = buildAnnotatedString {
                    ancestors.forEachIndexed { i, b ->
                        if (i > 0) append(" › ")
                        append(b.itemName)
                        append(" ")
                        withStyle(SpanStyle(color = GREY)) { append("(${b.code})") }
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
    val childState = viewModel.childBarcodes
    val scanActive = viewModel.contentScanActive
    val saveState = viewModel.saveContentState

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val childCount = childState.data?.size ?: 0
        val totalCount = childCount + viewModel.newScannedBarcodes.size
        Text(
            "Contents ($totalCount)",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        when {
            childState.isLoading -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            childState.error != null -> Text(
                childState.error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
            else -> {
                val children = childState.data ?: emptyList()

                // Existing children
                children.forEach { child ->
                    val color = when {
                        !scanActive -> MaterialTheme.colorScheme.onSurface
                        child.code in viewModel.scannedChildCodes -> MaterialTheme.colorScheme.onSurface
                        else -> GREY
                    }
                    ChildRow(barcode = child, color = color)
                }

                // New barcodes from scan
                viewModel.newScannedBarcodes.forEach { child ->
                    ChildRow(barcode = child, color = GREEN)
                }

                if (children.isEmpty() && viewModel.newScannedBarcodes.isEmpty()) {
                    Text("—", style = MaterialTheme.typography.bodyLarge)
                }
            }
        }

        HorizontalDivider()

        // Buttons row
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
private fun ChildRow(barcode: Barcode, color: Color) {
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(color = color)) { append(barcode.itemName) }
            append(" ")
            withStyle(SpanStyle(color = if (color == MaterialTheme.colorScheme.onSurface) GREY else color.copy(alpha = 0.7f))) {
                append("(${barcode.code})")
            }
        },
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(vertical = 2.dp)
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(2.dp))
        Text(text = value, style = MaterialTheme.typography.bodyLarge)
        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
    }
}
