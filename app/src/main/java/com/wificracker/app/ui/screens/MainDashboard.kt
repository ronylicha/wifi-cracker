package com.wificracker.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.wificracker.app.ui.navigation.BottomNavBar
import com.wificracker.app.ui.navigation.BottomNavTab
import com.wificracker.attack.ui.AttackDashboard
import com.wificracker.crack.ui.CrackDashboard
import com.wificracker.report.ui.ReportDashboard
import com.wificracker.scan.ui.ScanScreen

@Composable
fun MainDashboard() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
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
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = BottomNavTab.Scan.route,
            modifier = Modifier.padding(padding),
        ) {
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
            composable("network_detail/{bssid}") { backStackEntry ->
                val bssid = backStackEntry.arguments?.getString("bssid") ?: return@composable
                // For now, get network from ScanEngine state (will be improved later)
                // Simple placeholder that shows the BSSID
                NetworkDetailPlaceholder(bssid = bssid, onBack = { navController.popBackStack() })
            }
        }
    }
}

@Composable
private fun PlaceholderScreen(name: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = name, style = MaterialTheme.typography.headlineLarge)
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
