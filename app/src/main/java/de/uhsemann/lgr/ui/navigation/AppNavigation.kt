package de.uhsemann.lgr.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import de.uhsemann.lgr.ui.screens.*
import de.uhsemann.lgr.viewmodel.AppViewModel

private sealed class Screen(val route: String, val label: String, val icon: ImageVector, val enabled: Boolean = true) {
    object Home : Screen("home", "Home\n ", Icons.Default.Home)
    object Items : Screen("items", "Items\n ", Icons.Default.Inventory)
    object Barcodes : Screen("barcodes", "Bar-\ncodes", Icons.Default.QrCode)
    object Persons : Screen("persons", "Per-\nsons", Icons.Default.People, enabled = false)
    object Loans : Screen("loans", "Loans\n ", Icons.Default.List, enabled = false)
    object MyLoans : Screen("my_loans", "My\nLoans", Icons.Default.AccountCircle, enabled = false)
}

private val fullScreenRoutes = setOf("scan", "barcode_detail", "content_scan", "scan_parent", "add_content_scan", "new_barcode", "new_barcode_scan_parent", "new_barcode_scan_code")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation(viewModel: AppViewModel) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    val showChrome = fullScreenRoutes.none { currentRoute?.startsWith(it) == true }

    val tabs = buildList {
        add(Screen.Home)
        add(Screen.Items)
        add(Screen.Barcodes)
        add(Screen.Persons)
        add(Screen.Loans)
        if (viewModel.isAuthenticated) add(Screen.MyLoans)
    }

    Scaffold(
        topBar = {
            if (showChrome) {
                TopAppBar(
                    title = { Text(if (viewModel.username.isNullOrBlank()) "LGR" else "LGR — ${viewModel.username}") },
                    actions = {
                        if (viewModel.readonlyMode) {
                            TextButton(onClick = { viewModel.exitReadonlyMode() }) { Text("Login") }
                        } else {
                            TextButton(onClick = { viewModel.logout() }) { Text("Logout") }
                        }
                    }
                )
            }
        },
        bottomBar = {
            if (showChrome) {
                NavigationBar {
                    tabs.forEach { screen ->
                        NavigationBarItem(
                            icon = { Icon(screen.icon, contentDescription = screen.label) },
                            label = { Text(screen.label) },
                            selected = currentRoute == screen.route,
                            enabled = screen.enabled,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(padding)
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    onScanBarcode = {
                        viewModel.clearScannedBarcode()
                        viewModel.clearBarcodeListContext()
                        navController.navigate("scan")
                    },
                    onNewBarcode = {
                        viewModel.clearNewBarcodeState()
                        navController.navigate("new_barcode")
                    },
                    showNew = !viewModel.readonlyMode
                )
            }
            composable(Screen.Items.route) { ItemsScreen(viewModel) }
            composable(Screen.Barcodes.route) {
                BarcodesScreen(viewModel, onOpenDetail = { list, index ->
                    viewModel.openBarcodeFromList(list, index)
                    navController.navigate("barcode_detail")
                })
            }
            composable(Screen.Persons.route) { PersonsScreen(viewModel) }
            composable(Screen.Loans.route) { LoansScreen(viewModel) }
            composable(Screen.MyLoans.route) { MyLoansScreen(viewModel) }
            composable("scan") {
                BarcodeScanScreen(
                    viewModel = viewModel,
                    onBarcodeDetected = { navController.navigate("barcode_detail") },
                    onBack = { navController.popBackStack() }
                )
            }
            composable("barcode_detail") {
                BarcodeDetailScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onScanContent = { navController.navigate("content_scan") },
                    onScanParent = { navController.navigate("scan_parent") },
                    onAddContent = {
                        viewModel.startAddContentScan()
                        navController.navigate("add_content_scan")
                    }
                )
            }
            composable("content_scan") {
                ContentScanScreen(
                    viewModel = viewModel,
                    onDone = { navController.popBackStack() }
                )
            }
            composable("scan_parent") {
                ScanParentScreen(
                    viewModel = viewModel,
                    onParentScanned = { navController.popBackStack() },
                    onBack = { navController.popBackStack() }
                )
            }
            composable("add_content_scan") {
                ContentScanScreen(
                    viewModel = viewModel,
                    onDone = { navController.popBackStack() },
                    addOnly = true
                )
            }
            composable("new_barcode") {
                NewBarcodeScreen(
                    viewModel = viewModel,
                    onBack = {
                        viewModel.clearNewBarcodeState()
                        navController.popBackStack()
                    },
                    onScanCode = { navController.navigate("new_barcode_scan_code") },
                    onScanParent = { navController.navigate("new_barcode_scan_parent") },
                    onCreated = {
                        navController.navigate("barcode_detail") {
                            popUpTo("new_barcode") { inclusive = true }
                        }
                    }
                )
            }
            composable("new_barcode_scan_code") {
                ScanNewBarcodeScreen(
                    viewModel = viewModel,
                    onScanned = { code ->
                        viewModel.onNewBarcodeCodeScanned(code)
                        navController.popBackStack()
                    },
                    onBack = { navController.popBackStack() }
                )
            }
            composable("new_barcode_scan_parent") {
                ScanParentScreen(
                    viewModel = viewModel,
                    onParentScanned = {
                        viewModel.onNewBarcodeParentScanned()
                        navController.popBackStack()
                    },
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
