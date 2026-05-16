package de.backspace.lgr.ui.screens

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import de.backspace.lgr.data.model.Item
import de.backspace.lgr.data.model.Person
import de.backspace.lgr.viewmodel.AppViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

private val GREY = Color(0xFF9E9E9E)

private fun Person.displayName(): String =
    listOf(firstname, lastname).filter { it.isNotBlank() }.joinToString(" ").ifBlank { nickname }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewBarcodeScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onScanCode: () -> Unit,
    onScanParent: () -> Unit,
    onCreated: () -> Unit
) {
    var itemSuggestions by remember { mutableStateOf<List<Pair<Item, Int>>>(emptyList()) }
    var showSuggestions by remember { mutableStateOf(false) }
    var ownerSuggestions by remember { mutableStateOf<List<Person>>(emptyList()) }
    var showOwnerSuggestions by remember { mutableStateOf(false) }
    val itemFocusRequester = remember { FocusRequester() }
    val ownerFocusRequester = remember { FocusRequester() }
    var itemNameTfv by remember { mutableStateOf(TextFieldValue(viewModel.newBarcodeNameQuery)) }
    var ownerQueryTfv by remember { mutableStateOf(TextFieldValue(viewModel.newBarcodeOwnerQuery)) }

    val newBarcodeState = viewModel.newBarcodeState

    LaunchedEffect(newBarcodeState.data) {
        if (newBarcodeState.data != null) onCreated()
    }

    LaunchedEffect(viewModel.newBarcodeNameQuery) {
        if (itemNameTfv.text != viewModel.newBarcodeNameQuery) {
            val t = viewModel.newBarcodeNameQuery
            itemNameTfv = TextFieldValue(t, TextRange(t.length))
        }
    }

    LaunchedEffect(viewModel.newBarcodeOwnerQuery) {
        if (ownerQueryTfv.text != viewModel.newBarcodeOwnerQuery) {
            val t = viewModel.newBarcodeOwnerQuery
            ownerQueryTfv = TextFieldValue(t, TextRange(t.length))
        }
    }

    LaunchedEffect(viewModel.newBarcodeNameQuery) {
        val query = viewModel.newBarcodeNameQuery
        if (query.length < 2 || viewModel.newBarcodeSelectedItem?.name == query) {
            if (viewModel.newBarcodeSelectedItem?.name != query) {
                itemSuggestions = emptyList()
                showSuggestions = false
            }
            return@LaunchedEffect
        }
        delay(300)
        val results = viewModel.searchItemsWithCounts(query).sortedBy { (item, _) -> item.name.lowercase() }
        val exactMatch = results.find { (item, _) -> item.name.equals(query, ignoreCase = true) }
        if (exactMatch != null && results.size == 1) {
            viewModel.newBarcodeNameQuery = exactMatch.first.name
            viewModel.newBarcodeSelectedItem = exactMatch.first
            viewModel.newBarcodeItemDescription = exactMatch.first.description
            itemSuggestions = emptyList()
            showSuggestions = false
        } else {
            itemSuggestions = results
            showSuggestions = results.isNotEmpty()
        }
    }

    LaunchedEffect(viewModel.newBarcodeOwnerQuery) {
        val query = viewModel.newBarcodeOwnerQuery
        if (query.length < 2 || viewModel.newBarcodeSelectedPerson != null || viewModel.newBarcodeOwnerUrl != null) {
            ownerSuggestions = emptyList()
            showOwnerSuggestions = false
            return@LaunchedEffect
        }
        delay(300)
        val results = viewModel.searchPersons(query).sortedBy { it.displayName().lowercase() }
        ownerSuggestions = results
        showOwnerSuggestions = results.isNotEmpty()
    }

    val canSave = viewModel.newBarcodeCode.isNotBlank() && viewModel.newBarcodeNameQuery.isNotBlank() && !newBarcodeState.isLoading

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New Barcode") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(
                        onClick = { viewModel.createNewBarcode() },
                        enabled = canSave
                    ) {
                        if (newBarcodeState.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        } else {
                            Text("Save")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (newBarcodeState.error != null) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = newBarcodeState.error.replaceFirstChar { it.uppercaseChar() },
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.fillMaxWidth().padding(12.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = viewModel.newBarcodeParentCode,
                    onValueChange = { viewModel.newBarcodeParentCode = it },
                    label = { Text("Location") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    colors = lgrTextFieldColors()
                )
                IconButton(onClick = onScanParent) {
                    Icon(
                        Icons.Default.QrCodeScanner,
                        contentDescription = "Scan location",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = viewModel.newBarcodeCode,
                    onValueChange = { viewModel.newBarcodeCode = it },
                    label = { Text("Barcode *") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    colors = lgrTextFieldColors()
                )
                IconButton(onClick = onScanCode) {
                    Icon(
                        Icons.Default.QrCodeScanner,
                        contentDescription = "Scan barcode",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Column {
                OutlinedTextField(
                        value = itemNameTfv,
                        onValueChange = { tfv ->
                            itemNameTfv = tfv
                            if (viewModel.newBarcodeSelectedItem != null) {
                                viewModel.newBarcodeItemDescription = ""
                            }
                            viewModel.newBarcodeNameQuery = tfv.text
                            viewModel.newBarcodeSelectedItem = null
                        },
                        label = { Text("Item *") },
                        modifier = Modifier.fillMaxWidth().focusRequester(itemFocusRequester),
                        singleLine = true,
                        colors = lgrTextFieldColors(),
                        trailingIcon = {
                            if (itemNameTfv.text.isNotEmpty()) {
                                IconButton(onClick = {
                                    if (viewModel.newBarcodeSelectedItem != null) {
                                        viewModel.newBarcodeItemDescription = ""
                                    }
                                    itemNameTfv = TextFieldValue("")
                                    viewModel.newBarcodeNameQuery = ""
                                    viewModel.newBarcodeSelectedItem = null
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
                                                viewModel.newBarcodeNameQuery = item.name
                                                viewModel.newBarcodeSelectedItem = item
                                                viewModel.newBarcodeItemDescription = item.description
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

            OutlinedTextField(
                value = viewModel.newBarcodeDescription,
                onValueChange = { viewModel.newBarcodeDescription = it },
                label = { Text("Description") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 4,
                colors = lgrTextFieldColors()
            )

            val itemSelected = viewModel.newBarcodeSelectedItem != null
            OutlinedTextField(
                value = viewModel.newBarcodeItemDescription,
                onValueChange = { if (!itemSelected) viewModel.newBarcodeItemDescription = it },
                label = { Text("Item description") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 4,
                readOnly = itemSelected,
                colors = lgrTextFieldColors()
            )

            Column {
                Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = ownerQueryTfv,
                            onValueChange = { tfv ->
                                ownerQueryTfv = tfv
                                viewModel.newBarcodeOwnerQuery = tfv.text
                                viewModel.newBarcodeSelectedPerson = null
                                viewModel.newBarcodeOwnerUrl = null
                            },
                            label = { Text("Owner") },
                            modifier = Modifier.weight(1f).focusRequester(ownerFocusRequester),
                            singleLine = true,
                            colors = lgrTextFieldColors(),
                            trailingIcon = {
                                if (ownerQueryTfv.text.isNotEmpty()) {
                                    IconButton(onClick = {
                                        ownerQueryTfv = TextFieldValue("")
                                        viewModel.newBarcodeOwnerQuery = ""
                                        viewModel.newBarcodeSelectedPerson = null
                                        viewModel.newBarcodeOwnerUrl = null
                                        ownerSuggestions = emptyList()
                                        showOwnerSuggestions = false
                                        ownerFocusRequester.requestFocus()
                                    }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                                    }
                                }
                            }
                        )
                        IconButton(onClick = { viewModel.fillOwnerWithCurrentUser() }) {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = "Set to current user",
                                tint = MaterialTheme.colorScheme.primary
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
                                                viewModel.newBarcodeOwnerQuery = display
                                                viewModel.newBarcodeSelectedPerson = person
                                                viewModel.newBarcodeOwnerUrl = person.url
                                                showOwnerSuggestions = false
                                            }
                                            .padding(horizontal = 16.dp, vertical = 12.dp)
                                    ) {
                                        Text(
                                            text = display,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                    HorizontalDivider()
                                }
                            }
                        }
                    }
                }

        }
    }
}

@Composable
fun ScanNewBarcodeScreen(
    viewModel: AppViewModel,
    onScanned: (String) -> Unit,
    onBack: () -> Unit
) {
    val cooldown = remember { AtomicBoolean(false) }
    val handler = remember { Handler(Looper.getMainLooper()) }
    val scope = rememberCoroutineScope()

    BarcodeScannerScaffold(
        onBack = onBack,
        label = "Scan new barcode",
        onBarcodeDetected = { code ->
            if (cooldown.compareAndSet(false, true)) {
                scope.launch {
                    val isNew = viewModel.isBarcodeNew(code)
                    if (isNew) {
                        try {
                            val tg = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
                            tg.startTone(ToneGenerator.TONE_PROP_BEEP, 100)
                            handler.postDelayed({ tg.release() }, 300)
                        } catch (_: Exception) {}
                        onScanned(code)
                    } else {
                        playRisingTone(handler)
                        delay(1000)
                        cooldown.set(false)
                    }
                }
            }
        }
    )
}
