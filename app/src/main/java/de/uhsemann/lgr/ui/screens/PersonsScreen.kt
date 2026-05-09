package de.uhsemann.lgr.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.uhsemann.lgr.data.model.Person
import de.uhsemann.lgr.viewmodel.AppViewModel

@Composable
fun PersonsScreen(viewModel: AppViewModel) {
    var search by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) { viewModel.loadPersons() }

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

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = search,
            onValueChange = { search = it; viewModel.loadPersons(it) },
            label = { Text("Search persons") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            singleLine = true
        )

        val state = viewModel.persons
        when {
            state.isLoading && state.data == null -> LoadingBox()
            state.error != null && state.data == null -> ErrorBox(state.error)
            state.data != null -> LazyColumn(state = listState) {
                items(state.data, key = { it.url }) { person ->
                    PersonCard(person)
                }
                if (viewModel.personsNextPage != null) {
                    item { LoadingFooter() }
                }
            }
        }
    }
}

@Composable
fun PersonCard(person: Person) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(person.nickname, style = MaterialTheme.typography.titleMedium)
            val fullName = listOf(person.firstname, person.lastname)
                .filter { it.isNotBlank() }
                .joinToString(" ")
            if (fullName.isNotBlank()) {
                Text(fullName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (person.email.isNotBlank()) {
                Text(person.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
