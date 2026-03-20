package com.wificracker.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.wificracker.app.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VulnDatabaseScreen(
    viewModel: VulnDatabaseViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
) {
    val vulns by viewModel.vulns.collectAsState(initial = emptyList())
    var searchQuery by remember { mutableStateOf("") }
    val filteredVulns = if (searchQuery.isBlank()) {
        vulns
    } else {
        vulns.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
                it.cveId.contains(searchQuery, ignoreCase = true) ||
                it.protocol.contains(searchQuery, ignoreCase = true)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.vuln_database) + " (${vulns.size})") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text(stringResource(R.string.vuln_search)) },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                singleLine = true,
            )

            // Protocol filter chips
            LazyRow(
                modifier = Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(listOf("ALL", "WEP", "WPA2", "WPA3", "802.11", "WPS")) { protocol ->
                    FilterChip(
                        selected = if (protocol == "ALL") searchQuery.isBlank() else searchQuery == protocol,
                        onClick = { searchQuery = if (protocol == "ALL") "" else protocol },
                        label = { Text(protocol, style = MaterialTheme.typography.labelSmall) },
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(filteredVulns, key = { it.cveId }) { vuln ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    vuln.cveId,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                val severityColor = when (vuln.severity) {
                                    "CRITICAL" -> MaterialTheme.colorScheme.error
                                    "HIGH" -> MaterialTheme.colorScheme.tertiary
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                                Text(
                                    "${vuln.severity} (${vuln.cvssScore})",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = severityColor,
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(vuln.title, style = MaterialTheme.typography.titleSmall)
                            Text(
                                "[${vuln.protocol}]",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                vuln.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 3,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                vuln.recommendation,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary,
                            )
                        }
                    }
                }
            }
        }
    }
}
