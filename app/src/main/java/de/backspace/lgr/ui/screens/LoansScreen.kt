package de.backspace.lgr.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import de.backspace.lgr.data.model.Loan
import de.backspace.lgr.viewmodel.AppViewModel

private val STATUS_OPTIONS = listOf("taken" to "Taken", "returned" to "Returned", "" to "All")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoansScreen(viewModel: AppViewModel, onOpenDetail: (List<Loan>, Int) -> Unit = { _, _ -> }) {
    val listState = rememberLazyListState()
    val pullToRefreshState = rememberPullToRefreshState()

    LaunchedEffect(pullToRefreshState.isRefreshing) {
        if (pullToRefreshState.isRefreshing) viewModel.loadLoans()
    }
    LaunchedEffect(viewModel.loans.isLoading) {
        if (!viewModel.loans.isLoading && pullToRefreshState.isRefreshing) pullToRefreshState.endRefresh()
    }

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

    Box(modifier = Modifier.fillMaxSize().nestedScroll(pullToRefreshState.nestedScrollConnection)) {
        Column(modifier = Modifier.fillMaxSize()) {
            LoanStatusFilterRow(
                selected = viewModel.loansStatusFilter,
                onSelect = { viewModel.loadLoans(it) }
            )
            LoanCountRow(viewModel.loansCount)
            HorizontalDivider()
            val state = viewModel.loans
            when {
                state.isLoading && state.data == null -> LoadingBox()
                state.error != null && state.data == null -> ErrorBox(state.error)
                state.data != null -> LazyColumn(modifier = Modifier.fillMaxSize().verticalScrollbar(listState), state = listState) {
                    itemsIndexed(state.data, key = { _, loan -> loan.id ?: loan.hashCode() }) { index, loan ->
                        LoanCard(loan, onClick = { onOpenDetail(state.data, index) })
                    }
                    if (viewModel.loansNextPage != null) {
                        item { LoadingFooter() }
                    }
                }
                else -> LoadingBox()
            }
        }
        PullToRefreshContainer(state = pullToRefreshState, modifier = Modifier.align(Alignment.TopCenter))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyLoansScreen(viewModel: AppViewModel, onOpenDetail: (List<Loan>, Int) -> Unit = { _, _ -> }) {
    val listState = rememberLazyListState()
    val pullToRefreshState = rememberPullToRefreshState()

    LaunchedEffect(pullToRefreshState.isRefreshing) {
        if (pullToRefreshState.isRefreshing) viewModel.loadMyLoans()
    }
    LaunchedEffect(viewModel.myLoans.isLoading) {
        if (!viewModel.myLoans.isLoading && pullToRefreshState.isRefreshing) pullToRefreshState.endRefresh()
    }

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

    Box(modifier = Modifier.fillMaxSize().nestedScroll(pullToRefreshState.nestedScrollConnection)) {
        Column(modifier = Modifier.fillMaxSize()) {
            LoanStatusFilterRow(
                selected = viewModel.myLoansStatusFilter,
                onSelect = { viewModel.loadMyLoans(it) }
            )
            LoanCountRow(viewModel.myLoansCount)
            HorizontalDivider()
            val state = viewModel.myLoans
            when {
                state.isLoading && state.data == null -> LoadingBox()
                state.error != null && state.data == null -> ErrorBox(state.error)
                state.data != null -> {
                    if (state.data.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No loans", style = MaterialTheme.typography.bodyLarge)
                        }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize().verticalScrollbar(listState), state = listState) {
                            itemsIndexed(state.data, key = { _, loan -> loan.id ?: loan.hashCode() }) { index, loan ->
                                LoanCard(loan, onClick = { onOpenDetail(state.data, index) })
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
        PullToRefreshContainer(state = pullToRefreshState, modifier = Modifier.align(Alignment.TopCenter))
    }
}

@Composable
private fun LoanCountRow(count: Int?) {
    Row(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp).fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(modifier = Modifier.weight(1f))
        count?.let {
            Text(
                text = "$it ${if (it == 1) "result" else "results"}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun LoanStatusFilterRow(selected: String, onSelect: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        STATUS_OPTIONS.forEach { (value, label) ->
            FilterChip(
                selected = selected == value,
                onClick = { if (selected != value) onSelect(value) },
                label = { Text(label) }
            )
        }
    }
}

@Composable
fun LoanCard(loan: Loan, onClick: (() -> Unit)? = null) {
    val isTaken = loan.status.equals("taken", ignoreCase = true)
    val statusColor = if (isTaken) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary

    Card(
        onClick = onClick ?: {},
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
                Text(loan.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(Modifier.height(6.dp))
            Text(
                "${loan.barcodes.size} barcode(s): ${loan.barcodes.take(3).joinToString(", ")}${if (loan.barcodes.size > 3) "…" else ""}",
                style = MaterialTheme.typography.bodyMedium
            )

            if (loan.takenDate != null) {
                Spacer(Modifier.height(4.dp))
                Text("Taken: ${loan.takenDate.take(10)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (loan.returnDate != null) {
                val dateStr = loan.returnDate.take(10)
                val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
                val isOverdue = isTaken && dateStr < today
                Text("Due: $dateStr", style = MaterialTheme.typography.bodySmall, color = if (isOverdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (loan.returnedDate != null) {
                Text("Returned: ${loan.returnedDate.take(10)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
