package de.backspace.lgr.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.backspace.lgr.viewmodel.AppViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditItemScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onSaved: () -> Unit
) {
    val saveState = viewModel.saveItemEditState

    LaunchedEffect(saveState.data) {
        if (saveState.data != null) onSaved()
    }

    val canSave = viewModel.editItemNameQuery.isNotBlank() && !saveState.isLoading

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Item") },
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
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (saveState.error != null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Text(
                            text = saveState.error.replaceFirstChar { it.uppercaseChar() },
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.fillMaxWidth().padding(12.dp)
                        )
                    }
                }

                OutlinedTextField(
                    value = viewModel.editItemNameQuery,
                    onValueChange = { viewModel.editItemNameQuery = it },
                    label = { Text("Name *") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = lgrTextFieldColors()
                )

                OutlinedTextField(
                    value = viewModel.editItemDescription,
                    onValueChange = { viewModel.editItemDescription = it },
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4,
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
                    onClick = { viewModel.saveItemEdit() },
                    enabled = canSave
                ) {
                    if (saveState.isLoading) CircularProgressIndicator(
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
