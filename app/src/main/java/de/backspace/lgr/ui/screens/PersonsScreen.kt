package de.backspace.lgr.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonsScreen(
    viewModel: AppViewModel,
    onOpenDetail: ((List<Person>, Int) -> Unit)? = null,
    onNew: (() -> Unit)? = null
) {
    var search by remember { mutableStateOf(viewModel.personsSearch) }
    val listState = rememberLazyListState()
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

    Box(modifier = Modifier.fillMaxSize().nestedScroll(pullToRefreshState.nestedScrollConnection)) {
        Column(modifier = Modifier.fillMaxSize()) {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it; viewModel.loadPersons(it) },
                label = { Text("Search persons") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (search.isNotBlank()) {
                        IconButton(onClick = { search = ""; viewModel.loadPersons("") }) {
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
                if (onNew != null && !viewModel.readonlyMode) {
                    IconButton(onClick = onNew) {
                        Icon(Icons.Default.PersonAdd, contentDescription = "New person", tint = MaterialTheme.colorScheme.primary)
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                viewModel.personsCount?.let { count ->
                    Text(
                        text = "$count ${if (count == 1) "result" else "results"}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider()

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
