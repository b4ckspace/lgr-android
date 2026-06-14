// SPDX-FileCopyrightText: 2026 Andreas Uhsemann
// SPDX-License-Identifier: Apache-2.0

package de.backspace.lgr.ui.screens

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import de.backspace.lgr.viewmodel.AppViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun BarcodeScanScreen(viewModel: AppViewModel, onBarcodeDetected: () -> Unit, onBack: () -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val detected = remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val handler = remember { Handler(Looper.getMainLooper()) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) detected.value = false
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    BarcodeScannerScaffold(
        onBack = onBack,
        label = "Scan barcode",
        onBarcodeDetected = { code ->
            if (!detected.value) {
                detected.value = true
                scope.launch {
                    val found = viewModel.tryLoadBarcode(code)
                    if (found) {
                        try {
                            val tg = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
                            tg.startTone(ToneGenerator.TONE_PROP_BEEP, 100)
                            handler.postDelayed({ tg.release() }, 300)
                        } catch (_: Exception) {}
                        onBarcodeDetected()
                    } else {
                        try {
                            val tg = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
                            tg.startTone(ToneGenerator.TONE_PROP_NACK, 300)
                            handler.postDelayed({ tg.release() }, 1000)
                        } catch (_: Exception) {}
                        delay(1000)
                        detected.value = false
                    }
                }
            }
        }
    )
}
