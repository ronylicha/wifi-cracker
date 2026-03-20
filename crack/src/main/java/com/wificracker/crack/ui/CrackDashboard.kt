package com.wificracker.crack.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.wificracker.crack.model.CrackStrategy
import com.wificracker.crack.model.CrackStatus
import com.wificracker.crack.ui.components.ProgressGauge

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrackDashboard(viewModel: CrackViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Crack") }, colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { if (state.isRunning) viewModel.stopCrack() else viewModel.startCrack() },
                containerColor = if (state.isRunning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            ) { Icon(if (state.isRunning) Icons.Default.Stop else Icons.Default.PlayArrow, contentDescription = null) }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedTextField(value = state.capturePath, onValueChange = { viewModel.setCapture(it) }, label = { Text("Capture file path") }, singleLine = true, modifier = Modifier.fillMaxWidth())

            Text("Strategy", style = MaterialTheme.typography.titleMedium)
            CrackStrategy.entries.forEach { strategy ->
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { viewModel.selectStrategy(strategy) },
                    colors = CardDefaults.cardColors(containerColor = if (state.selectedStrategy == strategy) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(strategy.label, style = MaterialTheme.typography.titleMedium, color = if (state.selectedStrategy == strategy) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                        Text(strategy.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            if (state.selectedStrategy == CrackStrategy.DICTIONARY || state.selectedStrategy == CrackStrategy.RULE_BASED) {
                Text("Wordlist", style = MaterialTheme.typography.titleMedium)
                state.wordlists.forEach { wl ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { viewModel.selectWordlist(wl) },
                        colors = CardDefaults.cardColors(containerColor = if (state.selectedWordlist == wl) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface),
                    ) { Text(wl.name, modifier = Modifier.padding(12.dp), color = if (state.selectedWordlist == wl) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) }
                }
            }

            if (state.isRunning || state.progress.status != CrackStatus.PENDING) { ProgressGauge(progress = state.progress) }

            state.result?.let { result ->
                Card(colors = CardDefaults.cardColors(containerColor = if (result.success) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer)) {
                    Column(Modifier.padding(16.dp)) {
                        Text(if (result.success) "PASSWORD FOUND!" else "Password not found", style = MaterialTheme.typography.titleMedium)
                        if (result.success) Text(result.password, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                        Text("Duration: ${result.duration / 1000}s | Keys tested: ${result.keysTested}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
