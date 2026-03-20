package com.wificracker.scan.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wificracker.scan.R
import com.wificracker.scan.model.Network
import com.wificracker.scan.model.VulnMatch
import com.wificracker.scan.ui.components.ClientList
import com.wificracker.scan.ui.components.EncryptionBadge
import com.wificracker.scan.ui.components.SignalStrengthIndicator
import com.wificracker.scan.ui.components.VulnBadge

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkDetailScreen(
    network: Network,
    vulnMatches: List<VulnMatch> = emptyList(),
    onBack: () -> Unit = {},
    onAttack: () -> Unit = {},
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(network.ssid.ifBlank { stringResource(R.string.scan_hidden_network) }) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.scan_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Network Info Card
            NetworkInfoCard(network = network)

            // Vulnerabilities
            if (vulnMatches.isNotEmpty()) {
                VulnerabilitiesCard(vulnMatches = vulnMatches)
            }

            // Connected Clients
            if (network.clients.isNotEmpty()) {
                ClientsCard(network = network)
            }

            // Action buttons
            ActionButtons(onAttack = onAttack)
        }
    }
}

@Composable
private fun NetworkInfoCard(network: Network) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.network_info), style = MaterialTheme.typography.titleMedium)
            HorizontalDivider()
            InfoRow("BSSID", network.bssid)
            InfoRow("SSID", network.ssid.ifBlank { stringResource(R.string.scan_hidden_network) })
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Encryption", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                EncryptionBadge(encryption = network.encryption)
            }
            if (network.cipher.isNotBlank()) InfoRow("Cipher", network.cipher)
            if (network.authentication.isNotBlank()) InfoRow("Auth", network.authentication)
            InfoRow("Channel", network.channel.toString())
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Signal", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                SignalStrengthIndicator(dbm = network.signalStrength)
            }
            if (network.wps) InfoRow("WPS", "Enabled (vulnerable)")
        }
    }
}

@Composable
private fun VulnerabilitiesCard(vulnMatches: List<VulnMatch>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.network_vulns, vulnMatches.size), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
            }
            HorizontalDivider()
            vulnMatches.forEach { vuln ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            VulnBadge(severity = vuln.severity)
                            Text(text = "CVSS ${vuln.cvssScore}", style = MaterialTheme.typography.labelMedium)
                        }
                        Text(text = vuln.title, style = MaterialTheme.typography.bodyMedium)
                        Text(text = vuln.cveId, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = vuln.recommendation, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            }
        }
    }
}

@Composable
private fun ClientsCard(network: Network) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(stringResource(R.string.network_clients, network.clients.size), style = MaterialTheme.typography.titleMedium)
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            ClientList(clients = network.clients)
        }
    }
}

@Composable
private fun ActionButtons(onAttack: () -> Unit) {
    Button(
        onClick = onAttack,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
    ) {
        Icon(Icons.Default.Security, contentDescription = null)
        Spacer(modifier = Modifier.padding(4.dp))
        Text(stringResource(R.string.network_launch_attack))
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
