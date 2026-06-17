// SPDX-FileCopyrightText: 2026 Andreas Uhsemann
// SPDX-License-Identifier: Apache-2.0

package de.backspace.lgr.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.backspace.lgr.data.model.Person
import de.backspace.lgr.viewmodel.AppViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PersonsScreen(
    viewModel: AppViewModel,
    onOpenDetail: ((List<Person>, Int) -> Unit)? = null,
    onNew: (() -> Unit)? = null
) {
    var search by remember { mutableStateOf(viewModel.personsSearch) }
    val listState = rememberLazyListState(viewModel.personsScrollIndex, viewModel.personsScrollOffset)
    DisposableEffect(Unit) {
        onDispose {
            viewModel.personsScrollIndex = listState.firstVisibleItemIndex
            viewModel.personsScrollOffset = listState.firstVisibleItemScrollOffset
        }
    }
    val pullToRefreshState = rememberPullToRefreshState()

    LaunchedEffect(pullToRefreshState.isRefreshing) {
        if (pullToRefreshState.isRefreshing) viewModel.refreshPersons()
    }
    LaunchedEffect(viewModel.persons.isLoading) {
        if (!viewModel.persons.isLoading && pullToRefreshState.isRefreshing) pullToRefreshState.endRefresh()
    }

    LaunchedEffect(Unit) { viewModel.loadPersons() }
    var lastGeneration by rememberSaveable { mutableStateOf(viewModel.personsGeneration) }
    LaunchedEffect(viewModel.personsGeneration) {
        if (viewModel.personsGeneration != lastGeneration) listState.scrollToItem(0)
        lastGeneration = viewModel.personsGeneration
    }

    val shouldLoadMore by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= info.totalItemsCount - 3 && !viewModel.persons.isLoading
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) viewModel.loadMorePersons()
    }

    // Edge-to-edge means the window does not resize for the keyboard (manifest adjustResize is a
    // no-op), so shrink the content above the IME ourselves; otherwise the list keeps full height
    // behind the keyboard and cannot be scrolled.
    Box(modifier = Modifier.fillMaxSize().imePadding().nestedScroll(pullToRefreshState.nestedScrollConnection)) {
        Column(modifier = Modifier.fillMaxSize()) {
            AnimatedVisibility(visible = !viewModel.fullscreen) {
            SearchHeader(
                query = search,
                onQueryChange = { search = it; viewModel.loadPersons(it) },
                placeholder = "Search persons",
                resultCount = viewModel.personsCount
            )
            }

            val state = viewModel.persons
            when {
                state.isLoading && state.data == null -> LoadingBox()
                state.error != null && state.data == null -> ErrorBox(state.error)
                state.data != null -> LazyColumn(modifier = Modifier.fillMaxSize().verticalScrollbar(listState), state = listState) {
                    itemsIndexed(state.data, key = { _, person -> person.url }) { index, person ->
                        PersonCard(person, onClick = onOpenDetail?.let { cb -> { cb(state.data, index) } })
                    }
                    if (viewModel.personsNextPage != null) {
                        item { LoadingFooter() }
                    }
                }
            }
        }

        PullToRefreshContainer(state = pullToRefreshState, modifier = Modifier.align(Alignment.TopCenter))

        if (onNew != null && !viewModel.readonlyMode && !viewModel.fullscreen) {
            FloatingActionButton(
                onClick = onNew,
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
            ) {
                Icon(Icons.Default.PersonAdd, contentDescription = "Add new person")
            }
        }
    }
}

@Composable
fun PersonCard(person: Person, onClick: (() -> Unit)? = null) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            val fullName = listOf(person.firstname, person.lastname)
                .filter { it.isNotBlank() }
                .joinToString(" ")
            Text(
                if (fullName.isNotBlank()) fullName else person.nickname,
                style = MaterialTheme.typography.titleMedium
            )
            if (fullName.isNotBlank() && person.nickname.isNotBlank()) {
                Text(
                    person.nickname,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (person.email.isNotBlank()) {
                Text(
                    person.email,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
