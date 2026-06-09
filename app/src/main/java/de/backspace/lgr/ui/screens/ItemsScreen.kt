package de.backspace.lgr.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.backspace.lgr.data.model.Item
import de.backspace.lgr.viewmodel.AppViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemsScreen(viewModel: AppViewModel, onOpenDetail: ((List<Item>, Int) -> Unit)? = null) {
    var search by remember { mutableStateOf(viewModel.itemsSearch) }
    val listState = rememberLazyListState(viewModel.itemsScrollIndex, viewModel.itemsScrollOffset)
    DisposableEffect(Unit) {
        onDispose {
            viewModel.itemsScrollIndex = listState.firstVisibleItemIndex
            viewModel.itemsScrollOffset = listState.firstVisibleItemScrollOffset
        }
    }
    val pullToRefreshState = rememberPullToRefreshState()

    LaunchedEffect(pullToRefreshState.isRefreshing) {
        if (pullToRefreshState.isRefreshing) viewModel.refreshItems()
    }
    LaunchedEffect(viewModel.items.isLoading) {
        if (!viewModel.items.isLoading && pullToRefreshState.isRefreshing) pullToRefreshState.endRefresh()
    }

    LaunchedEffect(Unit) { viewModel.loadItems() }
    var lastItemsGeneration by rememberSaveable { mutableStateOf(viewModel.itemsGeneration) }
    LaunchedEffect(viewModel.itemsGeneration) {
        if (viewModel.itemsGeneration != lastItemsGeneration) {
            listState.scrollToItem(0)
        }
        lastItemsGeneration = viewModel.itemsGeneration
    }

    // Load next page when near the end
    val shouldLoadMore by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= info.totalItemsCount - 3 && !viewModel.items.isLoading
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) viewModel.loadMoreItems()
    }

    Box(modifier = Modifier.fillMaxSize().nestedScroll(pullToRefreshState.nestedScrollConnection)) {
    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = search,
            onValueChange = { search = it; viewModel.loadItems(it) },
            label = { Text("Search items") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (search.isNotBlank()) {
                    IconButton(onClick = { search = ""; viewModel.loadItems("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear search")
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            singleLine = true,
            colors = lgrTextFieldColors()
        )

        Row(
            modifier = Modifier.padding(horizontal = 8.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = viewModel.itemsNoBarcodeFilter,
                onClick = { viewModel.toggleItemsNoBarcodeFilter() },
                label = { Text("No barcodes") },
                leadingIcon = if (viewModel.itemsNoBarcodeFilter) {
                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                } else null
            )
            Spacer(modifier = Modifier.weight(1f))
            viewModel.itemsCount?.let { count ->
                Text(
                    text = "$count ${if (count == 1) "result" else "results"}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
        }

        HorizontalDivider()

        val state = viewModel.items
        when {
            state.isLoading && state.data == null -> LoadingBox()
            state.error != null && state.data == null -> ErrorBox(state.error)
            state.data != null -> {
                LazyColumn(modifier = Modifier.fillMaxSize().verticalScrollbar(listState), state = listState) {
                    itemsIndexed(state.data, key = { _, item -> item.url }) { index, item ->
                        ItemCard(item, onClick = onOpenDetail?.let { cb -> { cb(state.data, index) } })
                    }
                    if (viewModel.itemsNextPage != null) {
                        item { LoadingFooter() }
                    }
                }
            }
        }
    }
    PullToRefreshContainer(state = pullToRefreshState, modifier = Modifier.align(Alignment.TopCenter))
    }
}

@Composable
fun ItemCard(item: Item, onClick: (() -> Unit)? = null) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text(item.name, style = MaterialTheme.typography.titleMedium)
            if (item.description.isNotBlank()) {
                Text(
                    item.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (item.tags.isNotEmpty()) {
                Text(
                    "${item.tags.size} tag(s)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun lgrTextFieldColors() = OutlinedTextFieldDefaults.colors(
    unfocusedLabelColor = Color(0xFF9E9E9E)
)

@Composable
fun LoadingBox() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
fun ErrorBox(message: String) {
    Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
        Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun LoadingFooter() {
    Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(modifier = Modifier.size(24.dp))
    }
}
