package com.wificracker.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.wificracker.app.R
import com.wificracker.app.ui.navigation.BottomNavBar
import com.wificracker.app.ui.navigation.BottomNavTab
import com.wificracker.attack.ui.AttackDashboard
import com.wificracker.crack.ui.CrackDashboard
import com.wificracker.report.ui.ClientEditScreen
import com.wificracker.report.ui.ClientListScreen
import com.wificracker.report.ui.CompanyProfileScreen
import com.wificracker.report.ui.ReportDashboard
import com.wificracker.core.model.SelectedNetwork
import com.wificracker.core.service.SelectedNetworkRepository
import com.wificracker.scan.ui.ScanScreen
import com.wificracker.scan.ui.ScanViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainDashboard() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val isMainTab = currentRoute in BottomNavTab.entries.map { it.route }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = isMainTab,
        drawerContent = {
            DrawerContent(
                onNavigate = { route ->
                    scope.launch { drawerState.close() }
                    navController.navigate(route) {
                        launchSingleTop = true
                    }
                },
            )
        },
    ) {
        Scaffold(
            topBar = {
                if (isMainTab) {
                    TopAppBar(
                        title = { Text(stringResource(R.string.app_name)) },
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Default.Menu, contentDescription = "Menu")
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background,
                        ),
                    )
                }
            },
            bottomBar = {
                if (isMainTab) {
                    BottomNavBar(
                        currentRoute = currentRoute,
                        onTabSelected = { tab ->
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                    )
                }
            },
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = BottomNavTab.Scan.route,
                modifier = Modifier.padding(padding),
            ) {
                // Main tabs
                composable(BottomNavTab.Scan.route) {
                    ScanScreen(
                        onNetworkClick = { bssid ->
                            navController.navigate("network_detail/$bssid")
                        },
                    )
                }
                composable(BottomNavTab.Attack.route) { AttackDashboard() }
                composable(BottomNavTab.Crack.route) { CrackDashboard() }
                composable(BottomNavTab.Reports.route) { ReportDashboard() }

                // Network detail — récupère le réseau depuis le ScanViewModel partagé
                composable("network_detail/{bssid}") { backStackEntry ->
                    val bssid = backStackEntry.arguments?.getString("bssid") ?: return@composable
                    val scanBackStackEntry = remember(backStackEntry) {
                        navController.getBackStackEntry(BottomNavTab.Scan.route)
                    }
                    val scanViewModel: ScanViewModel = hiltViewModel(scanBackStackEntry)
                    val scanState by scanViewModel.uiState.collectAsState()
                    val network = scanState.scanResult.networks.find { it.bssid == bssid }
                    val vulnMatches = scanState.vulnMatches[bssid] ?: emptyList()

                    if (network != null) {
                        com.wificracker.scan.ui.NetworkDetailScreen(
                            network = network,
                            vulnMatches = vulnMatches,
                            onBack = { navController.popBackStack() },
                            onAttack = {
                                scanViewModel.selectNetwork(network.bssid)
                                navController.navigate(BottomNavTab.Attack.route) {
                                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                        )
                    } else {
                        NetworkDetailPlaceholder(bssid = bssid, onBack = { navController.popBackStack() })
                    }
                }

                // Drawer screens
                composable("company_profile") {
                    CompanyProfileScreen(onBack = { navController.popBackStack() })
                }
                composable("client_list") {
                    ClientListScreen(
                        onBack = { navController.popBackStack() },
                        onEditClient = { id -> navController.navigate("client_edit/$id") },
                        onAddClient = { navController.navigate("client_edit/0") },
                    )
                }
                composable("client_edit/{clientId}") { backStackEntry ->
                    val clientId = backStackEntry.arguments?.getString("clientId")?.toLongOrNull() ?: 0
                    ClientEditScreen(clientId = clientId, onBack = { navController.popBackStack() })
                }
                composable("about") {
                    AboutScreen(onBack = { navController.popBackStack() })
                }
                composable("settings") {
                    SettingsScreen(onBack = { navController.popBackStack() })
                }
                composable("vuln_database") {
                    VulnDatabaseScreen(onBack = { navController.popBackStack() })
                }
                composable("audit_log") {
                    AuditLogScreen(onBack = { navController.popBackStack() })
                }
                composable("wordlists") {
                    WordlistScreen(onBack = { navController.popBackStack() })
                }
            }
        }
    }
}

@Composable
private fun DrawerContent(onNavigate: (String) -> Unit) {
    ModalDrawerSheet(modifier = Modifier.verticalScroll(rememberScrollState())) {
        // Header with logo
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(R.drawable.logo_wificracker),
                contentDescription = null,
                modifier = Modifier.size(48.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = stringResource(R.string.drawer_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(R.string.drawer_version),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        HorizontalDivider()
        Spacer(modifier = Modifier.height(8.dp))

        // Drawer items
        DrawerItem(
            icon = Icons.Default.Business,
            label = stringResource(R.string.company_profile),
            onClick = { onNavigate("company_profile") },
        )
        DrawerItem(
            icon = Icons.Default.People,
            label = stringResource(R.string.saved_clients),
            onClick = { onNavigate("client_list") },
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        DrawerItem(
            icon = Icons.Default.Security,
            label = stringResource(R.string.vuln_database),
            onClick = { onNavigate("vuln_database") },
        )
        DrawerItem(
            icon = Icons.Default.History,
            label = stringResource(R.string.audit_log),
            onClick = { onNavigate("audit_log") },
        )
        DrawerItem(
            icon = Icons.AutoMirrored.Filled.List,
            label = stringResource(R.string.wordlists),
            onClick = { onNavigate("wordlists") },
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        DrawerItem(
            icon = Icons.Default.Info,
            label = stringResource(R.string.about),
            onClick = { onNavigate("about") },
        )
        DrawerItem(
            icon = Icons.Default.Settings,
            label = stringResource(R.string.settings),
            onClick = { onNavigate("settings") },
        )
    }
}

@Composable
private fun DrawerItem(icon: ImageVector, label: String, onClick: () -> Unit) {
    NavigationDrawerItem(
        icon = { Icon(icon, contentDescription = null) },
        label = { Text(label) },
        selected = false,
        onClick = onClick,
        modifier = Modifier.padding(horizontal = 12.dp),
    )
}

@Composable
private fun AboutScreen(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.logo_wificracker),
            contentDescription = null,
            modifier = Modifier.size(120.dp),
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = stringResource(R.string.drawer_version),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.about_description),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.about_license),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onBack) {
            Text(stringResource(R.string.about))
        }
    }
}

@Composable
private fun NetworkDetailPlaceholder(bssid: String, onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Network Detail", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Text(bssid, style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onBack) { Text("Back") }
    }
}
