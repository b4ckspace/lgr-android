package de.backspace.lgr.ui.screens

import android.graphics.BitmapFactory
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlusOne
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.roundToInt
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import de.backspace.lgr.data.model.Barcode
import de.backspace.lgr.data.model.Item
import de.backspace.lgr.data.model.Person
import de.backspace.lgr.viewmodel.AppViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

private val GREY = Color(0xFF9E9E9E)

private fun ByteArray.applyExifRotation(): ByteArray {
    val exif = android.media.ExifInterface(java.io.ByteArrayInputStream(this))
    val degrees = when (exif.getAttributeInt(
        android.media.ExifInterface.TAG_ORIENTATION,
        android.media.ExifInterface.ORIENTATION_NORMAL
    )) {
        android.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90f
        android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180f
        android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270f
        else -> 0f
    }
    if (degrees == 0f) return this
    val bitmap = BitmapFactory.decodeByteArray(this, 0, size) ?: return this
    val matrix = android.graphics.Matrix().apply { postRotate(degrees) }
    val rotated = android.graphics.Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    val out = java.io.ByteArrayOutputStream()
    rotated.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out)
    return out.toByteArray()
}

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
    var locationQuery by remember { mutableStateOf(viewModel.newBarcodeParentCode) }
    var locationSelected by remember { mutableStateOf(viewModel.newBarcodeParentCode.isNotBlank()) }
    var locationSuggestions by remember { mutableStateOf<List<Barcode>>(emptyList()) }
    var showLocationSuggestions by remember { mutableStateOf(false) }
    val itemFocusRequester = remember { FocusRequester() }
    val ownerFocusRequester = remember { FocusRequester() }
    val scrollState = rememberScrollState()
    val density = LocalDensity.current
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
    var itemNameTfv by remember { mutableStateOf(TextFieldValue(viewModel.newBarcodeNameQuery)) }
    var ownerQueryTfv by remember { mutableStateOf(TextFieldValue(viewModel.newBarcodeOwnerQuery)) }

    val context = LocalContext.current
    val cameraUri = remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            val uri = cameraUri.value ?: return@rememberLauncherForActivityResult
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            if (bytes != null) viewModel.setNewBarcodePendingImage(bytes.applyExifRotation())
        }
    }

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
        itemSuggestions = results
        showSuggestions = results.isNotEmpty()
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

    // Sync location field when scan updates newBarcodeParentCode externally
    LaunchedEffect(viewModel.newBarcodeParentCode) {
        if (locationQuery != viewModel.newBarcodeParentCode) {
            locationQuery = viewModel.newBarcodeParentCode
            locationSelected = viewModel.newBarcodeParentCode.isNotBlank()
        }
    }

    LaunchedEffect(locationQuery, locationSelected) {
        if (locationQuery.length < 2 || locationSelected) {
            locationSuggestions = emptyList()
            showLocationSuggestions = false
            return@LaunchedEffect
        }
        delay(300)
        val results = viewModel.searchBarcodes(locationQuery).sortedBy { it.itemName.lowercase() }
        locationSuggestions = results
        showLocationSuggestions = results.isNotEmpty()
    }

    val canSave = viewModel.newBarcodeCode.isNotBlank() && viewModel.newBarcodeNameQuery.isNotBlank() && !newBarcodeState.isLoading
    var showFullscreenImage by remember { mutableStateOf(false) }

    val fsItemImage = viewModel.newBarcodeSelectedItem?.image
    if (showFullscreenImage && fsItemImage != null) {
        Dialog(
            onDismissRequest = { showFullscreenImage = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            var scale by remember { mutableStateOf(1f) }
            var offset by remember { mutableStateOf(Offset.Zero) }
            val transformableState = rememberTransformableState { zoomChange, panChange, _ ->
                scale = (scale * zoomChange).coerceIn(1f, 8f)
                offset = if (scale > 1f) offset + panChange / scale else Offset.Zero
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .transformable(state = transformableState)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { if (scale <= 1.05f) showFullscreenImage = false },
                            onDoubleTap = { if (scale > 1f) { scale = 1f; offset = Offset.Zero } else scale = 2f }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = fsItemImage,
                    contentDescription = "Full size item image",
                    imageLoader = viewModel.imageLoader,
                    modifier = Modifier.fillMaxSize()
                        .scale(scale)
                        .offset { IntOffset(offset.x.roundToInt(), offset.y.roundToInt()) },
                    contentScale = ContentScale.Fit
                )
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New Barcode") },
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
            if (showLocationSuggestions && focusedBounds != Rect.Zero) scrollState.animateScrollTo(fieldScrollTarget())
        }
        LaunchedEffect(showSuggestions) {
            if (showSuggestions && focusedBounds != Rect.Zero) scrollState.animateScrollTo(fieldScrollTarget())
        }
        LaunchedEffect(showOwnerSuggestions) {
            if (showOwnerSuggestions && focusedBounds != Rect.Zero) scrollState.animateScrollTo(fieldScrollTarget())
        }
        LaunchedEffect(newBarcodeState.error) {
            if (newBarcodeState.error != null) scrollState.animateScrollTo(0)
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(padding).imePadding()
        ) {
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

            Column {
                var locationFocused by remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = locationQuery,
                        onValueChange = { query ->
                            locationQuery = query
                            viewModel.newBarcodeParentCode = query
                            locationSelected = false
                        },
                        label = { Text("Location") },
                        modifier = Modifier.weight(1f)
                            .onFocusChanged { locationFocused = it.isFocused; if (!it.isFocused) focusedBounds = Rect.Zero }
                            .onGloballyPositioned { if (locationFocused) focusedBounds = it.boundsInRoot() },
                        singleLine = true,
                        colors = lgrTextFieldColors(),
                        trailingIcon = {
                            if (locationQuery.isNotEmpty()) {
                                IconButton(onClick = {
                                    locationQuery = ""
                                    viewModel.newBarcodeParentCode = ""
                                    locationSelected = false
                                    locationSuggestions = emptyList()
                                    showLocationSuggestions = false
                                }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                                }
                            }
                        }
                    )
                    IconButton(onClick = onScanParent) {
                        Icon(
                            Icons.Default.QrCodeScanner,
                            contentDescription = "Scan location",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                if (showLocationSuggestions) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Column {
                            locationSuggestions.forEach { barcode ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            locationQuery = barcode.code
                                            viewModel.newBarcodeParentCode = barcode.code
                                            locationSelected = true
                                            showLocationSuggestions = false
                                        }
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = barcode.itemName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = "(${barcode.code})",
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

            var barcodeFocused by remember { mutableStateOf(false) }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = viewModel.newBarcodeCode,
                    onValueChange = { viewModel.newBarcodeCode = it },
                    label = { Text("Barcode *") },
                    modifier = Modifier.weight(1f)
                        .onFocusChanged { barcodeFocused = it.isFocused; if (!it.isFocused) focusedBounds = Rect.Zero }
                        .onGloballyPositioned { if (barcodeFocused) focusedBounds = it.boundsInRoot() },
                    singleLine = true,
                    readOnly = viewModel.newBarcodeGenerating,
                    colors = lgrTextFieldColors()
                )
                IconButton(
                    onClick = { viewModel.generateNextAvailableBarcode() },
                    enabled = !viewModel.newBarcodeGenerating
                ) {
                    if (viewModel.newBarcodeGenerating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Icon(
                            Icons.Default.PlusOne,
                            contentDescription = "Generate next available barcode",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                IconButton(onClick = onScanCode) {
                    Icon(
                        Icons.Default.QrCodeScanner,
                        contentDescription = "Scan barcode",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Column {
                var itemFocused by remember { mutableStateOf(false) }
                OutlinedTextField(
                        value = itemNameTfv,
                        onValueChange = { tfv ->
                            if (tfv.text != itemNameTfv.text) {
                                if (viewModel.newBarcodeSelectedItem != null) {
                                    viewModel.newBarcodeItemDescription = ""
                                }
                                viewModel.newBarcodeNameQuery = tfv.text
                                viewModel.newBarcodeSelectedItem = null
                            }
                            itemNameTfv = tfv
                        },
                        label = { Text("Item *") },
                        modifier = Modifier.fillMaxWidth().focusRequester(itemFocusRequester)
                            .onFocusChanged { itemFocused = it.isFocused; if (!it.isFocused) focusedBounds = Rect.Zero }
                            .onGloballyPositioned { if (itemFocused) focusedBounds = it.boundsInRoot() },
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
                                                viewModel.setNewBarcodePendingImage(null)
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
                value = viewModel.newBarcodeDescription,
                onValueChange = { viewModel.newBarcodeDescription = it },
                label = { Text("Barcode description") },
                modifier = Modifier.fillMaxWidth()
                    .onFocusChanged { descFocused = it.isFocused; if (!it.isFocused) focusedBounds = Rect.Zero }
                    .onGloballyPositioned { if (descFocused) focusedBounds = it.boundsInRoot() },
                minLines = 2,
                maxLines = 4,
                colors = lgrTextFieldColors()
            )

            val itemSelected = viewModel.newBarcodeSelectedItem != null
            var itemDescFocused by remember { mutableStateOf(false) }
            OutlinedTextField(
                value = viewModel.newBarcodeItemDescription,
                onValueChange = { viewModel.newBarcodeItemDescription = it },
                label = { Text("Item description") },
                modifier = Modifier.fillMaxWidth()
                    .onFocusChanged { itemDescFocused = it.isFocused; if (!it.isFocused) focusedBounds = Rect.Zero }
                    .onGloballyPositioned { if (itemDescFocused) focusedBounds = it.boundsInRoot() },
                minLines = 2,
                maxLines = 4,
                enabled = !itemSelected,
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
                                viewModel.newBarcodeOwnerQuery = tfv.text
                                viewModel.newBarcodeSelectedPerson = null
                                viewModel.newBarcodeOwnerUrl = null
                            },
                            label = { Text("Owner") },
                            modifier = Modifier.weight(1f).focusRequester(ownerFocusRequester)
                                .onFocusChanged { ownerFieldFocused = it.isFocused; if (!it.isFocused) focusedBounds = Rect.Zero }
                                .onGloballyPositioned { if (ownerFieldFocused) focusedBounds = it.boundsInRoot() },
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

            if (viewModel.supportsImages && !viewModel.readonlyMode) {
                HorizontalDivider()
                val selectedItemImage = viewModel.newBarcodeSelectedItem?.image
                val pendingBytes = viewModel.newBarcodePendingImageBytes
                if (itemSelected && selectedItemImage != null) {
                    AsyncImage(
                        model = selectedItemImage,
                        contentDescription = "Item image",
                        imageLoader = viewModel.imageLoader,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { showFullscreenImage = true },
                        contentScale = ContentScale.Crop
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (!itemSelected && pendingBytes != null) {
                        val bitmap = remember(pendingBytes) {
                            BitmapFactory.decodeByteArray(pendingBytes, 0, pendingBytes.size)?.asImageBitmap()
                        }
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap,
                                contentDescription = "Pending photo",
                                modifier = Modifier.size(64.dp).clip(RoundedCornerShape(4.dp)),
                                contentScale = ContentScale.Crop
                            )
                        }
                        Text(
                            "Photo attached",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { viewModel.setNewBarcodePendingImage(null) }) {
                            Icon(Icons.Default.Clear, contentDescription = "Remove photo", tint = MaterialTheme.colorScheme.error)
                        }
                    } else if (!itemSelected) {
                        Text(
                            "Photo",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                    IconButton(
                        onClick = {
                            val cameraDir = File(context.cacheDir, "camera_images").also { it.mkdirs() }
                            val imageFile = File(cameraDir, "new_barcode.jpg")
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", imageFile)
                            cameraUri.value = uri
                            cameraLauncher.launch(uri)
                        },
                        enabled = !itemSelected
                    ) {
                        Icon(
                            Icons.Default.PhotoCamera,
                            contentDescription = "Take photo",
                            tint = if (itemSelected) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                   else MaterialTheme.colorScheme.primary
                        )
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
                    if (viewModel.newBarcodeOwnerUrl == null && viewModel.newBarcodeOwnerQuery.isNotBlank()) {
                        val match = ownerSuggestions.find { it.displayName().equals(viewModel.newBarcodeOwnerQuery.trim(), ignoreCase = true) }
                        if (match != null) {
                            viewModel.newBarcodeSelectedPerson = match
                            viewModel.newBarcodeOwnerUrl = match.url
                        }
                    }
                    viewModel.createNewBarcode()
                },
                enabled = canSave
            ) {
                if (newBarcodeState.isLoading) CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                else Text("Save")
            }
        }
        } // outer Column
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
