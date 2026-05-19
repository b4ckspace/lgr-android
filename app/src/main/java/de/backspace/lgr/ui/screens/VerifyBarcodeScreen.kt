package de.backspace.lgr.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import de.backspace.lgr.data.model.Barcode
import de.backspace.lgr.viewmodel.AppViewModel

private val VERIFY_GREY = Color(0xFF9E9E9E)
private val VERIFY_RED = Color(0xFFE53935)
private val VERIFY_GREEN = Color(0xFF4CAF50)

private enum class RowType { MATCHED, LEFT_ONLY, RIGHT_ONLY }

private data class VerifyRow(
    val leftCode: String?,
    val leftName: String?,
    val rightCode: String?,
    val rightName: String?,
    val type: RowType
)

private fun buildRows(location: Barcode, scanned: List<Barcode>): List<VerifyRow> {
    val dbChildren = location.apiChildNames ?: emptyList()
    val dbCodes = dbChildren.map { it.code }.toSet()
    val scannedCodes = scanned.map { it.code }.toSet()
    val scannedByCode = scanned.associateBy { it.code }

    val matched = dbChildren.filter { it.code in scannedCodes }.map { child ->
        val itemName = child.name.removeSuffix(" (${child.code})")
        VerifyRow(child.code, itemName, child.code, scannedByCode[child.code]!!.itemName, RowType.MATCHED)
    }
    val leftOnly = dbChildren.filter { it.code !in scannedCodes }.map { child ->
        VerifyRow(child.code, child.name.removeSuffix(" (${child.code})"), null, null, RowType.LEFT_ONLY)
    }
    val rightOnly = scanned.filter { it.code !in dbCodes }.map { b ->
        VerifyRow(null, null, b.code, b.itemName, RowType.RIGHT_ONLY)
    }
    return matched + leftOnly + rightOnly
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VerifyBarcodeScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onRescan: () -> Unit,
    onCancel: () -> Unit,
    onSaved: () -> Unit,
    onVerifyNext: () -> Unit,
    onOk: () -> Unit,
    onBarcodeClick: ((String) -> Unit)? = null
) {
    val location = viewModel.verifyLocation
    val scanned = viewModel.verifyContents
    val saveState = viewModel.verifyState

    LaunchedEffect(saveState.data) {
        if (saveState.data != null) onSaved()
    }

    if (location == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No location scanned.", color = MaterialTheme.colorScheme.error)
        }
        return
    }

    val rows = remember(location, scanned) { buildRows(location, scanned) }
    val hasMismatches = rows.any { it.type != RowType.MATCHED }
    val totalCount = (location.apiChildNames?.size ?: 0) + scanned.filter { b ->
        location.apiChildNames?.none { it.code == b.code } == true
    }.size

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Verify") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Location section
                item {
                    val ancestors = location.apiParentNames ?: emptyList()
                    Column {
                        Text(
                            "Location",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(2.dp))
                        if (ancestors.isEmpty()) {
                            Text("—", style = MaterialTheme.typography.bodyLarge)
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                ancestors.reversed().forEachIndexed { i, info ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (i > 0) Text("› ", style = MaterialTheme.typography.bodyLarge, color = VERIFY_GREY)
                                        val name = info.name.removeSuffix(" (${info.code})")
                                        Text(
                                            text = buildAnnotatedString {
                                                append(name)
                                                append(" ")
                                                withStyle(SpanStyle(color = VERIFY_GREY)) { append("(${info.code})") }
                                            },
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                    }
                                }
                            }
                        }
                        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
                    }
                }

                item { VerifyDetailRow("Barcode", location.code) }
                item { VerifyDetailRow("Item", location.itemName) }
                if (location.description.isNotBlank())
                    item { VerifyDetailRow("Description", location.description) }

                // Two-column contents section
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Content ($totalCount)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            IconButton(
                                onClick = {
                                    viewModel.startVerifyContentRescan()
                                    onRescan()
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.QrCodeScanner,
                                    contentDescription = "Re-scan content",
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        // Column headers
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                "Current",
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "Scanned",
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (rows.isEmpty()) {
                            Text("—", style = MaterialTheme.typography.bodyMedium)
                        } else {
                            rows.forEach { row ->
                                val leftCode = row.leftCode
                                val rightCode = row.rightCode
                                val clickHandler = onBarcodeClick
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    // Left cell
                                    Box(
                                        modifier = Modifier.weight(1f).padding(end = 4.dp)
                                            .then(if (leftCode != null && clickHandler != null)
                                                Modifier.clickable { clickHandler(leftCode) } else Modifier)
                                    ) {
                                        if (row.leftCode != null) {
                                            val color = if (row.type == RowType.LEFT_ONLY) VERIFY_RED
                                                        else MaterialTheme.colorScheme.onSurface
                                            Text(
                                                text = buildAnnotatedString {
                                                    withStyle(SpanStyle(color = color)) { append(row.leftName ?: "") }
                                                    append(" ")
                                                    withStyle(SpanStyle(color = if (row.type == RowType.LEFT_ONLY) VERIFY_RED.copy(alpha = 0.7f) else VERIFY_GREY)) {
                                                        append("(${row.leftCode})")
                                                    }
                                                },
                                                style = MaterialTheme.typography.bodySmall,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    // Right cell
                                    Box(
                                        modifier = Modifier.weight(1f).padding(start = 4.dp)
                                            .then(if (rightCode != null && clickHandler != null)
                                                Modifier.clickable { clickHandler(rightCode) } else Modifier)
                                    ) {
                                        if (row.rightCode != null) {
                                            val color = if (row.type == RowType.RIGHT_ONLY) VERIFY_GREEN
                                                        else MaterialTheme.colorScheme.onSurface
                                            Text(
                                                text = buildAnnotatedString {
                                                    withStyle(SpanStyle(color = color)) { append(row.rightName ?: "") }
                                                    append(" ")
                                                    withStyle(SpanStyle(color = if (row.type == RowType.RIGHT_ONLY) VERIFY_GREEN.copy(alpha = 0.7f) else VERIFY_GREY)) {
                                                        append("(${row.rightCode})")
                                                    }
                                                },
                                                style = MaterialTheme.typography.bodySmall,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            }
                        }
                    }
                }

                if (saveState.error != null) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                        ) {
                            Text(
                                text = saveState.error,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.fillMaxWidth().padding(12.dp)
                            )
                        }
                    }
                }
            }

            HorizontalDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
            ) {
                if (hasMismatches) {
                    OutlinedButton(onClick = onCancel) { Text("Cancel") }
                    Button(
                        onClick = { viewModel.saveVerifyChanges() },
                        enabled = !saveState.isLoading
                    ) {
                        if (saveState.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Text("Save")
                        }
                    }
                } else {
                    OutlinedButton(onClick = onVerifyNext) { Text("Verify next") }
                    Button(onClick = onOk) { Text("OK") }
                }
            }
        }
    }
}

@Composable
private fun VerifyDetailRow(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(2.dp))
        Text(value, style = MaterialTheme.typography.bodyLarge)
        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
    }
}
