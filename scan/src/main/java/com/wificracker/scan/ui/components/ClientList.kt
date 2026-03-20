package com.wificracker.scan.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wificracker.scan.model.Client

@Composable
fun ClientList(clients: List<Client>, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        clients.forEachIndexed { index, client ->
            ClientRow(client = client)
            if (index < clients.lastIndex) {
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            }
        }
    }
}

@Composable
private fun ClientRow(client: Client) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = client.macAddress,
                style = MaterialTheme.typography.bodyMedium,
            )
            if (client.vendor != "Unknown") {
                Text(
                    text = client.vendor,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (client.probeRequests.isNotEmpty()) {
                Text(
                    text = "Probes: ${client.probeRequests.joinToString(", ")}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            SignalStrengthIndicator(dbm = client.signalStrength)
            Text(
                text = "${client.packets} pkts",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
