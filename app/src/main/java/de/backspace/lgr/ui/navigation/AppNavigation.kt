// SPDX-FileCopyrightText: 2026 Andreas Uhsemann
// SPDX-License-Identifier: Apache-2.0

package de.backspace.lgr.ui.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
    object Loans : Screen("loans", "Loans", Icons.Default.List)
    object MyLoans : Screen("my_loans", "My Loans", Icons.Default.AccountCircle)
}

// Routes where the shared top bar is hidden (screens have their own TopAppBar or are camera-only)
private val fullScreenRoutes = setOf("scan", "barcode_detail", "content_scan", "scan_parent", "add_content_scan", "new_barcode", "new_barcode_scan_parent", "new_barcode_scan_code", "verify_scan", "verify_detail", "barcodes_scan_search", "item_detail", "edit_barcode", "edit_item", "edit_barcode_scan_parent", "edit_barcode_scan_code", "loan_cart", "loan_detail", "person_detail", "edit_person", "new_person")

// Camera/scanner screens where even the bottom bar is hidden (need full screen for viewfinder)
private val cameraRoutes = setOf("scan", "content_scan", "scan_parent", "add_content_scan", "new_barcode_scan_parent", "new_barcode_scan_code", "verify_scan", "barcodes_scan_search", "edit_barcode_scan_parent", "edit_barcode_scan_code")

