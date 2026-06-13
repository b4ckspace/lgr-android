// SPDX-FileCopyrightText: 2026 Andreas Uhsemann
// SPDX-License-Identifier: Apache-2.0

package de.backspace.lgr.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.backspace.lgr.viewmodel.AppViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewPersonScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onCreated: () -> Unit
) {
    val createState = viewModel.newPersonState
    val scrollState = rememberScrollState()

    LaunchedEffect(createState.data) {
        if (createState.data != null) onCreated()
    }
    LaunchedEffect(createState.error) {
        if (createState.error != null) scrollState.animateScrollTo(0)
    }

    val canSave = viewModel.newPersonNickname.isNotBlank() && !createState.isLoading

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New Person") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                windowInsets = WindowInsets(0)
            )
        },
        contentWindowInsets = WindowInsets(0)
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).imePadding()
        ) {
            HorizontalDivider()
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScrollbar(scrollState)
                    .verticalScroll(scrollState)
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (createState.error != null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Text(
                            text = createState.error.replaceFirstChar { it.uppercaseChar() },
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.fillMaxWidth().padding(12.dp)
                        )
                    }
                }

                OutlinedTextField(
                    value = viewModel.newPersonNickname,
                    onValueChange = { viewModel.newPersonNickname = it },
                    label = { Text("Nickname *") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = lgrTextFieldColors()
                )
                OutlinedTextField(
                    value = viewModel.newPersonFirstname,
                    onValueChange = { viewModel.newPersonFirstname = it },
                    label = { Text("First name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = lgrTextFieldColors()
                )
                OutlinedTextField(
                    value = viewModel.newPersonLastname,
                    onValueChange = { viewModel.newPersonLastname = it },
                    label = { Text("Last name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = lgrTextFieldColors()
                )
                OutlinedTextField(
                    value = viewModel.newPersonEmail,
                    onValueChange = { viewModel.newPersonEmail = it },
                    label = { Text("Email") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    colors = lgrTextFieldColors()
                )
            }
            HorizontalDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
            ) {
                OutlinedButton(onClick = onBack) { Text("Cancel") }
                Button(
                    onClick = { viewModel.createNewPerson() },
                    enabled = canSave
                ) {
                    if (createState.isLoading) CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    else Text("Save")
                }
            }
        }
    }
}
