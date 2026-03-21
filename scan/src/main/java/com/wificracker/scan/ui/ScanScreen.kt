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
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import androidx.hilt.navigation.compose.hiltViewModel
import com.wificracker.scan.R
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
                title = { Text(stringResource(R.string.scan_title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    when {
                        state.isStarting -> {} // ignore clicks while starting
                        state.isScanning -> viewModel.stopScan()
                        else -> viewModel.startScan()
                    }
                },
                containerColor = when {
                    state.isStarting -> MaterialTheme.colorScheme.surfaceVariant
                    state.isScanning -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.primary
                },
            ) {
                when {
                    state.isStarting -> CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 3.dp,
                    )
                    state.isScanning -> Icon(Icons.Default.Stop, contentDescription = stringResource(R.string.scan_stop))
                    else -> Icon(Icons.Default.PlayArrow, contentDescription = stringResource(R.string.scan_start))
                }
            }
        },
    ) { padding ->
        if (state.scanResult.networks.isEmpty() && !state.isScanning) {
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                ScanHeader(state = state, onInterfaceSelected = viewModel::selectInterface, onExportClick = viewModel::exportPcap)
                EmptyState(hasInterfaces = state.interfaces.isNotEmpty())
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(bottom = 80.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    ScanHeader(state = state, onInterfaceSelected = viewModel::selectInterface, onExportClick = viewModel::exportPcap)
                }
                items(
                    items = state.scanResult.networks.sortedByDescending { it.signalStrength },
                    key = { it.bssid },
                ) { network ->
                    NetworkCard(
                        network = network,
                        vulnCount = state.vulnMatches[network.bssid]?.size ?: 0,
                        isSelected = state.selectedBssid == network.bssid,
                        onClick = {
                            viewModel.selectNetwork(network.bssid)
                            onNetworkClick(network.bssid)
                        },
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ScanHeader(
    state: ScanUiState,
    onInterfaceSelected: (com.wificracker.core.wifi.WifiInterface) -> Unit,
    onExportClick: () -> Unit,
) {
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
                    Text(stringResource(R.string.scan_interface), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    InterfaceSelector(state = state, onSelected = onInterfaceSelected)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(stringResource(R.string.scan_status), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    Text(stringResource(R.string.scan_networks_count, state.scanResult.networks.size), style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.scan_clients_count, state.scanResult.clients.size), style = MaterialTheme.typography.bodySmall)
                    Text("${state.scanResult.duration / 1000}s", style = MaterialTheme.typography.bodySmall)
                }
            }

            if (state.isScanning) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("CH ${state.currentChannel}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        Text("${state.packetCount} pkts", style = MaterialTheme.typography.bodySmall)
                    }
                    IconButton(onClick = onExportClick) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = stringResource(R.string.scan_export),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            // Chipset info
            if (state.chipsetInfo.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = state.chipsetInfo,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!state.supportsInternalMonitor) {
                    Text(
                        text = stringResource(R.string.scan_monitor_not_supported),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.scan_monitor_supported),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
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
            Text(state.selectedInterface?.name ?: stringResource(R.string.scan_no_interface))
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
private fun EmptyState(hasInterfaces: Boolean) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.scan_no_networks), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (hasInterfaces) stringResource(R.string.scan_tap_play) else stringResource(R.string.scan_no_monitor_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
