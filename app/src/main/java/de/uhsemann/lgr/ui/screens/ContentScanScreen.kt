package de.uhsemann.lgr.ui.screens

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
import de.uhsemann.lgr.viewmodel.AppViewModel
import de.uhsemann.lgr.viewmodel.ScanResult
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

private val SCAN_GREEN = Color(0xFF4CAF50)

@Composable
fun ContentScanScreen(viewModel: AppViewModel, onDone: () -> Unit, addOnly: Boolean = false) {
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
        onBack = onDone,
        label = "Scan content",
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
                    handler.postDelayed({
                        scanFlash = false
                        cooldown.set(false)
                    }, 1000)
                }
            }
        },
        bottomBar = {
            Surface(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                color = Color.Black.copy(alpha = 0.65f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$scannedCount barcode(s) scanned",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Button(onClick = onDone) { Text("Done") }
                }
            }
        }
    )
}
