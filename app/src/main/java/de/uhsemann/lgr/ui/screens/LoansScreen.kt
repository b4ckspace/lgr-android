package de.uhsemann.lgr.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.uhsemann.lgr.data.model.Loan
import de.uhsemann.lgr.viewmodel.AppViewModel

@Composable
fun LoansScreen(viewModel: AppViewModel) {
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) { viewModel.loadLoans() }

    val shouldLoadMore by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= info.totalItemsCount - 3 && !viewModel.loans.isLoading
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) viewModel.loadMoreLoans()
    }

    val state = viewModel.loans
    when {
        state.isLoading && state.data == null -> LoadingBox()
        state.error != null && state.data == null -> ErrorBox(state.error)
        state.data != null -> LazyColumn(modifier = Modifier.fillMaxSize(), state = listState) {
            items(state.data, key = { it.id ?: it.hashCode() }) { loan ->
                LoanCard(loan)
            }
            if (viewModel.loansNextPage != null) {
                item { LoadingFooter() }
            }
        }
        else -> LoadingBox()
    }
}

@Composable
fun MyLoansScreen(viewModel: AppViewModel) {
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) { viewModel.loadMyLoans() }

    val shouldLoadMore by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= info.totalItemsCount - 3 && !viewModel.myLoans.isLoading
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) viewModel.loadMoreMyLoans()
    }

    val state = viewModel.myLoans
    when {
        state.isLoading && state.data == null -> LoadingBox()
        state.error != null && state.data == null -> ErrorBox(state.error)
        state.data != null -> {
            if (state.data.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No active loans", style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize(), state = listState) {
                    items(state.data, key = { it.id ?: it.hashCode() }) { loan ->
                        LoanCard(loan)
                    }
                    if (viewModel.myLoansNextPage != null) {
                        item { LoadingFooter() }
                    }
                }
            }
        }
        else -> LoadingBox()
    }
}

@Composable
fun LoanCard(loan: Loan) {
    val isTaken = loan.status.equals("taken", ignoreCase = true)
    val statusColor = if (isTaken) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Loan #${loan.id ?: "—"}",
                    style = MaterialTheme.typography.titleMedium
                )
                Badge(containerColor = statusColor) {
                    Text(loan.status.uppercase(), style = MaterialTheme.typography.labelSmall)
                }
            }

            if (!loan.description.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(loan.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(Modifier.height(6.dp))
            Text(
                "${loan.barcodes.size} barcode(s): ${loan.barcodes.take(3).joinToString(", ")}${if (loan.barcodes.size > 3) "…" else ""}",
                style = MaterialTheme.typography.bodySmall
            )

            if (loan.takenDate != null) {
                Spacer(Modifier.height(4.dp))
                Text("Taken: ${loan.takenDate.take(10)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (loan.returnDate != null) {
                Text("Due: ${loan.returnDate.take(10)}", style = MaterialTheme.typography.labelSmall, color = if (isTaken) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (loan.returnedDate != null) {
                Text("Returned: ${loan.returnedDate.take(10)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
