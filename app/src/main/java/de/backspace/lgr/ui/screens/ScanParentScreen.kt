package de.backspace.lgr.ui.screens

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalLifecycleOwner
import de.backspace.lgr.viewmodel.AppViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ScanParentScreen(
    viewModel: AppViewModel,
    onParentScanned: () -> Unit,
    onBack: () -> Unit,
    selfCode: String? = null
) {
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
        label = "Scan location",
        onBarcodeDetected = { code ->
            if (!detected.value) {
                detected.value = true
                if (!selfCode.isNullOrBlank() && code == selfCode) {
                    playRisingTone(handler)
                    handler.postDelayed({ detected.value = false }, 1000)
                } else {
                    scope.launch {
                        val found = viewModel.setPendingNewParent(code)
                        if (found) {
                            try {
                                val tg = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
                                tg.startTone(ToneGenerator.TONE_PROP_BEEP, 100)
                                handler.postDelayed({ tg.release() }, 300)
                            } catch (_: Exception) {}
                            onParentScanned()
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
        }
    )
}
