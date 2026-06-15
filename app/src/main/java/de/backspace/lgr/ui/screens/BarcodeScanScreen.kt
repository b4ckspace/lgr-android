// SPDX-FileCopyrightText: 2026 Andreas Uhsemann
// SPDX-License-Identifier: Apache-2.0

package de.backspace.lgr.ui.screens

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
                // Acknowledge instantly, before the lookup; correct with a burp if unknown.
                ScanTones.ack()
                scope.launch {
                    val found = viewModel.tryLoadBarcode(code)
                    if (found) {
                        onBarcodeDetected()
                    } else {
                        ScanTones.notFound()
                        delay(1000)
                        detected.value = false
                    }
                }
            }
        }
    )
}
