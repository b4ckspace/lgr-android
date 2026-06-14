// SPDX-FileCopyrightText: 2026 Andreas Uhsemann
// SPDX-License-Identifier: Apache-2.0

package de.backspace.lgr.ui.screens

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.QrCodeScanner
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
    onScanSearch: () -> Unit = {},
    onNewBarcode: () -> Unit = {}
) {
    var search by remember { mutableStateOf(viewModel.barcodesSearch) }
    val listState = rememberLazyListState(viewModel.barcodesScrollIndex, viewModel.barcodesScrollOffset)
    DisposableEffect(Unit) {
        onDispose {
            viewModel.barcodesScrollIndex = listState.firstVisibleItemIndex
            viewModel.barcodesScrollOffset = listState.firstVisibleItemScrollOffset
        }
    }
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

    // Edge-to-edge means the window does not resize for the keyboard (manifest adjustResize is a
    // no-op), so shrink the content above the IME ourselves; otherwise the list keeps full height
    // behind the keyboard and cannot be scrolled.
    Box(modifier = Modifier.fillMaxSize().imePadding().nestedScroll(pullToRefreshState.nestedScrollConnection)) {
        Column(modifier = Modifier.fillMaxSize()) {
            SearchHeader(
                query = search,
                onQueryChange = { search = it; viewModel.updateBarcodesSearch(it) },
                placeholder = "Search barcodes",
                supportingText = "Supports !user: and !item: syntax",
                searchTrailing = {
                    IconButton(onClick = { viewModel.clearBarcodeFilters(); onScanSearch() }) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan barcode", tint = MaterialTheme.colorScheme.primary)
                    }
                },
                resultCount = viewModel.barcodesCount,
                hasFilters = true,
                filtersExpanded = viewModel.filtersExpanded,
                onToggleFilters = { viewModel.updateFiltersExpanded(!viewModel.filtersExpanded) },
                activeFilterCount = (if (viewModel.barcodesNoParentFilter) 1 else 0) + viewModel.selectedOwners.size,
                activeFilterChips = {
                    if (viewModel.barcodesNoParentFilter) {
                        InputChip(
                            selected = true,
                            onClick = { viewModel.toggleBarcodesNoParentFilter() },
                            label = { Text("No location") },
                            trailingIcon = {
                                Icon(Icons.Default.Clear, contentDescription = "Remove no-location filter",
                                    modifier = Modifier.size(InputChipDefaults.IconSize))
                            }
                        )
                    }
                    viewModel.selectedOwners.forEach { person ->
                        InputChip(
                            selected = true,
                            onClick = { viewModel.removeOwnerPerson(person) },
                            label = { Text(person.displayName()) },
                            trailingIcon = {
                                Icon(Icons.Default.Clear, contentDescription = "Remove ${person.displayName()}",
                                    modifier = Modifier.size(InputChipDefaults.IconSize))
                            }
                        )
                    }
                },
                filterPanel = {
                    FilterChip(
                        selected = viewModel.barcodesNoParentFilter,
                        onClick = { viewModel.toggleBarcodesNoParentFilter() },
                        label = { Text("No location") },
                        leadingIcon = if (viewModel.barcodesNoParentFilter) {
                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                        } else null
                    )
                    Spacer(Modifier.height(8.dp))
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
                }
            )

            val state = viewModel.barcodes
            Box(modifier = Modifier.weight(1f)) {
                when {
                    state.isLoading && state.data == null -> LoadingBox()
                    state.error != null && state.data == null -> ErrorBox(state.error)
                    state.data != null -> LazyColumn(modifier = Modifier.fillMaxSize().verticalScrollbar(listState), state = listState) {
                        itemsIndexed(state.data, key = { _, barcode -> barcode.code }) { index, barcode ->
                            BarcodeCard(
                                barcode = barcode,
                                isSelected = barcode.code in viewModel.selectedBarcodes,
                                onToggle = { viewModel.toggleBarcodeSelection(barcode.code, barcode) },
                                onTap = { onOpenDetail(state.data, index) }
                            )
                        }
                        if (viewModel.barcodesNextPage != null) {
                            item { LoadingFooter() }
                        }
                    }
                }
            }

        }

        PullToRefreshContainer(state = pullToRefreshState, modifier = Modifier.align(Alignment.TopCenter))

        if (!viewModel.readonlyMode) {
            FloatingActionButton(
                onClick = onNewBarcode,
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
            ) {
                Icon(Icons.Default.NoteAdd, contentDescription = "Add new barcode")
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
            val isOnLoan = barcode.apiLoanInfo?.loan == true
            Checkbox(checked = isSelected, onCheckedChange = { onToggle() }, enabled = !isOnLoan || isSelected)
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
