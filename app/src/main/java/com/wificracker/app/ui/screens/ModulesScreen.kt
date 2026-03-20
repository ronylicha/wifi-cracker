package com.wificracker.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.wificracker.app.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModulesScreen(
    viewModel: ModulesViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    val installedCount = state.modules.count { it.isInstalled }
    val totalCount = state.modules.size

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.modules_title) + " ($installedCount/$totalCount)") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                actions = {
                    IconButton(onClick = { viewModel.checkModules() }) {
                        Icon(Icons.Default.Refresh, stringResource(R.string.modules_refresh))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Install all button
            if (state.modules.any { !it.isInstalled }) {
                Button(
                    onClick = { viewModel.installAllMissing() },
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                ) {
                    Icon(Icons.Default.Download, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.modules_install_all))
                }
            }

            if (state.isChecking) {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    items(state.modules, key = { it.name }) { module ->
                        ModuleCard(
                            module = module,
                            onInstall = { viewModel.installFromTermux(module.name) },
                        )
                    }
                }
            }

            // Install log
            if (state.installLog.isNotBlank()) {
                Text(
                    stringResource(R.string.modules_log),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .background(Color(0xFF0D1117), RoundedCornerShape(8.dp))
                        .padding(8.dp),
                ) {
                    Text(
                        text = state.installLog,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = Color(0xFF00FF41),
                    )
                }
            }
        }
    }
}

@Composable
private fun ModuleCard(module: ModuleInfo, onInstall: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Status icon
            Icon(
                imageVector = if (module.isInstalled) Icons.Default.CheckCircle else Icons.Default.Cancel,
                contentDescription = null,
                tint = if (module.isInstalled) Color(0xFF00C853) else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(32.dp),
            )

            Spacer(Modifier.width(12.dp))

            // Info
            Column(modifier = Modifier.weight(1f)) {
                Text(module.name, style = MaterialTheme.typography.titleMedium)
                Text(module.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // Install button
            if (!module.isInstalled) {
                if (module.isInstalling) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                } else {
                    IconButton(onClick = onInstall) {
                        Icon(Icons.Default.Download, stringResource(R.string.modules_install), tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}
