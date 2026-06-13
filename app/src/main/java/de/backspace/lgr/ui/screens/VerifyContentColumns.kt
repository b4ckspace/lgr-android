// SPDX-FileCopyrightText: 2026 Andreas Uhsemann
// SPDX-License-Identifier: Apache-2.0

package de.backspace.lgr.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import de.backspace.lgr.data.model.Barcode
import de.backspace.lgr.data.model.ChildInfo

private val COL_GREY = Color(0xFF9E9E9E)
private val COL_RED = Color(0xFFE53935)
private val COL_GREEN = Color(0xFF4CAF50)

private enum class ColRowType { MATCHED, LEFT_ONLY, RIGHT_ONLY }
private data class ColRow(
    val leftCode: String?, val leftName: String?,
    val rightCode: String?, val rightName: String?,
    val type: ColRowType
)

// Two-column "Current | Scanned" comparison of a container's expected vs scanned contents:
// matched rows are normal, expected-but-missing rows are red on the left, unexpected scans are
// green on the right. Shared by the standalone Verify result screen and the Barcode Detail
// content-verify mode so both look identical.
@Composable
fun VerifyContentColumns(
    dbChildren: List<ChildInfo>,
    scannedCodes: Set<String>,
    extraScanned: List<Barcode>,
    onBarcodeClick: ((String) -> Unit)?,
    modifier: Modifier = Modifier
) {
    val dbCodes = dbChildren.map { it.code }.toSet()
    val matched = dbChildren.filter { it.code in scannedCodes }.map {
        val name = it.name.removeSuffix(" (${it.code})")
        ColRow(it.code, name, it.code, name, ColRowType.MATCHED)
    }
    val leftOnly = dbChildren.filter { it.code !in scannedCodes }.map {
        ColRow(it.code, it.name.removeSuffix(" (${it.code})"), null, null, ColRowType.LEFT_ONLY)
    }
    val rightOnly = extraScanned.filter { it.code !in dbCodes }.map {
        ColRow(null, null, it.code, it.itemName, ColRowType.RIGHT_ONLY)
    }
    val rows = matched + leftOnly + rightOnly

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    Box(
                        modifier = Modifier.weight(1f).padding(end = 4.dp).then(
                            if (row.leftCode != null && onBarcodeClick != null)
                                Modifier.clickable { onBarcodeClick(row.leftCode) } else Modifier
                        )
                    ) {
                        if (row.leftCode != null) {
                            val color = if (row.type == ColRowType.LEFT_ONLY) COL_RED
                                        else MaterialTheme.colorScheme.onSurface
                            Text(
                                text = buildAnnotatedString {
                                    withStyle(SpanStyle(color = color)) { append(row.leftName ?: "") }
                                    append(" ")
                                    withStyle(SpanStyle(color = if (row.type == ColRowType.LEFT_ONLY) COL_RED.copy(alpha = 0.7f) else COL_GREY)) {
                                        append("(${row.leftCode})")
                                    }
                                },
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Box(
                        modifier = Modifier.weight(1f).padding(start = 4.dp).then(
                            if (row.rightCode != null && onBarcodeClick != null)
                                Modifier.clickable { onBarcodeClick(row.rightCode) } else Modifier
                        )
                    ) {
                        if (row.rightCode != null) {
                            val color = if (row.type == ColRowType.RIGHT_ONLY) COL_GREEN
                                        else MaterialTheme.colorScheme.onSurface
                            Text(
                                text = buildAnnotatedString {
                                    withStyle(SpanStyle(color = color)) { append(row.rightName ?: "") }
                                    append(" ")
                                    withStyle(SpanStyle(color = if (row.type == ColRowType.RIGHT_ONLY) COL_GREEN.copy(alpha = 0.7f) else COL_GREY)) {
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
