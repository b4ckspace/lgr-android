package de.backspace.lgr.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import de.backspace.lgr.viewmodel.AppViewModel

private val GREY_LOAN = Color(0xFF9E9E9E)

private val LOAN_STATUS_TAKEN_COLOR = Color(0xFFD32F2F)
private val LOAN_STATUS_RETURNED_COLOR = Color(0xFF388E3C)

@OptIn(ExperimentalMaterial3Api::class)
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
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Loan #${loan.id ?: "—"}") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                item { Spacer(Modifier.height(4.dp)) }

                item {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Status:", style = MaterialTheme.typography.bodyMedium)
                        val statusColor = if (isTaken) LOAN_STATUS_TAKEN_COLOR else LOAN_STATUS_RETURNED_COLOR
                        Badge(containerColor = statusColor) {
                            Text(loan.status.uppercase(), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                loan.personName?.takeIf { it.isNotBlank() }?.let { name ->
                    item {
                        Text("Person: $name", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 2.dp))
                    }
                }

                loan.description?.takeIf { it.isNotBlank() }?.let { desc ->
                    item {
                        Text("Description:", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
                        Text(desc, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                item {
                    Column(modifier = Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        loan.takenDate?.let { Text("Taken: ${it.take(10)}", style = MaterialTheme.typography.bodyMedium) }
                        loan.returnDate?.let {
                            val dateStr = it.take(10)
                            val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
                            val isOverdue = isTaken && dateStr < today
                            Text(
                                "Return by: $dateStr",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isOverdue) LOAN_STATUS_TAKEN_COLOR else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        loan.returnedDate?.let {
                            Text("Returned: ${it.take(10)}", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }

                item {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Text("Barcodes (${loan.barcodes.size})", style = MaterialTheme.typography.titleSmall)
                }

                items(loan.barcodes) { code ->
                    val itemName by produceState<String?>(null, code) {
                        value = viewModel.resolveBarcodeName(code)
                    }
                    Text(
                        text = buildAnnotatedString {
                            if (!itemName.isNullOrBlank()) {
                                append(itemName!!)
                                append(" ")
                            }
                            withStyle(SpanStyle(color = GREY_LOAN)) { append("($code)") }
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onBarcodeClick(code) }
                            .padding(vertical = 4.dp)
                    )
                }

                if (returnState.error != null) {
                    item {
                        Text(returnState.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                    }
                }

                if (isMyLoan && isTaken) {
                    item {
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = { showReturnConfirm = true },
                            enabled = !returnState.isLoading,
                            colors = ButtonDefaults.buttonColors(containerColor = LOAN_STATUS_TAKEN_COLOR),
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

                item { Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars)) }
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
    } // Box
}
