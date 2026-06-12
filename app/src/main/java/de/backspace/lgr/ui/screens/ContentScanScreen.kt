package de.backspace.lgr.ui.screens

import androidx.compose.runtime.Composable
import de.backspace.lgr.viewmodel.AppViewModel

@Composable
fun ContentScanScreen(
    viewModel: AppViewModel,
    onDone: () -> Unit,
    addOnly: Boolean = false,
    onCancel: (() -> Unit)? = null
) {
    val scannedCount = if (addOnly) viewModel.newScannedBarcodes.size
                       else viewModel.scannedChildCodes.size + viewModel.newScannedBarcodes.size

    ContentScannerScaffold(
        label = if (addOnly) "Add content"
                else if (viewModel.contentScanAdditive) "Scan additional content"
                else "Scan all content",
        onBack = onCancel ?: onDone,
        selfCode = viewModel.scannedBarcode.data?.code,
        onScan = { code ->
            if (addOnly) viewModel.onAddContentBarcodeScanned(code)
            else viewModel.onContentBarcodeScanned(code)
        },
        scannedCount = scannedCount,
        onDone = onDone,
        onCancel = onCancel
    )
}
