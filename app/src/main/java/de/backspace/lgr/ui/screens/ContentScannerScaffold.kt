// SPDX-FileCopyrightText: 2026 Andreas Uhsemann
// SPDX-License-Identifier: Apache-2.0

package de.backspace.lgr.ui.screens

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import de.backspace.lgr.viewmodel.ScanResult
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

private val SCAN_FLASH_GREEN = Color(0xFF4CAF50)

// Standard audio feedback for a scan result (shared by every content-collecting scanner).
private fun playScanFeedback(handler: Handler, result: ScanResult) {
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

// A camera scanner specialised for collecting the contents of a container: it owns the scan
// cooldown, audio feedback, the green border flash on a recognised scan, and the "N scanned"
// bottom bar. Both the standalone Verify flow (VerifyScanScreen) and the Barcode Detail
// content scan (ContentScanScreen) are thin wrappers over this, which is why the camera-text
// and behaviour only need to be defined once.
@Composable
fun ContentScannerScaffold(
    label: String,
    onBack: () -> Unit,
    selfCode: String?,
    onScan: suspend (String) -> ScanResult,
    scannedCount: Int,
    onDone: () -> Unit,
    onCancel: (() -> Unit)? = null,
    showBottomBar: Boolean = true,
    doneColor: Color? = null
) {
    val cooldown = remember { AtomicBoolean(false) }
    val handler = remember { Handler(Looper.getMainLooper()) }
    val scope = rememberCoroutineScope()
    var scanFlash by remember { mutableStateOf(false) }

    BarcodeScannerScaffold(
        onBack = onBack,
        label = label,
        borderColor = if (scanFlash) SCAN_FLASH_GREEN else Color.White,
        onBarcodeDetected = { code ->
            if (cooldown.compareAndSet(false, true)) {
                if (selfCode != null && code == selfCode) {
                    // Scanning the container itself is meaningless; reject with a rising tone.
                    playRisingTone(handler)
                    handler.postDelayed({ cooldown.set(false) }, 1000)
                } else {
                    scope.launch {
                        val result = onScan(code)
                        playScanFeedback(handler, result)
                        if (result == ScanResult.FOUND_NEW || result == ScanResult.FOUND_EXISTING) {
                            scanFlash = true
                            handler.postDelayed({ scanFlash = false }, 500)
                        }
                        handler.postDelayed({ cooldown.set(false) }, 1000)
                    }
                }
            }
        },
        bottomBar = {
            if (showBottomBar) {
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
                                    border = BorderStroke(1.dp, Color.White)
                                ) { Text("Cancel") }
                            }
                            Button(
                                onClick = onDone,
                                colors = if (doneColor != null)
                                    ButtonDefaults.buttonColors(containerColor = doneColor)
                                else ButtonDefaults.buttonColors()
                            ) { Text("Done") }
                        }
                    }
                }
            }
        }
    )
}
