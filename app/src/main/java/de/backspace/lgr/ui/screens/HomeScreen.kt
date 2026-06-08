package de.backspace.lgr.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@Composable
fun HomeScreen(
    onScanBarcode: () -> Unit,
    onNewBarcode: () -> Unit,
    onVerify: () -> Unit,
    onItems: () -> Unit,
    onBarcodes: () -> Unit,
    onPersons: () -> Unit,
    onLoans: () -> Unit,
    onMyLoans: () -> Unit,
    showNew: Boolean = true,
    isAuthenticated: Boolean = false
) {
    val gridState = rememberLazyGridState()
    Box(modifier = Modifier.fillMaxSize().verticalScrollbar(gridState)) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        state = gridState,
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { HomeTile(icon = Icons.Default.QrCodeScanner, label = "Details", onClick = onScanBarcode) }
        item { HomeTile(icon = Icons.Default.FactCheck, label = "Verify", onClick = onVerify) }
        item { HomeTile(icon = Icons.Default.NoteAdd, label = "New", onClick = onNewBarcode, enabled = showNew) }
        item { HomeTile(icon = Icons.Default.Inventory, label = "Items", onClick = onItems) }
        item { HomeTile(icon = Icons.Default.QrCode, label = "Barcodes", onClick = onBarcodes) }
        item { HomeTile(icon = Icons.Default.People, label = "Persons", onClick = onPersons, enabled = isAuthenticated) }
        item { HomeTile(icon = Icons.Default.List, label = "Loans", onClick = onLoans, enabled = isAuthenticated) }
        item { HomeTile(icon = Icons.Default.AccountCircle, label = "My Loans", onClick = onMyLoans, enabled = isAuthenticated) }
    }
    } // Box
}

@Composable
private fun HomeTile(icon: ImageVector, label: String, onClick: () -> Unit, enabled: Boolean = true) {
    val containerColor = if (enabled) MaterialTheme.colorScheme.primaryContainer
                         else MaterialTheme.colorScheme.surfaceVariant
    val contentColor = if (enabled) MaterialTheme.colorScheme.onPrimaryContainer
                       else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().aspectRatio(1f),
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            disabledContainerColor = containerColor
        )
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(52.dp),
                tint = contentColor
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = contentColor
            )
        }
    }
}
