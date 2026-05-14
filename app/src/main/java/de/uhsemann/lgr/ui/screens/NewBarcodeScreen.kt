package de.uhsemann.lgr.ui.screens

import android.Manifest
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import de.uhsemann.lgr.data.model.Item
import de.uhsemann.lgr.viewmodel.AppViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

private val GREY = Color(0xFF9E9E9E)

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

    val newBarcodeState = viewModel.newBarcodeState

    LaunchedEffect(newBarcodeState.data) {
        if (newBarcodeState.data != null) onCreated()
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
        val results = viewModel.searchItemsWithCounts(query)
        itemSuggestions = results
        showSuggestions = results.isNotEmpty()
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
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
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
            }

            item {
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
            }

            item {
                Column {
                    OutlinedTextField(
                        value = viewModel.newBarcodeNameQuery,
                        onValueChange = {
                            if (viewModel.newBarcodeSelectedItem != null) {
                                viewModel.newBarcodeItemDescription = ""
                            }
                            viewModel.newBarcodeNameQuery = it
                            viewModel.newBarcodeSelectedItem = null
                        },
                        label = { Text("Item *") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = lgrTextFieldColors(),
                        trailingIcon = {
                            if (viewModel.newBarcodeNameQuery.isNotEmpty()) {
                                IconButton(onClick = {
                                    viewModel.newBarcodeNameQuery = ""
                                    viewModel.newBarcodeSelectedItem = null
                                    viewModel.newBarcodeItemDescription = ""
                                    itemSuggestions = emptyList()
                                    showSuggestions = false
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
            }

            item {
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
            }

            item {
                OutlinedTextField(
                    value = viewModel.newBarcodeDescription,
                    onValueChange = { viewModel.newBarcodeDescription = it },
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4,
                    colors = lgrTextFieldColors()
                )
            }

            if (newBarcodeState.error != null) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                            text = newBarcodeState.error,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.fillMaxWidth().padding(12.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ScanNewBarcodeScreen(
    viewModel: AppViewModel,
    onScanned: (String) -> Unit,
    onBack: () -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val permissionState = rememberPermissionState(Manifest.permission.CAMERA)
    val cooldown = remember { AtomicBoolean(false) }
    val handler = remember { Handler(Looper.getMainLooper()) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        if (!permissionState.status.isGranted) permissionState.launchPermissionRequest()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (permissionState.status.isGranted) {
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    val executor = ContextCompat.getMainExecutor(ctx)
                    val scanner = BarcodeScanning.getClient()
                    val providerFuture = ProcessCameraProvider.getInstance(ctx)

                    providerFuture.addListener({
                        val provider = providerFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }
                        val analyzer = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                        analyzer.setAnalyzer(executor) { imageProxy ->
                            val mediaImage = imageProxy.image
                            if (mediaImage != null) {
                                val image = InputImage.fromMediaImage(
                                    mediaImage, imageProxy.imageInfo.rotationDegrees
                                )
                                scanner.process(image)
                                    .addOnSuccessListener { barcodes ->
                                        barcodes.firstOrNull()?.rawValue?.let { code ->
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
                                                        try {
                                                            val tg = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
                                                            tg.startTone(ToneGenerator.TONE_PROP_NACK, 300)
                                                            handler.postDelayed({ tg.release() }, 500)
                                                        } catch (_: Exception) {}
                                                        delay(1500)
                                                        cooldown.set(false)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    .addOnCompleteListener { imageProxy.close() }
                            } else {
                                imageProxy.close()
                            }
                        }
                        try {
                            provider.unbindAll()
                            provider.bindToLifecycle(
                                lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analyzer
                            )
                        } catch (_: Exception) {}
                    }, executor)

                    previewView
                },
                modifier = Modifier.fillMaxSize()
            )

            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(260.dp)
                    .border(2.dp, Color.White, RoundedCornerShape(12.dp))
            )

            Text(
                text = "Scan barcode of new entry",
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.align(Alignment.Center).offset(y = 150.dp)
            )
        } else {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("Camera permission is required.")
                Spacer(Modifier.height(16.dp))
                Button(onClick = { permissionState.launchPermissionRequest() }) {
                    Text("Grant Permission")
                }
            }
        }

        IconButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.TopStart).padding(8.dp).statusBarsPadding()
        ) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun RawBarcodeScanScreen(
    label: String,
    onScanned: (String) -> Unit,
    onBack: () -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val permissionState = rememberPermissionState(Manifest.permission.CAMERA)
    var detected by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!permissionState.status.isGranted) permissionState.launchPermissionRequest()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (permissionState.status.isGranted) {
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    val executor = ContextCompat.getMainExecutor(ctx)
                    val scanner = BarcodeScanning.getClient()
                    val providerFuture = ProcessCameraProvider.getInstance(ctx)

                    providerFuture.addListener({
                        val provider = providerFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }
                        val analyzer = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                        analyzer.setAnalyzer(executor) { imageProxy ->
                            val mediaImage = imageProxy.image
                            if (mediaImage != null && !detected) {
                                val image = InputImage.fromMediaImage(
                                    mediaImage, imageProxy.imageInfo.rotationDegrees
                                )
                                scanner.process(image)
                                    .addOnSuccessListener { barcodes ->
                                        barcodes.firstOrNull()?.rawValue?.let { scanned ->
                                            if (!detected) {
                                                detected = true
                                                onScanned(scanned)
                                            }
                                        }
                                    }
                                    .addOnCompleteListener { imageProxy.close() }
                            } else {
                                imageProxy.close()
                            }
                        }
                        try {
                            provider.unbindAll()
                            provider.bindToLifecycle(
                                lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analyzer
                            )
                        } catch (_: Exception) {}
                    }, executor)

                    previewView
                },
                modifier = Modifier.fillMaxSize()
            )

            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(260.dp)
                    .border(2.dp, Color.White, RoundedCornerShape(12.dp))
            )

            Text(
                text = label,
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.align(Alignment.Center).offset(y = 150.dp)
            )
        } else {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("Camera permission is required.")
                Spacer(Modifier.height(16.dp))
                Button(onClick = { permissionState.launchPermissionRequest() }) {
                    Text("Grant Permission")
                }
            }
        }

        IconButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.TopStart).padding(8.dp).statusBarsPadding()
        ) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
        }
    }
}
