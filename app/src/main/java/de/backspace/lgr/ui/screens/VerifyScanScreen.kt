package de.backspace.lgr.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import de.backspace.lgr.viewmodel.AppViewModel
import de.backspace.lgr.viewmodel.VerifyPhase

private val VERIFY_DONE_GREEN = Color(0xFF4CAF50)

@Composable
fun VerifyScanScreen(
    viewModel: AppViewModel,
    onDone: () -> Unit,
    onBack: () -> Unit
) {
    val phase = viewModel.verifyPhase

    ContentScannerScaffold(
        label = when {
            phase == VerifyPhase.LOCATION -> "Scan location"
            viewModel.verifyAdditive -> "Scan additional content"
            else -> "Scan all content"
        },
        onBack = onBack,
        // Only reject scanning the container itself once we are past the location phase.
        selfCode = if (phase == VerifyPhase.CONTENT) viewModel.verifyLocation?.code else null,
        onScan = { code -> viewModel.onVerifyBarcodeScanned(code) },
        scannedCount = viewModel.verifyContents.size,
        onDone = onDone,
        onCancel = onBack,
        showBottomBar = phase == VerifyPhase.CONTENT,
        doneColor = VERIFY_DONE_GREEN
    )
}
