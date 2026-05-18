package de.backspace.lgr.ui.screens

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import de.backspace.lgr.viewmodel.AppViewModel
import de.backspace.lgr.viewmodel.ScanResult
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

private val SCAN_GREEN = Color(0xFF4CAF50)

@Composable
fun ContentScanScreen(
    viewModel: AppViewModel,
    onDone: () -> Unit,
    addOnly: Boolean = false,
    onCancel: (() -> Unit)? = null
) {
    val cooldown = remember { AtomicBoolean(false) }
    val handler = remember { Handler(Looper.getMainLooper()) }
    val scope = rememberCoroutineScope()
    var scanFlash by remember { mutableStateOf(false) }

    val scannedCount by remember {
        derivedStateOf {
            if (addOnly) viewModel.newScannedBarcodes.size
            else viewModel.scannedChildCodes.size + viewModel.newScannedBarcodes.size
        }
    }

    BarcodeScannerScaffold(
        onBack = onCancel ?: onDone,
        label = if (addOnly) "Add content" else "Scan all content",
        borderColor = if (scanFlash) SCAN_GREEN else Color.White,
        onBarcodeDetected = { code ->
            if (cooldown.compareAndSet(false, true)) {
                if (code == viewModel.scannedBarcode.data?.code) {
                    playRisingTone(handler)
                    handler.postDelayed({ cooldown.set(false) }, 1000)
                } else {
                    scanFlash = true
                    scope.launch {
                        val result = if (addOnly) viewModel.onAddContentBarcodeScanned(code)
                                     else viewModel.onContentBarcodeScanned(code)
                        try {
                            val tg = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
                            when (result) {
                                ScanResult.FOUND_NEW -> {
                                    tg.startTone(ToneGenerator.TONE_PROP_BEEP, 100)
                                    handler.postDelayed({ tg.release() }, 300)
                                }
                                ScanResult.FOUND_EXISTING -> {
                                    tg.startTone(ToneGenerator.TONE_PROP_BEEP, 100)
                                    handler.postDelayed({
                                        tg.startTone(ToneGenerator.TONE_DTMF_A, 80)
                                        handler.postDelayed({ tg.release() }, 200)
                                    }, 200)
                                }
                                ScanResult.DUPLICATE -> {
                                    tg.startTone(ToneGenerator.TONE_PROP_BEEP, 100)
                                    handler.postDelayed({
                                        tg.startTone(ToneGenerator.TONE_DTMF_A, 80)
                                        handler.postDelayed({
                                            tg.startTone(ToneGenerator.TONE_DTMF_A, 80)
                                            handler.postDelayed({ tg.release() }, 200)
                                        }, 150)
                                    }, 200)
                                }
                                ScanResult.NOT_FOUND -> {
                                    tg.startTone(ToneGenerator.TONE_PROP_NACK, 300)
                                    handler.postDelayed({ tg.release() }, 1000)
                                }
                            }
                        } catch (_: Exception) {}
                    }
                    handler.postDelayed({ scanFlash = false }, 500)
                    handler.postDelayed({ cooldown.set(false) }, 1000)
                }
            }
        },
        bottomBar = {
            Surface(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                color = Color.Black.copy(alpha = 0.65f)
            ) {
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "$scannedCount barcode(s) scanned",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.align(Alignment.CenterVertically)
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (onCancel != null) {
                            OutlinedButton(
                                onClick = onCancel,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White)
                            ) { Text("Cancel") }
                        }
                        Button(onClick = onDone) { Text("Done") }
                    }
                }
            }
        }
    )
}
