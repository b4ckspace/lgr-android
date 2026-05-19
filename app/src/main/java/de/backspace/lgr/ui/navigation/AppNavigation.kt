package de.backspace.lgr.ui.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.ui.unit.dp
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
import de.backspace.lgr.ui.screens.*
import de.backspace.lgr.viewmodel.AppViewModel

private sealed class Screen(val route: String, val title: String, val icon: ImageVector, val enabled: Boolean = true) {
    object Home : Screen("home", "Home", Icons.Default.Home)
    object Items : Screen("items", "Items", Icons.Default.Inventory)
    object Barcodes : Screen("barcodes", "Barcodes", Icons.Default.QrCode)
    object Persons : Screen("persons", "Persons", Icons.Default.People)
    object Loans : Screen("loans", "Loans", Icons.Default.List, enabled = false)
    object MyLoans : Screen("my_loans", "My Loans", Icons.Default.AccountCircle, enabled = false)
}

// Routes where the shared top bar is hidden (screens have their own TopAppBar or are camera-only)
private val fullScreenRoutes = setOf("scan", "barcode_detail", "content_scan", "scan_parent", "add_content_scan", "new_barcode", "new_barcode_scan_parent", "new_barcode_scan_code", "verify_scan", "verify_detail", "barcodes_scan_search", "item_detail", "edit_barcode", "edit_item", "edit_barcode_scan_parent")

// Camera/scanner screens where even the bottom bar is hidden (need full screen for viewfinder)
private val cameraRoutes = setOf("scan", "content_scan", "scan_parent", "add_content_scan", "new_barcode_scan_parent", "new_barcode_scan_code", "verify_scan", "barcodes_scan_search", "edit_barcode_scan_parent")