// Maps every route to its logical parent tab so the tab stays highlighted on sub-pages.
// A loan detail keeps the tab it was opened from (Loans vs My Loans).
private fun activeTabFor(route: String?, loanIsMyLoan: Boolean = false): Screen? = when (route) {
    "home", "scan", "verify_scan" -> Screen.Home
    "items", "item_detail", "edit_item" -> Screen.Items
    "barcodes", "barcode_detail", "edit_barcode", "edit_barcode_scan_parent", "edit_barcode_scan_code",
    "content_scan", "scan_parent", "add_content_scan",
    "new_barcode", "new_barcode_scan_parent", "new_barcode_scan_code",
    "barcodes_scan_search", "verify_detail" -> Screen.Barcodes
    "persons", "person_detail", "edit_person", "new_person" -> Screen.Persons
    "loan_detail" -> if (loanIsMyLoan) Screen.MyLoans else Screen.Loans
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
    val activeTab = activeTabFor(currentRoute, viewModel.currentLoanOriginMyLoans)

    // Track the last non-root, non-camera route visited per tab so that
    // switching away from a deeper screen and back restores it.
    val tabLastRoutes = remember { HashMap<Screen, String>() }
    LaunchedEffect(currentRoute) {
        val route = currentRoute ?: return@LaunchedEffect
        if (cameraRoutes.any { route.startsWith(it) }) return@LaunchedEffect
        val tab = activeTabFor(route, viewModel.currentLoanOriginMyLoans) ?: return@LaunchedEffect
        if (route == tab.route) tabLastRoutes.remove(tab) else tabLastRoutes[tab] = route
    }

    val tabs = buildList {
        add(Screen.Home)
        add(Screen.Items)
        add(Screen.Barcodes)
        add(Screen.Persons)
        add(Screen.Loans)
        add(Screen.MyLoans)
    }

    Scaffold(
        topBar = {
            if (showChrome) {
                TopAppBar(
                    title = { Text(activeTab?.title ?: "LGR") },
                    actions = {
                        if (viewModel.selectedBarcodes.isNotEmpty()) {
                            BadgedBox(
                                badge = { Badge { Text(viewModel.selectedBarcodes.size.toString()) } }
                            ) {
                                IconButton(onClick = { navController.navigate("loan_cart") }) {
                                    Icon(Icons.Default.ShoppingCart, contentDescription = "Loan cart")
                                }
                            }
                        }
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
                            (screen !in setOf(Screen.Persons, Screen.Loans, Screen.MyLoans) || viewModel.isAuthenticated)
                        NavigationBarItem(
                            icon = { Icon(screen.icon, contentDescription = screen.title) },
                            selected = screen == activeTab,
                            enabled = isEnabled,
                            onClick = {
                                val restored = if (screen != activeTab) tabLastRoutes[screen] else null
                                var targetRoute = restored ?: screen.route
                                // loan_detail is shared between the Loans and My Loans tabs; swap in
                                // that tab's own open loan before showing it, or fall back to the list
                                // if that tab no longer has a loan open.
                                if (targetRoute == "loan_detail") {
                                    val ok = viewModel.restoreLoanTab(fromMyLoans = screen == Screen.MyLoans)
                                    if (!ok) targetRoute = screen.route
                                }
                                // Only restore the verify result while its scan state is still
                                // alive; once cleared (backed out), fall back to the tab list so
                                // it doesn't reopen an empty "No location scanned." screen.
                                if (targetRoute == "verify_detail" && viewModel.verifyLocation == null) {
                                    targetRoute = screen.route
                                }
                                if (targetRoute != screen.route) {
                                    // Restore a sub-screen: put the tab root in the back stack
                                    // first so the back arrow returns to the tab list.
                                    navController.navigate(screen.route) {
                                        popUpTo(navController.graph.startDestinationId)
                                        launchSingleTop = true
                                    }
                                    navController.navigate(targetRoute)
                                } else {
                                    navController.navigate(targetRoute) {
                                        popUpTo(navController.graph.startDestinationId)
                                        launchSingleTop = true
                                    }
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
            modifier = Modifier.padding(padding).consumeWindowInsets(padding)
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
                    onItems = {
                        navController.navigate(Screen.Items.route) {
                            popUpTo(navController.graph.startDestinationId)
                            launchSingleTop = true
                        }
                    },
                    onBarcodes = {
                        navController.navigate(Screen.Barcodes.route) {
                            popUpTo(navController.graph.startDestinationId)
                            launchSingleTop = true
                        }
                    },
                    onPersons = {
                        navController.navigate(Screen.Persons.route) {
                            popUpTo(navController.graph.startDestinationId)
                            launchSingleTop = true
                        }
                    },
                    onLoans = {
                        navController.navigate(Screen.Loans.route) {
                            popUpTo(navController.graph.startDestinationId)
                            launchSingleTop = true
                        }
                    },
                    onMyLoans = {
                        navController.navigate(Screen.MyLoans.route) {
                            popUpTo(navController.graph.startDestinationId)
                            launchSingleTop = true
                        }
                    },
                    showNew = !viewModel.readonlyMode,
                    isAuthenticated = viewModel.isAuthenticated
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
                    onScanSearch = { navController.navigate("barcodes_scan_search") },
                    onNewBarcode = {
                        viewModel.clearNewBarcodeState()
                        navController.navigate("new_barcode")
                    }
                )
            }
            composable(Screen.Persons.route) {
                PersonsScreen(
                    viewModel = viewModel,
                    onOpenDetail = { list, index ->
                        viewModel.openPersonFromList(list, index)
                        navController.navigate("person_detail")
                    },
                    onNew = {
                        viewModel.prepareNewPerson()
                        navController.navigate("new_person")
                    }
                )
            }
            composable(Screen.Loans.route) {
                LoansScreen(viewModel, onOpenDetail = { list, index ->
                    viewModel.openLoanFromList(list, index, fromMyLoans = false)
                    navController.navigate("loan_detail")
                })
            }
            composable(Screen.MyLoans.route) {
                MyLoansScreen(viewModel, onOpenDetail = { list, index ->
                    viewModel.openLoanFromList(list, index, fromMyLoans = true)
                    navController.navigate("loan_detail")
                })
            }
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
                        val locationCode = viewModel.scannedBarcode.data?.apiParentNames?.lastOrNull()?.code ?: ""
                        viewModel.prepareNewBarcodeAsChild(locationCode)
                        viewModel.setNewBarcodeSource(viewModel.scannedBarcode.data?.code)
                        navController.navigate("new_barcode")
                    },
                    onEditBarcode = {
                        val barcode = viewModel.scannedBarcode.data ?: return@BarcodeDetailScreen
                        viewModel.enterBarcodeEditMode(barcode)
                        navController.navigate("edit_barcode")
                    },
                    onGoToCart = { navController.navigate("loan_cart") },
                    onNewBarcodeAsChild = {
                        val code = viewModel.scannedBarcode.data?.code ?: return@BarcodeDetailScreen
                        viewModel.prepareNewBarcodeAsChild(code)
                        viewModel.setNewBarcodeSource(code)
                        navController.navigate("new_barcode")
                    },
                    onItemClick = {
                        val barcode = viewModel.scannedBarcode.data ?: return@BarcodeDetailScreen
                        val item = de.backspace.lgr.data.model.Item(
                            url = barcode.item,
                            name = barcode.itemName,
                            description = barcode.itemDescription,
                            tags = emptyList(),
                            image = barcode.itemImage
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
            composable("person_detail") {
                PersonDetailScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onEditPerson = {
                        val person = viewModel.currentPerson ?: return@PersonDetailScreen
                        viewModel.enterPersonEditMode(person)
                        navController.navigate("edit_person")
                    }
                )
            }
            composable("edit_person") {
                EditPersonScreen(
                    viewModel = viewModel,
                    onBack = {
                        viewModel.clearPersonEditState()
                        navController.popBackStack()
                    },
                    onSaved = {
                        viewModel.clearPersonEditState()
                        navController.popBackStack()
                    }
                )
            }
            composable("new_person") {
                NewPersonScreen(
                    viewModel = viewModel,
                    onBack = {
                        viewModel.clearNewPersonState()
                        navController.popBackStack()
                    },
                    onCreated = {
                        viewModel.clearNewPersonState()
                        navController.popBackStack()
                        navController.navigate("person_detail")
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
                    onScanParent = { navController.navigate("edit_barcode_scan_parent") },
                    onScanNewCode = { navController.navigate("edit_barcode_scan_code") }
                )
            }
            composable("edit_barcode_scan_code") {
                ScanNewBarcodeScreen(
                    viewModel = viewModel,
                    onScanned = { code ->
                        viewModel.onEditBarcodeNewCodeScanned(code)
                        navController.popBackStack()
                    },
                    onBack = { navController.popBackStack() }
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
                        if (viewModel.newBarcodeSourceDetail != null) {
                            // Created from a detail: return to that existing detail entry (now
                            // showing the new barcode, with the source pushed onto history).
                            navController.popBackStack()
                        } else {
                            navController.navigate("barcode_detail") {
                                popUpTo("new_barcode") { inclusive = true }
                            }
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
            composable("loan_cart") {
                LoanCartScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onBarcodeClick = { code ->
                        viewModel.clearBarcodeListContext()
                        viewModel.loadBarcode(code)
                        navController.navigate("barcode_detail")
                    },
                    onConfirmed = {
                        navController.navigate(Screen.MyLoans.route) {
                            popUpTo(navController.graph.startDestinationId)
                            launchSingleTop = true
                        }
                    }
                )
            }
            composable("loan_detail") {
                LoanDetailScreen(
                    viewModel = viewModel,
                    onBack = { viewModel.closeLoanDetail(); navController.popBackStack() },
                    onBarcodeClick = { code ->
                        viewModel.clearBarcodeListContext()
                        viewModel.loadBarcode(code)
                        navController.navigate("barcode_detail")
                    }
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
                    onBack = { viewModel.clearVerifyState(); navController.popBackStack() },
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
                            tags = emptyList(),
                            image = location.itemImage
                        )
                        viewModel.openItemDetail(item)
                        navController.navigate("item_detail")
                    }
                )
            }
        }
    }
}
