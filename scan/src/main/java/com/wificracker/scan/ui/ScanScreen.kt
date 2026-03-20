package com.wificracker.scan.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.wificracker.scan.model.ScanStatus
import com.wificracker.scan.ui.components.NetworkCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(
    viewModel: ScanViewModel = hiltViewModel(),
    onNetworkClick: (String) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("WiFi Scanner") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (state.isScanning) viewModel.stopScan() else viewModel.startScan()
                },
                containerColor = if (state.isScanning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            ) {
                Icon(
                    imageVector = if (state.isScanning) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = if (state.isScanning) "Stop scan" else "Start scan",
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // Interface selector + scan info
            ScanHeader(
                state = state,
                onInterfaceSelected = viewModel::selectInterface,
            )

            // Network list
            if (state.scanResult.networks.isEmpty() && !state.isScanning) {
                EmptyState()
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(
                        items = state.scanResult.networks.sortedByDescending { it.signalStrength },
                        key = { it.bssid },
                    ) { network ->
                        NetworkCard(
                            network = network,
                            vulnCount = state.vulnMatches[network.bssid]?.size ?: 0,
                            onClick = { onNetworkClick(network.bssid) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScanHeader(state: ScanUiState, onInterfaceSelected: (com.wificracker.core.wifi.WifiInterface) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("Interface", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    InterfaceSelector(state = state, onSelected = onInterfaceSelected)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Status", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = state.scanResult.status.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = when (state.scanResult.status) {
                            ScanStatus.SCANNING -> MaterialTheme.colorScheme.primary
                            ScanStatus.COMPLETED -> MaterialTheme.colorScheme.secondary
                            ScanStatus.FAILED -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                    )
                }
            }

            if (state.isScanning || state.scanResult.networks.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("${state.scanResult.networks.size} networks", style = MaterialTheme.typography.bodySmall)
                    Text("${state.scanResult.clients.size} clients", style = MaterialTheme.typography.bodySmall)
                    Text("${state.scanResult.duration / 1000}s", style = MaterialTheme.typography.bodySmall)
                }
            }

            state.errorMessage?.let { error ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun InterfaceSelector(state: ScanUiState, onSelected: (com.wificracker.core.wifi.WifiInterface) -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        OutlinedButton(onClick = { expanded = true }) {
            Text(state.selectedInterface?.name ?: "No interface")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            state.interfaces.forEach { iface ->
                DropdownMenuItem(
                    text = { Text("${iface.name} (${iface.chipset})") },
                    onClick = { onSelected(iface); expanded = false },
                )
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("No networks discovered", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Tap the play button to start scanning", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
