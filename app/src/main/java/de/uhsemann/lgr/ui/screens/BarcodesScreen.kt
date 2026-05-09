package de.uhsemann.lgr.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.uhsemann.lgr.data.model.Barcode
import de.uhsemann.lgr.data.model.BarcodeStatus
import de.uhsemann.lgr.viewmodel.AppViewModel

@Composable
fun BarcodesScreen(viewModel: AppViewModel) {
    var search by remember { mutableStateOf("") }
    var showLoanDialog by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) { viewModel.loadBarcodes() }

    val shouldLoadMore by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= info.totalItemsCount - 3 && !viewModel.barcodes.isLoading
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) viewModel.loadMoreBarcodes()
    }

    if (showLoanDialog) {
        LoanDialog(
            viewModel = viewModel,
            onDismiss = {
                showLoanDialog = false
                viewModel.resetLoanState()
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it; viewModel.loadBarcodes(it) },
                label = { Text("Search barcodes (supports !user: !item: syntax)") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                singleLine = true
            )

            if (viewModel.selectedBarcodes.isNotEmpty()) {
                Surface(color = MaterialTheme.colorScheme.primaryContainer) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${viewModel.selectedBarcodes.size} barcode(s) selected",
                            style = MaterialTheme.typography.labelLarge
                        )
                        TextButton(onClick = { viewModel.clearBarcodeSelection() }) { Text("Clear") }
                    }
                }
            }

            val state = viewModel.barcodes
            when {
                state.isLoading && state.data == null -> LoadingBox()
                state.error != null && state.data == null -> ErrorBox(state.error)
                state.data != null -> LazyColumn(state = listState) {
                    items(state.data, key = { it.code }) { barcode ->
                        BarcodeCard(
                            barcode = barcode,
                            isSelected = barcode.code in viewModel.selectedBarcodes,
                            onToggle = { viewModel.toggleBarcodeSelection(barcode.code) }
                        )
                    }
                    if (viewModel.barcodesNextPage != null) {
                        item { LoadingFooter() }
                    }
                }
            }
        }

        if (viewModel.selectedBarcodes.isNotEmpty() && viewModel.isAuthenticated) {
            FloatingActionButton(
                onClick = { showLoanDialog = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            ) {
                Icon(Icons.Default.ShoppingCart, contentDescription = "Create loan")
            }
        }
    }
}

@Composable
fun BarcodeCard(barcode: Barcode, isSelected: Boolean, onToggle: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clickable(onClick = onToggle),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = isSelected, onCheckedChange = { onToggle() })
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp)
            ) {
                Text(barcode.code, style = MaterialTheme.typography.titleMedium)
                Text(
                    barcode.itemName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (barcode.description.isNotBlank()) {
                    Text(
                        barcode.description,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun LoanDialog(viewModel: AppViewModel, onDismiss: () -> Unit) {
    var returnDate by remember { mutableStateOf("") }
    val state = viewModel.loanState
    val success = state.data?.message?.contains("created", ignoreCase = true) == true

    LaunchedEffect(success) {
        if (success) onDismiss()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Loan") },
        text = {
            Column {
                Text(
                    "Selected: ${viewModel.selectedBarcodes.joinToString(", ")}",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(10.dp))

                OutlinedTextField(
                    value = returnDate,
                    onValueChange = { returnDate = it },
                    label = { Text("Return date (YYYY-MM-DD)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                if (state.isLoading) {
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                state.data?.let { response ->
                    Spacer(Modifier.height(12.dp))
                    if (!response.blocked.isNullOrEmpty()) {
                        Text("Blocked items:", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
                        response.blocked.forEach { BarcodeStatusRow(it, blocked = true) }
                    }
                    if (!response.items.isNullOrEmpty()) {
                        Text("Available items:", style = MaterialTheme.typography.labelMedium)
                        response.items.forEach { BarcodeStatusRow(it, blocked = false) }
                    }
                    response.message?.let {
                        Spacer(Modifier.height(4.dp))
                        Text(it, style = MaterialTheme.typography.bodySmall)
                    }
                }

                state.error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            val hasPreview = state.data != null
            val hasBlocked = state.data?.blocked?.isNotEmpty() == true
            when {
                !hasPreview -> Button(
                    onClick = { viewModel.previewLoan(returnDate.takeIf { it.isNotBlank() }) },
                    enabled = !state.isLoading
                ) { Text("Preview") }
                !hasBlocked -> Button(
                    onClick = { viewModel.confirmLoan(returnDate.takeIf { it.isNotBlank() }) },
                    enabled = !state.isLoading
                ) { Text("Confirm") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun BarcodeStatusRow(status: BarcodeStatus, blocked: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val color = if (blocked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
        Text("• ${status.code}", color = color, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        if (blocked && status.person.isNotBlank()) {
            Text("(${status.person})", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