// Maps every route to its logical parent tab so the tab stays highlighted on sub-pages
private fun activeTabFor(route: String?): Screen? = when (route) {
    "home", "scan", "verify_scan" -> Screen.Home
    "items", "item_detail", "edit_item" -> Screen.Items
    "barcodes", "barcode_detail", "edit_barcode", "edit_barcode_scan_parent",
    "content_scan", "scan_parent", "add_content_scan",
    "new_barcode", "new_barcode_scan_parent", "new_barcode_scan_code",
    "barcodes_scan_search", "verify_detail" -> Screen.Barcodes
    "persons" -> Screen.Persons
    "loans" -> Screen.Loans
    "my_loans" -> Screen.MyLoans
    else -> null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation(viewModel: AppViewModel) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    val showChrome = fullScreenRoutes.none { currentRoute?.startsWith(it) == true }
    val showBottomBar = cameraRoutes.none { currentRoute?.startsWith(it) == true }
    val activeTab = activeTabFor(currentRoute)

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
                    title = { Text(activeTab?.title ?: "LGR") },
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
            if (showBottomBar) {
                Column {
                NavigationBar(
                    modifier = Modifier.height(48.dp),
                    windowInsets = WindowInsets(0, 0, 0, 0)
                ) {
                    tabs.forEach { screen ->
                        val isEnabled = screen.enabled &&
                            (screen != Screen.Persons || viewModel.isAuthenticated)
                        NavigationBarItem(
                            icon = { Icon(screen.icon, contentDescription = screen.title) },
                            selected = screen == activeTab,
                            enabled = isEnabled,
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
                Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                } // Column
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
                    onVerify = {
                        viewModel.clearVerifyState()
                        navController.navigate("verify_scan")
                    },
                    showNew = !viewModel.readonlyMode
                )
            }
            composable(Screen.Items.route) {
                ItemsScreen(
                    viewModel = viewModel,
                    onOpenDetail = { list, index ->
                        viewModel.openItemFromList(list, index)
                        navController.navigate("item_detail")
                    }
                )
            }
            composable(Screen.Barcodes.route) {
                BarcodesScreen(
                    viewModel = viewModel,
                    onOpenDetail = { list, index ->
                        viewModel.openBarcodeFromList(list, index)
                        navController.navigate("barcode_detail")
                    },
                    onScanSearch = { navController.navigate("barcodes_scan_search") }
                )
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
                    },
                    onNewBarcode = {
                        viewModel.clearNewBarcodeState()
                        navController.navigate("new_barcode")
                    },
                    onEditBarcode = {
                        val barcode = viewModel.scannedBarcode.data ?: return@BarcodeDetailScreen
                        viewModel.enterBarcodeEditMode(barcode)
                        navController.navigate("edit_barcode")
                    },
                    onItemClick = {
                        val barcode = viewModel.scannedBarcode.data ?: return@BarcodeDetailScreen
                        val item = de.backspace.lgr.data.model.Item(
                            url = barcode.item,
                            name = barcode.itemName,
                            description = barcode.itemDescription,
                            tags = emptyList()
                        )
                        viewModel.openItemDetail(item)
                        navController.navigate("item_detail")
                    }
                )
            }
            composable("item_detail") {
                ItemDetailScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onBarcodeClick = { barcode ->
                        viewModel.loadBarcode(barcode.code)
                        navController.navigate("barcode_detail")
                    },
                    onEditItem = {
                        val item = viewModel.currentItem ?: return@ItemDetailScreen
                        viewModel.enterItemEditMode(item)
                        navController.navigate("edit_item")
                    }
                )
            }
            composable("edit_item") {
                EditItemScreen(
                    viewModel = viewModel,
                    onBack = {
                        viewModel.clearItemEditState()
                        navController.popBackStack()
                    },
                    onSaved = {
                        viewModel.clearItemEditState()
                        navController.popBackStack()
                    }
                )
            }
            composable("edit_barcode") {
                EditBarcodeScreen(
                    viewModel = viewModel,
                    onBack = {
                        viewModel.clearBarcodeEditState()
                        navController.popBackStack()
                    },
                    onSaved = {
                        viewModel.clearBarcodeEditState()
                        navController.popBackStack()
                    },
                    onScanParent = { navController.navigate("edit_barcode_scan_parent") }
                )
            }
            composable("edit_barcode_scan_parent") {
                ScanParentScreen(
                    viewModel = viewModel,
                    onParentScanned = {
                        viewModel.onEditBarcodeParentScanned()
                        navController.popBackStack()
                    },
                    onBack = { navController.popBackStack() },
                    selfCode = viewModel.scannedBarcode.data?.code
                )
            }
            composable("content_scan") {
                ContentScanScreen(
                    viewModel = viewModel,
                    onDone = { viewModel.onContentScanDone(); navController.popBackStack() },
                    onCancel = { viewModel.cancelContentScan(); navController.popBackStack() }
                )
            }
            composable("scan_parent") {
                ScanParentScreen(
                    viewModel = viewModel,
                    onParentScanned = { navController.popBackStack() },
                    onBack = { navController.popBackStack() },
                    selfCode = viewModel.scannedBarcode.data?.code
                )
            }
            composable("add_content_scan") {
                ContentScanScreen(
                    viewModel = viewModel,
                    onDone = { viewModel.onContentScanDone(); navController.popBackStack() },
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
                    onBack = { navController.popBackStack() },
                    selfCode = viewModel.newBarcodeCode.takeIf { it.isNotBlank() }
                )
            }
            composable("barcodes_scan_search") {
                ScanBarcodeSearchScreen(
                    viewModel = viewModel,
                    onScanned = { code ->
                        viewModel.updateBarcodesSearch(code)
                        navController.popBackStack()
                    },
                    onBack = { navController.popBackStack() }
                )
            }
            composable("verify_scan") {
                VerifyScanScreen(
                    viewModel = viewModel,
                    onDone = { navController.navigate("verify_detail") },
                    onBack = { viewModel.clearVerifyState(); navController.popBackStack() }
                )
            }
            composable("verify_detail") {
                val navigateToBarcodeDetail: () -> Unit = {
                    val code = viewModel.verifyLocation?.code
                    viewModel.clearVerifyState()
                    if (code != null) {
                        viewModel.clearBarcodeListContext()
                        viewModel.loadBarcode(code)
                        navController.navigate("barcode_detail") {
                            popUpTo("home") { inclusive = false }
                        }
                    } else {
                        navController.popBackStack("home", inclusive = false)
                    }
                }
                VerifyBarcodeScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onRescan = { navController.popBackStack() },
                    onCancel = navigateToBarcodeDetail,
                    onSaved = navigateToBarcodeDetail,
                    onVerifyNext = {
                        viewModel.clearVerifyState()
                        navController.popBackStack()
                    },
                    onOk = navigateToBarcodeDetail,
                    onBarcodeClick = { code ->
                        viewModel.clearBarcodeListContext()
                        viewModel.loadBarcode(code)
                        navController.navigate("barcode_detail")
                    },
                    onItemClick = {
                        val location = viewModel.verifyLocation ?: return@VerifyBarcodeScreen
                        val item = de.backspace.lgr.data.model.Item(
                            url = location.item,
                            name = location.itemName,
                            description = location.itemDescription,
                            tags = emptyList()
                        )
                        viewModel.openItemDetail(item)
                        navController.navigate("item_detail")
                    }
                )
            }
        }
    }
}
