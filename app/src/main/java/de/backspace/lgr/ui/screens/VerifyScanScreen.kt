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
import de.backspace.lgr.viewmodel.VerifyPhase
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

private val DONE_GREEN = Color(0xFF4CAF50)
private val SCAN_GREEN = Color(0xFF4CAF50)

@Composable
fun VerifyScanScreen(
    viewModel: AppViewModel,
    onDone: () -> Unit,
    onBack: () -> Unit
) {
    val cooldown = remember { AtomicBoolean(false) }
    val handler = remember { Handler(Looper.getMainLooper()) }
    val scope = rememberCoroutineScope()
    var scanFlash by remember { mutableStateOf(false) }

    val phase = viewModel.verifyPhase
    val contentCount = viewModel.verifyContents.size

    BarcodeScannerScaffold(
        onBack = onBack,
        label = if (phase == VerifyPhase.LOCATION) "Scan location" else "Scan all content",
        borderColor = if (scanFlash) SCAN_GREEN else Color.White,
        onBarcodeDetected = { code ->
            if (cooldown.compareAndSet(false, true)) {
                if (viewModel.verifyPhase == VerifyPhase.CONTENT && code == viewModel.verifyLocation?.code) {
                    playRisingTone(handler)
                    handler.postDelayed({ cooldown.set(false) }, 1000)
                } else {
                    scope.launch {
                        val result = viewModel.onVerifyBarcodeScanned(code)
                        try {
                            val tg = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
                            when (result) {
                                ScanResult.FOUND_NEW -> {
                                    scanFlash = true
                                    tg.startTone(ToneGenerator.TONE_PROP_BEEP, 100)
                                    handler.postDelayed({ tg.release() }, 300)
                                    handler.postDelayed({ scanFlash = false }, 500)
                                    handler.postDelayed({ cooldown.set(false) }, 1000)
                                }
                                ScanResult.FOUND_EXISTING -> {
                                    scanFlash = true
                                    tg.startTone(ToneGenerator.TONE_PROP_BEEP, 100)
                                    handler.postDelayed({
                                        tg.startTone(ToneGenerator.TONE_DTMF_A, 80)
                                        handler.postDelayed({ tg.release() }, 200)
                                    }, 200)
                                    handler.postDelayed({ scanFlash = false }, 500)
                                    handler.postDelayed({ cooldown.set(false) }, 1000)
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
                                    handler.postDelayed({ cooldown.set(false) }, 1000)
                                }
                                ScanResult.NOT_FOUND -> {
                                    tg.startTone(ToneGenerator.TONE_PROP_NACK, 300)
                                    handler.postDelayed({ tg.release() }, 1000)
                                    handler.postDelayed({ cooldown.set(false) }, 1000)
                                }
                            }
                        } catch (_: Exception) {
                            handler.postDelayed({ cooldown.set(false) }, 1000)
                        }
                    }
                }
            }
        },
        bottomBar = {
            if (phase == VerifyPhase.CONTENT) {
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
                            text = "$contentCount barcode(s) scanned",
                            color = Color.White,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.align(Alignment.CenterVertically)
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = onBack,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White)
                            ) { Text("Cancel") }
                            Button(
                                onClick = onDone,
                                colors = ButtonDefaults.buttonColors(containerColor = DONE_GREEN)
                            ) { Text("Done") }
                        }
                    }
                }
            }
        }
    )
}
