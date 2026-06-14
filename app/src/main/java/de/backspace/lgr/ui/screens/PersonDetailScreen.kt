// SPDX-FileCopyrightText: 2026 Andreas Uhsemann
// SPDX-License-Identifier: Apache-2.0

package de.backspace.lgr.ui.screens

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import de.backspace.lgr.viewmodel.AppViewModel

private val PERSON_RED = Color(0xFFE53935)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonDetailScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onEditPerson: () -> Unit = {}
) {
    val person = viewModel.currentPerson ?: run { onBack(); return }
    val displayName = listOf(person.firstname, person.lastname)
        .filter { it.isNotBlank() }
        .joinToString(" ")
        .ifBlank { person.nickname }
    val deleteState = viewModel.deletePersonState
    val personList = viewModel.personListContext
    val currentIndex = viewModel.personListIndex
    val listState = rememberLazyListState()
    var showDeleteDialog by remember { mutableStateOf(false) }
    val swipeThresholdPx = with(LocalDensity.current) { 80.dp.toPx() }
    var dragTotal by remember(currentIndex) { mutableStateOf(0f) }

    val pullToRefreshState = rememberPullToRefreshState()
    LaunchedEffect(pullToRefreshState.isRefreshing) {
        if (pullToRefreshState.isRefreshing) {
            viewModel.refreshPersonDetail().join()
            pullToRefreshState.endRefresh()
        }
    }

    LaunchedEffect(deleteState.data) {
        if (deleteState.data != null) {
            viewModel.resetDeletePersonState()
            onBack()
        }
    }
    LaunchedEffect(deleteState.error) {
        if (deleteState.error != null) showDeleteDialog = true
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false; viewModel.resetDeletePersonState() },
            title = { Text("Delete Person") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Are you sure you want to permanently delete person:")
                    Text(displayName, style = MaterialTheme.typography.titleMedium)
                    deleteState.error?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showDeleteDialog = false; viewModel.deletePerson() },
                    colors = ButtonDefaults.buttonColors(containerColor = PERSON_RED)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false; viewModel.resetDeletePersonState() }) {
                    Text("Cancel")
                }
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize().nestedScroll(pullToRefreshState.nestedScrollConnection)) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
            Text("Person", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.weight(1f))
            if (!viewModel.readonlyMode) {
                Row(horizontalArrangement = Arrangement.spacedBy((-8).dp)) {
                    IconButton(onClick = onEditPerson) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit Person")
                    }
                    IconButton(
                        onClick = { showDeleteDialog = true },
                        enabled = !deleteState.isLoading
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete Person",
                            tint = if (deleteState.isLoading)
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            else
                                MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(currentIndex) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            when {
                                personList != null && dragTotal < -swipeThresholdPx && currentIndex < personList.size - 1 ->
                                    viewModel.navigateToPersonInList(currentIndex + 1)
                                personList != null && dragTotal > swipeThresholdPx && currentIndex > 0 ->
                                    viewModel.navigateToPersonInList(currentIndex - 1)
                            }
                            dragTotal = 0f
                        },
                        onDragCancel = { dragTotal = 0f }
                    ) { _, dragAmount -> dragTotal += dragAmount }
                }
        ) {
            if (deleteState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                return@Box
            }

            Column(modifier = Modifier.fillMaxSize()) {
                HorizontalDivider()
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxWidth().weight(1f).verticalScrollbar(listState),
                    contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        val fields = buildList {
                            add("Nickname" to person.nickname)
                            if (person.firstname.isNotBlank()) add("First name" to person.firstname)
                            if (person.lastname.isNotBlank()) add("Last name" to person.lastname)
                            if (person.email.isNotBlank()) add("Email" to person.email)
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            fields.forEachIndexed { index, (label, value) ->
                                // No divider after the last field, so the page doesn't end with a line.
                                DetailRow(label, value, divider = index < fields.lastIndex)
                            }
                        }
                    }
                }

                if (personList != null) {
                    HorizontalDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { viewModel.navigateToPersonInList(currentIndex - 1) },
                            enabled = currentIndex > 0
                        ) {
                            Icon(Icons.Default.KeyboardArrowLeft, "Previous person")
                        }
                        Text(
                            "${currentIndex + 1} / ${personList.size}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        IconButton(
                            onClick = { viewModel.navigateToPersonInList(currentIndex + 1) },
                            enabled = currentIndex < personList.size - 1
                        ) {
                            Icon(Icons.Default.KeyboardArrowRight, "Next person")
                        }
                    }
                }
            }
        }
    }
    PullToRefreshContainer(state = pullToRefreshState, modifier = Modifier.align(Alignment.TopCenter))
    }
}

