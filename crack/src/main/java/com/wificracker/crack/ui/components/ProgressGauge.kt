package com.wificracker.crack.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wificracker.crack.R
import com.wificracker.crack.model.CrackProgress

@Composable
fun ProgressGauge(progress: CrackProgress, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.crack_progress), style = MaterialTheme.typography.titleMedium)
                Text("${(progress.progress * 100).toInt()}%", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            }
            LinearProgressIndicator(progress = { progress.progress }, modifier = Modifier.fillMaxWidth().height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column { Text(stringResource(R.string.crack_speed), style = MaterialTheme.typography.labelSmall); Text("${progress.keysPerSecond} keys/s", style = MaterialTheme.typography.bodyMedium) }
                Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(stringResource(R.string.crack_tested), style = MaterialTheme.typography.labelSmall); Text("${progress.keysTested}", style = MaterialTheme.typography.bodyMedium) }
                Column(horizontalAlignment = Alignment.End) { Text(stringResource(R.string.crack_eta), style = MaterialTheme.typography.labelSmall); Text(progress.eta.ifBlank { "--" }, style = MaterialTheme.typography.bodyMedium) }
            }
            if (progress.currentKey.isNotBlank()) { Text("Current: ${progress.currentKey}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary) }
            if (progress.message.isNotBlank()) { Text(progress.message, style = MaterialTheme.typography.bodySmall) }
        }
    }
}
