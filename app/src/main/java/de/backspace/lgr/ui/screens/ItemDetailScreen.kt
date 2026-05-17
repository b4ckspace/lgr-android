package de.backspace.lgr.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
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
import de.backspace.lgr.data.model.Barcode
import de.backspace.lgr.viewmodel.AppViewModel

private val ITEM_GREY = Color(0xFF9E9E9E)
private val ITEM_RED = Color(0xFFE53935)

@Composable
fun ItemDetailScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onBarcodeClick: (Barcode) -> Unit
) {
    val item = viewModel.currentItem ?: return
    val barcodesState = viewModel.itemBarcodes
    val deleteState = viewModel.deleteItemState
    val itemList = viewModel.itemListContext
    val currentIndex = viewModel.itemListIndex
    var showDeleteDialog by remember { mutableStateOf(false) }
    val hasBarcodes = barcodesState.data?.isNotEmpty() == true
    val swipeThresholdPx = with(LocalDensity.current) { 80.dp.toPx() }
    var dragTotal by remember(currentIndex) { mutableStateOf(0f) }

    LaunchedEffect(deleteState.data) {
        if (deleteState.data != null) {
            viewModel.resetDeleteItemState()
            onBack()
        }
    }

    LaunchedEffect(deleteState.error) {
        if (deleteState.error != null) showDeleteDialog = true
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false; viewModel.resetDeleteItemState() },
            title = { Text("Delete Item") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Are you sure you want to permanently delete item:")
                    Text(item.name, style = MaterialTheme.typography.titleMedium)
                    deleteState.error?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showDeleteDialog = false; viewModel.deleteItem() },
                    colors = ButtonDefaults.buttonColors(containerColor = ITEM_RED)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false; viewModel.resetDeleteItemState() }) {
                    Text("Cancel")
                }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
            Spacer(Modifier.weight(1f))
            if (!viewModel.readonlyMode) {
                IconButton(
                    onClick = { showDeleteDialog = true },
                    enabled = !hasBarcodes && !deleteState.isLoading
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete Item",
                        tint = if (hasBarcodes || deleteState.isLoading)
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        else
                            MaterialTheme.colorScheme.onSurface
                    )
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
                                itemList != null && dragTotal < -swipeThresholdPx && currentIndex < itemList.size - 1 ->
                                    viewModel.navigateToItemInList(currentIndex + 1)
                                itemList != null && dragTotal > swipeThresholdPx && currentIndex > 0 ->
                                    viewModel.navigateToItemInList(currentIndex - 1)
                            }
                            dragTotal = 0f
                        },
                        onDragCancel = { dragTotal = 0f }
                    ) { _, dragAmount -> dragTotal += dragAmount }
                }
        ) {
            if (deleteState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                return@Box
            }

            Column(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item { ItemDetailRow("Name", item.name) }

                    if (item.description.isNotBlank()) {
                        item { ItemDetailRow("Description", item.description) }
                    }

                    item {
                        val barcodes = barcodesState.data
                        val count = barcodes?.size ?: 0
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "Barcodes ($count)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            when {
                                barcodesState.isLoading -> CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                barcodesState.error != null -> Text(
                                    barcodesState.error,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall
                                )
                                barcodes != null && barcodes.isEmpty() -> Text("—", style = MaterialTheme.typography.bodyLarge)
                                barcodes != null -> barcodes.forEach { barcode ->
                                    ItemBarcodeRow(barcode = barcode, onClick = { onBarcodeClick(barcode) })
                                }
                            }
                            HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                }

                if (itemList != null) {
                    HorizontalDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { viewModel.navigateToItemInList(currentIndex - 1) },
                            enabled = currentIndex > 0
                        ) {
                            Icon(Icons.Default.KeyboardArrowLeft, "Previous item")
                        }
                        Text(
                            "${currentIndex + 1} / ${itemList.size}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        IconButton(
                            onClick = { viewModel.navigateToItemInList(currentIndex + 1) },
                            enabled = currentIndex < itemList.size - 1
                        ) {
                            Icon(Icons.Default.KeyboardArrowRight, "Next item")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ItemBarcodeRow(barcode: Barcode, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp)
    ) {
        Text(
            text = buildAnnotatedString {
                append(barcode.code)
                if (barcode.description.isNotBlank()) {
                    append(" ")
                    withStyle(SpanStyle(color = ITEM_GREY)) { append("— ${barcode.description}") }
                }
            },
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun ItemDetailRow(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(2.dp))
        Text(value, style = MaterialTheme.typography.bodyLarge)
        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
    }
}
