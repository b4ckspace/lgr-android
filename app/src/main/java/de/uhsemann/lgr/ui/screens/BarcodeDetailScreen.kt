package de.uhsemann.lgr.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.uhsemann.lgr.viewmodel.AppViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BarcodeDetailScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val state = viewModel.scannedBarcode

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Barcode") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            state.data?.let { barcode ->
                FloatingActionButton(onClick = {
                    viewModel.toggleBarcodeSelection(barcode.code)
                    onBack()
                }) {
                    Icon(Icons.Default.ShoppingCart, contentDescription = "Select for Loan")
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                state.error != null -> Text(
                    text = state.error,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp)
                )
                state.data != null -> {
                    val barcode = state.data
                    Column(modifier = Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        DetailRow(label = "Code", value = barcode.code)
                        DetailRow(label = "Item", value = barcode.itemName)
                        if (barcode.itemDescription.isNotBlank()) {
                            DetailRow(label = "Item description", value = barcode.itemDescription)
                        }
                        if (barcode.description.isNotBlank()) {
                            DetailRow(label = "Description", value = barcode.description)
                        }
                        if (barcode.owner != null) {
                            DetailRow(label = "Owner", value = barcode.owner)
                        }
                        Spacer(Modifier.height(8.dp))
                        val alreadySelected = barcode.code in viewModel.selectedBarcodes
                        if (alreadySelected) {
                            Text(
                                "Already in loan selection",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(2.dp))
        Text(text = value, style = MaterialTheme.typography.bodyLarge)
        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
    }
}
