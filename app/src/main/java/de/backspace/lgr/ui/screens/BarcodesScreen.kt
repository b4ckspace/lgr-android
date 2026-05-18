package de.backspace.lgr.ui.screens

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.backspace.lgr.data.model.Barcode
import de.backspace.lgr.data.model.BarcodeStatus
import de.backspace.lgr.data.model.Person
import de.backspace.lgr.viewmodel.AppViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

private fun Person.displayName(): String =
    listOf(firstname, lastname).filter { it.isNotBlank() }.joinToString(" ").ifBlank { nickname }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BarcodesScreen(
    viewModel: AppViewModel,
    onOpenDetail: (List<Barcode>, Int) -> Unit = { _, _ -> },
    onScanSearch: () -> Unit = {}
) {
    var search by remember { mutableStateOf(viewModel.barcodesSearch) }
    var showLoanDialog by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val pullToRefreshState = rememberPullToRefreshState()

    LaunchedEffect(pullToRefreshState.isRefreshing) {
        if (pullToRefreshState.isRefreshing) viewModel.refreshBarcodes()
    }
    LaunchedEffect(viewModel.barcodes.isLoading) {
        if (!viewModel.barcodes.isLoading && pullToRefreshState.isRefreshing) pullToRefreshState.endRefresh()
    }

    LaunchedEffect(Unit) { viewModel.loadBarcodes() }
    var lastBarcodesGeneration by rememberSaveable { mutableStateOf(viewModel.barcodesGeneration) }
    LaunchedEffect(viewModel.barcodesGeneration) {
        if (viewModel.barcodesGeneration != lastBarcodesGeneration) {
            listState.scrollToItem(0)
        }
        lastBarcodesGeneration = viewModel.barcodesGeneration
    }

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

    Box(modifier = Modifier.fillMaxSize().nestedScroll(pullToRefreshState.nestedScrollConnection)) {
        Column(modifier = Modifier.fillMaxSize()) {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it; viewModel.updateBarcodesSearch(it) },
                label = { Text("Search barcodes") },
                placeholder = { Text("supports !user: !item: syntax") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    Row {
                        if (search.isNotBlank()) {
                            IconButton(onClick = { search = ""; viewModel.updateBarcodesSearch("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear search")
                            }
                        }
                        IconButton(onClick = onScanSearch) {
                            Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan barcode", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                singleLine = true,
                colors = lgrTextFieldColors()
            )

            // Always-visible row: expand toggle on left, result count on right
            Row(
                modifier = Modifier.padding(horizontal = 8.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { viewModel.updateFiltersExpanded(!viewModel.filtersExpanded) }) {
                    Icon(
                        if (viewModel.filtersExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (viewModel.filtersExpanded) "Collapse filters" else "Expand filters"
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                viewModel.barcodesCount?.let { count ->
                    Text(
                        text = "$count ${if (count == 1) "result" else "results"}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
            }

            // Collapsible additional filters
            AnimatedVisibility(visible = viewModel.filtersExpanded) {
                Column(modifier = Modifier.padding(horizontal = 8.dp)) {
                    FilterChip(
                        selected = viewModel.barcodesNoParentFilter,
                        onClick = { viewModel.toggleBarcodesNoParentFilter() },
                        label = { Text("No location") },
                        leadingIcon = if (viewModel.barcodesNoParentFilter) {
                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                        } else null
                    )
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(
                        value = viewModel.ownerSearchQuery,
                        onValueChange = { viewModel.updateOwnerSearchQuery(it) },
                        label = { Text("Owner") },
                        trailingIcon = {
                            if (viewModel.ownerSearchQuery.isNotBlank()) {
                                IconButton(onClick = { viewModel.updateOwnerSearchQuery("") }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear owner search")
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = lgrTextFieldColors()
                    )
                    if (viewModel.ownerSuggestions.isNotEmpty()) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                        ) {
                            Column {
                                viewModel.ownerSuggestions.forEach { person ->
                                    val isSelected = viewModel.selectedOwners.any { it.url == person.url }
                                    Row(
                                        modifier = Modifier.fillMaxWidth().clickable {
                                            viewModel.selectOnlyOwnerPerson(person)
                                        }.padding(horizontal = 12.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = isSelected,
                                            onCheckedChange = { viewModel.toggleOwnerPersonSelection(person) }
                                        )
                                        Text(
                                            text = person.displayName(),
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.padding(start = 4.dp)
                                        )
                                    }
                                    HorizontalDivider()
                                }
                            }
                        }
                    }
                    if (viewModel.selectedOwners.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            viewModel.selectedOwners.forEach { person ->
                                InputChip(
                                    selected = true,
                                    onClick = {},
                                    label = { Text(person.displayName()) },
                                    trailingIcon = {
                                        Icon(
                                            Icons.Default.Clear,
                                            contentDescription = "Remove ${person.displayName()}",
                                            modifier = Modifier.size(InputChipDefaults.IconSize)
                                                .clickable { viewModel.removeOwnerPerson(person) }
                                        )
                                    }
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }

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
                    itemsIndexed(state.data, key = { _, barcode -> barcode.code }) { index, barcode ->
                        BarcodeCard(
                            barcode = barcode,
                            isSelected = barcode.code in viewModel.selectedBarcodes,
                            onToggle = { viewModel.toggleBarcodeSelection(barcode.code) },
                            onTap = { onOpenDetail(state.data, index) }
                        )
                    }
                    if (viewModel.barcodesNextPage != null) {
                        item { LoadingFooter() }
                    }
                }
            }
        }

        PullToRefreshContainer(state = pullToRefreshState, modifier = Modifier.align(Alignment.TopCenter))

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
fun BarcodeCard(barcode: Barcode, isSelected: Boolean, onToggle: () -> Unit, onTap: () -> Unit = {}) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clickable(onClick = onTap),
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
                    singleLine = true,
                    colors = lgrTextFieldColors()
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

@Composable
fun ScanBarcodeSearchScreen(
    viewModel: AppViewModel,
    onScanned: (String) -> Unit,
    onBack: () -> Unit
) {
    val detected = remember { AtomicBoolean(false) }
    val handler = remember { Handler(Looper.getMainLooper()) }
    val scope = rememberCoroutineScope()

    BarcodeScannerScaffold(
        onBack = onBack,
        label = "Scan to search",
        onBarcodeDetected = { code ->
            if (detected.compareAndSet(false, true)) {
                scope.launch {
                    val isNew = viewModel.isBarcodeNew(code)
                    if (!isNew) {
                        try {
                            val tg = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
                            tg.startTone(ToneGenerator.TONE_PROP_BEEP, 100)
                            handler.postDelayed({ tg.release() }, 300)
                        } catch (_: Exception) {}
                        onScanned(code)
                    } else {
                        try {
                            val tg = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
                            tg.startTone(ToneGenerator.TONE_PROP_NACK, 300)
                            handler.postDelayed({ tg.release() }, 1000)
                        } catch (_: Exception) {}
                        delay(1000)
                        detected.set(false)
                    }
                }
            }
        }
    )
}
