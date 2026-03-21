package com.wificracker.attack.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.wificracker.attack.R
import com.wificracker.attack.model.AttackStatus
import com.wificracker.attack.model.AttackType
import com.wificracker.attack.ui.components.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttackDashboard(viewModel: AttackViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.attack_title)) }, colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { if (state.isRunning) viewModel.stopAttack() else viewModel.launchAttack() },
                containerColor = if (state.isRunning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            ) { Icon(if (state.isRunning) Icons.Default.Stop else Icons.Default.PlayArrow, contentDescription = null) }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (state.hasPreselectedTarget) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.GpsFixed, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(state.targetSsid.ifBlank { "[Hidden]" }, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                            Text(state.targetBssid, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                }
            }
            Text(stringResource(R.string.attack_select_type), style = MaterialTheme.typography.titleMedium)
            AttackType.entries.forEach { type ->
                AttackTypeCard(type = type, isSelected = state.selectedAttackType == type, onClick = { viewModel.selectAttackType(type) })
            }
            Text(stringResource(R.string.attack_target), style = MaterialTheme.typography.titleMedium)
            TargetSelector(bssid = state.targetBssid, ssid = state.targetSsid, onBssidChange = { viewModel.setTarget(it, state.targetSsid) }, onSsidChange = { viewModel.setTarget(state.targetBssid, it) })
            if (state.consoleLines.isNotEmpty()) {
                Text(stringResource(R.string.attack_console), style = MaterialTheme.typography.titleMedium)
                LiveConsole(lines = state.consoleLines)
            }
            if (state.attackStatus == AttackStatus.COMPLETED) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Text(stringResource(R.string.attack_completed), modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyLarge)
                }
            }
            if (state.attackStatus == AttackStatus.FAILED) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Text(stringResource(R.string.attack_failed), modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
