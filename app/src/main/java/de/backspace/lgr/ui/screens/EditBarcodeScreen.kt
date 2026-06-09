package de.backspace.lgr.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import de.backspace.lgr.data.model.Barcode
import de.backspace.lgr.data.model.Item
import de.backspace.lgr.data.model.Person
import de.backspace.lgr.viewmodel.AppViewModel
import kotlinx.coroutines.delay

private val GREY = Color(0xFF9E9E9E)

private fun Person.displayName(): String =
    listOf(firstname, lastname).filter { it.isNotBlank() }.joinToString(" ").ifBlank { nickname }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditBarcodeScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    onScanParent: () -> Unit = {},
    onScanNewCode: () -> Unit = {}
) {
    val barcode = viewModel.scannedBarcode.data
    var itemSuggestions by remember { mutableStateOf<List<Pair<Item, Int>>>(emptyList()) }
    var showSuggestions by remember { mutableStateOf(false) }
    var ownerSuggestions by remember { mutableStateOf<List<Person>>(emptyList()) }
    var showOwnerSuggestions by remember { mutableStateOf(false) }
    var locationSelected by remember { mutableStateOf(viewModel.editBarcodeLocationQuery.isNotBlank()) }
    var lastTypedLocation by remember { mutableStateOf(viewModel.editBarcodeLocationQuery) }
    var locationSuggestions by remember { mutableStateOf<List<Barcode>>(emptyList()) }
    var showLocationSuggestions by remember { mutableStateOf(false) }
    val itemFocusRequester = remember { FocusRequester() }
    val ownerFocusRequester = remember { FocusRequester() }
    val scrollState = rememberScrollState()
    val density = LocalDensity.current

    // Scroll the focused field into view when the keyboard appears.
    // focusedBounds is updated continuously via onGloballyPositioned while a field is focused.
    // viewportBottom tracks the actual bottom of the content area (excluding the bottom nav bar).
    var focusedBounds by remember { mutableStateOf(Rect.Zero) }
    var viewportTop by remember { mutableStateOf(0f) }
    var viewportBottom by remember { mutableStateOf(0f) }
    val imeBottom = WindowInsets.ime.getBottom(density)
    LaunchedEffect(imeBottom) {
        if (imeBottom > 0 && focusedBounds != Rect.Zero && viewportBottom > 0f) {
            delay(50)
            val overflow = focusedBounds.bottom - viewportBottom + 24f
            if (overflow > 0) scrollState.animateScrollTo(scrollState.value + overflow.toInt())
        }
    }
    var itemNameTfv by remember { mutableStateOf(TextFieldValue(viewModel.editBarcodeNameQuery)) }
    var ownerQueryTfv by remember { mutableStateOf(TextFieldValue(viewModel.editBarcodeOwnerQuery)) }

    val saveState = viewModel.saveBarcodeEditState
    val renameState = viewModel.renameBarcodeState
    val codeChanged = viewModel.editBarcodeCodeChanged
    // While renaming, every other field is locked so the rename is the only pending change.
    val otherFieldsEnabled = !viewModel.editBarcodeRenameMode
    var showRenameConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(saveState.data) {
        if (saveState.data != null) onSaved()
    }
    LaunchedEffect(renameState.data) {
        if (renameState.data != null) onSaved()
    }

    LaunchedEffect(viewModel.editBarcodeNameQuery) {
        if (itemNameTfv.text != viewModel.editBarcodeNameQuery) {
            val t = viewModel.editBarcodeNameQuery
            itemNameTfv = TextFieldValue(t, TextRange(t.length))
        }
    }

    LaunchedEffect(viewModel.editBarcodeOwnerQuery) {
        if (ownerQueryTfv.text != viewModel.editBarcodeOwnerQuery) {
            val t = viewModel.editBarcodeOwnerQuery
            ownerQueryTfv = TextFieldValue(t, TextRange(t.length))
        }
    }

    LaunchedEffect(viewModel.editBarcodeNameQuery) {
        val query = viewModel.editBarcodeNameQuery
        if (query.length < 2 || viewModel.editBarcodeSelectedItem?.name == query) {
            if (viewModel.editBarcodeSelectedItem?.name != query) {
                itemSuggestions = emptyList()
                showSuggestions = false
            }
            return@LaunchedEffect
        }
        delay(300)
        val results = viewModel.searchItemsWithCounts(query).sortedBy { (item, _) -> item.name.lowercase() }
        itemSuggestions = results
        showSuggestions = results.isNotEmpty()
    }

    LaunchedEffect(viewModel.editBarcodeOwnerQuery) {
        val query = viewModel.editBarcodeOwnerQuery
        if (query.length < 2 || viewModel.editBarcodeSelectedPerson != null || viewModel.editBarcodeOwnerUrl != null) {
            ownerSuggestions = emptyList()
            showOwnerSuggestions = false
            return@LaunchedEffect
        }
        delay(300)
        val results = viewModel.searchPersons(query).sortedBy { it.displayName().lowercase() }
        ownerSuggestions = results
        showOwnerSuggestions = results.isNotEmpty()
    }

    LaunchedEffect(viewModel.editBarcodeLocationQuery) {
        if (viewModel.editBarcodeLocationQuery != lastTypedLocation) {
            locationSelected = viewModel.editBarcodeLocationQuery.isNotBlank()
            lastTypedLocation = viewModel.editBarcodeLocationQuery
        }
    }

    LaunchedEffect(viewModel.editBarcodeLocationQuery, locationSelected) {
        val query = viewModel.editBarcodeLocationQuery
        if (query.length < 2 || locationSelected) {
            locationSuggestions = emptyList()
            showLocationSuggestions = false
            return@LaunchedEffect
        }
        delay(300)
        val results = viewModel.searchBarcodes(query).sortedBy { it.itemName.lowercase() }
        locationSuggestions = results
        showLocationSuggestions = results.isNotEmpty()
    }

    val isBusy = saveState.isLoading || renameState.isLoading
    val canSave = if (viewModel.editBarcodeRenameMode) codeChanged && !isBusy
        else viewModel.editBarcodeNameQuery.isNotBlank() && !isBusy

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Barcode") },
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
        fun fieldScrollTarget() =
            (focusedBounds.top - viewportTop + scrollState.value).toInt().coerceAtLeast(0)

        LaunchedEffect(showLocationSuggestions) {
            if (showLocationSuggestions && focusedBounds != Rect.Zero)
                scrollState.animateScrollTo(fieldScrollTarget())
        }
        LaunchedEffect(showSuggestions) {
            if (showSuggestions && focusedBounds != Rect.Zero)
                scrollState.animateScrollTo(fieldScrollTarget())
        }
        LaunchedEffect(showOwnerSuggestions) {
            if (showOwnerSuggestions && focusedBounds != Rect.Zero)
                scrollState.animateScrollTo(fieldScrollTarget())
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(padding).imePadding()
        ) {
        HorizontalDivider()
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScrollbar(scrollState)
                .onGloballyPositioned {
                    val bounds = it.boundsInRoot()
                    viewportTop = bounds.top
                    viewportBottom = bounds.bottom
                }
        ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val errorText = saveState.error ?: renameState.error
            if (errorText != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Text(
                        text = errorText.replaceFirstChar { it.uppercaseChar() },
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.fillMaxWidth().padding(12.dp)
                    )
                }
            }

            Column {
                var locationFocused by remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = viewModel.editBarcodeLocationQuery,
                        onValueChange = { query ->
                            viewModel.editBarcodeLocationQuery = query
                            lastTypedLocation = query
                            locationSelected = false
                        },
                        label = { Text("Location") },
                        enabled = otherFieldsEnabled,
                        modifier = Modifier.weight(1f)
                            .onFocusChanged { locationFocused = it.isFocused; if (!it.isFocused) focusedBounds = Rect.Zero }
                            .onGloballyPositioned { if (locationFocused) focusedBounds = it.boundsInRoot() },
                        singleLine = true,
                        colors = lgrTextFieldColors(),
                        trailingIcon = {
                            if (otherFieldsEnabled && viewModel.editBarcodeLocationQuery.isNotEmpty()) {
                                IconButton(onClick = {
                                    viewModel.editBarcodeLocationQuery = ""
                                    lastTypedLocation = ""
                                    locationSelected = false
                                    locationSuggestions = emptyList()
                                    showLocationSuggestions = false
                                }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                                }
                            }
                        }
                    )
                    IconButton(onClick = onScanParent, enabled = otherFieldsEnabled) {
                        Icon(
                            Icons.Default.QrCodeScanner,
                            contentDescription = "Scan location",
                            tint = if (otherFieldsEnabled) MaterialTheme.colorScheme.primary else GREY
                        )
                    }
                }
                if (showLocationSuggestions) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Column {
                            locationSuggestions.forEach { suggestion ->
                                val isSelf = suggestion.code == barcode?.code
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .then(if (!isSelf) Modifier.clickable {
                                            viewModel.editBarcodeLocationQuery = suggestion.code
                                            locationSelected = true
                                            showLocationSuggestions = false
                                        } else Modifier)
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = suggestion.itemName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (isSelf) GREY else Color.Unspecified,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = "(${suggestion.code})",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = GREY
                                    )
                                }
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }

            barcode?.let {
                if (viewModel.editBarcodeRenameMode) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = viewModel.editBarcodeNewCode,
                            onValueChange = { viewModel.editBarcodeNewCode = it },
                            label = { Text("New barcode") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            isError = renameState.error != null,
                            colors = lgrTextFieldColors()
                        )
                        IconButton(onClick = onScanNewCode, enabled = !isBusy) {
                            Icon(
                                Icons.Default.QrCodeScanner,
                                contentDescription = "Scan new barcode",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(onClick = { viewModel.cancelBarcodeRename() }, enabled = !isBusy) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Undo barcode change",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = it.code,
                            onValueChange = {},
                            label = { Text("Barcode") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            enabled = false,
                            colors = lgrTextFieldColors()
                        )
                        // Renaming recreates the entry, which would drop it from an active
                        // loan, so it is not offered while the barcode is on loan.
                        val onLoan = it.apiLoanInfo?.loan == true
                        val canRename = !viewModel.editBarcodeOtherFieldsDirty && !onLoan
                        IconButton(
                            onClick = { viewModel.startBarcodeRename() },
                            enabled = canRename
                        ) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = "Change barcode",
                                tint = if (canRename) MaterialTheme.colorScheme.primary else GREY
                            )
                        }
                    }
                }
            }

            Column {
                var itemFocused by remember { mutableStateOf(false) }
                OutlinedTextField(
                    value = itemNameTfv,
                    onValueChange = { tfv ->
                        if (tfv.text != itemNameTfv.text) {
                            if (viewModel.editBarcodeSelectedItem != null) {
                                viewModel.editBarcodeItemDescription = ""
                            }
                            viewModel.editBarcodeNameQuery = tfv.text
                            viewModel.editBarcodeSelectedItem = null
                        }
                        itemNameTfv = tfv
                    },
                    label = { Text("Item *") },
                    enabled = otherFieldsEnabled,
                    modifier = Modifier.fillMaxWidth().focusRequester(itemFocusRequester)
                        .onFocusChanged { itemFocused = it.isFocused; if (!it.isFocused) focusedBounds = Rect.Zero }
                        .onGloballyPositioned { if (itemFocused) focusedBounds = it.boundsInRoot() },
                    singleLine = true,
                    colors = lgrTextFieldColors(),
                    trailingIcon = {
                        if (otherFieldsEnabled && itemNameTfv.text.isNotEmpty()) {
                            IconButton(onClick = {
                                if (viewModel.editBarcodeSelectedItem != null) {
                                    viewModel.editBarcodeItemDescription = ""
                                }
                                itemNameTfv = TextFieldValue("")
                                viewModel.editBarcodeNameQuery = ""
                                viewModel.editBarcodeSelectedItem = null
                                itemSuggestions = emptyList()
                                showSuggestions = false
                                itemFocusRequester.requestFocus()
                            }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    }
                )
                if (showSuggestions) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Column {
                            itemSuggestions.forEach { (item, count) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            itemNameTfv = TextFieldValue(item.name, TextRange(item.name.length))
                                            viewModel.editBarcodeNameQuery = item.name
                                            viewModel.editBarcodeSelectedItem = item
                                            viewModel.editBarcodeItemDescription = item.description
                                            showSuggestions = false
                                        }
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = item.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = "($count)",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = GREY
                                    )
                                }
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }

            var descFocused by remember { mutableStateOf(false) }
            OutlinedTextField(
                value = viewModel.editBarcodeDescription,
                onValueChange = { viewModel.editBarcodeDescription = it },
                label = { Text("Barcode description") },
                enabled = otherFieldsEnabled,
                modifier = Modifier.fillMaxWidth()
                    .onFocusChanged { descFocused = it.isFocused; if (!it.isFocused) focusedBounds = Rect.Zero }
                    .onGloballyPositioned { if (descFocused) focusedBounds = it.boundsInRoot() },
                minLines = 2,
                maxLines = 4,
                colors = lgrTextFieldColors()
            )

            val itemSelected = viewModel.editBarcodeSelectedItem != null
            var itemDescFocused by remember { mutableStateOf(false) }
            OutlinedTextField(
                value = viewModel.editBarcodeItemDescription,
                onValueChange = { viewModel.editBarcodeItemDescription = it },
                label = { Text("Item description") },
                modifier = Modifier.fillMaxWidth()
                    .onFocusChanged { itemDescFocused = it.isFocused; if (!it.isFocused) focusedBounds = Rect.Zero }
                    .onGloballyPositioned { if (itemDescFocused) focusedBounds = it.boundsInRoot() },
                minLines = 2,
                maxLines = 4,
                enabled = !itemSelected && otherFieldsEnabled,
                colors = lgrTextFieldColors()
            )

            Column {
                var ownerFieldFocused by remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = ownerQueryTfv,
                        onValueChange = { tfv ->
                            ownerQueryTfv = tfv
                            viewModel.editBarcodeOwnerQuery = tfv.text
                            viewModel.editBarcodeSelectedPerson = null
                            viewModel.editBarcodeOwnerUrl = null
                        },
                        label = { Text("Owner") },
                        enabled = otherFieldsEnabled,
                        modifier = Modifier.weight(1f).focusRequester(ownerFocusRequester)
                            .onFocusChanged { ownerFieldFocused = it.isFocused; if (!it.isFocused) focusedBounds = Rect.Zero }
                            .onGloballyPositioned { if (ownerFieldFocused) focusedBounds = it.boundsInRoot() },
                        singleLine = true,
                        colors = lgrTextFieldColors(),
                        trailingIcon = {
                            if (otherFieldsEnabled && ownerQueryTfv.text.isNotEmpty()) {
                                IconButton(onClick = {
                                    ownerQueryTfv = TextFieldValue("")
                                    viewModel.editBarcodeOwnerQuery = ""
                                    viewModel.editBarcodeSelectedPerson = null
                                    viewModel.editBarcodeOwnerUrl = null
                                    ownerSuggestions = emptyList()
                                    showOwnerSuggestions = false
                                    ownerFocusRequester.requestFocus()
                                }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                                }
                            }
                        }
                    )
                    IconButton(onClick = { viewModel.fillEditOwnerWithCurrentUser() }, enabled = otherFieldsEnabled) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = "Set to current user",
                            tint = if (otherFieldsEnabled) MaterialTheme.colorScheme.primary else GREY
                        )
                    }
                }
                if (showOwnerSuggestions) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Column {
                            ownerSuggestions.forEach { person ->
                                val display = person.displayName()
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            ownerQueryTfv = TextFieldValue(display, TextRange(display.length))
                                            viewModel.editBarcodeOwnerQuery = display
                                            viewModel.editBarcodeSelectedPerson = person
                                            viewModel.editBarcodeOwnerUrl = person.url
                                            showOwnerSuggestions = false
                                        }
                                        .padding(horizontal = 16.dp, vertical = 12.dp)
                                ) {
                                    Text(text = display, style = MaterialTheme.typography.bodyMedium)
                                }
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }

        }
        } // Box
        HorizontalDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
        ) {
            OutlinedButton(onClick = onBack) { Text("Cancel") }
            Button(
                onClick = {
                    if (codeChanged) {
                        showRenameConfirm = true
                    } else {
                        if (viewModel.editBarcodeOwnerUrl == null && viewModel.editBarcodeOwnerQuery.isNotBlank()) {
                            val match = ownerSuggestions.find { it.displayName().equals(viewModel.editBarcodeOwnerQuery.trim(), ignoreCase = true) }
                            if (match != null) {
                                viewModel.editBarcodeSelectedPerson = match
                                viewModel.editBarcodeOwnerUrl = match.url
                            }
                        }
                        viewModel.saveBarcodeEdit()
                    }
                },
                enabled = canSave
            ) {
                if (isBusy) CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                else Text("Save")
            }
        }
        } // outer Column
    }

    if (showRenameConfirm) {
        val newCode = viewModel.editBarcodeNewCode.lowercase().trim()
        AlertDialog(
            onDismissRequest = { showRenameConfirm = false },
            title = { Text("Change barcode?") },
            text = {
                Text(
                    "Change the code from “${barcode?.code}” to “$newCode”?\n\n" +
                        "The old entry is deleted and recreated under the new code. Any contained " +
                        "barcodes are moved along, but the scan history is not carried over."
                )
            },
            confirmButton = {
                Button(onClick = {
                    showRenameConfirm = false
                    viewModel.renameBarcode()
                }) { Text("Change") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showRenameConfirm = false }) { Text("Cancel") }
            }
        )
    }
}
