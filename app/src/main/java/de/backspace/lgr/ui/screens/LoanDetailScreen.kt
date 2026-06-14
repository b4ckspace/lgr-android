// SPDX-FileCopyrightText: 2026 Andreas Uhsemann
// SPDX-License-Identifier: Apache-2.0

package de.backspace.lgr.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import de.backspace.lgr.viewmodel.AppViewModel

private val GREY_LOAN = Color(0xFF9E9E9E)
private val LOAN_STATUS_TAKEN_COLOR = Color(0xFFD32F2F)
private val LOAN_STATUS_RETURNED_COLOR = Color(0xFF388E3C)

@Composable
fun LoanDetailScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onBarcodeClick: (String) -> Unit
) {
    val loan = viewModel.currentLoan ?: run { onBack(); return }
    val isMyLoan = viewModel.currentLoanIsMyLoan
    val isTaken = loan.status.equals("taken", ignoreCase = true)
    val returnState = viewModel.returnLoanState

    val loanList = viewModel.loanListContext
    val currentIndex = viewModel.loanListIndex
    val swipeThresholdPx = with(LocalDensity.current) { 60.dp.toPx() }
    var dragTotal by remember(currentIndex) { mutableStateOf(0f) }

    val scrollState = rememberScrollState()
    var showReturnConfirm by remember { mutableStateOf(false) }

    if (showReturnConfirm) {
        AlertDialog(
            onDismissRequest = { showReturnConfirm = false },
            title = { Text("Return loan") },
            text = { Text("Mark loan #${loan.id} as returned?") },
            confirmButton = {
                TextButton(onClick = {
                    showReturnConfirm = false
                    viewModel.returnCurrentLoan()
                }) { Text("Return") }
            },
            dismissButton = {
                TextButton(onClick = { showReturnConfirm = false }) { Text("Cancel") }
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(currentIndex) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        when {
                            loanList != null && dragTotal < -swipeThresholdPx && currentIndex < loanList.size - 1 ->
                                viewModel.navigateToLoanInList(currentIndex + 1)
                            loanList != null && dragTotal > swipeThresholdPx && currentIndex > 0 ->
                                viewModel.navigateToLoanInList(currentIndex - 1)
                        }
                        dragTotal = 0f
                    },
                    onDragCancel = { dragTotal = 0f }
                ) { _, dragAmount -> dragTotal += dragAmount }
            }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text("Loan #${loan.id ?: "—"}", style = MaterialTheme.typography.titleLarge)
            }
            HorizontalDivider()

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScrollbar(scrollState)
                    .verticalScroll(scrollState)
                    .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                DetailRow(
                    "Status",
                    loan.status.uppercase(),
                    valueColor = if (isTaken) LOAN_STATUS_TAKEN_COLOR else LOAN_STATUS_RETURNED_COLOR
                )

                loan.personName?.takeIf { it.isNotBlank() }?.let { DetailRow("Person", it) }
                loan.description?.takeIf { it.isNotBlank() }?.let { DetailRow("Description", it) }
                loan.takenDate?.let { DetailRow("Taken", it.take(10)) }
                loan.returnDate?.let {
                    val dateStr = it.take(10)
                    val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
                    val isOverdue = isTaken && dateStr < today
                    DetailRow("Return by", dateStr, valueColor = if (isOverdue) LOAN_STATUS_TAKEN_COLOR else Color.Unspecified)
                }
                loan.returnedDate?.let { DetailRow("Returned", it.take(10)) }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Barcodes (${loan.barcodes.size})",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (loan.barcodes.isEmpty()) {
                        Text("—", style = MaterialTheme.typography.bodyMedium)
                    } else {
                        loan.barcodes.forEach { code ->
                            val itemName by produceState(viewModel.cachedBarcodeName(code), code) {
                                value = viewModel.resolveBarcodeName(code)
                            }
                            LoanBarcodeRow(itemName, code, onClick = { onBarcodeClick(code) })
                        }
                    }
                }

                if (returnState.error != null) {
                    Text(returnState.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                if (isMyLoan && isTaken) {
                    Button(
                        onClick = { showReturnConfirm = true },
                        enabled = !returnState.isLoading,
                        colors = ButtonDefaults.buttonColors(containerColor = LOAN_STATUS_RETURNED_COLOR),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (returnState.isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        } else {
                            Text("Return loan")
                        }
                    }
                }
            }

            if (loanList != null) {
                HorizontalDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { viewModel.navigateToLoanInList(currentIndex - 1) },
                        enabled = currentIndex > 0
                    ) {
                        Icon(Icons.Default.KeyboardArrowLeft, "Previous loan")
                    }
                    Text(
                        "${currentIndex + 1} / ${loanList.size}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    IconButton(
                        onClick = { viewModel.navigateToLoanInList(currentIndex + 1) },
                        enabled = currentIndex < loanList.size - 1
                    ) {
                        Icon(Icons.Default.KeyboardArrowRight, "Next loan")
                    }
                }
            }
        }
    }
}

// A loan's barcode shown as "item name (code)" (code in grey), tappable to open it and
// long-pressable to copy the code — matching the Barcode Detail contents style.
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LoanBarcodeRow(itemName: String?, code: String, onClick: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    Text(
        text = buildAnnotatedString {
            if (!itemName.isNullOrBlank()) {
                append(itemName)
                append(" ")
            }
            withStyle(SpanStyle(color = GREY_LOAN)) { append("($code)") }
        },
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = { clipboard.setText(AnnotatedString(code)) }
            )
            .padding(vertical = 2.dp)
    )
}
