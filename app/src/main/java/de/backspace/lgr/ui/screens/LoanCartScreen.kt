package de.backspace.lgr.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import de.backspace.lgr.viewmodel.AppViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val GREY_CART = Color(0xFF9E9E9E)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoanCartScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onBarcodeClick: (String) -> Unit,
    onConfirmed: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    val listState = rememberLazyListState()
    var description by remember { mutableStateOf("") }
    var returnDate by remember { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState()

    val loanState = viewModel.loanState
    val hasPreview = loanState.data != null
    val availableItems = loanState.data?.items ?: emptyList()
    val blockedItems = loanState.data?.blocked ?: emptyList()

    LaunchedEffect(loanState.data) {
        if (loanState.data?.message?.contains("created") == true) {
            onConfirmed()
        }
    }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        returnDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(millis))
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) { DatePicker(state = datePickerState) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Loan Cart (${viewModel.selectedBarcodes.size})") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.Close, "Close") } }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding).verticalScrollbar(listState)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { Spacer(Modifier.height(4.dp)) }

            // Description
            item {
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            // Return date picker
            item {
                OutlinedTextField(
                    value = returnDate,
                    onValueChange = { returnDate = it },
                    label = { Text("Return date (optional)") },
                    placeholder = { Text("YYYY-MM-DD") },
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        IconButton(onClick = { showDatePicker = true }) {
                            Icon(Icons.Default.CalendarMonth, contentDescription = "Pick date")
                        }
                    },
                    singleLine = true
                )
            }

            item { Spacer(Modifier.height(4.dp)) }

            // Barcode list
            items(viewModel.selectedBarcodes.toList()) { code ->
                val detail = viewModel.selectedBarcodeDetails[code]
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onBarcodeClick(code) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = buildAnnotatedString {
                                if (detail != null && detail.itemName.isNotBlank()) {
                                    append(detail.itemName)
                                    append(" ")
                                }
                                withStyle(SpanStyle(color = GREY_CART)) { append("($code)") }
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        IconButton(onClick = { viewModel.toggleBarcodeSelection(code) }) {
                            Icon(Icons.Default.Close, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            // Preview result
            if (hasPreview) {
                item {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Text("Preview", style = MaterialTheme.typography.titleSmall)
                }
                viewModel.loanConflictMessage?.let { msg ->
                    item {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    msg,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }
                if (availableItems.isNotEmpty()) {
                    item {
                        Text(
                            "Available (${availableItems.size})",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    items(availableItems) { bs ->
                        Text(
                            text = buildAnnotatedString {
                                append("• ")
                                if (bs.itemName.isNotBlank()) {
                                    append(bs.itemName)
                                    append(" ")
                                }
                                withStyle(SpanStyle(color = GREY_CART)) { append("(${bs.code})") }
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (blockedItems.isNotEmpty()) {
                    item {
                        Text(
                            "Blocked (${blockedItems.size})",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    items(blockedItems) { bs ->
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                            Text(
                                text = buildAnnotatedString {
                                    append("• ")
                                    if (bs.itemName.isNotBlank()) {
                                        append(bs.itemName)
                                        append(" ")
                                    }
                                    withStyle(SpanStyle(color = GREY_CART)) { append("(${bs.code})") }
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                "On loan — ${bs.person}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
                loanState.data?.message?.takeIf { it.isNotBlank() }?.let { msg ->
                    item {
                        Text(msg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }

            loanState.error?.let { err ->
                item {
                    Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }

            // Buttons
            item {
                val effectiveDate = returnDate.takeIf { it.isNotBlank() }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { focusManager.clearFocus(); viewModel.previewLoan(effectiveDate, description.takeIf { it.isNotBlank() }) },
                        enabled = viewModel.selectedBarcodes.isNotEmpty() && !loanState.isLoading,
                        modifier = Modifier.weight(1f)
                    ) { Text("Preview") }
                    Button(
                        onClick = { viewModel.confirmLoan(effectiveDate, description.takeIf { it.isNotBlank() }) },
                        enabled = hasPreview && availableItems.isNotEmpty() && !loanState.isLoading,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (loanState.isLoading) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        else Text("Confirm")
                    }
                }
            }

            item { Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars)) }
        }
        } // Box
    }
}
