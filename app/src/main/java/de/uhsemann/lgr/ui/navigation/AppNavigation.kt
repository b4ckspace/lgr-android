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

private sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Items : Screen("items", "Items", Icons.Default.Inventory)
    object Barcodes : Screen("barcodes", "Barcodes", Icons.Default.QrCode)
    object Persons : Screen("persons", "Persons", Icons.Default.People)
    object Loans : Screen("loans", "Loans", Icons.Default.List)
    object MyLoans : Screen("my_loans", "My Loans", Icons.Default.AccountCircle)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation(viewModel: AppViewModel) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    val tabs = buildList {
        add(Screen.Items)
        add(Screen.Barcodes)
        add(Screen.Persons)
        add(Screen.Loans)
        if (viewModel.isAuthenticated) add(Screen.MyLoans)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("LGR — ${viewModel.username ?: ""}") },
                actions = {
                    TextButton(onClick = { viewModel.logout() }) {
                        Text("Logout")
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                tabs.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.label) },
                        label = { Text(screen.label) },
                        selected = currentRoute == screen.route,
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
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Items.route,
            modifier = Modifier.padding(padding)
        ) {
            composable(Screen.Items.route) { ItemsScreen(viewModel) }
            composable(Screen.Barcodes.route) { BarcodesScreen(viewModel) }
            composable(Screen.Persons.route) { PersonsScreen(viewModel) }
            composable(Screen.Loans.route) { LoansScreen(viewModel) }
            composable(Screen.MyLoans.route) { MyLoansScreen(viewModel) }
        }
    }
}
